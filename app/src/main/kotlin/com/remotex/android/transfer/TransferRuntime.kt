package com.remotex.android.transfer

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PersistableBundle
import androidx.annotation.RequiresApi
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ForegroundInfo
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.remotex.android.R
import com.remotex.android.RemoteXApplication
import com.remotex.core.model.CredentialPolicy
import com.remotex.feature.sftp.engine.RemoteSftpClient
import com.remotex.feature.sftp.storage.AndroidDownloadStore
import com.remotex.feature.sftp.transfer.BackgroundTransferDirection
import com.remotex.feature.sftp.transfer.BackgroundTransferHandle
import com.remotex.feature.sftp.transfer.BackgroundTransferRequest
import com.remotex.feature.sftp.transfer.BackgroundTransferScheduler
import com.remotex.feature.ssh.domain.SshConnectionSpec
import java.io.File
import java.io.FileInputStream
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

private const val CHANNEL_ID = "remotex_transfers"
private const val ACTION_CANCEL_TRANSFER = "com.remotex.android.action.CANCEL_TRANSFER"
private const val EXTRA_TRANSFER_HANDLE = "transfer_handle"
private const val EXTRA_CANCEL_LOCAL_URI = "cancel_local_uri"
private const val EXTRA_CANCEL_DIRECTION = "cancel_direction"
private const val KEY_PROFILE_ID = "profile_id"
private const val KEY_DIRECTION = "direction"
private const val KEY_LOCAL_URI = "local_uri"
private const val KEY_REMOTE_PATH = "remote_path"
private const val KEY_DISPLAY_NAME = "display_name"
private const val KEY_TOTAL = "total"

class AndroidBackgroundTransferScheduler(
    private val context: Context,
) : BackgroundTransferScheduler {
    private val nextJobId = AtomicInteger(41000)

    override fun schedule(request: BackgroundTransferRequest): BackgroundTransferHandle? {
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return null
        }
        createTransferChannel(context)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            scheduleUidt(request)
        } else {
            scheduleWork(request)
        }
    }

    override fun cancel(handle: BackgroundTransferHandle) {
        when {
            handle.id.startsWith("job:") -> {
                val jobId = handle.id.substringAfter("job:").toIntOrNull() ?: return
                context.getSystemService(JobScheduler::class.java).cancel(jobId)
            }
            handle.id.startsWith("work:") -> {
                val workId = runCatching { UUID.fromString(handle.id.substringAfter("work:")) }.getOrNull() ?: return
                WorkManager.getInstance(context).cancelWorkById(workId)
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    private fun scheduleUidt(request: BackgroundTransferRequest): BackgroundTransferHandle? {
        val scheduler = context.getSystemService(JobScheduler::class.java)
        if (!scheduler.canRunUserInitiatedJobs()) return null
        val jobId = nextJobId.incrementAndGet()
        val builder = JobInfo.Builder(
            jobId,
            ComponentName(context, RemoteXTransferJobService::class.java),
        )
            .setUserInitiated(true)
            .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
            .setExtras(request.toPersistableBundle())
        request.totalBytes?.takeIf { it >= 0 }?.let { total ->
            if (request.direction == BackgroundTransferDirection.DOWNLOAD) {
                builder.setEstimatedNetworkBytes(total, 0L)
            } else {
                builder.setEstimatedNetworkBytes(0L, total)
            }
        }
        return if (scheduler.schedule(builder.build()) == JobScheduler.RESULT_SUCCESS) {
            BackgroundTransferHandle("job:$jobId")
        } else {
            null
        }
    }

    private fun scheduleWork(request: BackgroundTransferRequest): BackgroundTransferHandle {
        val work = OneTimeWorkRequestBuilder<RemoteXTransferWorker>()
            .setInputData(request.toData())
            .build()
        WorkManager.getInstance(context).enqueue(work)
        return BackgroundTransferHandle("work:${work.id}")
    }
}

class TransferCancelReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_CANCEL_TRANSFER) return
        val id = intent.getStringExtra(EXTRA_TRANSFER_HANDLE) ?: return
        AndroidBackgroundTransferScheduler(context.applicationContext).cancel(BackgroundTransferHandle(id))
        val direction = intent.getStringExtra(EXTRA_CANCEL_DIRECTION)
        val localUri = intent.getStringExtra(EXTRA_CANCEL_LOCAL_URI)
        if (direction == BackgroundTransferDirection.DOWNLOAD.name && localUri != null) {
            AndroidDownloadStore(context.applicationContext).abort(Uri.parse(localUri))
        }
    }
}

