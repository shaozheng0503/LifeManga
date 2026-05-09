package com.lifemanga.android.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "characters")
data class CharacterEntity(
    @PrimaryKey val id: String,
    val name: String,
    val bio: String,
    val sourcePhotoPath: String?,
    val artStyle: String = CharacterArtStyle.JP_ANIME.key,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(tableName = "character_views")
data class CharacterViewEntity(
    @PrimaryKey val id: String,
    val characterId: String,
    val label: String,       // e.g. "正面", "侧面", "背面", "表情", "服装"
    val imagePath: String,
    val createdAt: Long,
)

@Dao
interface CharacterDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCharacter(character: CharacterEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertView(view: CharacterViewEntity)

    @Query("SELECT * FROM characters ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<CharacterEntity>>

    @Query("SELECT * FROM characters WHERE id = :id")
    suspend fun getById(id: String): CharacterEntity?

    @Query("SELECT * FROM character_views WHERE characterId = :characterId ORDER BY createdAt ASC")
    fun observeViewsForCharacter(characterId: String): Flow<List<CharacterViewEntity>>

    @Query("DELETE FROM characters WHERE id = :id")
    suspend fun deleteCharacter(id: String)

    @Query("DELETE FROM character_views WHERE id = :viewId")
    suspend fun deleteView(viewId: String)

    @Query("UPDATE characters SET name=:name, updatedAt=:updatedAt WHERE id=:id")
    suspend fun rename(id: String, name: String, updatedAt: Long)
}
