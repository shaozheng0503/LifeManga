package com.lifemanga.android.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "projects")
data class ProjectEntity(
    @PrimaryKey val id: String,
    val name: String,
    val createdAt: Long,
    val updatedAt: Long,
    val coverItemId: String?,
    val notes: String?,
)

@Dao
interface ProjectDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(p: ProjectEntity)

    @Query("SELECT * FROM projects ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<ProjectEntity>>

    @Query("SELECT * FROM projects WHERE id = :id")
    suspend fun getById(id: String): ProjectEntity?

    @Query("UPDATE projects SET name=:name, updatedAt=:updatedAt WHERE id=:id")
    suspend fun rename(id: String, name: String, updatedAt: Long)

    @Query("UPDATE projects SET updatedAt=:updatedAt, coverItemId=:coverId WHERE id=:id")
    suspend fun updateCover(id: String, coverId: String?, updatedAt: Long)

    @Query("DELETE FROM projects WHERE id=:id")
    suspend fun deleteById(id: String)
}
