package com.lifemanga.android.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface MangaItemDao {

    @Query("SELECT * FROM manga_items ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<MangaItemEntity>>

    @Query("SELECT * FROM manga_items WHERE id = :id")
    suspend fun getById(id: String): MangaItemEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: MangaItemEntity)

    @Query("UPDATE manga_items SET isFavorite = :fav WHERE id = :id")
    suspend fun setFavorite(id: String, fav: Boolean)

    @Query("DELETE FROM manga_items WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("SELECT * FROM manga_items WHERE projectId=:pid ORDER BY createdAt DESC")
    fun observeByProject(pid: String): Flow<List<MangaItemEntity>>

    @Query("SELECT * FROM manga_items WHERE projectId=:pid ORDER BY createdAt DESC")
    suspend fun getByProject(pid: String): List<MangaItemEntity>
}
