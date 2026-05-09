package com.lifemanga.android.work

import android.content.Context
import android.content.pm.ServiceInfo
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ForegroundInfo
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.lifemanga.android.ServiceLocator
import com.lifemanga.android.data.BubbleMode
import com.lifemanga.android.data.MangaStyle
import com.lifemanga.android.network.ComfyResult
import com.lifemanga.android.network.ComfyUIClient
import com.lifemanga.android.network.ComfyWorkflows
import com.lifemanga.android.network.ImageCompression
import com.lifemanga.android.network.MangaPrompts
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File

class MangaGenerationWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            runGeneration()
        } catch (e: Exception) {
            val msg = "未捕获异常：${e.javaClass.simpleName}: ${e.message}"
            android.util.Log.e("MangaWorker", msg, e)
            emitLog(msg)
            NotificationHelper.showResult(applicationContext, success = false, message = msg)
            Result.failure(workDataOf(KEY_ERROR_MESSAGE to msg))
        }
    }

    private suspend fun runGeneration(): Result {
        NotificationHelper.ensureChannels(applicationContext)
        emitLog("⏳ 正在初始化…")

        val styleKey = inputData.getString(KEY_STYLE) ?: return failure("缺少风格参数")
        val bubbleKey = inputData.getString(KEY_BUBBLE) ?: BubbleMode.CHINESE.key
        val userPrompt = inputData.getString(KEY_USER_PROMPT).orEmpty()
        val storyPrompt = inputData.getString(KEY_STORY_PROMPT).orEmpty()
        val isColor = inputData.getBoolean(KEY_IS_COLOR, true)
        val inputPaths = inputData.getStringArray(KEY_INPUT_PATHS)?.toList().orEmpty()
        val panelCount = inputData.getInt(KEY_PANEL_COUNT, 1)
        val projectId = inputData.getString(KEY_PROJECT_ID)

        val settings = ServiceLocator.appSettings.flow.first()
        val comfyUrl = inputData.getString(KEY_COMFY_URL) ?: settings.comfyUiUrl
        val comfyApiKey = inputData.getString(KEY_COMFY_API_KEY)
            ?: ServiceLocator.secureStore.comfyApiKey.orEmpty()

        val style = MangaStyle.fromKey(styleKey) ?: return failure("未知风格 $styleKey")
        val bubble = BubbleMode.fromKey(bubbleKey) ?: BubbleMode.CHINESE

        emitLog("🔗 ComfyUI 地址：${comfyUrl.take(40)}…")
        emitLog("🔑 API Key：${if (comfyApiKey.isNotBlank()) "已配置（${comfyApiKey.length} 位）" else "未配置（将以匿名请求）"}")

        val client = ComfyUIClient(comfyUrl, comfyApiKey)

        val combinedPrompt = MangaPrompts.buildMangaPrompt(
            style = style,
            bubbleMode = bubble,
            userPrompt = if (storyPrompt.isNotBlank()) storyPrompt else userPrompt,
            isColor = isColor,
            panelCount = panelCount,
        )
        val negPrompt = MangaPrompts.buildNegativePrompt(style, isColor)
        emitLog("📝 Prompt 生成完毕（${combinedPrompt.length} 字符）")

        val outputBytes: ByteArray = if (inputPaths.isEmpty()) {
            emitLog("🎨 文生图模式，提交 ComfyUI 工作流…")
            val workflow = ComfyWorkflows.textToManga(combinedPrompt, negPrompt)
            val promptId = try {
                client.submitWorkflow(workflow)
            } catch (e: Exception) {
                return failure("提交工作流失败：${e.message}")
            }
            emitLog("✅ 工作流已提交，promptId=$promptId")
            emitLog("⏳ 等待 ComfyUI 出图（最长 5 分钟）…")
            when (val result = client.pollUntilDone(promptId, 300_000)) {
                is ComfyResult.Success -> {
                    val filename = result.images.firstOrNull()
                        ?: return failure("未返回图片文件名")
                    emitLog("🖼 出图完成，下载：$filename")
                    try {
                        client.downloadImage(filename)
                    } catch (e: Exception) {
                        return failure("下载图片失败：${e.message}")
                    }
                }
                is ComfyResult.Failure -> return failure(result.message)
            }
        } else {
            emitLog("🗜 压缩 ${inputPaths.size} 张参考图…")
            val compressedList = inputPaths.mapNotNull { path ->
                ImageCompression.compressFileToJpeg(path)
            }
            if (compressedList.isEmpty()) return failure("参考图压缩失败，请检查图片格式")

            emitLog("⬆️ 上传参考图到 ComfyUI…")
            val uploadedNames = try {
                compressedList.mapIndexed { idx, bytes ->
                    client.uploadImage(bytes, "ref_${idx}.jpg").also { name ->
                        emitLog("  ↑ 图 ${idx + 1} → $name")
                    }
                }
            } catch (e: Exception) {
                return failure("上传图片失败：${e.message}")
            }

            emitLog("🎨 图生图模式，提交工作流…")
            val workflow = when {
                uploadedNames.size >= 2 -> ComfyWorkflows.multiImageToManga(uploadedNames, combinedPrompt, negPrompt)
                else -> ComfyWorkflows.imageToManga(uploadedNames.first(), combinedPrompt, negPrompt)
            }
            val promptId = try {
                client.submitWorkflow(workflow)
            } catch (e: Exception) {
                return failure("提交工作流失败：${e.message}")
            }
            emitLog("✅ 工作流已提交，promptId=$promptId")
            emitLog("⏳ 等待 ComfyUI 出图（最长 5 分钟）…")
            when (val result = client.pollUntilDone(promptId, 300_000)) {
                is ComfyResult.Success -> {
                    val filename = result.images.firstOrNull()
                        ?: return failure("未返回图片文件名")
                    emitLog("🖼 出图完成，下载：$filename")
                    try {
                        client.downloadImage(filename)
                    } catch (e: Exception) {
                        return failure("下载图片失败：${e.message}")
                    }
                }
                is ComfyResult.Failure -> return failure(result.message)
            }
        }

        emitLog("💾 保存到本地数据库…")
        val item = if (projectId != null) {
            ServiceLocator.repository.saveGeneratedForProject(
                projectId = projectId,
                style = style,
                userPrompt = userPrompt,
                inputImagePaths = inputPaths.filter { File(it).exists() },
                outputBytes = listOf(outputBytes),
                storyScriptJson = null,
            )
        } else {
            ServiceLocator.repository.saveGenerated(
                style = style,
                userPrompt = userPrompt,
                inputImagePaths = inputPaths.filter { File(it).exists() },
                outputBytes = listOf(outputBytes),
            )
        }

        emitLog("✅ 完成！已保存到历史（id=${item.id.take(8)}）")
        NotificationHelper.showResult(
            applicationContext,
            success = true,
            message = "「${style.displayName}」已保存到历史",
        )
        return Result.success(workDataOf(KEY_RESULT_ITEM_ID to item.id))
    }

    private suspend fun emitLog(text: String) {
        android.util.Log.d("MangaWorker", text)
        setProgress(workDataOf(KEY_PROGRESS to text))
        setForegroundCompat(text)
    }

    private suspend fun setForegroundCompat(text: String) {
        val notif = NotificationHelper.progressNotification(applicationContext, text).build()
        val info = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ForegroundInfo(NotificationHelper.NOTIF_ID_PROGRESS, notif, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(NotificationHelper.NOTIF_ID_PROGRESS, notif)
        }
        runCatching { setForeground(info) }
    }

    private fun failure(message: String): Result {
        android.util.Log.e("MangaWorker", "FAIL: $message")
        NotificationHelper.showResult(applicationContext, success = false, message = message)
        return Result.failure(workDataOf(KEY_ERROR_MESSAGE to message))
    }

    companion object {
        const val UNIQUE_NAME = "manga_generation"
        const val KEY_STYLE = "style"
        const val KEY_BUBBLE = "bubble"
        const val KEY_USER_PROMPT = "user_prompt"
        const val KEY_STORY_PROMPT = "story_prompt"
        const val KEY_IS_COLOR = "is_color"
        const val KEY_INPUT_PATHS = "input_paths"
        const val KEY_PANEL_COUNT = "panel_count"
        const val KEY_PROJECT_ID = "project_id"
        const val KEY_COMFY_URL = "comfy_url"
        const val KEY_COMFY_API_KEY = "comfy_api_key"
        const val KEY_ERROR_MESSAGE = "error_message"
        const val KEY_RESULT_ITEM_ID = "result_item_id"
        const val KEY_PROGRESS = "progress"

        fun enqueue(
            context: Context,
            style: MangaStyle,
            bubble: BubbleMode,
            userPrompt: String,
            storyPrompt: String = "",
            isColor: Boolean,
            inputPaths: List<String>,
            panelCount: Int = 1,
            projectId: String? = null,
            comfyUrl: String,
            comfyApiKey: String,
        ) {
            val data = Data.Builder()
                .putString(KEY_STYLE, style.key)
                .putString(KEY_BUBBLE, bubble.key)
                .putString(KEY_USER_PROMPT, userPrompt)
                .putString(KEY_STORY_PROMPT, storyPrompt)
                .putBoolean(KEY_IS_COLOR, isColor)
                .putStringArray(KEY_INPUT_PATHS, inputPaths.toTypedArray())
                .putInt(KEY_PANEL_COUNT, panelCount)
                .putString(KEY_COMFY_URL, comfyUrl)
                .putString(KEY_COMFY_API_KEY, comfyApiKey)
                .apply { projectId?.let { putString(KEY_PROJECT_ID, it) } }
                .build()
            val request = OneTimeWorkRequestBuilder<MangaGenerationWorker>()
                .setInputData(data)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                UNIQUE_NAME,
                androidx.work.ExistingWorkPolicy.APPEND_OR_REPLACE,
                request,
            )
        }
    }
}
