package com.example.hexkeyboard.logic.managers

import android.content.Context
import android.media.AudioManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.preference.PreferenceManager

object FeedbackManager {
    private var vibrator: Vibrator? = null
    private var audioManager: AudioManager? = null
    
    private var vibrationEnabled: Boolean = true
    private var vibrationIntensity: Int = 30
    private var soundEnabled: Boolean = true
    private var soundVolume: Float = 0.5f
    private var isInitialized = false

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
        
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        updateSettings(prefs)
        
        prefs.registerOnSharedPreferenceChangeListener { p, key ->
            if (key == null || key.startsWith("keyboard_vibration") || key.startsWith("keyboard_sound")) {
                updateSettings(p)
            }
        }
        isInitialized = true
    }

    private fun updateSettings(prefs: android.content.SharedPreferences) {
        vibrationEnabled = prefs.getBoolean("keyboard_vibration", true)
        vibrationIntensity = try {
            prefs.getInt("keyboard_vibration_intensity", 30)
        } catch (e: Exception) {
            prefs.getString("keyboard_vibration_intensity", "30")?.toIntOrNull() ?: 30
        }
        
        soundEnabled = prefs.getBoolean("keyboard_sound", true)
        soundVolume = try {
            prefs.getInt("keyboard_sound_volume", 50) / 100f
        } catch (e: Exception) {
            (prefs.getString("keyboard_sound_volume", "50")?.toIntOrNull() ?: 50) / 100f
        }
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
