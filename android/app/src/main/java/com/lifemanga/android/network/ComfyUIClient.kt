package com.lifemanga.android.network

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

private val JSON_MEDIA_COMFY = "application/json; charset=utf-8".toMediaType()

sealed class ComfyResult {
    data class Success(val images: List<String>) : ComfyResult()
    data class Failure(val message: String) : ComfyResult()
}

class ComfyUIClient(private val baseUrl: String, private val apiKey: String = "") {

    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(120, TimeUnit.SECONDS)
        .retryOnConnectionFailure(false)
        .build()

    private val base: String get() = baseUrl.trimEnd('/')

    /**
     * Upload an image to ComfyUI's /upload/image endpoint.
     * Returns the server-assigned filename.
     */
    @Throws(Exception::class)
    fun uploadImage(bytes: ByteArray, filename: String): String {
        val body = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart(
                "image",
                filename,
                bytes.toRequestBody("image/png".toMediaType()),
            )
            .addFormDataPart("overwrite", "true")
            .build()

        val request = Request.Builder()
            .url("$base/upload/image")
            .post(body)
            .build()

        return httpClient.newCall(request).execute().use { response ->
            val bodyStr = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw IOException("ComfyUI upload failed (HTTP ${response.code}): $bodyStr")
            }
            val json = JSONObject(bodyStr)
            json.optString("name").takeIf { it.isNotBlank() }
                ?: throw IOException("ComfyUI upload response missing 'name' field: $bodyStr")
        }
    }

    /**
     * Submit a workflow to ComfyUI's /prompt endpoint.
     * Returns the prompt_id.
     */
    @Throws(Exception::class)
    fun submitWorkflow(workflow: Map<String, Any>): String {
        val promptJson = JSONObject()
        promptJson.put("prompt", mapToJsonObject(workflow))
        if (apiKey.isNotBlank()) {
            val extra = JSONObject()
            extra.put("api_key_comfy_org", apiKey)
            extra.put("auth_token_comfy_org", apiKey)
            promptJson.put("extra_data", extra)
        }

        val body = promptJson.toString().toRequestBody(JSON_MEDIA_COMFY)
        val request = Request.Builder()
            .url("$base/prompt")
            .post(body)
            .build()

        return httpClient.newCall(request).execute().use { response ->
            val bodyStr = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw IOException("ComfyUI submit failed (HTTP ${response.code}): $bodyStr")
            }
            val json = JSONObject(bodyStr)
            json.optString("prompt_id").takeIf { it.isNotBlank() }
                ?: throw IOException("ComfyUI response missing 'prompt_id': $bodyStr")
        }
    }

    /**
     * Poll /history/{promptId} every 3 seconds until status.completed == true or timeout.
     * Returns a ComfyResult with output image filenames.
     */
    @Throws(Exception::class)
    fun pollUntilDone(promptId: String, timeoutMs: Long = 300_000): ComfyResult {
        val deadline = System.currentTimeMillis() + timeoutMs
        val pollClient = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()

        while (System.currentTimeMillis() < deadline) {
            val request = Request.Builder()
                .url("$base/history/$promptId")
                .get()
                .build()

            val result = pollClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use null
                val bodyStr = response.body?.string().orEmpty()
                if (bodyStr.isBlank() || bodyStr == "{}") return@use null

                val root = JSONObject(bodyStr)
                val entry = root.optJSONObject(promptId) ?: return@use null

                val status = entry.optJSONObject("status") ?: return@use null
                val completed = status.optBoolean("completed", false)
                if (!completed) {
                    // Check for execution error in messages
                    val messages = status.optJSONArray("messages")
                    if (messages != null) {
                        for (i in 0 until messages.length()) {
                            val msg = messages.optJSONArray(i) ?: continue
                            if (msg.optString(0) == "execution_error") {
                                val detail = msg.optJSONObject(1)
                                val errMsg = detail?.optString("exception_message") ?: "Unknown error"
                                throw IOException("ComfyUI execution error: $errMsg")
                            }
                        }
                    }
                    return@use null
                }

                val outputs = entry.optJSONObject("outputs") ?: return@use emptyList<String>()
                val filenames = mutableListOf<String>()
                val nodeKeys = outputs.keys()
                while (nodeKeys.hasNext()) {
                    val nodeKey = nodeKeys.next()
                    val nodeOutput = outputs.optJSONObject(nodeKey) ?: continue
                    val images = nodeOutput.optJSONArray("images") ?: continue
                    for (i in 0 until images.length()) {
                        val img = images.optJSONObject(i) ?: continue
                        val fname = img.optString("filename")
                        if (fname.isNotBlank()) filenames.add(fname)
                    }
                }
                filenames as List<String>
            }

            if (result != null) {
                return if (result.isNotEmpty()) {
                    ComfyResult.Success(result)
                } else {
                    ComfyResult.Failure("Workflow completed but produced no output images")
                }
            }

            Thread.sleep(3_000)
        }

        return ComfyResult.Failure("Timed out waiting for ComfyUI prompt $promptId after ${timeoutMs / 1000}s")
    }

    /**
     * Download an output image from ComfyUI's /view endpoint.
     */
    @Throws(Exception::class)
    fun downloadImage(
        filename: String,
        subfolder: String = "",
        type: String = "output",
    ): ByteArray {
        val url = buildString {
            append("$base/view?filename=")
            append(java.net.URLEncoder.encode(filename, "UTF-8"))
            if (subfolder.isNotEmpty()) {
                append("&subfolder=")
                append(java.net.URLEncoder.encode(subfolder, "UTF-8"))
            }
            append("&type=")
            append(java.net.URLEncoder.encode(type, "UTF-8"))
        }

        val downloadClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .build()

        val request = Request.Builder().url(url).get().build()
        return downloadClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("ComfyUI download failed (HTTP ${response.code}) for $filename")
            }
            response.body?.bytes()
                ?: throw IOException("ComfyUI download returned empty body for $filename")
        }
    }

    // ---------------------------------------------------------------------------
    // Internal helpers
    // ---------------------------------------------------------------------------

    private fun mapToJsonObject(map: Map<String, Any>): JSONObject {
        val obj = JSONObject()
        for ((k, v) in map) {
            obj.put(k, convertValue(v))
        }
        return obj
    }

    @Suppress("UNCHECKED_CAST")
    private fun convertValue(value: Any): Any {
        return when (value) {
            is Map<*, *> -> mapToJsonObject(value as Map<String, Any>)
            is List<*> -> {
                val arr = org.json.JSONArray()
                value.forEach { arr.put(if (it != null) convertValue(it) else JSONObject.NULL) }
                arr
            }
            is Boolean, is Int, is Long, is Double, is Float, is String -> value
            else -> value.toString()
        }
    }
}
