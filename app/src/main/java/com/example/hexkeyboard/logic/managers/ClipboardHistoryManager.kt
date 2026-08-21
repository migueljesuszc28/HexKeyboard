package com.example.hexkeyboard.logic.managers

import android.content.Context
import androidx.datastore.preferences.core.edit
import com.example.hexkeyboard.data.repository.ThemeUtils
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.concurrent.TimeUnit

@Serializable
data class ClipboardItem(
    val text: String,
    val timestamp: Long = System.currentTimeMillis(),
    val isPinned: Boolean = false,
)

object ClipboardHistoryManager {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun getHistoryFlow(context: Context): Flow<List<ClipboardItem>> {
        return ThemeUtils.getDataStore(context).data.map { prefs ->
            val jsonString = prefs[ThemeUtils.CLIPBOARD_HISTORY] ?: return@map emptyList()
            try {
                json.decodeFromString<List<ClipboardItem>>(jsonString)
            } catch (_: Exception) {
                emptyList()
            }
        }
    }

    fun getHistory(context: Context): List<ClipboardItem> = runBlocking {
        getHistoryFlow(context).first()
    }

    suspend fun saveHistory(context: Context, history: List<ClipboardItem>) {
        ThemeUtils.getDataStore(context).edit { prefs ->
            val jsonString = json.encodeToString(history)
            prefs[ThemeUtils.CLIPBOARD_HISTORY] = jsonString
        }
    }

    suspend fun addItem(context: Context, text: String) {
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

    suspend fun deleteItem(context: Context, item: ClipboardItem) {
        val currentHistory = getHistory(context).toMutableList()
        currentHistory.removeAll { (it.text == item.text) && (it.timestamp == item.timestamp) }
        saveHistory(context, currentHistory)
    }

    suspend fun togglePin(context: Context, item: ClipboardItem) {
        val currentHistory = getHistory(context).map {
            if ((it.text == item.text) && (it.timestamp == item.timestamp)) {
                it.copy(isPinned = !it.isPinned)
            } else it
        }
        saveHistory(context, currentHistory)
    }

    suspend fun cleanUpExpiredItems(context: Context) {
        val dataStore = ThemeUtils.getDataStore(context)
        val prefs = dataStore.data.first()
        
        if (!(prefs[ThemeUtils.CLIPBOARD_AUTO_DELETE] ?: true)) return

        val expiryHoursStr = prefs[ThemeUtils.CLIPBOARD_EXPIRY_HOURS] ?: "1"
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
