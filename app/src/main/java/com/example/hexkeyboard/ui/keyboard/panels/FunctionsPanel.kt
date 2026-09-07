package com.example.hexkeyboard.ui.keyboard.panels

import android.content.Intent
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
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

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Transparent)
            .padding(horizontal = 8.dp, vertical = 16.dp),
        contentAlignment = Alignment.TopCenter
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
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
                    viewModel?.switchToNextLanguage()
                },
                onLongClick = {
                    viewModel?.showLanguagePicker()
                },
                theme = theme
            )
            FunctionItem(
                icon = Icons.Default.Key,
                label = "Contraseñas",
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

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(76.dp)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick,
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            )
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = bgColor,
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