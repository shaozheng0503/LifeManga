package com.lifemanga.android.ui.characters

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lifemanga.android.ServiceLocator
import com.lifemanga.android.data.CharacterEntity
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class CharacterSummary(
    val id: String,
    val name: String,
    val bio: String,
    val coverImagePath: String?,
)

class CharacterLibraryViewModel : ViewModel() {

    private val repo = ServiceLocator.characterRepository

    val characters: StateFlow<List<CharacterSummary>> = repo.observeAll()
        .map { list -> list.map { it.toSummary() } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun deleteCharacter(id: String) {
        viewModelScope.launch { repo.delete(id) }
    }

    private fun CharacterEntity.toSummary() = CharacterSummary(
        id = id,
        name = name,
        bio = bio,
        coverImagePath = sourcePhotoPath,
    )
}
