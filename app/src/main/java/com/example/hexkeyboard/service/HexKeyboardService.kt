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
import android.graphics.Bitmap
import android.graphics.PointF
import android.graphics.Typeface
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
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.toColorInt
import androidx.core.view.WindowCompat
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
import com.example.hexkeyboard.R
import com.example.hexkeyboard.data.repository.EmojiProvider
import com.example.hexkeyboard.data.repository.KeyboardTheme
import com.example.hexkeyboard.data.repository.ThemeUtils
import com.example.hexkeyboard.logic.engine.PredictionEngine
import com.example.hexkeyboard.logic.managers.ClipboardHistoryManager
import com.example.hexkeyboard.logic.managers.ClipboardItem
import com.example.hexkeyboard.logic.managers.ParallaxSensorManager
import com.example.hexkeyboard.logic.managers.VoiceRecognitionHelper
import com.example.hexkeyboard.logic.managers.FeedbackManager
import com.example.hexkeyboard.ui.keyboard.components.HexKeyboardView
import com.example.hexkeyboard.ui.keyboard.panels.ClipboardPanel
import com.example.hexkeyboard.ui.keyboard.panels.EmojiPanel
import com.example.hexkeyboard.ui.keyboard.panels.FunctionsPanel
import com.example.hexkeyboard.ui.settings.PermissionActivity
import com.example.hexkeyboard.ui.settings.SettingsActivity
import com.example.hexkeyboard.ui.theme.HexKeyboardTheme
import dev.chrisbanes.haze.HazeDefaults
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
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
                // Resetear el estado de uso para que el nuevo item aparezca en sugerencias
                lastUsedClipboardText = null
                refreshClipboardHistory(triggerSuggestionsUpdate = true)
            }
        }
    }

    internal var mHexKeyboardView: HexKeyboardView? = null
    private var mComposeView: ComposeView? = null

    private lateinit var parallaxSensorManager: ParallaxSensorManager
    private val _parallaxOffset = MutableStateFlow(ParallaxSensorManager.Offset(0f, 0f))
    val parallaxOffset: StateFlow<ParallaxSensorManager.Offset> = _parallaxOffset.asStateFlow()

    private val _suggestions = MutableStateFlow<List<String>>(emptyList())
    val suggestions = _suggestions.asStateFlow()

    private val _clipboardHistory = MutableStateFlow<List<ClipboardItem>>(emptyList())
    val clipboardHistory = _clipboardHistory.asStateFlow()

    private val _keyboardTheme = MutableStateFlow<KeyboardTheme?>(null)
    val keyboardTheme = _keyboardTheme.asStateFlow()

    private val _currentView = MutableStateFlow("keyboard")
    val currentView = _currentView.asStateFlow()

    private val _currentLocale = MutableStateFlow("es")
    val currentLocale = _currentLocale.asStateFlow()

    private val _emojiSearchQuery = MutableStateFlow("")
    val emojiSearchQuery = _emojiSearchQuery.asStateFlow()

    private val _selectedSkinTone = MutableStateFlow("")
    val selectedSkinTone = _selectedSkinTone.asStateFlow()

    private val _selectedGenderIndex = MutableStateFlow(0)
    val selectedGenderIndex = _selectedGenderIndex.asStateFlow()

    private val _isEmojiSearchActive = MutableStateFlow(value = false)
    val isEmojiSearchActive = _isEmojiSearchActive.asStateFlow()

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
        
        // Obtener el ID de nuestro propio IME
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
                _currentLocale.value = lang
                serviceScope.launch(Dispatchers.IO) {
                    predictionEngine.initialize(lang)
                }
                spellCheckerManager.closeSession()
                spellCheckerManager.clearSuggestions()
                spellCheckerManager.initSession(Locale.forLanguageTag(lang))
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

    override val viewModelStore: ViewModelStore get() = mViewModelStore
    override val savedStateRegistry: SavedStateRegistry get() = mSavedStateRegistryController.savedStateRegistry
    override val lifecycle: Lifecycle get() = mLifecycleRegistry

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
        // 1. Double Struck: 𝔸𝔹ℂ / 𝕒𝕓𝕔
        listOf("𝔸","𝔹","ℂ","𝔻","𝔼","𝔽","𝔾","ℍ","𝕀","𝕁","𝕂","𝕃","𝕄","ℕ","𝕆","ℙ","ℚ","ℝ","𝕊","𝕋","𝕌","𝕍","𝕎","𝕏","𝕐","ℤ","𝕒","𝕓","𝕔","𝕕","𝕖","𝕗","𝕘","𝕙","𝕚","𝕛","𝕜","𝕝","𝕞","𝕟","𝕠","𝕡","𝕢","𝕣","𝕤","𝕥","𝕦","𝕧","𝕨","𝕩","𝕪","𝕫"),
        // 2. Script Bold: 𝓐𝓑𝓒 / 𝓪𝓫𝓬
        listOf("𝓐","𝓑","𝓒","𝓓","𝓔","𝓕","𝓖","𝓗","𝓘","𝓙","𝓚","𝓛","𝓜","𝓝","𝓞","𝓟","𝓠","𝓡","𝓢","𝓣","𝓤","𝓥","𝓦","𝓧","𝓨","𝓩","𝓪","𝓫","𝓬","𝓭","𝓮","𝓯","𝓰","𝓱","𝓲","𝓳","𝓴","𝓵","𝓶","𝓷","𝓸","𝓹","𝓺","𝓻","𝓼","𝓽","𝓾","𝓿","𝔀","𝔁","𝔂","𝔃"),
        // 3. Fraktur Bold: 𝕬𝕭𝕮 / 𝖆𝖇𝖈
        listOf("𝕬","𝕭","𝕮","𝕯","𝕰","𝕱","𝕲","𝕳","𝕴","𝕵","𝕶","𝕷","𝕸","𝕹","𝕺","𝕻","𝕼","𝕽","𝕾","𝕿","𝖀","𝖁","𝖂","𝖃","𝖄","𝖅","𝖆","𝖇","𝖈","𝖉","𝖊","𝖋","𝖌","𝖍","𝖎","𝖏","𝖐","𝖑","𝖒","𝖓","𝖔","𝖕","𝖖","𝖗","𝖘","𝖙","𝖚","𝖛","𝖜","𝖝","𝖞","𝖟"),
        // 4. Sans Bold: 𝗔𝗕𝗖 / 𝗮𝗯𝗰
        listOf("𝗔","𝗕","𝗖","𝗗","𝗘","𝗙","𝗚","𝗛","𝗜","𝗝","𝗞","𝗟","𝗠","𝗡","𝗢","𝗣","𝗤","𝗥","𝗦","𝗧","𝗨","𝗩","𝗪","𝗫","𝗬","𝗭","𝗮","𝗯","𝗰","𝗱","𝗲","𝗳","𝗴","𝗵","𝗶","𝗷","𝗸","𝗹","𝗺","𝗻","𝗼","𝗽","𝗾","𝗿","𝘀","𝘁","𝘂","𝘃","𝘄","𝘅","𝘆","𝘇"),
        // 5. Sans Italic: 𝘈𝘉𝘊 / 𝘢𝘣𝘤
        listOf("𝘈","𝘉","𝘊","𝘋","𝘌","𝘍","𝘎","𝘏","𝘐","𝘑","𝘒","𝘓","𝘔","𝘕","𝘖","𝘗","𝘘","𝘙","𝘚","𝘛","𝘜","𝘝","𝘞","𝘟","𝘠","𝘡","𝘢","𝘣","𝘤","𝘥","𝘦","𝘧","𝘨","𝘩","𝘪","𝘫","𝘬","𝘭","𝘮","𝘯","𝘰","𝘱","𝘲","𝘳","𝘴","𝘵","𝘶","𝘷","𝘸","𝘹","𝘺","𝘻"),
        // 6. Monospace: 𝙰𝙱𝙲 / 𝚊𝚋𝚌
        listOf("𝙰","𝙱","𝙲","𝙳","𝙴","𝙵","𝙶","𝙷","𝙸","𝙹","𝙺","𝙻","𝙼","𝙽","𝙾","𝙿","𝚀","𝚁","𝚂","𝚃","𝚄","𝚅","𝚆","𝚇","𝚈","𝚉","𝚊","𝚋","𝚌","𝚍","𝚎","𝚏","𝚐","𝚑","𝚒","𝚓","𝚔","𝚕","𝚖","𝚗","𝚘","𝚙","𝚚","𝚛","𝚜","𝚝","𝚞","𝚟","𝚠","𝚡","𝚢","𝚣"),
        // 7. Circled: ⒶⒷⒸ / ⓐⓑⓒ
        listOf("Ⓐ","Ⓑ","Ⓒ","Ⓓ","Ⓔ","Ⓕ","Ⓖ","Ⓗ","Ⓘ","Ⓙ","Ⓚ","Ⓛ","Ⓜ","Ⓝ","Ⓞ","Ⓟ","Ⓠ","Ⓡ","Ⓢ","Ⓣ","Ⓤ","Ⓥ","Ⓦ","Ⓧ","Ⓨ","Ⓩ","ⓐ","ⓑ","ⓒ","ⓓ","ⓔ","ⓕ","ⓖ","ⓗ","ⓘ","ⓙ","ⓚ","ⓛ","ⓜ","ⓝ","ⓞ","ⓟ","ⓠ","ⓡ","ⓢ","ⓣ","ⓤ","ⓥ","ⓦ","ⓧ","ⓨ","ⓩ"),
        // 8. Squared: 🄰🄱🄲 (sin minúsculas en Unicode, se reutiliza el mismo set)
        listOf("🄰","🄱","🄲","🄳","🄴","🄵","🄶","🄷","🄸","🄹","🄺","🄻","🄼","🄽","🄾","🄿","🅀","🅁","🅂","🅃","🅄","🅅","🅆","🅇","🅈","🅉","🄰","🄱","🄲","🄳","🄴","🄵","🄶","🄷","🄸","🄹","🄺","🄻","🄼","🄽","🄾","🄿","🅀","🅁","🅂","🅃","🅄","🅅","🅆","🅇","🅈","🅉"),
        // 9. Bold Serif: 𝐀𝐁𝐂 / 𝐚𝐛𝐜
        listOf("𝐀","𝐁","𝐂","𝐃","𝐄","𝐅","𝐆","𝐇","𝐈","𝐉","𝐊","𝐋","𝐌","𝐍","𝐎","𝐏","𝐐","𝐑","𝐒","𝐓","𝐔","𝐕","𝐖","𝐗","𝐘","𝐙","𝐚","𝐛","𝐜","𝐝","𝐞","𝐟","𝐠","𝐡","𝐢","𝐣","𝐤","𝐥","𝐦","𝐧","𝐨","𝐩","𝐪","𝐫","𝐬","𝐭","𝐮","𝐯","𝐰","𝐱","𝐲","𝐳"),
        // 10. Bold Italic Serif: 𝑨𝑩𝑪 / 𝒂𝒃𝒄
        listOf("𝑨","𝑩","𝑪","𝑫","𝑬","𝑭","𝑮","𝑯","𝑰","𝑱","𝑲","𝑳","𝑴","𝑵","𝑶","𝑷","𝑸","𝑹","𝑺","𝑻","𝑼","𝑽","𝑾","𝑿","𝒀","𝒁","𝒂","𝒃","𝒄","𝒅","𝒆","𝒇","𝒈","𝒉","𝒊","𝒋","𝒌","𝒍","𝒎","𝒏","𝒐","𝒑","𝒒","𝒓","𝒔","𝒕","𝒖","𝒗","𝒘","𝒙","𝒚","𝒛")
    )

    fun setEmojiSearchActive(active: Boolean) {
        _isEmojiSearchActive.value = active
        if (!active) _emojiSearchQuery.value = ""
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        // Forzar actualización del tema cuando cambia el modo noche del sistema
        ThemeUtils.notifyDynamicColorsChanged()
    }

    override fun onCurrentInputMethodSubtypeChanged(newSubtype: InputMethodSubtype) {
        super.onCurrentInputMethodSubtypeChanged(newSubtype)
        val lang = newSubtype.languageTag.split("-")[0]
        Log.d("HexKB", "System subtype changed to: $lang")
        
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        if (prefs.getString("keyboard_language", "es") != lang) {
            prefs.edit { putString("keyboard_language", lang) }
        }

        // Asegurar que la vista se actualiza
        _currentLocale.value = lang
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
                Log.d("HexKB", "Switching system subtype to: $sLang")
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

        // Registrar el receptor para cambios de fondo de pantalla
        val filter = IntentFilter(Intent.ACTION_WALLPAPER_CHANGED)
        registerReceiver(wallpaperReceiver, filter)

        parallaxSensorManager = ParallaxSensorManager(this)
        serviceScope.launch {
            parallaxSensorManager.parallaxOffset.collect {
                _parallaxOffset.value = it
            }
        }

        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        prefs.registerOnSharedPreferenceChangeListener(prefsListener)
        
        _selectedSkinTone.value = prefs.getString("selected_skin_tone", "") ?: ""
        _selectedGenderIndex.value = prefs.getInt("selected_gender_index", 0)

        clipboardManager = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        clipboardManager.addPrimaryClipChangedListener(clipboardListener)

        predictionEngine = PredictionEngine(this)
        spellCheckerManager = SpellCheckerManager(this, predictionEngine)
        voiceRecognitionHelper = VoiceRecognitionHelper(this)
        
        serviceScope.launch(Dispatchers.IO) {
            val lang = prefs.getString("keyboard_language", "es") ?: "es"
            val layoutType = prefs.getString("keyboard_layout_type", "default") ?: "default"
            _currentLocale.value = lang
            predictionEngine.initialize(lang)
            predictionEngine.setLayoutType(layoutType)
            withContext(Dispatchers.Main) {
                switchToLanguage(lang)
            }
        }

        serviceScope.launch {
            // Observar cambios de tema de forma reactiva
            ThemeUtils.getKeyboardThemeFlow(this@HexKeyboardService).collect { theme ->
                _keyboardTheme.value = theme
            }
        }


        serviceScope.launch(Dispatchers.IO) {
            // Cargar historial inicial del portapapeles
            val history = ClipboardHistoryManager.getHistory(this@HexKeyboardService)
            withContext(Dispatchers.Main) {
                _clipboardHistory.value = history
            }
        }
        
        serviceScope.launch {
            Log.d("HexKB", "Starting to collect suggestions from SpellCheckerManager")
            spellCheckerManager.suggestionsState.collect { systemSuggestions ->
                Log.d("HexKB", "Collected from system: $systemSuggestions")
                if (systemSuggestions.isNotEmpty()) {
                    combineAndFilterSuggestions(systemSuggestions)
                }
            }
        }

        mSavedStateRegistryController.performRestore(null)
        mLifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
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
                val keyboardTheme by this@HexKeyboardService.keyboardTheme.collectAsState()
                CompositionLocalProvider(
                    LocalLifecycleOwner provides this@HexKeyboardService
                ) {
                    HexKeyboardTheme(dynamicColor = keyboardTheme?.id == "m3_dynamic") {
                        KeyboardScreen(service = this@HexKeyboardService)
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
        val theme = _keyboardTheme.value ?: return
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
            
            // Sincronizar el color de la barra de navegación con el fondo del teclado
            @Suppress("DEPRECATION")
            win.navigationBarColor = if (theme.id == "glass") android.graphics.Color.TRANSPARENT else theme.backgroundColor

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (theme.id == "glass") {
                    win.setBackgroundBlurRadius(60)
                } else {
                    win.setBackgroundBlurRadius(0)
                }
            }

            val isDark = ColorUtils.calculateLuminance(theme.backgroundColor) < 0.5
            WindowCompat.getInsetsController(win, win.decorView).apply {
                isAppearanceLightNavigationBars = !isDark
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                @Suppress("DEPRECATION")
                win.navigationBarDividerColor = android.graphics.Color.TRANSPARENT
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
            _currentView.value = "keyboard"
        }
        updateShiftState()
        mLifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        mLifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        super.onFinishInputView(finishingInput)
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        if (prefs.getBoolean("reset_on_close", true)) {
            _currentView.value = "keyboard"
            mHexKeyboardView?.resetState()
        }
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        if (info == null) return
        
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        val lang = prefs.getString("keyboard_language", "es") ?: "es"
        
        // Sincronizar siempre al iniciar vista y forzar recarga de diccionario
        _currentLocale.value = lang
        serviceScope.launch(Dispatchers.IO) {
            predictionEngine.initialize(lang, forceUserDictReload = true)
        }
        switchToLanguage(lang)

        spellCheckerManager.closeSession()
        spellCheckerManager.clearSuggestions()
        spellCheckerManager.initSession(Locale.forLanguageTag(lang))
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
            
            // 1. Portapapeles (Prioridad alta)
            val history = _clipboardHistory.value
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

            // 2. Motor de predicción interno
            val predictions = predictionEngine.getSuggestions(lastWord, prevWord)
            predictions.forEach { localSuggestions.add(it.text) }

            if (lastWord.isNotEmpty()) {
                // 3. Mayúscula automática como sugerencia inmediata
                val capsMode = ic.getCursorCapsMode(TextUtils.CAP_MODE_SENTENCES or TextUtils.CAP_MODE_WORDS)
                if ((capsMode != 0) || (mHexKeyboardView?.shifted == true)) {
                    localSuggestions.add(lastWord.replaceFirstChar { it.uppercase() })
                }
                
                // 4. Pedir al sistema (esto actualizará la barra asíncronamente)
                spellCheckerManager.fetchSuggestions(lastWord)
            }
            
            val finalSuggestions = localSuggestions.asSequence().distinct().take(6).toList()
            withContext(Dispatchers.Main) {
                _suggestions.value = finalSuggestions
            }
        }
    }

    private fun combineAndFilterSuggestions(systemSuggestions: List<String>) {
        val ic = currentInputConnection ?: return
        val textBefore = ic.getTextBeforeCursor(40, 0) ?: ""
        val lastWord = if (textBefore.isNotEmpty() && !textBefore.endsWith(" ")) {
            textBefore.toString().split(" ", "\n", "\t").last().lowercase()
        } else ""

        val currentList = _suggestions.value.toMutableList()
        
        // Si el sistema devuelve sugerencias, las priorizamos, pero mantenemos lo local (como el portapapeles)
        val filteredSystem = systemSuggestions.filter { sugg ->
            sugg.lowercase() != lastWord // No repetir lo que ya se está escribiendo si el sistema lo devuelve igual
        }

        // Si el sistema devuelve sugerencias reales, las ponemos al principio
        val final = if (filteredSystem.isNotEmpty()) {
            (filteredSystem + currentList).asSequence().distinct().take(6).toList()
        } else {
            currentList.asSequence().distinct().take(6).toList()
        }
        _suggestions.value = final
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
        _isEmojiSearchActive.value = false
        _emojiSearchQuery.value = ""
        updateShiftState()
        updateSuggestions()
    }

    fun handleChar(text: String) {
        // 1. Lógica contextual: Búsqueda de emojis
        if (_isEmojiSearchActive.value) {
            _emojiSearchQuery.value += text
            return
        }

        // Logic for switching back to ALPHA from SYMBOLS/NUMERIC on space
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

        Log.d("HexKB", "handleChar: '$text'")
        val ic = currentInputConnection ?: return
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)

        if (text != " ") {
            lastAutoCorrection = null
            lastUndoneCorrection = null
        }
        
        ic.beginBatchEdit()
        
        // Aprender palabra si se completa
        if ((text == " " || text == "." || text == "," || text == "!") && !lastKeyWasSpace) {
            val before = ic.getTextBeforeCursor(40, 0) ?: ""
            val words = before.split(" ", "\n", "\t").filter { it.isNotEmpty() }
            if (words.isNotEmpty()) {
                val currentWord = words.last()
                val previousWord = if (words.size > 1) words[words.size - 2] else null
                predictionEngine.learnFromInput(currentWord, previousWord)
            }
        }

        // 1. Doble espacio -> Punto
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

        // 2. Auto-corrección al presionar espacio (opcional, si hay sugerencias claras)
        if (text == " " && prefs.getBoolean("auto_correct", false)) {
            val suggestions = _suggestions.value
            if (suggestions.isNotEmpty() && !suggestions[0].contains(" ")) {
                val textBefore = ic.getTextBeforeCursor(30, 0) ?: ""
                val originalWord = textBefore.toString().split(" ", "\n").lastOrNull() ?: ""
                
                // Si la palabra es la que acabamos de deshacer, no la corrijas de nuevo
                if (originalWord.isNotEmpty() && originalWord == lastUndoneCorrection) {
                    lastUndoneCorrection = null
                    // Continuamos sin corregir
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
        // 1. Lógica contextual: Si la búsqueda de emojis está activa
        if (_isEmojiSearchActive.value) {
            val current = _emojiSearchQuery.value
            if (current.isNotEmpty()) {
                mHexKeyboardView?.triggerVibration()
                mHexKeyboardView?.triggerSound()
                _emojiSearchQuery.value = current.dropLast(1)
            } else {
                _isEmojiSearchActive.value = false
                // No vibramos al cerrar la búsqueda si ya estaba vacía
            }
            return
        }

        val ic = currentInputConnection ?: return
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)

        // 2. Lógica Gboard: Revertir auto-corrección si se presiona borrar inmediatamente después
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
        
        // 3. Verificar si hay algo que borrar para decidir si vibramos/sonamos
        val selectedText = ic.getSelectedText(0)
        val hasSelection = !selectedText.isNullOrEmpty()
        val textBefore = ic.getTextBeforeCursor(40, 0)
        val hasTextBefore = !textBefore.isNullOrEmpty()

        if (hasSelection || hasTextBefore) {
            mHexKeyboardView?.triggerVibration()
            mHexKeyboardView?.triggerSound()
            if (symbolsTypedCount > 0) symbolsTypedCount--
        } else {
            // Si no hay nada que borrar, salimos temprano
            return
        }

        // 4. Ejecutar el borrado real
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

    /**
     * Calcula la longitud en caracteres Java (UTF-16) del último cluster de grafemas.
     * Maneja correctamente Emojis, secuencias ZWJ, selectores de variación y diacríticos.
     */
    private fun calculateLastGraphemeLength(text: String): Int {
        if (text.isEmpty()) return 0
        
        val it = BreakIterator.getCharacterInstance()
        it.setText(text)
        val last = it.last()
        val previous = it.previous()
        
        if (previous == BreakIterator.DONE) return 0
        
        val resultLen = last - previous
        
        return resultLen
    }

    fun handleEnter() {
        if (_isEmojiSearchActive.value) {
            _isEmojiSearchActive.value = false
            return
        }

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
                    // Si la confianza es alta, insertar automáticamente
                    if (best.score > 200) {
                        handleChar(best.text + " ")
                    } else {
                        // Si no, solo actualizar la barra de sugerencias
                        _suggestions.value = suggestions.map { it.text }
                    }
                }
            }
        }
    }

    fun replaceLastWord(newWord: String) {
        val ic = currentInputConnection ?: return
        
        // Si el texto seleccionado es exactamente lo que hay en el portapapeles, lo marcamos como usado
        // Usamos el caché reactivo para evitar lecturas de disco innecesarias
        val latestClip = _clipboardHistory.value.firstOrNull()?.text ?: ""
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

    /**
     * Inserta un elemento del portapapeles y lo marca como usado para que desaparezca de las sugerencias.
     */
    fun useClipboardItem(text: String) {
        val latestClip = _clipboardHistory.value.firstOrNull()?.text ?: ""
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
        _currentView.value = view
        if (view == "clipboard") {
            refreshClipboardHistory()
        }
        if (view != "emoji") {
            _emojiSearchQuery.value = ""
            _isEmojiSearchActive.value = false
        }
    }

    fun refreshClipboardHistory(triggerSuggestionsUpdate: Boolean = false) {
        serviceScope.launch {
            ClipboardHistoryManager.cleanUpExpiredItems(this@HexKeyboardService)
            _clipboardHistory.value = ClipboardHistoryManager.getHistory(this@HexKeyboardService)
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
            // Limpiar también sugerencias actuales para liberar RAM
            _suggestions.value = emptyList()
            _clipboardHistory.value = emptyList() // Se recargará cuando se abra el panel
            System.gc()
        }
    }

    fun setSkinTone(modifier: String) {
        _selectedSkinTone.value = modifier
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        prefs.edit { putString("selected_skin_tone", modifier) }
    }

    fun setGenderIndex(index: Int) {
        _selectedGenderIndex.value = index
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
        
        // Sincronización Global de Tono de Piel
        EmojiProvider.skinToneModifiers.find { variant.contains(it) && it.isNotEmpty() }?.let {
            setSkinTone(it)
        }

        // Sincronización Global de Género
        val family = EmojiProvider.getEmojiFamily(variant.replace(Regex("[\uD83C\uDFFB-\uD83C\uDFFF]"), ""))
        val cleanVariant = variant.replace(Regex("[\uD83C\uDFFB-\uD83C\uDFFF]"), "")
        when (cleanVariant) {
            family.male -> setGenderIndex(1)
            family.female -> setGenderIndex(2)
            family.neutral -> setGenderIndex(0)
        }
    }
}

