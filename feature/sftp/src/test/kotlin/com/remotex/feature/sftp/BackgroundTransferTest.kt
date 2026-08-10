package com.remotex.feature.sftp

import com.remotex.feature.sftp.transfer.BackgroundTransferDirection
import com.remotex.feature.sftp.transfer.BackgroundTransferHandle
import com.remotex.feature.sftp.transfer.BackgroundTransferRequest
import com.remotex.feature.sftp.transfer.BackgroundTransferScheduler
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BackgroundTransferTest {
    private class FakeScheduler : BackgroundTransferScheduler {
        val scheduled = mutableListOf<BackgroundTransferRequest>()
        val cancelled = mutableListOf<BackgroundTransferHandle>()
        var enabled = true

        override fun schedule(request: BackgroundTransferRequest): BackgroundTransferHandle? {
            if (!enabled) return null
            scheduled += request
            return BackgroundTransferHandle("job-${scheduled.size}")
        }

        override fun cancel(handle: BackgroundTransferHandle) {
            cancelled += handle
        }
    }

    @Test
    fun scheduler_returnsHandleThatCanBeCancelled() {
        val scheduler = FakeScheduler()
        val request = BackgroundTransferRequest(
            profileId = 7,
            direction = BackgroundTransferDirection.DOWNLOAD,
            localUri = "content://downloads/7",
            remotePath = "/home/user/report.pdf",
            displayName = "report.pdf",
            totalBytes = 42,
        )

        val handle = scheduler.schedule(request)!!
        scheduler.cancel(handle)

        assertEquals(listOf(request), scheduler.scheduled)
        assertEquals(listOf(handle), scheduler.cancelled)
    }

    @Test
    fun scheduler_canRejectBackgroundExecution() {
        val scheduler = FakeScheduler().apply { enabled = false }
        val handle = scheduler.schedule(
            BackgroundTransferRequest(
                profileId = 1,
                direction = BackgroundTransferDirection.UPLOAD,
                localUri = "content://document/1",
                remotePath = "/tmp/a.txt",
                displayName = "a.txt",
            ),
        )
        assertNull(handle)
    }
}
