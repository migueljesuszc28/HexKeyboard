package com.example.hexkeyboard.ui.settings.themes

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.net.Uri
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import kotlin.math.max
import kotlin.math.min

class CropImageView(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {

    private var bitmap: Bitmap? = null
    private val matrix = Matrix()
    private val inverseMatrix = Matrix()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

    private var cropRect = RectF()

    private val scaleDetector =
        ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                matrix.postScale(
                    detector.scaleFactor,
                    detector.scaleFactor,
                    detector.focusX,
                    detector.focusY
                )
                invalidate()
                return true
            }
        })

    private val gestureDetector =
        GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
            override fun onScroll(
                e1: MotionEvent?,
                e2: MotionEvent,
                distanceX: Float,
                distanceY: Float
            ): Boolean {
                matrix.postTranslate(-distanceX, -distanceY)
                invalidate()
                return true
            }

            override fun onSingleTapUp(e: MotionEvent): Boolean {
                performClick()
                return true
            }
        })

    fun setImageUri(uri: Uri) {
        try {
            val inputStream = context.contentResolver.openInputStream(uri)
            val original = BitmapFactory.decodeStream(inputStream)
            bitmap = original
            centerImage()
            invalidate()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun setCropRect(rect: RectF) {
        cropRect = rect
        centerImage()
        invalidate()
    }

    private fun centerImage() {
        val b = bitmap ?: return
        if (width == 0 || height == 0 || cropRect.isEmpty) return

        matrix.reset()
        val scale = max(cropRect.width() / b.width, cropRect.height() / b.height)
        matrix.postScale(scale, scale)
        val dx = cropRect.left + (cropRect.width() - b.width * scale) / 2f
        val dy = cropRect.top + (cropRect.height() - b.height * scale) / 2f
        matrix.postTranslate(dx, dy)
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        if (changed) centerImage()
    }

    override fun onDraw(canvas: Canvas) {
        val b = bitmap ?: return
        canvas.drawBitmap(b, matrix, paint)

        if (!cropRect.isEmpty) {
            val oldColor = paint.color
            val oldStyle = paint.style

            // Draw dim overlay (Darker as in GBoard)
            paint.color = Color.parseColor("#AA000000")
            paint.style = Paint.Style.FILL

            // Top
            canvas.drawRect(0f, 0f, width.toFloat(), cropRect.top, paint)
            // Bottom
            canvas.drawRect(0f, cropRect.bottom, width.toFloat(), height.toFloat(), paint)
            // Left
            canvas.drawRect(0f, cropRect.top, cropRect.left, cropRect.bottom, paint)
            // Right
            canvas.drawRect(cropRect.right, cropRect.top, width.toFloat(), cropRect.bottom, paint)

            paint.color = oldColor
            paint.style = oldStyle
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        scaleDetector.onTouchEvent(event)
        gestureDetector.onTouchEvent(event)
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    fun getCroppedBitmap(): Bitmap? {
        val b = bitmap ?: return null
        if (cropRect.isEmpty) return null

        matrix.invert(inverseMatrix)

        val mappedRect = RectF()
        inverseMatrix.mapRect(mappedRect, cropRect)

        // Ensure within bitmap bounds
        val left = max(0f, mappedRect.left).toInt()
        val top = max(0f, mappedRect.top).toInt()
        val right = min(b.width.toFloat(), mappedRect.right).toInt()
        val bottom = min(b.height.toFloat(), mappedRect.bottom).toInt()

        if (right <= left || bottom <= top) return null

        return Bitmap.createBitmap(b, left, top, right - left, bottom - top)
    }
}