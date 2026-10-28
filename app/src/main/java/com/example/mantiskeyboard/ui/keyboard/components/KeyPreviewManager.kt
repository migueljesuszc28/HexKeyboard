package com.example.mantiskeyboard.ui.keyboard.components

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
import com.example.mantiskeyboard.data.repository.KeyboardTheme
import kotlin.math.min

/**
 * Gestor desacoplado para la vista previa de teclas (Key Preview) al estilo Gboard / Material 3.
 *
 * Utiliza una única ventana flotante (PopupWindow) de alto rendimiento para evitar llamados
 * innecesarios a invalidate() en la vista principal del teclado.
 */
/**
 * Gestor desacoplado para la vista previa de teclas (Key Preview) al estilo Gboard / Material 3.
 *
 * Utiliza un pool de ventanas flotantes (PopupWindow) de alto rendimiento para soportar
 * escritura rápida multitáctil (ej. alternar pulgares rápido como "jajaja") simultáneamente.
 */
class KeyPreviewManager(private val context: Context) {

    private class PreviewHolder(val context: Context) {
        var popupWindow: PopupWindow? = null
        var previewView: KeyPreviewView? = null
        var exitAnimator: ValueAnimator? = null
        var activeKey: KeyboardLayoutEngine.Key? = null
        var activePointerId: Int = -1

        fun dismissInternal() {
            try {
                if (popupWindow?.isShowing == true) {
                    popupWindow?.dismiss()
                }
            } catch (_: Exception) {}
            activeKey = null
            activePointerId = -1
        }

        fun cancelExitAnimation() {
            exitAnimator?.removeAllListeners()
            exitAnimator?.cancel()
            exitAnimator = null
        }
    }

    private val pool = mutableListOf<PreviewHolder>()
    private var keyboardRenderer: KeyboardRenderer? = null

    var isEnabled: Boolean = true

    // Parámetros de tema/diseño
    private var popupBgColor: Int = Color.WHITE
    private var popupTextColor: Int = Color.BLACK
    private var popupStrokeColor: Int = Color.TRANSPARENT
    private var popupShadowColor: Int = "#40000000".toColorInt()
    private var typeface: Typeface = Typeface.DEFAULT

    fun updateTheme(renderer: KeyboardRenderer, theme: KeyboardTheme?) {
        this.keyboardRenderer = renderer
        val bg = if (renderer.colorPopupBg != 0) renderer.colorPopupBg else (theme?.popupBackgroundColor ?: Color.WHITE)
        val text = if (renderer.colorPopupText != 0) renderer.colorPopupText else (theme?.popupTextColor ?: Color.BLACK)
        popupBgColor = if (bg != 0) bg else Color.WHITE
        popupTextColor = if (text != 0) text else Color.BLACK
        popupStrokeColor = if (renderer.strokeWidth > 0) renderer.colorStroke else Color.TRANSPARENT
        popupShadowColor = if (renderer.colorPopupShadow != 0) renderer.colorPopupShadow else "#40000000".toColorInt()
        typeface = renderer.sharedTypeface
        pool.forEach { holder ->
            holder.previewView?.updateTheme(popupBgColor, popupTextColor, popupStrokeColor, popupShadowColor, typeface, renderer)
        }
    }

