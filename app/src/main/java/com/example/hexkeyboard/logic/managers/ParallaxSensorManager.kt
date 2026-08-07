package com.example.hexkeyboard.logic.managers

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.abs

class ParallaxSensorManager(context: Context) : SensorEventListener {
    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    private val rotationSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)

    private val _parallaxOffset = MutableStateFlow(Offset(0f, 0f))
    val parallaxOffset: StateFlow<Offset> = _parallaxOffset.asStateFlow()

    private var parallaxX = 0f
    private var parallaxY = 0f
    private var targetParallaxX = 0f
    private var targetParallaxY = 0f

    data class Offset(val x: Float, val y: Float)

    fun start() {
        rotationSensor?.let {
            sensorManager?.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
        }
    }

    fun stop() {
        sensorManager?.unregisterListener(this)
        _parallaxOffset.value = Offset(0f, 0f)
        parallaxX = 0f
        parallaxY = 0f
        targetParallaxX = 0f
        targetParallaxY = 0f
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type == Sensor.TYPE_ROTATION_VECTOR) {
            val rotationMatrix = FloatArray(9)
            SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
            val orientation = FloatArray(3)
            SensorManager.getOrientation(rotationMatrix, orientation)

            val rawRoll = orientation[2]
            val rawPitch = orientation[1] + 0.8f // Average hand tilt

            // Sensitivity
            targetParallaxX = (rawRoll * 1.2f).coerceIn(-1f, 1f)
            targetParallaxY = (rawPitch * 1.2f).coerceIn(-1f, 1f)

            // Smoothing
            val newX = parallaxX * 0.85f + targetParallaxX * 0.15f
            val newY = parallaxY * 0.85f + targetParallaxY * 0.15f

            // Solo actualizar si el cambio es perceptible (> 0.005) para reducir emisiones
            if (abs(newX - parallaxX) > 0.005f || abs(newY - parallaxY) > 0.005f) {
                parallaxX = newX
                parallaxY = newY
                _parallaxOffset.value = Offset(parallaxX, parallaxY)
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}