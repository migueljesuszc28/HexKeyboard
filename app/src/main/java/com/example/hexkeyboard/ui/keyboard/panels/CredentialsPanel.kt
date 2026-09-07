package com.example.hexkeyboard.ui.keyboard.panels

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.hexkeyboard.data.model.CredentialItem
import com.example.hexkeyboard.data.repository.KeyboardTheme
import com.example.hexkeyboard.logic.managers.FeedbackManager

@Composable
fun CredentialsPanel(
    credentials: List<CredentialItem>,
    onInsertText: (String) -> Unit,
    theme: KeyboardTheme,
    bottomOffset: Int = 0,
    navBarBottomDp: Int = 0
) {
    val baseThemeColor = Color(theme.backgroundColor)

    val topShadowMaskBrush = remember(theme.backgroundColor) {
        Brush.verticalGradient(
            colorStops = arrayOf(
                0.0f to baseThemeColor.copy(alpha = 0.55f),
                0.6f to baseThemeColor.copy(alpha = 0.20f),
                1.0f to Color.Transparent
            )
        )
    }

    val bottomShadowMaskBrush = remember(theme.backgroundColor) {
        Brush.verticalGradient(
            colorStops = arrayOf(
                0.0f to Color.Transparent,
                0.3f to baseThemeColor.copy(alpha = 0.40f),
                1.0f to baseThemeColor.copy(alpha = 0.82f)
            )
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Transparent)
    ) {
        if (credentials.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = 46.dp, bottom = (70 + bottomOffset + navBarBottomDp).dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No hay credenciales. Añádelas desde Ajustes.",
                    color = Color(theme.keyTextColor).copy(alpha = 0.7f),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 12.dp,
                    end = 12.dp,
                    top = 52.dp,
                    bottom = (70 + bottomOffset + navBarBottomDp).dp
                ),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(credentials, key = { it.id }) { item ->
                    CredentialKeyboardCard(item = item, theme = theme, onInsert = onInsertText)
                }
            }
        }

        // Top Shadow Mask (under suggestions bar)
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .height(58.dp)
                .background(topShadowMaskBrush)
        )

        // Bottom Floating Shadow Mask
        val totalBottomHazeHeight = bottomOffset + navBarBottomDp + 16
        if (totalBottomHazeHeight > 0) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(totalBottomHazeHeight.dp)
                    .background(bottomShadowMaskBrush)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { /* Absorbe toques */ }
            )
        }
    }
}

@Composable
fun CredentialKeyboardCard(
    item: CredentialItem,
    theme: KeyboardTheme,
    onInsert: (String) -> Unit
) {
    val context = LocalContext.current
    var showPassword by remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color(theme.keyBackgroundColor),
        shape = RoundedCornerShape(12.dp),
        tonalElevation = if (theme.id == "glass") 0.dp else 2.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp)
        ) {
            Text(
                text = item.title,
                color = Color(theme.keyTextColor),
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp
            )
            Spacer(Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Botón Usuario / Correo
                Button(
                    onClick = {
                        FeedbackManager.triggerFeedback(context, FeedbackManager.HapticType.KEY_CLICK)
                        onInsert(item.username)
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(32.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(theme.keyTextColor).copy(alpha = 0.1f),
                        contentColor = Color(theme.keyTextColor)
                    ),
                    contentPadding = PaddingValues(horizontal = 8.dp)
                ) {
                    Icon(Icons.Default.Person, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(text = item.username, fontSize = 12.sp, maxLines = 1)
                }

                // Botón Contraseña
                Button(
                    onClick = {
                        FeedbackManager.triggerFeedback(context, FeedbackManager.HapticType.KEY_CLICK)
                        onInsert(item.password)
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(32.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(theme.keyTextColor).copy(alpha = 0.15f),
                        contentColor = Color(theme.keyTextColor)
                    ),
                    contentPadding = PaddingValues(horizontal = 8.dp)
                ) {
                    Icon(Icons.Default.Key, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(text = if (showPassword) item.password else "••••••••", fontSize = 12.sp, maxLines = 1)
                }
            }
        }
    }
}