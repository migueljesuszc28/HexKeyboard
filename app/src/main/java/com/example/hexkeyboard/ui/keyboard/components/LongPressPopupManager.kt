package com.example.hexkeyboard.ui.keyboard.components

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.view.Gravity
import android.view.View
import android.widget.PopupWindow
import androidx.core.graphics.toColorInt
import androidx.core.graphics.withTranslation
import com.example.hexkeyboard.data.repository.KeyboardTheme
import com.example.hexkeyboard.logic.managers.FeedbackManager
import kotlin.math.min

/**
 * Gestor desacoplado para la pulsación larga (Long Press) y menú de alternativas / acentos al estilo Gboard / Material 3.
 */
class LongPressPopupManager(private val context: Context) {

    data class PopupCell(
        val label: String,
        val bounds: RectF,
        val centerX: Float,
        val centerY: Float
    )

    data class PopupLayoutInfo(
        val key: KeyboardLayoutEngine.Key,
        val isSingleSymbol: Boolean,
        val cells: List<PopupCell>,
        val width: Int,
        val height: Int,
        val popupX: Int,
        val popupY: Int
    )

    private var popupWindow: PopupWindow? = null
    private var contentView: LongPressPopupView? = null
    private var currentLayout: PopupLayoutInfo? = null
    private var selectedIndex: Int = 0

    // Colores y diseño
    private var popupBgColor: Int = Color.WHITE
    private var popupTextColor: Int = Color.BLACK
    private var popupSelectedBgColor: Int = "#4285F4".toColorInt()
    private var popupSelectedTextColor: Int = Color.WHITE
    private var popupStrokeColor: Int = Color.TRANSPARENT
    private var popupShadowColor: Int = "#40000000".toColorInt()
    private var typeface: Typeface = Typeface.DEFAULT

    fun updateTheme(renderer: KeyboardRenderer, theme: KeyboardTheme?) {
        val bg = if (renderer.colorPopupBg != 0) renderer.colorPopupBg else (theme?.popupBackgroundColor ?: Color.WHITE)
        val text = if (renderer.colorPopupText != 0) renderer.colorPopupText else (theme?.popupTextColor ?: Color.BLACK)
        popupBgColor = if (bg != 0) bg else Color.WHITE
        popupTextColor = if (text != 0) text else Color.BLACK
        popupSelectedBgColor = if (renderer.colorPopupSelectedBg != 0) renderer.colorPopupSelectedBg else (theme?.popupSelectedBackgroundColor ?: "#4285F4".toColorInt())
        popupSelectedTextColor = if (renderer.colorPopupSelectedText != 0) renderer.colorPopupSelectedText else (theme?.popupSelectedTextColor ?: Color.WHITE)
        popupStrokeColor = if (renderer.strokeWidth > 0) renderer.colorStroke else Color.TRANSPARENT
        popupShadowColor = if (renderer.colorPopupShadow != 0) renderer.colorPopupShadow else "#40000000".toColorInt()
        typeface = renderer.sharedTypeface
        contentView?.updateTheme(
            popupBgColor,
            popupTextColor,
            popupSelectedBgColor,
            popupSelectedTextColor,
            popupStrokeColor,
            popupShadowColor,
            typeface
        )
    }

    private fun getPopupAlternatives(key: KeyboardLayoutEngine.Key): List<String> {
        return if (key.type == KeyboardLayoutEngine.KeyType.FONT_PAGE || key.type == KeyboardLayoutEngine.KeyType.SYMBOL_PAGE) {
            key.alternatives
        } else {
            val firstLabel = key.value
            if (key.alternatives.contains(firstLabel)) key.alternatives else listOf(firstLabel) + key.alternatives
        }
    }

