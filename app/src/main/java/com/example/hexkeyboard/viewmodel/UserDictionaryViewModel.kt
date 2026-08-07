package com.example.hexkeyboard.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.hexkeyboard.logic.engine.PredictionEngine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class UserDictionaryUiState(
    val words: List<String> = emptyList(),
    val filteredWords: List<String> = emptyList(),
    val searchQuery: String = "",
    val isLoading: Boolean = true,
    val isAddDialogOpen: Boolean = false,
    val newWordError: String? = null
)

class UserDictionaryViewModel(private val engine: PredictionEngine) : ViewModel() {

    private val _uiState = MutableStateFlow(UserDictionaryUiState())
    val uiState: StateFlow<UserDictionaryUiState> = _uiState.asStateFlow()

    init {
        loadWords()
    }

    private fun loadWords(showLoading: Boolean = true) {
        viewModelScope.launch {
            if (showLoading) _uiState.update { it.copy(isLoading = true) }
            // Cargamos solo el diccionario de usuario para evitar el OOM/Colapso
            engine.initialize(includeBase = false)
            val words = engine.getUserWords()
            _uiState.update { 
                it.copy(
                    words = words, 
                    filteredWords = filterWords(words, it.searchQuery),
                    isLoading = false 
                )
            }
        }
    }

    fun onSearchQueryChange(query: String) {
        _uiState.update { 
            it.copy(
                searchQuery = query,
                filteredWords = filterWords(it.words, query)
            )
        }
    }

    fun addWord(word: String) {
        val trimmed = word.trim()
        if (trimmed.isEmpty()) {
            _uiState.update { it.copy(newWordError = "La palabra no puede estar vacía") }
            return
        }
        if (_uiState.value.words.contains(trimmed.lowercase())) {
            _uiState.update { it.copy(newWordError = "Esta palabra ya existe") }
            return
        }

        viewModelScope.launch {
            engine.addUserWord(trimmed)
            loadWords(showLoading = false)
            _uiState.update { it.copy(isAddDialogOpen = false, newWordError = null) }
        }
    }

    fun removeWord(word: String) {
        viewModelScope.launch {
            engine.removeUserWord(word)
            loadWords(showLoading = false)
        }
    }

    fun clearAllWords() {
        viewModelScope.launch {
            engine.removeAllUserWords()
            loadWords(showLoading = false)
        }
    }

    fun toggleAddDialog(open: Boolean) {
        _uiState.update { it.copy(isAddDialogOpen = open, newWordError = null) }
    }

    private fun filterWords(words: List<String>, query: String): List<String> {
        if (query.isEmpty()) return words
        return words.filter { it.contains(query, ignoreCase = true) }
    }
}
