package com.example.hexkeyboard.logic.managers

import android.content.Context
import android.content.SharedPreferences
import androidx.preference.PreferenceManager
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.util.concurrent.TimeUnit

data class ClipboardItem(
    val text: String,
    val timestamp: Long = System.currentTimeMillis(),
    val isPinned: Boolean = false
)

object ClipboardHistoryManager {
    private const val PREF_HISTORY = "clipboard_history_json"
    private val gson = Gson()

    fun getHistory(context: Context): List<ClipboardItem> {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val json = prefs.getString(PREF_HISTORY, null) ?: return emptyList()
        return try {
            val type = object : com.google.gson.reflect.TypeToken<List<ClipboardItem>>() {}.type
            gson.fromJson(json, type)
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun saveHistory(context: Context, history: List<ClipboardItem>) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val json = gson.toJson(history)
        prefs.edit().putString(PREF_HISTORY, json).apply()
    }

    fun addItem(context: Context, text: String) {
        if (text.isBlank()) return
        val currentHistory = getHistory(context).toMutableList()
        
        // Remove existing to move to top
        currentHistory.removeAll { it.text == text }
        
        currentHistory.add(0, ClipboardItem(text))
        
        // Limit history size (e.g., 50 items)
        val limitedHistory = currentHistory.take(50)
        saveHistory(context, limitedHistory)
        
        // Clean up expired items if auto-delete is enabled
        cleanUpExpiredItems(context)
    }

    fun deleteItem(context: Context, item: ClipboardItem) {
        val currentHistory = getHistory(context).toMutableList()
        currentHistory.removeAll { it.text == item.text && it.timestamp == item.timestamp }
        saveHistory(context, currentHistory)
    }

    fun togglePin(context: Context, item: ClipboardItem) {
        val currentHistory = getHistory(context).map {
            if (it.text == item.text && it.timestamp == item.timestamp) {
                it.copy(isPinned = !it.isPinned)
            } else it
        }
        saveHistory(context, currentHistory)
    }

    fun cleanUpExpiredItems(context: Context) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        if (!prefs.getBoolean("clipboard_auto_delete", true)) return

        val expiryHoursStr = prefs.getString("clipboard_expiry_hours", "1") ?: "1"
        // Asegurar que usamos el punto decimal independientemente del locale
        val expiryHours = expiryHoursStr.replace(',', '.').toDoubleOrNull() ?: 1.0
        val expiryMillis = (expiryHours * 3600000).toLong()
        
        val now = System.currentTimeMillis()
        val currentHistory = getHistory(context)
        val newHistory = currentHistory.filter { 
            it.isPinned || (now - it.timestamp) < expiryMillis
        }
        
        if (newHistory.size != currentHistory.size) {
            saveHistory(context, newHistory)
        }
    }
}
