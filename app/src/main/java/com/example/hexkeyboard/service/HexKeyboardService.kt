package com.example.hexkeyboard.service

import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.PointF
import android.inputmethodservice.InputMethodService
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.text.InputType
import android.text.TextUtils
import androidx.core.net.toUri
import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.view.inputmethod.InputMethodSubtype
import android.widget.Toast
import androidx.core.content.edit
import androidx.core.view.inputmethod.EditorInfoCompat
import androidx.core.view.inputmethod.InputConnectionCompat
import androidx.core.view.inputmethod.InputContentInfoCompat
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.preference.PreferenceManager
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.example.hexkeyboard.data.repository.EmojiProvider
import com.example.hexkeyboard.data.repository.KeyboardTheme
import com.example.hexkeyboard.data.repository.ThemeUtils
import com.example.hexkeyboard.logic.engine.PredictionEngine
import com.example.hexkeyboard.logic.managers.ClipboardHistoryManager
import com.example.hexkeyboard.logic.managers.ClipboardItem
import com.example.hexkeyboard.logic.managers.FeedbackManager
import com.example.hexkeyboard.logic.managers.ParallaxSensorManager
import com.example.hexkeyboard.logic.managers.VoiceRecognitionHelper
import com.example.hexkeyboard.ui.keyboard.components.LayoutRegistry
import com.example.hexkeyboard.ui.keyboard.components.HexLayoutEngine
import com.example.hexkeyboard.ui.keyboard.components.HexKeyboardView
import com.example.hexkeyboard.ui.keyboard.KeyboardScreen
import com.example.hexkeyboard.ui.theme.HexKeyboardTheme
import androidx.lifecycle.ViewModelProvider
import com.example.hexkeyboard.viewmodel.KeyboardViewModel
import com.example.hexkeyboard.ui.settings.SettingsActivity
import com.example.hexkeyboard.ui.settings.themes.ThemeSettingsActivity
import com.example.hexkeyboard.ui.settings.PermissionActivity
import androidx.core.graphics.ColorUtils
import androidx.core.view.WindowCompat
import androidx.core.content.edit
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.text.BreakIterator
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

