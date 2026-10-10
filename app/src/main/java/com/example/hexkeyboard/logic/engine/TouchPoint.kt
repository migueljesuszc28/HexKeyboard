package com.example.hexkeyboard.logic.engine

/**
 * Representa una coordenada táctil continua (x, y) capturada en la pantalla
 * durante la pulsación de una tecla por el usuario.
 */
data class TouchPoint(
    val char: Char,
    val x: Float,
    val y: Float,
    val timestamp: Long = System.currentTimeMillis()
)
