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
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathNode
import androidx.compose.ui.graphics.vector.VectorGroup
import androidx.compose.ui.graphics.vector.VectorNode
import androidx.compose.ui.graphics.vector.VectorPath
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import com.example.hexkeyboard.R
import com.example.hexkeyboard.data.repository.KeyboardTheme
import com.example.hexkeyboard.data.repository.ThemeUtils.getKeyboardString
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
        var curX = 0f
        var curY = 0f
        var subpathStartX = 0f
        var subpathStartY = 0f

        fun addNodes(nodes: Iterable<VectorNode>) {
            for (node in nodes) {
                when (node) {
                    is VectorPath -> {
                        path.fillType = if (node.pathFillType == PathFillType.EvenOdd) Path.FillType.EVEN_ODD else Path.FillType.WINDING
                        for (cmd in node.pathData) {
                            when (cmd) {
                                is PathNode.MoveTo -> {
                                    path.moveTo(cmd.x, cmd.y)
                                    curX = cmd.x; curY = cmd.y; subpathStartX = cmd.x; subpathStartY = cmd.y
                                }
                                is PathNode.RelativeMoveTo -> {
                                    path.rMoveTo(cmd.dx, cmd.dy)
                                    curX += cmd.dx; curY += cmd.dy; subpathStartX = curX; subpathStartY = curY
                                }
                                is PathNode.LineTo -> {
                                    path.lineTo(cmd.x, cmd.y)
                                    curX = cmd.x; curY = cmd.y
                                }
                                is PathNode.RelativeLineTo -> {
                                    path.rLineTo(cmd.dx, cmd.dy)
                                    curX += cmd.dx; curY += cmd.dy
                                }
                                is PathNode.HorizontalTo -> {
                                    path.lineTo(cmd.x, curY)
                                    curX = cmd.x
                                }
                                is PathNode.RelativeHorizontalTo -> {
                                    path.rLineTo(cmd.dx, 0f)
                                    curX += cmd.dx
                                }
                                is PathNode.VerticalTo -> {
                                    path.lineTo(curX, cmd.y)
                                    curY = cmd.y
                                }
                                is PathNode.RelativeVerticalTo -> {
                                    path.rLineTo(0f, cmd.dy)
                                    curY += cmd.dy
                                }
                                is PathNode.CurveTo -> {
                                    path.cubicTo(cmd.x1, cmd.y1, cmd.x2, cmd.y2, cmd.x3, cmd.y3)
                                    curX = cmd.x3; curY = cmd.y3
                                }
                                is PathNode.RelativeCurveTo -> {
                                    path.rCubicTo(cmd.dx1, cmd.dy1, cmd.dx2, cmd.dy2, cmd.dx3, cmd.dy3)
                                    curX += cmd.dx3; curY += cmd.dy3
                                }
                                is PathNode.QuadTo -> {
                                    path.quadTo(cmd.x1, cmd.y1, cmd.x2, cmd.y2)
                                    curX = cmd.x2; curY = cmd.y2
                                }
                                is PathNode.RelativeQuadTo -> {
                                    path.rQuadTo(cmd.dx1, cmd.dy1, cmd.dx2, cmd.dy2)
                                    curX += cmd.dx2; curY += cmd.dy2
                                }
                                is PathNode.Close -> {
                                    path.close()
                                    curX = subpathStartX; curY = subpathStartY
                                }
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
    val iconDelete: Drawable? by lazy { imageVectorToDrawable(Icons.AutoMirrored.Outlined.Backspace) }
    val iconDeleteFilled: Drawable? by lazy { imageVectorToDrawable(Icons.AutoMirrored.Filled.Backspace) }
    val iconEnter: Drawable? by lazy { ContextCompat.getDrawable(context, R.drawable.ic_enter) }
    val iconSend: Drawable? by lazy { ContextCompat.getDrawable(context, R.drawable.ic_send) }
    val iconSearch: Drawable? by lazy { ContextCompat.getDrawable(context, R.drawable.ic_search) }
    val iconDone: Drawable? by lazy { ContextCompat.getDrawable(context, R.drawable.ic_done) }
    val iconGo: Drawable? by lazy { ContextCompat.getDrawable(context, R.drawable.ic_go) }
    val iconEmoji: Drawable? by lazy { ContextCompat.getDrawable(context, R.drawable.ic_emoji) }
    val iconEmojiFilled: Drawable? by lazy { ContextCompat.getDrawable(context, R.drawable.ic_emoji_filled) }
    val iconPuzzle: Drawable? by lazy { imageVectorToDrawable(Icons.Outlined.Extension) }
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

        val baseAlpha = (theme.keysOpacity * 255).toInt().coerceIn(0, 255)
        pFill.alpha = baseAlpha
        pSpec.alpha = baseAlpha
        pPress.alpha = 255

        if (theme.keysBlur > 0.1f) {
            view.setLayerType(View.LAYER_TYPE_SOFTWARE, null)
            val radius = theme.keysBlur.coerceAtLeast(0.1f)
            val filter = BlurMaskFilter(radius, BlurMaskFilter.Blur.NORMAL)
            pFill.maskFilter = filter
            pSpec.maskFilter = filter
            pPress.maskFilter = filter
            pStroke.maskFilter = filter
        } else {
            view.setLayerType(View.LAYER_TYPE_HARDWARE, null)
            pFill.maskFilter = null
            pSpec.maskFilter = null
            pPress.maskFilter = null
            pStroke.maskFilter = null
        }
    }

    fun drawBackground(canvas: Canvas, theme: KeyboardTheme, backgroundImage: Bitmap?, backgroundBlurImage: Bitmap?, width: Int, height: Int) {
        val img = backgroundBlurImage ?: backgroundImage
        if (img != null && !img.isRecycled) {
            pBackground.alpha = (theme.backgroundOpacity * 255).toInt()
            val finalScale = max(width.toFloat() / img.width, height.toFloat() / img.height)
            val dw = img.width * finalScale
            val dh = img.height * finalScale
            val baseDx = (width - dw) / 2f
            val baseDy = (height - dh) / 2f
            rectF.set(baseDx, baseDy, baseDx + dw, baseDy + dh)
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
        val individualColor = themeObj.individualKeyColors[key.value]
        val activeBgColor: Int

        val fill = when {
            pressed -> { pPress.color = colorPress; pPress }
            key.type == HexLayoutEngine.KeyType.SHIFT && (shifted || capsLock) -> { pFill.color = colorShiftActive; pFill }
            key.type == HexLayoutEngine.KeyType.SHIFT -> { pFill.color = colorShiftInactive; pFill }
            individualColor != null -> { pFill.color = individualColor; pFill }
            key.type in listOf(
                HexLayoutEngine.KeyType.TOGGLE, HexLayoutEngine.KeyType.ENTER,
                HexLayoutEngine.KeyType.SYMBOL_PAGE, HexLayoutEngine.KeyType.EMOJI,
                HexLayoutEngine.KeyType.FUNCTIONS, HexLayoutEngine.KeyType.CLIPBOARD,
                HexLayoutEngine.KeyType.LANGUAGE, HexLayoutEngine.KeyType.DELETE
            ) -> { pSpec.color = colorSpec; pSpec }
            else -> { pFill.color = colorFill; pFill }
        }
        activeBgColor = fill.color
        val targetAlpha = (themeObj.keysOpacity * 255).toInt().coerceIn(0, 255)
        fill.alpha = if (pressed) 255 else targetAlpha

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

        if (key.isHalfLeft || key.isHalfRight) return
        if (overrideCx != null && key.display.isEmpty()) return

        val label = if (shifted && key.type == HexLayoutEngine.KeyType.CHAR && key.value.length == 1 && key.value[0].isLetter()) key.display.uppercase() else key.display

        var customLabel: String? = null
        val isEnterWithText = key.type == HexLayoutEngine.KeyType.ENTER && layoutMode in listOf(HexLayoutEngine.LayoutMode.ALPHA, HexLayoutEngine.LayoutMode.FONTS, HexLayoutEngine.LayoutMode.SYMBOLS, HexLayoutEngine.LayoutMode.NUMERIC)
        val isDeleteWithText = key.type == HexLayoutEngine.KeyType.DELETE && overrideCx != null

        when (key.type) {
            HexLayoutEngine.KeyType.SHIFT -> customLabel = context.getKeyboardString(R.string.key_label_shift, language)
            HexLayoutEngine.KeyType.DELETE -> {
                if (isDeleteWithText) {
                    customLabel = context.getKeyboardString(R.string.key_label_delete, language)
                }
            }
            HexLayoutEngine.KeyType.ENTER -> {
                if (isEnterWithText) {
                    customLabel = when (currentImeAction) {
                        EditorInfo.IME_ACTION_GO -> context.getKeyboardString(R.string.key_action_go, language)
                        EditorInfo.IME_ACTION_SEARCH -> context.getKeyboardString(R.string.key_action_search, language)
                        EditorInfo.IME_ACTION_SEND -> context.getKeyboardString(R.string.key_action_send, language)
                        EditorInfo.IME_ACTION_NEXT -> context.getKeyboardString(R.string.key_action_next, language)
                        EditorInfo.IME_ACTION_DONE -> context.getKeyboardString(R.string.key_action_done, language)
                        EditorInfo.IME_ACTION_PREVIOUS -> context.getKeyboardString(R.string.key_action_prev, language)
                        else -> context.getKeyboardString(R.string.key_label_enter, language)
                    }
                }
            }
            else -> {}
        }

        val sz = if (customLabel != null) {
            min(key.rx, key.ry) * 0.45f
        } else {
            min(key.rx, key.ry) * 0.62f
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
                HexLayoutEngine.KeyType.SYMBOL_PAGE -> null
                HexLayoutEngine.KeyType.FONT_PAGE -> null
                HexLayoutEngine.KeyType.EMOJI -> if (themeObj.backgroundImageUri != null) iconEmojiFilled else iconEmoji
                HexLayoutEngine.KeyType.CLIPBOARD -> null
                HexLayoutEngine.KeyType.FUNCTIONS -> iconPuzzle
                HexLayoutEngine.KeyType.LANGUAGE -> iconLanguage
                else -> null
            }
        }

        val isLightBg = ColorUtils.calculateLuminance(activeBgColor) > 0.5
        val isLightText = ColorUtils.calculateLuminance(colorText) > 0.5
        val textColorCalculated = if (isKeyTextColorCustom) {
            colorText
        } else if (isLightBg == isLightText) {
            if (isLightBg) Color.BLACK else Color.WHITE
        } else {
            colorText
        }

        if (dr != null) {
            val s = (min(key.rx, key.ry) * 1.0f).toInt()
            dr.setBounds((drawCx - s / 2).toInt(), (key.cy - s / 2).toInt(), (drawCx + s / 2).toInt(), (key.cy + s / 2).toInt())
            val tint = when {
                individualColor != null -> Color.WHITE
                key.type in listOf(HexLayoutEngine.KeyType.EMOJI, HexLayoutEngine.KeyType.FUNCTIONS, HexLayoutEngine.KeyType.LANGUAGE) -> colorIcon
                else -> textColorCalculated
            }
            dr.mutate().setTint(tint)
            dr.alpha = 255
            dr.draw(canvas)
        } else {
            pText.color = textColorCalculated
            pText.alpha = 255
            var drawLabel = customLabel ?: label
            if (customLabel == null && themeObj.id == "default" && (key.type == HexLayoutEngine.KeyType.ENTER && layoutMode != HexLayoutEngine.LayoutMode.PURE_NUMERIC)) {
                drawLabel = ""
            }
            if (drawLabel.isNotEmpty()) canvas.drawText(drawLabel, drawCx, ty, pText)
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
