package com.example.hexkeyboard.logic.managers

import android.content.Context
import android.media.AudioManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.example.hexkeyboard.data.repository.ThemeUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

object FeedbackManager {
    enum class HapticType {
        KEY_CLICK,
        LONG_PRESS,
        DELETE,
        TICK
    }

    private var vibrator: Vibrator? = null
    private var audioManager: AudioManager? = null
    
    private var vibrationEnabled: Boolean = true
    private var vibrationIntensity: Int = 30
    private var soundEnabled: Boolean = true
    private var soundVolume: Float = 0.5f
    private var isInitialized = false
    
    private val managerScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private var primitiveClickSupported = false
    private var primitiveTickSupported = false
    private var primitiveLowTickSupported = false

    fun initialize(context: Context) {
        if (isInitialized) return
        val appContext = context.applicationContext
        
        vibrator = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            val vibratorManager = appContext.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            vibratorManager?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            appContext.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
        audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            vibrator?.let { v ->
                val supported = v.arePrimitivesSupported(
                    VibrationEffect.Composition.PRIMITIVE_CLICK,
                    VibrationEffect.Composition.PRIMITIVE_TICK,
                    VibrationEffect.Composition.PRIMITIVE_LOW_TICK
                )
                if (supported.size >= 3) {
                    primitiveClickSupported = supported[0]
                    primitiveTickSupported = supported[1]
                    primitiveLowTickSupported = supported[2]
                }
            }
        }
        
        managerScope.launch {
            ThemeUtils.getDataStore(appContext).data.collectLatest { prefs ->
                vibrationEnabled = prefs[ThemeUtils.KEYBOARD_VIBRATION] ?: true
                vibrationIntensity = prefs[ThemeUtils.KEYBOARD_VIBRATION_INTENSITY] ?: 30
                
                soundEnabled = prefs[ThemeUtils.KEYBOARD_SOUND] ?: true
                val volumeInt = prefs[ThemeUtils.KEYBOARD_SOUND_VOLUME] ?: 50
                soundVolume = volumeInt / 100f
            }
        }
        
        isInitialized = true
    }

    fun triggerFeedback(context: Context, type: HapticType = HapticType.KEY_CLICK) {
        if (!isInitialized) initialize(context)
        triggerVibration(context, type)
        triggerSound(context)
    }

    fun triggerVibration(context: Context, type: HapticType = HapticType.KEY_CLICK) {
        if (!isInitialized) initialize(context)
        
        if ((!vibrationEnabled) || (vibrationIntensity <= 0)) return
        
        val v = vibrator ?: return
        
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            val effect = createModernEffect(v, type)
            v.vibrate(effect)
        } else {
            executeLegacyVibration(v, type)
        }
    }

    private fun executeLegacyVibration(v: Vibrator, type: HapticType) {
        val duration = when(type) {
            HapticType.KEY_CLICK -> vibrationIntensity.toLong()
            HapticType.LONG_PRESS -> (vibrationIntensity * 1.5).toLong()
            HapticType.DELETE -> (vibrationIntensity * 0.8).toLong().coerceAtLeast(1)
            HapticType.TICK -> (vibrationIntensity * 0.5).toLong().coerceAtLeast(1)
        }
        val amplitude = (vibrationIntensity * 2.55).toInt().coerceIn(1, 255)
        v.vibrate(VibrationEffect.createOneShot(duration, amplitude))
    }

    @androidx.annotation.RequiresApi(android.os.Build.VERSION_CODES.S)
    private fun createModernEffect(v: Vibrator, type: HapticType): VibrationEffect {
        val baseIntensity = (vibrationIntensity / 100f)
        val adjustedIntensity = (0.3f + baseIntensity * 0.7f).coerceIn(0.1f, 1f)

        val isSupported = when (type) {
            HapticType.KEY_CLICK, HapticType.LONG_PRESS -> primitiveClickSupported
            HapticType.DELETE -> primitiveTickSupported
            HapticType.TICK -> primitiveLowTickSupported
        }

        if (!isSupported) {
            return createFallbackEffect(type)
        }

        val composition = VibrationEffect.startComposition()
        when (type) {
            HapticType.KEY_CLICK -> {
                composition.addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, adjustedIntensity)
            }
            HapticType.LONG_PRESS -> {
                composition.addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, (adjustedIntensity * 1.2f).coerceAtMost(1f))
                if (primitiveTickSupported) {
                    composition.addPrimitive(VibrationEffect.Composition.PRIMITIVE_TICK, adjustedIntensity, 20)
                }
            }
            HapticType.DELETE -> {
                composition.addPrimitive(VibrationEffect.Composition.PRIMITIVE_TICK, adjustedIntensity)
            }
            HapticType.TICK -> {
                composition.addPrimitive(VibrationEffect.Composition.PRIMITIVE_LOW_TICK, adjustedIntensity)
            }
        }
        return composition.compose()
    }

    private fun createFallbackEffect(type: HapticType): VibrationEffect {
        val duration = when(type) {
            HapticType.KEY_CLICK -> vibrationIntensity.toLong()
            HapticType.LONG_PRESS -> (vibrationIntensity * 1.5).toLong()
            HapticType.DELETE -> (vibrationIntensity * 0.8).toLong().coerceAtLeast(1)
            HapticType.TICK -> (vibrationIntensity * 0.5).toLong().coerceAtLeast(1)
        }
        val amplitude = (vibrationIntensity * 2.55).toInt().coerceIn(1, 255)
        return VibrationEffect.createOneShot(duration, amplitude)
    }

    fun triggerSound(context: Context) {
        if (!isInitialized) initialize(context)
        if (!soundEnabled) return
        
        audioManager?.playSoundEffect(AudioManager.FX_KEYPRESS_STANDARD, soundVolume)
    }
}
