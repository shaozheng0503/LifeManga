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
import com.lifemanga.android.data.EndpointConfig
import com.lifemanga.android.data.EndpointType
import com.lifemanga.android.data.MangaStyle
import com.lifemanga.android.network.ImageCompression
import com.lifemanga.android.network.OpenAIException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class MangaGenerationWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        NotificationHelper.ensureChannels(applicationContext)
        setForegroundCompat("正在压缩图片…")

        val styleKey = inputData.getString(KEY_STYLE) ?: return@withContext failure("缺少风格参数")
        val bubbleKey = inputData.getString(KEY_BUBBLE) ?: BubbleMode.CHINESE.key
        val userPrompt = inputData.getString(KEY_USER_PROMPT).orEmpty()
        val isColor = inputData.getBoolean(KEY_IS_COLOR, true)
        val inputPaths = inputData.getStringArray(KEY_INPUT_PATHS)?.toList().orEmpty()
        val endpointKey = inputData.getString(KEY_ENDPOINT_TYPE) ?: EndpointType.OPENAI.key
        val azureEndpoint = inputData.getString(KEY_AZURE_ENDPOINT).orEmpty()
        val azureDeployment = inputData.getString(KEY_AZURE_DEPLOYMENT).orEmpty()
        val azureApiVersion = inputData.getString(KEY_AZURE_API_VERSION) ?: "2024-02-01"

        if (inputPaths.isEmpty()) return@withContext failure("未提供输入图")

        val style = MangaStyle.fromKey(styleKey) ?: return@withContext failure("未知风格 $styleKey")
        val bubble = BubbleMode.fromKey(bubbleKey) ?: BubbleMode.CHINESE
        val endpointType = EndpointType.fromKey(endpointKey) ?: EndpointType.OPENAI
        val config = EndpointConfig(endpointType, azureEndpoint, azureDeployment, azureApiVersion)

        val inputBytes = inputPaths.mapNotNull { ImageCompression.compressFileToJpeg(it) }
        if (inputBytes.isEmpty()) return@withContext failure("输入图压缩失败")

        setForegroundCompat("等待图像生成（1~3 分钟，锁屏不影响）")

        val outputBytes = try {
            ServiceLocator.openAIClient.generateSingleImage(
                config = config,
                style = style,
                bubbleMode = bubble,
                userPrompt = userPrompt,
                isColor = isColor,
                inputImageBytes = inputBytes,
            )
        } catch (e: OpenAIException) {
            NotificationHelper.showResult(applicationContext, success = false, message = e.message ?: "未知错误")
            return@withContext Result.failure(workDataOf(KEY_ERROR_MESSAGE to (e.message ?: "未知错误")))
        } catch (e: Exception) {
            NotificationHelper.showResult(applicationContext, success = false, message = e.message ?: "未知错误")
            return@withContext Result.failure(workDataOf(KEY_ERROR_MESSAGE to (e.message ?: "未知错误")))
        }

        setForegroundCompat("保存到本地…")
        val item = ServiceLocator.repository.saveGenerated(
            style = style,
            userPrompt = userPrompt,
            inputImagePaths = inputPaths.filter { File(it).exists() },
            outputBytes = listOf(outputBytes),
        )

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
        const val KEY_IS_COLOR = "is_color"
        const val KEY_INPUT_PATHS = "input_paths"
        const val KEY_ENDPOINT_TYPE = "endpoint_type"
        const val KEY_AZURE_ENDPOINT = "azure_endpoint"
        const val KEY_AZURE_DEPLOYMENT = "azure_deployment"
        const val KEY_AZURE_API_VERSION = "azure_api_version"
        const val KEY_ERROR_MESSAGE = "error_message"
        const val KEY_RESULT_ITEM_ID = "result_item_id"

        fun enqueue(
            context: Context,
            style: MangaStyle,
            bubble: BubbleMode,
            userPrompt: String,
            isColor: Boolean,
            inputPaths: List<String>,
            endpoint: EndpointConfig,
        ) {
            val data = Data.Builder()
                .putString(KEY_STYLE, style.key)
                .putString(KEY_BUBBLE, bubble.key)
                .putString(KEY_USER_PROMPT, userPrompt)
                .putBoolean(KEY_IS_COLOR, isColor)
                .putStringArray(KEY_INPUT_PATHS, inputPaths.toTypedArray())
                .putString(KEY_ENDPOINT_TYPE, endpoint.type.key)
                .putString(KEY_AZURE_ENDPOINT, endpoint.azureEndpoint)
                .putString(KEY_AZURE_DEPLOYMENT, endpoint.azureDeployment)
                .putString(KEY_AZURE_API_VERSION, endpoint.azureApiVersion)
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
