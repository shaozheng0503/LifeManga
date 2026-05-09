package com.lifemanga.android.work

import android.content.Context
import android.content.pm.ServiceInfo
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.lifemanga.android.ServiceLocator
import com.lifemanga.android.data.CharacterArtStyle
import com.lifemanga.android.network.ComfyUIClient
import com.lifemanga.android.network.ComfyResult
import com.lifemanga.android.network.ComfyWorkflows
import com.lifemanga.android.network.ImageCompression
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

class CharacterGenerationWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        NotificationHelper.ensureChannels(applicationContext)
        setForegroundCompat("正在初始化角色生成…")

        val characterId = inputData.getString(INPUT_KEY_CHARACTER_ID)
            ?: return@withContext failWith("缺少 characterId")

        val settings = ServiceLocator.appSettings.flow.first()
        val comfyUrl = inputData.getString(INPUT_KEY_COMFY_URL) ?: settings.comfyUiUrl
        val comfyApiKey = inputData.getString(INPUT_KEY_COMFY_API_KEY)
            ?: ServiceLocator.secureStore.comfyApiKey.orEmpty()

        val character = ServiceLocator.characterRepository.getById(characterId)
            ?: return@withContext failWith("找不到角色 $characterId")

        val client = ComfyUIClient(comfyUrl, comfyApiKey)
        val artStyle = CharacterArtStyle.fromKey(character.artStyle)
        val styleTag = artStyle.promptTag

        val views = listOf(
            "正面立绘" to "full-body front view character sheet, ${character.name}, ${character.bio}, $styleTag, clean lineart, white background",
            "侧面" to "side profile full-body character illustration, ${character.name}, ${character.bio}, $styleTag, clean lineart",
            "表情包" to "character emotion expression sheet, ${character.name}, multiple facial expressions (happy, sad, angry, surprised, embarrassed), $styleTag",
            "全身服装" to "full-body outfit and costume detail illustration, ${character.name}, ${character.bio}, $styleTag, fashion reference sheet",
        )

        for ((label, prompt) in views) {
            setForegroundCompat("生成「$label」…")
            try {
                val uploadedFilename: String? = character.sourcePhotoPath?.let { path ->
                    val bytes = ImageCompression.compressFileToJpeg(path)
                    if (bytes != null) {
                        runCatching { client.uploadImage(bytes, "ref_${character.id}.jpg") }.getOrNull()
                    } else null
                }

                val workflow = if (uploadedFilename != null) {
                    ComfyWorkflows.imageToManga(
                        uploadedFilename = uploadedFilename,
                        prompt = prompt,
                        negPrompt = "blurry, low quality, deformed, extra limbs",
                    )
                } else {
                    ComfyWorkflows.textToManga(
                        prompt = prompt,
                        negPrompt = "blurry, low quality, deformed, extra limbs",
                    )
                }

                val promptId = client.submitWorkflow(workflow)
                val result = client.pollUntilDone(promptId, timeoutMs = 300_000)

                when (result) {
                    is ComfyResult.Success -> {
                        val filename = result.images.firstOrNull()
                            ?: continue
                        val imageBytes = client.downloadImage(filename)
                        ServiceLocator.characterRepository.saveViewFromBytes(
                            characterId = characterId,
                            label = label,
                            bytes = imageBytes,
                        )
                    }
                    is ComfyResult.Failure -> {
                        // Log but continue generating remaining views
                        android.util.Log.w("CharacterGen", "Failed to generate '$label': ${result.message}")
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("CharacterGen", "Exception generating '$label'", e)
                // Continue with remaining views instead of aborting entirely
            }
        }

        NotificationHelper.showResult(
            applicationContext,
            success = true,
            message = "「${character.name}」角色视图已生成",
        )
        Result.success()
    }

    private suspend fun setForegroundCompat(text: String) {
        val notif = NotificationHelper.progressNotification(applicationContext, text).build()
        val info = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ForegroundInfo(
                NotificationHelper.NOTIF_ID_PROGRESS + 100,
                notif,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
            )
        } else {
            ForegroundInfo(NotificationHelper.NOTIF_ID_PROGRESS + 100, notif)
        }
        runCatching { setForeground(info) }
    }

    private fun failWith(message: String): Result {
        NotificationHelper.showResult(applicationContext, success = false, message = message)
        return Result.failure(workDataOf("error" to message))
    }

    companion object {
        const val INPUT_KEY_CHARACTER_ID = "character_id"
        const val INPUT_KEY_COMFY_URL = "comfy_url"
        const val INPUT_KEY_COMFY_API_KEY = "comfy_api_key"

        fun enqueue(context: Context, characterId: String) {
            val request = OneTimeWorkRequestBuilder<CharacterGenerationWorker>()
                .setInputData(workDataOf(INPUT_KEY_CHARACTER_ID to characterId))
                .build()
            WorkManager.getInstance(context).enqueue(request)
        }
    }
}
