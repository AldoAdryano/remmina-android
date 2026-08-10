package com.remotex.core.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

enum class CredentialKind {
    PASSWORD,
    VNC_PASSWORD,
    PRIVATE_KEY,
    PRIVATE_KEY_PASSPHRASE,
}

@Entity(tableName = "credentials")
data class CredentialEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val kind: String,
    val ciphertext: ByteArray,
    val iv: ByteArray,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)
