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
import com.lifemanga.android.data.ReferenceIntent
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
import java.security.MessageDigest

data class CreateUiState(
    val pickedImagePaths: List<String> = emptyList(),
    val userPrompt: String = "",
    val storyPrompt: String = "",
    val style: MangaStyle = MangaStyle.SHONEN_JUMP,
    val isColor: Boolean = true,
    val hasComfyApiKey: Boolean = false,
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
    private val characterRepo = ServiceLocator.characterRepository

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

    private val comfyKeyFlow = secureStore.hasComfyKey

    val uiState: StateFlow<CreateUiState> = combine(
        pickedFlow,
        promptFlow,
        settingsStore.flow,
        comfyKeyFlow,
        genStateFlow,
    ) { picked, prompt, settings: AppSettings, hasComfy, gen ->
        CreateUiState(
            pickedImagePaths = picked,
            userPrompt = prompt,
            storyPrompt = gen.storyPrompt,
            style = settings.style,
            isColor = settings.isColor,
            hasComfyApiKey = hasComfy,
            isGenerating = gen.isGenerating,
            toast = gen.toast,
            storyMode = settings.storyMode,
            panelCount = settings.panelCount,
            progressLog = gen.progressLog,
            lastError = gen.lastError,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CreateUiState())

    // ---------------------------------------------------------------------------
    // 60s 同请求去重：按 (sorted input paths + style + isColor + bubble + prompt) 算 SHA256
    // ---------------------------------------------------------------------------
    private data class DedupKey(val hash: String, val timestamp: Long)
    private val recentSubmissions = ArrayDeque<DedupKey>()
    private val dedupWindowMs = 60_000L

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

        // 跨屏参考图意图：消费 ReferenceIntent.offer(...) 留下的批量
        viewModelScope.launch {
            ReferenceIntent.pending.collect { _ ->
                val incoming = ReferenceIntent.consume()
                if (incoming.isNotEmpty()) {
                    addImageFromPathsInternal(incoming)
                    toastFlow.value = "已载入 ${incoming.size} 张参考图"
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
            addImageFromPathInternal(path)
        } else {
            toastFlow.value = "无法读取这张图，换一张试试"
        }
    }

    /** UI 直接给一个已存在的本地路径（来自历史 / 角色视图等）。 */
    fun addImageFromPath(path: String) {
        addImageFromPathInternal(path)
    }

    /** 一次性添加多张（角色视图批量载入 / ReferenceIntent 消费）。 */
    fun addImageFromPaths(paths: List<String>) {
        addImageFromPathsInternal(paths)
    }

    /** 从角色库载入：把该角色所有视图当作参考图。 */
    fun loadCharacterViews(characterId: String) {
        viewModelScope.launch {
            val views = characterRepo.observeViews(characterId).first()
            val paths = views.map { it.imagePath }
            if (paths.isEmpty()) {
                toastFlow.value = "这个角色还没有视图，先去生成"
            } else {
                addImageFromPathsInternal(paths)
                toastFlow.value = "已载入角色「${views.firstOrNull()?.label ?: ""}」的 ${paths.size} 张视图"
            }
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

            // 60s 同 hash 去重
            val fingerprint = computeFingerprint(
                paths = state.pickedImagePaths,
                styleKey = settings.style.key,
                isColor = settings.isColor,
                bubbleKey = settings.bubbleMode.key,
                prompt = (state.storyPrompt.ifBlank { state.userPrompt }).trim(),
            )
            val now = System.currentTimeMillis()
            pruneExpired(now)
            val duplicate = recentSubmissions.firstOrNull { it.hash == fingerprint }
            if (duplicate != null) {
                val ageSec = (now - duplicate.timestamp) / 1000
                toastFlow.value = "跟 $ageSec 秒前那次一样的请求，已拦截（防误触）"
                return@launch
            }
            recentSubmissions.addLast(DedupKey(fingerprint, now))
            // 最多留 32 条防止内存泄漏
            while (recentSubmissions.size > 32) recentSubmissions.removeFirst()

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

    // ---------------------------------------------------------------------------
    // helpers
    // ---------------------------------------------------------------------------

    private fun addImageFromPathInternal(path: String) {
        if (path.isBlank()) return
        pickedFlow.update { current ->
            if (current.contains(path)) current
            else if (current.size >= 6) {
                toastFlow.value = "最多 6 张参考图，先删一张"
                current
            } else current + path
        }
    }

    private fun addImageFromPathsInternal(paths: List<String>) {
        if (paths.isEmpty()) return
        pickedFlow.update { current ->
            val merged = (current + paths).distinct()
            if (merged.size > 6) {
                toastFlow.value = "参考图上限 6 张，多出的被丢弃"
                merged.take(6)
            } else merged
        }
    }

    private fun pruneExpired(now: Long) {
        while (recentSubmissions.isNotEmpty() && now - recentSubmissions.first().timestamp > dedupWindowMs) {
            recentSubmissions.removeFirst()
        }
    }

    private fun computeFingerprint(
        paths: List<String>,
        styleKey: String,
        isColor: Boolean,
        bubbleKey: String,
        prompt: String,
    ): String {
        val md = MessageDigest.getInstance("SHA-256")
        // 用排序后的路径，确保调换顺序算同 hash
        paths.sorted().forEach { md.update(it.toByteArray()); md.update(0) }
        md.update(styleKey.toByteArray()); md.update(0)
        md.update(if (isColor) 1 else 0); md.update(0)
        md.update(bubbleKey.toByteArray()); md.update(0)
        md.update(prompt.toByteArray())
        return md.digest().joinToString("") { "%02x".format(it) }
    }
}
