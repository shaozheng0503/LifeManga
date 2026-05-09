package com.lifemanga.android.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lifemanga.android.ServiceLocator
import com.lifemanga.android.data.AppSettings
import com.lifemanga.android.data.BubbleMode
import com.lifemanga.android.data.EndpointType
import com.lifemanga.android.data.MangaStyle
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SettingsUiState(
    val apiKeyMasked: String,
    val hasApiKey: Boolean,
    val comfyApiKeyMasked: String,
    val hasComfyApiKey: Boolean,
    val settings: AppSettings,
)

class SettingsViewModel : ViewModel() {

    private val secureStore = ServiceLocator.secureStore
    private val store = ServiceLocator.appSettings

    val uiState: StateFlow<SettingsUiState> = combine(
        secureStore.hasKey,
        secureStore.hasComfyKey,
        store.flow,
    ) { has, hasComfy, settings ->
        SettingsUiState(
            apiKeyMasked = secureStore.apiKey?.let { mask(it) }.orEmpty(),
            hasApiKey = has,
            comfyApiKeyMasked = secureStore.comfyApiKey?.let { mask(it) }.orEmpty(),
            hasComfyApiKey = hasComfy,
            settings = settings,
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        SettingsUiState("", false, "", false, AppSettings()),
    )

    fun setApiKey(value: String) {
        secureStore.apiKey = value.trim()
    }

    fun clearApiKey() {
        secureStore.clear()
    }

    fun setComfyApiKey(value: String) {
        secureStore.comfyApiKey = value.trim()
    }

    fun clearComfyApiKey() {
        secureStore.clearComfyApiKey()
    }

    fun setStyle(style: MangaStyle) = viewModelScope.launch { store.setStyle(style) }
    fun setIsColor(value: Boolean) = viewModelScope.launch { store.setIsColor(value) }
    fun setBubbleMode(value: BubbleMode) = viewModelScope.launch { store.setBubbleMode(value) }
    fun setEndpointType(value: EndpointType) = viewModelScope.launch { store.setEndpointType(value) }
    fun setAzureEndpoint(value: String) = viewModelScope.launch { store.setAzureEndpoint(value) }
    fun setAzureDeployment(value: String) = viewModelScope.launch { store.setAzureDeployment(value) }
    fun setAzureApiVersion(value: String) = viewModelScope.launch { store.setAzureApiVersion(value) }
    fun setComfyUiUrl(value: String) = viewModelScope.launch { store.setComfyUiUrl(value) }
    fun setQwenUrl(value: String) = viewModelScope.launch { store.setQwenUrl(value) }
    fun setStoryMode(value: Boolean) = viewModelScope.launch { store.setStoryMode(value) }
    fun setPanelCount(value: Int) = viewModelScope.launch { store.setPanelCount(value) }

    private fun mask(key: String): String =
        if (key.length <= 10) "****" else "${key.take(6)}…${key.takeLast(4)}"
}
