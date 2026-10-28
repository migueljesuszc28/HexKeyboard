package com.example.mantiskeyboard.ui.keyboard.components

import android.content.Context
import android.graphics.*
import java.util.Locale
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import androidx.core.graphics.createBitmap
import androidx.core.graphics.withSave
import androidx.core.graphics.withTranslation
import androidx.core.net.toUri
import androidx.datastore.preferences.core.Preferences
import androidx.dynamicanimation.animation.FloatPropertyCompat
import androidx.dynamicanimation.animation.SpringAnimation
import androidx.dynamicanimation.animation.SpringForce
import com.example.mantiskeyboard.data.repository.KeyboardTheme
import com.example.mantiskeyboard.data.repository.ThemeUtils
import com.example.mantiskeyboard.logic.managers.FeedbackManager
import com.example.mantiskeyboard.service.KeyboardService
import kotlinx.coroutines.*
import kotlin.math.*

class KeyboardView(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {

    private val engine = KeyboardLayoutEngine()
    private val renderer = KeyboardRenderer(context)

    init {
        isClickable = true
        isFocusable = false
        isFocusableInTouchMode = false
    }

    interface Listener {
        fun onChar(text: String)
        fun onCharWithPoint(text: String, x: Float, y: Float) {}
        fun onDelete()
        fun onEnter()
        fun onLongPressSelect(char: String)
        fun onSymbolPageChange(page: String)
        fun onKeyClick(key: KeyboardLayoutEngine.Key) {}
        fun onGesture(points: List<PointF>) {}
        fun onLiveGesture(points: List<PointF>) {}
        fun onLayoutUpdated(keyCenters: Map<Char, PointF>) {}
    }

    var listener: Listener? = null
    var shifted = false
        set(value) {
            if (field != value) {
                field = value
                if (!value) capsLock = false
                if ((layoutMode == KeyboardLayoutEngine.LayoutMode.FONTS) && (width > 0)) buildLayout(width.toFloat())
                invalidate()
            }
        }
    var capsLock = false
        set(value) {
            if (field != value) {
                field = value
                if (width > 0) buildLayout(width.toFloat())
                invalidate()
            }
        }

    var layoutMode = KeyboardLayoutEngine.LayoutMode.ALPHA
        set(value) {
            if (field != value) {
                field = value
                renderer.layoutMode = value
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
                engine.language = value
                renderer.language = value
                if (width > 0) buildLayout(width.toFloat())
                invalidate()
            }
        }

    var layoutType: String = "default"
        set(value) {
            if (field != value) {
                field = value
                engine.layoutType = value
                if (width > 0) buildLayout(width.toFloat())
                invalidate()
            }
        }

    var isSplitMode: Boolean = false
        set(value) {
            if (field != value) {
                field = value
                engine.isSplitMode = value
                if (width > 0) buildLayout(width.toFloat())
                invalidate()
            }
        }

    private var showKeyPopup: Boolean = true
    private var showLongPressIndicators: Boolean = true
        set(value) {
            field = value
            renderer.showLongPressIndicators = value
        }
    private var popupScale: Float = 1.0f
    private var keyScale: Float = 0.92f
        set(value) {
            field = value
            engine.keyScale = value
        }
    private var vibrationEnabled: Boolean = true
    private var soundEnabled: Boolean = true
    private var cachedLongPressTimeout: Long = 259L
    private var cachedKeyboardHeight: Int = 50
    var isResizeMode: Boolean = false

    private val viewScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var prefsJob: Job? = null

    var longPressAlternatives: Map<Char, List<String>> = emptyMap()
        set(value) {
            field = value; engine.longPressAlternatives = value
        }
    var symbolPages: Map<String, List<String>> = emptyMap()
        set(value) {
            field = value; engine.symbolPages = value
        }
    var fontPages: List<List<String>> = emptyList()
        set(value) {
            field = value; engine.fontPages = value
        }

    var keyboardTheme: KeyboardTheme? = null
        set(value) {
            if (field != value) {
                field = value
                renderer.clearCaches()
                if (value != null) applyTheme(value)
                else updateColors()
                invalidate()
            }
        }

    var drawBackground: Boolean = false
        set(value) {
            field = value; invalidate()
        }

    private var backgroundImage: Bitmap? = null
    private var backgroundBlurImage: Bitmap? = null
    private var currentBackgroundUri: String? = null
    private var currentBackgroundBlur: Float = -1f

    private val gesturePath = Path()
    private val gestureTrailPath = Path()
    private val gesturePoints = mutableListOf<PointF>()
    private var isGestureActive = false
    private var isDissolvingTrail = false
    private var dissolveStartTime = 0L
    private var isShiftGesture = false
    private var gestureStartX = 0f
    private var gestureStartY = 0f
    private var lastLiveGestureTime = 0L
    private val gestureThreshold get() = if (keys.isNotEmpty()) (keys.first().rx * 1.8f).coerceAtLeast(80f) else 80f

    var livePredictedWord: String = ""
        set(value) {
            if (field != value) {
                field = value
                if (isGestureActive) invalidate()
            }
        }

    private val pGesture = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        strokeWidth = 12f
    }

    private val pFloatingText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 34f
        typeface = Typeface.DEFAULT_BOLD
        textAlign = Paint.Align.CENTER
    }
    private val pFloatingBg = Paint(Paint.ANTI_ALIAS_FLAG)
    private val pFloatingShadow = Paint(Paint.ANTI_ALIAS_FLAG)
    private val pFloatingStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }
    private val floatingRect = RectF()

    private fun applyTheme(theme: KeyboardTheme) {
        renderer.applyTheme(theme, this)
        keyPreviewManager.updateTheme(renderer, theme)
        longPressPopupManager.updateTheme(renderer, theme)
        pGesture.color = renderer.colorShiftActive
        setBackgroundColor(Color.TRANSPARENT)
        if (theme.backgroundImageUri != currentBackgroundUri || theme.backgroundBlur != currentBackgroundBlur) {
            loadBackgroundImage(theme.backgroundImageUri, theme.backgroundBlur)
            currentBackgroundUri = theme.backgroundImageUri
            currentBackgroundBlur = theme.backgroundBlur
        }
        invalidate()
    }

    private var bgLoadJob: Job? = null

    private fun loadBackgroundImage(uriString: String?, blurRadius: Float) {
        bgLoadJob?.cancel()
        if (uriString == null) {
            backgroundImage?.recycle(); backgroundBlurImage?.recycle()
            backgroundImage = null; backgroundBlurImage = null
            invalidate()
            return
        }
        bgLoadJob = viewScope.launch(Dispatchers.IO) {
            try {
                val uri = uriString.toUri()
                context.contentResolver.openInputStream(uri)?.use { inputStream ->
                    val original = BitmapFactory.decodeStream(inputStream)
                    val blurred = if (blurRadius > 0 && original != null) ThemeUtils.blurBitmap(original, blurRadius) else null
                    withContext(Dispatchers.Main) {
                        backgroundImage?.recycle(); backgroundBlurImage?.recycle()
                        backgroundImage = original
                        backgroundBlurImage = blurred
                        invalidate()
                    }
                }
            } catch (e: Exception) {
                Log.e("HexKB", "Error loading background image", e)
            }
        }
    }

    fun resetState() {
        hidePopup(immediate = true)
        layoutMode = KeyboardLayoutEngine.LayoutMode.ALPHA
        symbolPage = "basic"
        if (width > 0) buildLayout(width.toFloat())
        invalidate()
    }

    private var keyBounceEnabled = true
    private var gestureTypingEnabled = true

    private fun applyPrefs(prefs: Preferences) {
        showKeyPopup = prefs[ThemeUtils.SHOW_KEY_POPUP] ?: true
        keyPreviewManager.isEnabled = showKeyPopup
        showLongPressIndicators = prefs[ThemeUtils.SHOW_LONG_PRESS_INDICATORS] ?: true
        keyBounceEnabled = prefs[ThemeUtils.KEY_BOUNCE_ANIMATION] ?: true
        gestureTypingEnabled = prefs[ThemeUtils.GESTURE_TYPING_ENABLED] ?: true
        popupScale = (prefs[ThemeUtils.POPUP_SCALE] ?: 95) / 100f
        vibrationEnabled = prefs[ThemeUtils.KEYBOARD_VIBRATION] ?: true
        soundEnabled = prefs[ThemeUtils.KEYBOARD_SOUND] ?: true
        cachedLongPressTimeout = (prefs[ThemeUtils.LONG_PRESS_DURATION] ?: 259).toLong()
        if (!isResizeMode) {
            cachedKeyboardHeight = prefs[ThemeUtils.KEYBOARD_HEIGHT] ?: 50
        }
        keyScale = (prefs[ThemeUtils.KEYBOARD_KEY_SIZE] ?: 90) / 100f
        updateColors()
        if (keys.isEmpty() && width > 0) buildLayout(width.toFloat())
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        isSoundEffectsEnabled = true
        isHapticFeedbackEnabled = true
        prefsJob?.cancel()
        prefsJob = viewScope.launch {
            ThemeUtils.getDataStore(context).data.collect { prefs ->
                applyPrefs(prefs)
                buildLayoutExternally()
            }
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        prefsJob?.cancel()
        prefsJob = null
        handler.removeCallbacksAndMessages(null)
        hidePopup(immediate = true)
        keysBitmap?.recycle()
        keysBitmap = null
    }

    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        super.onVisibilityChanged(changedView, visibility)
        if (visibility != VISIBLE) {
            hidePopup(immediate = true)
        }
    }

    override fun onWindowFocusChanged(hasWindowFocus: Boolean) {
        super.onWindowFocusChanged(hasWindowFocus)
        if (!hasWindowFocus) {
            hidePopup(immediate = true)
        }
    }

    private fun getHeightFactor(): Float {
        val isLandscape = resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
        val baseFactor = 0.7f + (cachedKeyboardHeight / 100f) * 0.6f
        return if (isLandscape) baseFactor * 0.78f else baseFactor
    }

    fun setCustomKeyboardHeight(height: Int) {
        if (cachedKeyboardHeight != height) {
            cachedKeyboardHeight = height
            if (width > 0) buildLayout(width.toFloat())
            requestLayout()
            invalidate()
        }
    }

    var sharedTypeface: Typeface = Typeface.DEFAULT
        set(value) {
            field = value
            renderer.sharedTypeface = value
            invalidate()
        }

    fun clearCaches() {
        renderer.clearCaches()
    }

    inner class UIKey(val key: KeyboardLayoutEngine.Key) {
        private val springAnim = SpringAnimation(this, object : FloatPropertyCompat<UIKey>("scale") {
            override fun getValue(k: UIKey): Float = k.key.currentScale
            override fun setValue(k: UIKey, value: Float) {
                k.key.currentScale = value
                this@KeyboardView.invalidate()
            }
        }).apply {
            spring = SpringForce().apply {
                dampingRatio = SpringForce.DAMPING_RATIO_MEDIUM_BOUNCY
                stiffness = SpringForce.STIFFNESS_HIGH
            }
        }

        fun animateToScale(target: Float) {
            if (keyBounceEnabled) {
                springAnim.animateToFinalPosition(target)
            } else {
                springAnim.cancel()
                key.currentScale = 1.0f
                this@KeyboardView.invalidate()
            }
        }
    }

    private val keys = mutableListOf<KeyboardLayoutEngine.Key>()
    val allKeys: List<KeyboardLayoutEngine.Key> = keys
    private val uiKeyMap = mutableMapOf<KeyboardLayoutEngine.Key, UIKey>()
    private val spaceKeys = mutableListOf<KeyboardLayoutEngine.Key>()
    private val shiftKeys = mutableListOf<KeyboardLayoutEngine.Key>()
    private val deleteKeys = mutableListOf<KeyboardLayoutEngine.Key>()
    private val enterKeys = mutableListOf<KeyboardLayoutEngine.Key>()

    private var unifiedShiftCenterX = 0f
    private var unifiedDeleteCenterX = 0f
    private var unifiedSpaceCenterX = 0f
    private var unifiedEnterCenterX = 0f

    private var spacePressed = false
    private var shiftPressed = false
    private var deletePressed = false
    private var enterPressed = false
    private val keyPreviewManager = KeyPreviewManager(context)
    private val longPressPopupManager = LongPressPopupManager(context)

    private var pressedKey: KeyboardLayoutEngine.Key? = null
    private var popupVisibleKey: KeyboardLayoutEngine.Key? = null
    private val activePointers = mutableMapOf<Int, KeyboardLayoutEngine.Key>()
    private val initialPointerKeys = mutableMapOf<Int, KeyboardLayoutEngine.Key>()
    private val pointerStartTime = mutableMapOf<Int, Long>()
    private val handler = Handler(Looper.getMainLooper())
    private var customPopupOnSelect: ((String) -> Unit)? = null
    private var isHybridLongPress = false
    private var hasDraggedSinceLongPress = false
    private var longPressTriggered = false
    private var longPressStarted = false
    private val longPressTimeout: Long get() = cachedLongPressTimeout
    private val longPressRunnable = Runnable {
        pressedKey?.let { hit ->
            if (hit.alternatives.isNotEmpty() && !isGestureActive) {
                longPressStarted = true
                isHybridLongPress = false
                hasDraggedSinceLongPress = false
                keyPreviewManager.dismissPreview(immediate = true)
                val loc = IntArray(2)
                getLocationInWindow(loc)
                val windowX = loc[0] + hit.cx
                val windowY = loc[1] + hit.cy
                longPressPopupManager.showLongPressPopup(this, hit, windowX, windowY, popupScale)
            }
        }
    }
    private var deleteRepeatRunnable: Runnable? = null
    private var lastShiftClickTime = 0L
    private val doubleClickThreshold = 280L
    private val keysToIgnoreDuringNGesture = setOf("l", "d", "g", "p", "c", "f", "u")

    private var isScrollingCursor = false
    private var lastScrollX = 0f
    private var lastScrollY = 0f

    override fun onMeasure(wMS: Int, hMS: Int) {
        val w = MeasureSpec.getSize(wMS)
        if (w <= 0) { setMeasuredDimension(0, 0); return }
        val f = getHeightFactor(); engine.heightFactor = f
        val isQwertyPureNumeric = layoutMode == KeyboardLayoutEngine.LayoutMode.PURE_NUMERIC && layoutType == "qwerty"
        val r = if (layoutMode == KeyboardLayoutEngine.LayoutMode.PURE_NUMERIC && !isQwertyPureNumeric) engine.getNumericR(w.toFloat()) else engine.getHexR(w.toFloat())
        val rows = if (layoutMode == KeyboardLayoutEngine.LayoutMode.PURE_NUMERIC && !isQwertyPureNumeric) 3f else 4f
        val dr = r * keyScale; val dry = dr * f
        val tp = r * 0.05f * f
        val h = (tp + 2 * dry + rows * r * 1.5f * f + r * 0.15f * f).toInt()
        setMeasuredDimension(w, h)
    }

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        super.onSizeChanged(w, h, ow, oh); buildLayout(w.toFloat())
    }

    private fun buildLayout(vw: Float) {
        if (vw <= 0) return
        keys.clear(); uiKeyMap.clear(); spaceKeys.clear(); shiftKeys.clear(); deleteKeys.clear(); enterKeys.clear()
        val newKeys = engine.buildLayout(vw, layoutMode, symbolPage, fontPage, shifted, capsLock)
        keys.addAll(newKeys)
        keys.forEach { uiKeyMap[it] = UIKey(it) }

        spaceKeys.addAll(keys.filter { it.type == KeyboardLayoutEngine.KeyType.SPACE })
        shiftKeys.addAll(keys.filter { it.type == KeyboardLayoutEngine.KeyType.SHIFT })
        deleteKeys.addAll(keys.filter { it.type == KeyboardLayoutEngine.KeyType.DELETE })
        enterKeys.addAll(keys.filter { it.type == KeyboardLayoutEngine.KeyType.ENTER })

        if (shiftKeys.size >= 2) {
            val half = shiftKeys.find { it.isHalfLeft }
            val full = shiftKeys.find { !it.isHalfLeft }
            if (half != null && full != null) {
                val s32 = 0.8660254f
                val r = full.rx * s32
                val rFull = if (keyScale > 0) (full.rx / keyScale) * s32 else r
                unifiedShiftCenterX = ((half.cx + (rFull - r)) + (full.cx + r)) / 2f
            }
        }
        if (deleteKeys.size >= 2) {
            val half = deleteKeys.find { it.isHalfRight }
            val full = deleteKeys.find { !it.isHalfRight }
            if (half != null && full != null) {
                val s32 = 0.8660254f
                val r = full.rx * s32
                val rFull = if (keyScale > 0) (full.rx / keyScale) * s32 else r
                unifiedDeleteCenterX = ((full.cx - r) + (half.cx - (rFull - r))) / 2f
            }
        }
        if (spaceKeys.isNotEmpty()) unifiedSpaceCenterX = (spaceKeys.minOf { it.cx } + spaceKeys.maxOf { it.cx }) / 2f
        if (enterKeys.isNotEmpty()) unifiedEnterCenterX = (enterKeys.minOf { it.cx } + enterKeys.maxOf { it.cx }) / 2f

        listener?.onLayoutUpdated(getKeyCenters())
    }

    fun getKeyCenters(): Map<Char, PointF> {
        val map = mutableMapOf<Char, PointF>()
        for (key in keys) {
            if (key.type == KeyboardLayoutEngine.KeyType.CHAR && key.value.isNotEmpty()) {
                val c = key.value.first().lowercaseChar()
                map[c] = PointF(key.cx, key.cy)
            }
        }
        return map
    }

    fun buildLayoutExternally() {
        if (width > 0) buildLayout(width.toFloat())
        requestLayout()
        invalidate()
    }

    private var keysBitmap: Bitmap? = null
    private val keysCanvas = Canvas()
    private var needsRedraw = true

    override fun invalidate() {
        needsRedraw = true; super.invalidate()
    }

    private fun updateColors() {
        keyboardTheme?.let { applyTheme(it) }
        keyPreviewManager.updateTheme(renderer, keyboardTheme)
        longPressPopupManager.updateTheme(renderer, keyboardTheme)
    }

    private fun getGroupCenter(key: KeyboardLayoutEngine.Key): Float? {
        return when (key.type) {
            KeyboardLayoutEngine.KeyType.SPACE -> if (spaceKeys.size >= 2) unifiedSpaceCenterX else null
            KeyboardLayoutEngine.KeyType.SHIFT -> if (shiftKeys.size >= 2) unifiedShiftCenterX else null
            KeyboardLayoutEngine.KeyType.DELETE -> if (deleteKeys.size >= 2) unifiedDeleteCenterX else null
            KeyboardLayoutEngine.KeyType.ENTER -> if (enterKeys.size >= 2) unifiedEnterCenterX else null
            else -> null
        }
    }

    private fun drawKeysToCache() {
        val w = width; val h = height
        if (w <= 0 || h <= 0) return
        if (keysBitmap == null || keysBitmap?.width != w || keysBitmap?.height != h) {
            keysBitmap?.recycle()
            keysBitmap = createBitmap(w, h)
            keysCanvas.setBitmap(keysBitmap)
        }
        keysCanvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
        for (key in keys) {
            if (!key.isPressed && key.currentScale == 1.0f) {
                renderer.drawSingleKey(keysCanvas, key, keyboardTheme, false, shifted, capsLock, 1.0f, getGroupCenter(key))
            }
        }
        needsRedraw = false
    }

    override fun onDraw(canvas: Canvas) {
        val theme = keyboardTheme ?: return
        if (drawBackground) {
            renderer.drawBackground(canvas, theme, backgroundImage, backgroundBlurImage, width, height)
        }
        if (needsRedraw || keysBitmap == null) drawKeysToCache()
        keysBitmap?.let { canvas.drawBitmap(it, 0f, 0f, null) }
        for (key in keys) {
            if (key.isPressed || key.currentScale != 1.0f) {
                drawKey(canvas, key)
            }
        }

        if (isGestureActive && gesturePoints.size >= 2) {
            drawSmoothGestureTrail(canvas, 1f)
            if (livePredictedWord.isNotEmpty()) {
                drawFloatingLiveBubble(canvas)
            }
        } else if (isDissolvingTrail && gesturePoints.size >= 2) {
            val elapsed = System.currentTimeMillis() - dissolveStartTime
            val fadeProgress = (1f - elapsed / 140f).coerceIn(0f, 1f)
            if (fadeProgress > 0f) {
                drawSmoothGestureTrail(canvas, fadeProgress)
                postInvalidateOnAnimation()
            } else {
                isDissolvingTrail = false
                gesturePoints.clear()
            }
        }
    }

    private fun drawSmoothGestureTrail(canvas: Canvas, alphaScale: Float = 1f) {
        val count = gesturePoints.size
        if (count < 2) return
        val activeColor = renderer.colorShiftActive
        val alphaBase = (Color.alpha(activeColor) * alphaScale).toInt().coerceIn(0, 255)
        val red = Color.red(activeColor)
        val green = Color.green(activeColor)
        val blue = Color.blue(activeColor)

        for (i in 0 until count - 1) {
            val p1 = gesturePoints[i]
            val p2 = gesturePoints[i + 1]

            val progress = (i + 1).toFloat() / count.toFloat()
            val segAlpha = (alphaBase * (0.25f + 0.75f * progress * progress)).toInt().coerceIn(0, 255)
            val segWidth = 4f + 14f * progress

            pGesture.color = Color.argb(segAlpha, red, green, blue)
            pGesture.strokeWidth = segWidth

            gestureTrailPath.reset()
            if (i == 0) {
                gestureTrailPath.moveTo(p1.x, p1.y)
                gestureTrailPath.lineTo(p2.x, p2.y)
            } else {
                val pPrev = gesturePoints[i - 1]
                val mid1X = (pPrev.x + p1.x) / 2f
                val mid1Y = (pPrev.y + p1.y) / 2f
                val mid2X = (p1.x + p2.x) / 2f
                val mid2Y = (p1.y + p2.y) / 2f
                gestureTrailPath.moveTo(mid1X, mid1Y)
                gestureTrailPath.quadTo(p1.x, p1.y, mid2X, mid2Y)
            }
            canvas.drawPath(gestureTrailPath, pGesture)
        }
    }

    private fun drawFloatingLiveBubble(canvas: Canvas) {
        val tip = gesturePoints.lastOrNull() ?: return
        val rawText = livePredictedWord.trim()
        if (rawText.isEmpty()) return

        val text = if (isShiftGesture || shifted) {
            rawText.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
        } else rawText

        pFloatingText.color = if (renderer.colorPopupText != 0) renderer.colorPopupText else Color.WHITE
        pFloatingBg.color = renderer.colorPopupBg
        pFloatingShadow.color = renderer.colorPopupShadow
        pFloatingStroke.color = renderer.colorShiftActive

        val textWidth = pFloatingText.measureText(text)
        val paddingH = 32f
        val paddingV = 16f
        val bubbleW = textWidth + paddingH * 2f
        val bubbleH = pFloatingText.textSize + paddingV * 2f

        val cx = tip.x.coerceIn(bubbleW / 2f + 16f, (width.toFloat() - bubbleW / 2f - 16f).coerceAtLeast(bubbleW / 2f + 16f))
        val cy = (tip.y - 110f).coerceAtLeast(bubbleH / 2f + 10f)

        floatingRect.set(cx - bubbleW / 2f, cy - bubbleH / 2f, cx + bubbleW / 2f, cy + bubbleH / 2f)

        canvas.withTranslation(0f, 4f) {
            drawRoundRect(floatingRect, bubbleH / 2f, bubbleH / 2f, pFloatingShadow)
        }

        canvas.drawRoundRect(floatingRect, bubbleH / 2f, bubbleH / 2f, pFloatingBg)
        canvas.drawRoundRect(floatingRect, bubbleH / 2f, bubbleH / 2f, pFloatingStroke)

        val fontMetrics = pFloatingText.fontMetrics
        val textY = cy - (fontMetrics.ascent + fontMetrics.descent) / 2f
        canvas.drawText(text, cx, textY, pFloatingText)
    }

    private fun drawKey(canvas: Canvas, key: KeyboardLayoutEngine.Key) {
        val isSpace = key.type == KeyboardLayoutEngine.KeyType.SPACE
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
        val drawCx = getGroupCenter(key)
        val scaleCx = drawCx ?: key.cx
        canvas.withSave {
            scale(appliedScale, appliedScale, scaleCx, key.cy)
            renderer.drawSingleKey(canvas, key, keyboardTheme, pressed, shifted, capsLock, appliedScale, drawCx)
        }
    }

    fun showKeyPreview(key: KeyboardLayoutEngine.Key, pointerId: Int = 0) {
        if (!showKeyPopup || key.type != KeyboardLayoutEngine.KeyType.CHAR) return
        val loc = IntArray(2)
        getLocationInWindow(loc)
        val windowX = loc[0] + key.cx
        val windowY = loc[1] + key.cy
        val label = if (shifted && key.value.length == 1 && key.value[0].isLetter()) key.display.uppercase() else key.display
        keyPreviewManager.showPreview(this, key, windowX, windowY, label, key.isRectangular || layoutType == "qwerty", popupScale, pointerId)
    }

    fun showPopupForAlternatives(
        screenX: Float,
        screenY: Float,
        rx: Float = 40f,
        ry: Float = 40f,
        mainSymbol: String,
        alternatives: List<String>,
        onSelect: (String) -> Unit
    ) {
        if (windowToken == null || alternatives.isEmpty()) return
        val dummyKey = KeyboardLayoutEngine.Key(
            display = mainSymbol,
            type = KeyboardLayoutEngine.KeyType.CHAR,
            value = mainSymbol,
            alternatives = alternatives,
            cx = screenX,
            cy = screenY,
            rx = rx,
            ry = ry,
            isRectangular = true
        )
        customPopupOnSelect = onSelect
        isHybridLongPress = true
        longPressStarted = true
        hasDraggedSinceLongPress = false
        longPressPopupManager.showLongPressPopup(this, dummyKey, screenX, screenY, popupScale)
    }

    fun onLongPressDrag(screenX: Float, screenY: Float) {
        if (!longPressPopupManager.isShowing) return
        val distFromStart = hypot(screenX - gestureStartX, screenY - gestureStartY)
        if (distFromStart > 16f) hasDraggedSinceLongPress = true
        longPressPopupManager.onDrag(screenX, screenY)
    }

    fun onLongPressRelease() {
        if (!longPressPopupManager.isShowing) return
        val selectedChar = longPressPopupManager.onRelease()
        if (selectedChar != null) {
            triggerVibration()
            if (customPopupOnSelect != null) {
                val cb = customPopupOnSelect
                customPopupOnSelect = null
                cb?.invoke(selectedChar)
            } else {
                pressedKey?.let { key ->
                    when (key.type) {
                        KeyboardLayoutEngine.KeyType.LANGUAGE -> if (selectedChar == "🌐") (context as? KeyboardService)?.showLanguagePicker() else (context as? KeyboardService)?.switchToNextLanguage()
                        KeyboardLayoutEngine.KeyType.TOGGLE -> switchLayoutByValue(selectedChar)
                        KeyboardLayoutEngine.KeyType.SYMBOL_PAGE -> { if (selectedChar.toIntOrNull() != null) switchSymbolPageByValue(selectedChar) else fireKey(key) }
                        KeyboardLayoutEngine.KeyType.FONT_PAGE -> { fontPage = (selectedChar.toIntOrNull() ?: 1) - 1; buildLayout(width.toFloat()); invalidate() }
                        else -> listener?.onLongPressSelect(selectedChar)
                    }
                } ?: listener?.onLongPressSelect(selectedChar)
            }
        }
        hidePopup(immediate = true)
        longPressStarted = false
    }

    fun hidePopup(immediate: Boolean = false) {
        handler.removeCallbacks(longPressRunnable)
        keyPreviewManager.dismissPreview(immediate)
        longPressPopupManager.dismissPopup()
        customPopupOnSelect = null
    }

    private var currentImeAction: Int = EditorInfo.IME_ACTION_NONE
    var isMultiLine: Boolean = false; set(value) { field = value; renderer.isMultiLine = value; invalidate() }
    fun setImeAction(action: Int) { currentImeAction = action; renderer.currentImeAction = action; invalidate() }

    fun triggerVibration(type: FeedbackManager.HapticType = FeedbackManager.HapticType.KEY_CLICK) {
        FeedbackManager.triggerVibration(context, type)
    }

    fun triggerSound() {
        FeedbackManager.triggerSound(context)
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        val action = e.actionMasked; val pointerIndex = e.actionIndex; val pointerId = e.getPointerId(pointerIndex)
        val x = e.getX(pointerIndex); val y = e.getY(pointerIndex)

        when (action) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                if (longPressPopupManager.isShowing && !longPressStarted) {
                    val loc = IntArray(2)
                    getLocationInWindow(loc)
                    longPressPopupManager.onDrag(loc[0] + x, loc[1] + y)
                    val sel = longPressPopupManager.onRelease()
                    if (sel != null) {
                        triggerVibration()
                        if (customPopupOnSelect != null) {
                            customPopupOnSelect?.invoke(sel)
                            customPopupOnSelect = null
                        } else {
                            listener?.onLongPressSelect(sel)
                        }
                    }
                    hidePopup(immediate = true)
                    return true
                }
                hasDraggedSinceLongPress = false
                if (keys.isEmpty() && width > 0) buildLayout(width.toFloat())
                if (e.pointerCount > 1 || activePointers.size >= 1) {
                    isGestureActive = false
                    gesturePath.reset()
                    gesturePoints.clear()
                }
                val hit = hitTest(x, y)
                if (hit != null) {
                    activePointers[pointerId] = hit
                    initialPointerKeys[pointerId] = hit
                    hit.isPressed = true
                    pointerStartTime[pointerId] = System.currentTimeMillis()
                    if (hit.type != KeyboardLayoutEngine.KeyType.DELETE) { triggerVibration(); triggerSound() }
                    if (pointerId == e.getPointerId(0)) {
                        val currentPressed = pressedKey
                        val isSameGroup = (currentPressed in spaceKeys && hit in spaceKeys) ||
                                          (currentPressed in shiftKeys && hit in shiftKeys) ||
                                          (currentPressed in deleteKeys && hit in deleteKeys)
                        if (hit != currentPressed && !isSameGroup) {
                            animateKeyToScale(currentPressed, 1.0f); cancelKeyLongPress(); cancelDeleteRepeat()
                        }
                        pressedKey = hit; animateKeyToScale(hit, 0.85f)
                        lastScrollX = x; lastScrollY = y; gestureStartX = x; gestureStartY = y; gesturePoints.clear(); gesturePath.reset(); gesturePoints.add(PointF(x, y)); gesturePath.moveTo(x, y)
                        isGestureActive = false; isScrollingCursor = false; longPressTriggered = false; longPressStarted = false
                        if (showKeyPopup && hit.type == KeyboardLayoutEngine.KeyType.CHAR) { showKeyPreview(hit, pointerId) }
                        if (hit.alternatives.isNotEmpty()) handler.postDelayed(longPressRunnable, longPressTimeout)
                    } else {
                        animateKeyToScale(hit, 0.85f)
                        if (showKeyPopup && hit.type == KeyboardLayoutEngine.KeyType.CHAR) { showKeyPreview(hit, pointerId) }
                    }
                    if (hit.type == KeyboardLayoutEngine.KeyType.DELETE || hit.isHalfRight) { listener?.onDelete(); startDeleteRepeat() }
                    invalidate()
                }
            }
            MotionEvent.ACTION_MOVE -> {
                val isSingleTouch = e.pointerCount == 1 && activePointers.size <= 1
                if (longPressPopupManager.isShowing && pointerId == e.getPointerId(0)) {
                    val loc = IntArray(2)
                    getLocationInWindow(loc)
                    val distFromStart = hypot(x - gestureStartX, y - gestureStartY)
                    if (distFromStart > 16f) hasDraggedSinceLongPress = true
                    longPressPopupManager.onDrag(loc[0] + x, loc[1] + y)
                }
                else if (pressedKey?.type == KeyboardLayoutEngine.KeyType.SPACE && pointerId == e.getPointerId(0)) {
                    val deltaX = x - lastScrollX
                    if (abs(deltaX) > 20) {
                        isScrollingCursor = true; (context as? KeyboardService)?.moveCursor(if (deltaX > 0) 1 else -1)
                        lastScrollX = x; invalidate()
                    }
                } else if (pointerId == e.getPointerId(0) && pressedKey?.type == KeyboardLayoutEngine.KeyType.CHAR && !longPressStarted && !isScrollingCursor && !longPressTriggered) {
                    val deltaY = y - lastScrollY; val deltaX = x - gestureStartX; val totalDist = sqrt(deltaX * deltaX + (y - gestureStartY) * (y - gestureStartY))
                    val gestureMinDist = (pressedKey?.rx ?: 50f) * 2.2f
                    if (pressedKey?.value == "n") {
                        if (deltaY < -80f && abs(deltaY) > 2.0f * abs(deltaX)) {
                            longPressTriggered = true; isGestureActive = false; gesturePath.reset(); gesturePoints.clear(); cancelKeyLongPress(); hidePopup(immediate = true)
                            triggerVibration(FeedbackManager.HapticType.LONG_PRESS); triggerSound()
                            listener?.onChar(if (shifted || capsLock) "Ñ" else "ñ"); if (shifted && !capsLock) shifted = false
                            invalidate()
                        } else if (!isGestureActive && gestureTypingEnabled && isSingleTouch && totalDist > gestureMinDist) {
                            isGestureActive = true; cancelKeyLongPress(); hidePopup(immediate = true)
                        }
                    } else if (!isGestureActive && gestureTypingEnabled && isSingleTouch && totalDist > gestureMinDist) {
                        isGestureActive = true; cancelKeyLongPress(); hidePopup(immediate = true)
                    }
                    if (isGestureActive && isSingleTouch) {
                        val lastP = gesturePoints.lastOrNull()
                        if (lastP == null || hypot(x - lastP.x, y - lastP.y) > 8f) {
                            gesturePoints.add(PointF(x, y))
                            gesturePath.lineTo(x, y)
                        }
                        val now = System.currentTimeMillis()
                        if (now - lastLiveGestureTime > 60) {
                            lastLiveGestureTime = now
                            if (gesturePoints.size >= 3) {
                                listener?.onLiveGesture(ArrayList(gesturePoints))
                            }
                        }
                        invalidate()
                    }
                }
                for (i in 0 until e.pointerCount) {
                    val pid = e.getPointerId(i)
                    if (pid == e.getPointerId(0) && (longPressStarted || isScrollingCursor || longPressTriggered || isGestureActive || pressedKey?.value == "n")) continue
                    val px = e.getX(i); val py = e.getY(i)
                    val oldHit = activePointers[pid]
                    val initialHit = initialPointerKeys[pid]
                    val distFromStart = hypot(px - gestureStartX, py - gestureStartY)
                    val minMoveThreshold = (initialHit?.rx ?: 40f) * 0.85f
                    val newHit = if (distFromStart > minMoveThreshold) hitTest(px, py) else (oldHit ?: initialHit)
                    if (newHit != oldHit && newHit != null) {
                        oldHit?.isPressed = false
                        if (!(oldHit in spaceKeys && newHit in spaceKeys) && !(oldHit in shiftKeys && newHit in shiftKeys) && !(oldHit in deleteKeys && newHit in deleteKeys)) {
                            animateKeyToScale(oldHit, 1.0f); animateKeyToScale(newHit, 0.85f)
                        }
                        activePointers[pid] = newHit; newHit.isPressed = true
                        updateGroupPressStates()
                        if (pid == e.getPointerId(0)) {
                            if (pressedKey?.type == KeyboardLayoutEngine.KeyType.DELETE || pressedKey?.isHalfRight == true) cancelDeleteRepeat()
                            pressedKey = newHit
                            if (!(oldHit in spaceKeys && newHit in spaceKeys)) {
                                cancelKeyLongPress()
                                if (showKeyPopup && newHit.type == KeyboardLayoutEngine.KeyType.CHAR) {
                                    showKeyPreview(newHit, pid)
                                } else {
                                    keyPreviewManager.dismissPreviewForPointer(pid, immediate = false)
                                }
                                if (newHit.alternatives.isNotEmpty() && !longPressStarted) handler.postDelayed(longPressRunnable, longPressTimeout)
                            }
                        } else {
                            if (showKeyPopup && newHit.type == KeyboardLayoutEngine.KeyType.CHAR) {
                                showKeyPreview(newHit, pid)
                            }
                        }
                        invalidate()
                    }
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                val hit = activePointers[pointerId] ?: initialPointerKeys[pointerId]
                if (hit != null) {
                    hit.isPressed = false; animateKeyToScale(hit, 1.0f)
                    if (pointerId == e.getPointerId(0)) {
                        cancelKeyLongPress(); cancelDeleteRepeat()
                        if (isGestureActive) {
                            val firstP = gesturePoints.firstOrNull()
                            val lastP = gesturePoints.lastOrNull()
                            val netDist = if (firstP != null && lastP != null) hypot(lastP.x - firstP.x, lastP.y - firstP.y) else 0f
                            if (gesturePoints.size >= 5 && netDist >= gestureThreshold * 1.2f) {
                                listener?.onGesture(ArrayList(gesturePoints))
                            } else {
                                fireKey(hit)
                            }
                            isGestureActive = false; gesturePath.reset(); gesturePoints.clear(); invalidate()
                        } else if (longPressPopupManager.isShowing) {
                            onLongPressRelease()
                        } else {
                            if (!longPressTriggered) { fireKey(hit); performClick() }
                            keyPreviewManager.dismissPreviewForPointer(pointerId, immediate = false)
                        }
                        pressedKey = null; longPressTriggered = false; longPressStarted = false; isScrollingCursor = false
                    } else {
                        fireKey(hit)
                        keyPreviewManager.dismissPreviewForPointer(pointerId, immediate = false)
                    }
                    activePointers.remove(pointerId); initialPointerKeys.remove(pointerId); updateGroupPressStates(); invalidate()
                }
            }
            MotionEvent.ACTION_CANCEL -> {
                keys.forEach { it.isPressed = false; animateKeyToScale(it, 1.0f) }
                activePointers.clear(); initialPointerKeys.clear(); pointerStartTime.clear(); spacePressed = false; shiftPressed = false; deletePressed = false; enterPressed = false
                isGestureActive = false; gesturePath.reset(); gesturePoints.clear(); cancelKeyLongPress(); cancelDeleteRepeat()
                hidePopup(immediate = true)
                keyPreviewManager.dismissAllPreviews(immediate = true)
                pressedKey = null; longPressTriggered = false; longPressStarted = false; isScrollingCursor = false; invalidate()
            }
        }
        return true
    }

    private fun animateKeyToScale(key: KeyboardLayoutEngine.Key?, target: Float) {
        if (key == null) return
        when (key) {
            in spaceKeys -> spaceKeys.forEach { uiKeyMap[it]?.animateToScale(target) }
            in shiftKeys -> shiftKeys.forEach { uiKeyMap[it]?.animateToScale(target) }
            in deleteKeys -> deleteKeys.forEach { uiKeyMap[it]?.animateToScale(target) }
            in enterKeys -> enterKeys.forEach { uiKeyMap[it]?.animateToScale(target) }
            else -> uiKeyMap[key]?.animateToScale(target)
        }
    }

    private fun updateGroupPressStates() {
        spacePressed = activePointers.values.any { it in spaceKeys }
        shiftPressed = activePointers.values.any { it in shiftKeys }
        deletePressed = activePointers.values.any { it in deleteKeys }
        enterPressed = activePointers.values.any { it in enterKeys }
    }

    private fun switchSymbolPageByValue(value: String) {
        val pages = symbolPages.keys.toList()
        val index = value.toIntOrNull()?.minus(1) ?: return
        if (index in pages.indices) {
            symbolPage = pages[index]; listener?.onSymbolPageChange(symbolPage)
            buildLayout(width.toFloat()); invalidate()
        }
    }

    private fun switchLayoutByValue(value: String) {
        layoutMode = when (value) {
            "ABC" -> KeyboardLayoutEngine.LayoutMode.ALPHA
            "?123" -> KeyboardLayoutEngine.LayoutMode.NUMERIC
            "123" -> KeyboardLayoutEngine.LayoutMode.PURE_NUMERIC
            "#+=" -> KeyboardLayoutEngine.LayoutMode.SYMBOLS
            "𝔉" -> KeyboardLayoutEngine.LayoutMode.FONTS
            else -> return
        }
    }



    private fun hitTest(x: Float, y: Float): KeyboardLayoutEngine.Key? {
        if (keys.isEmpty()) return null
        val isNGestureActive = longPressTriggered && pressedKey?.value == "n"
        return engine.findKeyAt(x, y, if (isNGestureActive) keys.filter { it.value !in keysToIgnoreDuringNGesture } else keys)
    }

    private fun fireKey(key: KeyboardLayoutEngine.Key, touchX: Float = key.cx, touchY: Float = key.cy) {
        listener?.onKeyClick(key)
        when (key.type) {
            KeyboardLayoutEngine.KeyType.LANGUAGE -> (context as? KeyboardService)?.switchToNextLanguage()
            KeyboardLayoutEngine.KeyType.SHIFT -> {
                val n = System.currentTimeMillis()
                if (n - lastShiftClickTime <= doubleClickThreshold) { capsLock = true; shifted = true }
                else { if (capsLock) { capsLock = false; shifted = false } else shifted = !shifted }
                lastShiftClickTime = n; invalidate()
            }
            KeyboardLayoutEngine.KeyType.ENTER -> listener?.onEnter()
            KeyboardLayoutEngine.KeyType.SPACE -> listener?.onChar(" ")
            KeyboardLayoutEngine.KeyType.TOGGLE -> {
                layoutMode = when (layoutMode) {
                    KeyboardLayoutEngine.LayoutMode.ALPHA -> KeyboardLayoutEngine.LayoutMode.NUMERIC
                    KeyboardLayoutEngine.LayoutMode.NUMERIC -> KeyboardLayoutEngine.LayoutMode.ALPHA
                    KeyboardLayoutEngine.LayoutMode.SYMBOLS -> KeyboardLayoutEngine.LayoutMode.ALPHA
                    KeyboardLayoutEngine.LayoutMode.PURE_NUMERIC -> KeyboardLayoutEngine.LayoutMode.ALPHA
                    KeyboardLayoutEngine.LayoutMode.FONTS -> KeyboardLayoutEngine.LayoutMode.ALPHA
                }
                if (layoutMode == KeyboardLayoutEngine.LayoutMode.ALPHA) (context as? KeyboardService)?.updateShiftState()
            }
            KeyboardLayoutEngine.KeyType.SYMBOL_PAGE -> {
                val pages = symbolPages.keys.toList()
                if (pages.isNotEmpty()) {
                    val i = pages.indexOf(symbolPage); symbolPage = pages[(i + 1) % pages.size]
                    listener?.onSymbolPageChange(symbolPage); buildLayout(width.toFloat()); invalidate()
                }
            }
            KeyboardLayoutEngine.KeyType.FONT_PAGE -> { fontPage = (fontPage + 1) % 10; buildLayout(width.toFloat()); invalidate() }
            KeyboardLayoutEngine.KeyType.CHAR -> {
                val o = if (shifted && key.value.length == 1 && key.value[0].isLetter()) key.value.uppercase() else key.value
                listener?.onChar(o)
                listener?.onCharWithPoint(o, touchX, touchY)
                if (shifted && !capsLock) { shifted = false; invalidate() }
            }
            else -> {}
        }
    }

    private fun cancelKeyLongPress() { handler.removeCallbacks(longPressRunnable) }
    private fun startDeleteRepeat() {
        cancelDeleteRepeat()
        val runnable = object : Runnable {
            var count = 0
            override fun run() {
                count++; listener?.onDelete()
                handler.postDelayed(this, if (count < 5) 80L else if (count < 15) 60L else 45L)
            }
        }
        deleteRepeatRunnable = runnable
        handler.postDelayed(runnable, 400L)
    }
    private fun cancelDeleteRepeat() { deleteRepeatRunnable?.let { handler.removeCallbacks(it) }; deleteRepeatRunnable = null }
    override fun performClick(): Boolean { super.performClick(); return true }
}
