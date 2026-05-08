package com.sonuverma.deeploader.data.db

import androidx.room.Database
import androidx.room.RoomDatabase

/**
 * Room database for DeepLoader.
 * Stores download history, queue, and metadata.
 * Version 1 — initial schema.
 */
@Database(
    entities = [DownloadEntity::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun downloadDao(): DownloadDao

    companion object {
        const val DATABASE_NAME = "deeploader_db"
    }
}
