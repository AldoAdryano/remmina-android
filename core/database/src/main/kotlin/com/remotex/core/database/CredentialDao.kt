package com.remotex.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update

@Dao
interface CredentialDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entity: CredentialEntity): Long

    @Update
    suspend fun update(entity: CredentialEntity)

    @Query("SELECT * FROM credentials WHERE id = :id LIMIT 1")
    suspend fun findById(id: Long): CredentialEntity?

    @Query("DELETE FROM credentials WHERE id = :id")
    suspend fun deleteById(id: Long)
}
