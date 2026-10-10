package com.example.hexkeyboard.ui.keyboard.components

import android.content.Context
import android.graphics.*
import java.util.Locale
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.PopupWindow
import androidx.core.graphics.createBitmap
import androidx.core.graphics.toColorInt
import androidx.core.graphics.withSave
import androidx.core.graphics.withTranslation
import androidx.core.net.toUri
import androidx.datastore.preferences.core.Preferences
import androidx.dynamicanimation.animation.FloatPropertyCompat
import androidx.dynamicanimation.animation.SpringAnimation
import androidx.dynamicanimation.animation.SpringForce
import com.example.hexkeyboard.data.repository.KeyboardTheme
import com.example.hexkeyboard.data.repository.ThemeUtils
import com.example.hexkeyboard.logic.managers.FeedbackManager
import com.example.hexkeyboard.service.HexKeyboardService
import kotlinx.coroutines.*
import kotlin.math.*

class HexKeyboardView(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {

    private val engine = HexLayoutEngine()
    private val renderer = HexRenderer(context)

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
        fun onKeyClick(key: HexLayoutEngine.Key) {}
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
                if ((layoutMode == HexLayoutEngine.LayoutMode.FONTS) && (width > 0)) buildLayout(width.toFloat())
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

    var layoutMode = HexLayoutEngine.LayoutMode.ALPHA
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
    private val gestureThreshold get() = if (keys.isNotEmpty()) (keys.first().rx * 0.45f).coerceAtLeast(40f) else 50f

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
        layoutMode = HexLayoutEngine.LayoutMode.ALPHA
        symbolPage = "basic"
        if (width > 0) buildLayout(width.toFloat())
        invalidate()
    }

    private var keyBounceEnabled = true
    private var gestureTypingEnabled = true

    private fun applyPrefs(prefs: Preferences) {
        showKeyPopup = prefs[ThemeUtils.SHOW_KEY_POPUP] ?: true
        showLongPressIndicators = prefs[ThemeUtils.SHOW_LONG_PRESS_INDICATORS] ?: true
        keyBounceEnabled = prefs[ThemeUtils.KEY_BOUNCE_ANIMATION] ?: true
        gestureTypingEnabled = prefs[ThemeUtils.GESTURE_TYPING_ENABLED] ?: true
        popupScale = (prefs[ThemeUtils.POPUP_SCALE] ?: 95) / 100f
        vibrationEnabled = prefs[ThemeUtils.KEYBOARD_VIBRATION] ?: true
        soundEnabled = prefs[ThemeUtils.KEYBOARD_SOUND] ?: true
        cachedLongPressTimeout = (prefs[ThemeUtils.LONG_PRESS_DURATION] ?: 259).toLong()
        cachedKeyboardHeight = prefs[ThemeUtils.KEYBOARD_HEIGHT] ?: 50
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
        popupWindow = null
        popupContentView = null
    }

    private fun getHeightFactor(): Float {
        return 0.7f + (cachedKeyboardHeight / 100f) * 0.6f
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

    inner class UIKey(val key: HexLayoutEngine.Key) {
        private val springAnim = SpringAnimation(this, object : FloatPropertyCompat<UIKey>("scale") {
            override fun getValue(k: UIKey): Float = k.key.currentScale
            override fun setValue(k: UIKey, value: Float) {
                k.key.currentScale = value
                this@HexKeyboardView.invalidate()
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
                this@HexKeyboardView.invalidate()
            }
        }
    }

    private val keys = mutableListOf<HexLayoutEngine.Key>()
    val allKeys: List<HexLayoutEngine.Key> = keys
    private val uiKeyMap = mutableMapOf<HexLayoutEngine.Key, UIKey>()
    private val spaceKeys = mutableListOf<HexLayoutEngine.Key>()
    private val shiftKeys = mutableListOf<HexLayoutEngine.Key>()
    private val deleteKeys = mutableListOf<HexLayoutEngine.Key>()
    private val enterKeys = mutableListOf<HexLayoutEngine.Key>()

    private var unifiedShiftCenterX = 0f
    private var unifiedDeleteCenterX = 0f
    private var unifiedSpaceCenterX = 0f
    private var unifiedEnterCenterX = 0f

    private var spacePressed = false
    private var shiftPressed = false
    private var deletePressed = false
    private var enterPressed = false
    private var pressedKey: HexLayoutEngine.Key? = null
    private var popupVisibleKey: HexLayoutEngine.Key? = null
    private var popupSelectedIndex = -1
    private val activePointers = mutableMapOf<Int, HexLayoutEngine.Key>()
    private val initialPointerKeys = mutableMapOf<Int, HexLayoutEngine.Key>()
    private val pointerStartTime = mutableMapOf<Int, Long>()
    private val handler = Handler(Looper.getMainLooper())
    private var longPressTriggered = false
    private var longPressStarted = false
    private val longPressTimeout: Long get() = cachedLongPressTimeout
    private val longPressRunnable = Runnable {
        pressedKey?.let { if (it.alternatives.isNotEmpty() && !isGestureActive) { longPressStarted = true; popupVisibleKey = it; popupSelectedIndex = 0; showPopup(it, isLongPress = true) } }
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
        val r = if (layoutMode == HexLayoutEngine.LayoutMode.PURE_NUMERIC) engine.getNumericR(w.toFloat()) else engine.getHexR(w.toFloat())
        val rows = if (layoutMode == HexLayoutEngine.LayoutMode.PURE_NUMERIC) 3f else 4f
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

        spaceKeys.addAll(keys.filter { it.type == HexLayoutEngine.KeyType.SPACE })
        shiftKeys.addAll(keys.filter { it.type == HexLayoutEngine.KeyType.SHIFT })
        deleteKeys.addAll(keys.filter { it.type == HexLayoutEngine.KeyType.DELETE })
        enterKeys.addAll(keys.filter { it.type == HexLayoutEngine.KeyType.ENTER })

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
            if (key.type == HexLayoutEngine.KeyType.CHAR && key.value.isNotEmpty()) {
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
    }

    private fun getGroupCenter(key: HexLayoutEngine.Key): Float? {
        return when (key.type) {
            HexLayoutEngine.KeyType.SPACE -> if (spaceKeys.size >= 2) unifiedSpaceCenterX else null
            HexLayoutEngine.KeyType.SHIFT -> if (shiftKeys.size >= 2) unifiedShiftCenterX else null
            HexLayoutEngine.KeyType.DELETE -> if (deleteKeys.size >= 2) unifiedDeleteCenterX else null
            HexLayoutEngine.KeyType.ENTER -> if (enterKeys.size >= 2) unifiedEnterCenterX else null
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

    private fun drawKey(canvas: Canvas, key: HexLayoutEngine.Key) {
        val isSpace = key.type == HexLayoutEngine.KeyType.SPACE
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

    data class PopupCell(
        val label: String,
        val bounds: RectF,
        val centerX: Float,
        val centerY: Float
    )

    data class PopupLayoutInfo(
        val key: HexLayoutEngine.Key,
        val isLongPress: Boolean,
        val cells: List<PopupCell>,
        val width: Int,
        val height: Int,
        val popupX: Int,
        val popupY: Int
    )

    private var currentPopupLayout: PopupLayoutInfo? = null

    private var popupWindow: PopupWindow? = null
    private var popupContentView: KeyPopupView? = null

    inner class KeyPopupView(context: Context) : View(context) {
        var key: HexLayoutEngine.Key? = null
        var isLongPress: Boolean = false
        var selectedIndex: Int = -1
        private val localPopupPath = Path()
        private val localPopupHexPath = Path()

        override fun onDraw(canvas: Canvas) {
            val layout = currentPopupLayout ?: return
            val px = width / 2f
            val py = height / 2f

            if (!layout.isLongPress) {
                val cell = layout.cells.firstOrNull() ?: return
                val prx = width * 0.44f
                val pry = height * 0.44f
                val textSize = min(prx, pry) * 0.7f

                renderer.updateHexPath(localPopupHexPath, px, py, prx, pry)
                canvas.withTranslation(0f, 6f) {
                    renderer.pShadow.color = renderer.colorPopupShadow
                    drawPath(localPopupHexPath, renderer.pShadow)
                }
                renderer.pPopupBg.color = renderer.colorPopupBg
                canvas.drawPath(localPopupHexPath, renderer.pPopupBg)
                if (renderer.strokeWidth > 0) {
                    renderer.pStroke.color = renderer.colorStroke
                    renderer.pStroke.strokeWidth = 2f
                    canvas.drawPath(localPopupHexPath, renderer.pStroke)
                }
                renderer.pPopupText.textSize = textSize
                val ty = py - (renderer.pPopupText.ascent() + renderer.pPopupText.descent()) / 2f
                renderer.pPopupText.color = renderer.colorPopupText
                canvas.drawText(cell.label, px, ty, renderer.pPopupText)
            } else {
                val p = min(width, height) * 0.08f
                localPopupPath.reset()
                val cornerRadius = p * 1.5f
                localPopupPath.addRoundRect(p, p, width - p, height - p, cornerRadius, cornerRadius, Path.Direction.CW)
                canvas.withTranslation(0f, 6f) {
                    renderer.pShadow.color = renderer.colorPopupShadow
                    drawPath(localPopupPath, renderer.pShadow)
                }
                renderer.pPopupBg.color = renderer.colorPopupBg
                canvas.drawPath(localPopupPath, renderer.pPopupBg)
                if (renderer.strokeWidth > 0) {
                    renderer.pStroke.color = renderer.colorStroke
                    renderer.pStroke.strokeWidth = 1f
                    canvas.drawPath(localPopupPath, renderer.pStroke)
                }

                val textSize = min(width, height) * 0.22f
                renderer.pPopupText.textSize = textSize

                val themeId = keyboardTheme?.id ?: ""
                val highlightColor = if (themeId.contains("dark") || themeId == "terminal") "#4285F4".toColorInt() else renderer.colorPopupSelectedBg

                layout.cells.forEachIndexed { i, cell ->
                    val kx = cell.centerX
                    val ky = cell.centerY
                    val ty = ky - (renderer.pPopupText.ascent() + renderer.pPopupText.descent()) / 2f

                    if (i == selectedIndex) {
                        val sW = cell.bounds.width()
                        val sH = cell.bounds.height()
                        renderer.updateHexPath(localPopupHexPath, kx, ky, sW * 0.48f, sH * 0.48f)
                        canvas.drawPath(localPopupHexPath, renderer.pPress.apply { color = highlightColor })
                        renderer.pPopupText.color = renderer.colorPopupSelectedText
                    } else {
                        renderer.pPopupText.color = renderer.colorPopupText
                    }
                    canvas.drawText(cell.label, kx, ty, renderer.pPopupText)
                }
            }
        }
    }

    private fun getPopupAlternatives(key: HexLayoutEngine.Key): List<String> {
        return if (key.type == HexLayoutEngine.KeyType.FONT_PAGE || key.type == HexLayoutEngine.KeyType.SYMBOL_PAGE) {
            key.alternatives
        } else {
            val firstLabel = key.value
            listOf(firstLabel) + key.alternatives
        }
    }

    private fun buildPopupLayoutInfo(key: HexLayoutEngine.Key, isLongPress: Boolean): PopupLayoutInfo {
        val prx = key.rx * keyScale * 1.1f * popupScale
        val pry = key.ry * keyScale * 1.1f * popupScale
        val screenWidth = context.resources.displayMetrics.widthPixels

        if (!isLongPress) {
            val w = (prx * 2.5f).toInt()
            val h = (pry * 2.5f).toInt()
            val location = IntArray(2)
            getLocationInWindow(location)
            var x = (location[0] + key.cx - w / 2f).toInt()
            val y = (location[1] + getPopupOffsetY(key, false, h)).toInt()
            if (x < 0) x = 0
            if (x + w > screenWidth) x = screenWidth - w

            val label = if (shifted && key.type == HexLayoutEngine.KeyType.CHAR && key.value.length == 1 && key.value[0].isLetter()) key.display.uppercase() else key.display
            val singleCell = PopupCell(
                label = label,
                bounds = RectF(0f, 0f, w.toFloat(), h.toFloat()),
                centerX = w / 2f,
                centerY = h / 2f
            )
            return PopupLayoutInfo(key, false, listOf(singleCell), w, h, x, y)
        } else {
            val alts = getPopupAlternatives(key)
            val sW = prx * 1.45f
            val sH = pry * 1.45f
            val g = sW * 0.15f
            val p = sW * 0.5f

            val maxW = screenWidth - p * 2f
            var cols = when { alts.size <= 5 -> alts.size; alts.size <= 10 -> 5; else -> 6 }.coerceAtMost(alts.size)
            while (cols > 1 && (cols * sW + (cols - 1) * g) > maxW) cols--
            val rows = (alts.size + cols - 1) / cols

            val tw = if (rows > 1) cols * sW + (cols - 1) * g else alts.size * sW + (alts.size - 1) * g
            val th = rows * sH + (rows - 1) * g

            val w = (tw + p * 2.5f).toInt().coerceAtMost(screenWidth)
            val h = (th + p * 1.5f).toInt()

            val px = w / 2f
            val py = h / 2f

            val cells = mutableListOf<PopupCell>()
            alts.forEachIndexed { i, a ->
                val row = i / cols
                val col = i % cols
                val itemsInThisRow = if (row == rows - 1) alts.size - (row * cols) else cols
                val rowW = itemsInThisRow * sW + (itemsInThisRow - 1) * g
                val kx = px - rowW / 2f + sW / 2f + col * (sW + g)
                val ky = py - th / 2f + sH / 2f + row * (sH + g)
                val displayLabel = if (a == " " && i == 0) "␣" else a
                val bounds = RectF(kx - sW / 2f, ky - sH / 2f, kx + sW / 2f, ky + sH / 2f)
                cells.add(PopupCell(displayLabel, bounds, kx, ky))
            }

            val location = IntArray(2)
            getLocationInWindow(location)
            var x = (location[0] + key.cx - w / 2f).toInt()
            val y = (location[1] + getPopupOffsetY(key, true, h)).toInt()

            // Anclaje inteligente en bordes
            if (x < 0) x = 0
            if (x + w > screenWidth) x = screenWidth - w

            return PopupLayoutInfo(key, true, cells, w, h, x, y)
        }
    }

    private val hidePopupRunnable = Runnable {
        try { if (popupWindow?.isShowing == true) popupWindow?.dismiss() } catch (e: Exception) { Log.e("HexKB", "Error dismissing popup", e) }
        popupVisibleKey = null
    }

    private fun getPopupOffsetY(key: HexLayoutEngine.Key, isLongPress: Boolean, h: Int): Int {
        return if (!isLongPress) {
            (key.cy - key.ry * (1.1f + 1.2f * popupScale) - h / 2f).toInt()
        } else {
            (key.cy - key.ry * (1.1f + 0.2f * popupScale) - h).toInt()
        }
    }

    private fun showPopup(key: HexLayoutEngine.Key, isLongPress: Boolean = false) {
        if (!showKeyPopup || windowToken == null) return
        handler.removeCallbacks(hidePopupRunnable)

        val layout = buildPopupLayoutInfo(key, isLongPress)
        currentPopupLayout = layout

        if (popupWindow == null) {
            popupContentView = KeyPopupView(context).apply { setLayerType(LAYER_TYPE_SOFTWARE, null) }
            popupWindow = PopupWindow(popupContentView, ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                elevation = 20f
                isTouchable = false
                isOutsideTouchable = false
                setBackgroundDrawable(null)
                inputMethodMode = PopupWindow.INPUT_METHOD_NOT_NEEDED
                isClippingEnabled = false
                animationStyle = 0
            }
        }

        val alts = getPopupAlternatives(key)
        val initialIndex = if (isLongPress) {
            val idx = alts.indexOf(key.display)
            if (idx != -1) idx else 0
        } else {
            -1
        }

        popupContentView?.key = key
        popupContentView?.isLongPress = isLongPress
        popupContentView?.selectedIndex = initialIndex
        popupSelectedIndex = initialIndex

        popupWindow?.width = layout.width
        popupWindow?.height = layout.height
        popupContentView?.invalidate()

        if (popupWindow?.isShowing == true) {
            popupWindow?.update(layout.popupX, layout.popupY, layout.width, layout.height)
        } else {
            popupWindow?.showAtLocation(this, Gravity.NO_GRAVITY, layout.popupX, layout.popupY)
        }
    }

    private fun hidePopup(immediate: Boolean = false) {
        handler.removeCallbacks(hidePopupRunnable)
        if (immediate || popupContentView?.isLongPress == true) {
            hidePopupRunnable.run()
        } else {
            handler.postDelayed(hidePopupRunnable, 80L)
        }
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
                if (keys.isEmpty() && width > 0) buildLayout(width.toFloat())
                val hit = hitTest(x, y)
                if (hit != null) {
                    activePointers[pointerId] = hit
                    initialPointerKeys[pointerId] = hit
                    hit.isPressed = true
                    pointerStartTime[pointerId] = System.currentTimeMillis()
                    if (hit.type != HexLayoutEngine.KeyType.DELETE) { triggerVibration(); triggerSound() }
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
                        isGestureActive = false; isScrollingCursor = false; longPressTriggered = false; longPressStarted = false; popupVisibleKey = null; popupSelectedIndex = -1
                        if (showKeyPopup && hit.type == HexLayoutEngine.KeyType.CHAR) { popupVisibleKey = hit; popupSelectedIndex = 0; showPopup(hit, false) }
                        if (hit.alternatives.isNotEmpty()) handler.postDelayed(longPressRunnable, longPressTimeout)
                    } else animateKeyToScale(hit, 0.85f)
                    if (hit.type == HexLayoutEngine.KeyType.DELETE || hit.isHalfRight) { listener?.onDelete(); startDeleteRepeat() }
                    invalidate()
                }
            }
            MotionEvent.ACTION_MOVE -> {
                if (popupVisibleKey != null && longPressStarted && pointerId == e.getPointerId(0)) handlePopupSelection(x, y)
                else if (pressedKey?.type == HexLayoutEngine.KeyType.SPACE && pointerId == e.getPointerId(0)) {
                    val deltaX = x - lastScrollX
                    if (abs(deltaX) > 20) {
                        isScrollingCursor = true; (context as? HexKeyboardService)?.moveCursor(if (deltaX > 0) 1 else -1)
                        lastScrollX = x; invalidate()
                    }
                } else if (pointerId == e.getPointerId(0) && pressedKey?.type == HexLayoutEngine.KeyType.CHAR && !longPressStarted && !isScrollingCursor && !longPressTriggered) {
                    val deltaY = y - lastScrollY; val deltaX = x - gestureStartX; val totalDist = sqrt(deltaX * deltaX + (y - gestureStartY) * (y - gestureStartY))
                    if (pressedKey?.value == "n") {
                        if (deltaY < -80f && abs(deltaY) > 2.0f * abs(deltaX)) {
                            longPressTriggered = true; isGestureActive = false; gesturePath.reset(); gesturePoints.clear(); cancelKeyLongPress(); hidePopup()
                            triggerVibration(FeedbackManager.HapticType.LONG_PRESS); triggerSound()
                            listener?.onChar(if (shifted || capsLock) "Ñ" else "ñ"); if (shifted && !capsLock) shifted = false
                            invalidate()
                        } else if (!isGestureActive && gestureTypingEnabled && totalDist > gestureThreshold) {
                            isGestureActive = true; cancelKeyLongPress(); popupVisibleKey = null; hidePopup()
                        }
                    } else if (!isGestureActive && gestureTypingEnabled && totalDist > gestureThreshold) {
                        isGestureActive = true; cancelKeyLongPress(); popupVisibleKey = null; hidePopup()
                    }
                    if (isGestureActive) {
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
                    val px = e.getX(i); val py = e.getY(i); val oldHit = activePointers[pid]; val newHit = hitTest(px, py)
                    if (newHit != oldHit && newHit != null) {
                        oldHit?.isPressed = false
                        if (!(oldHit in spaceKeys && newHit in spaceKeys) && !(oldHit in shiftKeys && newHit in shiftKeys) && !(oldHit in deleteKeys && newHit in deleteKeys)) {
                            animateKeyToScale(oldHit, 1.0f); animateKeyToScale(newHit, 0.85f)
                        }
                        activePointers[pid] = newHit; newHit.isPressed = true
                        updateGroupPressStates()
                        if (pid == e.getPointerId(0)) {
                            if (pressedKey?.type == HexLayoutEngine.KeyType.DELETE || pressedKey?.isHalfRight == true) cancelDeleteRepeat()
                            pressedKey = newHit
                            if (!(oldHit in spaceKeys && newHit in spaceKeys)) {
                                cancelKeyLongPress(); if (showKeyPopup && newHit.type == HexLayoutEngine.KeyType.CHAR) { popupVisibleKey = newHit; popupSelectedIndex = 0; showPopup(newHit, false) } else hidePopup()
                                if (newHit.alternatives.isNotEmpty() && !longPressStarted) handler.postDelayed(longPressRunnable, longPressTimeout)
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
                            if (gesturePoints.size >= 4) {
                                listener?.onGesture(ArrayList(gesturePoints))
                            } else {
                                fireKey(hit)
                            }
                            isGestureActive = false; gesturePath.reset(); gesturePoints.clear(); invalidate()
                        } else if (popupVisibleKey != null && longPressStarted) {
                            val a = getPopupAlternatives(popupVisibleKey!!)
                            if (popupSelectedIndex in a.indices) {
                                triggerVibration(); val v = a[popupSelectedIndex]
                                when (popupVisibleKey!!.type) {
                                    HexLayoutEngine.KeyType.LANGUAGE -> if (v == "🌐") (context as? HexKeyboardService)?.showLanguagePicker() else (context as? HexKeyboardService)?.switchToNextLanguage()
                                    HexLayoutEngine.KeyType.TOGGLE -> switchLayoutByValue(v)
                                    HexLayoutEngine.KeyType.SYMBOL_PAGE -> { if (v.toIntOrNull() != null) switchSymbolPageByValue(v) else fireKey(popupVisibleKey!!) }
                                    HexLayoutEngine.KeyType.FONT_PAGE -> { fontPage = (v.toIntOrNull() ?: 1) - 1; buildLayout(width.toFloat()); invalidate() }
                                    else -> listener?.onLongPressSelect(v)
                                }
                            }
                            hidePopup()
                        } else { if (!longPressTriggered) { fireKey(hit); performClick() }; hidePopup() }
                        pressedKey = null; longPressTriggered = false; longPressStarted = false; isScrollingCursor = false
                    } else fireKey(hit)
                    activePointers.remove(pointerId); initialPointerKeys.remove(pointerId); updateGroupPressStates(); invalidate()
                }
            }
            MotionEvent.ACTION_CANCEL -> {
                keys.forEach { it.isPressed = false; animateKeyToScale(it, 1.0f) }
                activePointers.clear(); initialPointerKeys.clear(); pointerStartTime.clear(); spacePressed = false; shiftPressed = false; deletePressed = false; enterPressed = false
                isGestureActive = false; gesturePath.reset(); gesturePoints.clear(); cancelKeyLongPress(); cancelDeleteRepeat(); hidePopup()
                pressedKey = null; longPressTriggered = false; longPressStarted = false; isScrollingCursor = false; invalidate()
            }
        }
        return true
    }

    private fun animateKeyToScale(key: HexLayoutEngine.Key?, target: Float) {
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
            "ABC" -> HexLayoutEngine.LayoutMode.ALPHA
            "?123" -> HexLayoutEngine.LayoutMode.NUMERIC
            "123" -> HexLayoutEngine.LayoutMode.PURE_NUMERIC
            "#+=" -> HexLayoutEngine.LayoutMode.SYMBOLS
            "𝔉" -> HexLayoutEngine.LayoutMode.FONTS
            else -> return
        }
    }

    private fun handlePopupSelection(x: Float, y: Float) {
        val layout = currentPopupLayout ?: return
        if (!layout.isLongPress) return

        // Convertir coordenadas de pantalla global a la vista local del popup
        val localX = x - layout.popupX
        val localY = y - layout.popupY

        // Búsqueda por Hitbox / Bounding Box de la celda con tolerancia cómoda
        var newIndex = layout.cells.indexOfFirst { cell ->
            val expandedRect = RectF(cell.bounds)
            expandedRect.inset(-24f, -24f)
            expandedRect.contains(localX, localY)
        }

        // Si el dedo está dentro del área del popup pero entre celdas, elegir la celda más cercana
        if (newIndex == -1 && localY >= -50f && localY <= layout.height + 150f) {
            var minDist = Float.MAX_VALUE
            layout.cells.forEachIndexed { i, cell ->
                val dx = localX - cell.centerX
                val dy = localY - cell.centerY
                val dist = dx * dx + dy * dy
                if (dist < minDist) {
                    minDist = dist
                    newIndex = i
                }
            }
        }

        if (newIndex != -1 && newIndex != popupSelectedIndex) {
            popupSelectedIndex = newIndex
            triggerVibration(FeedbackManager.HapticType.TICK)
            popupContentView?.selectedIndex = newIndex
            popupContentView?.invalidate()
        }
    }

    private fun hitTest(x: Float, y: Float): HexLayoutEngine.Key? {
        if (keys.isEmpty()) return null
        val isNGestureActive = longPressTriggered && pressedKey?.value == "n"
        return engine.findKeyAt(x, y, if (isNGestureActive) keys.filter { it.value !in keysToIgnoreDuringNGesture } else keys)
    }

    private fun fireKey(key: HexLayoutEngine.Key, touchX: Float = key.cx, touchY: Float = key.cy) {
        listener?.onKeyClick(key)
        when (key.type) {
            HexLayoutEngine.KeyType.LANGUAGE -> (context as? HexKeyboardService)?.switchToNextLanguage()
            HexLayoutEngine.KeyType.SHIFT -> {
                val n = System.currentTimeMillis()
                if (n - lastShiftClickTime <= doubleClickThreshold) { capsLock = true; shifted = true }
                else { if (capsLock) { capsLock = false; shifted = false } else shifted = !shifted }
                lastShiftClickTime = n; invalidate()
            }
            HexLayoutEngine.KeyType.ENTER -> listener?.onEnter()
            HexLayoutEngine.KeyType.SPACE -> listener?.onChar(" ")
            HexLayoutEngine.KeyType.TOGGLE -> {
                layoutMode = when (layoutMode) {
                    HexLayoutEngine.LayoutMode.ALPHA -> HexLayoutEngine.LayoutMode.NUMERIC
                    HexLayoutEngine.LayoutMode.NUMERIC -> HexLayoutEngine.LayoutMode.ALPHA
                    HexLayoutEngine.LayoutMode.SYMBOLS -> HexLayoutEngine.LayoutMode.ALPHA
                    HexLayoutEngine.LayoutMode.PURE_NUMERIC -> HexLayoutEngine.LayoutMode.ALPHA
                    HexLayoutEngine.LayoutMode.FONTS -> HexLayoutEngine.LayoutMode.ALPHA
                }
                if (layoutMode == HexLayoutEngine.LayoutMode.ALPHA) (context as? HexKeyboardService)?.updateShiftState()
            }
            HexLayoutEngine.KeyType.SYMBOL_PAGE -> {
                val pages = symbolPages.keys.toList()
                if (pages.isNotEmpty()) {
                    val i = pages.indexOf(symbolPage); symbolPage = pages[(i + 1) % pages.size]
                    listener?.onSymbolPageChange(symbolPage); buildLayout(width.toFloat()); invalidate()
                }
            }
            HexLayoutEngine.KeyType.FONT_PAGE -> { fontPage = (fontPage + 1) % 10; buildLayout(width.toFloat()); invalidate() }
            HexLayoutEngine.KeyType.CHAR -> {
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
