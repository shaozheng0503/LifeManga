package com.lifemanga.android.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lifemanga.android.ServiceLocator
import com.lifemanga.android.data.AppSettings
import com.lifemanga.android.data.BubbleMode
import com.lifemanga.android.data.MangaStyle
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SettingsUiState(
    val comfyApiKeyMasked: String,
    val hasComfyApiKey: Boolean,
    val settings: AppSettings,
)

class SettingsViewModel : ViewModel() {

    private val secureStore = ServiceLocator.secureStore
    private val store = ServiceLocator.appSettings

    val uiState: StateFlow<SettingsUiState> = combine(
        secureStore.hasComfyKey,
        store.flow,
    ) { hasComfy, settings ->
        SettingsUiState(
            comfyApiKeyMasked = secureStore.comfyApiKey?.let { mask(it) }.orEmpty(),
            hasComfyApiKey = hasComfy,
            settings = settings,
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        SettingsUiState("", false, AppSettings()),
    )

    fun setComfyApiKey(value: String) {
        secureStore.comfyApiKey = value.trim()
    }

    fun clearComfyApiKey() {
        secureStore.clearComfyApiKey()
    }

    fun setStyle(style: MangaStyle) = viewModelScope.launch { store.setStyle(style) }
    fun setIsColor(value: Boolean) = viewModelScope.launch { store.setIsColor(value) }
    fun setBubbleMode(value: BubbleMode) = viewModelScope.launch { store.setBubbleMode(value) }
    fun setComfyUiUrl(value: String) = viewModelScope.launch { store.setComfyUiUrl(value) }
    fun setQwenUrl(value: String) = viewModelScope.launch { store.setQwenUrl(value) }
    fun setStoryMode(value: Boolean) = viewModelScope.launch { store.setStoryMode(value) }
    fun setPanelCount(value: Int) = viewModelScope.launch { store.setPanelCount(value) }

    private fun mask(key: String): String =
        if (key.length <= 10) "****" else "${key.take(6)}…${key.takeLast(4)}"
}
