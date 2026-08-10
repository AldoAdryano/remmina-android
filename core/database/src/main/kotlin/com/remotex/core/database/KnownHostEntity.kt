package com.remotex.core.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "known_hosts",
    indices = [Index(value = ["host", "port"], unique = true)],
)
data class KnownHostEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val host: String,
    val port: Int,
    val algorithm: String,
    @ColumnInfo(name = "sha256_fingerprint") val sha256Fingerprint: String,
    @ColumnInfo(name = "key_blob") val keyBlob: ByteArray,
    @ColumnInfo(name = "trusted_at") val trustedAt: Long,
)
