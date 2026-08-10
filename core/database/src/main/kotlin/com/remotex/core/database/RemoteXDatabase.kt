package com.remotex.core.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [ProfileEntity::class, CredentialEntity::class, KnownHostEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class RemoteXDatabase : RoomDatabase() {
    abstract fun profileDao(): ProfileDao
    abstract fun credentialDao(): CredentialDao
    abstract fun knownHostDao(): KnownHostDao

    companion object {
        fun create(context: Context): RemoteXDatabase = Room.databaseBuilder(
            context.applicationContext,
            RemoteXDatabase::class.java,
            "remotex.db",
        ).build()
    }
}