@Composable
fun KeyboardScreen(service: HexKeyboardService) {
    val context = LocalContext.current
    val keyboardThemeOpt by service.keyboardTheme.collectAsState()
    val keyboardTheme = keyboardThemeOpt ?: return

    val parallaxOffsetState = service.parallaxOffset.collectAsState()

    val hazeState = remember { HazeState() }
    val isGlassTheme = keyboardTheme.id == "glass"
    val hasBackgroundImage = keyboardTheme.backgroundImageUri != null

    var backgroundBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var blurredBitmap by remember { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(keyboardTheme.backgroundImageUri, keyboardTheme.backgroundBlur) {
        if (keyboardTheme.backgroundImageUri != null) {
            val newBitmap = withContext(Dispatchers.IO) {
                ThemeUtils.loadBitmapFromUri(context, keyboardTheme.backgroundImageUri)
            }
            if (newBitmap != null) {
                backgroundBitmap?.recycle()
                blurredBitmap?.recycle()

                backgroundBitmap = newBitmap
                blurredBitmap = if (keyboardTheme.backgroundBlur > 0) {
                    withContext(Dispatchers.IO) {
                        ThemeUtils.blurBitmap(newBitmap, keyboardTheme.backgroundBlur)
                    }
                } else {
                    null
                }
            }
        } else {
            backgroundBitmap?.recycle()
            blurredBitmap?.recycle()
            backgroundBitmap = null
            blurredBitmap = null
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            backgroundBitmap?.recycle()
            blurredBitmap?.recycle()
        }
    }

    val prefs = remember { PreferenceManager.getDefaultSharedPreferences(context) }
    var bottomOffset by remember { mutableIntStateOf(prefs.getInt("keyboard_bottom_offset", 35)) }

    DisposableEffect(context) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { p, key ->
            if (key == "keyboard_bottom_offset") {
                bottomOffset = p.getInt(key, 0)
            }
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .wrapContentHeight()
            .background(
                color = Color(keyboardTheme.backgroundColor),
                shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
            )
            .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
    ) {
        // Continuous Background Layer
        val img = blurredBitmap ?: backgroundBitmap
        img?.let {
            Image(
                bitmap = it.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier
                    .matchParentSize()
                    .alpha(keyboardTheme.backgroundOpacity)
                    .graphicsLayer {
                        if (keyboardTheme.parallaxEffect) {
                            val parallaxLimit = 0.10f
                            val scale = 1.0f + parallaxLimit
                            val maxShiftX = (size.width * parallaxLimit) / 2f
                            val maxShiftY = (size.height * parallaxLimit) / 2f

                            // Lectura de estado dentro de graphicsLayer para evitar recomposición
                            val offset = parallaxOffsetState.value
                            translationX = offset.x * maxShiftX
                            translationY = offset.y * maxShiftY
                            scaleX = scale
                            scaleY = scale
                        }
                    },
                contentScale = ContentScale.Crop
            )
        }

        if (isGlassTheme) {
            Box(modifier = Modifier.matchParentSize().hazeEffect(state = hazeState, style = HazeDefaults.style(backgroundColor = Color(keyboardTheme.backgroundColor), blurRadius = 20.dp)).background(Color(keyboardTheme.backgroundColor)))
        } else if (!hasBackgroundImage) {
            Box(modifier = Modifier.matchParentSize().background(Color(keyboardTheme.backgroundColor)))
        } else {
            Box(modifier = Modifier.matchParentSize().background(Color(keyboardTheme.backgroundColor).copy(alpha = 1f - keyboardTheme.backgroundOpacity)))
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .then(if (isGlassTheme) Modifier.hazeSource(hazeState) else Modifier)
                .navigationBarsPadding()
        ) {
            SuggestionsBarSection(service, keyboardTheme)

            Box(
                modifier = Modifier.fillMaxWidth().wrapContentHeight(),
                contentAlignment = Alignment.TopCenter
            ) {
                KeyboardMainSection(service, keyboardTheme, hasBackgroundImage) { _, _ -> }
                PanelsSection(service, keyboardTheme)
            }

            if (bottomOffset > 0) {
                Spacer(modifier = Modifier.height(bottomOffset.dp))
            }
        }
    }
}

@Composable
fun SuggestionsBarSection(service: HexKeyboardService, theme: KeyboardTheme) {
    val currentView by service.currentView.collectAsState()
    val suggestions by service.suggestions.collectAsState()
    val emojiSearchQuery by service.emojiSearchQuery.collectAsState()
    val context = LocalContext.current

    Box(modifier = Modifier.fillMaxWidth().height(46.dp)) {
        AnimatedVisibility(
            visible = currentView == "keyboard",
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            Row(
                modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val isListening by service.voiceRecognitionHelper.isListening.collectAsState()
                val partialVoiceResult by service.voiceRecognitionHelper.partialResult.collectAsState()

                IconButton(
                    onClick = {
                        FeedbackManager.triggerFeedback(context)
                        service.setCurrentView("functions")
                    },
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Extension,
                        contentDescription = "Funciones",
                        tint = Color(theme.keyboardIconTint),
                        modifier = Modifier.size(26.dp)
                    )
                }

                LazyRow(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (isListening) {
                        item {
                            Text(
                                text = partialVoiceResult.ifEmpty { "Escuchando..." },
                                color = Color(theme.keyboardIconTint).copy(alpha = 0.6f),
                                fontSize = 14.sp
                            )
                        }
                    } else {
                        items(suggestions, key = { it }) { suggestion ->
                            SuggestionChip(suggestion, theme) {
                                FeedbackManager.triggerFeedback(context)
                                service.replaceLastWord("$suggestion ")
                            }
                        }
                    }
                }

                IconButton(
                    onClick = {
                        FeedbackManager.triggerFeedback(context)
                        if (isListening) {
                            service.voiceRecognitionHelper.stopListening()
                        } else {
                            val permission = Manifest.permission.RECORD_AUDIO
                            val granted = ContextCompat.checkSelfPermission(
                                context, permission
                            ) == PackageManager.PERMISSION_GRANTED

                            if (granted) {
                                service.voiceRecognitionHelper.startListening(
                                    object : VoiceRecognitionHelper.VoiceResultListener {
                                        override fun onVoiceResult(text: String) {
                                            service.handleChar("$text ")
                                        }
                                        override fun onVoiceError(error: Int) {}
                                    }
                                )
                            } else {
                                val intent = Intent(context, PermissionActivity::class.java).apply {
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    putExtra("request_permission", permission)
                                }
                                context.startActivity(intent)
                            }
                        }
                    },
                    modifier = Modifier.size(46.dp)
                ) {
                    Icon(
                        imageVector = if (isListening) Icons.Default.Done else Icons.Default.Mic,
                        contentDescription = "Dictado por voz",
                        tint = if (isListening) Color.Red else Color(theme.keyboardIconTint),
                        modifier = Modifier.size(26.dp)
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = currentView == "emoji",
            enter = fadeIn(), exit = fadeOut()
        ) {
            EmojiSearchBar(service, theme, emojiSearchQuery)
        }

        AnimatedVisibility(
            visible = currentView != "keyboard" && currentView != "emoji",
            enter = fadeIn(), exit = fadeOut()
        ) {
            PanelHeader(currentView, theme) { service.setCurrentView("keyboard") }
        }
    }
}

@Composable
fun KeyboardMainSection(
    service: HexKeyboardService,
    theme: KeyboardTheme,
    hasBackgroundImage: Boolean,
    onParallax: (Float, Float) -> Unit
) {
    val currentView by service.currentView.collectAsState()
    val isEmojiSearchActive by service.isEmojiSearchActive.collectAsState()
    val currentLocale by service.currentLocale.collectAsState()
    val keyboardVisible = currentView == "keyboard" || isEmojiSearchActive

    val prefs = remember { PreferenceManager.getDefaultSharedPreferences(service) }
    var fontPath by remember { mutableStateOf(prefs.getString("custom_font_path", "system") ?: "system") }

    DisposableEffect(prefs) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { p, key ->
            if (key == "custom_font_path") {
                fontPath = p.getString(key, "system") ?: "system"
            }
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    val nunitoTypeface = remember(fontPath) {
        when (fontPath) {
            "nunito" -> ResourcesCompat.getFont(service, R.font.nunito) ?: Typeface.DEFAULT
            "system" -> Typeface.DEFAULT
            else -> {
                val file = File(fontPath)
                if (file.exists()) {
                    try {
                        Typeface.createFromFile(file)
                    } catch (_: Exception) {
                        Typeface.DEFAULT
                    }
                } else {
                    Typeface.DEFAULT
                }
            }
        }
    }

    AndroidView(
        factory = { ctx ->
            HexKeyboardView(ctx).apply {
                this.sharedTypeface = nunitoTypeface
                this.symbolPages = service.symbolPages
                this.fontPages = service.fontPages
                this.longPressAlternatives = service.longPressAlternatives
                this.language = currentLocale
                this.layoutType = prefs.getString("keyboard_layout_type", "default") ?: "default"
                service.mHexKeyboardView = this
                this.drawBackground = !hasBackgroundImage
                listener = object : HexKeyboardView.Listener {
                    override fun onChar(text: String) {
                        service.handleChar(text)
                    }
                    override fun onDelete() {
                        service.handleDelete()
                    }
                    override fun onEnter() {
                        service.handleEnter()
                    }
                    override fun onLongPressSelect(char: String) {
                        service.handleChar(char)
                    }
                    override fun onSymbolPageChange(page: String) {}
                    override fun onParallaxChange(x: Float, y: Float) { onParallax(x, y) }
                    override fun onGesture(points: List<PointF>) {
                        service.handleGesture(points, allKeys)
                    }
                    override fun onKeyClick(key: HexKeyboardView.Key) {
                        when(key.type) {
                            HexKeyboardView.KeyType.EMOJI -> service.setCurrentView("emoji")
                            HexKeyboardView.KeyType.CLIPBOARD -> service.setCurrentView("clipboard")
                            HexKeyboardView.KeyType.FUNCTIONS -> service.setCurrentView("functions")
                            HexKeyboardView.KeyType.TOGGLE -> service.symbolsTypedCount = 0
                            else -> {}
                        }
                    }
                }
            }
        },
        update = { view ->
            view.sharedTypeface = nunitoTypeface
            view.keyboardTheme = theme
            view.language = currentLocale
            view.drawBackground = !hasBackgroundImage
            view.visibility = if (keyboardVisible) View.VISIBLE else View.INVISIBLE

            // Asegurar que las fuentes se actualicen
            view.fontPages = service.fontPages

            // Asegurar que el listener esté actualizado con la lambda de paralaje
            view.listener = object : HexKeyboardView.Listener {
                override fun onChar(text: String) { service.handleChar(text) }
                override fun onDelete() { service.handleDelete() }
                override fun onEnter() { service.handleEnter() }
                override fun onLongPressSelect(char: String) { service.handleChar(char) }
                override fun onSymbolPageChange(page: String) {}
                override fun onParallaxChange(x: Float, y: Float) { onParallax(x, y) }
                override fun onGesture(points: List<PointF>) {
                    service.handleGesture(points, view.allKeys)
                }
                override fun onKeyClick(key: HexKeyboardView.Key) {
                    when(key.type) {
                        HexKeyboardView.KeyType.EMOJI -> service.setCurrentView("emoji")
                        HexKeyboardView.KeyType.CLIPBOARD -> service.setCurrentView("clipboard")
                        HexKeyboardView.KeyType.FUNCTIONS -> service.setCurrentView("functions")
                        HexKeyboardView.KeyType.TOGGLE -> service.symbolsTypedCount = 0
                        else -> {}
                    }
                }
            }
        },
        modifier = Modifier.fillMaxWidth().then(if (isEmojiSearchActive) Modifier.padding(top = 180.dp) else Modifier)
    )
}

@Composable
fun BoxScope.PanelsSection(service: HexKeyboardService, theme: KeyboardTheme) {
    val currentView by service.currentView.collectAsState()
    val clipboardHistory by service.clipboardHistory.collectAsState()
    val isEmojiSearchActive by service.isEmojiSearchActive.collectAsState()
    val context = LocalContext.current

    if (currentView != "keyboard") {
        val panelModifier = if (isEmojiSearchActive) Modifier.fillMaxWidth().height(180.dp) else Modifier.matchParentSize()
        Box(modifier = panelModifier) {
            when (currentView) {
                "emoji" -> EmojiPanel(onEmojiSelected = { service.onEmojiSelected(it) }, onBack = { service.setCurrentView("keyboard") }, theme = theme)
                "clipboard" -> ClipboardPanel(
                    history = clipboardHistory,
                    onItemSelected = { service.useClipboardItem(it); service.setCurrentView("keyboard") },
                    onDelete = { ClipboardHistoryManager.deleteItem(context, it); service.refreshClipboardHistory() },
                    onTogglePin = { ClipboardHistoryManager.togglePin(context, it); service.refreshClipboardHistory() },
                    onBack = { service.setCurrentView("keyboard") },
                    theme = theme
                )
                "functions" -> FunctionsPanel(
                    onBack = { service.setCurrentView("keyboard") },
                    onSettings = { context.startActivity(Intent(context, SettingsActivity::class.java).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }) },
                    theme = theme
                )
            }
        }
    }
}

@Composable
private fun SuggestionChip(suggestion: String, theme: KeyboardTheme, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        color = Color(theme.keyBackgroundColor).copy(alpha = 0.5f),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.height(34.dp)
    ) {
        Box(modifier = Modifier.padding(horizontal = 12.dp), contentAlignment = Alignment.Center) {
            Text(suggestion, color = Color(theme.keyTextColor), fontSize = 16.sp)
        }
    }
}

@Composable
private fun EmojiSearchBar(service: HexKeyboardService, theme: KeyboardTheme, query: String) {
    Row(modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.weight(1f).height(36.dp).background(Color(theme.keyBackgroundColor).copy(alpha = 0.4f), CircleShape).clickable { service.setEmojiSearchActive(active = true) }, contentAlignment = Alignment.CenterStart) {
            Row(modifier = Modifier.padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Search, null, modifier = Modifier.size(20.dp), tint = Color(theme.keyTextColor).copy(alpha = 0.6f))
                Spacer(Modifier.width(8.dp))
                Text(
                    text = query.ifEmpty { "Buscar emojis..." },
                    color = Color(theme.keyTextColor).copy(alpha = 0.6f)
                )
            }
        }
        IconButton(onClick = { service.setCurrentView("keyboard") }) {
            Icon(Icons.Default.Keyboard, null, tint = Color(theme.keyboardIconTint))
        }
    }
}

