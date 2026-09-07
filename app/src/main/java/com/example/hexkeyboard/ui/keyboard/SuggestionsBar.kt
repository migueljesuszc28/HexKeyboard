package com.example.hexkeyboard.ui.keyboard

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import androidx.compose.animation.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.hexkeyboard.data.repository.KeyboardTheme
import com.example.hexkeyboard.logic.managers.ClipboardItem
import com.example.hexkeyboard.logic.managers.VoiceRecognitionHelper
import com.example.hexkeyboard.logic.managers.FeedbackManager
import com.example.hexkeyboard.service.HexKeyboardService
import com.example.hexkeyboard.ui.keyboard.components.EmojiSearchBar
import com.example.hexkeyboard.ui.keyboard.components.PanelHeader
import com.example.hexkeyboard.ui.settings.PermissionActivity
import com.example.hexkeyboard.viewmodel.KeyboardViewModel
import kotlinx.coroutines.flow.MutableStateFlow

@Composable
fun SuggestionsBarSection(viewModel: KeyboardViewModel, theme: KeyboardTheme) {
    val currentView by viewModel.currentView.collectAsState()
    val suggestions by viewModel.suggestions.collectAsState()
    val emojiSearchQuery by viewModel.emojiSearchQuery.collectAsState()
    val context = LocalContext.current
    val service = context as? HexKeyboardService

    Box(modifier = Modifier.fillMaxWidth().height(46.dp)) {
        AnimatedVisibility(
            visible = currentView == "keyboard",
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            Row(
                modifier = Modifier.fillMaxSize().padding(horizontal = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val isListeningFlow = remember(service) { service?.voiceRecognitionHelper?.isListening ?: MutableStateFlow(false) }
                val partialVoiceResultFlow = remember(service) { service?.voiceRecognitionHelper?.partialResult ?: MutableStateFlow("") }
                
                val isListening by isListeningFlow.collectAsState()
                val partialVoiceResult by partialVoiceResultFlow.collectAsState()

                // Botón Izquierdo: Funciones
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {
                            FeedbackManager.triggerFeedback(context, FeedbackManager.HapticType.KEY_CLICK)
                            viewModel.setCurrentView("functions")
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Extension,
                        contentDescription = "Funciones",
                        tint = Color(theme.keyboardIconTint),
                        modifier = Modifier.size(24.dp)
                    )
                }

                // Sección Central: Las 3 Cápsulas estilo Gboard
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 2.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (isListening) {
                        Text(
                            text = partialVoiceResult.ifEmpty { "Escuchando..." },
                            color = Color(theme.keyboardIconTint),
                            fontSize = 14.sp
                        )
                    } else if (suggestions.firstOrNull()?.startsWith("CLIPBOARD:") == true) {
                        val clipContent = suggestions.first().removePrefix("CLIPBOARD:")
                        ClipboardSuggestionChip(clipContent, theme) {
                            FeedbackManager.triggerFeedback(context, FeedbackManager.HapticType.KEY_CLICK)
                            viewModel.onClipboardItemClick(ClipboardItem(clipContent))
                        }
                    } else if (suggestions.isNotEmpty()) {
                        val centerCandidate = suggestions.getOrNull(0) // Índice 0: Central (Principal / Autocorrección)
                        val leftCandidate = suggestions.getOrNull(1)   // Índice 1: Izquierda (Literal / Fallback)
                        val rightCandidate = suggestions.getOrNull(2)  // Índice 2: Derecha (Alternativa)

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Cápsula 1 (Izquierda: Literal / Fallback)
                            GboardCapsule(
                                text = leftCandidate,
                                isCenterPrimary = false,
                                theme = theme,
                                modifier = Modifier.weight(1f)
                            ) {
                                if (leftCandidate != null) {
                                    FeedbackManager.triggerFeedback(context, FeedbackManager.HapticType.KEY_CLICK)
                                    viewModel.onSuggestionClick(leftCandidate)
                                }
                            }

                            // Cápsula 2 (Centro: Predeterminada / Autocorrección)
                            GboardCapsule(
                                text = centerCandidate,
                                isCenterPrimary = true,
                                theme = theme,
                                modifier = Modifier.weight(1f)
                            ) {
                                if (centerCandidate != null) {
                                    FeedbackManager.triggerFeedback(context, FeedbackManager.HapticType.KEY_CLICK)
                                    viewModel.onSuggestionClick(centerCandidate)
                                }
                            }

                            // Cápsula 3 (Derecha: Alternativa semántica)
                            GboardCapsule(
                                text = rightCandidate,
                                isCenterPrimary = false,
                                theme = theme,
                                modifier = Modifier.weight(1f)
                            ) {
                                if (rightCandidate != null) {
                                    FeedbackManager.triggerFeedback(context, FeedbackManager.HapticType.KEY_CLICK)
                                    viewModel.onSuggestionClick(rightCandidate)
                                }
                            }
                        }
                    }
                }

                // Botón Derecho: Dictado por voz
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {
                            FeedbackManager.triggerFeedback(context, FeedbackManager.HapticType.KEY_CLICK)
                            if (isListening) {
                                service?.voiceRecognitionHelper?.stopListening()
                            } else {
                                val permission = Manifest.permission.RECORD_AUDIO
                                val granted = ContextCompat.checkSelfPermission(
                                    context, permission
                                ) == PackageManager.PERMISSION_GRANTED

                                if (granted) {
                                    service?.voiceRecognitionHelper?.startListening(
                                        object : VoiceRecognitionHelper.VoiceResultListener {
                                            override fun onVoiceResult(text: String) {
                                                viewModel.onCharTyped("$text ")
                                            }
                                            override fun onVoiceError(error: Int) {}
                                        }
                                    )
                                } else {
                                    val intent = Intent(context, PermissionActivity::class.java).apply {
                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        putExtra("request_permission", permission)
                                    }
                                    context.startActivity(intent)
                                }
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isListening) Icons.Default.Done else Icons.Default.Mic,
                        contentDescription = "Dictado por voz",
                        tint = if (isListening) Color.Red else Color(theme.keyboardIconTint),
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = currentView == "emoji",
            enter = fadeIn(), exit = fadeOut()
        ) {
            EmojiSearchBar(viewModel, theme, emojiSearchQuery)
        }

        AnimatedVisibility(
            visible = currentView != "keyboard" && currentView != "emoji",
            enter = fadeIn(), exit = fadeOut()
        ) {
            val title = when(currentView) {
                "clipboard" -> "Portapapeles"
                "functions" -> "Funciones"
                "credentials" -> "Contraseñas"
                else -> currentView
            }
            PanelHeader(title, theme) { viewModel.setCurrentView("keyboard") }
        }
    }
}

