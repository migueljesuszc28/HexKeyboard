package com.example.hexkeyboard.ui.keyboard.panels

import android.content.Intent
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Assignment
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.hexkeyboard.data.repository.KeyboardTheme
import com.example.hexkeyboard.ui.keyboard.components.bounceClick
import com.example.hexkeyboard.ui.keyboard.components.rememberKeyBorderStroke
import com.example.hexkeyboard.ui.settings.themes.ThemeSettingsActivity
import com.example.hexkeyboard.viewmodel.KeyboardViewModel

@Composable
fun FunctionsPanel(
    onBack: () -> Unit,
    onSettings: () -> Unit,
    theme: KeyboardTheme,
    viewModel: KeyboardViewModel? = null
) {
    val context = LocalContext.current
    val currentLocaleFlow = remember(viewModel) { viewModel?.currentLocale ?: kotlinx.coroutines.flow.MutableStateFlow("es") }
    val currentLocale by currentLocaleFlow.collectAsState("es")
    val isEn = currentLocale == "en"

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Transparent)
            .padding(horizontal = 8.dp, vertical = 12.dp),
        contentAlignment = Alignment.TopCenter
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.Top
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                FunctionItem(
                    icon = Icons.Rounded.Settings,
                    label = if (isEn) "Settings" else "Ajustes",
                    onClick = onSettings,
                    theme = theme
                )
                FunctionItem(
                    icon = Icons.AutoMirrored.Outlined.Assignment,
                    label = if (isEn) "Clipboard" else "Portapapeles",
                    onClick = {
                        viewModel?.setCurrentView("clipboard", context)
                    },
                    theme = theme
                )
            }
            FunctionItem(
                icon = Icons.Outlined.Palette,
                label = if (isEn) "Themes" else "Temas",
                onClick = {
                    context.startActivity(Intent(context, ThemeSettingsActivity::class.java).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) })
                },
                theme = theme
            )
            FunctionItem(
                icon = Icons.Default.Language,
                label = if (isEn) "Language" else "Idioma",
                onClick = {
                    viewModel?.setCurrentView("languages", context)
                },
                theme = theme
            )
            FunctionItem(
                icon = Icons.Default.Key,
                label = if (isEn) "Passwords" else "Contraseñas",
                onClick = {
                    viewModel?.setCurrentView("credentials", context)
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

    val borderStroke = rememberKeyBorderStroke(theme)

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(76.dp)
            .bounceClick(
                onClick = onClick,
                onLongClick = onLongClick
            )
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = bgColor,
            border = borderStroke,
            modifier = Modifier.size(56.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                when (icon) {
                    is ImageVector -> Icon(icon, contentDescription = null, tint = iconColor)
                    is Painter -> Icon(icon, contentDescription = null, tint = iconColor, modifier = Modifier.size(24.dp))
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = textColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}