package com.example.hexkeyboard.data.repository

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import androidx.core.content.edit
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.example.hexkeyboard.data.model.CredentialItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.BufferedReader
import java.io.InputStreamReader

object CredentialsManager {

    private const val PREFS_FILENAME = "secure_credentials_prefs"

    private fun getPrefs(context: Context): SharedPreferences {
        val masterKey = MasterKey.Builder(context, MasterKey.DEFAULT_MASTER_KEY_ALIAS)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        return EncryptedSharedPreferences.create(
            context,
            PREFS_FILENAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    suspend fun saveCredential(context: Context, item: CredentialItem) = withContext(Dispatchers.IO) {
        val prefs = getPrefs(context)
        prefs.edit { putString(item.id, Json.encodeToString(item)) }
    }

    suspend fun getCredential(context: Context, id: String): CredentialItem? = withContext(Dispatchers.IO) {
        val prefs = getPrefs(context)
        prefs.getString(id, null)?.let {
            try {
                Json.decodeFromString<CredentialItem>(it)
            } catch (_: Exception) {
                null
            }
        }
    }

    suspend fun deleteCredential(context: Context, id: String) = withContext(Dispatchers.IO) {
        val prefs = getPrefs(context)
        prefs.edit { remove(id) }
    }

    suspend fun getAllCredentials(context: Context): List<CredentialItem> = withContext(Dispatchers.IO) {
        val prefs = getPrefs(context)
        prefs.all.values
            .filterIsInstance<String>()
            .mapNotNull {
                try {
                    Json.decodeFromString<CredentialItem>(it)
                } catch (_: Exception) {
                    null
                }
            }
            .sortedByDescending { it.timestamp }
    }

    private fun parseCsvLine(line: String): List<String> {
        val result = mutableListOf<String>()
        val sb = StringBuilder()
        var inQuotes = false
        for (char in line) {
            when (char) {
                '"' -> inQuotes = !inQuotes
                ',' -> if (!inQuotes) {
                    result.add(sb.toString().trim(' ', '"'))
                    sb.clear()
                } else {
                    sb.append(char)
                }
                else -> sb.append(char)
            }
        }
        result.add(sb.toString().trim(' ', '"'))
        return result
    }

    private fun extractDomain(url: String): String {
        return try {
            val clean = url.removePrefix("https://").removePrefix("http://").removePrefix("www.")
            clean.substringBefore("/").substringBefore("?")
        } catch (_: Exception) {
            url
        }
    }

    suspend fun importFromCsv(context: Context, uri: Uri): Result<Int> = withContext(Dispatchers.IO) {
        try {
            var importedCount = 0
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                BufferedReader(InputStreamReader(inputStream)).use { reader ->
                    val lines = reader.readLines()
                    if (lines.isEmpty()) return@use Result.success(0)

                    // Detectar si la primera línea es la cabecera
                    var startIndex = 0
                    val header = lines[0].lowercase()
                    if (header.contains("name") || header.contains("url") || header.contains("username") || header.contains("password")) {
                        startIndex = 1
                    }

                    for (i in startIndex until lines.size) {
                        val line = lines[i]
                        if (line.isBlank()) continue

                        val parts = parseCsvLine(line)

                        // Google Chrome format: name, url, username, password (4 columns)
                        // Generic format: title, username, password (3 columns)
                        val title: String
                        val username: String
                        val password: String

                        if (parts.size >= 4) {
                            val rawName = parts[0].ifBlank { extractDomain(parts[1]) }
                            title = rawName.ifBlank { "Sitio web" }
                            username = parts[2]
                            password = parts[3]
                        } else if (parts.size >= 3) {
                            title = parts[0]
                            username = parts[1]
                            password = parts[2]
                        } else {
                            continue
                        }

                        if (title.isEmpty() && username.isEmpty() && password.isEmpty()) continue

                        val item = CredentialItem(
                            title = title,
                            username = username,
                            password = password
                        )

                        val prefs = getPrefs(context)
                        prefs.edit { putString(item.id, Json.encodeToString(item)) }
                        importedCount++
                    }
                }
            }
            Result.success(importedCount)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