@Composable
private fun PanelHeader(view: String, theme: KeyboardTheme, onBack: () -> Unit) {
    Row(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Text(if (view == "clipboard") "Portapapeles" else "Funciones", fontWeight = FontWeight.Bold, color = Color(theme.keyTextColor))
        IconButton(onClick = onBack) { Icon(Icons.Default.Keyboard, null, tint = Color(theme.keyboardIconTint)) }
    }
}



private val PreviewLightTheme = KeyboardTheme(
    id = "light",
    backgroundColor = "#F5F5F5".toColorInt(),
    keyBackgroundColor = android.graphics.Color.WHITE,
    keyTextColor = android.graphics.Color.BLACK,
    keyboardIconTint = "#5F6368".toColorInt(),
    keyShiftActiveColor = "#1A73E8".toColorInt()
)

private val PreviewDarkTheme = KeyboardTheme(
    id = "dark",
    backgroundColor = "#121212".toColorInt(),
    keyBackgroundSpecialColor = "#2C2C2C".toColorInt(),
    keyBackgroundColor = "#2C2C2C".toColorInt(),
    keyTextColor = android.graphics.Color.WHITE,
    keyboardIconTint = "#E8EAED".toColorInt(),
    keyShiftActiveColor = "#8AB4F8".toColorInt()
)

