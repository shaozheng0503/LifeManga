package com.lifemanga.android.ui.characters

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.lifemanga.android.ServiceLocator
import com.lifemanga.android.data.CharacterEntity
import com.lifemanga.android.data.CharacterViewEntity
import com.lifemanga.android.data.ReferenceIntent
import com.lifemanga.android.work.CharacterGenerationWorker
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class CharacterDetailState(
    val character: CharacterEntity? = null,
    val views: List<CharacterViewEntity> = emptyList(),
    val isGenerating: Boolean = false,
    val toast: String? = null,
)

class CharacterDetailViewModel(private val characterId: String) : ViewModel() {

    private val repo = ServiceLocator.characterRepository
    private val toastFlow = MutableStateFlow<String?>(null)

    val uiState: StateFlow<CharacterDetailState> = combine(
        repo.observeViews(characterId),
        toastFlow,
    ) { views, toast ->
        val character = repo.getById(characterId)
        CharacterDetailState(
            character = character,
            views = views,
            isGenerating = false,
            toast = toast,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CharacterDetailState())

    fun generateMoreViews() {
        viewModelScope.launch {
            CharacterGenerationWorker.enqueue(ServiceLocator.context, characterId)
            toastFlow.value = "已提交生成任务，完成后通知会响"
        }
    }

    fun deleteView(viewId: String) {
        viewModelScope.launch {
            repo.deleteView(viewId)
        }
    }

    /**
     * 把当前角色的全部视图路径塞进 [ReferenceIntent]，
     * 配合 AppNav 的 onLoadIntoCreate 导航到工程列表，
     * 用户挑工程进 CreateScreen 时由 CreateViewModel.init 自动消费。
     */
    fun loadViewsIntoReferenceIntent() {
        viewModelScope.launch {
            val views = repo.observeViews(characterId).first()
            ReferenceIntent.offer(views.map { it.imagePath })
            toastFlow.value = if (views.isEmpty())
                "该角色还没有视图，先去生成"
            else
                "已为下次创作载入 ${views.size} 张角色视图"
        }
    }

    fun renameCharacter(newName: String) {
        viewModelScope.launch {
            repo.rename(characterId, newName)
        }
    }

    fun clearToast() {
        toastFlow.value = null
    }

    class Factory(private val characterId: String) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            CharacterDetailViewModel(characterId) as T
    }
}
