package com.example.hexkeyboard.service

import android.Manifest
import android.content.BroadcastReceiver
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.PointF
import android.inputmethodservice.InputMethodService
import android.os.Build
import android.provider.Settings
import android.text.InputType
import android.text.TextUtils
import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.view.inputmethod.InputMethodSubtype
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
import com.example.hexkeyboard.logic.managers.ParallaxSensorManager
import com.example.hexkeyboard.logic.managers.VoiceRecognitionHelper
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.BreakIterator
import java.util.Locale

class HexKeyboardService : InputMethodService(),
    LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner {

    private val mViewModelStore = ViewModelStore()
    private val mSavedStateRegistryController = SavedStateRegistryController.create(this)
    private val mLifecycleRegistry = LifecycleRegistry(this)

    private lateinit var clipboardManager: ClipboardManager
    private lateinit var spellCheckerManager: SpellCheckerManager
    private lateinit var predictionEngine: PredictionEngine
    lateinit var voiceRecognitionHelper: VoiceRecognitionHelper
    private val clipboardListener = ClipboardManager.OnPrimaryClipChangedListener {
        val clip = clipboardManager.primaryClip
        if ((clip != null) && (clip.itemCount > 0)) {
            val text = clip.getItemAt(0).text?.toString() ?: ""
            if (text.isNotEmpty()) {
                ClipboardHistoryManager.addItem(this, text)
                lastUsedClipboardText = null
                refreshClipboardHistory(triggerSuggestionsUpdate = true)
            }
        }
    }

    internal var mHexKeyboardView: HexKeyboardView? = null
    private var mComposeView: ComposeView? = null

    private lateinit var viewModel: KeyboardViewModel
    private lateinit var parallaxSensorManager: ParallaxSensorManager

    private var lastUsedClipboardText: String? = null
    private var lastAutoCorrection: LastCorrection? = null
    private var lastUndoneCorrection: String? = null
    internal var symbolsTypedCount = 0

    data class LastCorrection(val original: String, val corrected: String)
    private var lastKeyTime: Long = 0
    private var lastKeyWasSpace: Boolean = false
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

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

    private val prefsListener = SharedPreferences.OnSharedPreferenceChangeListener { prefs, key ->
        when (key) {
            "keyboard_language" -> {
                val lang = prefs.getString(key, "es") ?: "es"
                Log.d("HexKB", "Preference changed to: $lang")
                viewModel.updateCurrentLocale(lang)
                serviceScope.launch(Dispatchers.IO) {
                    predictionEngine.initialize(lang)
                }
                spellCheckerManager.closeSession()
                spellCheckerManager.clearSuggestions()
                spellCheckerManager.initSession()
                switchToLanguage(lang)
                mHexKeyboardView?.let { view ->
                    view.post { 
                        view.language = lang
                        view.buildLayoutExternally() 
                    }
                }
            }
            "keyboard_layout_type" -> {
                val type = prefs.getString(key, "default") ?: "default"
                predictionEngine.setLayoutType(type)
                mHexKeyboardView?.let { view ->
                    view.post {
                        view.layoutType = type
                        view.buildLayoutExternally()
                    }
                }
            }
        }
    }

    val longPressAlternatives = mapOf(
        'a' to listOf("á", "à", "â", "ä", "ã", "å", "æ", "@"),
        'e' to listOf("é", "è", "ê", "ë", "ē", "ę", "€"),
        'i' to listOf("í", "ì", "î", "ï", "ī", "į"),
        'o' to listOf("ó", "ò", "ô", "ö", "õ", "ø", "œ", "°"),
        'u' to listOf("ú", "ù", "û", "ü", "ū", "ų"),
        'n' to listOf("ñ", "ń", "ň", "ņ"),
        'c' to listOf("ç", "ć", "č", "©", "¢"),
        'l' to listOf("ł", "ļ", "ľ", "£"),
        's' to listOf("ś", "š", "ş", "§", "$"),
        'z' to listOf("ź", "ż", "ž"),
        'y' to listOf("ý", "ÿ", "¥"),
        '?' to listOf("¿", "!", "¡"),
        '(' to listOf("{", "[", "<"),
        ')' to listOf("}", "]", ">"),
        '.' to listOf("·", "…"),
        ',' to listOf(";", ":"),
        '-' to listOf("—", "_", "¯", "•", "·"),
        '+' to listOf("±"),
        '*' to listOf("★", "✝", "‡"),
        '=' to listOf("∞", "≠", "≈"),
        '%' to listOf("‰"),
        'π' to listOf("∏", "μ"),
        '"' to listOf("„", "“", "”", "‟", "«", "»"),
        '\'' to listOf("‘", "’", "‹", "›"),
        '<' to listOf("‹", "〈"),
        '>' to listOf("›", "〉"),
        '/' to listOf("\\"),
        '#' to listOf("№"),
        '$' to listOf("¢","€", "£", "₱", "¥", "₹"),
        '§' to listOf("¶"),
        '^' to listOf("↑", "↓", "←", "→"),
        '°' to listOf("′", "″"),
        '1' to listOf("¹", "₁", "½", "⅓", "¼", "⅕", "⅙", "⅐", "⅛", "⅑", "⅒"),
        '2' to listOf("²", "₂", "⅔", "⅖"),
        '3' to listOf("³", "₃", "¾", "⅗", "⅜"),
        '4' to listOf("⁴", "₄", "⅘"),
        '5' to listOf("⁵", "₅", "⅚", "⅝"),
        '6' to listOf("⁶", "₆"),
        '7' to listOf("⁷", "₇", "⅞"),
        '8' to listOf("⁸", "₈"),
        '9' to listOf("⁹", "₉"),
        '0' to listOf("⁰", "₀", "∅"),
    )

    val symbolPages = mapOf(
        "basic" to listOf(
            "~", "`", "|", "•", "√", "π",
            "÷", "×", "§", "∆", "£", "€", "₡",
            "₲", "^", "°", "{", "}", "\\", "©", "%",
            "®", "™", "✓", "[", "]", "<", ">",
        ),
        "math_currency" to listOf(
            "±", "×", "÷", "√", "∞", "≈",
            "≠", "≤", "≥", "°", "¹", "²", "³",
            "€", "£", "¥", "¢", "₩", "₹", "₪", "₱", "฿",
            "₫", "₮", "₼", "₿", "§", "¶",
        ),
        "special" to listOf(
            "⬢", "⧫", "☤", "⚧", "★", "☆",
            "✓", "✗", "✘", "♡", "♢", "♤", "♧",
            "ツ", "«", "»", "‹", "›", "〈", "〉", "☂", "⌘",
            "⌥", "⌃", "←", "↑", "↓", "→",
        ),
        "extra" to listOf(
            "♫", "ⓘ", "☠︎︎", "☀︎", "㋡", "⚠︎", "✰",
            "♛", "♥︎", "❦", "✴︎", "✶", "☭", "⚛",
            "♱", "✪", "𓋹", "𓂀", "𓁈", "☯︎", "✯",
            "☾", "☽", "☥", "☧", "☨", "☩", "☫"
        )
    )

    val fontPages = listOf(
        listOf("𝔸","𝔹","ℂ","𝔻","𝔼","𝔽","𝔾","ℍ","𝕀","𝕁","𝕂","𝕃","𝕄","ℕ","𝕆","ℙ","ℚ","ℝ","𝕊","𝕋","𝕌","𝕍","𝕎","𝕏","𝕐","ℤ","𝕒","𝕓","𝕔","𝕕","𝕖","𝕗","𝕘","𝕙","𝕚","𝕛","𝕜","𝕝","𝕞","𝕟","𝕠","𝕡","𝕢","𝕣","𝕤","𝕥","𝕦","𝕧","𝕨","𝕩","𝕪","𝕫"),
        listOf("𝓐","𝓑","𝓒","𝓓","𝓔","𝓕","𝓖","𝓗","𝓘","𝓙","𝓚","𝓛","𝓜","𝓝","𝓞","𝓟","𝓠","𝓡","𝓢","𝓣","𝓤","𝓥","𝓦","𝓧","𝓨","𝓩","𝓪","𝓫","𝓬","𝓭","𝓮","𝓯","𝓰","𝓱","𝓲","𝓳","𝓴","𝓵","𝓶","𝓷","𝓸","𝓹","𝓺","𝓻","𝓼","𝓽","𝓾","𝓿","𝔀","𝔁","𝔂","𝔃"),
        listOf("𝕬","𝕭","𝕮","𝕯","𝕰","𝕱","𝕲","𝕳","𝕴","𝕵","𝕶","𝕷","𝕸","𝕹","𝕺","𝕻","𝕼","𝕽","𝕾","𝕿","𝖀","𝖁","𝖂","𝖃","𝖄","𝖅","𝖆","𝖇","𝖈","𝖉","𝖊","𝖋","𝖌","𝖍","𝖎","𝖏","𝖐","𝖑","𝖒","𝖓","𝖔","𝖕","𝖖","𝖗","𝖘","𝖙","𝖚","𝖛","𝖜","𝖝","𝖞","𝖟"),
        listOf("𝗔","𝗕","𝗖","𝗗","𝗘","𝗙","𝗚","𝗛","𝗜","𝗝","𝗞","𝗟","𝗠","𝗡","𝗢","𝗣","𝗤","𝗥","𝗦","𝗧","𝗨","𝗩","𝗪","𝗫","𝗬","𝗭","𝗮","𝗯","𝗰","𝗱","𝗲","𝗳","𝗴","𝗵","𝗶","𝗷","𝗸","𝗹","𝗺","𝗻","𝗼","𝗽","𝗾","𝗿","𝘀","𝘁","𝘂","𝘃","𝘄","𝘅","𝘆","𝘇"),
        listOf("𝘈","𝘉","𝘊","𝘋","𝘌","𝘍","𝘎","𝘏","𝘐","𝘑","𝘒","𝘓","𝘔","𝘕","𝘖","𝘗","𝘘","𝘙","𝘚","𝘛","𝘜","𝘝","𝘞","𝘟","𝘠","𝘡","𝘢","𝘣","𝘤","𝘥","𝘦","𝘧","𝘨","𝘩","𝘪","ජ","ක","ල","ම","න","ඔ","ප","ක්‍","ර","ස","ත","උ","ව","ව","ක්‍","ය","ස"),
        listOf("𝙰","𝙱","𝙲","𝙳","𝙴","𝙵","𝙶","𝙷","𝙸","𝙹","𝙺","𝙻","𝙼","𝙽","𝙾","𝙿","𝚀","𝚁","𝚂","𝚃","𝚄","𝚅","𝚆","𝚇","𝚈","𝚉","𝚊","𝚋","𝚌","𝚍","𝚎","𝚏","𝚐","𝚑","𝚒","𝚓","𝚔","𝚕","𝚖","𝚗","𝚘","𝚙","𝚚","𝚛","𝚜","𝚝","𝚞","𝚟","𝚠","𝚡","𝚢","𝚣"),
        listOf("Ⓐ","Ⓑ","Ⓒ","Ⓓ","Ⓔ","Ⓕ","Ⓖ","Ⓗ","Ⓘ","Ⓙ","Ⓚ","Ⓛ","Ⓜ","Ⓝ","Ⓞ","Ⓟ","Ⓠ","Ⓡ","Ⓢ","Ⓣ","Ⓤ","Ⓥ","Ⓦ","Ⓧ","Ⓨ","Ⓩ","ⓐ","ⓑ","ⓒ","ⓓ","ⓔ","ⓕ","ⓖ","ⓗ","ⓘ","ⓙ","ⓚ","ⓛ","ⓜ","ⓝ","ⓞ","ⓟ","ⓠ","ⓡ","ⓢ","ⓣ","ⓤ","ⓥ","ⓦ","ⓧ","ⓨ","ⓩ"),
        listOf("🄰","🄱","🄲","🄳","🄴","🄵","🄶","🄷","🄸","🄹","🄺","🄻","🄼","🄽","🄾","🄿","🅀","🅁","🅂","🅃","🅄","🅅","🅆","🅇","🅈","🅉","🄰","🄱","🄲","🄳","🄴","🄵","🄶","🄷","🄸","🄹","🄺","🄻","🄼","🄽","🄾","🄿","🅀","🅁","🅂","🅃","🅄","🅅","🅆","🅇","🅈","🅉"),
        listOf("𝐀","𝐁","𝐂","𝐃","𝐄","𝐅","𝐆","𝐇","𝐈","𝐉","𝐊","𝐋","𝐌","𝐍","𝐎","𝐏","𝐐","𝐑","𝐒","𝐓","𝐔","𝐕","𝐖","𝐗","𝐘","𝐙","𝐚","𝐛","𝐜","𝐝","𝐞","𝐟","𝐠","𝐡","𝐢","𝐣","𝐤","𝐥","𝐦","𝐧","𝐨","𝐩","𝐪","𝐫","𝐬","𝐭","𝐮","𝐯","𝐰","𝐱","𝐲","𝐳"),
        listOf("𝑨","𝑩","𝑪","𝑫","𝑬","𝑭","𝑮","𝑯","𝑰","𝑱","𝑲","𝑳","𝑴","𝑵","𝑶","𝑷","𝑸","𝑹","𝑺","𝑻","𝑼","𝑽","𝑾","𝑿","𝒀","𝒁","𝒂","𝒃","𝒄","𝒅","𝒆","𝒇","𝒈","𝒉","𝒊","𝒋","𝒌","𝒍","𝒎","𝒏","𝒐","𝒑","𝒒","𝒓","𝒔","𝒕","𝒖","𝒗","𝒘","𝒙","𝒚","𝒛")
    )

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
        
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        if (prefs.getString("keyboard_language", "es") != lang) {
            prefs.edit { putString("keyboard_language", lang) }
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

        parallaxSensorManager = ParallaxSensorManager(this)
        serviceScope.launch {
            parallaxSensorManager.parallaxOffset.collect {
                viewModel.updateParallaxOffset(it)
            }
        }

        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        prefs.registerOnSharedPreferenceChangeListener(prefsListener)
        
        viewModel.setSkinTone(prefs.getString("selected_skin_tone", "") ?: "")
        viewModel.setGenderIndex(prefs.getInt("selected_gender_index", 0))

        clipboardManager = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        clipboardManager.addPrimaryClipChangedListener(clipboardListener)

        predictionEngine = PredictionEngine(this)
        spellCheckerManager = SpellCheckerManager(predictionEngine)
        voiceRecognitionHelper = VoiceRecognitionHelper(this)
        
        serviceScope.launch(Dispatchers.IO) {
            val lang = prefs.getString("keyboard_language", "es") ?: "es"
            val layoutType = prefs.getString("keyboard_layout_type", "default") ?: "default"
            viewModel.updateCurrentLocale(lang)
            predictionEngine.initialize(lang)
            predictionEngine.setLayoutType(layoutType)
            withContext(Dispatchers.Main) {
                switchToLanguage(lang)
            }
        }

        serviceScope.launch {
            ThemeUtils.getKeyboardThemeFlow(this@HexKeyboardService).collect { theme ->
                viewModel.updateKeyboardTheme(theme)
            }
        }

        serviceScope.launch(Dispatchers.IO) {
            val history = ClipboardHistoryManager.getHistory(this@HexKeyboardService)
            withContext(Dispatchers.Main) {
                viewModel.updateClipboardHistory(history)
            }
        }
        
        serviceScope.launch {
            spellCheckerManager.suggestionsState.collect { suggestions ->
                if (suggestions.isNotEmpty()) {
                    viewModel.updateSuggestions(suggestions)
                }
            }
        }

        mSavedStateRegistryController.performRestore(null)
        mLifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
    }

    private fun setupViewModelCallbacks() {
        viewModel.onActionRequested = { action ->
            when (action) {
                is KeyboardViewModel.Action.InsertText -> handleChar(action.text)
                is KeyboardViewModel.Action.DeleteBackward -> handleDelete()
                is KeyboardViewModel.Action.InsertNewLine -> handleEnter()
                is KeyboardViewModel.Action.ReplaceLastWord -> replaceLastWord(action.newWord)
                is KeyboardViewModel.Action.SetEmojiSearchActive -> {
                    // La lógica ya está en el ViewModel, pero si el servicio necesita reaccionar:
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
            setBackgroundColor(android.graphics.Color.TRANSPARENT)

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
        unregisterReceiver(wallpaperReceiver)
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        prefs.unregisterOnSharedPreferenceChangeListener(prefsListener)
        clipboardManager.removePrimaryClipChangedListener(clipboardListener)
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

        mComposeView?.setBackgroundColor(android.graphics.Color.TRANSPARENT)

        window?.window?.let { win ->
            WindowCompat.setDecorFitsSystemWindows(win, false)
            win.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
            
            @Suppress("DEPRECATION")
            win.navigationBarColor = if (theme.id == "glass") android.graphics.Color.TRANSPARENT else theme.backgroundColor

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
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        if (prefs.getBoolean("reset_on_close", true)) {
            mHexKeyboardView?.let {
                it.capsLock = false
                it.shifted = false
                it.resetState()
            }
            viewModel.setCurrentView("keyboard")
        }
        updateShiftState()
        mLifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        mLifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        super.onFinishInputView(finishingInput)
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        if (prefs.getBoolean("reset_on_close", true)) {
            viewModel.setCurrentView("keyboard")
            mHexKeyboardView?.resetState()
        }
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        if (info == null) return
        
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        val lang = prefs.getString("keyboard_language", "es") ?: "es"
        
        viewModel.updateCurrentLocale(lang)
        serviceScope.launch(Dispatchers.IO) {
            predictionEngine.initialize(lang, forceUserDictReload = true)
        }
        switchToLanguage(lang)

        spellCheckerManager.closeSession()
        spellCheckerManager.clearSuggestions()
        spellCheckerManager.initSession()
        val action = info.imeOptions and EditorInfo.IME_MASK_ACTION
        mHexKeyboardView?.setImeAction(action)
        
        val inputType = info.inputType
        val classMask = inputType and InputType.TYPE_MASK_CLASS
        if ((classMask == InputType.TYPE_CLASS_NUMBER) ||
            (classMask == InputType.TYPE_CLASS_PHONE)) {
            mHexKeyboardView?.layoutMode = HexKeyboardView.LayoutMode.PURE_NUMERIC
        } else {
            mHexKeyboardView?.layoutMode = HexKeyboardView.LayoutMode.ALPHA
        }
        
        updateShiftState()
    }

    fun updateSuggestions() {
        val ic = currentInputConnection ?: return
        val textBefore = ic.getTextBeforeCursor(40, 0) ?: ""
        
        serviceScope.launch(Dispatchers.Default) {
            val localSuggestions = mutableListOf<String>()
            
            val history = viewModel.clipboardHistory.value
            history.firstOrNull()?.text?.let { 
                if ((it.isNotEmpty()) && (it != lastUsedClipboardText)) {
                    localSuggestions.add(it)
                }
            }

            val allWords = textBefore.toString().trim().split(" ", "\n", "\t").filter { it.isNotEmpty() }
            val lastWord = if (textBefore.isNotEmpty() && !textBefore.endsWith(" ")) {
                allWords.lastOrNull() ?: ""
            } else ""
            val prevWord = if (lastWord.isEmpty()) allWords.lastOrNull() else allWords.getOrNull(allWords.size - 2)

            val predictions = predictionEngine.getSuggestions(lastWord, prevWord)
            predictions.forEach { localSuggestions.add(it.text) }

            if (lastWord.isNotEmpty()) {
                val capsMode = ic.getCursorCapsMode(TextUtils.CAP_MODE_SENTENCES or TextUtils.CAP_MODE_WORDS)
                if ((capsMode != 0) || (mHexKeyboardView?.shifted == true)) {
                    localSuggestions.add(lastWord.replaceFirstChar { it.uppercase() })
                }
                spellCheckerManager.fetchSuggestions(lastWord)
            }
            
            val finalSuggestions = localSuggestions.asSequence().distinct().take(6).toList()
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
            if (view.layoutMode == HexKeyboardView.LayoutMode.NUMERIC || view.layoutMode == HexKeyboardView.LayoutMode.SYMBOLS) {
                if (text == " ") {
                    if (symbolsTypedCount > 0) {
                        view.layoutMode = HexKeyboardView.LayoutMode.ALPHA
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
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)

        if (text != " ") {
            lastAutoCorrection = null
            lastUndoneCorrection = null
        }
        
        ic.beginBatchEdit()
        
        if ((text == " " || text == "." || text == "," || text == "!") && !lastKeyWasSpace) {
            val before = ic.getTextBeforeCursor(40, 0) ?: ""
            val words = before.split(" ", "\n", "\t").filter { it.isNotEmpty() }
            if (words.isNotEmpty()) {
                val currentWord = words.last()
                val previousWord = if (words.size > 1) words[words.size - 2] else null
                predictionEngine.learnFromInput(currentWord, previousWord)
            }
        }

        if (text == " " && lastKeyWasSpace && (System.currentTimeMillis() - lastKeyTime < 500)) {
            if (prefs.getBoolean("double_space_period", true)) {
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

        if (text == " " && prefs.getBoolean("auto_correct", false)) {
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
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)

        if (prefs.getBoolean("undo_correction_on_backspace", true)) {
            lastAutoCorrection?.let { correction ->
                val textBefore = ic.getTextBeforeCursor(correction.corrected.length + 1, 0)
                if (textBefore != null && textBefore.toString() == "${correction.corrected} ") {
                    mHexKeyboardView?.triggerVibration()
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
            mHexKeyboardView?.triggerVibration()
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

    fun handleEnter() {
        val ic = currentInputConnection ?: return
        val ei = currentInputEditorInfo ?: return
        val action = ei.imeOptions and EditorInfo.IME_MASK_ACTION
        if (action != EditorInfo.IME_ACTION_NONE && action != EditorInfo.IME_ACTION_UNSPECIFIED) {
            ic.performEditorAction(action)
        } else {
            ic.commitText("\n", 1)
        }
        updateShiftState()
        updateSuggestions()
    }

    fun handleGesture(points: List<PointF>, keys: List<HexKeyboardView.Key>) {
        if (points.size < 2) return
        
        serviceScope.launch(Dispatchers.IO) {
            val charPoints = keys.filter { it.type == HexKeyboardView.KeyType.CHAR && it.value.length == 1 }
                .map { PredictionEngine.CharPoint(it.value[0], it.cx, it.cy) }
            
            val suggestions = predictionEngine.getGestureSuggestions(points, charPoints)
            
            withContext(Dispatchers.Main) {
                if (suggestions.isNotEmpty()) {
                    val best = suggestions.first()
                    if (best.score > 200) {
                        handleChar(best.text + " ")
                    } else {
                        viewModel.updateSuggestions(suggestions.map { it.text })
                    }
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

    fun useClipboardItem(text: String) {
        val latestClip = viewModel.clipboardHistory.value.firstOrNull()?.text ?: ""
        if (text.trim() == latestClip.trim()) {
            lastUsedClipboardText = latestClip
        }
        handleChar(text)
    }

    fun updateShiftState() {
        val ic = currentInputConnection ?: return
        val ei = currentInputEditorInfo ?: return
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        if (!prefs.getBoolean("auto_capitalize", true)) return

        val view = mHexKeyboardView ?: return
        if (view.layoutMode != HexKeyboardView.LayoutMode.ALPHA) return

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

    fun refreshClipboardHistory(triggerSuggestionsUpdate: Boolean = false) {
        serviceScope.launch {
            ClipboardHistoryManager.cleanUpExpiredItems(this@HexKeyboardService)
            val history = ClipboardHistoryManager.getHistory(this@HexKeyboardService)
            viewModel.updateClipboardHistory(history)
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
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        prefs.edit { putString("selected_skin_tone", modifier) }
    }

    fun setGenderIndex(index: Int) {
        viewModel.setGenderIndex(index)
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        prefs.edit { putInt("selected_gender_index", index) }
    }

    fun getStickyVariant(canonicalEmoji: String): String? {
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        return prefs.getString("sticky_emoji_$canonicalEmoji", null)
    }

    fun saveStickyVariant(canonicalEmoji: String, variant: String) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        prefs.edit { putString("sticky_emoji_$canonicalEmoji", variant) }
        
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
