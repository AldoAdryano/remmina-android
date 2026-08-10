package com.remotex.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ProfileDao {
    @Query("SELECT * FROM profiles ORDER BY name COLLATE NOCASE ASC")
    fun observeAll(): Flow<List<ProfileEntity>>

    @Query("SELECT * FROM profiles WHERE favorite = 1 ORDER BY name COLLATE NOCASE ASC")
    fun observeFavorites(): Flow<List<ProfileEntity>>

    @Query("SELECT * FROM profiles WHERE last_connected_at IS NOT NULL ORDER BY last_connected_at DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<ProfileEntity>>

    @Query("SELECT * FROM profiles WHERE id = :id LIMIT 1")
    suspend fun findById(id: Long): ProfileEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entity: ProfileEntity): Long

    @Update
    suspend fun update(entity: ProfileEntity)

    @Query("DELETE FROM profiles WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("UPDATE profiles SET last_connected_at = :at WHERE id = :id")
    suspend fun markConnected(id: Long, at: Long)

    @Query("UPDATE profiles SET favorite = :favorite WHERE id = :id")
    suspend fun setFavorite(id: Long, favorite: Boolean)
}
