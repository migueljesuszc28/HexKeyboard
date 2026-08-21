package com.example.hexkeyboard.logic.managers

import android.content.Context
import android.media.AudioManager
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
    private var vibrator: Vibrator? = null
    private var audioManager: AudioManager? = null
    
    private var vibrationEnabled: Boolean = true
    private var vibrationIntensity: Int = 30
    private var soundEnabled: Boolean = true
    private var soundVolume: Float = 0.5f
    private var isInitialized = false
    
    private val managerScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    fun initialize(context: Context) {
        if (isInitialized) return
        
        vibrator = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            vibratorManager?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
        audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        
        managerScope.launch {
            ThemeUtils.getDataStore(context).data.collectLatest { prefs ->
                vibrationEnabled = prefs[ThemeUtils.KEYBOARD_VIBRATION] ?: true
                vibrationIntensity = prefs[ThemeUtils.KEYBOARD_VIBRATION_INTENSITY] ?: 30
                
                soundEnabled = prefs[ThemeUtils.KEYBOARD_SOUND] ?: true
                val volumeInt = prefs[ThemeUtils.KEYBOARD_SOUND_VOLUME] ?: 50
                soundVolume = volumeInt / 100f
            }
        }
        
        isInitialized = true
    }

    fun triggerFeedback(context: Context) {
        if (!isInitialized) initialize(context)
        triggerVibration(context)
        triggerSound(context)
    }

    fun triggerVibration(context: Context) {
        if (!isInitialized) initialize(context)
        if (!vibrationEnabled) return
        
        vibrator?.let { v ->
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                v.vibrate(VibrationEffect.createOneShot(vibrationIntensity.toLong(), (vibrationIntensity * 2.55).toInt().coerceIn(1, 255)))
            } else {
                @Suppress("DEPRECATION")
                v.vibrate(vibrationIntensity.toLong())
            }
        }
    }

    fun triggerSound(context: Context) {
        if (!isInitialized) initialize(context)
        if (!soundEnabled) return
        
        audioManager?.playSoundEffect(AudioManager.FX_KEYPRESS_STANDARD, soundVolume)
    }
}