/**
 * Cápsula de sugerencia individual estilo Gboard (3 casillas estáticas).
 */
@Composable
fun GboardCapsule(
    text: String?,
    isCenterPrimary: Boolean,
    theme: KeyboardTheme,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    if (text.isNullOrEmpty()) {
        Spacer(modifier = modifier)
        return
    }

    val bgColor = if (isCenterPrimary) {
        Color(theme.keyBackgroundColor).copy(alpha = 0.95f)
    } else {
        Color(theme.keyBackgroundColor).copy(alpha = 0.65f)
    }

    val textColor = Color(theme.keyTextColor)
    val fontWeight = if (isCenterPrimary) FontWeight.Bold else FontWeight.Medium
    val fontSize = if (isCenterPrimary) 14.5.sp else 13.5.sp

    Surface(
        modifier = modifier
            .height(34.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            ),
        color = bgColor,
        shape = CircleShape
    ) {
        Box(
            modifier = Modifier.fillMaxSize().padding(horizontal = 6.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = text,
                color = textColor,
                fontSize = fontSize,
                fontWeight = fontWeight,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * Cápsula para sugerencia del portapapeles cuando el usuario no está escribiendo una palabra.
 */
@Composable
fun ClipboardSuggestionChip(clipText: String, theme: KeyboardTheme, onClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(34.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            ),
        color = Color(theme.keyBackgroundColor).copy(alpha = 0.95f),
        shape = CircleShape
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Default.ContentPaste,
                contentDescription = "Portapapeles",
                tint = Color(theme.keyboardIconTint),
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = clipText,
                color = Color(theme.keyTextColor),
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
