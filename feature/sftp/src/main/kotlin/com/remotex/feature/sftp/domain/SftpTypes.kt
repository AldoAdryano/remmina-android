package com.remotex.feature.sftp.domain

data class RemoteFile(
    val path: String,
    val name: String,
    val directory: Boolean,
    val size: Long,
    val modifiedAtEpochMillis: Long?,
)

data class DestinationPreparation(
    val destination: String,
    val conflict: Boolean,
)

enum class TransferDirection { UPLOAD, DOWNLOAD, REMOTE_COPY }

data class TransferProgress(
    val transferredBytes: Long,
    val totalBytes: Long?,
)

sealed interface TransferState {
    data object Queued : TransferState
    data class Running(
        val transferredBytes: Long,
        val totalBytes: Long?,
        val bytesPerSecond: Long,
    ) : TransferState
    data object Completed : TransferState
    data class Failed(val message: String) : TransferState
    data object Cancelled : TransferState
}
