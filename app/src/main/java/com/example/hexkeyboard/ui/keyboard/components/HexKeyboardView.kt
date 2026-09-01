package com.example.hexkeyboard.ui.keyboard.components

import android.content.Context
import android.graphics.*
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.PopupWindow
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.datastore.preferences.core.Preferences
import androidx.dynamicanimation.animation.FloatPropertyCompat
import androidx.dynamicanimation.animation.SpringAnimation
import androidx.dynamicanimation.animation.SpringForce
import com.example.hexkeyboard.R
import com.example.hexkeyboard.data.repository.KeyboardTheme
import com.example.hexkeyboard.data.repository.ThemeUtils
import com.example.hexkeyboard.logic.managers.FeedbackManager
import com.example.hexkeyboard.service.HexKeyboardService
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
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
        fun onDelete()
        fun onEnter()
        fun onLongPressSelect(char: String)
        fun onSymbolPageChange(page: String)
        fun onKeyClick(key: HexLayoutEngine.Key) {}
        fun onParallaxChange(x: Float, y: Float) {}
        fun onGesture(points: List<PointF>) {}
    }

    var listener: Listener? = null
    var shifted = false
        set(value) {
            if (field != value) {
                field = value
                if (!value) capsLock = false
                if (layoutMode == HexLayoutEngine.LayoutMode.FONTS && width > 0) buildLayout(width.toFloat())
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
                renderer.emojiProcessedCache.clear()
                renderer.staticLayoutCache.clear()
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
    private var parallaxX = 0f
    private var parallaxY = 0f
    private var currentBackgroundUri: String? = null
    private var currentBackgroundBlur: Float = -1f
    private val parallaxLimit = 0.10f

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
        parallaxX = x; parallaxY = y; invalidate()
    }

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

    private fun loadBackgroundImage(uriString: String?, blurRadius: Float) {
        if (uriString == null) {
            backgroundImage?.recycle(); backgroundBlurImage?.recycle()
            backgroundImage = null; backgroundBlurImage = null
            return
        }
        try {
            val uri = Uri.parse(uriString)
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                val original = BitmapFactory.decodeStream(inputStream)
                backgroundImage?.recycle(); backgroundBlurImage?.recycle()
                backgroundImage = original
                backgroundBlurImage = if (blurRadius > 0) ThemeUtils.blurBitmap(original, blurRadius) else null
            }
        } catch (e: Exception) {
            Log.e("HexKB", "Error loading background image", e)
        }
    }

    fun resetState() {
        layoutMode = HexLayoutEngine.LayoutMode.ALPHA
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
        hidePopup(); viewScope.cancel()
    }

    private fun getHeightFactor(): Float {
        val h = getIntPrefSafely(ThemeUtils.KEYBOARD_HEIGHT, 50)
        return 0.7f + (h / 100f) * 0.6f
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
            springAnim.animateToFinalPosition(target)
        }
    }

    private val keys = mutableListOf<HexLayoutEngine.Key>()
    val allKeys: List<HexLayoutEngine.Key> get() = keys
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
    private val pointerStartTime = mutableMapOf<Int, Long>()
    private val handler = Handler(Looper.getMainLooper())
    private var longPressTriggered = false
    private var longPressStarted = false
    private val longPressTimeout: Long get() = getIntPrefSafely(ThemeUtils.LONG_PRESS_DURATION, 259).toLong()
    private val longPressRunnable = Runnable {
        pressedKey?.let { if (it.alternatives.isNotEmpty() && !isGestureActive) { longPressStarted = true; popupVisibleKey = it; popupSelectedIndex = 0; showPopup(it, true) } }
    }
    private var deleteRepeatRunnable: Runnable? = null
    private val deleteRepeatDelay = 400L
    private val deleteRepeatInterval = 80L
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
    }

    fun buildLayoutExternally() {
        if (width > 0) buildLayout(width.toFloat()); invalidate()
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
        return when {
            key.type == HexLayoutEngine.KeyType.SPACE && spaceKeys.size >= 2 -> unifiedSpaceCenterX
            key.type == HexLayoutEngine.KeyType.SHIFT && shiftKeys.size >= 2 -> unifiedShiftCenterX
            key.type == HexLayoutEngine.KeyType.DELETE && deleteKeys.size >= 2 -> unifiedDeleteCenterX
            key.type == HexLayoutEngine.KeyType.ENTER && enterKeys.size >= 2 -> unifiedEnterCenterX
            else -> null
        }
    }

    private fun drawKeysToCache() {
        val w = width; val h = height
        if (w <= 0 || h <= 0) return
        if (keysBitmap == null || keysBitmap?.width != w || keysBitmap?.height != h) {
            keysBitmap?.recycle()
            keysBitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
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
            renderer.drawBackground(canvas, theme, backgroundImage, backgroundBlurImage, parallaxX, parallaxY, width, height)
        }
        if (needsRedraw || keysBitmap == null) drawKeysToCache()
        keysBitmap?.let { canvas.drawBitmap(it, 0f, 0f, null) }
        for (key in keys) {
            if (key.isPressed || key.currentScale != 1.0f) {
                drawKey(canvas, key)
            }
        }
        if (isGestureActive && !gesturePath.isEmpty) canvas.drawPath(gesturePath, pGesture)
    }

    private fun drawKey(canvas: Canvas, key: HexLayoutEngine.Key) {
        canvas.save()
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
        canvas.scale(appliedScale, appliedScale, scaleCx, key.cy)
        renderer.drawSingleKey(canvas, key, keyboardTheme, pressed, shifted, capsLock, appliedScale, drawCx)
        canvas.restore()
    }

    private var popupWindow: PopupWindow? = null
    private var popupContentView: KeyPopupView? = null

    inner class KeyPopupView(context: Context) : View(context) {
        var key: HexLayoutEngine.Key? = null
        var isLongPress: Boolean = false
        var selectedIndex: Int = -1
        private val localPopupPath = Path()
        private val localPopupHexPath = Path()

        override fun onDraw(canvas: Canvas) {
            val k = key ?: return
            val px = width / 2f; val py = height / 2f
            val prx = k.rx * keyScale * 1.1f * popupScale
            val pry = k.ry * keyScale * 1.1f * popupScale
            val textSize = min(prx, pry) * 0.7f

            if (!isLongPress) {
                renderer.updateHexPath(localPopupHexPath, px, py, prx, pry)
                canvas.save(); canvas.translate(0f, 6f)
                renderer.pShadow.color = renderer.colorPopupShadow; canvas.drawPath(localPopupHexPath, renderer.pShadow)
                canvas.restore()
                renderer.pPopupBg.color = renderer.colorPopupBg; canvas.drawPath(localPopupHexPath, renderer.pPopupBg)
                if (renderer.strokeWidth > 0) {
                    renderer.pStroke.color = renderer.colorStroke; renderer.pStroke.strokeWidth = 2f; canvas.drawPath(localPopupHexPath, renderer.pStroke)
                }
                renderer.pPopupText.textSize = textSize
                val ty = py - (renderer.pPopupText.ascent() + renderer.pPopupText.descent()) / 2f
                val label = if (shifted && k.type == HexLayoutEngine.KeyType.CHAR && k.value.length == 1 && k.value[0].isLetter()) k.display.uppercase() else k.display
                renderer.pPopupText.color = renderer.colorPopupText
                canvas.drawText(label, px, ty, renderer.pPopupText)
            } else {
                val firstLabel = if (k.type != HexLayoutEngine.KeyType.CHAR && k.display.isNotEmpty()) k.display else k.value
                val alts = listOf(firstLabel) + k.alternatives
                //marca############################################3
                val sW = prx * 1.45f; val sH = pry * 1.45f; val g = sW * 0.15f; val p = sW * 0.5f
                //#################################################
                val maxW = width - p * 2f
                var cols = when { alts.size <= 5 -> alts.size; alts.size <= 10 -> 5; else -> 6 }.coerceAtMost(alts.size)
                while (cols > 1 && (cols * sW + (cols - 1) * g) > maxW) cols--
                val rows = (alts.size + cols - 1) / cols
                val tw = if (rows > 1) cols * sW + (cols - 1) * g else alts.size * sW + (alts.size - 1) * g
                val th = rows * sH + (rows - 1) * g
                localPopupPath.reset(); val cornerRadius = sW * 0.45f
                localPopupPath.addRoundRect(px - tw/2f - p, py - th/2f - p/2f, px + tw/2f + p, py + th/2f + p/2f, cornerRadius, cornerRadius, Path.Direction.CW)
                canvas.save(); canvas.translate(0f, 6f); renderer.pShadow.color = renderer.colorPopupShadow; canvas.drawPath(localPopupPath, renderer.pShadow); canvas.restore()
                renderer.pPopupBg.color = renderer.colorPopupBg; canvas.drawPath(localPopupPath, renderer.pPopupBg)
                if (renderer.strokeWidth > 0) { renderer.pStroke.color = renderer.colorStroke; renderer.pStroke.strokeWidth = 1f; canvas.drawPath(localPopupPath, renderer.pStroke) }
                renderer.pPopupText.textSize = textSize * 0.9f
                alts.forEachIndexed { i, a ->
                    val row = i / cols; val col = i % cols
                    val itemsInThisRow = if (row == rows - 1) alts.size - (row * cols) else cols
                    val rowW = itemsInThisRow * sW + (itemsInThisRow - 1) * g
                    val kx = px - rowW/2f + sW/2f + col * (sW + g)
                    val ky = py - th/2f + sH/2f + row * (sH + g)
                    val ty = ky - (renderer.pPopupText.ascent() + renderer.pPopupText.descent()) / 2f
                    if (i == selectedIndex) {
                        renderer.updateHexPath(localPopupHexPath, kx, ky, sW * 0.48f, sH * 0.48f)
                        val themeId = keyboardTheme?.id ?: ""
                        val highlightColor = if (themeId.contains("dark") || themeId == "terminal") Color.parseColor("#4285F4") else renderer.colorPopupSelectedBg
                        canvas.drawPath(localPopupHexPath, renderer.pPress.apply { color = highlightColor })
                        renderer.pPopupText.color = renderer.colorPopupSelectedText
                    } else renderer.pPopupText.color = renderer.colorPopupText
                    canvas.drawText(if (a == " " && i == 0) "␣" else a, kx, ty, renderer.pPopupText)
                }
            }
        }
    }

    private fun showPopup(key: HexLayoutEngine.Key, isLongPress: Boolean = false) {
        if (!showKeyPopup || windowToken == null) return
        if (popupWindow == null) {
            popupContentView = KeyPopupView(context).apply { setLayerType(LAYER_TYPE_SOFTWARE, null) }
            popupWindow = PopupWindow(popupContentView, ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                elevation = 20f; isTouchable = false; isOutsideTouchable = false; setBackgroundDrawable(null)
                inputMethodMode = PopupWindow.INPUT_METHOD_NOT_NEEDED; isClippingEnabled = false; animationStyle = 0
            }
        }
        popupContentView?.key = key; popupContentView?.isLongPress = isLongPress; popupContentView?.selectedIndex = if (isLongPress) 0 else -1
        val prx = key.rx * keyScale * 1.1f * popupScale; val pry = key.ry * keyScale * 1.1f * popupScale
        val screenWidth = context.resources.displayMetrics.widthPixels
        val w: Int; val h: Int
        if (!isLongPress) {
            w = (prx * 2.5f).toInt()
            h = (pry * 2.5f).toInt()
        } else {
            val firstLabel = if (key.type != HexLayoutEngine.KeyType.CHAR && key.display.isNotEmpty()) key.display else key.value
            val alts = listOf(firstLabel) + key.alternatives
            //###################################
            val sW = prx * 1.45f; val sH = pry * 1.45f; val g = sW * 0.15f; val p = sW * 0.5f
            //####################################
            val maxW = screenWidth - p * 2f
            var cols = when { alts.size <= 5 -> alts.size; alts.size <= 10 -> 5; else -> 6 }.coerceAtMost(alts.size)
            while (cols > 1 && (cols * sW + (cols - 1) * g) > maxW) cols--
            val rows = (alts.size + cols - 1) / cols
            val tw = if (rows > 1) cols * sW + (cols - 1) * g else alts.size * sW + (alts.size - 1) * g
            val th = rows * sH + (rows - 1) * g
            w = (tw + p * 2.5f).toInt()
            h = (th + p * 1.5f).toInt()
        }
        popupWindow?.width = w; popupWindow?.height = h; popupContentView?.invalidate()
        val location = IntArray(2); getLocationInWindow(location)
        val offsetX = (key.cx - w / 2f).toInt()
        val offsetY = (key.cy - key.ry * (1.1f + 1.2f * popupScale) - h / 2f).toInt()
        var x = location[0] + offsetX; var y = location[1] + offsetY
        if (x < 0) x = 0; if (x + w > screenWidth) x = screenWidth - w
        if (popupWindow?.isShowing == true) popupWindow?.update(x, y, w, h)
        else popupWindow?.showAtLocation(this, Gravity.NO_GRAVITY, x, y)
    }

    private fun hidePopup() {
        try { if (popupWindow?.isShowing == true) popupWindow?.dismiss() } catch (e: Exception) { Log.e("HexKB", "Error dismissing popup", e) }
        popupVisibleKey = null
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
                    activePointers[pointerId] = hit; hit.isPressed = true; pointerStartTime[pointerId] = System.currentTimeMillis()
                    if (hit.type != HexLayoutEngine.KeyType.DELETE) { triggerVibration(); triggerSound() }
                    if (pointerId == e.getPointerId(0)) {
                        val currentPressed = pressedKey
                        if (hit != currentPressed) {
                            if (currentPressed in spaceKeys && hit in spaceKeys) {} else if (currentPressed in shiftKeys && hit in shiftKeys) {} else if (currentPressed in deleteKeys && hit in deleteKeys) {} else {
                                animateKeyToScale(currentPressed, 1.0f); cancelKeyLongPress(); cancelDeleteRepeat()
                            }
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
                        if (deltaY < -100) {
                            longPressTriggered = true; isGestureActive = false; gesturePath.reset(); gesturePoints.clear(); cancelKeyLongPress(); hidePopup()
                            triggerVibration(FeedbackManager.HapticType.LONG_PRESS); triggerSound()
                            listener?.onChar(if (shifted || capsLock) "Ñ" else "ñ"); if (shifted && !capsLock) shifted = false
                            invalidate()
                        } else if (!isGestureActive && totalDist > gestureThreshold && !(deltaY < -20 && abs(deltaX) < abs(y - gestureStartY) * 0.5f)) {
                            isGestureActive = true; cancelKeyLongPress(); hidePopup()
                        }
                    } else if (!isGestureActive && totalDist > gestureThreshold) { isGestureActive = true; cancelKeyLongPress(); hidePopup() }
                    if (isGestureActive) { gesturePoints.add(PointF(x, y)); gesturePath.lineTo(x, y); invalidate() }
                }
                for (i in 0 until e.pointerCount) {
                    val pid = e.getPointerId(i)
                    if (pid == e.getPointerId(0) && (longPressStarted || isScrollingCursor || longPressTriggered || isGestureActive || (pressedKey?.value == "n" && !isGestureActive))) continue
                    val px = e.getX(i); val py = e.getY(i); val oldHit = activePointers[pid]; val newHit = hitTest(px, py)
                    if (newHit != oldHit) {
                        oldHit?.isPressed = false
                        if (!(oldHit in spaceKeys && newHit in spaceKeys) && !(oldHit in shiftKeys && newHit in shiftKeys) && !(oldHit in deleteKeys && newHit in deleteKeys)) {
                            animateKeyToScale(oldHit, 1.0f); animateKeyToScale(newHit, 0.85f)
                        }
                        if (newHit != null) { activePointers[pid] = newHit; newHit.isPressed = true } else activePointers.remove(pid)
                        updateGroupPressStates()
                        if (pid == e.getPointerId(0)) {
                            if (pressedKey?.type == HexLayoutEngine.KeyType.DELETE || pressedKey?.isHalfRight == true) cancelDeleteRepeat()
                            pressedKey = newHit
                            if (!(oldHit in spaceKeys && newHit in spaceKeys)) {
                                cancelKeyLongPress(); if (showKeyPopup && newHit?.type == HexLayoutEngine.KeyType.CHAR) { popupVisibleKey = newHit; popupSelectedIndex = 0; showPopup(newHit, false) } else hidePopup()
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
                    hit.isPressed = false; animateKeyToScale(hit, 1.0f)
                    if (pointerId == e.getPointerId(0)) {
                        cancelKeyLongPress(); cancelDeleteRepeat()
                        if (isGestureActive) { listener?.onGesture(ArrayList(gesturePoints)); isGestureActive = false; gesturePath.reset(); gesturePoints.clear(); invalidate() }
                        else if (popupVisibleKey != null && longPressStarted) {
                            val a = listOf(popupVisibleKey!!.value) + popupVisibleKey!!.alternatives
                            if (popupSelectedIndex in a.indices) {
                                triggerVibration(); val v = a[popupSelectedIndex]
                                when (popupVisibleKey!!.type) {
                                    HexLayoutEngine.KeyType.LANGUAGE -> if (v == "🌐") (context as? HexKeyboardService)?.showLanguagePicker() else (context as? HexKeyboardService)?.switchToNextLanguage()
                                    HexLayoutEngine.KeyType.TOGGLE -> switchLayoutByValue(v)
                                    HexLayoutEngine.KeyType.SYMBOL_PAGE -> if (v == "→") fireKey(popupVisibleKey!!) else switchSymbolPageByValue(v)
                                    HexLayoutEngine.KeyType.FONT_PAGE -> { fontPage = (v.toIntOrNull() ?: 1) - 1; buildLayout(width.toFloat()); invalidate() }
                                    else -> listener?.onLongPressSelect(v)
                                }
                            }
                            hidePopup()
                        } else { if (!longPressTriggered) { fireKey(hit); performClick() }; hidePopup() }
                        pressedKey = null; longPressTriggered = false; longPressStarted = false; isScrollingCursor = false
                    } else fireKey(hit)
                    activePointers.remove(pointerId); updateGroupPressStates(); invalidate()
                }
            }
            MotionEvent.ACTION_CANCEL -> {
                keys.forEach { it.isPressed = false; animateKeyToScale(it, 1.0f) }
                activePointers.clear(); pointerStartTime.clear(); spacePressed = false; shiftPressed = false; deletePressed = false; enterPressed = false
                isGestureActive = false; gesturePath.reset(); gesturePoints.clear(); cancelKeyLongPress(); cancelDeleteRepeat(); hidePopup()
                pressedKey = null; longPressTriggered = false; longPressStarted = false; isScrollingCursor = false; invalidate()
            }
        }
        return true
    }

    private fun animateKeyToScale(key: HexLayoutEngine.Key?, target: Float) {
        if (key == null) return
        when {
            key in spaceKeys -> spaceKeys.forEach { uiKeyMap[it]?.animateToScale(target) }
            key in shiftKeys -> shiftKeys.forEach { uiKeyMap[it]?.animateToScale(target) }
            key in deleteKeys -> deleteKeys.forEach { uiKeyMap[it]?.animateToScale(target) }
            key in enterKeys -> enterKeys.forEach { uiKeyMap[it]?.animateToScale(target) }
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
        val k = popupVisibleKey ?: return
        val prx = k.rx * keyScale * 1.1f * popupScale; val pry = k.ry * keyScale * 1.1f * popupScale
        val firstLabel = if (k.type != HexLayoutEngine.KeyType.CHAR && k.display.isNotEmpty()) k.display else k.value
        val alts = listOf(firstLabel) + k.alternatives; val sW = prx * 1.45f; val sH = pry * 1.45f; val g = sW * 0.15f; val p = sW * 0.5f
        val w = popupWindow?.width?.toFloat() ?: width.toFloat(); val h = popupWindow?.height?.toFloat() ?: 0f
        var cols = when { alts.size <= 5 -> alts.size; alts.size <= 10 -> 5; else -> 6 }.coerceAtMost(alts.size)
        while (cols > 1 && (cols * sW + (cols - 1) * g) > (w - p * 2f)) cols--
        val rows = (alts.size + cols - 1) / cols; val th = rows * sH + (rows - 1) * g
        val location = IntArray(2); getLocationInWindow(location)
        val screenWidth = context.resources.displayMetrics.widthPixels
        var finalPopupX = location[0] + (k.cx - w / 2f)
        if (finalPopupX < 0) finalPopupX = 0f; if (finalPopupX + w > screenWidth) finalPopupX = (screenWidth - w).toFloat()
        val rx = x - finalPopupX; val ry = y - (location[1] + (k.cy - k.ry * (1.1f + 1.2f * popupScale) - h / 2f))
        var minDist = Float.MAX_VALUE; var newIndex = 0
        alts.forEachIndexed { i, _ ->
            val r = i / cols; val c = i % cols; val itemsInThisRow = if (r == rows - 1) alts.size - (r * cols) else cols
            val rowW = itemsInThisRow * sW + (itemsInThisRow - 1) * g
            val kx = w/2f - rowW/2f + sW/2f + c * (sW + g); val ky = h/2f - th/2f + sH/2f + r * (sH + g)
            val dist = (rx - kx)*(rx - kx) + (ry - ky)*(ry - ky)
            if (dist < minDist) { minDist = dist; newIndex = i }
        }
        if (newIndex != popupSelectedIndex) {
            popupSelectedIndex = newIndex; triggerVibration(FeedbackManager.HapticType.TICK)
            popupContentView?.selectedIndex = newIndex; popupContentView?.invalidate()
        }
    }

    private fun hitTest(x: Float, y: Float): HexLayoutEngine.Key? {
        if (keys.isEmpty()) return null
        val isNGestureActive = longPressTriggered && pressedKey?.value == "n"
        return engine.findKeyAt(x, y, if (isNGestureActive) keys.filter { it.value !in keysToIgnoreDuringNGesture } else keys)
    }

    private fun fireKey(key: HexLayoutEngine.Key) {
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
                listener?.onChar(o); if (shifted && !capsLock) { shifted = false; invalidate() }
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