class RemoteXTransferJobService : JobService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val jobs = ConcurrentHashMap<Int, Job>()

    override fun onStartJob(params: JobParameters): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return false
        val request = params.extras.toTransferRequest()
        val handle = BackgroundTransferHandle("job:${params.jobId}")
        val notificationId = 50000 + (params.jobId % 10000)
        setNotification(
            params,
            notificationId,
            transferNotification(this, request.displayName, "Menyiapkan transfer…", null, handle, request),
            JOB_END_NOTIFICATION_POLICY_REMOVE,
        )
        jobs[params.jobId] = scope.launch {
            runCatching {
                TransferExecutor.execute(this@RemoteXTransferJobService, request) { done, total ->
                    val percent = total?.takeIf { it > 0 }?.let { ((done * 100) / it).toInt().coerceIn(0, 100) }
                    setNotification(
                        params,
                        notificationId,
                        transferNotification(this@RemoteXTransferJobService, request.displayName, "$done byte", percent, handle, request),
                        JOB_END_NOTIFICATION_POLICY_REMOVE,
                    )
                    if (request.direction == BackgroundTransferDirection.DOWNLOAD) {
                        updateTransferredNetworkBytes(params, done, 0L)
                    } else {
                        updateTransferredNetworkBytes(params, 0L, done)
                    }
                }
            }
            jobs.remove(params.jobId)
            jobFinished(params, false)
        }
        return true
    }

    override fun onStopJob(params: JobParameters): Boolean {
        jobs.remove(params.jobId)?.cancel()
        return false
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}

class RemoteXTransferWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val request = inputData.toTransferRequest()
        val handle = BackgroundTransferHandle("work:$id")
        val notificationId = 61000 + id.hashCode().and(0x0fff)
        setForeground(
            ForegroundInfo(
                notificationId,
                transferNotification(applicationContext, request.displayName, "Transfer berjalan…", null, handle, request),
            )
        )
        return try {
            TransferExecutor.execute(applicationContext, request) { done, total ->
                setProgress(Data.Builder().putLong("bytes", done).putLong("total", total ?: -1L).build())
            }
            Result.success()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Throwable) {
            Result.failure()
        }
    }
}

object TransferExecutor {
    suspend fun execute(
        context: Context,
        request: BackgroundTransferRequest,
        progress: (Long, Long?) -> Unit,
    ) {
        val container = (context.applicationContext as RemoteXApplication).container
        val profile = requireNotNull(container.profileRepository.findById(request.profileId)) { "Profil tidak ditemukan" }
        require(profile.credentialPolicy == CredentialPolicy.SAVE_SECURELY) {
            "Transfer background memerlukan kredensial yang disimpan terenkripsi"
        }
        val auth = requireNotNull(container.savedSshAuth(profile)) { "Kredensial SSH belum tersimpan" }
        val engine = container.newSshEngine()
        val session = engine.connect(SshConnectionSpec(profile.host, profile.sshPort, profile.username, auth))
            ?: error("SSH gagal atau host key belum dipercaya")
        val client = RemoteSftpClient(session.openSftpTransport())
        try {
            val uri = Uri.parse(request.localUri)
            when (request.direction) {
                BackgroundTransferDirection.UPLOAD -> {
                    val input = if (uri.scheme == "file") {
                        FileInputStream(File(requireNotNull(uri.path)))
                    } else {
                        requireNotNull(context.contentResolver.openInputStream(uri)) { "File lokal tidak dapat dibuka" }
                    }
                    client.upload(input, request.remotePath, request.totalBytes, progress)
                }
                BackgroundTransferDirection.DOWNLOAD -> {
                    val store = AndroidDownloadStore(context)
                    runCatching {
                        val output = store.open(uri)
                        client.download(request.remotePath, output, request.totalBytes, progress)
                        store.finish(uri)
                    }.onFailure {
                        store.abort(uri)
                        throw it
                    }
                }
            }
            container.profileRepository.markConnected(profile.id)
        } finally {
            client.close()
            session.close()
            engine.disconnect()
        }
    }
}

