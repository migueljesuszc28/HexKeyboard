package com.example.mantiskeyboard.service

import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.database.ContentObserver
import android.graphics.Color
import android.graphics.PointF
import android.inputmethodservice.InputMethodService
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.provider.Settings
import android.text.InputType
import android.text.TextUtils
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.view.inputmethod.InputMethodSubtype
import android.widget.Toast
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ComposeView
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.core.graphics.ColorUtils
import androidx.core.net.toUri
import androidx.core.view.WindowCompat
import androidx.core.view.inputmethod.EditorInfoCompat
import androidx.core.view.inputmethod.InputConnectionCompat
import androidx.core.view.inputmethod.InputContentInfoCompat
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.example.mantiskeyboard.data.repository.CredentialsManager
import com.example.mantiskeyboard.data.repository.EmojiProvider
import com.example.mantiskeyboard.data.repository.ThemeUtils
import com.example.mantiskeyboard.logic.engine.PredictionEngine
import com.example.mantiskeyboard.logic.engine.TouchPoint
import com.example.mantiskeyboard.logic.engine.WordSanitizer
import com.example.mantiskeyboard.logic.managers.ClipboardHistoryManager
import com.example.mantiskeyboard.logic.managers.ClipboardItem
import com.example.mantiskeyboard.logic.managers.FeedbackManager
import com.example.mantiskeyboard.logic.managers.VoiceRecognitionHelper
import com.example.mantiskeyboard.ui.keyboard.KeyboardScreen
import com.example.mantiskeyboard.ui.keyboard.components.KeyboardView
import com.example.mantiskeyboard.ui.keyboard.components.KeyboardLayoutEngine
import com.example.mantiskeyboard.ui.keyboard.components.LayoutRegistry
import com.example.mantiskeyboard.ui.settings.PermissionActivity
import com.example.mantiskeyboard.ui.settings.SettingsActivity
import com.example.mantiskeyboard.ui.settings.themes.ThemeSettingsActivity
import com.example.mantiskeyboard.ui.theme.MantisKeyboardTheme
import com.example.mantiskeyboard.viewmodel.KeyboardViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import kotlin.time.Duration.Companion.milliseconds

