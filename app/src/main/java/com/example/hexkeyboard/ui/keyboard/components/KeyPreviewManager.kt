package com.example.hexkeyboard.ui.keyboard.components

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
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
import android.view.animation.DecelerateInterpolator
import android.widget.PopupWindow
import androidx.core.graphics.toColorInt
import androidx.core.graphics.withTranslation
import com.example.hexkeyboard.data.repository.KeyboardTheme
import kotlin.math.min

/**
 * Gestor desacoplado para la vista previa de teclas (Key Preview) al estilo Gboard / Material 3.
 *
 * Utiliza una única ventana flotante (PopupWindow) de alto rendimiento para evitar llamados
 * innecesarios a invalidate() en la vista principal del teclado.
 */
class KeyPreviewManager(private val context: Context) {

    private var popupWindow: PopupWindow? = null
    private var previewView: KeyPreviewView? = null
    private var exitAnimator: ValueAnimator? = null
    private var hexRenderer: HexRenderer? = null

    private var currentKey: HexLayoutEngine.Key? = null
    var isEnabled: Boolean = true

    // Parámetros de tema/diseño
    private var popupBgColor: Int = Color.WHITE
    private var popupTextColor: Int = Color.BLACK
    private var popupStrokeColor: Int = Color.TRANSPARENT
    private var popupShadowColor: Int = "#40000000".toColorInt()
    private var typeface: Typeface = Typeface.DEFAULT

    fun updateTheme(renderer: HexRenderer, theme: KeyboardTheme?) {
        this.hexRenderer = renderer
        val bg = if (renderer.colorPopupBg != 0) renderer.colorPopupBg else (theme?.popupBackgroundColor ?: Color.WHITE)
        val text = if (renderer.colorPopupText != 0) renderer.colorPopupText else (theme?.popupTextColor ?: Color.BLACK)
        popupBgColor = if (bg != 0) bg else Color.WHITE
        popupTextColor = if (text != 0) text else Color.BLACK
        popupStrokeColor = if (renderer.strokeWidth > 0) renderer.colorStroke else Color.TRANSPARENT
        popupShadowColor = if (renderer.colorPopupShadow != 0) renderer.colorPopupShadow else "#40000000".toColorInt()
        typeface = renderer.sharedTypeface
        previewView?.updateTheme(popupBgColor, popupTextColor, popupStrokeColor, popupShadowColor, typeface, renderer)
    }

    /**
     * Muestra o traslada suavemente la vista previa para la tecla pulsada.
     */
    fun showPreview(
        parentView: View,
        key: HexLayoutEngine.Key,
        keyWindowX: Float,
        keyWindowY: Float,
        label: String,
        isRect: Boolean,
        popupScale: Float = 1.0f
    ) {
        if (!isEnabled || key.type != HexLayoutEngine.KeyType.CHAR || label.isEmpty()) {
            dismissPreview(immediate = true)
            return
        }

        currentKey = key
        cancelExitAnimation()

        val prx = (key.rx * 1.35f * popupScale).coerceAtLeast(36f)
        val pry = (key.ry * 1.35f * popupScale).coerceAtLeast(36f)

        val w = (prx * 2.2f).toInt()
        val h = (pry * 2.2f).toInt()

        // Calcular posición X e Y en ventana para desplazar el popup hacia arriba
        var x = (keyWindowX - w / 2f).toInt()
        val y = (keyWindowY - key.ry * (1.1f + 0.8f * popupScale) - h).toInt()

        // Restricción de bordes de la vista del teclado
        val parentWidth = if (parentView.width > 0) parentView.width else context.resources.displayMetrics.widthPixels
        if (x < 8) x = 8
        if (x + w > parentWidth - 8) x = parentWidth - w - 8

        // Inicializar vistas de popup si aún no existen
        if (popupWindow == null) {
            val v = KeyPreviewView(context)
            previewView = v
            popupWindow = PopupWindow(
                v,
                w,
                h
            ).apply {
                elevation = 16f
                isTouchable = false
                isOutsideTouchable = false
                setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
                inputMethodMode = PopupWindow.INPUT_METHOD_NOT_NEEDED
                isClippingEnabled = false
                animationStyle = 0
            }
        }

        previewView?.apply {
            updateTheme(popupBgColor, popupTextColor, popupStrokeColor, popupShadowColor, typeface, hexRenderer)
            setPreviewData(label, isRect, prx, pry)
            alpha = 1.0f
            scaleX = 1.0f
            scaleY = 1.0f
            requestLayout()
        }

        popupWindow?.width = w
        popupWindow?.height = h

        if (popupWindow?.isShowing == true) {
            popupWindow?.update(x, y, w, h)
            previewView?.invalidate()
        } else {
            try {
                popupWindow?.showAtLocation(parentView, Gravity.NO_GRAVITY, x, y)
            } catch (e: Exception) {
                android.util.Log.e("HexKB", "Error showing key preview", e)
            }
        }
    }

