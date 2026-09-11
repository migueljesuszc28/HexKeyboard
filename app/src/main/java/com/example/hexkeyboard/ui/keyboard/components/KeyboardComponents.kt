package com.example.hexkeyboard.ui.keyboard.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.hexkeyboard.data.repository.KeyboardTheme
import com.example.hexkeyboard.logic.managers.FeedbackManager
import com.example.hexkeyboard.ui.component.GlassEffectConfig
import com.example.hexkeyboard.ui.component.LocalGlassEffectConfig
import com.example.hexkeyboard.ui.component.glassContentColorFor
import com.example.hexkeyboard.ui.component.liquidGlass
import com.example.hexkeyboard.viewmodel.KeyboardViewModel

@Composable
fun EmojiSearchBar(
    viewModel: KeyboardViewModel,
    theme: KeyboardTheme,
    query: String,
    modifier: Modifier = Modifier,
    glassConfig: GlassEffectConfig = LocalGlassEffectConfig.current
) {
    val context = LocalContext.current
    val iconColor = glassContentColorFor(
        behind = Color(theme.backgroundColor),
        tint = glassConfig.surfaceTintColor,
        opacity = glassConfig.surfaceOpacity
    )
    val textColor = Color(theme.keyTextColor)
    val searchShape = remember { RoundedCornerShape(20.dp) }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(42.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Botón circular con efecto Liquid Glass dinámico para el icono de teclado (solo en panel de emojis)
        Box(
            modifier = Modifier
                .size(38.dp)
                .liquidGlass(
                    config = glassConfig,
                    shape = CircleShape,
                    highlightAlpha = 0.3f
                )
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {
                    FeedbackManager.triggerFeedback(context, FeedbackManager.HapticType.KEY_CLICK)
                    if (viewModel.isEmojiSearchActive.value) {
                        viewModel.setEmojiSearchActive(false)
                    } else {
                        viewModel.setCurrentView("keyboard")
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Keyboard,
                contentDescription = "Cerrar",
                tint = iconColor,
                modifier = Modifier.size(20.dp)
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        // Barra de búsqueda de emojis con efecto Liquid Glass dinámico
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .liquidGlass(
                    config = glassConfig,
                    shape = searchShape,
                    highlightAlpha = 0.3f
                )
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {
                    FeedbackManager.triggerFeedback(context, FeedbackManager.HapticType.KEY_CLICK)
                    viewModel.setEmojiSearchActive(true)
                }
                .padding(horizontal = 14.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = null,
                    tint = iconColor,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = query.ifEmpty { "Buscar emoji..." },
                    color = textColor,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Composable
fun CredentialsSearchBar(
    viewModel: KeyboardViewModel,
    theme: KeyboardTheme,
    query: String,
    modifier: Modifier = Modifier,
    glassConfig: GlassEffectConfig = LocalGlassEffectConfig.current
) {
    val context = LocalContext.current
    val iconColor = glassContentColorFor(
        behind = Color(theme.backgroundColor),
        tint = glassConfig.surfaceTintColor,
        opacity = glassConfig.surfaceOpacity
    )
    val textColor = Color(theme.keyTextColor)
    val searchShape = remember { RoundedCornerShape(20.dp) }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(42.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Botón circular con efecto Liquid Glass dinámico para el icono de teclado
        Box(
            modifier = Modifier
                .size(38.dp)
                .liquidGlass(
                    config = glassConfig,
                    shape = CircleShape,
                    highlightAlpha = 0.3f
                )
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {
                    FeedbackManager.triggerFeedback(context, FeedbackManager.HapticType.KEY_CLICK)
                    if (viewModel.isCredentialsSearchActive.value) {
                        viewModel.setCredentialsSearchActive(false)
                    } else {
                        viewModel.setCurrentView("keyboard")
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Keyboard,
                contentDescription = "Cerrar",
                tint = iconColor,
                modifier = Modifier.size(20.dp)
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        // Barra de búsqueda de contraseñas con efecto Liquid Glass dinámico
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .liquidGlass(
                    config = glassConfig,
                    shape = searchShape,
                    highlightAlpha = 0.3f
                )
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {
                    FeedbackManager.triggerFeedback(context, FeedbackManager.HapticType.KEY_CLICK)
                    viewModel.setCredentialsSearchActive(true)
                }
                .padding(horizontal = 14.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = null,
                    tint = iconColor,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = query.ifEmpty { "Buscar contraseña..." },
                    color = textColor,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Composable
fun PanelHeader(
    title: String,
    theme: KeyboardTheme,
    modifier: Modifier = Modifier,
    glassConfig: GlassEffectConfig = LocalGlassEffectConfig.current,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val textColor = Color(theme.keyTextColor)
    val iconColor = glassContentColorFor(
        behind = Color(theme.backgroundColor),
        tint = glassConfig.surfaceTintColor,
        opacity = glassConfig.surfaceOpacity
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(42.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Botón circular con efecto Liquid Glass dinámico para el icono de teclado/cerrar
        Box(
            modifier = Modifier
                .size(38.dp)
                .liquidGlass(
                    config = glassConfig,
                    shape = CircleShape,
                    highlightAlpha = 0.3f
                )
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {
                    FeedbackManager.triggerFeedback(context, FeedbackManager.HapticType.KEY_CLICK)
                    onBack()
                },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Keyboard,
                contentDescription = "Volver",
                tint = iconColor,
                modifier = Modifier.size(20.dp)
            )
        }
        Text(
            text = title,
            color = textColor,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(start = 12.dp)
        )
    }
}
