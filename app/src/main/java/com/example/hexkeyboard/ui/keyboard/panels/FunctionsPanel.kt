package com.example.hexkeyboard.ui.keyboard.panels

import android.content.Intent
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.hexkeyboard.service.HexKeyboardService
import com.example.hexkeyboard.data.repository.KeyboardTheme
import com.example.hexkeyboard.ui.settings.themes.ThemeSettingsActivity
import kotlin.math.abs

@Composable
fun FunctionsPanel(onBack: () -> Unit, onSettings: () -> Unit, theme: KeyboardTheme) {
    val context = LocalContext.current
    val service = context as? HexKeyboardService

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Transparent)
            .padding(16.dp)
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            FunctionItem(
                icon = Icons.Default.Settings,
                label = "Ajustes",
                onClick = onSettings,
                theme = theme
            )
            FunctionItem(
                icon = Icons.Default.Palette,
                label = "Temas",
                onClick = {
                    context.startActivity(Intent(context, ThemeSettingsActivity::class.java).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) })
                },
                theme = theme
            )
            FunctionItem(
                icon = Icons.Default.Language,
                label = "Idioma",
                onClick = {
                    service?.switchToNextLanguage()
                },
                onLongClick = {
                    service?.showLanguagePicker()
                },
                theme = theme
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FunctionItem(
    icon: Any, 
    label: String, 
    onClick: () -> Unit, 
    onLongClick: (() -> Unit)? = null,
    theme: KeyboardTheme
) {
    val bgColor = Color(theme.keyBackgroundColor)
    val iconColor = Color(theme.keyboardIconTint)
    val textColor = Color(theme.keyTextColor)
    
    // Calcular si el contraste es suficiente, si no, usar el color de texto o blanco/negro
    val bgLuminance = bgColor.luminance()
    val iconLuminance = iconColor.luminance()
    val contrast = abs(bgLuminance - iconLuminance)
    
    val adaptiveTint = if (contrast < 0.25f) {
        val textContrast = abs(bgLuminance - textColor.luminance())
        if (textContrast > 0.4f) textColor else (if (bgLuminance > 0.5f) Color.Black else Color.White)
    } else {
        iconColor
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(80.dp)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = bgColor,
            modifier = Modifier.size(56.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                when (icon) {
                    is ImageVector -> Icon(icon, contentDescription = null, tint = adaptiveTint)
                    is Painter -> Icon(icon, contentDescription = null, tint = adaptiveTint, modifier = Modifier.size(24.dp))
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        
        // Texto adaptable al fondo general si hay imagen
        val baseBgColor = Color(theme.backgroundColor)
        val textContrast = abs(baseBgColor.luminance() - textColor.luminance())
        val adaptiveTextColor = if (theme.backgroundImageUri != null && textContrast < 0.4f) {
            if (baseBgColor.luminance() > 0.5f) Color.Black else Color.White
        } else {
            textColor
        }
        
        Text(label, style = MaterialTheme.typography.labelMedium, color = adaptiveTextColor)
    }
}
