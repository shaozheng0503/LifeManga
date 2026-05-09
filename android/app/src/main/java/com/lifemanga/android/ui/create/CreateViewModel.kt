package com.lifemanga.android.ui.create

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.lifemanga.android.ServiceLocator
import com.lifemanga.android.data.AppSettings
import com.lifemanga.android.data.MangaStyle
import com.lifemanga.android.work.MangaGenerationWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class CreateUiState(
    val pickedImagePaths: List<String> = emptyList(),
    val userPrompt: String = "",
    val storyPrompt: String = "",
    val style: MangaStyle = MangaStyle.SHONEN_JUMP,
    val isColor: Boolean = true,
    val hasApiKey: Boolean = false,
    val isGenerating: Boolean = false,
    val toast: String? = null,
    val storyMode: Boolean = false,
    val panelCount: Int = 4,
    val progressLog: List<String> = emptyList(),
    val lastError: String? = null,
)

private data class GenState(
    val isGenerating: Boolean,
    val toast: String?,
    val storyPrompt: String,
    val progressLog: List<String>,
    val lastError: String?,
)

class CreateViewModel : ViewModel() {

    private val context: Context = ServiceLocator.context
    private val secureStore = ServiceLocator.secureStore
    private val settingsStore = ServiceLocator.appSettings
    private val imageStorage = ServiceLocator.imageStorage

    private val pickedFlow = MutableStateFlow<List<String>>(emptyList())
    private val promptFlow = MutableStateFlow("")
    private val storyPromptFlow = MutableStateFlow("")
    private val toastFlow = MutableStateFlow<String?>(null)
    private val progressLogFlow = MutableStateFlow<List<String>>(emptyList())
    private val lastErrorFlow = MutableStateFlow<String?>(null)

    private val workInfoFlow = WorkManager.getInstance(context)
        .getWorkInfosForUniqueWorkFlow(MangaGenerationWorker.UNIQUE_NAME)

    private val isGeneratingFlow = workInfoFlow.map { list ->
        list.any { it.state == WorkInfo.State.RUNNING || it.state == WorkInfo.State.ENQUEUED }
    }

    private val genStateFlow: kotlinx.coroutines.flow.Flow<GenState> = combine(
        isGeneratingFlow,
        toastFlow,
        storyPromptFlow,
        progressLogFlow,
        lastErrorFlow,
    ) { generating, toast, storyPrompt, log, error ->
        GenState(generating, toast, storyPrompt, log, error)
    }

    val uiState: StateFlow<CreateUiState> = combine(
        pickedFlow,
        promptFlow,
        settingsStore.flow,
        secureStore.hasKey,
        genStateFlow,
    ) { picked, prompt, settings: AppSettings, has, gen ->
        CreateUiState(
            pickedImagePaths = picked,
            userPrompt = prompt,
            storyPrompt = gen.storyPrompt,
            style = settings.style,
            isColor = settings.isColor,
            hasApiKey = has,
            isGenerating = gen.isGenerating,
            toast = gen.toast,
            storyMode = settings.storyMode,
            panelCount = settings.panelCount,
            progressLog = gen.progressLog,
            lastError = gen.lastError,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CreateUiState())

    init {
        // Observe WorkInfo to accumulate real-time progress log in UI
        viewModelScope.launch {
            workInfoFlow.collect { infoList ->
                val info = infoList.firstOrNull() ?: return@collect
                when (info.state) {
                    WorkInfo.State.ENQUEUED -> {
                        progressLogFlow.value = listOf("⏳ 任务已加入队列，等待执行…")
                        lastErrorFlow.value = null
                    }
                    WorkInfo.State.RUNNING -> {
                        val text = info.progress.getString(MangaGenerationWorker.KEY_PROGRESS)
                        if (text != null) {
                            progressLogFlow.update { prev ->
                                if (prev.lastOrNull() == text) prev else prev + text
                            }
                        }
                    }
                    WorkInfo.State.SUCCEEDED -> {
                        progressLogFlow.update { it + "🎉 生成完成！前往历史页查看结果。" }
                        lastErrorFlow.value = null
                    }
                    WorkInfo.State.FAILED -> {
                        val error = info.outputData.getString(MangaGenerationWorker.KEY_ERROR_MESSAGE)
                            ?: "未知错误"
                        lastErrorFlow.value = error
                        progressLogFlow.update { it + "❌ 失败：$error" }
                    }
                    WorkInfo.State.CANCELLED -> {
                        progressLogFlow.update { it + "⛔ 任务已取消" }
                    }
                    else -> {}
                }
            }
        }
    }

    fun setPrompt(value: String) {
        promptFlow.value = value
    }

    fun setStoryPrompt(value: String) {
        storyPromptFlow.value = value
    }

    fun addImageFromUri(uri: Uri) = viewModelScope.launch {
        val path = withContext(Dispatchers.IO) { imageStorage.saveUriAsJpeg(uri) }
        if (path != null) {
            pickedFlow.update { it + path }
        } else {
            toastFlow.value = "无法读取这张图，换一张试试"
        }
    }

    fun removeImage(path: String) {
        pickedFlow.update { it.filterNot { p -> p == path } }
        imageStorage.delete(path)
    }

    fun clearToast() {
        toastFlow.value = null
    }

    fun clearLog() {
        progressLogFlow.value = emptyList()
        lastErrorFlow.value = null
    }

    fun startGeneration(projectId: String? = null) {
        val state = uiState.value
        if (state.isGenerating) {
            toastFlow.value = "已有任务在跑，等它完成"
            return
        }
        viewModelScope.launch {
            val settings = settingsStore.flow.first()
            MangaGenerationWorker.enqueue(
                context = context,
                style = settings.style,
                bubble = settings.bubbleMode,
                userPrompt = state.userPrompt,
                storyPrompt = state.storyPrompt,
                isColor = settings.isColor,
                inputPaths = state.pickedImagePaths,
                panelCount = settings.panelCount,
                projectId = projectId,
                comfyUrl = settings.comfyUiUrl,
                comfyApiKey = secureStore.comfyApiKey.orEmpty(),
            )
            toastFlow.value = "已提交，可以锁屏。完成后通知会响"
        }
    }
}
