package com.lifemanga.android.network

import android.util.Base64
import com.lifemanga.android.data.BubbleMode
import com.lifemanga.android.data.EndpointConfig
import com.lifemanga.android.data.EndpointType
import com.lifemanga.android.data.MangaStyle
import com.lifemanga.android.data.SecureStore
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

class OpenAIClient(private val secureStore: SecureStore) {

    private val moshi: Moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
    private val responseAdapter = moshi.adapter(ImagesEditResponse::class.java)
    private val errorAdapter = moshi.adapter(ApiErrorEnvelope::class.java)

    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(300, TimeUnit.SECONDS)
        .writeTimeout(600, TimeUnit.SECONDS)
        .retryOnConnectionFailure(false)
        .build()

    /**
     * 调用 gpt-image-2 的 images/edits 接口（支持 OpenAI 直连或 Azure 部署）。返回 PNG 字节。
     * 永不自动重试 —— 长 GPU 任务，重试可能重复扣费。
     */
    @Throws(OpenAIException::class)
    fun generateSingleImage(
        config: EndpointConfig,
        style: MangaStyle,
        bubbleMode: BubbleMode,
        userPrompt: String,
        isColor: Boolean,
        inputImageBytes: List<ByteArray>,
        size: String = "1024x1024",
        quality: String = "medium",
    ): ByteArray {
        val apiKey = secureStore.apiKey?.takeIf { it.isNotBlank() }
            ?: throw OpenAIException("还没填 API Key，去设置页填一下")

        require(inputImageBytes.isNotEmpty()) { "至少需要一张参考图" }

        if (config.isAzure && !config.isAzureReady) {
            throw OpenAIException("Azure 模式需要填 endpoint / deployment / api-version")
        }

        val url = when (config.type) {
            EndpointType.OPENAI -> "https://api.openai.com/v1/images/edits"
            EndpointType.AZURE -> buildString {
                append(config.azureEndpoint.trimEnd('/'))
                append("/openai/deployments/")
                append(config.azureDeployment)
                append("/images/edits?api-version=")
                append(config.azureApiVersion)
            }
        }

        val combinedPrompt = buildPrompt(style, bubbleMode, userPrompt, isColor)

        val builder = MultipartBody.Builder().setType(MultipartBody.FORM)
            .addFormDataPart("prompt", combinedPrompt)
            .addFormDataPart("n", "1")
            .addFormDataPart("size", size)
            .addFormDataPart("quality", quality)
            .addFormDataPart("output_format", "png")

        if (config.type == EndpointType.OPENAI) {
            builder.addFormDataPart("model", "gpt-image-2")
        }

        inputImageBytes.forEachIndexed { idx, bytes ->
            builder.addFormDataPart(
                "image[]",
                "input_$idx.jpg",
                bytes.toRequestBody("image/jpeg".toMediaType()),
            )
        }

        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $apiKey")
            .apply {
                if (config.type == EndpointType.AZURE) {
                    header("api-key", apiKey)
                }
            }
            .post(builder.build())
            .build()

        val response = try {
            httpClient.newCall(request).execute()
        } catch (e: IOException) {
            throw OpenAIException("网络异常：${e.message ?: e.javaClass.simpleName}", cause = e)
        }

        response.use { resp ->
            val bodyString = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                val parsed = runCatching { errorAdapter.fromJson(bodyString) }.getOrNull()
                val msg = parsed?.error?.message
                    ?: bodyString.takeIf { it.isNotBlank() }
                    ?: "HTTP ${resp.code}"
                val isSafety = msg.contains("safety", ignoreCase = true) ||
                        msg.contains("content_policy", ignoreCase = true)
                throw OpenAIException(prettyError(resp.code, msg), isSafetyBlocked = isSafety)
            }
            val data = runCatching { responseAdapter.fromJson(bodyString) }.getOrNull()
            val first = data?.data?.firstOrNull()
                ?: throw OpenAIException("返回数据格式异常")
            val b64 = first.b64_json
            if (!b64.isNullOrBlank()) {
                return Base64.decode(b64, Base64.DEFAULT)
            }
            val urlOut = first.url
            if (!urlOut.isNullOrBlank()) {
                return downloadUrl(urlOut)
            }
            throw OpenAIException("返回里既没 b64_json 也没 url")
        }
    }

    private fun downloadUrl(url: String): ByteArray {
        val req = Request.Builder().url(url).get().build()
        return httpClient.newCall(req).execute().use { r ->
            if (!r.isSuccessful) throw OpenAIException("下载图片失败 HTTP ${r.code}")
            r.body?.bytes() ?: throw OpenAIException("下载图片返回空")
        }
    }

    private fun buildPrompt(
        style: MangaStyle,
        bubbleMode: BubbleMode,
        userPrompt: String,
        isColor: Boolean,
    ): String = buildString {
        append(style.effectivePrompt(isColor))
        append("\n\nSPEECH BUBBLE DIRECTIVE:\n")
        append(bubbleMode.directive)
        if (userPrompt.isNotBlank()) {
            append("\n\nADDITIONAL USER REQUEST:\n")
            append(userPrompt.trim())
        }
        append(
            "\n\nIMPORTANT: Output a single finished manga page. Compose the panels yourself. " +
                    "Use the provided reference photo(s) as inspiration for character likeness, scene, " +
                    "and atmosphere only — do not copy them as photos.",
        )
    }

    private fun prettyError(code: Int, raw: String): String = when (code) {
        401, 403 -> "API Key 无效或权限不足，去设置页检查"
        404 -> "Endpoint 或 deployment 不存在：$raw"
        429 -> "限流，等一会再试"
        400 -> if (raw.contains("safety", ignoreCase = true) || raw.contains("content_policy", ignoreCase = true)) {
            "安全策略拦截：换图或调整描述再试"
        } else {
            "请求被拒绝：$raw"
        }
        500, 502, 503, 504 -> "服务暂不可用：$raw"
        else -> "生成失败 (HTTP $code)：$raw"
    }
}

class OpenAIException(
    message: String,
    val isSafetyBlocked: Boolean = false,
    cause: Throwable? = null,
) : Exception(message, cause)
