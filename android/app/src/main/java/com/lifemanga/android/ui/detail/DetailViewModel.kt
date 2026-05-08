package com.lifemanga.android.ui.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lifemanga.android.ServiceLocator
import com.lifemanga.android.data.MangaItemEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class DetailViewModel : ViewModel() {

    private val repo = ServiceLocator.repository
    private val _item = MutableStateFlow<MangaItemEntity?>(null)
    val item: StateFlow<MangaItemEntity?> = _item.asStateFlow()
    private val _deleted = MutableStateFlow(false)
    val deleted: StateFlow<Boolean> = _deleted.asStateFlow()

    fun load(id: String) {
        viewModelScope.launch { _item.value = repo.get(id) }
    }

    fun toggleFavorite() {
        val current = _item.value ?: return
        viewModelScope.launch {
            repo.toggleFavorite(current.id)
            _item.value = repo.get(current.id)
        }
    }

    fun delete() {
        val current = _item.value ?: return
        viewModelScope.launch {
            repo.delete(current.id)
            _deleted.value = true
        }
    }
}