    /**
     * Muestra o traslada suavemente la vista previa para la tecla pulsada, soportando punteros multitáctiles.
     */
    fun showPreview(
        parentView: View,
        key: KeyboardLayoutEngine.Key,
        keyWindowX: Float,
        keyWindowY: Float,
        label: String,
        isRect: Boolean,
        popupScale: Float = 1.0f,
        pointerId: Int = 0
    ) {
        if (!isEnabled || key.type != KeyboardLayoutEngine.KeyType.CHAR || label.isEmpty()) {
            dismissPreviewForPointer(pointerId, immediate = true)
            return
        }

        // Buscar un holder asignado a este pointerId o a esta tecla
        var holder = pool.find { it.activePointerId == pointerId || it.activeKey == key }
        if (holder == null) {
            // Buscar uno inactivo
            holder = pool.find { it.popupWindow?.isShowing != true }
            if (holder == null) {
                if (pool.size < 4) {
                    holder = PreviewHolder(context)
                    pool.add(holder)
                } else {
                    holder = pool.first() // Reutilizar
                }
            }
        }

        if (holder.activeKey != null && holder.activeKey != key && holder.popupWindow?.isShowing == true) {
            holder.dismissInternal()
        }

        holder.activeKey = key
        holder.activePointerId = pointerId
        holder.cancelExitAnimation()

        val prx = (key.rx * 1.35f * popupScale).coerceAtLeast(36f)
        val pry = (key.ry * 1.35f * popupScale).coerceAtLeast(36f)

        val w = (prx * 2.2f).toInt()
        val h = (pry * 2.2f).toInt()

        var x = (keyWindowX - w / 2f).toInt()
        val y = (keyWindowY - key.ry * (0.2f + 0.8f * popupScale) - h).toInt()

        val screenWidth = context.resources.displayMetrics.widthPixels
        if (x < 8) x = 8
        if (x + w > screenWidth - 8) x = screenWidth - w - 8

        if (holder.popupWindow == null) {
            val v = KeyPreviewView(context)
            holder.previewView = v
            holder.popupWindow = PopupWindow(
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

        holder.previewView?.apply {
            updateTheme(popupBgColor, popupTextColor, popupStrokeColor, popupShadowColor, typeface, keyboardRenderer)
            setPreviewData(label, isRect, prx, pry)
            alpha = 1.0f
            scaleX = 1.0f
            scaleY = 1.0f
            requestLayout()
        }

        holder.popupWindow?.width = w
        holder.popupWindow?.height = h

        if (holder.popupWindow?.isShowing == true) {
            holder.popupWindow?.update(x, y, w, h)
            holder.previewView?.invalidate()
        } else {
            try {
                val anchor = parentView.rootView ?: parentView
                holder.popupWindow?.showAtLocation(anchor, Gravity.NO_GRAVITY, x, y)
            } catch (e: Exception) {
                android.util.Log.e("HexKB", "Error showing key preview", e)
            }
        }
    }

    /**
     * Oculta el preview asignado a un puntero táctil con animación suave de desvanecimiento (~60ms).
     */
    fun dismissPreviewForPointer(pointerId: Int = 0, immediate: Boolean = false) {
        val holdersToDismiss = pool.filter { it.activePointerId == pointerId && it.popupWindow?.isShowing == true }
        if (holdersToDismiss.isEmpty()) return

        holdersToDismiss.forEach { holder ->
            holder.cancelExitAnimation()

            if (immediate) {
                holder.dismissInternal()
            } else {
                val v = holder.previewView ?: run { holder.dismissInternal(); return@forEach }

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
                            holder.dismissInternal()
                        }
                    })
                }
                holder.exitAnimator = anim
                anim.start()
            }
        }
    }

    fun dismissPreviewForKey(key: KeyboardLayoutEngine.Key?, immediate: Boolean = false) {
        if (key == null) return
        val holdersToDismiss = pool.filter { it.activeKey == key && it.popupWindow?.isShowing == true }
        holdersToDismiss.forEach { holder ->
            holder.cancelExitAnimation()
            if (immediate) {
                holder.dismissInternal()
            } else {
                val v = holder.previewView ?: run { holder.dismissInternal(); return@forEach }
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
                            holder.dismissInternal()
                        }
                    })
                }
                holder.exitAnimator = anim
                anim.start()
            }
        }
    }

    fun dismissPreview(immediate: Boolean = false) {
        dismissPreviewForPointer(0, immediate)
    }

    fun dismissAllPreviews(immediate: Boolean = false) {
        pool.forEach { holder ->
            if (holder.popupWindow?.isShowing == true) {
                holder.cancelExitAnimation()
                if (immediate) {
                    holder.dismissInternal()
                } else {
                    val v = holder.previewView ?: run { holder.dismissInternal(); return@forEach }
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
                                holder.dismissInternal()
                            }
                        })
                    }
                    holder.exitAnimator = anim
                    anim.start()
                }
            }
        }
    }

    val isShowing: Boolean get() = pool.any { it.popupWindow?.isShowing == true }

    /**
     * Vista interna superpuesta para dibujar la silueta adaptativa Material 3 y el texto del preview.
     */
    private class KeyPreviewView(context: Context) : View(context) {

        private var label: String = ""
        private var isRect: Boolean = false
        private var prx: Float = 0f
        private var pry: Float = 0f
        private var renderer: KeyboardRenderer? = null

        private val path = Path()
        private val rectF = RectF()

        private val pBg = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        private val pText = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
        private val pStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 2f }
        private val pShadow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            maskFilter = android.graphics.BlurMaskFilter(8f, android.graphics.BlurMaskFilter.Blur.NORMAL)
        }

        fun updateTheme(bgColor: Int, textColor: Int, strokeColor: Int, shadowColor: Int, tf: Typeface, renderer: KeyboardRenderer? = null) {
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
                val cr = min(prx, pry) * 0.60f
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

