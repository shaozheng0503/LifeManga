package com.lifemanga.android.network

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

private val JSON_MEDIA_QWEN = "application/json; charset=utf-8".toMediaType()

private const val QWEN_MODEL_ID = "Qwen/Qwen3.5-9B"

class QwenClient(
    private val baseUrl: String = "https://deployment-452-lwgy9ka4-8000.550w.link",
) {

    private val moshi: Moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    private val requestAdapter = moshi.adapter(ChatCompletionRequest::class.java)
    private val responseAdapter = moshi.adapter(ChatCompletionResponse::class.java)

    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .retryOnConnectionFailure(false)
        .build()

    private val base: String get() = baseUrl.trimEnd('/')

    /**
     * Ask Qwen to generate a structured story script for the given user prompt.
     * Returns a [StoryScript] parsed from the model's JSON response.
     */
    @Throws(Exception::class)
    fun generateStoryScript(
        userPrompt: String,
        imageDescriptions: List<String>,
        style: String,
        panelCount: Int,
    ): StoryScript {
        val systemPrompt = MangaPrompts.buildStorySystemPrompt(panelCount)

        val userContent = buildString {
            append("Manga art style: $style\n")
            append("Number of panels: $panelCount\n")
            if (imageDescriptions.isNotEmpty()) {
                append("Reference image descriptions:\n")
                imageDescriptions.forEachIndexed { idx, desc ->
                    append("  Image ${idx + 1}: $desc\n")
                }
            }
            append("\nUser request:\n")
            append(userPrompt.trim())
        }

        val messages = listOf(
            ChatMessage(role = "system", content = systemPrompt),
            ChatMessage(role = "user", content = userContent),
        )

        val request = ChatCompletionRequest(
            model = QWEN_MODEL_ID,
            messages = messages,
            temperature = 0.75,
            max_tokens = 2048,
        )

        val responseText = executeChat(requestAdapter.toJson(request))
        return parseStoryScript(responseText, panelCount)
    }

    /**
     * Describe an image using Qwen's vision capability.
     * [imageBase64] must be a base64-encoded PNG or JPEG (without the data URI prefix).
     */
    @Throws(Exception::class)
    fun describeImage(imageBase64: String): String {
        // Build a manual JSON payload because VisionContentPart has a nested union type
        // that Moshi doesn't handle well without custom adapters.
        val payload = buildString {
            append("""{"model":"$QWEN_MODEL_ID","messages":[{"role":"user","content":[""")
            append("""{"type":"image_url","image_url":{"url":"data:image/jpeg;base64,$imageBase64"}},""")
            append("""{"type":"text","text":"Describe this image in detail for use as a manga panel reference. """)
            append("""Include character appearances, expressions, poses, setting, mood, and any notable visual elements. """)
            append("""Keep the description under 150 words."}""")
            append("""]}],"max_tokens":256,"temperature":0.3}""")
        }

        return executeChat(payload).trim()
    }

    // ---------------------------------------------------------------------------
    // Private helpers
    // ---------------------------------------------------------------------------

    private fun executeChat(jsonPayload: String): String {
        val body = jsonPayload.toRequestBody(JSON_MEDIA_QWEN)
        val request = Request.Builder()
            .url("$base/v1/chat/completions")
            .post(body)
            .build()

        return httpClient.newCall(request).execute().use { response ->
            val bodyStr = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                val errMsg = runCatching {
                    JSONObject(bodyStr).optJSONObject("error")?.optString("message")
                }.getOrNull() ?: bodyStr.take(300)
                throw IOException("Qwen API error (HTTP ${response.code}): $errMsg")
            }
            val parsed = runCatching { responseAdapter.fromJson(bodyStr) }.getOrNull()
                ?: throw IOException("Failed to parse Qwen response: ${bodyStr.take(300)}")
            parsed.error?.let { err ->
                throw IOException("Qwen error: ${err.message}")
            }
            parsed.choices?.firstOrNull()?.message?.content?.trim()
                ?: throw IOException("Qwen returned no content in choices")
        }
    }

    private fun parseStoryScript(raw: String, panelCount: Int): StoryScript {
        // Strip markdown code fences if present
        val cleaned = raw
            .removePrefix("```json").removePrefix("```")
            .removeSuffix("```")
            .trim()

        return try {
            val json = JSONObject(cleaned)
            val title = json.optString("title", "Untitled")
            val synopsis = json.optString("synopsis", "")
            val panelsArray = json.optJSONArray("panels")
            val panels = mutableListOf<PanelScript>()
            if (panelsArray != null) {
                for (i in 0 until panelsArray.length()) {
                    val p = panelsArray.optJSONObject(i) ?: continue
                    panels.add(
                        PanelScript(
                            description = p.optString("description", ""),
                            dialogue = p.optString("dialogue", ""),
                            narration = p.optString("narration", ""),
                        )
                    )
                }
            }
            // Pad with empty panels if Qwen returned fewer than requested
            while (panels.size < panelCount) {
                panels.add(PanelScript(description = ""))
            }
            StoryScript(title = title, synopsis = synopsis, panels = panels)
        } catch (e: Exception) {
            // Fallback: wrap the raw text as a single panel description
            StoryScript(
                title = "Story",
                synopsis = raw.take(200),
                panels = List(panelCount) { PanelScript(description = raw.take(300)) },
            )
        }
    }
}
