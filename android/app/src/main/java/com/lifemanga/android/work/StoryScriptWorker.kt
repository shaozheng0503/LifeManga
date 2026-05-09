package com.lifemanga.android.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.lifemanga.android.ServiceLocator
import com.lifemanga.android.network.QwenClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONObject

class StoryScriptWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val prompt = inputData.getString(INPUT_KEY_PROMPT).orEmpty()
        val style = inputData.getString(INPUT_KEY_STYLE).orEmpty()
        val panelCount = inputData.getInt(INPUT_KEY_PANEL_COUNT, 4)
        val imageDescriptions = inputData.getStringArray(INPUT_KEY_IMAGE_DESCRIPTIONS)
            ?.toList().orEmpty()

        if (prompt.isBlank()) {
            return@withContext Result.failure(workDataOf(OUTPUT_KEY_ERROR to "缺少故事提示词"))
        }

        val settings = ServiceLocator.appSettings.flow.first()
        val qwenUrl = settings.qwenUrl

        val client = QwenClient(qwenUrl)
        return@withContext try {
            val script = client.generateStoryScript(
                userPrompt = prompt,
                imageDescriptions = imageDescriptions,
                style = style.ifBlank { "manga" },
                panelCount = panelCount,
            )

            // Serialize the story script to JSON for storage
            val json = JSONObject().apply {
                put("title", script.title)
                put("synopsis", script.synopsis)
                val panelsArr = org.json.JSONArray()
                script.panels.forEach { panel ->
                    panelsArr.put(JSONObject().apply {
                        put("description", panel.description)
                        put("dialogue", panel.dialogue)
                        put("narration", panel.narration)
                    })
                }
                put("panels", panelsArr)
            }.toString()

            Result.success(workDataOf(OUTPUT_KEY_SCRIPT_JSON to json))
        } catch (e: Exception) {
            android.util.Log.e("StoryScriptWorker", "Error generating story script", e)
            Result.failure(workDataOf(OUTPUT_KEY_ERROR to (e.message ?: "未知错误")))
        }
    }

    companion object {
        const val INPUT_KEY_PROMPT = "prompt"
        const val INPUT_KEY_STYLE = "style"
        const val INPUT_KEY_PANEL_COUNT = "panel_count"
        const val INPUT_KEY_IMAGE_DESCRIPTIONS = "image_descriptions"
        const val OUTPUT_KEY_SCRIPT_JSON = "script_json"
        const val OUTPUT_KEY_ERROR = "error"

        fun enqueue(
            context: Context,
            prompt: String,
            style: String,
            panelCount: Int,
            imageDescriptions: List<String> = emptyList(),
        ) {
            val request = OneTimeWorkRequestBuilder<StoryScriptWorker>()
                .setInputData(
                    workDataOf(
                        INPUT_KEY_PROMPT to prompt,
                        INPUT_KEY_STYLE to style,
                        INPUT_KEY_PANEL_COUNT to panelCount,
                        INPUT_KEY_IMAGE_DESCRIPTIONS to imageDescriptions.toTypedArray(),
                    )
                )
                .build()
            WorkManager.getInstance(context).enqueue(request)
        }
    }
}
