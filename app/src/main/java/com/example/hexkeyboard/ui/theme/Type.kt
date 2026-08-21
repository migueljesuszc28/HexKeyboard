package com.example.hexkeyboard.ui.theme

import android.graphics.Typeface
import androidx.compose.material3.Typography
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.stringPreferencesKey
import com.example.hexkeyboard.data.repository.ThemeUtils
import kotlinx.coroutines.flow.map
import com.example.hexkeyboard.R
import java.io.File

// Definimos la familia de fuentes usando el recurso XML
val NunitoFontFamily = FontFamily(
    Font(R.font.nunito)
)

// Tipografía base (Sistema)
val Typography = Typography()

// Función para obtener la tipografía dinámica
@Composable
fun getTypography(): Typography {
    val context = LocalContext.current
    val dataStore = ThemeUtils.getDataStore(context)
    val customFontKey = stringPreferencesKey("custom_font_path")
    
    val fontPathFlow = remember { dataStore.data.map { it[customFontKey] ?: "system" } }
    val fontPath by fontPathFlow.collectAsState("system")

    val family = when (fontPath) {
        "nunito" -> NunitoFontFamily
        "system" -> FontFamily.Default
        else -> {
            val file = File(fontPath)
            if (file.exists()) {
                try {
                    FontFamily(Typeface.createFromFile(file))
                } catch (e: Exception) {
                    FontFamily.Default
                }
            } else {
                FontFamily.Default
            }
        }
    }

    return Typography(
        displayLarge = TextStyle(fontFamily = family, fontWeight = FontWeight.Normal, fontSize = 57.sp),
        displayMedium = TextStyle(fontFamily = family, fontWeight = FontWeight.Normal, fontSize = 45.sp),
        displaySmall = TextStyle(fontFamily = family, fontWeight = FontWeight.Normal, fontSize = 36.sp),
        headlineLarge = TextStyle(fontFamily = family, fontWeight = FontWeight.Normal, fontSize = 32.sp),
        headlineMedium = TextStyle(fontFamily = family, fontWeight = FontWeight.Normal, fontSize = 28.sp),
        headlineSmall = TextStyle(fontFamily = family, fontWeight = FontWeight.Normal, fontSize = 24.sp),
        titleLarge = TextStyle(fontFamily = family, fontWeight = FontWeight.Medium, fontSize = 22.sp),
        titleMedium = TextStyle(fontFamily = family, fontWeight = FontWeight.Medium, fontSize = 16.sp),
        titleSmall = TextStyle(fontFamily = family, fontWeight = FontWeight.Medium, fontSize = 14.sp),
        bodyLarge = TextStyle(fontFamily = family, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 24.sp, letterSpacing = 0.5.sp),
        bodyMedium = TextStyle(fontFamily = family, fontWeight = FontWeight.Normal, fontSize = 14.sp),
        bodySmall = TextStyle(fontFamily = family, fontWeight = FontWeight.Normal, fontSize = 12.sp),
        labelLarge = TextStyle(fontFamily = family, fontWeight = FontWeight.Medium, fontSize = 14.sp),
        labelMedium = TextStyle(fontFamily = family, fontWeight = FontWeight.Medium, fontSize = 12.sp),
        labelSmall = TextStyle(fontFamily = family, fontWeight = FontWeight.Medium, fontSize = 11.sp)
    )
}