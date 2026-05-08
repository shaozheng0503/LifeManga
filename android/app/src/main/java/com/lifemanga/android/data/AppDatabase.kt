package com.lifemanga.android.data

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [MangaItemEntity::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun mangaDao(): MangaItemDao
}
