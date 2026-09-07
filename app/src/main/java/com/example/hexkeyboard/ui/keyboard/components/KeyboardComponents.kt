package com.example.hexkeyboard.ui.keyboard.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
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
    val textColor = Color(theme.keyTextColor)

    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { viewModel.setCurrentView("keyboard") },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Keyboard,
                contentDescription = "Cerrar",
                tint = iconColor
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        val pillBg = if (theme.keyBackgroundColor != 0 && theme.keyBackgroundColor != android.graphics.Color.TRANSPARENT) Color(theme.keyBackgroundColor) else Color(theme.backgroundColor)
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .background(pillBg.copy(alpha = 0.9f), RoundedCornerShape(24.dp))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { viewModel.setEmojiSearchActive(true) }
                .padding(horizontal = 16.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = null,
                    tint = iconColor,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (query.isEmpty()) "Buscar emoji..." else query,
                    color = textColor,
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

    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { onBack() },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Keyboard,
                contentDescription = "Volver",
                tint = iconColor
            )
        }
        Text(
            text = title,
            color = textColor,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(start = 8.dp)
        )
    }
}