@Preview(name = "Keyboard - Light", showBackground = true)
@Preview(name = "Keyboard - Dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
fun PreviewHexKeyboardView() {
    val isDark = isSystemInDarkTheme()
    val theme = if (isDark) PreviewDarkTheme else PreviewLightTheme

    HexKeyboardTheme {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = Color(theme.backgroundColor),
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Box(modifier = Modifier.fillMaxWidth().height(280.dp)) {
                    AndroidView(
                        factory = { ctx ->
                            HexKeyboardView(ctx).apply {
                                this.symbolPages = mapOf("basic" to listOf("!", "@", "#"))
                                this.longPressAlternatives = emptyMap()
                                this.keyboardTheme = theme
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

@Preview(name = "Emoji Panel - Light", showBackground = true)
@Preview(name = "Emoji Panel - Dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
fun PreviewEmojiPanel() {
    val isDark = isSystemInDarkTheme()
    val theme = if (isDark) PreviewDarkTheme else PreviewLightTheme

    HexKeyboardTheme {
        Box(modifier = Modifier.fillMaxWidth().height(280.dp).background(Color(theme.backgroundColor))) {
            EmojiPanel(
                onEmojiSelected = {},
                onBack = {},
                theme = theme
            )
        }
    }
}

@Preview(name = "Clipboard Panel - Light", showBackground = true)
@Preview(name = "Clipboard Panel - Dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
fun PreviewClipboardPanel() {
    val isDark = isSystemInDarkTheme()
    val theme = if (isDark) PreviewDarkTheme else PreviewLightTheme

    HexKeyboardTheme {
        Box(modifier = Modifier.fillMaxWidth().height(280.dp).background(Color(theme.backgroundColor))) {
            ClipboardPanel(
                history = listOf(
                    ClipboardItem("Hola"),
                    ClipboardItem("Este es un mensaje de prueba", isPinned = true),
                    ClipboardItem("Hex Keyboard es genial")
                ),
                onItemSelected = {},
                onDelete = {},
                onTogglePin = {},
                onBack = {},
                theme = theme
            )
        }
    }
}

@Preview(name = "Functions Panel - Light", showBackground = true)
@Preview(name = "Functions Panel - Dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
fun PreviewFunctionsPanel() {
    val isDark = isSystemInDarkTheme()
    val theme = if (isDark) PreviewDarkTheme else PreviewLightTheme

    HexKeyboardTheme {
        Box(modifier = Modifier.fillMaxWidth().height(280.dp).background(Color(theme.backgroundColor))) {
            FunctionsPanel(
                onBack = {},
                onSettings = {},
                theme = theme
            )
        }
    }
}
