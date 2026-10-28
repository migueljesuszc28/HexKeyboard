package com.example.mantiskeyboard.viewmodel

import android.content.Context
import android.content.res.Configuration
import androidx.datastore.preferences.core.edit
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.mantiskeyboard.data.model.CredentialItem
import com.example.mantiskeyboard.data.repository.CredentialsManager
import com.example.mantiskeyboard.data.repository.EmojiProvider
import com.example.mantiskeyboard.data.repository.KeyboardTheme
import com.example.mantiskeyboard.data.repository.ThemeUtils
import com.example.mantiskeyboard.logic.managers.ClipboardItem
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class KeyboardViewModel @Inject constructor() : ViewModel() {

    // --- Estado de la UI ---
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

    private val _pendingView = MutableStateFlow<String?>(null)
    val pendingView: StateFlow<String?> = _pendingView.asStateFlow()

    fun setPendingView(view: String?) {
        _pendingView.value = view
    }

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

    private val _credentialsSearchQuery = MutableStateFlow("")
    val credentialsSearchQuery: StateFlow<String> = _credentialsSearchQuery.asStateFlow()

    private val _isCredentialsSearchActive = MutableStateFlow(false)
    val isCredentialsSearchActive: StateFlow<Boolean> = _isCredentialsSearchActive.asStateFlow()

    private val _recentEmojis = MutableStateFlow<List<String>>(emptyList())
    val recentEmojis: StateFlow<List<String>> = _recentEmojis.asStateFlow()

    private val _filteredEmojiList = MutableStateFlow<List<EmojiProvider.EmojiGridItem>>(emptyList())
    val filteredEmojiList: StateFlow<List<EmojiProvider.EmojiGridItem>> = _filteredEmojiList.asStateFlow()

    // --- Estado Teclado Flotante ---
    private val _isFloatingMode = MutableStateFlow(false)
    val isFloatingMode: StateFlow<Boolean> = _isFloatingMode.asStateFlow()

    // --- Estado Teclado Dividido ---
    private val _isSplitMode = MutableStateFlow(false)
    val isSplitMode: StateFlow<Boolean> = _isSplitMode.asStateFlow()

    // --- Estado Redimensionado (Resize) estilo Gboard ---
    private val _isResizeMode = MutableStateFlow(false)
    val isResizeMode: StateFlow<Boolean> = _isResizeMode.asStateFlow()

    private val _tempKeyboardHeight = MutableStateFlow(50)
    val tempKeyboardHeight: StateFlow<Int> = _tempKeyboardHeight.asStateFlow()

    private val _tempBottomOffset = MutableStateFlow(30)
    val tempBottomOffset: StateFlow<Int> = _tempBottomOffset.asStateFlow()

    private val _floatingOffsetX = MutableStateFlow(0f)
    val floatingOffsetX: StateFlow<Float> = _floatingOffsetX.asStateFlow()

    private val _floatingOffsetY = MutableStateFlow(0f)
    val floatingOffsetY: StateFlow<Float> = _floatingOffsetY.asStateFlow()

    private val _floatingScale = MutableStateFlow(0.85f)
    val floatingScale: StateFlow<Float> = _floatingScale.asStateFlow()

    private val _floatingCardRect = MutableStateFlow<android.graphics.Rect?>(null)
    val floatingCardRect: StateFlow<android.graphics.Rect?> = _floatingCardRect.asStateFlow()

    fun updateFloatingCardRect(rect: android.graphics.Rect) {
        _floatingCardRect.value = rect
        onActionRequested?.invoke(Action.UpdateFloatingCardRect(rect))
    }

    // --- Callbacks para comunicación con el Service ---
    var onActionRequested: ((Action) -> Unit)? = null

    sealed class Action {
        data class InsertText(val text: String) : Action()
        data class UseClipboardItem(val item: ClipboardItem) : Action()
        object DeleteBackward : Action()
        object InsertNewLine : Action()
        data class ReplaceLastWord(val newWord: String) : Action()
        data class SetEmojiSearchActive(val active: Boolean) : Action()
        data class SetCredentialsSearchActive(val active: Boolean) : Action()
        data class OpenSettings(val type: SettingsType) : Action()
        object RefreshClipboard : Action()
        object SwitchToNextLanguage : Action()
        object ShowLanguagePicker : Action()
        data class ToggleFloatingMode(val enabled: Boolean) : Action()
        data class UpdateFloatingWindowPosition(val x: Float, val y: Float) : Action()
        data class UpdateFloatingCardRect(val rect: android.graphics.Rect) : Action()
    }

    enum class SettingsType { GENERAL, THEMES, PERMISSIONS }

    // --- Lógica de Negocio / UI ---

    fun loadFloatingSettings(context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            val prefs = ThemeUtils.getDataStore(context).data.first()
            val config = context.resources.configuration
            val autoLandscape = prefs[ThemeUtils.AUTO_FLOATING_IN_LANDSCAPE] ?: true
            val isLandscape = config.orientation == Configuration.ORIENTATION_LANDSCAPE

            if (autoLandscape && isLandscape) {
                _isFloatingMode.value = true
            } else {
                _isFloatingMode.value = prefs[ThemeUtils.FLOATING_KEYBOARD_ENABLED] ?: false
            }
            _floatingOffsetX.value = prefs[ThemeUtils.FLOATING_KEYBOARD_X] ?: 0f
            _floatingOffsetY.value = prefs[ThemeUtils.FLOATING_KEYBOARD_Y] ?: 0f
            _floatingScale.value = prefs[ThemeUtils.FLOATING_KEYBOARD_SCALE] ?: 0.85f
            _isSplitMode.value = prefs[ThemeUtils.SPLIT_KEYBOARD_ENABLED] ?: false
        }
    }

    fun setFloatingMode(enabled: Boolean, context: Context? = null) {
        _isFloatingMode.value = enabled
        if (context != null) {
            viewModelScope.launch(Dispatchers.IO) {
                ThemeUtils.getDataStore(context).edit { prefs ->
                    prefs[ThemeUtils.FLOATING_KEYBOARD_ENABLED] = enabled
                }
            }
        }
        onActionRequested?.invoke(Action.ToggleFloatingMode(enabled))
    }

    fun toggleFloatingMode(context: Context) {
        val newState = !_isFloatingMode.value
        setFloatingMode(newState, context)
        setCurrentView("keyboard")
    }

    fun toggleSplitMode(context: Context) {
        val newState = !_isSplitMode.value
        _isSplitMode.value = newState
        viewModelScope.launch(Dispatchers.IO) {
            ThemeUtils.getDataStore(context).edit { prefs ->
                prefs[ThemeUtils.SPLIT_KEYBOARD_ENABLED] = newState
            }
        }
        setCurrentView("keyboard")
    }

    fun toggleLayoutType(context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            val dataStore = ThemeUtils.getDataStore(context)
            val prefs = dataStore.data.first()
            val currentLayout = prefs[ThemeUtils.KEYBOARD_LAYOUT_TYPE] ?: "default"
            val newLayout = if (currentLayout == "qwerty") "default" else "qwerty"
            dataStore.edit { prefs ->
                prefs[ThemeUtils.KEYBOARD_LAYOUT_TYPE] = newLayout
            }
        }
        setCurrentView("keyboard")
    }

    // --- Métodos de Redimensionado (Resize Mode) ---
    fun startResizeMode(context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            val prefs = ThemeUtils.getDataStore(context).data.first()
            _tempKeyboardHeight.value = prefs[ThemeUtils.KEYBOARD_HEIGHT] ?: 50
            _tempBottomOffset.value = prefs[ThemeUtils.KEYBOARD_BOTTOM_OFFSET] ?: 30
            _isResizeMode.value = true
            _currentView.value = "keyboard"
        }
    }

    fun updateTempResizeValues(height: Int, bottomOffset: Int) {
        _tempKeyboardHeight.value = height.coerceIn(30, 100)
        _tempBottomOffset.value = bottomOffset.coerceIn(0, 100)
    }

    fun resetResizeToDefaults() {
        _tempKeyboardHeight.value = 50
        _tempBottomOffset.value = 30
    }

    fun saveResizeAndExit(context: Context) {
        val h = _tempKeyboardHeight.value
        val offset = _tempBottomOffset.value
        viewModelScope.launch(Dispatchers.IO) {
            ThemeUtils.getDataStore(context).edit { prefs ->
                prefs[ThemeUtils.KEYBOARD_HEIGHT] = h
                prefs[ThemeUtils.KEYBOARD_BOTTOM_OFFSET] = offset
            }
            kotlinx.coroutines.withContext(Dispatchers.Main) {
                _isResizeMode.value = false
            }
        }
    }

    fun cancelResizeMode() {
        _isResizeMode.value = false
    }

    fun updateFloatingPosition(x: Float, y: Float, context: Context? = null) {
        _floatingOffsetX.value = x
        _floatingOffsetY.value = y
        onActionRequested?.invoke(Action.UpdateFloatingWindowPosition(x, y))
        if (context != null) {
            viewModelScope.launch(Dispatchers.IO) {
                ThemeUtils.getDataStore(context).edit { prefs ->
                    prefs[ThemeUtils.FLOATING_KEYBOARD_X] = x
                    prefs[ThemeUtils.FLOATING_KEYBOARD_Y] = y
                }
            }
        }
    }

    fun updateFloatingScale(scale: Float, context: Context? = null) {
        val clampedScale = scale.coerceIn(0.60f, 1.25f)
        _floatingScale.value = clampedScale
        if (context != null) {
            viewModelScope.launch(Dispatchers.IO) {
                ThemeUtils.getDataStore(context).edit { prefs ->
                    prefs[ThemeUtils.FLOATING_KEYBOARD_SCALE] = clampedScale
                }
            }
        }
    }

    fun resetFloatingPosition(context: Context? = null) {
        updateFloatingPosition(0f, 0f, context)
        updateFloatingScale(0.85f, context)
    }

    // --- Lógica de Negocio / UI ---

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
        _isResizeMode.value = false
        _clipboardItemWithOptions.value = null
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
        if (view != "credentials") {
            _credentialsSearchQuery.value = ""
            _isCredentialsSearchActive.value = false
            CredentialsManager.setKeyboardUnlocked(false)
            _pendingView.value = null
        }
    }

    fun setEmojiSearchActive(active: Boolean) {
        _isEmojiSearchActive.value = active
        if (!active) _emojiSearchQuery.value = ""
        onActionRequested?.invoke(Action.SetEmojiSearchActive(active))
    }

    fun setCredentialsSearchActive(active: Boolean) {
        _isCredentialsSearchActive.value = active
        if (!active) _credentialsSearchQuery.value = ""
        onActionRequested?.invoke(Action.SetCredentialsSearchActive(active))
    }

    fun updateCredentialsSearchQuery(query: String) {
        _credentialsSearchQuery.value = query
    }

    fun onEmojiSelected(emoji: String) {
        onActionRequested?.invoke(Action.InsertText(emoji))
        setEmojiSearchActive(false)
    }

    fun onCharTyped(text: String) {
        if (_isEmojiSearchActive.value) {
            updateEmojiSearchQuery(_emojiSearchQuery.value + text)
        } else if (_isCredentialsSearchActive.value) {
            updateCredentialsSearchQuery(_credentialsSearchQuery.value + text)
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
        } else if (_isCredentialsSearchActive.value) {
            val current = _credentialsSearchQuery.value
            if (current.isNotEmpty()) {
                updateCredentialsSearchQuery(current.dropLast(1))
            } else {
                setCredentialsSearchActive(false)
            }
        } else {
            onActionRequested?.invoke(Action.DeleteBackward)
        }
    }

    fun onEnter() {
        if (_isEmojiSearchActive.value) {
            setEmojiSearchActive(false)
        } else if (_isCredentialsSearchActive.value) {
            setCredentialsSearchActive(false)
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
        val targetCanonical = EmojiProvider.getCanonicalEmoji(emoji)
        val filtered = current.filter { EmojiProvider.getCanonicalEmoji(it) != targetCanonical }
        val newList = (listOf(emoji) + filtered).take(30)
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
