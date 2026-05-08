package com.lifemanga.android.data

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

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

    private val _hasKey = MutableStateFlow(prefs.getString(KEY_API, null).isNullOrBlank().not())
    val hasKey: StateFlow<Boolean> = _hasKey.asStateFlow()

    var apiKey: String?
        get() = prefs.getString(KEY_API, null)
        set(value) {
            prefs.edit().putString(KEY_API, value?.takeIf { it.isNotBlank() }).apply()
            _hasKey.value = !value.isNullOrBlank()
        }

    fun clear() {
        prefs.edit().remove(KEY_API).apply()
        _hasKey.value = false
    }

    private companion object {
        const val KEY_API = "openai_api_key"
    }
}
