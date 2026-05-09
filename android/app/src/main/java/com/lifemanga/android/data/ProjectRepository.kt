package com.lifemanga.android.data

import kotlinx.coroutines.flow.Flow
import java.util.UUID

class ProjectRepository(
    private val projectDao: ProjectDao,
    private val mangaItemDao: MangaItemDao,
    private val imageStorage: ImageStorage,
) {
    fun observeAll(): Flow<List<ProjectEntity>> = projectDao.observeAll()

    suspend fun getById(id: String): ProjectEntity? = projectDao.getById(id)

    suspend fun create(name: String): String {
        val id = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        projectDao.insert(
            ProjectEntity(
                id = id,
                name = name.trim(),
                createdAt = now,
                updatedAt = now,
                coverItemId = null,
                notes = null,
            )
        )
        return id
    }

    suspend fun rename(id: String, name: String) {
        projectDao.rename(id, name.trim(), System.currentTimeMillis())
    }

    suspend fun delete(id: String) {
        // Delete all manga items belonging to this project
        val items = mangaItemDao.getByProject(id)
        items.forEach { item ->
            item.inputImagePaths.forEach { imageStorage.delete(it) }
            item.outputImagePaths.forEach { imageStorage.delete(it) }
            mangaItemDao.deleteById(item.id)
        }
        projectDao.deleteById(id)
    }
}