@AndroidEntryPoint
class HexKeyboardService : InputMethodService(),
    LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner {

    @Inject lateinit var dataStore: DataStore<Preferences>
    @Inject lateinit var predictionEngine: PredictionEngine
    @Inject lateinit var feedbackManager: FeedbackManager
    @Inject lateinit var emojiProvider: EmojiProvider
    @Inject lateinit var parallaxSensorManager: ParallaxSensorManager
    @Inject lateinit var spellCheckerManager: SpellCheckerManager
    @Inject lateinit var voiceRecognitionHelper: VoiceRecognitionHelper

    private val mViewModelStore = ViewModelStore()
    private val mSavedStateRegistryController = SavedStateRegistryController.create(this)
    private val mLifecycleRegistry = LifecycleRegistry(this)

    private lateinit var clipboardManager: ClipboardManager
    private var isSettingInternalClip = false

    private val clipboardListener = ClipboardManager.OnPrimaryClipChangedListener {
        if (isSettingInternalClip) {
            isSettingInternalClip = false
            return@OnPrimaryClipChangedListener
        }
        val clip = try { if (::clipboardManager.isInitialized) clipboardManager.primaryClip else null } catch (_: Exception) { null }
        if ((clip != null) && (clip.itemCount > 0)) {
            val item = clip.getItemAt(0)
            val uri = item.uri
            val text = item.text?.toString() ?: ""
            val desc = clip.description
            val mimeType = desc?.let { d ->
                (0 until d.mimeTypeCount)
                    .map { d.getMimeType(it) }
                    .find { it.startsWith("image/") }
            }

            if (uri != null && mimeType != null) {
                serviceScope.launch(Dispatchers.IO) {
                    val cachedUri = ClipboardHistoryManager.saveImageToCache(this@HexKeyboardService, uri, mimeType)
                    if (cachedUri != null) {
                        ClipboardHistoryManager.addImageItem(this@HexKeyboardService, cachedUri, mimeType, text)
                        lastUsedClipboardText = null
                        refreshClipboardHistory(triggerSuggestionsUpdate = true)
                    }
                }
            } else if (text.isNotBlank()) {
                serviceScope.launch(Dispatchers.IO) {
                    ClipboardHistoryManager.addItem(this@HexKeyboardService, text)
                    lastUsedClipboardText = null
                    refreshClipboardHistory(triggerSuggestionsUpdate = true)
                }
            } else {
                lastUsedClipboardText = null
                refreshClipboardHistory(triggerSuggestionsUpdate = true)
            }
        } else {
            lastUsedClipboardText = null
            refreshClipboardHistory(triggerSuggestionsUpdate = true)
        }
    }

    internal var mHexKeyboardView: HexKeyboardView? = null
    private var lastSwipedWord: String? = null
    private var mComposeView: ComposeView? = null

    private lateinit var viewModel: KeyboardViewModel

    private var lastUsedClipboardText: String?
        get() {
            return try {
                val prefs = getSharedPreferences("hex_keyboard_prefs", MODE_PRIVATE)
                prefs.getString("last_used_clipboard_text", null)
            } catch (_: Exception) {
                null
            }
        }
        set(value) {
            try {
                val prefs = getSharedPreferences("hex_keyboard_prefs", MODE_PRIVATE)
                prefs.edit {
                    if (value == null) {
                        remove("last_used_clipboard_text")
                    } else {
                        putString("last_used_clipboard_text", value)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    private var lastAutoCorrection: LastCorrection? = null
    private var lastUndoneCorrection: String? = null
    internal var symbolsTypedCount = 0

    data class LastCorrection(val original: String, val corrected: String)
    private var lastKeyTime: Long = 0
    private var lastKeyWasSpace: Boolean = false
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private var autoCapitalize = true
    private var autoCorrect = false
    private var doubleSpacePeriod = true
    private var undoCorrectionOnBackspace = true

    fun switchToNextLanguage() {
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        val token = window?.window?.attributes?.token ?: return
        
        Settings.Secure.getString(contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)
        val list = imm.enabledInputMethodList.find { it.id.contains(packageName) }?.let { 
            imm.getEnabledInputMethodSubtypeList(it, true) 
        } ?: return

        if (list.size <= 1) return

        val currentSubtype = imm.currentInputMethodSubtype
        var nextIndex = 0
        for (i in list.indices) {
            if (list[i] == currentSubtype) {
                nextIndex = (i + 1) % list.size
                break
            }
        }
        
        val nextSubtype = list[nextIndex]
        @Suppress("DEPRECATION")
        imm.setInputMethodAndSubtype(token, null, nextSubtype)
    }

    fun showLanguagePicker() {
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        imm.showInputMethodPicker()
    }

    val longPressAlternatives = LayoutRegistry.longPressAlternatives
    val symbolPages = LayoutRegistry.symbolPages
    val fontPages = LayoutRegistry.fontPages

    fun setEmojiSearchActive(active: Boolean) {
        viewModel.setEmojiSearchActive(active)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        ThemeUtils.notifyDynamicColorsChanged()
    }

    override fun onCurrentInputMethodSubtypeChanged(newSubtype: InputMethodSubtype) {
        super.onCurrentInputMethodSubtypeChanged(newSubtype)
        val lang = newSubtype.languageTag.split("-")[0]
        
        serviceScope.launch {
            ThemeUtils.getDataStore(this@HexKeyboardService).edit { prefs ->
                if (prefs[ThemeUtils.KEYBOARD_LANGUAGE] != lang) {
                    prefs[ThemeUtils.KEYBOARD_LANGUAGE] = lang
                }
            }
        }

        viewModel.updateCurrentLocale(lang)
        mHexKeyboardView?.let { view ->
            view.post { 
                view.language = lang
                view.buildLayoutExternally() 
            }
        }
    }

    private fun switchToLanguage(langCode: String) {
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        val token = window?.window?.attributes?.token ?: return
        
        val currentSubtype = imm.currentInputMethodSubtype
        val currentLang = currentSubtype?.languageTag?.split("-")?.get(0)
        
        if (currentLang == langCode) return

        val list = imm.getEnabledInputMethodSubtypeList(null, true)
        for (subtype in list) {
            val sLang = subtype.languageTag.split("-")[0]
            
            if (sLang == langCode) {
                @Suppress("DEPRECATION")
                imm.setInputMethodAndSubtype(token, null, subtype)
                break
            }
        }
    }

    private val wallpaperReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == Intent.ACTION_WALLPAPER_CHANGED) {
                ThemeUtils.notifyDynamicColorsChanged()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        viewModel = ViewModelProvider(this)[KeyboardViewModel::class.java]
        setupViewModelCallbacks()

        val filter = IntentFilter(Intent.ACTION_WALLPAPER_CHANGED)
        registerReceiver(wallpaperReceiver, filter)

        serviceScope.launch {
            parallaxSensorManager.parallaxOffset.collect {
                viewModel.updateParallaxOffset(it)
            }
        }
        
        serviceScope.launch {
            dataStore.data.collectLatest { prefs ->
                val lang = prefs[ThemeUtils.KEYBOARD_LANGUAGE] ?: "es"
                val layoutType = prefs[ThemeUtils.KEYBOARD_LAYOUT_TYPE] ?: "default"
                
                autoCapitalize = prefs[ThemeUtils.AUTO_CAPITALIZE] ?: true
                autoCorrect = prefs[ThemeUtils.AUTO_CORRECT] ?: false
                doubleSpacePeriod = prefs[ThemeUtils.DOUBLE_SPACE_PERIOD] ?: true
                undoCorrectionOnBackspace = prefs[ThemeUtils.UNDO_CORRECTION_ON_BACKSPACE] ?: true

                viewModel.updateCurrentLocale(lang)
                viewModel.setSkinTone(prefs[ThemeUtils.SELECTED_SKIN_TONE] ?: "")
                viewModel.setGenderIndex(prefs[ThemeUtils.SELECTED_GENDER_INDEX] ?: 0)
                
                predictionEngine.initialize(lang)
                predictionEngine.setLayoutType(layoutType)
                
                withContext(Dispatchers.Main) {
                    switchToLanguage(lang)
                    mHexKeyboardView?.let { view ->
                        if (view.language != lang || view.layoutType != layoutType) {
                            view.language = lang
                            view.layoutType = layoutType
                            view.buildLayoutExternally()
                        }
                    }
                }
            }
        }

        clipboardManager = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        clipboardManager.addPrimaryClipChangedListener(clipboardListener)

        serviceScope.launch {
            ThemeUtils.getKeyboardThemeFlow(this@HexKeyboardService).collect { theme ->
                viewModel.updateKeyboardTheme(theme)
            }
        }

        serviceScope.launch(Dispatchers.IO) {
            ClipboardHistoryManager.getHistoryFlow(this@HexKeyboardService).collect { history ->
                withContext(Dispatchers.Main) {
                    viewModel.updateClipboardHistory(history)
                }
            }
        }
        
        serviceScope.launch {
            spellCheckerManager.suggestionsState.collect {
                updateSuggestions()
            }
        }

        mSavedStateRegistryController.performRestore(null)
        mLifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
    }

    private fun setupViewModelCallbacks() {
        viewModel.onActionRequested = { action ->
            when (action) {
                is KeyboardViewModel.Action.InsertText -> handleChar(action.text)
                is KeyboardViewModel.Action.UseClipboardItem -> useClipboardItem(action.item)
                is KeyboardViewModel.Action.DeleteBackward -> handleDelete()
                is KeyboardViewModel.Action.InsertNewLine -> handleEnter()
                is KeyboardViewModel.Action.ReplaceLastWord -> replaceLastWord(action.newWord)
                is KeyboardViewModel.Action.SetEmojiSearchActive,
                is KeyboardViewModel.Action.SetCredentialsSearchActive -> {
                    // La lógica ya se maneja en el ViewModel
                }
                is KeyboardViewModel.Action.OpenSettings -> {
                    val intent = when (action.type) {
                        KeyboardViewModel.SettingsType.GENERAL -> Intent(this, SettingsActivity::class.java)
                        KeyboardViewModel.SettingsType.THEMES -> Intent(this, ThemeSettingsActivity::class.java)
                        KeyboardViewModel.SettingsType.PERMISSIONS -> Intent(this, PermissionActivity::class.java)
                    }.apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
                    startActivity(intent)
                }
                KeyboardViewModel.Action.RefreshClipboard -> refreshClipboardHistory()
                KeyboardViewModel.Action.SwitchToNextLanguage -> switchToNextLanguage()
                KeyboardViewModel.Action.ShowLanguagePicker -> showLanguagePicker()
            }
        }
    }

    override fun onCreateInputView(): View {
        mLifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        
        val composeView = ComposeView(this).apply {
            mComposeView = this
            setBackgroundColor(Color.TRANSPARENT)

            setViewTreeLifecycleOwner(this@HexKeyboardService)
            setViewTreeViewModelStoreOwner(this@HexKeyboardService)
            setViewTreeSavedStateRegistryOwner(this@HexKeyboardService)
            
            setContent {
                val keyboardTheme by viewModel.keyboardTheme.collectAsState()
                CompositionLocalProvider(
                    LocalLifecycleOwner provides this@HexKeyboardService
                ) {
                    HexKeyboardTheme(dynamicColor = keyboardTheme?.id == "m3_dynamic") {
                        KeyboardScreen(viewModel = viewModel)
                    }
                }
            }
        }

        window?.window?.decorView?.let { decor ->
            decor.setViewTreeLifecycleOwner(this)
            decor.setViewTreeViewModelStoreOwner(this)
            decor.setViewTreeSavedStateRegistryOwner(this)
        }

        return composeView
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            unregisterReceiver(wallpaperReceiver)
        } catch (_: Exception) {}
        if (::clipboardManager.isInitialized) {
            try {
                clipboardManager.removePrimaryClipChangedListener(clipboardListener)
            } catch (_: Exception) {}
        }
        parallaxSensorManager.stop()
        spellCheckerManager.closeSession()
        voiceRecognitionHelper.destroy()
        serviceScope.cancel()
        
        mHexKeyboardView = null
        mComposeView = null
        
        mLifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        mViewModelStore.clear()
    }

    override fun onWindowShown() {
        super.onWindowShown()
        val theme = viewModel.keyboardTheme.value ?: return
        if (theme.parallaxEffect) {
            parallaxSensorManager.start()
        }

        mLifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        mLifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
        updateShiftState()
        updateSuggestions()

        serviceScope.launch(Dispatchers.IO) {
            ClipboardHistoryManager.cleanUpExpiredItems(this@HexKeyboardService)
        }

        mComposeView?.setBackgroundColor(Color.TRANSPARENT)

        window?.window?.let { win ->
            WindowCompat.setDecorFitsSystemWindows(win, false)
            win.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)

            @Suppress("DEPRECATION")
            win.navigationBarColor = if (theme.id == "glass") Color.TRANSPARENT else theme.backgroundColor

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                win.setBackgroundBlurRadius(if (theme.id == "glass") 60 else 0)
            }

            val isDark = ColorUtils.calculateLuminance(theme.backgroundColor) < 0.5
            WindowCompat.getInsetsController(win, win.decorView).apply {
                isAppearanceLightNavigationBars = !isDark
            }
        }
    }

    override fun onWindowHidden() {
        super.onWindowHidden()
        parallaxSensorManager.stop()

        symbolsTypedCount = 0
        serviceScope.launch {
            val resetOnClose = ThemeUtils.getDataStore(this@HexKeyboardService).data.first()[ThemeUtils.RESET_ON_CLOSE] ?: true
            if (resetOnClose) {
                withContext(Dispatchers.Main) {
                    mHexKeyboardView?.let {
                        it.capsLock = false
                        it.shifted = false
                        it.resetState()
                    }
                    viewModel.setCurrentView("keyboard")
                    updateShiftState()
                }
            }
        }
        mLifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        mLifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        super.onFinishInputView(finishingInput)
        serviceScope.launch {
            val resetOnClose = ThemeUtils.getDataStore(this@HexKeyboardService).data.first()[ThemeUtils.RESET_ON_CLOSE] ?: true
            if (resetOnClose) {
                withContext(Dispatchers.Main) {
                    viewModel.setCurrentView("keyboard")
                    mHexKeyboardView?.resetState()
                }
            }
        }
    }

    override fun onStartInput(attribute: EditorInfo?, restarting: Boolean) {
        super.onStartInput(attribute, restarting)
        if (attribute == null) return
        val effectiveAction = getEffectiveImeAction(attribute)
        mHexKeyboardView?.setImeAction(effectiveAction)
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        if (info == null) return
        
        val effectiveAction = getEffectiveImeAction(info)
        val inputType = info.inputType
        val classMask = inputType and InputType.TYPE_MASK_CLASS
        val isMultiLine = (classMask == InputType.TYPE_CLASS_TEXT) &&
                (inputType and InputType.TYPE_TEXT_FLAG_MULTI_LINE != 0) &&
                (effectiveAction == EditorInfo.IME_ACTION_NONE || effectiveAction == EditorInfo.IME_ACTION_UNSPECIFIED)

        mHexKeyboardView?.setImeAction(effectiveAction)
        mHexKeyboardView?.isMultiLine = isMultiLine

        if ((classMask == InputType.TYPE_CLASS_NUMBER) ||
            (classMask == InputType.TYPE_CLASS_PHONE)) {
            mHexKeyboardView?.layoutMode = HexLayoutEngine.LayoutMode.PURE_NUMERIC
        } else {
            mHexKeyboardView?.layoutMode = HexLayoutEngine.LayoutMode.ALPHA
        }
        
        updateShiftState()

        serviceScope.launch {
            val lang = ThemeUtils.getDataStore(this@HexKeyboardService).data.first()[ThemeUtils.KEYBOARD_LANGUAGE] ?: "es"
            
            withContext(Dispatchers.Main) {
                viewModel.updateCurrentLocale(lang)
                serviceScope.launch(Dispatchers.IO) {
                    predictionEngine.initialize(lang, forceUserDictReload = true)
                }
                switchToLanguage(lang)

                spellCheckerManager.closeSession()
                spellCheckerManager.clearSuggestions()
                spellCheckerManager.initSession()
            }
        }
    }

    fun updateSuggestions() {
        val ic = currentInputConnection ?: return
        val textBefore = ic.getTextBeforeCursor(40, 0) ?: ""
        
        serviceScope.launch(Dispatchers.Default) {
            val history = viewModel.clipboardHistory.value
            val topHistoryText = history.firstOrNull()?.text?.trim()
            
            val currentSystemClipText = try {
                if (::clipboardManager.isInitialized && clipboardManager.hasPrimaryClip()) {
                    clipboardManager.primaryClip?.getItemAt(0)?.text?.toString()?.trim()
                } else null
            } catch (_: Exception) { null }

            val clipToSuggest = if (!topHistoryText.isNullOrEmpty() &&
                !currentSystemClipText.isNullOrEmpty() &&
                topHistoryText == currentSystemClipText &&
                topHistoryText != lastUsedClipboardText?.trim()
            ) {
                topHistoryText
            } else null

            val allWords = textBefore.toString().trim().split(" ", "\n", "\t").filter { it.isNotEmpty() }
            val lastWord = if (textBefore.isNotEmpty() && !textBefore.endsWith(" ")) {
                allWords.lastOrNull() ?: ""
            } else ""
            val prevWord = if (lastWord.isEmpty()) allWords.lastOrNull() else allWords.getOrNull(allWords.size - 2)

            val localSuggestions = mutableListOf<String>()

            if (lastWord.isNotEmpty()) {
                val rawPredictions = predictionEngine.getSuggestions(lastWord, prevWord, limit = 10)
                var rawCandidates = rawPredictions.map { it.text }.distinct()

                val capsMode = ic.getCursorCapsMode(TextUtils.CAP_MODE_SENTENCES or TextUtils.CAP_MODE_WORDS)
                val shouldCapitalize = (capsMode != 0) || (mHexKeyboardView?.shifted == true)
                if (shouldCapitalize) {
                    rawCandidates = rawCandidates.map { word ->
                        word.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
                    }.distinct()
                }

                if (rawCandidates.isNotEmpty()) {
                    val primaryCandidate = rawCandidates.first()

                    val fallbackCandidate = if (lastWord.lowercase() != primaryCandidate.lowercase()) {
                        lastWord
                    } else {
                        rawCandidates.getOrNull(1) ?: lastWord
                    }

                    val alternativeCandidate = rawCandidates.find { 
                        it.lowercase() != primaryCandidate.lowercase() && it.lowercase() != fallbackCandidate.lowercase()
                    } ?: rawCandidates.getOrNull(2)

                    localSuggestions.add(primaryCandidate) // Índice 0: Centro (Predeterminada / Autocorrección)
                    if (fallbackCandidate.isNotEmpty()) {
                        localSuggestions.add(fallbackCandidate) // Índice 1: Izquierda (Literal / Fallback)
                    }
                    if (alternativeCandidate != null && alternativeCandidate.isNotEmpty()) {
                        localSuggestions.add(alternativeCandidate) // Índice 2: Derecha (Alternativa)
                    }
                } else {
                    localSuggestions.add(lastWord)
                }

                spellCheckerManager.fetchSuggestions(lastWord)
            } else {
                if (clipToSuggest != null) {
                    localSuggestions.add("CLIPBOARD:$clipToSuggest")
                } else {
                    val nextWordPredictions = predictionEngine.getSuggestions("", prevWord, limit = 3).map { it.text }.distinct()
                    localSuggestions.addAll(nextWordPredictions.take(3))
                }
            }
            
            val finalSuggestions = localSuggestions.distinct().take(3)
            withContext(Dispatchers.Main) {
                viewModel.updateSuggestions(finalSuggestions)
            }
        }
    }

    override fun onUpdateSelection(
        oldSelStart: Int, oldSelEnd: Int,
        newSelStart: Int, newSelEnd: Int,
        candidatesStart: Int, candidatesEnd: Int
    ) {
        super.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd, candidatesStart, candidatesEnd)
        updateShiftState()
        updateSuggestions()
    }

    fun onEmojiSelected(emoji: String) {
        val ic = currentInputConnection ?: return
        ic.commitText(emoji, 1)
        viewModel.setEmojiSearchActive(false)
        updateShiftState()
        updateSuggestions()
    }

    fun handleChar(text: String) {
        // La lógica de EmojiSearchQuery ahora se maneja en el ViewModel
        // pero por seguridad, si llegamos aquí, insertamos.
        
        mHexKeyboardView?.let { view ->
            if (view.layoutMode == HexLayoutEngine.LayoutMode.NUMERIC || view.layoutMode == HexLayoutEngine.LayoutMode.SYMBOLS) {
                if (text == " ") {
                    if (symbolsTypedCount > 0) {
                        view.layoutMode = HexLayoutEngine.LayoutMode.ALPHA
                        symbolsTypedCount = 0
                    }
                } else {
                    symbolsTypedCount++
                }
            } else {
                symbolsTypedCount = 0
            }
        }

        val ic = currentInputConnection ?: return

        if (text != " ") {
            lastAutoCorrection = null
            lastUndoneCorrection = null
        }
        
        ic.beginBatchEdit()

        if (text in listOf(".", ",", "!", "?", ";", ":")) {
            val textBefore = ic.getTextBeforeCursor(2, 0)
            if (textBefore != null && textBefore.endsWith(" ")) {
                ic.deleteSurroundingText(1, 0)
            }
            lastSwipedWord = null
        } else if (text != " ") {
            lastSwipedWord = null
        }
        
        if ((text == " " || text == "." || text == "," || text == "!") && !lastKeyWasSpace) {
            val before = ic.getTextBeforeCursor(40, 0) ?: ""
            val words = before.split(" ", "\n", "\t").filter { it.isNotEmpty() }
            if (words.isNotEmpty()) {
                val currentWord = words.last()
                val previousWord = if (words.size > 1) words[words.size - 2] else null
                val emailRegex = Regex("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")
                if (emailRegex.matches(currentWord)) {
                    predictionEngine.addUserWord(currentWord)
                } else {
                    predictionEngine.learnFromInput(currentWord, previousWord)
                }
            }
        }

        if (text == " " && lastKeyWasSpace && (System.currentTimeMillis() - lastKeyTime < 500)) {
            if (doubleSpacePeriod) {
                ic.deleteSurroundingText(1, 0)
                ic.commitText(". ", 1)
                lastKeyWasSpace = false
                lastAutoCorrection = null
                ic.endBatchEdit()
                updateShiftState()
                updateSuggestions()
                return
            }
        }

        if (text == " " && autoCorrect) {
            val suggestions = viewModel.suggestions.value
            if (suggestions.isNotEmpty() && !suggestions[0].contains(" ")) {
                val textBefore = ic.getTextBeforeCursor(30, 0) ?: ""
                val originalWord = textBefore.toString().split(" ", "\n").lastOrNull() ?: ""
                
                if (originalWord.isNotEmpty() && originalWord == lastUndoneCorrection) {
                    lastUndoneCorrection = null
                } else if (originalWord.isNotEmpty() && originalWord.lowercase() != suggestions[0].lowercase()) {
                    replaceLastWord(suggestions[0] + " ")
                    lastAutoCorrection = LastCorrection(originalWord, suggestions[0])
                    lastUndoneCorrection = null
                    ic.endBatchEdit()
                    return
                }
            }
        }

        if (text == " ") {
            lastAutoCorrection = null
            lastUndoneCorrection = null
        }
        ic.commitText(text, 1)
        ic.endBatchEdit()

        lastKeyTime = System.currentTimeMillis()
        lastKeyWasSpace = (text == " ")

        updateShiftState()
        updateSuggestions()
    }

    fun handleDelete() {
        // La lógica de borrar en búsqueda de emojis ahora se maneja en el ViewModel.
        
        val ic = currentInputConnection ?: return

        if (lastSwipedWord != null) {
            val sw = lastSwipedWord!!
            val textBefore = ic.getTextBeforeCursor(sw.length + 1, 0)
            if (textBefore != null && textBefore.toString() == "$sw ") {
                mHexKeyboardView?.triggerVibration(FeedbackManager.HapticType.DELETE)
                mHexKeyboardView?.triggerSound()
                ic.beginBatchEdit()
                ic.deleteSurroundingText(sw.length + 1, 0)
                ic.endBatchEdit()
                lastSwipedWord = null
                updateShiftState()
                updateSuggestions()
                return
            }
            lastSwipedWord = null
        }

        if (undoCorrectionOnBackspace) {
            lastAutoCorrection?.let { correction ->
                val textBefore = ic.getTextBeforeCursor(correction.corrected.length + 1, 0)
                if (textBefore != null && textBefore.toString() == "${correction.corrected} ") {
                    mHexKeyboardView?.triggerVibration(FeedbackManager.HapticType.DELETE)
                    mHexKeyboardView?.triggerSound()
                    ic.beginBatchEdit()
                    ic.deleteSurroundingText(correction.corrected.length + 1, 0)
                    ic.commitText(correction.original, 1)
                    ic.endBatchEdit()
                    lastUndoneCorrection = correction.original
                    lastAutoCorrection = null
                    updateShiftState()
                    updateSuggestions()
                    return
                }
            }
        }
        lastAutoCorrection = null
        lastUndoneCorrection = null
        
        val selectedText = ic.getSelectedText(0)
        val hasSelection = !selectedText.isNullOrEmpty()
        val textBefore = ic.getTextBeforeCursor(40, 0)
        val hasTextBefore = !textBefore.isNullOrEmpty()

        if (hasSelection || hasTextBefore) {
            mHexKeyboardView?.triggerVibration(FeedbackManager.HapticType.DELETE)
            mHexKeyboardView?.triggerSound()
            if (symbolsTypedCount > 0) symbolsTypedCount--
        } else {
            return
        }

        if (hasSelection) {
            ic.commitText("", 1)
        } else {
            val textStr = textBefore.toString()
            val len = calculateLastGraphemeLength(textStr)
            
            if (len > 1) {
                ic.deleteSurroundingText(len, 0)
            } else {
                ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DEL))
                ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DEL))
            }
        }
        
        updateShiftState()
        updateSuggestions()
    }

    private fun calculateLastGraphemeLength(text: String): Int {
        if (text.isEmpty()) return 0
        val it = BreakIterator.getCharacterInstance()
        it.setText(text)
        val last = it.last()
        val previous = it.previous()
        if (previous == BreakIterator.DONE) return 0
        return last - previous
    }

    fun getEffectiveImeAction(info: EditorInfo?): Int {
        if (info == null) return EditorInfo.IME_ACTION_NONE

        val action = info.imeOptions and EditorInfo.IME_MASK_ACTION

        // 1. Acciones explícitas de alta prioridad NUNCA deben ser sobrescritas por banderas multilinea.
        // Las barras de búsqueda y navegadores inyectan IME_ACTION_SEARCH, GO o SEND
        if (action == EditorInfo.IME_ACTION_SEARCH || 
            action == EditorInfo.IME_ACTION_GO || 
            action == EditorInfo.IME_ACTION_SEND) {
            return action
        }

        // 2. Si la app establece IME_FLAG_NO_ENTER_ACTION, pide explícitamente un Enter normal.
        if ((info.imeOptions and EditorInfo.IME_FLAG_NO_ENTER_ACTION) != 0) {
            return EditorInfo.IME_ACTION_NONE
        }

        // 3. En campos multilínea estándar (WhatsApp, Telegram) se prioriza el salto de línea
        // siempre y cuando la acción no haya sido una explícita procesada en el paso 1.
        val isTextClass = (info.inputType and InputType.TYPE_MASK_CLASS) == InputType.TYPE_CLASS_TEXT
        val isMultiLine = isTextClass && (info.inputType and InputType.TYPE_TEXT_FLAG_MULTI_LINE) != 0
        
        if (isMultiLine) {
            return EditorInfo.IME_ACTION_NONE
        }

        // 4. Si la app Android inyecta IME_ACTION_DONE (el default genérico de un EditText de línea única), 
        // lo convertimos a NONE si es un campo de texto, ya que la gente espera "Enter" para bajar la línea.
        if (action == EditorInfo.IME_ACTION_DONE && isTextClass) {
            return EditorInfo.IME_ACTION_NONE
        }

        // 5. Para todo lo demás (NEXT, PREVIOUS, DONE en formularios no textuales), respetamos la acción.
        return action
    }

    fun handleEnter() {
        val ic = currentInputConnection ?: return
        val ei = currentInputEditorInfo ?: return
        
        val before = ic.getTextBeforeCursor(40, 0) ?: ""
        val words = before.split(" ", "\n", "\t").filter { it.isNotEmpty() }
        if (words.isNotEmpty()) {
            val currentWord = words.last()
            val previousWord = if (words.size > 1) words[words.size - 2] else null
            val emailRegex = Regex("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")
            if (emailRegex.matches(currentWord)) {
                predictionEngine.addUserWord(currentWord)
            } else {
                predictionEngine.learnFromInput(currentWord, previousWord)
            }
        }

        val effectiveAction = getEffectiveImeAction(ei)
        
        if (effectiveAction != EditorInfo.IME_ACTION_NONE && effectiveAction != EditorInfo.IME_ACTION_UNSPECIFIED) {
            ic.performEditorAction(effectiveAction)
        } else {
            ic.commitText("\n", 1)
        }
        updateShiftState()
        updateSuggestions()
    }

    fun handleLiveGesture(points: List<PointF>, keys: List<HexLayoutEngine.Key>) {
        if (points.size < 2) return

        serviceScope.launch(Dispatchers.IO) {
            val charPoints = keys.filter { it.type == HexLayoutEngine.KeyType.CHAR && it.value.length == 1 }
                .map { PredictionEngine.CharPoint(it.value[0], it.cx, it.cy) }

            val suggestions = predictionEngine.getGestureSuggestions(points, charPoints, limit = 4)

            withContext(Dispatchers.Main) {
                if (suggestions.isNotEmpty()) {
                    viewModel.updateSuggestions(suggestions.map { it.text })
                }
            }
        }
    }

    fun handleGesture(points: List<PointF>, keys: List<HexLayoutEngine.Key>) {
        if (points.size < 2) return

        serviceScope.launch(Dispatchers.IO) {
            val charPoints = keys.filter { it.type == HexLayoutEngine.KeyType.CHAR && it.value.length == 1 }
                .map { PredictionEngine.CharPoint(it.value[0], it.cx, it.cy) }

            val suggestions = predictionEngine.getGestureSuggestions(points, charPoints, limit = 5)

            withContext(Dispatchers.Main) {
                if (suggestions.isNotEmpty()) {
                    val best = suggestions.first()
                    val textToCommit = if (mHexKeyboardView?.shifted == true) {
                        best.text.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
                    } else {
                        best.text
                    }

                    if (best.confidence >= 0.12f || best.score >= 30.0) {
                        val ic = currentInputConnection
                        ic?.beginBatchEdit()
                        ic?.commitText("$textToCommit ", 1)
                        ic?.endBatchEdit()
                        lastSwipedWord = textToCommit

                        val textBefore = ic?.getTextBeforeCursor(40, 0) ?: ""
                        val words = textBefore.split(" ", "\n", "\t").filter { it.isNotEmpty() }
                        val prevWord = if (words.size > 1) words[words.size - 2] else null
                        predictionEngine.learnFromInput(textToCommit, prevWord)

                        if (mHexKeyboardView?.shifted == true && mHexKeyboardView?.capsLock == false) {
                            mHexKeyboardView?.shifted = false
                        }
                    }
                    viewModel.updateSuggestions(suggestions.map { it.text })
                }
            }
        }
    }

    fun replaceLastWord(newWord: String) {
        val ic = currentInputConnection ?: return
        val latestClip = viewModel.clipboardHistory.value.firstOrNull()?.text ?: ""
        if (newWord.trim() == latestClip.trim()) {
            lastUsedClipboardText = latestClip
        }

        ic.beginBatchEdit()
        val textBefore = ic.getTextBeforeCursor(30, 0)
        if (!textBefore.isNullOrEmpty() && !textBefore.endsWith(" ")) {
            val lastWord = textBefore.toString().split(" ", "\n").last()
            ic.deleteSurroundingText(lastWord.length, 0)
        }
        ic.commitText(newWord, 1)
        ic.endBatchEdit()
        updateShiftState()
        updateSuggestions()
    }

    fun moveCursor(direction: Int) {
        val ic = currentInputConnection ?: return
        if (direction > 0) {
            ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_RIGHT))
        } else {
            ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_LEFT))
        }
    }

    fun useClipboardItem(item: ClipboardItem) {
        if (item.isImage) {
            commitImageContent(item)
        } else {
            val latestClip = viewModel.clipboardHistory.value.firstOrNull()?.text ?: ""
            if (item.text.trim() == latestClip.trim()) {
                lastUsedClipboardText = latestClip
            }
            handleChar(item.text)
        }
    }

    fun commitImageContent(item: ClipboardItem) {
        val ic = currentInputConnection ?: return
        val info = currentInputEditorInfo ?: return
        val imageUriString = item.imageUri ?: return
        val mimeType = item.mimeType ?: "image/png"

        try {
            val imageUri = imageUriString.toUri()
            val editorMimeTypes = EditorInfoCompat.getContentMimeTypes(info)
            val isSupported = editorMimeTypes.any { editorMime ->
                ClipDescription.compareMimeTypes(editorMime, mimeType) || editorMime == "*/*"
            }

            if (isSupported) {
                val inputContentInfo = InputContentInfoCompat(
                    imageUri,
                    ClipDescription("Clipboard Image", arrayOf(mimeType)),
                    null
                )
                
                val flags = InputConnectionCompat.INPUT_CONTENT_GRANT_READ_URI_PERMISSION
                
                val committed = InputConnectionCompat.commitContent(ic, info, inputContentInfo, flags, null)
                if (committed) {
                    val latestClip = viewModel.clipboardHistory.value.firstOrNull()?.imageUri ?: ""
                    if (item.imageUri == latestClip) {
                        lastUsedClipboardText = latestClip
                    }
                    return
                }
            }

            val clipData = ClipData.newUri(contentResolver, "Clipboard Image", imageUri)
            isSettingInternalClip = true
            clipboardManager.setPrimaryClip(clipData)
            Toast.makeText(this, "Imagen copiada al portapapeles. Usa Pegar.", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "No se pudo insertar la imagen en esta aplicación", Toast.LENGTH_SHORT).show()
        }
    }

    fun updateShiftState() {
        val ic = currentInputConnection ?: return
        val ei = currentInputEditorInfo ?: return
        if (!autoCapitalize) return

        val view = mHexKeyboardView ?: return
        if (view.layoutMode != HexLayoutEngine.LayoutMode.ALPHA) return

        var reqModes = 0
        val inputType = ei.inputType
        if ((inputType and InputType.TYPE_MASK_CLASS) == InputType.TYPE_CLASS_TEXT) {
            if ((inputType and InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS) != 0) reqModes = reqModes or TextUtils.CAP_MODE_CHARACTERS
            if ((inputType and InputType.TYPE_TEXT_FLAG_CAP_WORDS) != 0) reqModes = reqModes or TextUtils.CAP_MODE_WORDS
            if ((inputType and InputType.TYPE_TEXT_FLAG_CAP_SENTENCES) != 0) reqModes = reqModes or TextUtils.CAP_MODE_SENTENCES

            if (reqModes == 0) reqModes = TextUtils.CAP_MODE_SENTENCES
        }

        if (reqModes != 0) {
            val caps = ic.getCursorCapsMode(reqModes) != 0
            if (view.shifted != caps && !view.capsLock) {
                view.shifted = caps
            }
        } else {
            if (view.shifted && !view.capsLock) {
                view.shifted = false
            }
        }
    }

    fun setCurrentView(view: String) {
        viewModel.setCurrentView(view)
    }

    fun clearSystemClipboardIfMatches(item: ClipboardItem) {
        try {
            if (!::clipboardManager.isInitialized || !clipboardManager.hasPrimaryClip()) return
            val clip = clipboardManager.primaryClip ?: return
            if (clip.itemCount == 0) return
            val clipItem = clip.getItemAt(0)
            val clipText = clipItem.text?.toString()?.trim()
            val clipUri = clipItem.uri?.toString()

            val textMatches = item.text.isNotBlank() && clipText == item.text.trim()
            val uriMatches = !item.imageUri.isNullOrEmpty() && (clipUri == item.imageUri || clipText == item.imageUri)

            if (textMatches || uriMatches) {
                isSettingInternalClip = true
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    clipboardManager.clearPrimaryClip()
                } else {
                    clipboardManager.setPrimaryClip(ClipData.newPlainText("", ""))
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun deleteClipboardItem(item: ClipboardItem) {
        serviceScope.launch(Dispatchers.IO) {
            clearSystemClipboardIfMatches(item)
            ClipboardHistoryManager.deleteItem(this@HexKeyboardService, item)
            refreshClipboardHistory(triggerSuggestionsUpdate = true)
        }
    }

    fun refreshClipboardHistory(triggerSuggestionsUpdate: Boolean = true) {
        serviceScope.launch(Dispatchers.IO) {
            ClipboardHistoryManager.cleanUpExpiredItems(this@HexKeyboardService)
            val history = ClipboardHistoryManager.getHistory(this@HexKeyboardService)
            withContext(Dispatchers.Main) {
                viewModel.updateClipboardHistory(history)
            }
            if (triggerSuggestionsUpdate) {
                updateSuggestions()
            }
        }
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        @Suppress("DEPRECATION")
        if (level >= TRIM_MEMORY_MODERATE) {
            mHexKeyboardView?.clearCaches()
            predictionEngine.clearCaches()
            viewModel.updateSuggestions(emptyList())
            viewModel.updateClipboardHistory(emptyList())
            System.gc()
        }
    }

    fun setSkinTone(modifier: String) {
        viewModel.setSkinTone(modifier)
        serviceScope.launch {
            ThemeUtils.getDataStore(this@HexKeyboardService).edit { prefs ->
                prefs[ThemeUtils.SELECTED_SKIN_TONE] = modifier
            }
        }
    }

    fun setGenderIndex(index: Int) {
        viewModel.setGenderIndex(index)
        serviceScope.launch {
            ThemeUtils.getDataStore(this@HexKeyboardService).edit { prefs ->
                prefs[ThemeUtils.SELECTED_GENDER_INDEX] = index
            }
        }
    }

    private val stickyEmojiCache = ConcurrentHashMap<String, String>()

    fun getStickyVariant(canonicalEmoji: String): String? {
        return stickyEmojiCache[canonicalEmoji]
    }

    fun saveStickyVariant(canonicalEmoji: String, variant: String) {
        stickyEmojiCache[canonicalEmoji] = variant
        serviceScope.launch {
            ThemeUtils.getDataStore(this@HexKeyboardService).edit { prefs ->
                prefs[stringPreferencesKey("sticky_emoji_$canonicalEmoji")] = variant
            }
        }
        
        EmojiProvider.skinToneModifiers.find { variant.contains(it) && it.isNotEmpty() }?.let {
            setSkinTone(it)
        }

        val family = EmojiProvider.getEmojiFamily(variant.replace(Regex("[\uD83C\uDFFB-\uD83C\uDFFF]"), ""))
        val cleanVariant = variant.replace(Regex("[\uD83C\uDFFB-\uD83C\uDFFF]"), "")
        when (cleanVariant) {
            family.male -> setGenderIndex(1)
            family.female -> setGenderIndex(2)
            family.neutral -> setGenderIndex(0)
        }
    }

    override val viewModelStore: ViewModelStore get() = mViewModelStore
    override val savedStateRegistry: SavedStateRegistry get() = mSavedStateRegistryController.savedStateRegistry
    override val lifecycle: Lifecycle get() = mLifecycleRegistry
}