private fun BackgroundTransferRequest.toPersistableBundle() = PersistableBundle().apply {
    putLong(KEY_PROFILE_ID, profileId)
    putString(KEY_DIRECTION, direction.name)
    putString(KEY_LOCAL_URI, localUri)
    putString(KEY_REMOTE_PATH, remotePath)
    putString(KEY_DISPLAY_NAME, displayName)
    putLong(KEY_TOTAL, totalBytes ?: -1L)
}

private fun PersistableBundle.toTransferRequest() = BackgroundTransferRequest(
    profileId = getLong(KEY_PROFILE_ID),
    direction = BackgroundTransferDirection.valueOf(requireNotNull(getString(KEY_DIRECTION))),
    localUri = requireNotNull(getString(KEY_LOCAL_URI)),
    remotePath = requireNotNull(getString(KEY_REMOTE_PATH)),
    displayName = requireNotNull(getString(KEY_DISPLAY_NAME)),
    totalBytes = getLong(KEY_TOTAL).takeIf { it >= 0 },
)

private fun BackgroundTransferRequest.toData() = Data.Builder()
    .putLong(KEY_PROFILE_ID, profileId)
    .putString(KEY_DIRECTION, direction.name)
    .putString(KEY_LOCAL_URI, localUri)
    .putString(KEY_REMOTE_PATH, remotePath)
    .putString(KEY_DISPLAY_NAME, displayName)
    .putLong(KEY_TOTAL, totalBytes ?: -1L)
    .build()

private fun Data.toTransferRequest() = BackgroundTransferRequest(
    profileId = getLong(KEY_PROFILE_ID, 0),
    direction = BackgroundTransferDirection.valueOf(requireNotNull(getString(KEY_DIRECTION))),
    localUri = requireNotNull(getString(KEY_LOCAL_URI)),
    remotePath = requireNotNull(getString(KEY_REMOTE_PATH)),
    displayName = requireNotNull(getString(KEY_DISPLAY_NAME)),
    totalBytes = getLong(KEY_TOTAL, -1L).takeIf { it >= 0 },
)

private fun createTransferChannel(context: Context) {
    val manager = context.getSystemService(NotificationManager::class.java)
    manager.createNotificationChannel(
        NotificationChannel(CHANNEL_ID, "Transfer RemoteX", NotificationManager.IMPORTANCE_LOW)
    )
}

private fun transferNotification(
    context: Context,
    name: String,
    status: String,
    percent: Int?,
    handle: BackgroundTransferHandle?,
    request: BackgroundTransferRequest?,
): Notification {
    createTransferChannel(context)
    return Notification.Builder(context, CHANNEL_ID)
        .setSmallIcon(R.drawable.ic_stat_remotex)
        .setContentTitle(name)
        .setContentText(status)
        .setOnlyAlertOnce(true)
        .setOngoing(percent == null || percent < 100)
        .apply {
            if (percent != null) setProgress(100, percent, false) else setProgress(0, 0, true)
            if (handle != null) {
                addAction(
                    Notification.Action.Builder(
                        R.drawable.ic_stat_remotex,
                        "Batal",
                        cancelPendingIntent(context, handle, request),
                    ).build()
                )
            }
        }
        .build()
}

private fun cancelPendingIntent(
    context: Context,
    handle: BackgroundTransferHandle,
    request: BackgroundTransferRequest?,
): PendingIntent {
    val intent = Intent(context, TransferCancelReceiver::class.java)
        .setAction(ACTION_CANCEL_TRANSFER)
        .putExtra(EXTRA_TRANSFER_HANDLE, handle.id)
        .putExtra(EXTRA_CANCEL_LOCAL_URI, request?.localUri)
        .putExtra(EXTRA_CANCEL_DIRECTION, request?.direction?.name)
    return PendingIntent.getBroadcast(
        context,
        handle.id.hashCode(),
        intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
}
