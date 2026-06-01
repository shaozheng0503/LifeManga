package com.lifemanga.android.data

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 加密本地存储。
 *
 * 现阶段只有一个 Key：comfy.org 的 API Key（仅在用 comfy.org 云端节点时需要；
 * 本地 z_image workflow 走匿名请求，留空也能跑）。
 */
class SecureStore(context: Context) {
    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs = EncryptedSharedPreferences.create(
        context,
        "encrypted_prefs",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    private val _hasComfyKey = MutableStateFlow(
        prefs.getString(KEY_COMFY_API, null).isNullOrBlank().not(),
    )
    val hasComfyKey: StateFlow<Boolean> = _hasComfyKey.asStateFlow()

    var comfyApiKey: String?
        get() = prefs.getString(KEY_COMFY_API, null)
        set(value) {
            prefs.edit().putString(KEY_COMFY_API, value?.takeIf { it.isNotBlank() }).apply()
            _hasComfyKey.value = !value.isNullOrBlank()
        }

    fun clearComfyApiKey() {
        prefs.edit().remove(KEY_COMFY_API).apply()
        _hasComfyKey.value = false
    }

    private companion object {
        const val KEY_COMFY_API = "comfy_api_key"
    }
}
