package com.lifemanga.android.ui.create

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.lifemanga.android.ServiceLocator
import com.lifemanga.android.data.AppSettings
import com.lifemanga.android.data.EndpointType
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
    val style: MangaStyle = MangaStyle.SHONEN_JUMP,
    val isColor: Boolean = true,
    val hasApiKey: Boolean = false,
    val isGenerating: Boolean = false,
    val toast: String? = null,
    val endpointType: EndpointType = EndpointType.OPENAI,
)

class CreateViewModel : ViewModel() {

    private val context: Context = ServiceLocator.context
    private val secureStore = ServiceLocator.secureStore
    private val settingsStore = ServiceLocator.appSettings
    private val imageStorage = ServiceLocator.imageStorage

    private val pickedFlow = MutableStateFlow<List<String>>(emptyList())
    private val promptFlow = MutableStateFlow("")
    private val toastFlow = MutableStateFlow<String?>(null)

    private val workInfoFlow = WorkManager.getInstance(context)
        .getWorkInfosForUniqueWorkFlow(MangaGenerationWorker.UNIQUE_NAME)

    private val isGeneratingFlow = workInfoFlow.map { list ->
        list.any { it.state == WorkInfo.State.RUNNING || it.state == WorkInfo.State.ENQUEUED }
    }

    val uiState: StateFlow<CreateUiState> = combine(
        pickedFlow,
        promptFlow,
        settingsStore.flow,
        secureStore.hasKey,
        combine(isGeneratingFlow, toastFlow) { generating, toast -> generating to toast },
    ) { picked, prompt, settings: AppSettings, has, gt ->
        CreateUiState(
            pickedImagePaths = picked,
            userPrompt = prompt,
            style = settings.style,
            isColor = settings.isColor,
            hasApiKey = has,
            isGenerating = gt.first,
            toast = gt.second,
            endpointType = settings.endpointConfig.type,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CreateUiState())

    fun setPrompt(value: String) {
        promptFlow.value = value
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

    fun startGeneration() {
        val state = uiState.value
        if (!state.hasApiKey) {
            toastFlow.value = "先去设置页填 API Key"
            return
        }
        if (state.endpointType == EndpointType.OPENAI && state.pickedImagePaths.isEmpty()) {
            toastFlow.value = "OpenAI 直连需要至少选一张参考图"
            return
        }
        if (state.isGenerating) {
            toastFlow.value = "已有任务在跑，等它完成"
            return
        }
        viewModelScope.launch {
            val settings = settingsStore.flow.first()
            if (settings.endpointConfig.isAzure && !settings.endpointConfig.isAzureReady) {
                toastFlow.value = "Azure 端点还没配置完整，去设置页填"
                return@launch
            }
            MangaGenerationWorker.enqueue(
                context = context,
                style = settings.style,
                bubble = settings.bubbleMode,
                userPrompt = state.userPrompt,
                isColor = settings.isColor,
                inputPaths = state.pickedImagePaths,
                endpoint = settings.endpointConfig,
            )
            toastFlow.value = "已提交，可以锁屏。完成后通知会响"
        }
    }
}
