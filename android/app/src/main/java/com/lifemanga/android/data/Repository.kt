package com.lifemanga.android.data

import kotlinx.coroutines.flow.Flow
import java.util.UUID

class Repository(
    private val dao: MangaItemDao,
    private val imageStorage: ImageStorage,
) {

    val allItems: Flow<List<MangaItemEntity>> = dao.observeAll()

    suspend fun get(id: String): MangaItemEntity? = dao.getById(id)

    suspend fun saveGenerated(
        style: MangaStyle,
        userPrompt: String,
        inputImagePaths: List<String>,
        outputBytes: List<ByteArray>,
    ): MangaItemEntity {
        val outputPaths = outputBytes.map { imageStorage.saveBytes(it) }
        val entity = MangaItemEntity(
            id = UUID.randomUUID().toString(),
            createdAt = System.currentTimeMillis(),
            styleKey = style.key,
            userPrompt = userPrompt,
            isFavorite = false,
            inputImagePathsCsv = inputImagePaths.joinToString("|"),
            outputImagePathsCsv = outputPaths.joinToString("|"),
        )
        dao.insert(entity)
        return entity
    }

    fun observeItemsByProject(projectId: String): Flow<List<MangaItemEntity>> =
        dao.observeByProject(projectId)

    suspend fun saveGeneratedForProject(
        projectId: String,
        style: MangaStyle,
        userPrompt: String,
        inputImagePaths: List<String>,
        outputBytes: List<ByteArray>,
        storyScriptJson: String?,
    ): MangaItemEntity {
        val outputPaths = outputBytes.map { imageStorage.saveBytes(it) }
        val entity = MangaItemEntity(
            id = UUID.randomUUID().toString(),
            createdAt = System.currentTimeMillis(),
            styleKey = style.key,
            userPrompt = userPrompt,
            isFavorite = false,
            inputImagePathsCsv = inputImagePaths.joinToString("|"),
            outputImagePathsCsv = outputPaths.joinToString("|"),
            projectId = projectId,
            storyScriptJson = storyScriptJson,
        )
        dao.insert(entity)
        return entity
    }

    suspend fun toggleFavorite(id: String) {
        val item = dao.getById(id) ?: return
        dao.setFavorite(id, !item.isFavorite)
    }

    suspend fun delete(id: String) {
        val item = dao.getById(id) ?: return
        item.inputImagePaths.forEach { imageStorage.delete(it) }
        item.outputImagePaths.forEach { imageStorage.delete(it) }
        dao.deleteById(id)
    }
}
