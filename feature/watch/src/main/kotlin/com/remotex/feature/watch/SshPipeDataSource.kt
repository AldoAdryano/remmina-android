package com.remotex.feature.watch

import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import java.io.IOException

@OptIn(UnstableApi::class)
class SshPipeDataSource private constructor(
    private val pipe: RemoteWatchPipe,
) : BaseDataSource(true) {
    private var opened = false
    private var currentUri: Uri? = null

    @Throws(IOException::class)
    override fun open(dataSpec: DataSpec): Long {
        // The progressive loader may reopen at its current byte position after a retry.
        // This is a live, forward-only pipe, so the shared pipe simply continues where it is.
        currentUri = dataSpec.uri
        transferInitializing(dataSpec)
        opened = true
        transferStarted(dataSpec)
        return C.LENGTH_UNSET.toLong()
    }

    @Throws(IOException::class)
    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (!opened) throw IOException("Stream Mode Menonton belum dibuka")
        val count = pipe.read(buffer, offset, length)
        if (count > 0) bytesTransferred(count)
        return count
    }

    override fun getUri(): Uri? = currentUri

    override fun close() {
        if (opened) {
            opened = false
            transferEnded()
        }
        currentUri = null
    }

    class Factory(
        private val pipe: RemoteWatchPipe,
    ) : DataSource.Factory {
        override fun createDataSource(): DataSource = SshPipeDataSource(pipe)
    }
}
