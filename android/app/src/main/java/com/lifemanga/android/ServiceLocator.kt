package com.lifemanga.android

import android.content.Context
import androidx.room.Room
import com.lifemanga.android.data.AppDatabase
import com.lifemanga.android.data.AppSettingsStore
import com.lifemanga.android.data.CharacterRepository
import com.lifemanga.android.data.ImageStorage
import com.lifemanga.android.data.ProjectRepository
import com.lifemanga.android.data.Repository
import com.lifemanga.android.data.SecureStore
import com.lifemanga.android.network.ComfyUIClient
import com.lifemanga.android.network.QwenClient
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

object ServiceLocator {
    private lateinit var appContext: Context

    val context: Context get() = appContext

    val secureStore: SecureStore by lazy { SecureStore(appContext) }
    val appSettings: AppSettingsStore by lazy { AppSettingsStore(appContext) }

    private val database: AppDatabase by lazy {
        Room.databaseBuilder(appContext, AppDatabase::class.java, "lifemanga.db")
            .fallbackToDestructiveMigration()
            .build()
    }

    val imageStorage: ImageStorage by lazy { ImageStorage(appContext) }
    val repository: Repository by lazy { Repository(database.mangaDao(), imageStorage) }
    val projectRepository: ProjectRepository by lazy {
        ProjectRepository(database.projectDao(), database.mangaDao(), imageStorage)
    }
    val characterRepository: CharacterRepository by lazy {
        CharacterRepository(database.characterDao(), imageStorage)
    }

    fun comfyUIClient(): ComfyUIClient {
        val settings = runBlocking { appSettings.flow.first() }
        return ComfyUIClient(settings.comfyUiUrl, secureStore.comfyApiKey.orEmpty())
    }

    fun qwenClient(): QwenClient {
        val settings = runBlocking { appSettings.flow.first() }
        return QwenClient(settings.qwenUrl)
    }

    fun init(context: Context) {
        appContext = context.applicationContext
    }
}
