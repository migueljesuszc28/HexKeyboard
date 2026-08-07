package com.example.hexkeyboard.ui.keyboard.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.hexkeyboard.data.repository.KeyboardTheme
import com.example.hexkeyboard.viewmodel.KeyboardViewModel

@Composable
fun EmojiSearchBar(viewModel: KeyboardViewModel, theme: KeyboardTheme, query: String) {
    val iconColor = Color(theme.keyboardIconTint)
    val baseBgColor = Color(theme.backgroundColor)
    val useAdaptiveColor = theme.backgroundImageUri != null
    val isLightBg = baseBgColor.red * 0.299 + baseBgColor.green * 0.587 + baseBgColor.blue * 0.114 > 0.5
    
    val finalIconColor = if (useAdaptiveColor) {
        if (isLightBg) Color.Black else Color.White
    } else {
        iconColor
    }

    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(
            onClick = { viewModel.setCurrentView("keyboard") },
            modifier = Modifier.size(36.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Keyboard,
                contentDescription = "Cerrar",
                tint = finalIconColor
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .background(Color(theme.keyBackgroundColor).copy(alpha = 0.5f), RoundedCornerShape(24.dp))
                .clickable { viewModel.setEmojiSearchActive(true) }
                .padding(horizontal = 16.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = null,
                    tint = Color(theme.keyboardIconTint).copy(alpha = 0.6f),
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (query.isEmpty()) "Buscar emoji..." else query,
                    color = Color(theme.keyTextColor).copy(alpha = if (query.isEmpty()) 0.5f else 1f),
                    fontSize = 14.sp
                )
            }
        }
    }
}

@Composable
fun PanelHeader(title: String, theme: KeyboardTheme, onBack: () -> Unit) {
    val textColor = Color(theme.keyTextColor)
    val iconColor = Color(theme.keyboardIconTint)
    val baseBgColor = Color(theme.backgroundColor)
    
    // Lógica de color adaptable al fondo
    val useAdaptiveColor = theme.backgroundImageUri != null
    val isLightBg = baseBgColor.red * 0.299 + baseBgColor.green * 0.587 + baseBgColor.blue * 0.114 > 0.5
    
    val finalTextColor = if (useAdaptiveColor) {
        if (isLightBg) Color.Black else Color.White
    } else {
        textColor
    }

    val finalIconColor = if (useAdaptiveColor) {
        if (isLightBg) Color.Black else Color.White
    } else {
        iconColor
    }

    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack) {
            Icon(
                imageVector = Icons.Default.Keyboard,
                contentDescription = "Volver",
                tint = finalIconColor
            )
        }
        Text(
            text = title.uppercase(),
            color = finalTextColor,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 8.dp)
        )
    }
}