    /**
     * Oculta el preview con animación suave de desvanecimiento (debounce ~60ms) o de forma inmediata.
     */
    fun dismissPreview(immediate: Boolean = false) {
        if (popupWindow?.isShowing != true) return

        cancelExitAnimation()

        if (immediate) {
            dismissInternal()
            return
        }

        val v = previewView ?: run { dismissInternal(); return }

        // Animación de desvanecimiento + micro-escala hacia abajo estilo Gboard (60ms)
        val anim = ValueAnimator.ofFloat(1.0f, 0.0f).apply {
            duration = 60L
            interpolator = DecelerateInterpolator()
            addUpdateListener { animator ->
                val progress = animator.animatedValue as Float
                v.alpha = progress
                val scale = 0.88f + 0.12f * progress
                v.scaleX = scale
                v.scaleY = scale
                v.invalidate()
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    dismissInternal()
                }
            })
        }
        exitAnimator = anim
        anim.start()
    }

    private fun cancelExitAnimation() {
        exitAnimator?.cancel()
        exitAnimator = null
    }

    private fun dismissInternal() {
        try {
            if (popupWindow?.isShowing == true) {
                popupWindow?.dismiss()
            }
        } catch (_: Exception) {
            // Ignorar excepciones al cerrar
        }
        currentKey = null
    }

    val isShowing: Boolean get() = popupWindow?.isShowing == true

    /**
     * Vista interna superpuesta para dibujar la silueta adaptativa Material 3 y el texto del preview.
     */
    private class KeyPreviewView(context: Context) : View(context) {

        private var label: String = ""
        private var isRect: Boolean = false
        private var prx: Float = 0f
        private var pry: Float = 0f
        private var renderer: HexRenderer? = null

        private val path = Path()
        private val rectF = RectF()

        private val pBg = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        private val pText = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
        private val pStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 2f }
        private val pShadow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            maskFilter = android.graphics.BlurMaskFilter(8f, android.graphics.BlurMaskFilter.Blur.NORMAL)
        }

        fun updateTheme(bgColor: Int, textColor: Int, strokeColor: Int, shadowColor: Int, tf: Typeface, renderer: HexRenderer? = null) {
            pBg.color = if (bgColor != 0) bgColor else Color.WHITE
            pText.color = if (textColor != 0) textColor else Color.BLACK
            pText.typeface = tf
            pStroke.color = strokeColor
            pShadow.color = if (shadowColor != 0) shadowColor else "#40000000".toColorInt()
            if (renderer != null) this.renderer = renderer
        }

        fun setPreviewData(label: String, isRect: Boolean, prx: Float, pry: Float) {
            this.label = label
            this.isRect = isRect
            this.prx = prx
            this.pry = pry
            invalidate()
        }

        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            val mw = MeasureSpec.getSize(widthMeasureSpec)
            val mh = MeasureSpec.getSize(heightMeasureSpec)
            val finalW = if (mw > 0) mw else 120
            val finalH = if (mh > 0) mh else 140
            setMeasuredDimension(finalW, finalH)
        }

        override fun onDraw(canvas: Canvas) {
            val w = width.toFloat()
            val h = height.toFloat()
            if (w <= 0 || h <= 0) return

            val cx = w / 2f
            val cy = h / 2f

            path.reset()
            val r = renderer
            if (isRect || r == null) {
                val cr = min(prx, pry) * 0.4f
                rectF.set(cx - prx, cy - pry, cx + prx, cy + pry)
                path.addRoundRect(rectF, cr, cr, Path.Direction.CW)
            } else {
                // MODO HEXAGONAL EXACTO: Utiliza prx y pry manteniendo la proporción exacta de la tecla
                r.updateHexPath(path, cx, cy, prx, pry)
            }

            // Dibujar sombra desplazada M3
            canvas.withTranslation(0f, 4f) {
                drawPath(path, pShadow)
            }

            // Dibujar fondo flotante M3
            canvas.drawPath(path, pBg)

            // Dibujar borde si corresponde
            if (pStroke.color != Color.TRANSPARENT) {
                canvas.drawPath(path, pStroke)
            }

            // Dibujar carácter
            pText.textSize = min(prx, pry) * 1.15f
            val ty = cy - (pText.ascent() + pText.descent()) / 2f
            canvas.drawText(label, cx, ty, pText)
        }
    }
}

