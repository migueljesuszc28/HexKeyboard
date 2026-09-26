package com.example.hexkeyboard.ui.keyboard.components

import android.content.Context
import android.graphics.*
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.text.StaticLayout
import android.view.View
import android.view.inputmethod.EditorInfo
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.automirrored.outlined.Backspace
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.VectorNode
import androidx.compose.ui.graphics.vector.VectorPath
import androidx.compose.ui.graphics.vector.PathNode
import androidx.compose.ui.graphics.vector.VectorGroup
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import com.example.hexkeyboard.R
import com.example.hexkeyboard.data.repository.KeyboardTheme
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

class HexRenderer(private val context: Context) {

    // Paints
    val pFill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    val pSpec = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    val pPress = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    val pStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        isDither = true
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    val pText = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    val pPopupText = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    val pEmojiText = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    val pPopupBg = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    val pShadow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        maskFilter = BlurMaskFilter(8f, BlurMaskFilter.Blur.NORMAL)
    }
    val pBackground = Paint(Paint.ANTI_ALIAS_FLAG).apply { isFilterBitmap = true }

    private fun imageVectorToDrawable(imageVector: ImageVector): Drawable {
        val density = Density(context)
        val width = with(density) { 24.dp.toPx().toInt() }
        val height = with(density) { 24.dp.toPx().toInt() }
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val path = Path()
        fun addNodes(nodes: Iterable<VectorNode>) {
            for (node in nodes) {
                when (node) {
                    is VectorPath -> {
                        for (cmd in node.pathData) {
                            when (cmd) {
                                is PathNode.MoveTo -> path.moveTo(cmd.x, cmd.y)
                                is PathNode.RelativeMoveTo -> path.rMoveTo(cmd.dx, cmd.dy)
                                is PathNode.LineTo -> path.lineTo(cmd.x, cmd.y)
                                is PathNode.RelativeLineTo -> path.rLineTo(cmd.dx, cmd.dy)
                                is PathNode.HorizontalTo -> path.lineTo(cmd.x, 0f)
                                is PathNode.RelativeHorizontalTo -> path.rLineTo(cmd.dx, 0f)
                                is PathNode.VerticalTo -> path.lineTo(0f, cmd.y)
                                is PathNode.RelativeVerticalTo -> path.rLineTo(0f, cmd.dy)
                                is PathNode.CurveTo -> path.cubicTo(cmd.x1, cmd.y1, cmd.x2, cmd.y2, cmd.x3, cmd.y3)
                                is PathNode.RelativeCurveTo -> path.rCubicTo(cmd.dx1, cmd.dy1, cmd.dx2, cmd.dy2, cmd.dx3, cmd.dy3)
                                is PathNode.QuadTo -> path.quadTo(cmd.x1, cmd.y1, cmd.x2, cmd.y2)
                                is PathNode.RelativeQuadTo -> path.rQuadTo(cmd.dx1, cmd.dy1, cmd.dx2, cmd.dy2)
                                is PathNode.Close -> path.close()
                                else -> {}
                            }
                        }
                    }
                    is VectorGroup -> {
                        addNodes(node)
                    }
                }
            }
        }
        addNodes(imageVector.root)

        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.FILL
        }

        val scale = density.density
        val matrix = Matrix().apply { setScale(scale, scale) }
        path.transform(matrix)

        canvas.drawPath(path, paint)
        return BitmapDrawable(context.resources, bitmap)
    }

    // Icons
    val iconShift: Drawable? by lazy { ContextCompat.getDrawable(context, R.drawable.ic_shift) }
    val iconShiftActive: Drawable? by lazy { ContextCompat.getDrawable(context, R.drawable.ic_shift_active) }
    val iconShiftFilled: Drawable? by lazy { ContextCompat.getDrawable(context, R.drawable.ic_shift_filled) }
    val iconDelete: Drawable? by lazy { imageVectorToDrawable(Icons.AutoMirrored.Outlined.Backspace) }
    val iconDeleteFilled: Drawable? by lazy { imageVectorToDrawable(Icons.AutoMirrored.Filled.Backspace) }
    val iconEnter: Drawable? by lazy { ContextCompat.getDrawable(context, R.drawable.ic_enter) }
    val iconSend: Drawable? by lazy { ContextCompat.getDrawable(context, R.drawable.ic_send) }
    val iconSearch: Drawable? by lazy { ContextCompat.getDrawable(context, R.drawable.ic_search) }
    val iconDone: Drawable? by lazy { ContextCompat.getDrawable(context, R.drawable.ic_done) }
    val iconGo: Drawable? by lazy { ContextCompat.getDrawable(context, R.drawable.ic_go) }
    val iconSymbols: Drawable? by lazy { ContextCompat.getDrawable(context, R.drawable.ic_symbols) }
    val iconEmoji: Drawable? by lazy { ContextCompat.getDrawable(context, R.drawable.ic_emoji) }
    val iconEmojiFilled: Drawable? by lazy { ContextCompat.getDrawable(context, R.drawable.ic_emoji_filled) }
    val iconClipboard: Drawable? by lazy { ContextCompat.getDrawable(context, R.drawable.ic_clipboard) }
    val iconClipboardFilled: Drawable? by lazy { ContextCompat.getDrawable(context, R.drawable.ic_clipboard_filled) }
    val iconPuzzle: Drawable? by lazy { ContextCompat.getDrawable(context, R.drawable.ic_puzzle) }
    val iconLanguage: Drawable? by lazy { ContextCompat.getDrawable(context, android.R.drawable.ic_menu_mapmode) }

    // Colors
    var colorFill = 0
    var colorSpec = 0
    var colorPress = 0
    var colorStroke = 0
    var strokeWidth = 1f
    var colorText = 0
    var colorIcon = 0
    var colorShiftActive = 0
    var colorShiftInactive = 0
    var colorPopupBg = 0
    var colorPopupText = 0
    var colorPopupShadow = 0
    var colorPopupSelectedBg = 0
    var colorPopupSelectedText = 0
    var colorDeletePressedIcon = 0
    var isKeyTextColorCustom = false

    // Caches
    val emojiProcessedCache = object : LinkedHashMap<String, CharSequence>(200, 0.75f, true) {
        override fun removeEldestEntry(eldest: Map.Entry<String, CharSequence>?): Boolean = size > 200
    }
    val staticLayoutCache = object : LinkedHashMap<String, StaticLayout>(100, 0.75f, true) {
        override fun removeEldestEntry(eldest: Map.Entry<String, StaticLayout>?): Boolean = size > 100
    }

    private val rectF = RectF()
    private val fontMetrics = Paint.FontMetrics()

    var sharedTypeface: Typeface = Typeface.DEFAULT
        set(value) {
            field = value
            pText.typeface = value
            pPopupText.typeface = value
            pEmojiText.typeface = value
        }

    fun clearCaches() {
        emojiProcessedCache.clear()
        staticLayoutCache.clear()
    }

    fun applyTheme(theme: KeyboardTheme, view: View) {
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

        pFill.color = colorFill
        pSpec.color = colorSpec
        pPress.color = colorPress
        pStroke.color = colorStroke
        pStroke.strokeWidth = strokeWidth
        pText.color = colorText
        pPopupText.color = colorPopupText
        pPopupBg.color = colorPopupBg

        if (theme.keysBlur > 0.1f) {
            view.setLayerType(View.LAYER_TYPE_SOFTWARE, null)
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
            view.setLayerType(View.LAYER_TYPE_HARDWARE, null)
            pFill.maskFilter = null
            pSpec.maskFilter = null
            pPress.maskFilter = null
            pStroke.maskFilter = null
            pFill.alpha = 255
            pSpec.alpha = 255
            pPress.alpha = 255
        }
    }

    fun drawBackground(canvas: Canvas, theme: KeyboardTheme, backgroundImage: Bitmap?, backgroundBlurImage: Bitmap?, parallaxX: Float, parallaxY: Float, width: Int, height: Int) {
        val img = backgroundBlurImage ?: backgroundImage
        if (img != null && !img.isRecycled) {
            pBackground.alpha = (theme.backgroundOpacity * 255).toInt()
            val baseScale = max(width.toFloat() / img.width, height.toFloat() / img.height)
            val parallaxLimit = 0.10f
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

    var isMultiLine: Boolean = false
    var currentImeAction: Int = 0
    var language: String = "es"
    var layoutMode: HexLayoutEngine.LayoutMode = HexLayoutEngine.LayoutMode.ALPHA
    var showLongPressIndicators: Boolean = true

    fun drawSingleKey(
        canvas: Canvas,
        key: HexLayoutEngine.Key,
        theme: KeyboardTheme?,
        pressed: Boolean,
        shifted: Boolean,
        capsLock: Boolean,
        appliedScale: Float,
        overrideCx: Float? = null
    ) {
        val themeObj = theme ?: return
        val floating = false

        val individualColor = themeObj.individualKeyColors[key.value]
        var activeBgColor = colorFill

        if (!floating) {
            val fill = when {
                pressed -> { pPress.color = colorPress; pPress }
                key.type == HexLayoutEngine.KeyType.SHIFT && (shifted || capsLock) -> { pFill.color = colorShiftActive; pFill }
                key.type == HexLayoutEngine.KeyType.SHIFT && (!shifted && !capsLock) -> { pFill.color = colorShiftInactive; pFill }
                individualColor != null -> { pFill.color = individualColor; pFill }
                key.type in listOf(HexLayoutEngine.KeyType.TOGGLE, HexLayoutEngine.KeyType.ENTER, HexLayoutEngine.KeyType.SYMBOL_PAGE, HexLayoutEngine.KeyType.EMOJI, HexLayoutEngine.KeyType.FUNCTIONS, HexLayoutEngine.KeyType.CLIPBOARD, HexLayoutEngine.KeyType.LANGUAGE, HexLayoutEngine.KeyType.SHIFT, HexLayoutEngine.KeyType.DELETE) -> { pSpec.color = colorSpec; pSpec }
                else -> { pFill.color = colorFill; pFill }
            }
            activeBgColor = fill.color

            if (!pressed) {
                if (key.type == HexLayoutEngine.KeyType.SHIFT && (shifted || capsLock)) {
                    fill.color = colorShiftActive
                } else if (key.type == HexLayoutEngine.KeyType.SHIFT && (!shifted && !capsLock)) {
                    fill.color = colorShiftInactive
                } else if (individualColor != null) {
                    fill.color = individualColor
                } else if (key.type in listOf(HexLayoutEngine.KeyType.TOGGLE, HexLayoutEngine.KeyType.ENTER, HexLayoutEngine.KeyType.SYMBOL_PAGE, HexLayoutEngine.KeyType.EMOJI, HexLayoutEngine.KeyType.FUNCTIONS, HexLayoutEngine.KeyType.CLIPBOARD, HexLayoutEngine.KeyType.LANGUAGE, HexLayoutEngine.KeyType.SHIFT, HexLayoutEngine.KeyType.DELETE)) {
                    fill.color = colorSpec
                } else {
                    fill.color = colorFill
                }
                if (themeObj.keysBlur > 0f) fill.alpha = 200
            }

            canvas.drawPath(key.path, fill)

            val isShiftCapsLock = key.type == HexLayoutEngine.KeyType.SHIFT && capsLock
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

        if (key.isHalfLeft || key.isHalfRight) return
        if (overrideCx != null && key.display.isEmpty()) return

        val label = if (shifted && key.type == HexLayoutEngine.KeyType.CHAR && key.value.length == 1 && key.value[0].isLetter()) key.display.uppercase() else key.display

        var customLabel: String? = null
        val isEnterWithText = key.type == HexLayoutEngine.KeyType.ENTER && layoutMode in listOf(HexLayoutEngine.LayoutMode.ALPHA, HexLayoutEngine.LayoutMode.FONTS, HexLayoutEngine.LayoutMode.SYMBOLS, HexLayoutEngine.LayoutMode.NUMERIC)
        val isDeleteWithText = key.type == HexLayoutEngine.KeyType.DELETE && overrideCx != null

        when (key.type) {
            HexLayoutEngine.KeyType.SHIFT -> customLabel = "MAYUS"
            HexLayoutEngine.KeyType.DELETE -> {
                if (isDeleteWithText) {
                    customLabel = "BORRAR"
                }
            }
            HexLayoutEngine.KeyType.ENTER -> {
                if (isEnterWithText) {
                    customLabel = when (currentImeAction) {
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
            else -> {}
        }

        val sz = when {
            customLabel != null && (key.type == HexLayoutEngine.KeyType.SHIFT || key.type == HexLayoutEngine.KeyType.DELETE || key.type == HexLayoutEngine.KeyType.ENTER) ->
                min(key.rx, key.ry) * 0.45f
            customLabel != null && customLabel.length <= 3 -> min(key.rx, key.ry) * 0.5f
            customLabel != null -> min(key.rx, key.ry) * 0.35f
            else -> min(key.rx, key.ry) * 0.62f
        }
        pText.textSize = sz
        pText.isFakeBoldText = customLabel != null
        pText.getFontMetrics(fontMetrics)
        val ty = key.cy - (fontMetrics.ascent + fontMetrics.descent) / 2f

        val drawCx = overrideCx ?: key.cx

        val dr = if (customLabel != null) {
            null
        } else if (themeObj.id == "default" && (key.type == HexLayoutEngine.KeyType.ENTER && layoutMode != HexLayoutEngine.LayoutMode.PURE_NUMERIC)) {
            null
        } else {
            when (key.type) {
                HexLayoutEngine.KeyType.DELETE -> if (pressed) iconDeleteFilled else iconDelete
                HexLayoutEngine.KeyType.ENTER -> {
                    when (currentImeAction) {
                        EditorInfo.IME_ACTION_SEND -> iconSend
                        EditorInfo.IME_ACTION_SEARCH -> iconSearch
                        EditorInfo.IME_ACTION_DONE -> iconDone
                        EditorInfo.IME_ACTION_GO -> iconGo
                        else -> iconEnter
                    }
                }
                HexLayoutEngine.KeyType.SYMBOL_PAGE -> iconSymbols
                HexLayoutEngine.KeyType.FONT_PAGE -> null
                HexLayoutEngine.KeyType.EMOJI -> if (themeObj.backgroundImageUri != null) iconEmojiFilled else iconEmoji
                HexLayoutEngine.KeyType.CLIPBOARD -> if (themeObj.backgroundImageUri != null) iconClipboardFilled else iconClipboard
                HexLayoutEngine.KeyType.FUNCTIONS -> iconPuzzle
                HexLayoutEngine.KeyType.LANGUAGE -> iconLanguage
                else -> null
            }
        }

        if (dr != null) {
            val s = (min(key.rx, key.ry) * 1.0f).toInt()
            dr.setBounds((drawCx - s/2).toInt(), (key.cy - s/2).toInt(), (drawCx + s/2).toInt(), (key.cy + s/2).toInt())
            val tint = when {
                (key.type == HexLayoutEngine.KeyType.ENTER || key.type == HexLayoutEngine.KeyType.DELETE) && individualColor != null -> Color.WHITE
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
            dr.mutate().setTint(tint); dr.alpha = 255; dr.draw(canvas)
        } else if (key.type == HexLayoutEngine.KeyType.SYMBOL_PAGE) {
            val drawColor = if (isKeyTextColorCustom) colorIcon else {
                val isLightBg = ColorUtils.calculateLuminance(activeBgColor) > 0.5
                if (isLightBg) Color.BLACK else colorIcon
            }
            pText.color = drawColor; pText.textSize = sz * 0.8f
            canvas.drawText("SYM", drawCx, ty, pText)
            pText.color = colorText; pText.textSize = sz
        } else {
            if (floating) pText.color = colorIcon else {
                if (isKeyTextColorCustom) pText.color = colorText else {
                    val isLightBg = ColorUtils.calculateLuminance(activeBgColor) > 0.5
                    val isLightText = ColorUtils.calculateLuminance(colorText) > 0.5
                    pText.color = if (isLightBg == isLightText) (if (isLightBg) Color.BLACK else Color.WHITE) else colorText
                }
            }
            pText.alpha = 255
            var drawLabel = customLabel ?: label
            if (customLabel == null && themeObj.id == "default" && (key.type == HexLayoutEngine.KeyType.ENTER && layoutMode != HexLayoutEngine.LayoutMode.PURE_NUMERIC)) {
                drawLabel = ""
            }
            if (drawLabel.isNotEmpty()) canvas.drawText(drawLabel, drawCx, ty, pText)
            if (showLongPressIndicators && key.alternatives.isNotEmpty()) {
                val dotR = key.rx * 0.08f; val dx = key.cx + key.rx * 0.4f; val dy = key.cy - key.ry * 0.5f
                pPopupBg.color = pText.color; pPopupBg.alpha = 150; canvas.drawCircle(dx, dy, dotR, pPopupBg); pPopupBg.alpha = 255
            }
            pText.alpha = 255
        }
    }

    private fun getIconForKey(key: HexLayoutEngine.Key, pressed: Boolean): Drawable? {
        return when (key.type) {
            HexLayoutEngine.KeyType.DELETE -> if (pressed) iconDeleteFilled else iconDelete
            HexLayoutEngine.KeyType.ENTER -> iconEnter
            HexLayoutEngine.KeyType.SHIFT -> iconShift
            HexLayoutEngine.KeyType.EMOJI -> if (pressed) iconEmojiFilled else iconEmoji
            HexLayoutEngine.KeyType.CLIPBOARD -> if (pressed) iconClipboardFilled else iconClipboard
            HexLayoutEngine.KeyType.LANGUAGE -> iconLanguage
            else -> null
        }
    }
    
    fun updateHexPath(path: Path, cx: Float, cy: Float, rx: Float, ry: Float) {
        val s32 = 0.8660254f
        val r = rx * s32
        val h = ry
        val rounding = 0.28f
        fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t

        val px = floatArrayOf(cx, cx + r, cx + r, cx, cx - r, cx - r)
        val py = floatArrayOf(cy - h, cy - h/2f, cy + h/2f, cy + h, cy + h/2f, cy - h/2f)

        path.reset()
        val startX = lerp(px[0], px[5], rounding)
        val startY = lerp(py[0], py[5], rounding)
        path.moveTo(startX, startY)

        for (i in 0..5) {
            val next = (i + 1) % 6
            path.quadTo(px[i], py[i], lerp(px[i], px[next], rounding), lerp(py[i], py[next], rounding))
            path.lineTo(lerp(px[next], px[i], rounding), lerp(py[next], py[i], rounding))
        }
        path.close()
    }
}
