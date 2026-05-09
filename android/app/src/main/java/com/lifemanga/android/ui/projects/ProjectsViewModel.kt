package com.lifemanga.android.ui.projects

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lifemanga.android.ServiceLocator
import com.lifemanga.android.data.ProjectEntity
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ProjectItem(
    val id: String,
    val name: String,
    val createdAt: Long,
    val updatedAt: Long,
    val coverItemId: String?,
)

class ProjectsViewModel : ViewModel() {

    private val repo = ServiceLocator.projectRepository

    val projects: StateFlow<List<ProjectItem>> = repo.observeAll()
        .map { list -> list.map { it.toUi() } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun createProject(name: String): String {
        var newId = ""
        viewModelScope.launch {
            newId = repo.create(name)
        }
        // Return a temp id – callers that need the real id should use createProjectSuspend
        return newId
    }

    fun createProjectAsync(name: String, onCreated: (String) -> Unit) {
        viewModelScope.launch {
            val id = repo.create(name)
            onCreated(id)
        }
    }

    fun renameProject(id: String, name: String) {
        viewModelScope.launch { repo.rename(id, name) }
    }

    fun deleteProject(id: String) {
        viewModelScope.launch { repo.delete(id) }
    }

    private fun ProjectEntity.toUi() = ProjectItem(
        id = id,
        name = name,
        createdAt = createdAt,
        updatedAt = updatedAt,
        coverItemId = coverItemId,
    )
}
