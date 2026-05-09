package com.lifemanga.android.data

import kotlinx.coroutines.flow.Flow
import java.util.UUID

class CharacterRepository(
    private val characterDao: CharacterDao,
    private val imageStorage: ImageStorage,
) {
    fun observeAll(): Flow<List<CharacterEntity>> = characterDao.observeAll()

    fun observeViews(characterId: String): Flow<List<CharacterViewEntity>> =
        characterDao.observeViewsForCharacter(characterId)

    suspend fun getById(id: String): CharacterEntity? = characterDao.getById(id)

    suspend fun insert(character: CharacterEntity) = characterDao.insertCharacter(character)

    suspend fun insertView(view: CharacterViewEntity) = characterDao.insertView(view)

    suspend fun rename(id: String, name: String) {
        characterDao.rename(id, name.trim(), System.currentTimeMillis())
    }

    suspend fun deleteView(viewId: String) = characterDao.deleteView(viewId)

    suspend fun delete(id: String) {
        characterDao.deleteCharacter(id)
    }

    suspend fun createCharacter(
        name: String,
        bio: String,
        sourcePhotoPath: String?,
        artStyle: String = CharacterArtStyle.JP_ANIME.key,
    ): CharacterEntity {
        val now = System.currentTimeMillis()
        val entity = CharacterEntity(
            id = UUID.randomUUID().toString(),
            name = name.trim(),
            bio = bio.trim(),
            sourcePhotoPath = sourcePhotoPath,
            artStyle = artStyle,
            createdAt = now,
            updatedAt = now,
        )
        characterDao.insertCharacter(entity)
        return entity
    }

    suspend fun saveViewFromBytes(characterId: String, label: String, bytes: ByteArray): CharacterViewEntity {
        val path = imageStorage.saveBytes(bytes)
        val view = CharacterViewEntity(
            id = UUID.randomUUID().toString(),
            characterId = characterId,
            label = label,
            imagePath = path,
            createdAt = System.currentTimeMillis(),
        )
        characterDao.insertView(view)
        return view
    }
}
