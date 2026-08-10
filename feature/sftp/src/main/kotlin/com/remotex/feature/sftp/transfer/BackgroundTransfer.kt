package com.remotex.feature.sftp.transfer

enum class BackgroundTransferDirection { UPLOAD, DOWNLOAD }

data class BackgroundTransferRequest(
    val profileId: Long,
    val direction: BackgroundTransferDirection,
    val localUri: String,
    val remotePath: String,
    val displayName: String,
    val totalBytes: Long? = null,
)

data class BackgroundTransferHandle(val id: String)

interface BackgroundTransferScheduler {
    fun schedule(request: BackgroundTransferRequest): BackgroundTransferHandle?
    fun cancel(handle: BackgroundTransferHandle)
}
