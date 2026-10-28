package com.example.mantiskeyboard.data.repository

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.core.net.toUri
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import android.provider.OpenableColumns
import com.example.mantiskeyboard.data.model.CredentialItem
import com.example.mantiskeyboard.logic.engine.UserDictionary
import com.example.mantiskeyboard.ui.settings.fontselector.FontManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import java.io.*
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

data class BackupExportSummary(
    val credentialsCount: Int,
    val fontsCount: Int,
    val themesCount: Int,
    val preferencesCount: Int,
    val hasUserDict: Boolean,
    val hasLearnedNgrams: Boolean
)

data class BackupImportSummary(
    val credentialsCount: Int,
    val fontsCount: Int,
    val themesCount: Int,
    val preferencesRestored: Boolean,
    val userDictRestored: Boolean,
    val ngramsRestored: Boolean
)

object BackupManager {

    private const val TAG = "BackupManager"
    private const val CREDENTIALS_ENTRY = "credentials.json"
    private const val FONTS_DIR_ENTRY = "fonts/"
    private const val CUSTOM_THEMES_ENTRY = "custom_themes.json"
    private const val THEME_IMAGES_DIR_ENTRY = "theme_images/"
    private const val PREFERENCES_ENTRY = "preferences.json"
    private const val USER_DICT_ENTRY = "user_dict.txt"
    private const val LEARNED_NGRAMS_ENTRY = "learned_ngrams.json"

