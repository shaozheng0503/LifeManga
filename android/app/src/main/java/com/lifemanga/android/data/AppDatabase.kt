package com.lifemanga.android.data

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        MangaItemEntity::class,
        ProjectEntity::class,
        CharacterEntity::class,
        CharacterViewEntity::class,
    ],
    version = 3,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun mangaDao(): MangaItemDao
    abstract fun projectDao(): ProjectDao
    abstract fun characterDao(): CharacterDao
}
