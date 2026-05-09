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
        NotificationHelper.ensureChannels(applicationContext)
        setForegroundCompat("正在准备生成…")

        val styleKey = inputData.getString(KEY_STYLE) ?: return@withContext failure("缺少风格参数")
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

        val style = MangaStyle.fromKey(styleKey) ?: return@withContext failure("未知风格 $styleKey")
        val bubble = BubbleMode.fromKey(bubbleKey) ?: BubbleMode.CHINESE

        val client = ComfyUIClient(comfyUrl, comfyApiKey)

        val combinedPrompt = MangaPrompts.buildMangaPrompt(
            style = style,
            bubbleMode = bubble,
            userPrompt = if (storyPrompt.isNotBlank()) storyPrompt else userPrompt,
            isColor = isColor,
            panelCount = panelCount,
        )
        val negPrompt = MangaPrompts.buildNegativePrompt(style, isColor)

        setForegroundCompat("上传参考图…")

        val outputBytes: ByteArray = try {
            if (inputPaths.isEmpty()) {
                // Text-to-image
                setForegroundCompat("文生图中（1~3 分钟）…")
                val workflow = ComfyWorkflows.textToManga(combinedPrompt, negPrompt)
                val promptId = client.submitWorkflow(workflow)
                when (val result = client.pollUntilDone(promptId, 300_000)) {
                    is ComfyResult.Success -> {
                        val filename = result.images.firstOrNull()
                            ?: return@withContext failure("未返回图片文件名")
                        client.downloadImage(filename)
                    }
                    is ComfyResult.Failure -> return@withContext failure(result.message)
                }
            } else {
                // Image-to-image with reference photos
                setForegroundCompat("压缩参考图…")
                val compressedList = inputPaths.mapNotNull { path ->
                    ImageCompression.compressFileToJpeg(path)
                }
                if (compressedList.isEmpty()) return@withContext failure("参考图压缩失败")

                setForegroundCompat("上传参考图到 ComfyUI…")
                val uploadedNames = compressedList.mapIndexed { idx, bytes ->
                    client.uploadImage(bytes, "ref_${idx}.jpg")
                }

                setForegroundCompat("图生图中（1~3 分钟）…")
                val workflow = when {
                    uploadedNames.size >= 2 -> ComfyWorkflows.multiImageToManga(uploadedNames, combinedPrompt, negPrompt)
                    else -> ComfyWorkflows.imageToManga(uploadedNames.first(), combinedPrompt, negPrompt)
                }
                val promptId = client.submitWorkflow(workflow)
                when (val result = client.pollUntilDone(promptId, 300_000)) {
                    is ComfyResult.Success -> {
                        val filename = result.images.firstOrNull()
                            ?: return@withContext failure("未返回图片文件名")
                        client.downloadImage(filename)
                    }
                    is ComfyResult.Failure -> return@withContext failure(result.message)
                }
            }
        } catch (e: Exception) {
            val msg = e.message ?: e.javaClass.simpleName
            NotificationHelper.showResult(applicationContext, success = false, message = msg)
            return@withContext Result.failure(workDataOf(KEY_ERROR_MESSAGE to msg))
        }

        setForegroundCompat("保存到本地…")
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

        NotificationHelper.showResult(
            applicationContext,
            success = true,
            message = "「${style.displayName}」已保存到历史",
        )
        Result.success(workDataOf(KEY_RESULT_ITEM_ID to item.id))
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