    /**
     * Despliega la ventana flotante de pulsación larga (Strip M3) para la tecla seleccionada.
     */
    fun showLongPressPopup(
        parentView: View,
        key: KeyboardLayoutEngine.Key,
        keyWindowX: Float,
        keyWindowY: Float,
        popupScale: Float = 1.0f
    ) {
        val alternatives = getPopupAlternatives(key)
        if (alternatives.isEmpty()) return

        FeedbackManager.triggerVibration(context, FeedbackManager.HapticType.LONG_PRESS)

        val prx = (key.rx * popupScale * 1.15f).coerceAtLeast(32f)
        val pry = (key.ry * popupScale * 1.15f).coerceAtLeast(32f)

        val sW = prx * 1.5f
        val sH = pry * 1.5f
        val g = sW * 0.12f
        val p = sW * 0.4f

        val screenWidth = context.resources.displayMetrics.widthPixels
        val maxW = screenWidth - p * 2f
        var cols = when {
            alternatives.size <= 5 -> alternatives.size
            alternatives.size <= 10 -> 5
            else -> 6
        }.coerceAtMost(alternatives.size)

        while (cols > 1 && (cols * sW + (cols - 1) * g) > maxW) cols--
        val rows = (alternatives.size + cols - 1) / cols

        val tw = if (rows > 1) cols * sW + (cols - 1) * g else alternatives.size * sW + (alternatives.size - 1) * g
        val th = rows * sH + (rows - 1) * g

        val w = (tw + p * 2.2f).toInt().coerceAtMost(screenWidth)
        val h = (th + p * 1.5f).toInt()

        val px = w / 2f
        val py = h / 2f

        val cells = mutableListOf<PopupCell>()
        alternatives.forEachIndexed { i, a ->
            val row = i / cols
            val col = i % cols
            val itemsInThisRow = if (row == rows - 1) alternatives.size - (row * cols) else cols
            val rowW = itemsInThisRow * sW + (itemsInThisRow - 1) * g
            val kx = px - rowW / 2f + sW / 2f + col * (sW + g)
            val ky = py - th / 2f + sH / 2f + row * (sH + g)
            val displayLabel = if (a == " " && i == 0) "␣" else a
            val bounds = RectF(kx - sW / 2f, ky - sH / 2f, kx + sW / 2f, ky + sH / 2f)
            cells.add(PopupCell(displayLabel, bounds, kx, ky))
        }

        var x = (keyWindowX - w / 2f).toInt()
        val y = (keyWindowY - key.ry * (0.2f + 0.8f * popupScale) - h).toInt()

        if (x < 12) x = 12
        if (x + w > screenWidth - 12) x = screenWidth - w - 12

        // Posición inicial seleccionada: la primera variante alternativa si existe, o el carácter base
        val isSingleSymbol = alternatives.size == 1
        val initialIndex = if (alternatives.size > 1 && key.value.isNotEmpty()) {
            val idx = alternatives.indexOf(key.value)
            if (idx == 0 && alternatives.size > 1) 1 else if (idx != -1) idx else 0
        } else 0

        selectedIndex = initialIndex

        val layoutInfo = PopupLayoutInfo(key, isSingleSymbol, cells, w, h, x, y)
        currentLayout = layoutInfo

        if (popupWindow == null) {
            val v = LongPressPopupView(context)
            contentView = v
            popupWindow = PopupWindow(
                v,
                w,
                h
            ).apply {
                elevation = 20f
                isTouchable = true
                isOutsideTouchable = true
                setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
                inputMethodMode = PopupWindow.INPUT_METHOD_NOT_NEEDED
                isClippingEnabled = false
                animationStyle = 0
            }
        }

        contentView?.apply {
            updateTheme(
                popupBgColor,
                popupTextColor,
                popupSelectedBgColor,
                popupSelectedTextColor,
                popupStrokeColor,
                popupShadowColor,
                typeface
            )
            setLayoutInfo(layoutInfo, initialIndex)
            requestLayout()
        }

        popupWindow?.width = w
        popupWindow?.height = h

        if (popupWindow?.isShowing == true) {
            popupWindow?.update(x, y, w, h)
        } else {
            try {
                val anchor = parentView.rootView ?: parentView
                popupWindow?.showAtLocation(anchor, Gravity.NO_GRAVITY, x, y)
            } catch (e: Exception) {
                android.util.Log.e("HexKB", "Error showing long press popup", e)
            }
        }
    }

    /**
     * Rastrea la posición del dedo durante la interacción continua (ACTION_MOVE).
     */
    fun onDrag(screenX: Float, screenY: Float) {
        val layout = currentLayout ?: return
        if (popupWindow?.isShowing != true) return

        val localX = screenX - layout.popupX
        val localY = screenY - layout.popupY

        val newIndex = layout.cells.indexOfFirst { cell ->
            val expandedRect = RectF(cell.bounds)
            expandedRect.inset(-12f, -12f)
            expandedRect.contains(localX, localY)
        }

        if (newIndex != -1 && newIndex != selectedIndex) {
            selectedIndex = newIndex
            contentView?.setSelectedIndex(newIndex)
            FeedbackManager.triggerVibration(context, FeedbackManager.HapticType.TICK)
        }
    }

