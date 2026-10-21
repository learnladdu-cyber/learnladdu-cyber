package com.ashis.notesphare.ui.screens.tags

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ashis.notesphare.data.local.TagWithCount
import com.ashis.notesphare.data.repository.TagRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class TagsViewModel(private val tagRepository: TagRepository) : ViewModel() {

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    val tags: StateFlow<List<TagWithCount>> = tagRepository.observeTagsWithCounts()
        .catch { throwable ->
            _message.value = throwable.message ?: "Could not load tags"
            emit(emptyList())
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )

    fun createTag(name: String, color: Int) {
        viewModelScope.launch {
            runCatching { tagRepository.createTag(name, color) }
                .onFailure { _message.value = it.message ?: "Could not create tag" }
        }
    }

    fun updateTag(tagId: Long, name: String, color: Int) {
        viewModelScope.launch {
            runCatching { tagRepository.updateTag(tagId, name, color) }
                .onFailure { _message.value = it.message ?: "Could not rename tag" }
        }
    }

    fun deleteTag(tagId: Long) {
        viewModelScope.launch {
            runCatching { tagRepository.deleteTag(tagId) }
                .onSuccess { _message.value = "Tag deleted" }
                .onFailure { _message.value = it.message ?: "Could not delete tag" }
        }
    }

    fun clearMessage() {
        _message.value = null
    }
}
