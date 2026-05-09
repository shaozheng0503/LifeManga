package com.lifemanga.android.ui.characters

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lifemanga.android.ServiceLocator
import com.lifemanga.android.work.CharacterGenerationWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class CharacterCreateState(
    val name: String = "",
    val bio: String = "",
    val photoPath: String? = null,
    val isGenerating: Boolean = false,
    val toast: String? = null,
)

class CharacterCreateViewModel : ViewModel() {

    private val context: Context = ServiceLocator.context
    private val repo = ServiceLocator.characterRepository
    private val imageStorage = ServiceLocator.imageStorage

    private val _uiState = MutableStateFlow(CharacterCreateState())
    val uiState: StateFlow<CharacterCreateState> = _uiState.asStateFlow()

    fun setName(v: String) {
        _uiState.update { it.copy(name = v) }
    }

    fun setBio(v: String) {
        _uiState.update { it.copy(bio = v) }
    }

    fun addPhoto(uri: Uri) {
        viewModelScope.launch {
            val path = withContext(Dispatchers.IO) { imageStorage.saveUriAsJpeg(uri) }
            if (path != null) {
                _uiState.update { it.copy(photoPath = path) }
            } else {
                _uiState.update { it.copy(toast = "无法读取这张图，换一张试试") }
            }
        }
    }

    fun startGeneration(onSuccess: () -> Unit) {
        val state = _uiState.value
        if (state.name.isBlank()) {
            _uiState.update { it.copy(toast = "请填写角色名称") }
            return
        }
        if (state.isGenerating) return

        _uiState.update { it.copy(isGenerating = true) }
        viewModelScope.launch {
            val character = withContext(Dispatchers.IO) {
                repo.createCharacter(
                    name = state.name,
                    bio = state.bio,
                    sourcePhotoPath = state.photoPath,
                )
            }
            CharacterGenerationWorker.enqueue(context, character.id)
            _uiState.update { it.copy(isGenerating = false, toast = "角色已创建，正在生成姿态视图") }
            onSuccess()
        }
    }

    fun clearToast() {
        _uiState.update { it.copy(toast = null) }
    }
}
