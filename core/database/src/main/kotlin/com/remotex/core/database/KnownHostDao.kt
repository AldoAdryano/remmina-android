package com.remotex.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface KnownHostDao {
    @Query("SELECT * FROM known_hosts WHERE host = :host AND port = :port LIMIT 1")
    suspend fun find(host: String, port: Int): KnownHostEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: KnownHostEntity): Long

    @Query("DELETE FROM known_hosts WHERE host = :host AND port = :port")
    suspend fun delete(host: String, port: Int)
}
