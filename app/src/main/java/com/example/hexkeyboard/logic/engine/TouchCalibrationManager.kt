package com.example.hexkeyboard.logic.engine

import android.graphics.PointF
import kotlin.math.abs

/**
 * Gestor de calibración biomecánica táctil adaptativa estilo Gboard.
 * Registra los sesgos sistemáticos de los toques del usuario respecto al centro
 * geométrico de las teclas y ajusta dinámicamente las coordenadas para
 * personalizar la precisión táctil (ej. toques desviados hacia la derecha con el pulgar).
 */
class TouchCalibrationManager {

    companion object {
        private const val ALPHA = 0.05f // Factor de suavizado exponencial (EMA)
        private const val MAX_OFFSET_PX = 35.0f // Límite máximo de ajuste en píxeles
    }

    @Volatile
    var offsetX: Float = 0.0f
        private set

    @Volatile
    var offsetY: Float = 0.0f
        private set

    /**
     * Ajusta las coordenadas brutas del toque del usuario aplicando el sesgo calibrado.
     */
    fun calibrate(x: Float, y: Float): PointF {
        return PointF(x - offsetX, y - offsetY)
    }

    /**
     * Registra una pulsación confirmada y actualiza la estimación del sesgo del usuario.
     */
    fun registerTouchSample(touchX: Float, touchY: Float, keyCenterX: Float, keyCenterY: Float) {
        val dx = touchX - keyCenterX
        val dy = touchY - keyCenterY

        // Ignorar muestras extremadamente alejadas (posibles toques accidentales)
        if (abs(dx) > MAX_OFFSET_PX * 2.5f || abs(dy) > MAX_OFFSET_PX * 2.5f) return

        val newOffsetX = (1f - ALPHA) * offsetX + ALPHA * dx
        val newOffsetY = (1f - ALPHA) * offsetY + ALPHA * dy

        offsetX = newOffsetX.coerceIn(-MAX_OFFSET_PX, MAX_OFFSET_PX)
        offsetY = newOffsetY.coerceIn(-MAX_OFFSET_PX, MAX_OFFSET_PX)
    }

    /**
     * Restablece la calibración al estado inicial sin sesgo.
     */
    fun reset() {
        offsetX = 0.0f
        offsetY = 0.0f
    }
}
