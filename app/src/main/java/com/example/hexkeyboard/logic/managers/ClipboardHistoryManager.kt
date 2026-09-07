package com.example.hexkeyboard.logic.managers

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.datastore.preferences.core.edit
import com.example.hexkeyboard.data.repository.ThemeUtils
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

@Serializable
data class ClipboardItem(
    val text: String = "",
    val imageUri: String? = null,
    val mimeType: String? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val isPinned: Boolean = false,
) {
    val isImage: Boolean get() = !imageUri.isNullOrEmpty()
}

object ClipboardHistoryManager {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    suspend fun getHistoryRaw(context: Context): List<ClipboardItem> {
        val prefs = ThemeUtils.getDataStore(context).data.first()
        val jsonString = prefs[ThemeUtils.CLIPBOARD_HISTORY] ?: return emptyList()
        return try {
            json.decodeFromString<List<ClipboardItem>>(jsonString)
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun getHistoryFlow(context: Context): Flow<List<ClipboardItem>> {
        return ThemeUtils.getDataStore(context).data.map { prefs ->
            val jsonString = prefs[ThemeUtils.CLIPBOARD_HISTORY] ?: return@map emptyList()
            try {
                val fullList = json.decodeFromString<List<ClipboardItem>>(jsonString)
                val autoDelete = prefs[ThemeUtils.CLIPBOARD_AUTO_DELETE] ?: true
                if (!autoDelete) {
                    fullList
                } else {
                    val expiryHoursStr = prefs[ThemeUtils.CLIPBOARD_EXPIRY_HOURS] ?: "1"
                    val expiryHours = expiryHoursStr.replace(',', '.').toDoubleOrNull() ?: 1.0
                    val expiryMillis = (expiryHours * 3600000).toLong()
                    val now = System.currentTimeMillis()
                    
                    fullList.filter { it.isPinned || (now - it.timestamp) < expiryMillis }
                }
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

    fun saveImageToCache(context: Context, sourceUri: Uri, mimeType: String): Uri? {
        return try {
            val imagesDir = File(context.filesDir, "clipboard_images")
            if (!imagesDir.exists()) imagesDir.mkdirs()

            val ext = when (mimeType) {
                "image/png" -> "png"
                "image/webp" -> "webp"
                "image/gif" -> "gif"
                else -> "jpg"
            }
            val fileName = "clip_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(6)}.$ext"
            val destFile = File(imagesDir, fileName)

            context.contentResolver.openInputStream(sourceUri)?.use { input ->
                FileOutputStream(destFile).use { output ->
                    input.copyTo(output)
                }
            }

            FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                destFile
            )
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    suspend fun addItem(context: Context, text: String) {
        if (text.isBlank()) return
        val currentHistory = getHistory(context).toMutableList()
        
        // Remove existing to move to top
        currentHistory.removeAll { !it.isImage && it.text == text }
        
        currentHistory.add(0, ClipboardItem(text = text))
        
        // Limit history size (e.g., 50 items)
        val limitedHistory = currentHistory.take(50)
        saveHistory(context, limitedHistory)
        
        // Clean up expired items if auto-delete is enabled
        cleanUpExpiredItems(context)
    }

    suspend fun addImageItem(context: Context, imageUri: Uri, mimeType: String, caption: String = "") {
        val currentHistory = getHistory(context).toMutableList()
        val uriString = imageUri.toString()
        
        if (currentHistory.firstOrNull()?.imageUri == uriString) return
        
        currentHistory.removeAll { it.imageUri == uriString }
        
        val newItem = ClipboardItem(
            text = caption,
            imageUri = uriString,
            mimeType = mimeType
        )
        currentHistory.add(0, newItem)
        
        val limitedHistory = currentHistory.take(50)
        saveHistory(context, limitedHistory)
        cleanUpExpiredItems(context)
    }

    suspend fun deleteItem(context: Context, item: ClipboardItem) {
        val currentHistory = getHistory(context).toMutableList()
        currentHistory.removeAll { 
            (it.text == item.text) && (it.timestamp == item.timestamp) && (it.imageUri == item.imageUri) 
        }
        saveHistory(context, currentHistory)

        if (!item.imageUri.isNullOrEmpty()) {
            try {
                val uri = Uri.parse(item.imageUri)
                if (uri.scheme == "content") {
                    context.contentResolver.delete(uri, null, null)
                }
            } catch (_: Exception) {}
        }
    }

    suspend fun togglePin(context: Context, item: ClipboardItem) {
        val currentHistory = getHistoryRaw(context).map {
            if ((it.text == item.text) && (it.timestamp == item.timestamp) && (it.imageUri == item.imageUri)) {
                it.copy(isPinned = !it.isPinned)
            } else it
        }
        saveHistory(context, currentHistory)
    }

    suspend fun updateItem(context: Context, oldItem: ClipboardItem, newText: String) {
        val currentHistory = getHistoryRaw(context).toMutableList()
        val index = currentHistory.indexOfFirst { 
            (it.text == oldItem.text) && (it.timestamp == oldItem.timestamp) && (it.imageUri == oldItem.imageUri) 
        }
        if (index != -1) {
            currentHistory[index] = currentHistory[index].copy(text = newText)
            saveHistory(context, currentHistory)
        }
    }

    suspend fun cleanUpExpiredItems(context: Context) {
        val dataStore = ThemeUtils.getDataStore(context)
        val prefs = dataStore.data.first()
        
        if (!(prefs[ThemeUtils.CLIPBOARD_AUTO_DELETE] ?: true)) return

        val expiryHoursStr = prefs[ThemeUtils.CLIPBOARD_EXPIRY_HOURS] ?: "1"
        val expiryHours = expiryHoursStr.replace(',', '.').toDoubleOrNull() ?: 1.0
        val expiryMillis = (expiryHours * 3600000).toLong()
        
        val now = System.currentTimeMillis()
        val currentHistory = getHistoryRaw(context)
        val expiredItems = currentHistory.filter { 
            !it.isPinned && (now - it.timestamp) >= expiryMillis
        }
        val newHistory = currentHistory.filter { 
            it.isPinned || (now - it.timestamp) < expiryMillis
        }
        
        if (newHistory.size != currentHistory.size) {
            saveHistory(context, newHistory)
            expiredItems.forEach { item ->
                if (!item.imageUri.isNullOrEmpty()) {
                    try {
                        val uri = Uri.parse(item.imageUri)
                        if (uri.scheme == "content") {
                            context.contentResolver.delete(uri, null, null)
                        }
                    } catch (_: Exception) {}
                }
            }
        }
    }
}
