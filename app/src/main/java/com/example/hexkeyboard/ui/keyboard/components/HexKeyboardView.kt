package com.example.hexkeyboard.ui.keyboard.components

import android.content.Context
import androidx.datastore.preferences.core.edit
import com.example.hexkeyboard.data.repository.ThemeUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import android.graphics.*
import android.graphics.drawable.Drawable
import android.media.AudioManager
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.AttributeSet
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.PopupWindow
import android.text.*
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.dynamicanimation.animation.SpringAnimation
import androidx.dynamicanimation.animation.SpringForce
import androidx.preference.PreferenceManager
import com.example.hexkeyboard.service.HexKeyboardService
import com.example.hexkeyboard.data.repository.KeyboardTheme
import com.example.hexkeyboard.R
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import com.example.hexkeyboard.logic.managers.FeedbackManager
import kotlin.math.*

class HexKeyboardView(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {

    init {
        isClickable = true
        isFocusable = false
        isFocusableInTouchMode = false
    }

    interface Listener {
        fun onChar(text: String)
        fun onDelete()
        fun onEnter()
        fun onLongPressSelect(char: String)
        fun onSymbolPageChange(page: String)
        fun onKeyClick(key: Key) {}
        fun onParallaxChange(x: Float, y: Float) {}
        fun onGesture(points: List<PointF>) {}
    }

    var listener: Listener? = null
    var shifted = false
        set(value) {
            if (field != value) {
                field = value
                if (!value) capsLock = false
                if (layoutMode == LayoutMode.FONTS && width > 0) buildLayout(width.toFloat())
                invalidate()
            }
        }
    var capsLock = false
        set(value) {
            if (field != value) {
                field = value
                if (layoutMode == LayoutMode.FONTS && width > 0) buildLayout(width.toFloat())
                invalidate()
            }
        }

    enum class LayoutMode { ALPHA, NUMERIC, SYMBOLS, PURE_NUMERIC, FONTS }
    var layoutMode = LayoutMode.ALPHA
        set(value) {
            if (field != value) {
                field = value
                if (width > 0) buildLayout(width.toFloat())
                invalidate()
            }
        }
    var symbolPage: String = "basic"
    var fontPage: Int = 0

    var language: String = "es"
        set(value) {
            if (field != value) {
                field = value
                if (width > 0) buildLayout(width.toFloat())
                invalidate()
            }
        }

    var layoutType: String = "default"
        set(value) {
            if (field != value) {
                field = value
                if (width > 0) buildLayout(width.toFloat())
                invalidate()
            }
        }

    fun buildLayoutExternally() {
        if (width > 0) buildLayout(width.toFloat())
        invalidate()
    }

    private var showKeyPopup: Boolean = true
    private var showLongPressIndicators: Boolean = true
    private var popupScale: Float = 1.0f
    private var keyScale: Float = 0.92f
    private var vibrationEnabled: Boolean = true
    private var soundEnabled: Boolean = true

    private val viewScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private fun getIntPrefSafely(key: Preferences.Key<Int>, default: Int): Int {
        return runBlocking {
            ThemeUtils.getDataStore(context).data.first()[key] ?: default
        }
    }

    private fun getBooleanPrefSafely(key: Preferences.Key<Boolean>, default: Boolean): Boolean {
        return runBlocking {
            ThemeUtils.getDataStore(context).data.first()[key] ?: default
        }
    }

    // ── Propiedades inyectadas desde HexKeyboardService ──────────────────────
    var longPressAlternatives: Map<Char, List<String>> = emptyMap()
    var symbolPages: Map<String, List<String>> = emptyMap()
    var fontPages: List<List<String>> = emptyList()
    // ─────────────────────────────────────────────────────────────────────────

    var keyboardTheme: KeyboardTheme? = null
        set(value) {
            if (field != value) {
                field = value
                emojiProcessedCache.clear()
                staticLayoutCache.clear()
                if (value != null) applyTheme(value)
                else updateColors()
                invalidate()
            }
        }

    var drawBackground: Boolean = false
        set(value) {
            field = value
            invalidate()
        }

    private var backgroundImage: Bitmap? = null
    private var backgroundBlurImage: Bitmap? = null
    private var parallaxX = 0f
    private var parallaxY = 0f
    private var currentBackgroundUri: String? = null
    private var currentBackgroundBlur: Float = -1f
    private val parallaxLimit = 0.10f // Subtle parallax

    // Gesture Typing properties
    private val gesturePath = Path()
    private val gesturePoints = mutableListOf<PointF>()
    private var isGestureActive = false
    private var gestureStartX = 0f
    private var gestureStartY = 0f
    private val gestureThreshold = ViewConfiguration.get(context).scaledTouchSlop * 2f
    private val pGesture = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        strokeWidth = 12f
    }

    fun setParallaxOffset(x: Float, y: Float) {
        parallaxX = x
        parallaxY = y
        invalidate()
    }

    private fun applyTheme(theme: KeyboardTheme) {
        currentTheme = theme.id
        colorFill = theme.keyBackgroundColor
        colorSpec = theme.keyBackgroundSpecialColor
        colorPress = theme.keyBackgroundPressedColor
        colorStroke = theme.keyStrokeColor
        strokeWidth = theme.keyStrokeWidth
        colorText = theme.keyTextColor
        colorIcon = theme.keyboardIconTint
        colorShiftActive = theme.keyShiftActiveColor ?: theme.keyTextColor
        colorShiftInactive = theme.keyShiftInactiveColor ?: theme.keyBackgroundSpecialColor
        colorDeletePressedIcon = theme.deletePressedIconColor ?: theme.keyboardIconTint
        isKeyTextColorCustom = theme.isKeyTextColorCustom
        colorPopupBg = theme.popupBackgroundColor
        colorPopupText = theme.popupTextColor
        colorPopupSelectedBg = theme.popupSelectedBackgroundColor
        colorPopupSelectedText = theme.popupSelectedTextColor
        colorPopupShadow = Color.parseColor("#40000000")

        // Siempre transparente para evitar el "marcado" del contenedor por el sistema.
        setBackgroundColor(android.graphics.Color.TRANSPARENT)

        pFill.color = colorFill
        pSpec.color = colorSpec
        pPress.color = colorPress
        pStroke.color = colorStroke
        pStroke.strokeWidth = strokeWidth
        pText.color = colorText
        pPopupText.color = colorPopupText
        pPopupBg.color = colorPopupBg
        pGesture.color = colorShiftActive

        // Ajuste de Blur para las teclas
        if (theme.keysBlur > 0.1f) {
            setLayerType(LAYER_TYPE_SOFTWARE, null)
            val radius = theme.keysBlur.coerceAtLeast(0.1f)
            val filter = BlurMaskFilter(radius, BlurMaskFilter.Blur.NORMAL)
            pFill.maskFilter = filter
            pSpec.maskFilter = filter
            pPress.maskFilter = filter
            pStroke.maskFilter = filter
            pFill.alpha = 180
            pSpec.alpha = 180
            pPress.alpha = 180
        } else {
            setLayerType(LAYER_TYPE_HARDWARE, null)
            pFill.maskFilter = null
            pSpec.maskFilter = null
            pPress.maskFilter = null
            pStroke.maskFilter = null
            pFill.alpha = 255
            pSpec.alpha = 255
            pPress.alpha = 255
        }

        if (theme.backgroundImageUri != currentBackgroundUri || theme.backgroundBlur != currentBackgroundBlur) {
            loadBackgroundImage(theme.backgroundImageUri, theme.backgroundBlur)
            currentBackgroundUri = theme.backgroundImageUri
            currentBackgroundBlur = theme.backgroundBlur
        }

        invalidate()
    }

    private fun loadBackgroundImage(uriString: String?, blurRadius: Float) {
        if (uriString == null) {
            backgroundImage?.recycle()
            backgroundBlurImage?.recycle()
            backgroundImage = null
            backgroundBlurImage = null
            return
        }
        try {
            val uri = Uri.parse(uriString)
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                val original = BitmapFactory.decodeStream(inputStream)

                // Reciclar imágenes antiguas antes de asignar las nuevas
                backgroundImage?.recycle()
                backgroundBlurImage?.recycle()

                backgroundImage = original
                backgroundBlurImage = if (blurRadius > 0) ThemeUtils.blurBitmap(original, blurRadius) else null
            }
        } catch (e: Exception) {
            Log.e("HexKB", "Error loading background image", e)
        }
    }

    
    fun resetState() {
        layoutMode = LayoutMode.ALPHA
        // shifted y capsLock serán gestionados por HexKeyboardService
        symbolPage = "basic"
        if (width > 0) buildLayout(width.toFloat())
        invalidate()
    }

    private fun updateKeyScale() {
        keyScale = getIntPrefSafely(ThemeUtils.KEYBOARD_KEY_SIZE, 90) / 100f
    }

    private fun loadPrefs() {
        showKeyPopup = getBooleanPrefSafely(ThemeUtils.SHOW_KEY_POPUP, true)
        showLongPressIndicators = getBooleanPrefSafely(ThemeUtils.SHOW_LONG_PRESS_INDICATORS, true)
        popupScale = getIntPrefSafely(ThemeUtils.POPUP_SCALE, 95) / 100f
        vibrationEnabled = getBooleanPrefSafely(ThemeUtils.KEYBOARD_VIBRATION, true)
        soundEnabled = getBooleanPrefSafely(ThemeUtils.KEYBOARD_SOUND, true)
        updateColors(); updateKeyScale()
        if (keys.isEmpty() && width > 0) buildLayout(width.toFloat())
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        isSoundEffectsEnabled = true
        isHapticFeedbackEnabled = true
        loadPrefs()
        viewScope.launch {
            ThemeUtils.getDataStore(context).data.collect {
                loadPrefs()
                buildLayoutExternally()
            }
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        hidePopup()
        viewScope.cancel()
    }

    private fun getHeightFactor(): Float {
        val h = getIntPrefSafely(ThemeUtils.KEYBOARD_HEIGHT, 50)
        return 0.7f + (h / 100f) * 0.6f
    }

    enum class KeyType { CHAR, SHIFT, DELETE, ENTER, SPACE, TOGGLE, SYMBOL_PAGE, EMOJI, CLIPBOARD, FUNCTIONS, FONT_PAGE, LANGUAGE }

    inner class Key(
        val display: String,
        val type: KeyType,
        val value: String = "",
        val alternatives: List<String> = emptyList(),
        val cx: Float,
        val cy: Float,
        val rx: Float,
        val ry: Float,
        val gapLeft: Boolean = true,
        val gapRight: Boolean = true,
        val isHalfLeft: Boolean = false,
        val isHalfRight: Boolean = false
    ) {
        val path: Path = Path()
        val strokePath: Path = Path()
        var currentScale = 1.0f
        var isPressed = false

        private val springAnim = SpringAnimation(this, object : androidx.dynamicanimation.animation.FloatPropertyCompat<Key>("scale") {
            override fun getValue(k: Key): Float = k.currentScale
            override fun setValue(k: Key, value: Float) {
                k.currentScale = value
                this@HexKeyboardView.invalidate()
            }
        }).apply {
            spring = SpringForce().apply {
                dampingRatio = SpringForce.DAMPING_RATIO_MEDIUM_BOUNCY
                stiffness = SpringForce.STIFFNESS_HIGH
            }
        }

        fun animateToScale(target: Float) {
            springAnim.animateToFinalPosition(target)
        }

        init { updatePath() }

        private fun updatePath() {
            val s32 = 0.8660254f
            path.reset()
            strokePath.reset()
            val h = ry
            val r = rx * s32
            val rFull = if (keyScale > 0) (rx / keyScale) * s32 else r

            val rounding = 0.28f
            val overlap = 0.4f // Solapamiento para evitar líneas de anti-aliasing
            fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t

            if (isHalfLeft) {
                val margin = rFull - r
                val edgeX = cx + margin
                val p0x = cx; val p0y = cy - h
                val p1x = if (gapRight) cx + r else cx + rFull + overlap; val p1y = cy - h / 2f
                val p2x = if (gapRight) cx + r else cx + rFull + overlap; val p2y = cy + h / 2f
                val p3x = cx; val p3y = cy + h

                val startX = edgeX; val startY = cy - h + (h * rounding)
                path.moveTo(startX, startY)
                strokePath.moveTo(startX, startY)

                // Esquina superior (cut edge)
                path.quadTo(edgeX, cy - h, lerp(edgeX, p1x, rounding), lerp(cy - h, p1y, rounding))
                strokePath.quadTo(edgeX, cy - h, lerp(edgeX, p1x, rounding), lerp(cy - h, p1y, rounding))

                // Arista p0-p1
                path.lineTo(lerp(p1x, p0x, rounding), lerp(p1y, p0y, rounding))
                strokePath.lineTo(lerp(p1x, p0x, rounding), lerp(p1y, p0y, rounding))

                // Esquina p1
                if (gapRight) {
                    path.quadTo(p1x, p1y, lerp(p1x, p2x, rounding), lerp(p1y, p2y, rounding))
                    strokePath.quadTo(p1x, p1y, lerp(p1x, p2x, rounding), lerp(p1y, p2y, rounding))
                } else {
                    path.lineTo(p1x, p1y); path.lineTo(lerp(p1x, p2x, rounding), lerp(p1y, p2y, rounding))
                    strokePath.lineTo(p1x, p1y); strokePath.moveTo(p2x, p2y)
                }

                // Arista p1-p2
                path.lineTo(lerp(p2x, p1x, rounding), lerp(p2y, p1y, rounding))
                if (gapRight) strokePath.lineTo(lerp(p2x, p1x, rounding), lerp(p2y, p1y, rounding))

                // Esquina p2
                if (gapRight) {
                    path.quadTo(p2x, p2y, lerp(p2x, p3x, rounding), lerp(p2y, p3y, rounding))
                    strokePath.quadTo(p2x, p2y, lerp(p2x, p3x, rounding), lerp(p2y, p3y, rounding))
                } else {
                    path.lineTo(p2x, p2y); path.lineTo(lerp(p2x, p3x, rounding), lerp(p2y, p3y, rounding))
                    strokePath.lineTo(p2x, p2y)
                }

                // Arista p2-p3
                path.lineTo(lerp(p3x, p2x, rounding), lerp(p3y, p2y, rounding))
                strokePath.lineTo(lerp(p3x, p2x, rounding), lerp(p3y, p2y, rounding))

                // Esquina inferior (cut edge)
                path.quadTo(edgeX, cy + h, edgeX, cy + h - (h * rounding))
                strokePath.quadTo(edgeX, cy + h, edgeX, cy + h - (h * rounding))

                // Borde izquierdo recto
                path.lineTo(edgeX, cy - h + (h * rounding))
                strokePath.lineTo(edgeX, cy - h + (h * rounding))

            } else if (isHalfRight) {
                val margin = rFull - r
                val edgeX = cx - margin
                val p3x = cx; val p3y = cy + h
                val p4x = if (gapLeft) cx - r else cx - rFull - overlap; val p4y = cy + h / 2f
                val p5x = if (gapLeft) cx - r else cx - rFull - overlap; val p5y = cy - h / 2f
                val p0x = cx; val p0y = cy - h

                val startX = edgeX; val startY = cy + h - (h * rounding)
                path.moveTo(startX, startY)
                strokePath.moveTo(startX, startY)

                // Esquina inferior (cut edge)
                path.quadTo(edgeX, cy + h, lerp(edgeX, p4x, rounding), lerp(cy + h, p4y, rounding))
                strokePath.quadTo(edgeX, cy + h, lerp(edgeX, p4x, rounding), lerp(cy + h, p4y, rounding))

                // Arista p3-p4
                path.lineTo(lerp(p4x, p3x, rounding), lerp(p4y, p3y, rounding))
                strokePath.lineTo(lerp(p4x, p3x, rounding), lerp(p4y, p3y, rounding))

                // Esquina p4
                if (gapLeft) {
                    path.quadTo(p4x, p4y, lerp(p4x, p5x, rounding), lerp(p4y, p5y, rounding))
                    strokePath.quadTo(p4x, p4y, lerp(p4x, p5x, rounding), lerp(p4y, p5y, rounding))
                } else {
                    path.lineTo(p4x, p4y); path.lineTo(lerp(p4x, p5x, rounding), lerp(p4y, p5y, rounding))
                    strokePath.lineTo(p4x, p4y); strokePath.moveTo(p5x, p5y)
                }

                // Arista p4-p5
                path.lineTo(lerp(p5x, p4x, rounding), lerp(p5y, p4y, rounding))
                if (gapLeft) strokePath.lineTo(lerp(p5x, p4x, rounding), lerp(p5y, p4y, rounding))

                // Esquina p5
                if (gapLeft) {
                    path.quadTo(p5x, p5y, lerp(p5x, p0x, rounding), lerp(p5y, p0y, rounding))
                    strokePath.quadTo(p5x, p5y, lerp(p5x, p0x, rounding), lerp(p5y, p0y, rounding))
                } else {
                    path.lineTo(p5x, p5y); path.lineTo(lerp(p5x, p0x, rounding), lerp(p5y, p0y, rounding))
                    strokePath.lineTo(p5x, p5y)
                }

                // Arista p5-p0
                path.lineTo(lerp(p0x, p5x, rounding), lerp(p0y, p5y, rounding))
                strokePath.lineTo(lerp(p0x, p5x, rounding), lerp(p0y, p5y, rounding))

                // Esquina superior (cut edge)
                path.quadTo(edgeX, cy - h, edgeX, cy - h + (h * rounding))
                strokePath.quadTo(edgeX, cy - h, edgeX, cy - h + (h * rounding))

                // Borde derecho recto
                path.lineTo(edgeX, cy + h - (h * rounding))
                strokePath.lineTo(edgeX, cy + h - (h * rounding))

            } else {
                val px = FloatArray(6)
                val py = FloatArray(6)
                px[0] = cx; py[0] = cy - h
                px[1] = if (gapRight) cx + r else cx + rFull + overlap; py[1] = cy - h / 2f
                px[2] = if (gapRight) cx + r else cx + rFull + overlap; py[2] = cy + h / 2f
                px[3] = cx; py[3] = cy + h
                px[4] = if (gapLeft) cx - r else cx - rFull - overlap; py[4] = cy + h / 2f
                px[5] = if (gapLeft) cx - r else cx - rFull - overlap; py[5] = cy - h / 2f

                val startX = lerp(px[0], px[5], rounding)
                val startY = lerp(py[0], py[5], rounding)
                path.moveTo(startX, startY)
                strokePath.moveTo(startX, startY)

                for (i in 0..5) {
                    val next = (i + 1) % 6

                    // Esquina i
                    val useRound = when (i) {
                        1, 2 -> gapRight
                        4, 5 -> gapLeft
                        else -> true
                    }

                    if (useRound) {
                        path.quadTo(px[i], py[i], lerp(px[i], px[next], rounding), lerp(py[i], py[next], rounding))
                        strokePath.quadTo(px[i], py[i], lerp(px[i], px[next], rounding), lerp(py[i], py[next], rounding))
                    } else {
                        path.lineTo(px[i], py[i])
                        path.lineTo(lerp(px[i], px[next], rounding), lerp(py[i], py[next], rounding))

                        strokePath.lineTo(px[i], py[i])
                    }

                    // Arista i -> next
                    val isSharedEdge = when (i) {
                        1 -> !gapRight
                        4 -> !gapLeft
                        else -> false
                    }

                    if (isSharedEdge) {
                        path.lineTo(lerp(px[next], px[i], rounding), lerp(py[next], py[i], rounding))
                        strokePath.moveTo(px[next], py[next])
                    } else {
                        if (i < 5) {
                            path.lineTo(lerp(px[next], px[i], rounding), lerp(py[next], py[i], rounding))
                            strokePath.lineTo(lerp(px[next], px[i], rounding), lerp(py[next], py[i], rounding))
                        } else {
                            path.lineTo(startX, startY)
                            strokePath.lineTo(startX, startY)
                        }
                    }
                }
            }
            path.close()
        }

        fun contains(x: Float, y: Float): Boolean {
            val dx = x - cx
            val dy = abs(y - cy)
            val s32 = 0.8660254f
            val r = rx * s32
            val rFull = if (keyScale > 0) (rx / keyScale) * s32 else r

            val limitRight = if (gapRight) r else rFull
            val limitLeft = if (gapLeft) -r else -rFull

            if (dx > limitRight || dx < limitLeft || dy > ry) return false

            val activeR = if (dx >= 0) (if (gapRight) r else rFull) else (if (gapLeft) r else rFull)
            return dy * activeR + abs(dx) * (ry / 2f) <= ry * activeR
        }
    }

    private val pFill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val pSpec = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val pPress = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val pStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        isDither = true
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val pText = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    private val pPopupText = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    private val pEmojiText = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    private val pPopupBg = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val pShadow = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL; maskFilter = BlurMaskFilter(8f, BlurMaskFilter.Blur.NORMAL) }
    private val pBackground = Paint(Paint.ANTI_ALIAS_FLAG).apply { isFilterBitmap = true }

    private val rectF = RectF()
    private val fontMetrics = Paint.FontMetrics()
    var sharedTypeface: Typeface = Typeface.DEFAULT
        set(value) {
            field = value
            pText.typeface = value
            pPopupText.typeface = value
            pEmojiText.typeface = value
            invalidate()
        }

    private val iconShift: Drawable? by lazy { ContextCompat.getDrawable(context, R.drawable.ic_shift) }
    private val iconShiftActive: Drawable? by lazy { ContextCompat.getDrawable(context, R.drawable.ic_shift_active) }
    private val iconShiftFilled: Drawable? by lazy { ContextCompat.getDrawable(context, R.drawable.ic_shift_filled) }
    private val iconDelete: Drawable? by lazy { ContextCompat.getDrawable(context, R.drawable.ic_delete) }
    private val iconDeleteFilled: Drawable? by lazy { ContextCompat.getDrawable(context, R.drawable.ic_delete_filled) }
    private val iconEnter: Drawable? by lazy { ContextCompat.getDrawable(context, R.drawable.ic_enter) }
    private val iconSend: Drawable? by lazy { ContextCompat.getDrawable(context, R.drawable.ic_send) }
    private val iconSearch: Drawable? by lazy { ContextCompat.getDrawable(context, R.drawable.ic_search) }
    private val iconDone: Drawable? by lazy { ContextCompat.getDrawable(context, R.drawable.ic_done) }
    private val iconGo: Drawable? by lazy { ContextCompat.getDrawable(context, R.drawable.ic_go) }
    private val iconSymbols: Drawable? by lazy { ContextCompat.getDrawable(context, R.drawable.ic_symbols) }
    private val iconEmoji: Drawable? by lazy { ContextCompat.getDrawable(context, R.drawable.ic_emoji) }
    private val iconEmojiFilled: Drawable? by lazy { ContextCompat.getDrawable(context, R.drawable.ic_emoji_filled) }
    private val iconClipboard: Drawable? by lazy { ContextCompat.getDrawable(context, R.drawable.ic_clipboard) }
    private val iconClipboardFilled: Drawable? by lazy { ContextCompat.getDrawable(context, R.drawable.ic_clipboard_filled) }
    private val iconPuzzle: Drawable? by lazy { ContextCompat.getDrawable(context, R.drawable.ic_puzzle) }
    private val iconLanguage: Drawable? by lazy { ContextCompat.getDrawable(context, android.R.drawable.ic_menu_mapmode) }

    private var colorFill = 0; private var colorSpec = 0; private var colorPress = 0
    private var colorStroke = 0; private var strokeWidth = 1f; private var colorText = 0; private var colorIcon = 0
    private var colorShiftActive = 0; private var colorShiftInactive = 0; private var colorPopupBg = 0; private var colorPopupText = 0; private var colorPopupShadow = 0
    private var colorPopupSelectedBg = 0; private var colorPopupSelectedText = 0
    private var colorDeletePressedIcon = 0
    private var isKeyTextColorCustom = false
    private var currentTheme = "system"
    private val emojiProcessedCache = object : LinkedHashMap<String, CharSequence>(200, 0.75f, true) {
        override fun removeEldestEntry(eldest: Map.Entry<String, CharSequence>?): Boolean {
            return size > 200
        }
    }
    private val staticLayoutCache = object : LinkedHashMap<String, StaticLayout>(100, 0.75f, true) {
        override fun removeEldestEntry(eldest: Map.Entry<String, StaticLayout>?): Boolean {
            return size > 100 // Limitar a 100 layouts para ahorrar RAM
        }
    }

    fun clearCaches() {
        emojiProcessedCache.clear()
        staticLayoutCache.clear()
    }

    private fun getProcessedEmoji(text: String): CharSequence {
        return text
    }

    private var currentImeAction: Int = EditorInfo.IME_ACTION_NONE
    var isMultiLine: Boolean = false
        set(value) {
            field = value
            invalidate()
        }
    private var isScrollingCursor = false
    private var lastScrollX = 0f
    private var lastScrollY = 0f

    private var popupWindow: PopupWindow? = null
    private var popupContentView: KeyPopupView? = null

    inner class KeyPopupView(context: Context) : View(context) {
        var key: Key? = null
        var isLongPress: Boolean = false
        var selectedIndex: Int = -1

        private val localPopupPath = Path()
        private val localPopupHexPath = Path()

        override fun onDraw(canvas: Canvas) {
            val k = key ?: return
            val px = width / 2f
            val py = height / 2f

            val prx = k.rx * keyScale * 1.1f * popupScale
            val pry = k.ry * keyScale * 1.1f * popupScale
            val textSize = min(prx, pry) * 0.7f

            if (!isLongPress) {
                updateHexPath(localPopupHexPath, px, py, prx, pry)

                // Dibujar Sombra con desplazamiento (efecto flotante)
                canvas.save()
                canvas.translate(0f, 6f)
                pShadow.color = colorPopupShadow
                canvas.drawPath(localPopupHexPath, pShadow)
                canvas.restore()

                // Dibujar Fondo del Pop-up
                pPopupBg.color = colorPopupBg
                canvas.drawPath(localPopupHexPath, pPopupBg)

                if (strokeWidth > 0) {
                    pStroke.color = colorStroke
                    pStroke.strokeWidth = 2f
                    canvas.drawPath(localPopupHexPath, pStroke)
                }

                // Dibujar Texto
                pPopupText.textSize = textSize
                val ty = py - (pPopupText.ascent() + pPopupText.descent()) / 2f
                val label = if (shifted && k.type == KeyType.CHAR && k.value.length == 1 && k.value[0].isLetter()) k.display.uppercase() else k.display
                pPopupText.color = colorPopupText
                canvas.drawText(label, px, ty, pPopupText)
            } else {
                val alts = listOf(k.value) + k.alternatives
                val sW = prx * 1.35f
                val sH = pry * 1.35f
                val g = sW * 0.12f
                val p = sW * 0.45f

                val maxW = width - p * 2f
                // Gboard style: prefer 5 columns for many items
                val idealCols = when {
                    alts.size <= 5 -> alts.size
                    alts.size <= 10 -> 5
                    else -> 6
                }
                var cols = idealCols.coerceAtMost(alts.size)

                while (cols > 1 && (cols * sW + (cols - 1) * g) > maxW) {
                    cols--
                }

                val rows = (alts.size + cols - 1) / cols

                val tw = if (rows > 1) cols * sW + (cols - 1) * g else alts.size * sW + (alts.size - 1) * g
                val th = rows * sH + (rows - 1) * g

                localPopupPath.reset()
                val cornerRadius = sW * 0.45f
                localPopupPath.addRoundRect(px - tw/2f - p, py - th/2f - p/2f, px + tw/2f + p, py + th/2f + p/2f, cornerRadius, cornerRadius, Path.Direction.CW)

                // Sombra suave (Efecto Gboard)
                canvas.save()
                canvas.translate(0f, 6f)
                pShadow.color = colorPopupShadow
                canvas.drawPath(localPopupPath, pShadow)
                canvas.restore()

                pPopupBg.color = colorPopupBg
                canvas.drawPath(localPopupPath, pPopupBg)

                if (strokeWidth > 0) {
                    pStroke.color = colorStroke
                    pStroke.strokeWidth = 1f
                    canvas.drawPath(localPopupPath, pStroke)
                }

                pPopupText.textSize = textSize * 0.85f

                alts.forEachIndexed { i, a ->
                    val row = i / cols
                    val col = i % cols

                    val itemsInThisRow = if (row == rows - 1) alts.size - (row * cols) else cols
                    val rowW = itemsInThisRow * sW + (itemsInThisRow - 1) * g

                    val kx = px - rowW/2f + sW/2f + col * (sW + g)
                    val ky = py - th/2f + sH/2f + row * (sH + g)

                    val ty = ky - (pPopupText.ascent() + pPopupText.descent()) / 2f

                    if (i == selectedIndex) {
                        val hr = sW * 0.46f
                        val themeId = keyboardTheme?.id ?: ""
                        val highlightColor = if (themeId.contains("dark") || themeId == "terminal") {
                            Color.parseColor("#4285F4") // Azul Google para resaltar más
                        } else {
                            colorPopupSelectedBg
                        }

                        updateHexPath(localPopupHexPath, kx, ky, sW * 0.48f, sH * 0.48f)
                        canvas.drawPath(localPopupHexPath, pPress.apply { color = highlightColor })

                        if (strokeWidth > 0) {
                            pStroke.color = highlightColor
                            pStroke.strokeWidth = 2f
                            canvas.drawPath(localPopupHexPath, pStroke)
                        }
                        pPopupText.color = colorPopupSelectedText
                    } else {
                        pPopupText.color = colorPopupText
                    }
                    canvas.drawText(if (a == " " && i == 0) "␣" else a, kx, ty, pPopupText)
                }
            }
        }
    }

    private fun showPopup(key: Key, isLongPress: Boolean = false) {
        if (!showKeyPopup || windowToken == null) return
        if (popupWindow == null) {
            popupContentView = KeyPopupView(context).apply {
                setLayerType(LAYER_TYPE_SOFTWARE, null)
            }
            popupWindow = PopupWindow(popupContentView, ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                elevation = 20f
                isTouchable = false
                isOutsideTouchable = false
                elevation = 10f
                setBackgroundDrawable(null)
                inputMethodMode = PopupWindow.INPUT_METHOD_NOT_NEEDED
                isClippingEnabled = false
                // Desactivar animaciones nativas para evitar el log "sendCancelIfRunning"
                animationStyle = 0
            }
        }

        popupContentView?.key = key
        popupContentView?.isLongPress = isLongPress
        popupContentView?.selectedIndex = if (isLongPress) 0 else -1

        val prx = key.rx * keyScale * 1.1f * popupScale
        val pry = key.ry * keyScale * 1.1f * popupScale

        val displayMetrics = context.resources.displayMetrics
        val screenWidth = displayMetrics.widthPixels

        val w: Int
        val h: Int

        if (!isLongPress) {
            w = (prx * 2.5f).toInt()
            h = (pry * 2.5f).toInt()
        } else {
            val alts = listOf(key.value) + key.alternatives
            val sW = prx * 1.35f
            val sH = pry * 1.35f
            val g = sW * 0.12f
            val p = sW * 0.45f

            val maxW = screenWidth - p * 2f
            val idealCols = when {
                alts.size <= 5 -> alts.size
                alts.size <= 10 -> 5
                else -> 6
            }
            var cols = idealCols.coerceAtMost(alts.size)

            while (cols > 1 && (cols * sW + (cols - 1) * g) > maxW) {
                cols--
            }

            val rows = (alts.size + cols - 1) / cols

            val tw = if (rows > 1) cols * sW + (cols - 1) * g else alts.size * sW + (alts.size - 1) * g
            val th = rows * sH + (rows - 1) * g

            w = (tw + p * 2.2f).toInt()
            h = (th + p * 1.1f).toInt()
        }

        popupWindow?.width = w
        popupWindow?.height = h
        popupContentView?.invalidate()

        val location = IntArray(2)
        getLocationInWindow(location)

        val offsetX = (key.cx - w / 2f).toInt()
        val offsetY = (key.cy - key.ry * (1.1f + 1.2f * popupScale) - h / 2f).toInt()

        var x = location[0] + offsetX
        var y = location[1] + offsetY

        if (x < 0) x = 0
        if (x + w > screenWidth) x = screenWidth - w

        if (popupWindow?.isShowing == true) {
            popupWindow?.update(x, y, w, h)
        } else {
            popupWindow?.showAtLocation(this, android.view.Gravity.NO_GRAVITY, x, y)
        }
    }

    private fun hidePopup() {
        try {
            if (popupWindow?.isShowing == true) {
                popupWindow?.dismiss()
            }
        } catch (e: Exception) {
            Log.e("HexKB", "Error dismissing popup", e)
        }
        popupVisibleKey = null
    }

    fun setImeAction(action: Int) {
        currentImeAction = action
        invalidate()
    }

    private val keys = mutableListOf<Key>()
    val allKeys: List<Key> get() = keys
    private val spaceKeys = mutableListOf<Key>()
    private val shiftKeys = mutableListOf<Key>()
    private val deleteKeys = mutableListOf<Key>()
    private val enterKeys = mutableListOf<Key>()
    private var unifiedShiftCenterX = 0f
    private var unifiedDeleteCenterX = 0f
    private var unifiedSpaceCenterX = 0f
    private var unifiedEnterCenterX = 0f

    private var spacePressed = false
    private var shiftPressed = false
    private var deletePressed = false
    private var enterPressed = false
    private var pressedKey: Key? = null; private var popupVisibleKey: Key? = null; private var popupSelectedIndex = -1
    private val activePointers = mutableMapOf<Int, Key>()
    private val pointerStartTime = mutableMapOf<Int, Long>()
    private val handler = Handler(Looper.getMainLooper())
    private var longPressTriggered = false; private var longPressStarted = false
    private val longPressTimeout: Long
        get() = getIntPrefSafely(ThemeUtils.LONG_PRESS_DURATION, 259).toLong()
    private val longPressRunnable = Runnable { pressedKey?.let { if (it.alternatives.isNotEmpty() && !isGestureActive) { longPressStarted = true; popupVisibleKey = it; popupSelectedIndex = 0; showPopup(it, true) } } }
    private var deleteRepeatRunnable: Runnable? = null
    private val deleteRepeatDelay = 400L; private val deleteRepeatInterval = 80L
    private var lastShiftClickTime = 0L; private val doubleClickThreshold = 280L

    private val keysToIgnoreDuringNGesture = setOf("l", "d", "g", "p", "c", "f", "u")

    private fun getHexR(vw: Float): Float = if (vw <= 0) 0f else (vw / 8.0f) / sqrt(3.0f).toFloat()

    private fun getNumericR(vw: Float): Float {
        if (vw <= 0) return 0f
        val scale = getIntPrefSafely(ThemeUtils.NUMERIC_KEY_SIZE_SCALE, 85) / 100f
        return (vw / (5.6f / scale)) / sqrt(3.0f).toFloat()
    }

    override fun onMeasure(wMS: Int, hMS: Int) {
        val w = MeasureSpec.getSize(wMS)
        if (w <= 0) { setMeasuredDimension(0, 0); return }
        val f = getHeightFactor();
        val r = if (layoutMode == LayoutMode.PURE_NUMERIC) getNumericR(w.toFloat()) else getHexR(w.toFloat())
        val rows = if (layoutMode == LayoutMode.PURE_NUMERIC) 3f else 4f
        val dr = r * keyScale; val dry = dr * f
        val tp = r * 0.05f * f
        val h = (tp + 2 * dry + rows * r * 1.5f * f + r * 0.15f * f).toInt()
        setMeasuredDimension(w, h)
    }

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) { super.onSizeChanged(w, h, ow, oh); buildLayout(w.toFloat()) }
    private fun buildLayout(vw: Float) {
        if (vw <= 0) return
        keys.clear()
        spaceKeys.clear()
        shiftKeys.clear()
        deleteKeys.clear()

        when(layoutMode){
            LayoutMode.ALPHA->buildHexLayout(vw)
            LayoutMode.NUMERIC->buildHexNumericLayout(vw)
            LayoutMode.PURE_NUMERIC->buildHexPureNumericLayout(vw)
            LayoutMode.FONTS->buildHexFontsLayout(vw)
            else->buildHexSymbolLayout(vw)
        }

        spaceKeys.addAll(keys.filter { it.type == KeyType.SPACE })
        shiftKeys.addAll(keys.filter { it.type == KeyType.SHIFT })
        deleteKeys.addAll(keys.filter { it.type == KeyType.DELETE })
        enterKeys.addAll(keys.filter { it.type == KeyType.ENTER })

        if (shiftKeys.size >= 2) {
            val half = shiftKeys.find { it.isHalfLeft }
            val full = shiftKeys.find { !it.isHalfLeft }
            if (half != null && full != null) {
                val s32 = 0.8660254f
                val r = full.rx * s32
                val rFull = if (keyScale > 0) (full.rx / keyScale) * s32 else r
                val margin = rFull - r
                unifiedShiftCenterX = ((half.cx + margin) + (full.cx + r)) / 2f
            }
        }
        if (deleteKeys.size >= 2) {
            val half = deleteKeys.find { it.isHalfRight }
            val full = deleteKeys.find { !it.isHalfRight }
            if (half != null && full != null) {
                val s32 = 0.8660254f
                val r = full.rx * s32
                val rFull = if (keyScale > 0) (full.rx / keyScale) * s32 else r
                val margin = rFull - r
                unifiedDeleteCenterX = ((full.cx - r) + (half.cx - margin)) / 2f
            }
        }
        if (spaceKeys.isNotEmpty()) {
            unifiedSpaceCenterX = (spaceKeys.minOf { it.cx } + spaceKeys.maxOf { it.cx }) / 2f
        }
        if (enterKeys.isNotEmpty()) {
            unifiedEnterCenterX = (enterKeys.minOf { it.cx } + enterKeys.maxOf { it.cx }) / 2f
        }
    }

    private fun buildHexLayout(vw: Float) {
        val f = getHeightFactor(); val m = 0f; val u = vw; val r = getHexR(vw); val hw = r * sqrt(3f); val rs = r * 1.5f * f; val tp = r * 0.05f * f
        val dr = r * keyScale; val dry = dr * f
        pStroke.strokeWidth = maxOf(1f, r * 0.02f)

        fun cx8(c: Float) = m + u/2f + (c - 3.5f) * hw
        fun cx7(c: Float) = m + u/2f + (c - 3f) * hw
        fun ry(row: Int) = tp + dry + row * rs

        val row0 = if (layoutType == "qwerty") listOf("q","w","e","r","t","y") else listOf("q","w","f","g","p","b")
        val row1 = if (layoutType == "qwerty") listOf("u","i","o","p","a","s","d") else listOf("k","o","u","d","i","c","m")
        val row2 = if (layoutType == "qwerty") listOf("f","g","h","j","k","l","z","x") else listOf("a","e","s","r","h","t","n","l")
        val row3Chars = if (layoutType == "qwerty") listOf("c","v","b","n","m") else listOf("v","y","x","z","j")

        // Fila 0 (8 teclas)
        keys += Key("😀", KeyType.EMOJI, "EMOJI", emptyList(), cx = cx8(0f), cy = ry(0), rx = dr, ry = dry)
        row0.forEachIndexed { i, l ->
            keys += Key(l, KeyType.CHAR, l, longPressAlternatives[l[0]] ?: emptyList(), cx = cx8(i.toFloat() + 1f), cy = ry(0), rx = dr, ry = dry)
        }
        keys += Key("CLIP", KeyType.CLIPBOARD, "CLIPBOARD", emptyList(), cx = cx8(7f), cy = ry(0), rx = dr, ry = dry)

        // Fila 1 (7 teclas)
        row1.forEachIndexed { i, l ->
            keys += Key(l, KeyType.CHAR, l, longPressAlternatives[l[0]] ?: emptyList(), cx = cx7(i.toFloat()), cy = ry(1), rx = dr, ry = dry)
        }

        // Fila 2 (8 teclas)
        row2.forEachIndexed { i, l ->
            keys += Key(l, KeyType.CHAR, l, longPressAlternatives[l[0]] ?: emptyList(), cx = cx8(i.toFloat()), cy = ry(2), rx = dr, ry = dry)
        }

        // Fila 3 (Shift + 5 letras + Delete)
        keys += Key("", KeyType.SHIFT, "SHIFT", emptyList(), cx = cx7(-1.0f), cy = ry(3), rx = dr, ry = dry, isHalfLeft = true, gapRight = false)
        keys += Key("", KeyType.DELETE, "DELETE", emptyList(), cx = cx7(7.0f), cy = ry(3), rx = dr, ry = dry, isHalfRight = true, gapLeft = false)

        val row3Full = listOf("⇧") + row3Chars + listOf("⌫")
        row3Full.forEachIndexed { i, l ->
            val type = when(l) {
                "⇧" -> KeyType.SHIFT
                "⌫" -> KeyType.DELETE
                else -> KeyType.CHAR
            }
            val value = when(l) {
                "⇧" -> "SHIFT"
                "⌫" -> "DELETE"
                else -> l
            }
            val alt = if (type == KeyType.CHAR) (longPressAlternatives[l[0]] ?: emptyList()) else emptyList()

            val finalCx = when(type) {
                KeyType.SHIFT -> cx7(0.0f)
                KeyType.DELETE -> cx7(6.0f)
                else -> cx7(i.toFloat())
            }
            val gL = type != KeyType.SHIFT
            val gR = type != KeyType.DELETE

            keys += Key(l, type, value, alt, cx = finalCx, cy = ry(3), rx = dr, ry = dry, gapLeft = gL, gapRight = gR)
        }
        keys += Key("?123", KeyType.TOGGLE, "123", listOf("#+=", "𝔉"), cx = cx8(0f), cy = ry(4), rx = dr, ry = dry)
        // ... (resto de fila 4 se mantiene igual)

        if (language == "es") {
            keys += Key(",", KeyType.CHAR, ",", longPressAlternatives[','] ?: emptyList(), cx = cx8(1f), cy = ry(4), rx = dr, ry = dry)

            // Espacio (4 unidades en ES)
            keys += Key(" ", KeyType.SPACE, " ", emptyList(), cx = cx8(2f), cy = ry(4), rx = dr, ry = dry, gapRight = false)
            keys += Key(" ", KeyType.SPACE, " ", emptyList(), cx = cx8(3f), cy = ry(4), rx = dr, ry = dry, gapLeft = false, gapRight = false)
            keys += Key(" ", KeyType.SPACE, " ", emptyList(), cx = cx8(4f), cy = ry(4), rx = dr, ry = dry, gapLeft = false, gapRight = false)
            keys += Key(" ", KeyType.SPACE, " ", emptyList(), cx = cx8(5f), cy = ry(4), rx = dr, ry = dry, gapLeft = false)
        } else {
            keys += Key(",", KeyType.CHAR, ",", longPressAlternatives[','] ?: emptyList(), cx = cx8(1f), cy = ry(4), rx = dr, ry = dry)

            // Espacio (5 unidades en EN)
            keys += Key(" ", KeyType.SPACE, " ", emptyList(), cx = cx8(2f), cy = ry(4), rx = dr, ry = dry, gapRight = false)
            keys += Key(" ", KeyType.SPACE, " ", emptyList(), cx = cx8(3f), cy = ry(4), rx = dr, ry = dry, gapLeft = false, gapRight = false)
            keys += Key(" ", KeyType.SPACE, " ", emptyList(), cx = cx8(4f), cy = ry(4), rx = dr, ry = dry, gapLeft = false, gapRight = false)
            keys += Key(" ", KeyType.SPACE, " ", emptyList(), cx = cx8(5f), cy = ry(4), rx = dr, ry = dry, gapLeft = false, gapRight = false)
            keys += Key(" ", KeyType.SPACE, " ", emptyList(), cx = cx8(6f), cy = ry(4), rx = dr, ry = dry, gapLeft = false)
        }

        // Fusión de ENTER (6 y 7)
        keys += Key("", KeyType.ENTER, "ENTER", emptyList(), cx = cx8(6f), cy = ry(4), rx = dr, ry = dry, gapRight = false)
        keys += Key("↩", KeyType.ENTER, "ENTER", emptyList(), cx = cx8(7f), cy = ry(4), rx = dr, ry = dry, gapLeft = false)
    }

    private fun buildHexPureNumericLayout(vw: Float) {
        val f = getHeightFactor(); val m = 0f; val u = vw;
        val r = if (vw <= 0) 0f else getNumericR(vw)
        val hw = r * sqrt(3f); val rs = r * 1.5f * f; val tp = r * 0.05f * f
        val dr = r * keyScale; val dry = dr * f
        pStroke.strokeWidth = maxOf(1f, r * 0.02f)

        fun cx5(c: Float) = m + u/2f + (c - 2f) * hw
        fun cx4(c: Float) = m + u/2f + (c - 1.5f) * hw
        fun cx6(c: Float) = m + u/2f + (c - 2.5f) * hw
        fun ry(row: Int) = tp + dry + row * rs

        listOf("1", "2", "3", "4", "5").forEachIndexed { i, l -> keys += Key(l, KeyType.CHAR, l, emptyList(), cx = cx5(i.toFloat()), cy = ry(0), rx = dr, ry = dry) }

        // Fila 1: [CLIP, 6, 7, 8, 9, ⌫]
        keys += Key("CLIP", KeyType.CLIPBOARD, "CLIPBOARD", emptyList(), cx = cx6(0f), cy = ry(1), rx = dr, ry = dry)
        listOf("6", "7", "8", "9").forEachIndexed { i, l -> keys += Key(l, KeyType.CHAR, l, emptyList(), cx = cx6(i.toFloat() + 1f), cy = ry(1), rx = dr, ry = dry) }
        keys += Key("⌫", KeyType.DELETE, "DELETE", emptyList(), cx = cx6(5f), cy = ry(1), rx = dr, ry = dry)

        listOf("0", ",", ".", "-", "+").forEachIndexed { i, l -> keys += Key(l, KeyType.CHAR, l, emptyList(), cx = cx5(i.toFloat()), cy = ry(2), rx = dr, ry = dry) }

        // Fila 3: ABC a la izquierda, Enter a la derecha, Espacio de 4 unidades
        keys += Key("ABC", KeyType.TOGGLE, "?123", listOf("#+=", "𝔉"), cx = cx6(0f), cy = ry(3), rx = dr, ry = dry)
        keys += Key(" ", KeyType.SPACE, " ", emptyList(), cx = cx6(1f), cy = ry(3), rx = dr, ry = dry, gapRight = false)
        keys += Key(" ", KeyType.SPACE, " ", emptyList(), cx = cx6(2f), cy = ry(3), rx = dr, ry = dry, gapLeft = false, gapRight = false)
        keys += Key(" ", KeyType.SPACE, " ", emptyList(), cx = cx6(3f), cy = ry(3), rx = dr, ry = dry, gapLeft = false, gapRight = false)
        keys += Key(" ", KeyType.SPACE, " ", emptyList(), cx = cx6(4f), cy = ry(3), rx = dr, ry = dry, gapLeft = false)
        keys += Key("↩", KeyType.ENTER, "ENTER", emptyList(), cx = cx6(5f), cy = ry(3), rx = dr, ry = dry)
    }

    private fun buildHexNumericLayout(vw: Float) {
        val f = getHeightFactor(); val m = 0f; val u = vw; val r = getHexR(vw); val hw = r * sqrt(3f); val rs = r * 1.5f * f; val tp = r * 0.05f * f
        val dr = r * keyScale; val dry = dr * f
        pStroke.strokeWidth = maxOf(1f, r * 0.02f)
        fun cx8(c: Float) = m + u/2f + (c - 3.5f) * hw
        fun cx7(c: Float) = m + u/2f + (c - 3f) * hw
        fun cx6(c: Float) = m + u/2f + (c - 2.5f) * hw
        fun ry(row: Int) = tp + dry + row * rs

        keys += Key("😀", KeyType.EMOJI, "EMOJI", emptyList(), cx = cx6(-1f), cy = ry(0), rx = dr, ry = dry)
        listOf("1","2","3","4","5","6").forEachIndexed { i, l ->
            val type = KeyType.CHAR
            val value = l
            val alt = (longPressAlternatives[l[0]] ?: emptyList())
            keys += Key(l, type, value, alt, cx = cx6(i.toFloat()), cy = ry(0), rx = dr, ry = dry)
        }
        keys += Key("CLIP", KeyType.CLIPBOARD, "CLIPBOARD", emptyList(), cx = cx6(6f), cy = ry(0), rx = dr, ry = dry)
        listOf("7","8","9","0","-","+","=").forEachIndexed { i, l -> keys += Key(l, KeyType.CHAR, l, longPressAlternatives[l[0]] ?: emptyList(), cx = cx7(i.toFloat()), cy = ry(1), rx = dr, ry = dry) }

        // Fila 2
        listOf("@", "#", "$", "_", "&", "(", ")", "*").forEachIndexed { i, l ->
            val alt = if (l.length == 1) (longPressAlternatives[l[0]] ?: emptyList()) else emptyList()
            keys += Key(l, KeyType.CHAR, l, alt, cx = cx8(i.toFloat()), cy = ry(2), rx = dr, ry = dry)
        }

        // Fila 3: Extended Keys
        keys += Key("", KeyType.SHIFT, "SHIFT", emptyList(), cx = cx7(-1.0f), cy = ry(3), rx = dr, ry = dry, isHalfLeft = true, gapRight = false)
        keys += Key("", KeyType.DELETE, "DELETE", emptyList(), cx = cx7(7.0f), cy = ry(3), rx = dr, ry = dry, isHalfRight = true, gapLeft = false)

        listOf("⇧", "\"", "'", ":", ".", "?", "⌫").forEachIndexed { i, l ->
            val type = when(l) {
                "⇧" -> KeyType.SHIFT
                "⌫" -> KeyType.DELETE
                else -> KeyType.CHAR
            }
            val value = when(l) {
                "⇧" -> "SHIFT"
                "⌫" -> "DELETE"
                else -> l
            }
            val alt = if (type == KeyType.CHAR) (longPressAlternatives[l[0]] ?: emptyList()) else emptyList()

            val finalCx = when(type) {
                KeyType.SHIFT -> cx7(0.0f)
                KeyType.DELETE -> cx7(6.0f)
                else -> cx7(i.toFloat())
            }
            val gL = type != KeyType.SHIFT
            val gR = type != KeyType.DELETE

            keys += Key(l, type, value, alt, cx = finalCx, cy = ry(3), rx = dr, ry = dry, gapLeft = gL, gapRight = gR)
        }

        // Fila 4 (Bottom)
        keys += Key("ABC", KeyType.TOGGLE, "123", listOf("#+=", "𝔉"), cx = cx6(-1f), cy = ry(4), rx = dr, ry = dry)
        keys += Key(",", KeyType.CHAR, ",", longPressAlternatives[','] ?: emptyList(), cx = cx6(0f), cy = ry(4), rx = dr, ry = dry)

        // Espacio (4 unidades)
        keys += Key(" ", KeyType.SPACE, " ", emptyList(), cx = cx6(1f), cy = ry(4), rx = dr, ry = dry, gapRight = false)
        keys += Key(" ", KeyType.SPACE, " ", emptyList(), cx = cx6(2f), cy = ry(4), rx = dr, ry = dry, gapLeft = false, gapRight = false)
        keys += Key(" ", KeyType.SPACE, " ", emptyList(), cx = cx6(3f), cy = ry(4), rx = dr, ry = dry, gapLeft = false, gapRight = false)
        keys += Key(" ", KeyType.SPACE, " ", emptyList(), cx = cx6(4f), cy = ry(4), rx = dr, ry = dry, gapLeft = false)

        // Fusión de ENTER (5 y 6)
        keys += Key("", KeyType.ENTER, "ENTER", emptyList(), cx = cx6(5f), cy = ry(4), rx = dr, ry = dry, gapRight = false)
        keys += Key("↩", KeyType.ENTER, "ENTER", emptyList(), cx = cx6(6f), cy = ry(4), rx = dr, ry = dry, gapLeft = false)
    }

    // Orden dinámico de letras para que cada tecla muestre
    // la versión estilizada de la letra que ocupa esa misma posición.
    private fun getFontLayoutLetterOrder(): List<Char> {
        return if (layoutType == "qwerty") {
            listOf(
                'q','w','e','r','t','y',
                'u','i','o','p','a','s','d',
                'f','g','h','j','k','l','z','x',
                'c','v','b','n','m'
            )
        } else {
            listOf(
                'q','w','f','g','p','b',
                'k','o','u','d','i','c','m',
                'a','e','s','r','h','t','n','l',
                'v','y','x','z','j'
            )
        }
    }

    private fun buildHexFontsLayout(vw: Float) {
        val f = getHeightFactor(); val m = 0f; val u = vw; val r = getHexR(vw); val hw = r * sqrt(3f); val rs = r * 1.5f * f; val tp = r * 0.05f * f
        val dr = r * keyScale; val dry = dr * f
        pStroke.strokeWidth = maxOf(1f, r * 0.02f)

        fun cx8(c: Float) = m + u/2f + (c - 3.5f) * hw
        fun cx7(c: Float) = m + u/2f + (c - 3f) * hw
        fun ry(row: Int) = tp + dry + row * rs

        val currentFonts = if (fontPages.size > fontPage) fontPages[fontPage] else List(52) { "" }
        val offset = if (shifted || capsLock) 0 else 26
        val letterOrder = getFontLayoutLetterOrder()

        var letterIdx = 0
        fun nextChar(): String {
            val letter = letterOrder.getOrElse(letterIdx) { 'a' }
            letterIdx++
            val alphaIndex = letter - 'a'
            return currentFonts.getOrElse(alphaIndex + offset) { letter.toString() }
        }

        // Fila 0: Emoji + 6 letras + CLIP
        keys += Key("😀", KeyType.EMOJI, "EMOJI", emptyList(), cx = cx8(0f), cy = ry(0), rx = dr, ry = dry)
        for (i in 0 until 6) {
            val char = nextChar()
            keys += Key(char, KeyType.CHAR, char, emptyList(), cx = cx8(i.toFloat() + 1f), cy = ry(0), rx = dr, ry = dry)
        }
        keys += Key("CLIP", KeyType.CLIPBOARD, "CLIPBOARD", emptyList(), cx = cx8(7f), cy = ry(0), rx = dr, ry = dry)

        // Fila 1: 7 letras
        for (i in 0 until 7) {
            val char = nextChar()
            keys += Key(char, KeyType.CHAR, char, emptyList(), cx = cx7(i.toFloat()), cy = ry(1), rx = dr, ry = dry)
        }

        // Fila 2: 8 letras
        for (i in 0 until 8) {
            val char = nextChar()
            keys += Key(char, KeyType.CHAR, char, emptyList(), cx = cx8(i.toFloat()), cy = ry(2), rx = dr, ry = dry)
        }

        // Fila 3: Extended Keys
        keys += Key("", KeyType.SHIFT, "SHIFT", emptyList(), cx = cx7(-1.0f), cy = ry(3), rx = dr, ry = dry, isHalfLeft = true, gapRight = false)
        keys += Key("", KeyType.DELETE, "DELETE", emptyList(), cx = cx7(7.0f), cy = ry(3), rx = dr, ry = dry, isHalfRight = true, gapLeft = false)
        keys += Key("⇧", KeyType.SHIFT, "SHIFT", emptyList(), cx = cx7(0.0f), cy = ry(3), rx = dr, ry = dry, gapLeft = false)
        for (i in 0 until 5) {
            val char = nextChar()
            keys += Key(char, KeyType.CHAR, char, emptyList(), cx = cx7(i.toFloat() + 1f), cy = ry(3), rx = dr, ry = dry)
        }
        keys += Key("⌫", KeyType.DELETE, "DELETE", emptyList(), cx = cx7(6.0f), cy = ry(3), rx = dr, ry = dry, gapRight = false)

        // Fila 4 (Bottom): [ABC, PageToggle, SPACE x4, ENTER x2]
        keys += Key("ABC", KeyType.TOGGLE, "?123", listOf("123", "#+=", "𝔉"), cx = cx8(0f), cy = ry(4), rx = dr, ry = dry)
        // Paginación en lugar de la coma
        keys += Key((fontPage + 1).toString(), KeyType.FONT_PAGE, "FONT_PAGE", (1..10).map { it.toString() }, cx = cx8(1f), cy = ry(4), rx = dr, ry = dry)

        // Espacio
        keys += Key(" ", KeyType.SPACE, " ", emptyList(), cx = cx8(2f), cy = ry(4), rx = dr, ry = dry, gapRight = false)
        keys += Key(" ", KeyType.SPACE, " ", emptyList(), cx = cx8(3f), cy = ry(4), rx = dr, ry = dry, gapLeft = false, gapRight = false)
        keys += Key(" ", KeyType.SPACE, " ", emptyList(), cx = cx8(4f), cy = ry(4), rx = dr, ry = dry, gapLeft = false, gapRight = false)
        keys += Key(" ", KeyType.SPACE, " ", emptyList(), cx = cx8(5f), cy = ry(4), rx = dr, ry = dry, gapLeft = false)

        // Fusión de ENTER (6 y 7)
        keys += Key("", KeyType.ENTER, "ENTER", emptyList(), cx = cx8(6f), cy = ry(4), rx = dr, ry = dry, gapRight = false)
        keys += Key("↩", KeyType.ENTER, "ENTER", emptyList(), cx = cx8(7f), cy = ry(4), rx = dr, ry = dry, gapLeft = false)
    }

    private fun buildHexSymbolLayout(vw: Float) {
        val f = getHeightFactor(); val m = 0f; val u = vw; val r = getHexR(vw); val hw = r * sqrt(3f); val rs = r * 1.5f * f; val tp = r * 0.05f * f
        val dr = r * keyScale; val dry = dr * f
        pStroke.strokeWidth = maxOf(1f, r * 0.02f)

        fun cx8(c: Float) = m + u/2f + (c - 3.5f) * hw
        fun cx7(c: Float) = m + u/2f + (c - 3f) * hw
        fun cx6(c: Float) = m + u/2f + (c - 2.5f) * hw
        fun ry(row: Int) = tp + dry + row * rs

        val symbols = symbolPages[symbolPage] ?: emptyList()
        val pages = symbolPages.keys.toList()

        var idx = 0
        fun nextSym(): String = if (idx < symbols.size) symbols[idx++] else ""

        // Fila 0: Emoji + 6 símbolos + Clipboard
        keys += Key("😀", KeyType.EMOJI, "EMOJI", emptyList(), cx = cx6(-1f), cy = ry(0), rx = dr, ry = dry)
        for (i in 0 until 6) {
            val s = nextSym()
            keys += Key(s, KeyType.CHAR, s, if (s.length == 1) longPressAlternatives[s[0]] ?: emptyList() else emptyList(), cx = cx6(i.toFloat()), cy = ry(0), rx = dr, ry = dry)
        }
        keys += Key("CLIP", KeyType.CLIPBOARD, "CLIPBOARD", emptyList(), cx = cx6(6f), cy = ry(0), rx = dr, ry = dry)

        // Fila 1: 7 símbolos
        for (i in 0 until 7) {
            val s = nextSym()
            keys += Key(s, KeyType.CHAR, s, if (s.length == 1) longPressAlternatives[s[0]] ?: emptyList() else emptyList(), cx = cx7(i.toFloat()), cy = ry(1), rx = dr, ry = dry)
        }

        // Fila 2: 8 símbolos
        for (i in 0 until 8) {
            val s = nextSym()
            keys += Key(s, KeyType.CHAR, s, if (s.length == 1) longPressAlternatives[s[0]] ?: emptyList() else emptyList(), cx = cx8(i.toFloat()), cy = ry(2), rx = dr, ry = dry)
        }

        // Fila 3: Extended Keys
        keys += Key("", KeyType.SHIFT, "SHIFT", emptyList(), cx = cx7(-1.0f), cy = ry(3), rx = dr, ry = dry, isHalfLeft = true, gapRight = false)
        keys += Key("", KeyType.DELETE, "DELETE", emptyList(), cx = cx7(7.0f), cy = ry(3), rx = dr, ry = dry, isHalfRight = true, gapLeft = false)
        keys += Key("⇧", KeyType.SHIFT, "SHIFT", emptyList(), cx = cx7(0.0f), cy = ry(3), rx = dr, ry = dry, gapLeft = false)
        for (i in 0 until 5) {
            val s = nextSym()
            keys += Key(s, KeyType.CHAR, s, if (s.length == 1) longPressAlternatives[s[0]] ?: emptyList() else emptyList(), cx = cx7(i.toFloat() + 1f), cy = ry(3), rx = dr, ry = dry)
        }
        keys += Key("⌫", KeyType.DELETE, "DELETE", emptyList(), cx = cx7(6.0f), cy = ry(3), rx = dr, ry = dry, gapRight = false)

        // Fila 4: ABC + Flecha + Espacio (4 hex) + Símbolo extra + Enter
        keys += Key("ABC", KeyType.TOGGLE, "?123", listOf("123", "𝔉"), cx = cx6(-1f), cy = ry(4), rx = dr, ry = dry)
        keys += Key("→", KeyType.SYMBOL_PAGE, "→", pages.mapIndexed { i, _ -> (i + 1).toString() }, cx = cx6(0f), cy = ry(4), rx = dr, ry = dry)

        keys += Key(" ", KeyType.SPACE, " ", emptyList(), cx = cx6(1f), cy = ry(4), rx = dr, ry = dry, gapRight = false)
        keys += Key(" ", KeyType.SPACE, " ", emptyList(), cx = cx6(2f), cy = ry(4), rx = dr, ry = dry, gapLeft = false, gapRight = false)
        keys += Key(" ", KeyType.SPACE, " ", emptyList(), cx = cx6(3f), cy = ry(4), rx = dr, ry = dry, gapLeft = false, gapRight = false)
        keys += Key(" ", KeyType.SPACE, " ", emptyList(), cx = cx6(4f), cy = ry(4), rx = dr, ry = dry, gapLeft = false)

        // Fusión de ENTER (5 y 6)
        keys += Key("", KeyType.ENTER, "ENTER", emptyList(), cx = cx6(5f), cy = ry(4), rx = dr, ry = dry, gapRight = false)
        keys += Key("↩", KeyType.ENTER, "ENTER", emptyList(), cx = cx6(6f), cy = ry(4), rx = dr, ry = dry, gapLeft = false)
    }

    private var keysBitmap: Bitmap? = null
    private val keysCanvas = Canvas()
    private var needsRedraw = true

    override fun invalidate() {
        needsRedraw = true
        super.invalidate()
    }

    private fun updateColors() {
        val theme = keyboardTheme ?: return
        applyTheme(theme)
    }

    private fun drawKeysToCache() {
        val w = width
        val h = height
        if (w <= 0 || h <= 0) return

        if (keysBitmap == null || keysBitmap?.width != w || keysBitmap?.height != h) {
            keysBitmap?.recycle()
            keysBitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            keysCanvas.setBitmap(keysBitmap)
        }

        keysCanvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)

        for (key in keys) {
            if (!key.isPressed && key.currentScale == 1.0f) {
                drawSingleKey(keysCanvas, key, pressed = false, appliedScale = 1.0f)
            }
        }
        needsRedraw = false
    }

    override fun onDraw(canvas: Canvas) {
        val theme = keyboardTheme ?: return

        pStroke.color = colorStroke
        pStroke.strokeWidth = strokeWidth
        pText.typeface = sharedTypeface
        pText.isFakeBoldText = false

        if (drawBackground) {
            val img = backgroundBlurImage ?: backgroundImage
            if (img != null && !img.isRecycled) {
                pBackground.alpha = (theme.backgroundOpacity * 255).toInt()

                val baseScale = max(width.toFloat() / img.width, height.toFloat() / img.height)
                val parallaxScale = if (theme.parallaxEffect) 1.0f + parallaxLimit else 1.0f
                val finalScale = baseScale * parallaxScale
                val dw = img.width * finalScale
                val dh = img.height * finalScale

                val baseDx = (width - dw) / 2f
                val baseDy = (height - dh) / 2f

                val offsetX = if (theme.parallaxEffect) parallaxX * (dw - width) / 2f else 0f
                val offsetY = if (theme.parallaxEffect) parallaxY * (dh - height) / 2f else 0f

                rectF.set(baseDx + offsetX, baseDy + offsetY, baseDx + offsetX + dw, baseDy + offsetY + dh)
                canvas.drawBitmap(img, null, rectF, pBackground)
            }
        }

        if (needsRedraw || keysBitmap == null) {
            drawKeysToCache()
        }
        keysBitmap?.let { canvas.drawBitmap(it, 0f, 0f, null) }

        for (key in keys) {
            if (key.isPressed || key.currentScale != 1.0f) {
                drawKey(canvas, key)
            }
        }

        if (isGestureActive && !gesturePath.isEmpty) {
            canvas.drawPath(gesturePath, pGesture)
        }
    }

    private fun drawKey(canvas: Canvas, key: Key) {
        val currentThemeObj = keyboardTheme ?: return

        canvas.save()

        val isSpace = key.type == KeyType.SPACE

        val pressed = when {
            isSpace -> spacePressed && popupVisibleKey == null
            key in shiftKeys -> shiftPressed && popupVisibleKey == null
            key in deleteKeys -> deletePressed && popupVisibleKey == null
            key in enterKeys -> enterPressed && popupVisibleKey == null
            else -> key.isPressed && popupVisibleKey == null
        }

        val appliedScale = when {
            isSpace && spaceKeys.size >= 2 -> spaceKeys[0].currentScale
            key in shiftKeys && shiftKeys.size >= 2 -> shiftKeys.maxOf { it.currentScale }
            key in deleteKeys && deleteKeys.size >= 2 -> deleteKeys.maxOf { it.currentScale }
            key in enterKeys && enterKeys.size >= 2 -> enterKeys.maxOf { it.currentScale }
            else -> key.currentScale
        }

        if (isSpace) {
            if (spaceKeys.size >= 2) {
                val unifiedCenterX = (spaceKeys.minOf { it.cx } + spaceKeys.maxOf { it.cx }) / 2f
                canvas.scale(appliedScale, appliedScale, unifiedCenterX, key.cy)
            } else {
                canvas.scale(appliedScale, appliedScale, key.cx, key.cy)
            }
        } else if (key in shiftKeys && shiftKeys.size >= 2) {
            canvas.scale(appliedScale, appliedScale, unifiedShiftCenterX, key.cy)
        } else if (key in deleteKeys && deleteKeys.size >= 2) {
            canvas.scale(appliedScale, appliedScale, unifiedDeleteCenterX, key.cy)
        } else if (key in enterKeys && enterKeys.size >= 2) {
            val unifiedCenterX = (enterKeys.minOf { it.cx } + enterKeys.maxOf { it.cx }) / 2f
            canvas.scale(appliedScale, appliedScale, unifiedCenterX, key.cy)
        } else {
            canvas.scale(appliedScale, appliedScale, key.cx, key.cy)
        }

        drawSingleKey(canvas, key, pressed, appliedScale)
        canvas.restore()
    }

    private fun drawSingleKey(canvas: Canvas, key: Key, pressed: Boolean, appliedScale: Float) {
        val currentThemeObj = keyboardTheme ?: return
        val floating = key.type == KeyType.CLIPBOARD || key.type == KeyType.FUNCTIONS || key.type == KeyType.EMOJI || key.type == KeyType.LANGUAGE

        val individualColor = currentThemeObj.individualKeyColors[key.value]
        var activeBgColor = colorFill

        if (!floating) {
            val fill = when {
                pressed -> { pPress.color = colorPress; pPress }
                key in shiftKeys && (shifted || capsLock) -> { pFill.color = colorShiftActive; pFill }
                key in shiftKeys && (!shifted && !capsLock) -> { pFill.color = colorShiftInactive; pFill }
                individualColor != null -> { pFill.color = individualColor; pFill }
                key.type in listOf(KeyType.TOGGLE, KeyType.ENTER, KeyType.SYMBOL_PAGE, KeyType.EMOJI, KeyType.FUNCTIONS, KeyType.SHIFT, KeyType.DELETE) -> { pSpec.color = colorSpec; pSpec }
                else -> { pFill.color = colorFill; pFill }
            }
            activeBgColor = fill.color

            if (!pressed) {
                if (key in shiftKeys && (shifted || capsLock)) {
                    fill.color = colorShiftActive
                } else if (key in shiftKeys && (!shifted && !capsLock)) {
                    fill.color = colorShiftInactive
                } else if (individualColor != null) {
                    fill.color = individualColor
                } else if (key.type in listOf(KeyType.TOGGLE, KeyType.ENTER, KeyType.SYMBOL_PAGE, KeyType.SHIFT, KeyType.DELETE)) {
                    fill.color = colorSpec
                } else {
                    fill.color = colorFill
                }

                if (keyboardTheme?.keysBlur ?: 0f > 0f) {
                    fill.alpha = 200
                }
            }

            canvas.drawPath(key.path, fill)

            val isShiftCapsLock = key.type == KeyType.SHIFT && capsLock
            if (isShiftCapsLock) {
                val oldColor = pStroke.color
                val oldWidth = pStroke.strokeWidth
                pStroke.color = Color.rgb(227, 91, 18)
                pStroke.strokeWidth = (2f * context.resources.displayMetrics.density) / appliedScale
                canvas.drawPath(key.strokePath, pStroke)
                pStroke.color = oldColor
                pStroke.strokeWidth = oldWidth
            } else if (strokeWidth > 0) {
                val oldWidth = pStroke.strokeWidth
                pStroke.strokeWidth = strokeWidth / appliedScale
                canvas.drawPath(key.strokePath, pStroke)
                pStroke.strokeWidth = oldWidth
            }
        }

        if (key.isHalfLeft || key.isHalfRight || (key in enterKeys && enterKeys.size >= 2 && key.display.isEmpty())) {
            return
        }

        val label = if (shifted && key.type == KeyType.CHAR && key.value.length == 1 && key.value[0].isLetter()) key.display.uppercase() else key.display

        var customLabel: String? = null
        val isEnterWithText = key.type == KeyType.ENTER && layoutMode in listOf(LayoutMode.ALPHA, LayoutMode.FONTS, LayoutMode.SYMBOLS, LayoutMode.NUMERIC)

        when (key.type) {
            KeyType.SHIFT -> customLabel = "MAYUS"
            KeyType.DELETE -> customLabel = "BORRAR"
            KeyType.SPACE -> {
                customLabel = try {
                    val loc = java.util.Locale(language)
                    loc.getDisplayLanguage(loc).uppercase()
                } catch (_: Exception) { language.uppercase() }
            }
            KeyType.ENTER -> {
                if (isEnterWithText) {
                    customLabel = if (isMultiLine) {
                        "INTRO"
                    } else {
                        when (currentImeAction) {
                            EditorInfo.IME_ACTION_GO -> "IR"
                            EditorInfo.IME_ACTION_SEARCH -> "BUSCAR"
                            EditorInfo.IME_ACTION_SEND -> "ENVIAR"
                            EditorInfo.IME_ACTION_NEXT -> "SIG."
                            EditorInfo.IME_ACTION_DONE -> "HECHO"
                            EditorInfo.IME_ACTION_PREVIOUS -> "ANT."
                            else -> "INTRO"
                        }
                    }
                }
            }
            else -> {}
        }

        val sz = when {
            customLabel != null && (key.type == KeyType.SHIFT || key.type == KeyType.DELETE || key.type == KeyType.ENTER || key.type == KeyType.SPACE) ->
                min(key.rx, key.ry) * 0.45f
            customLabel != null && customLabel.length <= 3 -> min(key.rx, key.ry) * 0.5f
            customLabel != null -> min(key.rx, key.ry) * 0.35f
            else -> min(key.rx, key.ry) * 0.62f
        }
        pText.textSize = sz
        pText.isFakeBoldText = customLabel != null
        pText.getFontMetrics(fontMetrics)
        val ty = key.cy - (fontMetrics.ascent + fontMetrics.descent) / 2f

        val dr = if (customLabel != null) {
            null
        } else if (currentTheme == "default" && (key.type == KeyType.ENTER && layoutMode != LayoutMode.PURE_NUMERIC)) {
            null
        } else {
            when (key.type) {
                KeyType.ENTER -> {
                    if (isMultiLine) {
                        iconEnter
                    } else {
                        when (currentImeAction) {
                            EditorInfo.IME_ACTION_SEND -> iconSend
                            EditorInfo.IME_ACTION_SEARCH -> iconSearch
                            EditorInfo.IME_ACTION_DONE -> iconDone
                            EditorInfo.IME_ACTION_GO -> iconGo
                            else -> iconEnter
                        }
                    }
                }
                KeyType.SYMBOL_PAGE -> iconSymbols
                KeyType.FONT_PAGE -> null
                KeyType.EMOJI -> if (keyboardTheme?.backgroundImageUri != null) iconEmojiFilled else iconEmoji
                KeyType.CLIPBOARD -> if (keyboardTheme?.backgroundImageUri != null) iconClipboardFilled else iconClipboard
                KeyType.FUNCTIONS -> iconPuzzle
                KeyType.LANGUAGE -> iconLanguage
                else -> null
            }
        }

        val drawCx = when {
            key in shiftKeys && shiftKeys.size >= 2 -> unifiedShiftCenterX
            key in deleteKeys && deleteKeys.size >= 2 -> unifiedDeleteCenterX
            key in enterKeys && enterKeys.size >= 2 -> unifiedEnterCenterX
            key in spaceKeys && spaceKeys.size >= 2 -> unifiedSpaceCenterX
            else -> key.cx
        }

        if (dr != null) {
            val s = (min(key.rx, key.ry) * (if (key.type == KeyType.FUNCTIONS || key.type == KeyType.CLIPBOARD || key.type == KeyType.EMOJI) 0.8f else 1.1f)).toInt()

            dr.setBounds((drawCx - s/2).toInt(), (key.cy - s/2).toInt(), (drawCx + s/2).toInt(), (key.cy + s/2).toInt())
            val tint = when {
                key.type == KeyType.ENTER && individualColor != null -> Color.WHITE
                floating -> colorIcon
                else -> {
                    if (isKeyTextColorCustom) {
                        colorText
                    } else {
                        val isLightBg = ColorUtils.calculateLuminance(activeBgColor) > 0.5
                        val isLightText = ColorUtils.calculateLuminance(colorText) > 0.5
                        if (isLightBg == isLightText) {
                            if (isLightBg) Color.BLACK else Color.WHITE
                        } else {
                            colorText
                        }
                    }
                }
            }
            dr.mutate().setTint(tint); dr.alpha = if (pressed) 128 else 255; dr.draw(canvas)
        } else if (key.type == KeyType.SYMBOL_PAGE) {
            val drawColor = if (isKeyTextColorCustom) {
                colorIcon
            } else {
                val isLightBg = ColorUtils.calculateLuminance(activeBgColor) > 0.5
                if (isLightBg) Color.BLACK else colorIcon
            }
            pText.color = drawColor
            pText.textSize = sz * 0.8f
            canvas.drawText("SYM", drawCx, ty, pText)
            pText.color = colorText; pText.textSize = sz
        } else {
            if (floating) {
                pText.color = colorIcon
            } else {
                if (isKeyTextColorCustom) {
                    pText.color = colorText
                } else {
                    val isLightBg = ColorUtils.calculateLuminance(activeBgColor) > 0.5
                    val isLightText = ColorUtils.calculateLuminance(colorText) > 0.5
                    pText.color = if (isLightBg == isLightText) {
                        if (isLightBg) Color.BLACK else Color.WHITE
                    } else {
                        colorText
                    }
                }
            }
            pText.alpha = if (floating && pressed) 128 else 255

            var drawLabel = customLabel ?: label
            if (customLabel == null && currentTheme == "default" && (key.type == KeyType.ENTER && layoutMode != LayoutMode.PURE_NUMERIC)) {
                drawLabel = ""
            }

            if (drawLabel.isNotEmpty()) {
                canvas.drawText(drawLabel, drawCx, ty, pText)
            }

            if (showLongPressIndicators && key.alternatives.isNotEmpty()) {
                val dotR = key.rx * 0.08f
                val dx = key.cx + key.rx * 0.4f
                val dy = key.cy - key.ry * 0.5f
                pPopupBg.color = pText.color
                pPopupBg.alpha = 150
                canvas.drawCircle(dx, dy, dotR, pPopupBg)
                pPopupBg.alpha = 255
            }

            pText.alpha = 255
        }
    }

    private fun updateHexPath(p: Path, cx: Float, cy: Float, rx: Float, ry: Float) {
        val s32 = 0.8660254f
        p.reset()
        val p0x = cx; val p0y = cy - ry
        val p1x = cx + rx * s32; val p1y = cy - ry / 2f
        val p2x = cx + rx * s32; val p2y = cy + ry / 2f
        val p3x = cx; val p3y = cy + ry
        val p4x = cx - rx * s32; val p4y = cy + ry / 2f
        val p5x = cx - rx * s32; val p5y = cy - ry / 2f

        val rounding = 0.2f
        fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t

        p.moveTo(lerp(p0x, p5x, rounding), lerp(p0y, p5y, rounding))
        p.quadTo(p0x, p0y, lerp(p0x, p1x, rounding), lerp(p0y, p1y, rounding))
        p.lineTo(lerp(p1x, p0x, rounding), lerp(p1y, p0y, rounding))
        p.quadTo(p1x, p1y, lerp(p1x, p2x, rounding), lerp(p1y, p2y, rounding))
        p.lineTo(lerp(p2x, p1x, rounding), lerp(p2y, p1y, rounding))
        p.quadTo(p2x, p2y, lerp(p2x, p3x, rounding), lerp(p2y, p3y, rounding))
        p.lineTo(lerp(p3x, p2x, rounding), lerp(p3y, p2y, rounding))
        p.quadTo(p3x, p3y, lerp(p3x, p4x, rounding), lerp(p3y, p4y, rounding))
        p.lineTo(lerp(p4x, p3x, rounding), lerp(p4y, p3y, rounding))
        p.quadTo(p4x, p4y, lerp(p4x, p5x, rounding), lerp(p4y, p5y, rounding))
        p.lineTo(lerp(p5x, p4x, rounding), lerp(p5y, p4y, rounding))
        p.quadTo(p5x, p5y, lerp(p5x, p0x, rounding), lerp(p5y, p0y, rounding))
        p.close()
    }

    fun triggerVibration(type: FeedbackManager.HapticType = FeedbackManager.HapticType.KEY_CLICK) {
        FeedbackManager.triggerVibration(context, type)
    }

    fun triggerSound() {
        FeedbackManager.triggerSound(context)
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        val action = e.actionMasked
        val pointerIndex = e.actionIndex
        val pointerId = e.getPointerId(pointerIndex)
        val x = e.getX(pointerIndex)
        val y = e.getY(pointerIndex)

        when (action) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                if (keys.isEmpty() && width > 0) buildLayout(width.toFloat())
                val hit = hitTest(x, y)
                if (hit != null) {
                    activePointers[pointerId] = hit
                    hit.isPressed = true
                    pointerStartTime[pointerId] = System.currentTimeMillis()

                    // No sonar ni vibrar automáticamente para la tecla de borrado.
                    // El HexKeyboardService se encargará de ello si hay texto que borrar.
                    if (hit.type != KeyType.DELETE) {
                        triggerVibration(FeedbackManager.HapticType.KEY_CLICK)
                        triggerSound()
                    }

                    if (pointerId == e.getPointerId(0)) {
                        val currentPressed = pressedKey
                        if (hit != currentPressed) {
                            if (currentPressed in spaceKeys && hit in spaceKeys) {
                                // Continuar
                            } else if (currentPressed in shiftKeys && hit in shiftKeys) {
                                // Continuar
                            } else if (currentPressed in deleteKeys && hit in deleteKeys) {
                                // Continuar
                            } else {
                                when {
                                    currentPressed in spaceKeys -> spaceKeys.forEach { it.animateToScale(1.0f) }
                                    currentPressed in shiftKeys -> shiftKeys.forEach { it.animateToScale(1.0f) }
                                    currentPressed in deleteKeys -> deleteKeys.forEach { it.animateToScale(1.0f) }
                                    currentPressed in enterKeys -> enterKeys.forEach { it.animateToScale(1.0f) }
                                    else -> currentPressed?.animateToScale(1.0f)
                                }
                                cancelKeyLongPress()
                                cancelDeleteRepeat()
                            }
                        }
                        pressedKey = hit

                        when {
                            hit in spaceKeys -> {
                                spacePressed = true
                                spaceKeys.forEach { it.animateToScale(0.85f) }
                            }
                            hit in shiftKeys -> {
                                shiftPressed = true
                                shiftKeys.forEach { it.animateToScale(0.85f) }
                            }
                            hit in deleteKeys -> {
                                deletePressed = true
                                deleteKeys.forEach { it.animateToScale(0.85f) }
                            }
                            hit in enterKeys -> {
                                enterPressed = true
                                enterKeys.forEach { it.animateToScale(0.85f) }
                            }
                            else -> hit.animateToScale(0.85f)
                        }

                        lastScrollX = x
                        lastScrollY = y
                        gestureStartX = x
                        gestureStartY = y
                        gesturePoints.clear()
                        gesturePath.reset()
                        gesturePoints.add(PointF(x, y))
                        gesturePath.moveTo(x, y)
                        isGestureActive = false

                        isScrollingCursor = false
                        longPressTriggered = false; longPressStarted = false; popupVisibleKey = null; popupSelectedIndex = -1
                        if (showKeyPopup && hit.type == KeyType.CHAR) {
                            popupVisibleKey = hit
                            popupSelectedIndex = 0
                            showPopup(hit, false)
                        }
                        if (hit.alternatives.isNotEmpty()) handler.postDelayed(longPressRunnable, longPressTimeout)
                    } else {
                        when {
                            hit in spaceKeys -> {
                                spacePressed = true
                                spaceKeys.forEach { it.animateToScale(0.85f) }
                            }
                            hit in shiftKeys -> {
                                shiftPressed = true
                                shiftKeys.forEach { it.animateToScale(0.85f) }
                            }
                            hit in deleteKeys -> {
                                deletePressed = true
                                deleteKeys.forEach { it.animateToScale(0.85f) }
                            }
                            hit in enterKeys -> {
                                enterPressed = true
                                enterKeys.forEach { it.animateToScale(0.85f) }
                            }
                            else -> hit.animateToScale(0.85f)
                        }
                    }

                    if (hit.type == KeyType.DELETE || hit.isHalfRight) { listener?.onDelete(); startDeleteRepeat() }
                    invalidate()
                }
            }
            MotionEvent.ACTION_MOVE -> {
                if (popupVisibleKey != null && longPressStarted && pointerId == e.getPointerId(0)) {
                    handlePopupSelection(x, y)
                } else if (pressedKey?.type == KeyType.SPACE && pointerId == e.getPointerId(0)) {
                    val deltaX = x - lastScrollX
                    if (abs(deltaX) > 20) {
                        isScrollingCursor = true
                        val direction = if (deltaX > 0) 1 else -1
                        (context as? HexKeyboardService)?.moveCursor(direction)
                        lastScrollX = x
                        invalidate()
                    }
                } else if (pointerId == e.getPointerId(0) && pressedKey?.type == KeyType.CHAR && !longPressStarted && !isScrollingCursor && !longPressTriggered) {
                    val deltaY = y - lastScrollY
                    val deltaX = x - gestureStartX
                    val totalDist = sqrt(deltaX * deltaX + (y - gestureStartY) * (y - gestureStartY))

                    // Lógica unificada para Ñ y Glide
                    if (pressedKey?.value == "n") {
                        if (deltaY < -100) {
                            // Gesto de la Ñ completado
                            longPressTriggered = true
                            isGestureActive = false
                            gesturePath.reset()
                            gesturePoints.clear()
                            cancelKeyLongPress()
                            hidePopup()
                            triggerVibration(FeedbackManager.HapticType.LONG_PRESS)
                            triggerSound()
                            val char = if (shifted || capsLock) "Ñ" else "ñ"
                            listener?.onChar(char)
                            if (shifted && !capsLock) { shifted = false }
                            invalidate()
                        } else if (!isGestureActive && totalDist > gestureThreshold) {
                            // Si el movimiento no es puramente hacia arriba, activar Glide
                            val isVerticalSwipe = deltaY < -20 && abs(deltaX) < abs(y - gestureStartY) * 0.5f
                            if (!isVerticalSwipe) {
                                isGestureActive = true
                                cancelKeyLongPress()
                                hidePopup()
                            }
                        }
                    } else if (!isGestureActive && totalDist > gestureThreshold) {
                        isGestureActive = true
                        cancelKeyLongPress()
                        hidePopup()
                    }

                    if (isGestureActive) {
                        gesturePoints.add(PointF(x, y))
                        gesturePath.lineTo(x, y)
                        invalidate()
                    }
                }

                for (i in 0 until e.pointerCount) {
                    val pid = e.getPointerId(i)

                    // Impedir que el bucle de transición cancele gestos en curso o selecciones de alternativas
                    if (pid == e.getPointerId(0)) {
                        val isSpecialMode = longPressStarted || isScrollingCursor || longPressTriggered || isGestureActive
                        // Si estamos en la tecla 'n', no cambiar de tecla a menos que ya estemos en modo Glide
                        val isNProtected = pressedKey?.value == "n" && !isGestureActive

                        if (isSpecialMode || isNProtected) {
                            continue
                        }
                    }

                    val px = e.getX(i)
                    val py = e.getY(i)
                    val oldHit = activePointers[pid]
                    val newHit = hitTest(px, py)
                    if (newHit != oldHit) {
                        oldHit?.isPressed = false
                        val isSpaceTransition = oldHit in spaceKeys && newHit in spaceKeys
                        val isShiftTransition = oldHit in shiftKeys && newHit in shiftKeys
                        val isDeleteTransition = oldHit in deleteKeys && newHit in deleteKeys
                        val isEnterTransition = oldHit in enterKeys && newHit in enterKeys

                        if (!isSpaceTransition && !isShiftTransition && !isDeleteTransition && !isEnterTransition) {
                            when {
                                oldHit in spaceKeys -> spaceKeys.forEach { it.animateToScale(1.0f) }
                                oldHit in shiftKeys -> shiftKeys.forEach { it.animateToScale(1.0f) }
                                oldHit in deleteKeys -> deleteKeys.forEach { it.animateToScale(1.0f) }
                                oldHit in enterKeys -> enterKeys.forEach { it.animateToScale(1.0f) }
                                else -> oldHit?.animateToScale(1.0f)
                            }

                            if (newHit != null) {
                                when {
                                    newHit in spaceKeys -> spaceKeys.forEach { it.animateToScale(0.85f) }
                                    newHit in shiftKeys -> shiftKeys.forEach { it.animateToScale(0.85f) }
                                    newHit in deleteKeys -> deleteKeys.forEach { it.animateToScale(0.85f) }
                                    newHit in enterKeys -> enterKeys.forEach { it.animateToScale(0.85f) }
                                    else -> newHit.animateToScale(0.85f)
                                }
                            }
                        }

                        if (newHit != null) {
                            activePointers[pid] = newHit
                            newHit.isPressed = true
                        } else {
                            activePointers.remove(pid)
                        }

                        spacePressed = activePointers.values.any { it in spaceKeys }
                        shiftPressed = activePointers.values.any { it in shiftKeys }
                        deletePressed = activePointers.values.any { it in deleteKeys }
                        enterPressed = activePointers.values.any { it in enterKeys }

                        if (pid == e.getPointerId(0)) {
                            if (pressedKey?.type == KeyType.DELETE || pressedKey?.isHalfRight == true) cancelDeleteRepeat()
                            pressedKey = newHit
                            if (!isSpaceTransition && !isShiftTransition && !isDeleteTransition) {
                                cancelKeyLongPress()
                                if (showKeyPopup && newHit != null && newHit.type == KeyType.CHAR) {
                                    popupVisibleKey = newHit
                                    popupSelectedIndex = 0
                                    showPopup(newHit, false)
                                } else {
                                    hidePopup()
                                }
                                if (newHit?.alternatives?.isNotEmpty() == true && !longPressStarted) handler.postDelayed(longPressRunnable, longPressTimeout)
                            }
                        }
                        invalidate()
                    }
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                val hit = activePointers[pointerId]
                if (hit != null) {
                    hit.isPressed = false
                    when {
                        hit in spaceKeys -> spaceKeys.forEach { it.animateToScale(1.0f) }
                        hit in shiftKeys -> shiftKeys.forEach { it.animateToScale(1.0f) }
                        hit in deleteKeys -> deleteKeys.forEach { it.animateToScale(1.0f) }
                        hit in enterKeys -> enterKeys.forEach { it.animateToScale(1.0f) }
                        else -> hit.animateToScale(1.0f)
                    }

                    if (pointerId == e.getPointerId(0)) {
                        cancelKeyLongPress(); cancelDeleteRepeat()
                        if (isGestureActive) {
                            listener?.onGesture(ArrayList(gesturePoints))
                            isGestureActive = false
                            gesturePath.reset()
                            gesturePoints.clear()
                            invalidate()
                        } else if (popupVisibleKey != null && longPressStarted) {
                            if (popupSelectedIndex >= 0) {
                                val a = listOf(popupVisibleKey!!.value) + popupVisibleKey!!.alternatives
                                if (popupSelectedIndex < a.size) {
                                    triggerVibration(FeedbackManager.HapticType.KEY_CLICK)
                                    val selectedValue = a[popupSelectedIndex]
                                    when (popupVisibleKey!!.type) {
                                        KeyType.LANGUAGE -> {
                                            if (selectedValue == "🌐") {
                                                (context as? HexKeyboardService)?.showLanguagePicker()
                                            } else {
                                                (context as? HexKeyboardService)?.switchToNextLanguage()
                                            }
                                        }
                                        KeyType.TOGGLE -> switchLayoutByValue(selectedValue)
                                        KeyType.SYMBOL_PAGE -> {
                                            if (selectedValue == "→") fireKey(popupVisibleKey!!)
                                            else switchSymbolPageByValue(selectedValue)
                                        }
                                        KeyType.FONT_PAGE -> {
                                            fontPage = (selectedValue.toIntOrNull() ?: 1) - 1
                                            buildLayout(width.toFloat())
                                            invalidate()
                                        }
                                        else -> listener?.onLongPressSelect(selectedValue)
                                    }
                                }
                            }
                            hidePopup()
                        } else if (isScrollingCursor) {
                            hidePopup()
                        } else {
                            if (!longPressTriggered) {
                                fireKey(hit); performClick()
                            }
                            hidePopup()
                        }
                        pressedKey = null; longPressTriggered = false; longPressStarted = false; isScrollingCursor = false
                    } else {
                        fireKey(hit)
                    }
                    activePointers.remove(pointerId)
                    pointerStartTime.remove(pointerId)
                    spacePressed = activePointers.values.any { it in spaceKeys }
                    shiftPressed = activePointers.values.any { it in shiftKeys }
                    deletePressed = activePointers.values.any { it in deleteKeys }
                    enterPressed = activePointers.values.any { it in enterKeys }
                    invalidate()
                }
            }
            MotionEvent.ACTION_CANCEL -> {
                keys.forEach {
                    it.animateToScale(1.0f)
                    it.isPressed = false
                }
                activePointers.clear()
                pointerStartTime.clear()
                spacePressed = false
                shiftPressed = false
                deletePressed = false
                enterPressed = false
                isGestureActive = false
                gesturePath.reset()
                gesturePoints.clear()
                cancelKeyLongPress(); cancelDeleteRepeat()
                hidePopup()
                pressedKey = null; longPressTriggered = false; longPressStarted = false; isScrollingCursor = false; invalidate()
            }
        }
        return true
    }

    private fun switchSymbolPageByValue(value: String) {
        val pages = symbolPages.keys.toList()
        val index = value.toIntOrNull()?.minus(1) ?: return
        if (index in pages.indices) {
            symbolPage = pages[index]
            listener?.onSymbolPageChange(symbolPage)
            buildLayout(width.toFloat())
            invalidate()
        }
    }

    private fun switchLayoutByValue(value: String) {
        layoutMode = when (value) {
            "ABC" -> LayoutMode.ALPHA
            "?123" -> LayoutMode.NUMERIC
            "123" -> LayoutMode.PURE_NUMERIC
            "#+=" -> LayoutMode.SYMBOLS
            "𝔉" -> LayoutMode.FONTS
            else -> return
        }
    }

    private fun handlePopupSelection(x: Float, y: Float = 0f) {
        val k = popupVisibleKey ?: return
        val prx = k.rx * keyScale * 1.1f * popupScale
        val pry = k.ry * keyScale * 1.1f * popupScale

        val alts = listOf(k.value) + k.alternatives
        val sW = prx * 1.35f
        val sH = pry * 1.35f
        val g = sW * 0.12f
        val p = sW * 0.45f

        val w = popupWindow?.width?.toFloat() ?: width.toFloat()
        val h = popupWindow?.height?.toFloat() ?: 0f

        val maxW = w - p * 2f
        val idealCols = when {
            alts.size <= 5 -> alts.size
            alts.size <= 10 -> 5
            else -> 6
        }
        var cols = idealCols.coerceAtMost(alts.size)
        while (cols > 1 && (cols * sW + (cols - 1) * g) > maxW) {
            cols--
        }
        val rows = (alts.size + cols - 1) / cols

        val th = rows * sH + (rows - 1) * g

        val location = IntArray(2)
        getLocationInWindow(location)

        val screenWidth = context.resources.displayMetrics.widthPixels
        val offsetX = (k.cx - w / 2f)
        var finalPopupX = location[0] + offsetX
        if (finalPopupX < 0) finalPopupX = 0f
        if (finalPopupX + w > screenWidth) finalPopupX = (screenWidth - w).toFloat()

        val relativeX = x - finalPopupX
        val relativeY = y - (location[1] + (k.cy - k.ry * (1.1f + 1.2f * popupScale) - h / 2f))

        var minDist = Float.MAX_VALUE
        var newIndex = 0

        alts.forEachIndexed { i, _ ->
            val row = i / cols
            val col = i % cols
            val itemsInThisRow = if (row == rows - 1) alts.size - (row * cols) else cols
            val rowW = itemsInThisRow * sW + (itemsInThisRow - 1) * g

            val kx = w/2f - rowW/2f + sW/2f + col * (sW + g)
            val ky = h/2f - th/2f + sH/2f + row * (sH + g)

            val dx = relativeX - kx
            val dy = relativeY - ky
            val dist = dx*dx + dy*dy
            if (dist < minDist) {
                minDist = dist
                newIndex = i
            }
        }

        newIndex = newIndex.coerceIn(0, alts.size - 1)

        if (newIndex != popupSelectedIndex) {
            popupSelectedIndex = newIndex
            triggerVibration(FeedbackManager.HapticType.TICK)
            popupContentView?.selectedIndex = newIndex
            popupContentView?.invalidate()
        }
    }

    private fun hitTest(x: Float, y: Float): Key? {
        if (keys.isEmpty()) return null

        // Ignorar teclas específicas durante el gesto de la 'Ñ' para evitar toques accidentales
        val isNGestureActive = longPressTriggered && pressedKey?.value == "n"

        var best: Key? = null; var bd = Float.MAX_VALUE
        for (k in keys) {
            if (isNGestureActive && k.value in keysToIgnoreDuringNGesture) continue
            if (k.contains(x, y)) { val d = hypot(x - k.cx, y - k.cy); if (d < bd) { bd = d; best = k } }
        }
        if (best == null) {
            for (k in keys) {
                if (isNGestureActive && k.value in keysToIgnoreDuringNGesture) continue
                if (abs(x - k.cx) < k.rx && abs(y - k.cy) < k.ry) { best = k; break }
            }
        }
        return best
    }

    private fun fireKey(key: Key) {
        listener?.onKeyClick(key)
        when (key.type) {
            KeyType.LANGUAGE -> {
                (context as? HexKeyboardService)?.switchToNextLanguage()
            }
            KeyType.SHIFT -> {
                val n = System.currentTimeMillis()
                if (n - lastShiftClickTime <= doubleClickThreshold) {
                    capsLock = true
                    shifted = true
                } else {
                    if (capsLock) {
                        capsLock = false
                        shifted = false
                    } else {
                        shifted = !shifted
                    }
                }
                lastShiftClickTime = n
                invalidate()
            }
            KeyType.ENTER -> listener?.onEnter()
            KeyType.SPACE -> listener?.onChar(" ")
            KeyType.TOGGLE -> {
                layoutMode = when (layoutMode) {
                    LayoutMode.ALPHA -> LayoutMode.NUMERIC
                    LayoutMode.NUMERIC -> LayoutMode.ALPHA
                    LayoutMode.SYMBOLS -> LayoutMode.ALPHA
                    LayoutMode.PURE_NUMERIC -> LayoutMode.ALPHA
                    LayoutMode.FONTS -> LayoutMode.ALPHA
                }
                if (layoutMode == LayoutMode.ALPHA) {
                    (context as? HexKeyboardService)?.updateShiftState()
                }
            }
            KeyType.SYMBOL_PAGE -> {
                val pages = symbolPages.keys.toList()
                if (pages.isNotEmpty()) {
                    val i = pages.indexOf(symbolPage)
                    symbolPage = pages[(i + 1) % pages.size]
                    listener?.onSymbolPageChange(symbolPage)
                    buildLayout(width.toFloat())
                    invalidate()
                }
            }
            KeyType.FONT_PAGE -> {
                fontPage = (fontPage + 1) % 10
                buildLayout(width.toFloat())
                invalidate()
            }
            KeyType.CHAR -> {
                val o = if (shifted && key.value.length == 1 && key.value[0].isLetter()) key.value.uppercase() else key.value
                listener?.onChar(o)
                if (shifted && !capsLock) { shifted = false; invalidate() }
            }
            else -> {}
        }
    }

    private fun cancelKeyLongPress() { handler.removeCallbacks(longPressRunnable) }

    private var deleteRepeatCount = 0
    private fun startDeleteRepeat() {
        cancelDeleteRepeat()
        deleteRepeatCount = 0
        deleteRepeatRunnable = object : Runnable {
            override fun run() {
                deleteRepeatCount++
                // La vibración y sonido ahora los gestiona el listener (Service)
                listener?.onDelete()

                // Aceleración de borrado: empezamos en 400ms y bajamos hasta 50ms
                val nextDelay = when {
                    deleteRepeatCount < 5 -> deleteRepeatInterval // 80ms
                    deleteRepeatCount < 15 -> 60L
                    else -> 45L
                }
                handler.postDelayed(this, nextDelay)
            }
        }
        handler.postDelayed(deleteRepeatRunnable!!, deleteRepeatDelay)
    }
    private fun cancelDeleteRepeat() { deleteRepeatRunnable?.let { handler.removeCallbacks(it) }; deleteRepeatRunnable = null }
    override fun performClick(): Boolean { super.performClick(); return true }
}