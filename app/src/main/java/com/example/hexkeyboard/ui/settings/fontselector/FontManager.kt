package com.example.hexkeyboard.ui.settings.fontselector

import android.content.Context
import android.graphics.Typeface
import android.net.Uri
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.zip.ZipInputStream

object FontManager {
    private const val FONTS_DIR = "custom_fonts"

    fun getCustomFontsDir(context: Context): File {
        return File(context.filesDir, FONTS_DIR)
    }

    private fun ensureFontsDir(context: Context): File {
        val dir = getCustomFontsDir(context)
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun importFont(context: Context, uri: Uri, fileName: String): List<File> {
        val importedFiles = mutableListOf<File>()
        try {
            val contentResolver = context.contentResolver
            val mimeType = contentResolver.getType(uri)
            
            if (mimeType == "application/zip" || fileName.endsWith(".zip", ignoreCase = true)) {
                contentResolver.openInputStream(uri)?.use { input ->
                    importedFiles.addAll(unzipAndImportFonts(context, input))
                }
            } else {
                val destFile = File(ensureFontsDir(context), fileName)
                contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(destFile).use { output ->
                        input.copyTo(output)
                    }
                }
                importedFiles.add(destFile)
            }
        } catch (e: Exception) {
            Log.e("FontManager", "Error importing font", e)
        }
        return importedFiles
    }

    private fun unzipAndImportFonts(context: Context, inputStream: InputStream): List<File> {
        val importedFiles = mutableListOf<File>()
        val zipInput = ZipInputStream(inputStream)
        var entry = zipInput.nextEntry
        val destDir = ensureFontsDir(context)

        while (entry != null) {
            val name = entry.name.substringAfterLast("/")
            if (!entry.isDirectory && (name.endsWith(".ttf", true) || name.endsWith(".otf", true))) {
                // Evitar archivos ocultos o de sistema macos (__MACOSX)
                if (!name.startsWith(".")) {
                    val destFile = File(destDir, name)
                    FileOutputStream(destFile).use { output ->
                        zipInput.copyTo(output)
                    }
                    importedFiles.add(destFile)
                }
            }
            zipInput.closeEntry()
            entry = zipInput.nextEntry
        }
        return importedFiles
    }

    fun listCustomFonts(context: Context): List<File> {
        val dir = getCustomFontsDir(context)
        if (!dir.exists()) return emptyList()
        return dir.listFiles { file ->
            file.extension.lowercase() in listOf("ttf", "otf")
        }?.toList() ?: emptyList()
    }

    fun deleteFont(file: File): Boolean {
        return file.delete()
    }

    fun loadTypeface(file: File): Typeface? {
        return try {
            if (file.exists() && file.length() > 0) {
                Typeface.createFromFile(file)
            } else null
        } catch (_: Exception) {
            null
        }
    }
}