@AndroidEntryPoint
class KeyboardService : InputMethodService(),
    LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner {

    companion object {
        private const val CLIPBOARD_SUGGESTION_MAX_AGE_MS = 15 * 60 * 1000L // 15 minutos

        fun hasMediaPermission(context: Context): Boolean {
            val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                android.Manifest.permission.READ_MEDIA_IMAGES
            } else {
                android.Manifest.permission.READ_EXTERNAL_STORAGE
            }
            return ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
        }

        fun requestMediaPermission(context: Context) {
            val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                android.Manifest.permission.READ_MEDIA_IMAGES
            } else {
                android.Manifest.permission.READ_EXTERNAL_STORAGE
            }
            val intent = Intent(context, PermissionActivity::class.java).apply {
                putExtra("request_permission", permission)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }
    }

    @Inject lateinit var dataStore: DataStore<Preferences>
    @Inject lateinit var predictionEngine: PredictionEngine
    @Inject lateinit var feedbackManager: FeedbackManager
    @Inject lateinit var emojiProvider: EmojiProvider
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
            var mimeType = desc?.let { d ->
                (0 until d.mimeTypeCount)
                    .map { d.getMimeType(it) }
                    .find { it.startsWith("image/") }
            }

            if (mimeType == null && uri != null) {
                try {
                    val resolverType = contentResolver.getType(uri)
                    if (resolverType != null && resolverType.startsWith("image/")) {
                        mimeType = resolverType
                    }
                } catch (_: Exception) {}
            }

            if (mimeType == null && uri != null) {
                val path = uri.toString().lowercase()
                if (path.contains("png") || path.contains("jpg") || path.contains("jpeg") || path.contains("webp") || path.contains("gif")) {
                    mimeType = when {
                        path.contains("png") -> "image/png"
                        path.contains("webp") -> "image/webp"
                        path.contains("gif") -> "image/gif"
                        else -> "image/jpeg"
                    }
                }
            }

            if (uri != null && mimeType != null) {
                serviceScope.launch(Dispatchers.IO) {
                    val cachedUri = ClipboardHistoryManager.saveImageToCache(this@KeyboardService, uri, mimeType)
                    if (cachedUri != null) {
                        ClipboardHistoryManager.addImageItem(this@KeyboardService, cachedUri, mimeType, text)
                        lastUsedClipboardText = null
                        refreshClipboardHistory(triggerSuggestionsUpdate = true)
                    }
                }
            } else if (text.isNotBlank()) {
                serviceScope.launch(Dispatchers.IO) {
                    ClipboardHistoryManager.addItem(this@KeyboardService, text)
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

    private var screenshotObserver: ContentObserver? = null

    private fun registerScreenshotObserver() {
        if (screenshotObserver != null) return
        val handler = Handler(Looper.getMainLooper())
        screenshotObserver = object : ContentObserver(handler) {
            private var lastProcessedUriString: String? = null

            override fun onChange(selfChange: Boolean, uri: Uri?) {
                super.onChange(selfChange, uri)
                checkLatestScreenshot()
            }

            private fun checkLatestScreenshot() {
                if (!hasMediaPermission(this@KeyboardService)) return

                serviceScope.launch(Dispatchers.IO) {
                    try {
                        val enabled = ThemeUtils.getDataStore(this@KeyboardService).data.first()[ThemeUtils.AUTO_COPY_SCREENSHOTS] ?: true
                        if (!enabled) return@launch

                        val projection = arrayOf(
                            MediaStore.Images.Media._ID,
                            MediaStore.Images.Media.DISPLAY_NAME,
                            MediaStore.Images.Media.DATE_ADDED,
                            MediaStore.Images.Media.MIME_TYPE
                        )
                        val sortOrder = "${MediaStore.Images.Media.DATE_ADDED} DESC"

                        contentResolver.query(
                            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                            projection,
                            null,
                            null,
                            sortOrder
                        )?.use { cursor ->
                            if (cursor.moveToFirst()) {
                                val idColumn = cursor.getColumnIndex(MediaStore.Images.Media._ID)
                                val nameColumn = cursor.getColumnIndex(MediaStore.Images.Media.DISPLAY_NAME)
                                val dateColumn = cursor.getColumnIndex(MediaStore.Images.Media.DATE_ADDED)
                                val mimeColumn = cursor.getColumnIndex(MediaStore.Images.Media.MIME_TYPE)

                                if (idColumn != -1) {
                                    val id = cursor.getLong(idColumn)
                                    val name = if (nameColumn != -1) cursor.getString(nameColumn) ?: "" else ""
                                    val dateAdded = if (dateColumn != -1) cursor.getLong(dateColumn) else 0L
                                    val mimeType = if (mimeColumn != -1) cursor.getString(mimeColumn) ?: "image/png" else "image/png"

                                    val currentTimeSeconds = System.currentTimeMillis() / 1000
                                    val isRecent = (currentTimeSeconds - dateAdded) < 15
                                    val isScreenshot = name.contains("screenshot", ignoreCase = true) ||
                                                       name.contains("captura", ignoreCase = true) ||
                                                       name.contains("screen", ignoreCase = true)

                                    val contentUri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)

                                    if (isRecent && isScreenshot && contentUri.toString() != lastProcessedUriString) {
                                        lastProcessedUriString = contentUri.toString()
                                        val cachedUri = ClipboardHistoryManager.saveImageToCache(this@KeyboardService, contentUri, mimeType)
                                        if (cachedUri != null) {
                                            ClipboardHistoryManager.addImageItem(this@KeyboardService, cachedUri, mimeType, "Captura de pantalla")
                                            refreshClipboardHistory(triggerSuggestionsUpdate = true)
                                        }
                                    }
                                }
                            }
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
        }

        try {
            contentResolver.registerContentObserver(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                true,
                screenshotObserver!!
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun unregisterScreenshotObserver() {
        screenshotObserver?.let {
            try {
                contentResolver.unregisterContentObserver(it)
            } catch (_: Exception) {}
            screenshotObserver = null
        }
    }

    internal var mKeyboardView: KeyboardView? = null
    private var lastSwipedWord: String? = null
    private var mComposeView: ComposeView? = null

    private lateinit var viewModel: KeyboardViewModel

    private var lastUsedClipboardText: String?
        get() {
            return try {
                val prefs = getSharedPreferences("mantis_keyboard_prefs", MODE_PRIVATE)
                prefs.getString("last_used_clipboard_text", null)
            } catch (_: Exception) {
                null
            }
        }
        set(value) {
            try {
                val prefs = getSharedPreferences("mantis_keyboard_prefs", MODE_PRIVATE)
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

    private val currentWordTouchPoints = java.util.concurrent.CopyOnWriteArrayList<TouchPoint>()
    private var liveGestureJob: Job? = null
    private var autoCorrectForField = true
    private var allowPersonalizedLearning = true
    private var isAutoLandscapeFloating = false

    private fun learnFromInputSafely(word: String, prev1: String? = null, prev2: String? = null) {
        if (allowPersonalizedLearning) {
            predictionEngine.learnFromInput(word, prev1, prev2)
        }
    }

    fun recordTouchPoint(text: String, x: Float, y: Float) {
        if (text.isNotEmpty()) {
            val char = text.first()
            if (char.isLetter()) {
                currentWordTouchPoints.add(TouchPoint(char = char, x = x, y = y))
            }
        }
    }

    fun updateKeyCenters(centers: Map<Char, PointF>) {
        predictionEngine.updateKeyCenters(centers)
    }

    data class LastCorrection(val original: String, val corrected: String)
    private var lastKeyTime: Long = 0
    private var lastKeyWasSpace: Boolean = false
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private var autoCapitalize = true
    private var autoCorrect = true
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


    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        ThemeUtils.notifyDynamicColorsChanged()
        handleOrientationFloatingMode(newConfig)
    }

    private fun handleOrientationFloatingMode(config: Configuration) {
        serviceScope.launch {
            val prefs = dataStore.data.first()
            val autoLandscape = prefs[ThemeUtils.AUTO_FLOATING_IN_LANDSCAPE] ?: true
            val isFloating = viewModel.isFloatingMode.value

            if (autoLandscape) {
                if (config.orientation == Configuration.ORIENTATION_LANDSCAPE) {
                    if (!isFloating) {
                        isAutoLandscapeFloating = true
                        withContext(Dispatchers.Main) {
                            viewModel.setFloatingMode(true)
                        }
                    }
                } else if (config.orientation == Configuration.ORIENTATION_PORTRAIT) {
                    if (isAutoLandscapeFloating) {
                        isAutoLandscapeFloating = false
                        val portraitFloatingEnabled = prefs[ThemeUtils.FLOATING_KEYBOARD_ENABLED] ?: false
                        withContext(Dispatchers.Main) {
                            viewModel.setFloatingMode(portraitFloatingEnabled)
                        }
                    }
                }
            }
        }
    }

    override fun onCurrentInputMethodSubtypeChanged(newSubtype: InputMethodSubtype) {
        super.onCurrentInputMethodSubtypeChanged(newSubtype)
        val lang = newSubtype.languageTag.split("-")[0]
        
        serviceScope.launch {
            ThemeUtils.getDataStore(this@KeyboardService).edit { prefs ->
                if (prefs[ThemeUtils.KEYBOARD_LANGUAGE] != lang) {
                    prefs[ThemeUtils.KEYBOARD_LANGUAGE] = lang
                }
            }
        }

        viewModel.updateCurrentLocale(lang)
        mKeyboardView?.let { view ->
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
        @Suppress("DEPRECATION")
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
        registerScreenshotObserver()

        @Suppress("DEPRECATION")
        val filter = IntentFilter(Intent.ACTION_WALLPAPER_CHANGED)
        registerReceiver(wallpaperReceiver, filter)
        
        serviceScope.launch {
            dataStore.data.collectLatest { prefs ->
                val lang = prefs[ThemeUtils.KEYBOARD_LANGUAGE] ?: "es"
                val secLang = prefs[ThemeUtils.SECONDARY_LANGUAGE] ?: ""
                val multilingualEnabled = prefs[ThemeUtils.MULTILINGUAL_ENABLED] ?: true
                val secondaryLangs = if (multilingualEnabled && secLang.isNotBlank() && secLang != lang) listOf(secLang) else emptyList()
                val layoutType = prefs[ThemeUtils.KEYBOARD_LAYOUT_TYPE] ?: "default"
                
                autoCapitalize = prefs[ThemeUtils.AUTO_CAPITALIZE] ?: true
                autoCorrect = prefs[ThemeUtils.AUTO_CORRECT] ?: true
                doubleSpacePeriod = prefs[ThemeUtils.DOUBLE_SPACE_PERIOD] ?: true
                undoCorrectionOnBackspace = prefs[ThemeUtils.UNDO_CORRECTION_ON_BACKSPACE] ?: true

                viewModel.updateCurrentLocale(lang)
                viewModel.setSkinTone(prefs[ThemeUtils.SELECTED_SKIN_TONE] ?: "")
                viewModel.setGenderIndex(prefs[ThemeUtils.SELECTED_GENDER_INDEX] ?: 0)
                
                predictionEngine.initialize(lang = lang, secondaryLangs = secondaryLangs)
                predictionEngine.setLayoutType(layoutType)
                
                withContext(Dispatchers.Main) {
                    switchToLanguage(lang)
                    mKeyboardView?.let { view ->
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
            ThemeUtils.getKeyboardThemeFlow(this@KeyboardService).collect { theme ->
                viewModel.updateKeyboardTheme(theme)
            }
        }

        serviceScope.launch(Dispatchers.IO) {
            ClipboardHistoryManager.getHistoryFlow(this@KeyboardService).collect { history ->
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
                is KeyboardViewModel.Action.ToggleFloatingMode -> {
                    if (!action.enabled) {
                        isAutoLandscapeFloating = false
                    }
                    updateImeWindowForFloatingMode(action.enabled)
                }
                is KeyboardViewModel.Action.UpdateFloatingWindowPosition -> {
                    // Posición manejada por Compose + touchableRegion
                }
                is KeyboardViewModel.Action.UpdateFloatingCardRect -> {
                    window?.window?.decorView?.post {
                        window?.window?.decorView?.requestLayout()
                    }
                }
            }
        }
    }

    override fun onComputeInsets(outInsets: Insets) {
        super.onComputeInsets(outInsets)
        if (viewModel.isFloatingMode.value) {
            val decorHeight = window?.window?.decorView?.height ?: 0
            outInsets.contentTopInsets = decorHeight
            outInsets.visibleTopInsets = decorHeight

            val density = resources.displayMetrics.density
            val screenWidth = resources.displayMetrics.widthPixels
            val screenHeight = resources.displayMetrics.heightPixels

            val cardRect = viewModel.floatingCardRect.value
            if (cardRect != null && !cardRect.isEmpty) {
                outInsets.touchableInsets = Insets.TOUCHABLE_INSETS_REGION

                if (viewModel.clipboardItemWithOptions.value != null) {
                    // Si el menú de opciones del portapapeles está activo, capturar toda la pantalla
                    outInsets.touchableRegion.set(0, 0, screenWidth, screenHeight)
                } else {
                    // Margen superior holgado (300dp) para cubrir emergentes grandes de long press de emojis y teclas
                    val topMarginPx = (300 * density).toInt()
                    val sideMarginPx = (40 * density).toInt()

                    val expandedRect = android.graphics.Rect(
                        maxOf(0, cardRect.left - sideMarginPx),
                        maxOf(0, cardRect.top - topMarginPx),
                        minOf(screenWidth, cardRect.right + sideMarginPx),
                        minOf(screenHeight, cardRect.bottom + sideMarginPx)
                    )
                    outInsets.touchableRegion.set(expandedRect)
                }
            } else {
                outInsets.touchableInsets = Insets.TOUCHABLE_INSETS_REGION
                val cardWidthPx = (310 * density).toInt()
                val cardHeightPx = (280 * density).toInt()
                val centerX = screenWidth / 2
                val centerY = screenHeight / 2
                val topMarginPx = (300 * density).toInt()
                val sideMarginPx = (40 * density).toInt()

                outInsets.touchableRegion.set(
                    maxOf(0, centerX - cardWidthPx / 2 - sideMarginPx),
                    maxOf(0, centerY - cardHeightPx / 2 - topMarginPx),
                    minOf(screenWidth, centerX + cardWidthPx / 2 + sideMarginPx),
                    minOf(screenHeight, centerY + cardHeightPx / 2 + sideMarginPx)
                )
            }
        }
    }

    private fun updateImeWindowForFloatingMode(isFloating: Boolean) {
        predictionEngine.resetCalibration()
        window?.window?.let { win ->
            val params = win.attributes
            if (isFloating) {
                // Modo Flotante Real: Ventana de pantalla completa con Insets Region para passthrough perfecto
                win.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT)
                win.setGravity(android.view.Gravity.FILL)
                win.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(Color.TRANSPARENT))
                win.decorView.setBackgroundColor(Color.TRANSPARENT)
                @Suppress("DEPRECATION")
                win.navigationBarColor = Color.TRANSPARENT
                @Suppress("DEPRECATION")
                win.statusBarColor = Color.TRANSPARENT
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    win.setBackgroundBlurRadius(0)
                }
                
                win.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
                win.setDimAmount(0f)
                
                win.addFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN)
                win.clearFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS)

                params.x = 0
                params.y = 0
                win.attributes = params
                
            } else {
                // Modo Anclado (Docked): Restablecer alineación inferior y tamaño
                win.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT)
                win.setGravity(android.view.Gravity.BOTTOM)
                win.clearFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS)
                
                params.x = 0
                params.y = 0
                win.attributes = params
                win.decorView.post {
                    win.decorView.requestLayout()
                    win.decorView.requestApplyInsets()
                }
            }
        }
    }

    override fun onCreateInputView(): View {
        mLifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        
        val composeView = ComposeView(this).apply {
            mComposeView = this
            setBackgroundColor(Color.TRANSPARENT)

            setViewTreeLifecycleOwner(this@KeyboardService)
            setViewTreeViewModelStoreOwner(this@KeyboardService)
            setViewTreeSavedStateRegistryOwner(this@KeyboardService)
            
            setContent {
                val keyboardTheme by viewModel.keyboardTheme.collectAsState()
                CompositionLocalProvider(
                    LocalLifecycleOwner provides this@KeyboardService
                ) {
                    MantisKeyboardTheme(dynamicColor = keyboardTheme?.id == "m3_dynamic") {
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
        unregisterScreenshotObserver()
        try {
            unregisterReceiver(wallpaperReceiver)
        } catch (_: Exception) {}
        if (::clipboardManager.isInitialized) {
            try {
                clipboardManager.removePrimaryClipChangedListener(clipboardListener)
            } catch (_: Exception) {}
        }
        spellCheckerManager.closeSession()
        voiceRecognitionHelper.destroy()
        serviceScope.cancel()
        
        mKeyboardView = null
        mComposeView = null
        
        mLifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        mViewModelStore.clear()
    }

    override fun onWindowShown() {
        super.onWindowShown()
        val theme = viewModel.keyboardTheme.value ?: return

        mLifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        mLifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
        updateShiftState()
        updateSuggestions()

        val isFloating = viewModel.isFloatingMode.value
        updateImeWindowForFloatingMode(isFloating)

        val config = resources.configuration
        handleOrientationFloatingMode(config)

        val isCompactMultiWindow = config.screenWidthDp in 1..520 || config.screenHeightDp in 1..480
        if (isCompactMultiWindow) {
            serviceScope.launch {
                val prefs = dataStore.data.first()
                val autoFloating = prefs[ThemeUtils.AUTO_FLOATING_IN_MULTIWINDOW] ?: false
                if (autoFloating && !isFloating) {
                    withContext(Dispatchers.Main) {
                        viewModel.setFloatingMode(true, this@KeyboardService)
                    }
                }
            }
        }

        serviceScope.launch(Dispatchers.IO) {
            ClipboardHistoryManager.cleanUpExpiredItems(this@KeyboardService)
        }

        mComposeView?.setBackgroundColor(Color.TRANSPARENT)

        window?.window?.let { win ->
            WindowCompat.setDecorFitsSystemWindows(win, false)
            win.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)

            if (isFloating) {
                win.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(Color.TRANSPARENT))
                win.decorView.setBackgroundColor(Color.TRANSPARENT)
                @Suppress("DEPRECATION")
                win.navigationBarColor = Color.TRANSPARENT
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    win.setBackgroundBlurRadius(0)
                }
            } else {
                @Suppress("DEPRECATION")
                win.navigationBarColor = if (theme.id == "glass") Color.TRANSPARENT else theme.backgroundColor

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    win.setBackgroundBlurRadius(if (theme.id == "glass") 60 else 0)
                }
            }

            val isDark = ColorUtils.calculateLuminance(theme.backgroundColor) < 0.5
            WindowCompat.getInsetsController(win, win.decorView).apply {
                isAppearanceLightNavigationBars = !isDark
            }
        }
    }

    override fun onWindowHidden() {
        super.onWindowHidden()
        viewModel.cancelResizeMode()

        symbolsTypedCount = 0
        serviceScope.launch {
            val resetOnClose = ThemeUtils.getDataStore(this@KeyboardService).data.first()[ThemeUtils.RESET_ON_CLOSE] ?: true
            if (resetOnClose) {
                withContext(Dispatchers.Main) {
                    mKeyboardView?.let {
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
        viewModel.cancelResizeMode()
        serviceScope.launch {
            val resetOnClose = ThemeUtils.getDataStore(this@KeyboardService).data.first()[ThemeUtils.RESET_ON_CLOSE] ?: true
            withContext(Dispatchers.Main) {
                viewModel.setClipboardItemWithOptions(null)
                if (resetOnClose && finishingInput && !viewModel.isCredentialsSearchActive.value) {
                    viewModel.setCurrentView("keyboard")
                    mKeyboardView?.resetState()
                }
            }
        }
    }

    override fun onStartInput(attribute: EditorInfo?, restarting: Boolean) {
        super.onStartInput(attribute, restarting)
        if (attribute == null) return
        val effectiveAction = getEffectiveImeAction(attribute)
        mKeyboardView?.setImeAction(effectiveAction)
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        if (info == null) return
        
        val effectiveAction = getEffectiveImeAction(info)
        val inputType = info.inputType
        val classMask = inputType and InputType.TYPE_MASK_CLASS
        val variation = inputType and InputType.TYPE_MASK_VARIATION
        val isMultiLine = (classMask == InputType.TYPE_CLASS_TEXT) &&
                (inputType and InputType.TYPE_TEXT_FLAG_MULTI_LINE != 0) &&
                (effectiveAction == EditorInfo.IME_ACTION_NONE || effectiveAction == EditorInfo.IME_ACTION_UNSPECIFIED)

        val isPassword = variation == InputType.TYPE_TEXT_VARIATION_PASSWORD ||
                variation == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD ||
                variation == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD ||
                classMask == InputType.TYPE_CLASS_NUMBER ||
                classMask == InputType.TYPE_CLASS_PHONE ||
                classMask == InputType.TYPE_CLASS_DATETIME

        val isUrlOrEmail = variation == InputType.TYPE_TEXT_VARIATION_URI ||
                variation == InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS ||
                variation == InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS

        val noPersonalizedLearning = (info.imeOptions and EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING) != 0
        autoCorrectForField = !isPassword && !isUrlOrEmail
        allowPersonalizedLearning = !isPassword && !isUrlOrEmail && !noPersonalizedLearning
        currentWordTouchPoints.clear()

        mKeyboardView?.setImeAction(effectiveAction)
        mKeyboardView?.isMultiLine = isMultiLine

        if ((classMask == InputType.TYPE_CLASS_NUMBER) ||
            (classMask == InputType.TYPE_CLASS_PHONE)) {
            mKeyboardView?.layoutMode = KeyboardLayoutEngine.LayoutMode.PURE_NUMERIC
        } else {
            mKeyboardView?.layoutMode = KeyboardLayoutEngine.LayoutMode.ALPHA
        }
        
        updateShiftState()

        if (CredentialsManager.isKeyboardUnlocked.value) {
            viewModel.setCurrentView("credentials", this)
        }

        serviceScope.launch {
            val prefs = ThemeUtils.getDataStore(this@KeyboardService).data.first()
            val lang = prefs[ThemeUtils.KEYBOARD_LANGUAGE] ?: "es"
            val secLang = prefs[ThemeUtils.SECONDARY_LANGUAGE] ?: ""
            val multilingualEnabled = prefs[ThemeUtils.MULTILINGUAL_ENABLED] ?: true
            val secondaryLangs = if (multilingualEnabled && secLang.isNotBlank() && secLang != lang) listOf(secLang) else emptyList()
            
            withContext(Dispatchers.Main) {
                viewModel.updateCurrentLocale(lang)
                serviceScope.launch(Dispatchers.IO) {
                    predictionEngine.initialize(lang = lang, secondaryLangs = secondaryLangs, forceUserDictReload = true)
                }
                switchToLanguage(lang)

                spellCheckerManager.closeSession()
                spellCheckerManager.clearSuggestions()
                spellCheckerManager.initSession()
            }
        }
    }

    private var lastProcessedTextBefore: String? = null
    private var updateSuggestionsJob: Job? = null

    fun updateSuggestions() {
        updateSuggestionsJob?.cancel()
        updateSuggestionsJob = serviceScope.launch(Dispatchers.Default) {
            delay(15.milliseconds)
            val textBeforeSequence = withContext(Dispatchers.Main) {
                currentInputConnection?.getTextBeforeCursor(40, 0) ?: ""
            }
            val textBefore = textBeforeSequence.toString()

            val capsMode = withContext(Dispatchers.Main) {
                currentInputConnection?.getCursorCapsMode(TextUtils.CAP_MODE_SENTENCES or TextUtils.CAP_MODE_WORDS) ?: 0
            }
            val isShifted = mKeyboardView?.shifted == true
            val shouldCapitalize = (capsMode != 0) || isShifted

            if (textBefore == lastProcessedTextBefore) return@launch
            lastProcessedTextBefore = textBefore

            val history = viewModel.clipboardHistory.value
            val topHistoryItem = history.firstOrNull()
            val topHistoryText = topHistoryItem?.text?.trim()
            val now = System.currentTimeMillis()
            val isRecentClip = topHistoryItem != null && (now - topHistoryItem.timestamp) < CLIPBOARD_SUGGESTION_MAX_AGE_MS

            val clipToSuggest = if (!topHistoryText.isNullOrEmpty() &&
                isRecentClip &&
                topHistoryText != lastUsedClipboardText?.trim()
            ) {
                topHistoryText
            } else null

            val rawTokens = textBefore.trim().split(" ", "\n", "\t").filter { it.isNotEmpty() }
            val rawLastToken = if (textBefore.isNotEmpty() && !textBefore.endsWith(" ")) {
                rawTokens.lastOrNull() ?: ""
            } else ""

            val sanitizedLast = WordSanitizer.sanitizeToken(rawLastToken)
            val cleanLastWord = sanitizedLast.cleanWord

            val prev1Token = if (cleanLastWord.isEmpty()) rawTokens.lastOrNull() else rawTokens.getOrNull(rawTokens.size - 2)
            val prev2Token = if (cleanLastWord.isEmpty()) rawTokens.getOrNull(rawTokens.size - 2) else rawTokens.getOrNull(rawTokens.size - 3)

            val prev1 = WordSanitizer.sanitizeToken(prev1Token ?: "").cleanWord
            val prev2 = WordSanitizer.sanitizeToken(prev2Token ?: "").cleanWord

            val localSuggestions = mutableListOf<String>()

            if (cleanLastWord.isNotEmpty()) {
                val isImmune = WordSanitizer.isAutocorrectImmune(rawLastToken, cleanLastWord)
                val rawPredictions = predictionEngine.getSuggestions(
                    cleanLastWord,
                    previousWord = prev1,
                    prev2 = prev2,
                    touchPoints = ArrayList(currentWordTouchPoints),
                    limit = 10
                )
                var rawCandidates = rawPredictions.map { it.text }.distinct()

                if (shouldCapitalize) {
                    rawCandidates = rawCandidates.map { word ->
                        word.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
                    }.distinct()
                }

                if (isImmune) {
                    // Para palabras o tokens inmunes (correos, palabras con números, etc.), no autocorrige.
                    // Mantiene la palabra escrita intacta en primera posición.
                    localSuggestions.add(cleanLastWord)
                    val matchingPredictions = rawCandidates.filter { it.lowercase() != cleanLastWord.lowercase() }
                    localSuggestions.addAll(matchingPredictions.take(2))
                } else {
                    if (rawCandidates.isNotEmpty()) {
                        val primaryCandidate = rawCandidates.first()

                        val fallbackCandidate = if (cleanLastWord.lowercase() != primaryCandidate.lowercase()) {
                            cleanLastWord
                        } else {
                            rawCandidates.getOrNull(1) ?: cleanLastWord
                        }

                        val alternativeCandidate = rawCandidates.find { 
                            it.lowercase() != primaryCandidate.lowercase() && it.lowercase() != fallbackCandidate.lowercase()
                        } ?: rawCandidates.getOrNull(2)

                        localSuggestions.add(primaryCandidate)
                        if (fallbackCandidate.isNotEmpty()) {
                            localSuggestions.add(fallbackCandidate)
                        }
                        if (!alternativeCandidate.isNullOrEmpty()) {
                            localSuggestions.add(alternativeCandidate)
                        }
                    } else {
                        localSuggestions.add(cleanLastWord)
                    }

                    spellCheckerManager.fetchSuggestions(cleanLastWord)
                }
            } else {
                if (clipToSuggest != null) {
                    localSuggestions.add("CLIPBOARD:$clipToSuggest")
                } else {
                    val nextWordPredictions = predictionEngine.getSuggestions("", previousWord = prev1, prev2 = prev2, limit = 3).map { it.text }.distinct()
                    localSuggestions.addAll(nextWordPredictions.take(3))
                }
            }
            
            if (cleanLastWord.length >= 2) {
                try {
                    EmojiProvider.initialize(this@KeyboardService)
                    val matchingEmojis = emojiProvider.searchEmojis(cleanLastWord)
                    if (matchingEmojis.isNotEmpty()) {
                        val emojiToAdd = matchingEmojis.first()
                        if (!localSuggestions.contains(emojiToAdd)) {
                            if (localSuggestions.size >= 3) {
                                localSuggestions[2] = emojiToAdd
                            } else {
                                localSuggestions.add(emojiToAdd)
                            }
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.e("KeyboardService", "Error en emoji contextual: ${e.message}")
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

    private val textEditingEngine by lazy { TextEditingEngine(this, predictionEngine) }

    fun handleChar(text: String) {
        val ic = currentInputConnection ?: return
        textEditingEngine.handleChar(
            text = text,
            ic = ic,
            keyboardView = mKeyboardView,
            autoCorrect = autoCorrect,
            autoCorrectForField = autoCorrectForField,
            doubleSpacePeriod = doubleSpacePeriod,
            allowPersonalizedLearning = allowPersonalizedLearning,
            currentWordTouchPoints = currentWordTouchPoints,
            symbolsTypedCount = symbolsTypedCount,
            onSymbolsTypedCountChanged = { symbolsTypedCount = it },
            onStateUpdated = {
                updateShiftState()
                updateSuggestions()
            }
        )
    }

    fun handleDelete() {
        val ic = currentInputConnection ?: return
        textEditingEngine.handleDelete(
            ic = ic,
            keyboardView = mKeyboardView,
            undoCorrectionOnBackspace = undoCorrectionOnBackspace,
            currentWordTouchPoints = currentWordTouchPoints,
            symbolsTypedCount = symbolsTypedCount,
            onSymbolsTypedCountChanged = { symbolsTypedCount = it },
            onStateUpdated = {
                updateShiftState()
                updateSuggestions()
            }
        )
    }

    fun replaceLastWord(newWord: String) {
        val ic = currentInputConnection ?: return
        val latestClip = viewModel.clipboardHistory.value.firstOrNull()?.text ?: ""
        if (newWord.trim() == latestClip.trim()) {
            lastUsedClipboardText = latestClip
        }
        textEditingEngine.replaceLastWord(newWord, ic)
        updateShiftState()
        updateSuggestions()
    }

    fun getEffectiveImeAction(info: EditorInfo?): Int {
        return textEditingEngine.getEffectiveImeAction(info)
    }

    fun handleEnter() {
        val ic = currentInputConnection ?: return
        val ei = currentInputEditorInfo ?: return
        textEditingEngine.handleEnter(
            ic = ic,
            editorInfo = ei,
            allowPersonalizedLearning = allowPersonalizedLearning,
            onStateUpdated = {
                updateShiftState()
                updateSuggestions()
            }
        )
    }

    fun handleLiveGesture(points: List<PointF>, keys: List<KeyboardLayoutEngine.Key>) {
        if (points.size < 2) return

        liveGestureJob?.cancel()
        liveGestureJob = serviceScope.launch(Dispatchers.IO) {
            val charPoints = keys.filter { it.type == KeyboardLayoutEngine.KeyType.CHAR && it.value.length == 1 }
                .map { PredictionEngine.CharPoint(it.value[0], it.cx, it.cy) }

            val suggestions = predictionEngine.getGestureSuggestions(points, charPoints, limit = 4)

            if (!isActive) return@launch
            withContext(Dispatchers.Main) {
                if (suggestions.isNotEmpty()) {
                    viewModel.updateSuggestions(suggestions.map { it.text })
                }
            }
        }
    }

    fun handleGesture(points: List<PointF>, keys: List<KeyboardLayoutEngine.Key>) {
        if (points.size < 2) return

        liveGestureJob?.cancel()
        liveGestureJob = serviceScope.launch(Dispatchers.IO) {
            val charPoints = keys.filter { it.type == KeyboardLayoutEngine.KeyType.CHAR && it.value.length == 1 }
                .map { PredictionEngine.CharPoint(it.value[0], it.cx, it.cy) }

            val suggestions = predictionEngine.getGestureSuggestions(points, charPoints, limit = 5)

            withContext(Dispatchers.Main) {
                if (suggestions.isNotEmpty()) {
                    val best = suggestions.first()
                    val textToCommit = if (mKeyboardView?.shifted == true) {
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
                        val prev1 = if (words.size > 1) words[words.size - 2] else null
                        val prev2 = if (words.size > 2) words[words.size - 3] else null
                        learnFromInputSafely(textToCommit, prev1, prev2)
                        currentWordTouchPoints.clear()

                        if (mKeyboardView?.shifted == true && mKeyboardView?.capsLock == false) {
                            mKeyboardView?.shifted = false
                        }
                        updateShiftState()
                        updateSuggestions()
                    }
                }
            }
        }
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
            @Suppress("UsePropertyAccessSyntax")
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

        val view = mKeyboardView ?: return
        if (view.layoutMode != KeyboardLayoutEngine.LayoutMode.ALPHA) return

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
                    @Suppress("UsePropertyAccessSyntax")
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
            ClipboardHistoryManager.deleteItem(this@KeyboardService, item)
            refreshClipboardHistory(triggerSuggestionsUpdate = true)
        }
    }

    fun refreshClipboardHistory(triggerSuggestionsUpdate: Boolean = true) {
        serviceScope.launch(Dispatchers.IO) {
            ClipboardHistoryManager.cleanUpExpiredItems(this@KeyboardService)
            val history = ClipboardHistoryManager.getHistory(this@KeyboardService)
            withContext(Dispatchers.Main) {
                viewModel.updateClipboardHistory(history)
                if (triggerSuggestionsUpdate) {
                    lastProcessedTextBefore = null
                    updateSuggestions()
                }
            }
        }
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        @Suppress("DEPRECATION")
        if (level >= TRIM_MEMORY_MODERATE) {
            mKeyboardView?.clearCaches()
            predictionEngine.clearCaches()
            viewModel.updateSuggestions(emptyList())
            viewModel.updateClipboardHistory(emptyList())
            System.gc()
        }
    }

    fun setSkinTone(modifier: String) {
        viewModel.setSkinTone(modifier)
        serviceScope.launch {
            ThemeUtils.getDataStore(this@KeyboardService).edit { prefs ->
                prefs[ThemeUtils.SELECTED_SKIN_TONE] = modifier
            }
        }
    }

    fun setGenderIndex(index: Int) {
        viewModel.setGenderIndex(index)
        serviceScope.launch {
            ThemeUtils.getDataStore(this@KeyboardService).edit { prefs ->
                prefs[ThemeUtils.SELECTED_GENDER_INDEX] = index
            }
        }
    }

    private val stickyEmojiCache = ConcurrentHashMap<String, String>()

    fun saveStickyVariant(canonicalEmoji: String, variant: String) {
        stickyEmojiCache[canonicalEmoji] = variant
        serviceScope.launch {
            ThemeUtils.getDataStore(this@KeyboardService).edit { prefs ->
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
    override val lifecycle: Lifecycle = mLifecycleRegistry
}