    suspend fun exportBackup(context: Context, destinationUri: Uri): Result<BackupExportSummary> = withContext(Dispatchers.IO) {
        try {
            var credentialsCount = 0
            var fontsCount = 0
            var themesCount = 0
            var preferencesCount = 0
            var hasUserDict = false
            var hasLearnedNgrams = false

            val outputStream = context.contentResolver.openOutputStream(destinationUri)
                ?: return@withContext Result.failure(IOException("No se pudo abrir el stream de salida para exportar"))

            ZipOutputStream(BufferedOutputStream(outputStream)).use { zipOut ->
                // 1. Exportar Credenciales
                try {
                    val credentials = CredentialsManager.getAllCredentials(context)
                    credentialsCount = credentials.size
                    val credsJson = Json.encodeToString(credentials)
                    zipOut.putNextEntry(ZipEntry(CREDENTIALS_ENTRY))
                    zipOut.write(credsJson.toByteArray(Charsets.UTF_8))
                    zipOut.closeEntry()
                } catch (e: Exception) {
                    Log.e(TAG, "Error exportando credenciales", e)
                }

                // 2. Exportar Fuentes Personalizadas
                try {
                    val fontsDir = FontManager.getCustomFontsDir(context)
                    val fontFiles = FontManager.listCustomFonts(context)
                    fontFiles.forEach { fontFile ->
                        if (fontFile.exists() && fontFile.isFile) {
                            zipOut.putNextEntry(ZipEntry("$FONTS_DIR_ENTRY${fontFile.name}"))
                            fontFile.inputStream().use { input -> input.copyTo(zipOut) }
                            zipOut.closeEntry()
                            fontsCount++
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error exportando fuentes", e)
                }

                // 3. Exportar Temas Creados e Imágenes de Fondo Locales
                try {
                    val customThemes = ThemeUtils.getAllThemes(context).filter { it.id.startsWith("custom_") }
                    themesCount = customThemes.size
                    val exportedThemes = mutableListOf<KeyboardTheme>()

                    customThemes.forEach { theme ->
                        var exportedTheme = theme
                        theme.backgroundImageUri?.let { bgUriStr ->
                            try {
                                val uri = bgUriStr.toUri()
                                val imageFile = if (uri.scheme == "file") {
                                    File(uri.path ?: "")
                                } else {
                                    // Intentar resolver por nombre o en filesDir
                                    val fileName = uri.lastPathSegment ?: "keyboard_bg_${theme.id}.jpg"
                                    File(context.filesDir, fileName)
                                }

                                if (imageFile.exists() && imageFile.isFile) {
                                    val entryName = "$THEME_IMAGES_DIR_ENTRY${imageFile.name}"
                                    zipOut.putNextEntry(ZipEntry(entryName))
                                    imageFile.inputStream().use { input -> input.copyTo(zipOut) }
                                    zipOut.closeEntry()
                                    exportedTheme = theme.copy(backgroundImageUri = "theme_images/${imageFile.name}")
                                } else {
                                    // Intentar leer InputStream directamente si es Content Uri
                                    context.contentResolver.openInputStream(uri)?.use { input ->
                                        val fileName = "keyboard_bg_${theme.id}.jpg"
                                        zipOut.putNextEntry(ZipEntry("$THEME_IMAGES_DIR_ENTRY$fileName"))
                                        input.copyTo(zipOut)
                                        zipOut.closeEntry()
                                        exportedTheme = theme.copy(backgroundImageUri = "theme_images/$fileName")
                                    }
                                }
                            } catch (e: Exception) {
                                Log.e(TAG, "Error exportando imagen de tema ${theme.id}", e)
                            }
                        }
                        exportedThemes.add(exportedTheme)
                    }

                    val themesJson = ThemeUtils.json.encodeToString(exportedThemes)
                    zipOut.putNextEntry(ZipEntry(CUSTOM_THEMES_ENTRY))
                    zipOut.write(themesJson.toByteArray(Charsets.UTF_8))
                    zipOut.closeEntry()
                } catch (e: Exception) {
                    Log.e(TAG, "Error exportando temas", e)
                }

                // 4. Exportar Preferencias de DataStore
                try {
                    val prefsMap = ThemeUtils.getDataStore(context).data.first().asMap()
                    val prefsJsonObject = buildJsonObject {
                        prefsMap.forEach { (key, value) ->
                            when (value) {
                                is String -> put(key.name, value)
                                is Boolean -> put(key.name, value)
                                is Int -> put(key.name, value)
                                is Float -> put(key.name, value)
                                is Long -> put(key.name, value)
                            }
                            preferencesCount++
                        }
                    }

                    val prefsJson = Json.encodeToString(prefsJsonObject)
                    zipOut.putNextEntry(ZipEntry(PREFERENCES_ENTRY))
                    zipOut.write(prefsJson.toByteArray(Charsets.UTF_8))
                    zipOut.closeEntry()
                } catch (e: Exception) {
                    Log.e(TAG, "Error exportando preferencias", e)
                }

                // 5. Exportar Diccionario de Usuario y Patrones Aprendidos
                try {
                    val userDictFile = File(context.filesDir, "user_dict.txt")
                    if (userDictFile.exists() && userDictFile.isFile) {
                        zipOut.putNextEntry(ZipEntry(USER_DICT_ENTRY))
                        userDictFile.inputStream().use { input -> input.copyTo(zipOut) }
                        zipOut.closeEntry()
                        hasUserDict = true
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error exportando user_dict.txt", e)
                }

                try {
                    val learnedNgramsFile = File(context.filesDir, "learned_ngrams.json")
                    if (learnedNgramsFile.exists() && learnedNgramsFile.isFile) {
                        zipOut.putNextEntry(ZipEntry(LEARNED_NGRAMS_ENTRY))
                        learnedNgramsFile.inputStream().use { input -> input.copyTo(zipOut) }
                        zipOut.closeEntry()
                        hasLearnedNgrams = true
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error exportando learned_ngrams.json", e)
                }
            }

            Result.success(
                BackupExportSummary(
                    credentialsCount = credentialsCount,
                    fontsCount = fontsCount,
                    themesCount = themesCount,
                    preferencesCount = preferencesCount,
                    hasUserDict = hasUserDict,
                    hasLearnedNgrams = hasLearnedNgrams
                )
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error durante exportación de backup", e)
            Result.failure(e)
        }
    }

    fun getFileName(context: Context, uri: Uri): String? {
        var fileName: String? = null
        if (uri.scheme == "content") {
            try {
                context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (index != -1) {
                            fileName = cursor.getString(index)
                        }
                    }
                }
            } catch (_: Exception) {}
        }
        if (fileName == null) {
            fileName = uri.path?.substringAfterLast('/')
        }
        return fileName
    }

    fun isMantisDataFile(context: Context, uri: Uri): Boolean {
        val fileName = getFileName(context, uri) ?: return true
        return fileName.endsWith(".mantisdata", ignoreCase = true)
    }

    suspend fun importBackup(context: Context, sourceUri: Uri): Result<BackupImportSummary> = withContext(Dispatchers.IO) {
        try {
            if (!isMantisDataFile(context, sourceUri)) {
                return@withContext Result.failure(IllegalArgumentException("El archivo seleccionado no tiene la extensión .mantisdata"))
            }

            var credentialsCount = 0
            var fontsCount = 0
            var themesCount = 0
            var preferencesRestored = false
            var userDictRestored = false
            var ngramsRestored = false

            val inputStream = context.contentResolver.openInputStream(sourceUri)
                ?: return@withContext Result.failure(IOException("No se pudo abrir el stream de lectura de la copia de seguridad"))

            // 1. Primera pasada para descomprimir archivos locales (fuentes, imágenes, diccionarios) y buffers de JSON
            val byteEntries = mutableMapOf<String, ByteArray>()

            ZipInputStream(BufferedInputStream(inputStream)).use { zipIn ->
                var entry = zipIn.nextEntry
                while (entry != null) {
                    val entryName = entry.name
                    if (!entry.isDirectory) {
                        val baos = ByteArrayOutputStream()
                        zipIn.copyTo(baos)
                        val bytes = baos.toByteArray()

                        if (entryName.startsWith(FONTS_DIR_ENTRY)) {
                            val fontName = entryName.substringAfterLast("/")
                            if (fontName.isNotBlank() && !fontName.startsWith(".")) {
                                val destFile = File(FontManager.getCustomFontsDir(context), fontName)
                                destFile.parentFile?.mkdirs()
                                FileOutputStream(destFile).use { out -> out.write(bytes) }
                                fontsCount++
                            }
                        } else if (entryName.startsWith(THEME_IMAGES_DIR_ENTRY)) {
                            val imageName = entryName.substringAfterLast("/")
                            if (imageName.isNotBlank() && !imageName.startsWith(".")) {
                                val destFile = File(context.filesDir, imageName)
                                FileOutputStream(destFile).use { out -> out.write(bytes) }
                            }
                        } else if (entryName == USER_DICT_ENTRY) {
                            val destFile = File(context.filesDir, USER_DICT_ENTRY)
                            FileOutputStream(destFile).use { out -> out.write(bytes) }
                            userDictRestored = true
                        } else if (entryName == LEARNED_NGRAMS_ENTRY) {
                            val destFile = File(context.filesDir, LEARNED_NGRAMS_ENTRY)
                            FileOutputStream(destFile).use { out -> out.write(bytes) }
                            ngramsRestored = true
                        } else {
                            byteEntries[entryName] = bytes
                        }
                    }
                    zipIn.closeEntry()
                    entry = zipIn.nextEntry
                }
            }

            // 2. Restaurar Credenciales
            byteEntries[CREDENTIALS_ENTRY]?.let { bytes ->
                try {
                    val credsJson = String(bytes, Charsets.UTF_8)
                    val credentials = Json.decodeFromString<List<CredentialItem>>(credsJson)
                    credentials.forEach { item ->
                        CredentialsManager.saveCredential(context, item)
                        credentialsCount++
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error restaurando credenciales", e)
                }
            }

            // 3. Restaurar Temas Personalizados
            byteEntries[CUSTOM_THEMES_ENTRY]?.let { bytes ->
                try {
                    val themesJson = String(bytes, Charsets.UTF_8)
                    val themes = ThemeUtils.json.decodeFromString<List<KeyboardTheme>>(themesJson)
                    themes.forEach { theme ->
                        var restoredTheme = theme
                        theme.backgroundImageUri?.let { bgUriStr ->
                            if (bgUriStr.startsWith("theme_images/")) {
                                val imageName = bgUriStr.substringAfterLast("/")
                                val localFile = File(context.filesDir, imageName)
                                if (localFile.exists()) {
                                    restoredTheme = theme.copy(backgroundImageUri = Uri.fromFile(localFile).toString())
                                }
                            }
                        }
                        ThemeUtils.saveCustomTheme(context, restoredTheme)
                        themesCount++
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error restaurando temas personalizados", e)
                }
            }

            // 4. Restaurar Preferencias de DataStore
            byteEntries[PREFERENCES_ENTRY]?.let { bytes ->
                try {
                    val prefsJson = String(bytes, Charsets.UTF_8)
                    val jsonElement = Json.parseToJsonElement(prefsJson)
                    if (jsonElement is JsonObject) {
                        ThemeUtils.getDataStore(context).edit { editPrefs ->
                            jsonElement.forEach { (keyName, element) ->
                                if (keyName == ThemeUtils.CUSTOM_THEMES.name) {
                                    // Ignorar para no sobreescribir la fusión de temas previa
                                    return@forEach
                                }

                                when (element) {
                                    is JsonPrimitive -> {
                                        when {
                                            element.isString -> editPrefs[stringPreferencesKey(keyName)] = element.content
                                            element.booleanOrNull != null -> editPrefs[booleanPreferencesKey(keyName)] = element.boolean
                                            element.intOrNull != null -> editPrefs[intPreferencesKey(keyName)] = element.int
                                            element.floatOrNull != null -> editPrefs[floatPreferencesKey(keyName)] = element.float
                                            element.longOrNull != null -> editPrefs[intPreferencesKey(keyName)] = element.long.toInt()
                                        }
                                    }
                                    else -> {}
                                }
                            }
                        }
                        preferencesRestored = true
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error restaurando preferencias", e)
                }
            }

            // 5. Notificar recarga a componentes en memoria si es necesario
            if (userDictRestored) {
                try {
                    UserDictionary(context).loadUserDictionary()
                } catch (e: Exception) {
                    Log.e(TAG, "Error recargando diccionario de usuario tras restaurar", e)
                }
            }

            ThemeUtils.notifyDynamicColorsChanged()

            Result.success(
                BackupImportSummary(
                    credentialsCount = credentialsCount,
                    fontsCount = fontsCount,
                    themesCount = themesCount,
                    preferencesRestored = preferencesRestored,
                    userDictRestored = userDictRestored,
                    ngramsRestored = ngramsRestored
                )
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error durante importación de backup", e)
            Result.failure(e)
        }
    }
}