    /**
     * Finaliza la selección al soltar el dedo (ACTION_UP) y retorna el carácter elegido.
     */
    fun onRelease(): String? {
        val layout = currentLayout ?: run { dismissPopup(); return null }
        val alternatives = getPopupAlternatives(layout.key)

        val selectedChar = if (selectedIndex in alternatives.indices) {
            alternatives[selectedIndex]
        } else null

        dismissPopup()
        return selectedChar
    }

    fun dismissPopup() {
        try {
            if (popupWindow?.isShowing == true) {
                popupWindow?.dismiss()
            }
        } catch (_: Exception) {
        }
        popupWindow = null
        contentView = null
        currentLayout = null
    }

    val isShowing: Boolean get() = popupWindow?.isShowing == true

    /**
     * Vista interna para renderizar el strip M3 de alternativas con celdas y resaltado tonal.
     */
    private class LongPressPopupView(context: Context) : View(context) {

        private var layoutInfo: PopupLayoutInfo? = null
        private var selectedIndex: Int = 0

        private val path = Path()
        private val cellPath = Path()
        private val rectF = RectF()

        private val pBg = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        private val pSelectedBg = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        private val pText = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
        private val pStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 1f }
        private val pShadow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            maskFilter = android.graphics.BlurMaskFilter(8f, android.graphics.BlurMaskFilter.Blur.NORMAL)
        }

        private var textColor: Int = Color.BLACK
        private var selectedTextColor: Int = Color.WHITE

        fun updateTheme(
            bgColor: Int,
            txtColor: Int,
            selBgColor: Int,
            selTxtColor: Int,
            strokeColor: Int,
            shadowColor: Int,
            tf: Typeface
        ) {
            pBg.color = if (bgColor != 0) bgColor else Color.WHITE
            pSelectedBg.color = if (selBgColor != 0) selBgColor else "#4285F4".toColorInt()
            textColor = if (txtColor != 0) txtColor else Color.BLACK
            selectedTextColor = if (selTxtColor != 0) selTxtColor else Color.WHITE
            pText.typeface = tf
            pStroke.color = strokeColor
            pShadow.color = if (shadowColor != 0) shadowColor else "#40000000".toColorInt()
        }

        fun setLayoutInfo(info: PopupLayoutInfo, initialIndex: Int) {
            layoutInfo = info
            selectedIndex = initialIndex
            invalidate()
        }

        fun setSelectedIndex(index: Int) {
            if (selectedIndex != index) {
                selectedIndex = index
                invalidate()
            }
        }

        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            val mw = MeasureSpec.getSize(widthMeasureSpec)
            val mh = MeasureSpec.getSize(heightMeasureSpec)
            val finalW = if (mw > 0) mw else (layoutInfo?.width ?: 200)
            val finalH = if (mh > 0) mh else (layoutInfo?.height ?: 100)
            setMeasuredDimension(finalW, finalH)
        }

        override fun onDraw(canvas: Canvas) {
            val layout = layoutInfo ?: return
            val w = width.toFloat()
            val h = height.toFloat()
            if (w <= 0 || h <= 0) return

            val p = min(w, h) * 0.08f
            path.reset()
            val cornerRadius = p * 1.5f
            rectF.set(p, p, w - p, h - p)
            path.addRoundRect(rectF, cornerRadius, cornerRadius, Path.Direction.CW)

            // Sombras y fondo M3 del contenedor
            canvas.withTranslation(0f, 6f) {
                drawPath(path, pShadow)
            }
            canvas.drawPath(path, pBg)
            if (pStroke.color != Color.TRANSPARENT) {
                canvas.drawPath(path, pStroke)
            }

            // Dibujar celdas y resaltado de la celda activa
            layout.cells.forEachIndexed { i, cell ->
                val kx = cell.centerX
                val ky = cell.centerY
                val sW = cell.bounds.width()
                val sH = cell.bounds.height()

                val isSelected = i == selectedIndex

                if (isSelected) {
                    val cr = min(sW, sH) * 0.28f
                    rectF.set(kx - sW * 0.48f, ky - sH * 0.48f, kx + sW * 0.48f, ky + sH * 0.48f)
                    cellPath.reset()
                    cellPath.addRoundRect(rectF, cr, cr, Path.Direction.CW)
                    canvas.drawPath(cellPath, pSelectedBg)
                    pText.color = selectedTextColor
                } else {
                    pText.color = textColor
                }

                val textSize = if (cell.label.length > 1) sH * 0.42f else sH * 0.58f
                pText.textSize = textSize
                val ty = ky - (pText.ascent() + pText.descent()) / 2f
                canvas.drawText(cell.label, kx, ty, pText)
            }
        }
    }
}
