package com.example.hexkeyboard.viewmodel

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.hexkeyboard.data.model.CredentialItem
import com.example.hexkeyboard.data.repository.CredentialsManager
import com.example.hexkeyboard.data.repository.EmojiProvider
import com.example.hexkeyboard.data.repository.KeyboardTheme
import com.example.hexkeyboard.data.repository.ThemeUtils
import com.example.hexkeyboard.logic.managers.ClipboardItem
import com.example.hexkeyboard.logic.managers.ParallaxSensorManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class KeyboardViewModel @Inject constructor() : ViewModel() {

    // --- Estado de la UI ---
    private val _parallaxOffset = MutableStateFlow(ParallaxSensorManager.Offset(0f, 0f))
    val parallaxOffset: StateFlow<ParallaxSensorManager.Offset> = _parallaxOffset.asStateFlow()

    private val _suggestions = MutableStateFlow<List<String>>(emptyList())
    val suggestions: StateFlow<List<String>> = _suggestions.asStateFlow()

    private val _clipboardHistory = MutableStateFlow<List<ClipboardItem>>(emptyList())
    val clipboardHistory: StateFlow<List<ClipboardItem>> = _clipboardHistory.asStateFlow()

    private val _clipboardItemWithOptions = MutableStateFlow<ClipboardItem?>(null)
    val clipboardItemWithOptions: StateFlow<ClipboardItem?> = _clipboardItemWithOptions.asStateFlow()

    fun setClipboardItemWithOptions(item: ClipboardItem?) {
        _clipboardItemWithOptions.value = item
    }

    private val _credentials = MutableStateFlow<List<CredentialItem>>(emptyList())
    val credentials: StateFlow<List<CredentialItem>> = _credentials.asStateFlow()

    fun loadCredentials(context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            _credentials.value = CredentialsManager.getAllCredentials(context)
        }
    }

    private val _keyboardTheme = MutableStateFlow<KeyboardTheme?>(null)
    val keyboardTheme: StateFlow<KeyboardTheme?> = _keyboardTheme.asStateFlow()

    private val _currentView = MutableStateFlow("keyboard")
    val currentView: StateFlow<String> = _currentView.asStateFlow()

    private val _currentLocale = MutableStateFlow("es")
    val currentLocale: StateFlow<String> = _currentLocale.asStateFlow()

    private val _emojiSearchQuery = MutableStateFlow("")
    val emojiSearchQuery: StateFlow<String> = _emojiSearchQuery.asStateFlow()

    private val _selectedSkinTone = MutableStateFlow("")
    val selectedSkinTone: StateFlow<String> = _selectedSkinTone.asStateFlow()

    private val _selectedGenderIndex = MutableStateFlow(0)
    val selectedGenderIndex: StateFlow<Int> = _selectedGenderIndex.asStateFlow()

    private val _isEmojiSearchActive = MutableStateFlow(false)
    val isEmojiSearchActive: StateFlow<Boolean> = _isEmojiSearchActive.asStateFlow()

    private val _recentEmojis = MutableStateFlow<List<String>>(emptyList())
    val recentEmojis: StateFlow<List<String>> = _recentEmojis.asStateFlow()

    private val _filteredEmojiList = MutableStateFlow<List<EmojiProvider.EmojiGridItem>>(emptyList())
    val filteredEmojiList: StateFlow<List<EmojiProvider.EmojiGridItem>> = _filteredEmojiList.asStateFlow()

    // --- Callbacks para comunicación con el Service ---
    var onActionRequested: ((Action) -> Unit)? = null

    sealed class Action {
        data class InsertText(val text: String) : Action()
        data class UseClipboardItem(val item: ClipboardItem) : Action()
        object DeleteBackward : Action()
        object InsertNewLine : Action()
        data class ReplaceLastWord(val newWord: String) : Action()
        data class SetEmojiSearchActive(val active: Boolean) : Action()
        data class OpenSettings(val type: SettingsType) : Action()
        object RefreshClipboard : Action()
        object SwitchToNextLanguage : Action()
        object ShowLanguagePicker : Action()
    }

    enum class SettingsType { GENERAL, THEMES, PERMISSIONS }

    // --- Lógica de Negocio / UI ---

    fun updateParallaxOffset(offset: ParallaxSensorManager.Offset) {
        _parallaxOffset.value = offset
    }

    fun updateSuggestions(newList: List<String>) {
        _suggestions.value = newList
    }

    fun updateClipboardHistory(newList: List<ClipboardItem>) {
        _clipboardHistory.value = newList
    }

    fun updateKeyboardTheme(theme: KeyboardTheme?) {
        _keyboardTheme.value = theme
    }

    fun updateCurrentLocale(locale: String) {
        _currentLocale.value = locale
    }

    fun setCurrentView(view: String, context: Context? = null) {
        _currentView.value = view
        if (view == "clipboard") {
            onActionRequested?.invoke(Action.RefreshClipboard)
        }
        if (view == "credentials" && context != null) {
            loadCredentials(context)
        }
        if (view != "emoji") {
            _emojiSearchQuery.value = ""
            _isEmojiSearchActive.value = false
        }
    }

    fun setEmojiSearchActive(active: Boolean) {
        _isEmojiSearchActive.value = active
        if (!active) _emojiSearchQuery.value = ""
        onActionRequested?.invoke(Action.SetEmojiSearchActive(active))
    }

    fun onEmojiSelected(emoji: String) {
        onActionRequested?.invoke(Action.InsertText(emoji))
        setEmojiSearchActive(false)
    }

    fun onCharTyped(text: String) {
        if (_isEmojiSearchActive.value) {
            updateEmojiSearchQuery(_emojiSearchQuery.value + text)
        } else {
            onActionRequested?.invoke(Action.InsertText(text))
        }
    }

    fun onDelete() {
        if (_isEmojiSearchActive.value) {
            val current = _emojiSearchQuery.value
            if (current.isNotEmpty()) {
                updateEmojiSearchQuery(current.dropLast(1))
            } else {
                setEmojiSearchActive(false)
            }
        } else {
            onActionRequested?.invoke(Action.DeleteBackward)
        }
    }

    fun onEnter() {
        if (_isEmojiSearchActive.value) {
            setEmojiSearchActive(false)
        } else {
            onActionRequested?.invoke(Action.InsertNewLine)
        }
    }

    fun onSuggestionClick(suggestion: String) {
        onActionRequested?.invoke(Action.ReplaceLastWord("$suggestion "))
    }

    fun onClipboardItemClick(item: ClipboardItem) {
        onActionRequested?.invoke(Action.UseClipboardItem(item))
        setCurrentView("keyboard")
    }

    fun setSkinTone(modifier: String) {
        _selectedSkinTone.value = modifier
    }

    fun setGenderIndex(index: Int) {
        _selectedGenderIndex.value = index
    }

    fun updateRecentEmojis(emojis: List<String>) {
        _recentEmojis.value = emojis
        updateFilteredEmojiList()
    }

    fun saveRecentEmoji(context: Context, emoji: String) {
        val current = _recentEmojis.value
        val newList = (listOf(emoji) + current.filter { it != emoji }).take(30)
        _recentEmojis.value = newList
        viewModelScope.launch(Dispatchers.IO) {
            ThemeUtils.getDataStore(context).edit { prefs ->
                prefs[ThemeUtils.RECENT_EMOJIS] = newList.joinToString(",")
            }
        }
        updateFilteredEmojiList()
    }

    fun updateEmojiSearchQuery(query: String) {
        _emojiSearchQuery.value = query
        updateFilteredEmojiList()
    }

    private fun updateFilteredEmojiList() {
        val query = _emojiSearchQuery.value
        val recents = _recentEmojis.value
        
        val list = mutableListOf<EmojiProvider.EmojiGridItem>()
        if (query.isEmpty()) {
            if (recents.isNotEmpty()) {
                list.add(EmojiProvider.EmojiGridItem.Header("Recientes", 0))
                recents.forEach { 
                    val canonical = EmojiProvider.getCanonicalEmoji(it)
                    val family = EmojiProvider.getEmojiFamily(canonical)
                    list.add(EmojiProvider.EmojiGridItem.Emoji(it, "Recientes", 0, canonical, family))
                }
            }
            list.addAll(EmojiProvider.flatGridItems)
        } else {
            val filtered = EmojiProvider.searchEmojis(query)
            val collapsed = filtered.map { EmojiProvider.getEmojiFamily(it).neutral ?: it }.distinct()

            if (collapsed.isNotEmpty()) {
                list.add(EmojiProvider.EmojiGridItem.Header("Resultados", 0))
                collapsed.forEach { 
                    val family = EmojiProvider.getEmojiFamily(it)
                    list.add(EmojiProvider.EmojiGridItem.Emoji(it, "Search", 0, it, family))
                }
            }
        }
        _filteredEmojiList.value = list
    }

    fun onSettingsClick() {
        onActionRequested?.invoke(Action.OpenSettings(SettingsType.GENERAL))
    }

    fun onThemesClick() {
        onActionRequested?.invoke(Action.OpenSettings(SettingsType.THEMES))
    }

    fun switchToNextLanguage() {
        onActionRequested?.invoke(Action.SwitchToNextLanguage)
    }

    fun showLanguagePicker() {
        onActionRequested?.invoke(Action.ShowLanguagePicker)
    }
}
