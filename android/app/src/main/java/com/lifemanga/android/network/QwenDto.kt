package com.lifemanga.android.network

import com.squareup.moshi.JsonClass

// ---------------------------------------------------------------------------
// Story / panel data models
// ---------------------------------------------------------------------------

data class StoryScript(
    val title: String,
    val synopsis: String,
    val panels: List<PanelScript>,
)

data class PanelScript(
    val description: String,
    val dialogue: String = "",
    val narration: String = "",
)

// ---------------------------------------------------------------------------
// OpenAI-compatible chat completions DTOs (used by QwenClient)
// ---------------------------------------------------------------------------

@JsonClass(generateAdapter = true)
data class ChatMessage(
    val role: String,
    val content: String,
)

@JsonClass(generateAdapter = true)
data class ChatCompletionRequest(
    val model: String,
    val messages: List<ChatMessage>,
    val temperature: Double = 0.7,
    val max_tokens: Int = 2048,
    val stream: Boolean = false,
)

@JsonClass(generateAdapter = true)
data class ChatCompletionResponse(
    val id: String?,
    val choices: List<ChatChoice>?,
    val error: ApiError?,
)

@JsonClass(generateAdapter = true)
data class ChatChoice(
    val index: Int?,
    val message: ChatMessage?,
    val finish_reason: String?,
)

@JsonClass(generateAdapter = true)
data class VisionImageUrl(
    val url: String,
)

@JsonClass(generateAdapter = true)
data class VisionContentPart(
    val type: String,
    val text: String? = null,
    val image_url: VisionImageUrl? = null,
)
