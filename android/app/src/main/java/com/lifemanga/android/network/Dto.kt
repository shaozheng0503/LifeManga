package com.lifemanga.android.network

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class ImagesEditResponse(
    val data: List<ImageDataEntry>?,
    val error: ApiError?,
)

@JsonClass(generateAdapter = true)
data class ImageDataEntry(
    val b64_json: String?,
    val url: String?,
)

@JsonClass(generateAdapter = true)
data class ApiError(
    val message: String?,
    val type: String?,
    val code: String?,
)

@JsonClass(generateAdapter = true)
data class ApiErrorEnvelope(
    val error: ApiError?,
)

@JsonClass(generateAdapter = true)
data class ImagesGenerationsRequest(
    val prompt: String,
    val n: Int = 1,
    val size: String = "1024x1024",
    val quality: String = "medium",
    val output_format: String = "png",
)
