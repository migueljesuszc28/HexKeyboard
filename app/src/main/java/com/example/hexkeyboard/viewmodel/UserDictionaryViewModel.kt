package com.example.hexkeyboard.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.hexkeyboard.logic.engine.PredictionEngine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class UserDictionarySortType {
    ALPHABETICAL,
    DATE_ADDED_DESC,
    DATE_ADDED_ASC
}

data class UserDictionaryUiState(
    val entries: List<PredictionEngine.UserWordEntry> = emptyList(),
    val words: List<String> = emptyList(),
    val filteredWords: List<String> = emptyList(),
    val searchQuery: String = "",
    val sortType: UserDictionarySortType = UserDictionarySortType.ALPHABETICAL,
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
            engine.initialize(includeBase = false)
            val entries = engine.getUserWordEntries()
            _uiState.update { state ->
                val sortedWords = sortEntries(entries, state.sortType)
                state.copy(
                    entries = entries,
                    words = sortedWords, 
                    filteredWords = filterWords(sortedWords, state.searchQuery),
                    isLoading = false 
                )
            }
        }
    }

    fun setSortType(sortType: UserDictionarySortType) {
        _uiState.update { state ->
            val sortedWords = sortEntries(state.entries, sortType)
            state.copy(
                sortType = sortType,
                words = sortedWords,
                filteredWords = filterWords(sortedWords, state.searchQuery)
            )
        }
    }

    private fun sortEntries(entries: List<PredictionEngine.UserWordEntry>, sortType: UserDictionarySortType): List<String> {
        return when (sortType) {
            UserDictionarySortType.ALPHABETICAL -> entries.map { it.word }.sorted()
            UserDictionarySortType.DATE_ADDED_DESC -> entries.sortedByDescending { it.timestamp }.map { it.word }
            UserDictionarySortType.DATE_ADDED_ASC -> entries.sortedBy { it.timestamp }.map { it.word }
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
