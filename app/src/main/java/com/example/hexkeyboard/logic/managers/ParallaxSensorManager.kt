package com.example.hexkeyboard.logic.managers

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.os.Process
import android.view.Choreographer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.tanh

class ParallaxSensorManager(context: Context) : SensorEventListener {

    data class Offset(val x: Float, val y: Float)

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager

    // Preferred game rotation vector sensor (unaffected by magnetic interference)
    private val rotationSensor: Sensor? = sensorManager?.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR)
        ?: sensorManager?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)

    private val _parallaxOffset = MutableStateFlow(Offset(0f, 0f))
    val parallaxOffset: StateFlow<Offset> = _parallaxOffset.asStateFlow()

    // Pre-allocated arrays to eliminate GC allocations on sensor callbacks
    private val rotationMatrix = FloatArray(9)
    private val orientation = FloatArray(3)

    // Dedicated background thread for processing sensor callbacks
    private var sensorThread: HandlerThread? = null
    private var sensorHandler: Handler? = null

    // Target values computed from sensor (thread-safe volatile read/write)
    @Volatile private var targetX = 0f
    @Volatile private var targetY = 0f

    // Current interpolated offset values (updated on main/Choreographer thread)
    private var currentX = 0f
    private var currentY = 0f

    // Dynamic adaptive baseline for pitch & roll
    private var basePitch = 0f
    private var baseRoll = 0f
    private var isBaselineInitialized = false

    // Frame interpolation state
    private var isFrameCallbackScheduled = false
    private var lastFrameTimeNanos = 0L

    private val mainHandler = Handler(Looper.getMainLooper())

    private val frameCallback = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            if (!isFrameCallbackScheduled) return

            if (lastFrameTimeNanos == 0L) {
                lastFrameTimeNanos = frameTimeNanos
                Choreographer.getInstance().postFrameCallback(this)
                return
            }

            val dt = ((frameTimeNanos - lastFrameTimeNanos) / 1_000_000_000f).coerceIn(0.001f, 0.05f)
            lastFrameTimeNanos = frameTimeNanos

            // Frame-rate independent exponential lerp
            val smoothingFactor = 1f - exp(-18f * dt)

            val nextX = currentX + (targetX - currentX) * smoothingFactor
            val nextY = currentY + (targetY - currentY) * smoothingFactor

            currentX = nextX
            currentY = nextY

            _parallaxOffset.value = Offset(currentX, currentY)

            // Auto-pause frame ticker when movement settles to target to conserve CPU/battery
            if (abs(currentX - targetX) < 0.0001f && abs(currentY - targetY) < 0.0001f) {
                currentX = targetX
                currentY = targetY
                _parallaxOffset.value = Offset(currentX, currentY)
                isFrameCallbackScheduled = false
                lastFrameTimeNanos = 0L
            } else {
                Choreographer.getInstance().postFrameCallback(this)
            }
        }
    }

    @Synchronized
    fun start() {
        if (rotationSensor == null) return
        if (sensorThread != null) return

        val thread = HandlerThread("ParallaxSensorThread", Process.THREAD_PRIORITY_BACKGROUND)
        thread.start()
        sensorThread = thread
        val handler = Handler(thread.looper)
        sensorHandler = handler

        isBaselineInitialized = false
        targetX = 0f
        targetY = 0f
        currentX = 0f
        currentY = 0f
        lastFrameTimeNanos = 0L
        _parallaxOffset.value = Offset(0f, 0f)

        sensorManager?.registerListener(this, rotationSensor, SensorManager.SENSOR_DELAY_GAME, handler)
    }

    @Synchronized
    fun stop() {
        sensorManager?.unregisterListener(this)

        sensorHandler?.removeCallbacksAndMessages(null)
        sensorThread?.quitSafely()
        sensorThread = null
        sensorHandler = null

        mainHandler.post {
            if (isFrameCallbackScheduled) {
                isFrameCallbackScheduled = false
                Choreographer.getInstance().removeFrameCallback(frameCallback)
            }
            lastFrameTimeNanos = 0L
            currentX = 0f
            currentY = 0f
            targetX = 0f
            targetY = 0f
            _parallaxOffset.value = Offset(0f, 0f)
        }
    }

    override fun onSensorChanged(event: SensorEvent?) {
        val sensorEvent = event ?: return
        if (sensorEvent.sensor.type != rotationSensor?.type) return

        SensorManager.getRotationMatrixFromVector(rotationMatrix, sensorEvent.values)
        SensorManager.getOrientation(rotationMatrix, orientation)

        val rawPitch = orientation[1]
        val rawRoll = orientation[2]

        if (!isBaselineInitialized) {
            basePitch = rawPitch
            baseRoll = rawRoll
            isBaselineInitialized = true
        } else {
            basePitch = basePitch * 0.992f + rawPitch * 0.008f
            baseRoll = baseRoll * 0.992f + rawRoll * 0.008f
        }

        val deltaRoll = rawRoll - baseRoll
        val deltaPitch = rawPitch - basePitch

        val newTargetX = tanh((deltaRoll * 2.2f).toDouble()).toFloat()
        val newTargetY = tanh((deltaPitch * 2.2f).toDouble()).toFloat()

        targetX = newTargetX
        targetY = newTargetY

        if (!isFrameCallbackScheduled) {
            mainHandler.post {
                if (!isFrameCallbackScheduled) {
                    isFrameCallbackScheduled = true
                    lastFrameTimeNanos = 0L
                    Choreographer.getInstance().postFrameCallback(frameCallback)
                }
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}
