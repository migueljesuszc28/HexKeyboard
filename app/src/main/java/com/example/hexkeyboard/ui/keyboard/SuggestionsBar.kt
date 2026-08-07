package com.example.hexkeyboard.ui.keyboard

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.hexkeyboard.data.repository.KeyboardTheme
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
                modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val isListeningFlow = remember(service) { service?.voiceRecognitionHelper?.isListening ?: MutableStateFlow(false) }
                val partialVoiceResultFlow = remember(service) { service?.voiceRecognitionHelper?.partialResult ?: MutableStateFlow("") }
                
                val isListening by isListeningFlow.collectAsState()
                val partialVoiceResult by partialVoiceResultFlow.collectAsState()

                IconButton(
                    onClick = {
                        FeedbackManager.triggerFeedback(context)
                        viewModel.setCurrentView("functions")
                    },
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Extension,
                        contentDescription = "Funciones",
                        tint = Color(theme.keyboardIconTint),
                        modifier = Modifier.size(26.dp)
                    )
                }

                LazyRow(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (isListening) {
                        item {
                            Text(
                                text = partialVoiceResult.ifEmpty { "Escuchando..." },
                                color = Color(theme.keyboardIconTint).copy(alpha = 0.6f),
                                fontSize = 14.sp
                            )
                        }
                    } else {
                        items(suggestions, key = { it }) { suggestion ->
                            SuggestionChip(suggestion, theme) {
                                FeedbackManager.triggerFeedback(context)
                                viewModel.onSuggestionClick(suggestion)
                            }
                        }
                    }
                }

                IconButton(
                    onClick = {
                        FeedbackManager.triggerFeedback(context)
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
                    modifier = Modifier.size(46.dp)
                ) {
                    Icon(
                        imageVector = if (isListening) Icons.Default.Done else Icons.Default.Mic,
                        contentDescription = "Dictado por voz",
                        tint = if (isListening) Color.Red else Color(theme.keyboardIconTint),
                        modifier = Modifier.size(26.dp)
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
            PanelHeader(currentView, theme) { viewModel.setCurrentView("keyboard") }
        }
    }
}

@Composable
fun SuggestionChip(suggestion: String, theme: KeyboardTheme, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        color = Color(theme.keyBackgroundColor).copy(alpha = 0.5f),
        shape = CircleShape
    ) {
        Text(
            text = suggestion,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            color = Color(theme.keyTextColor),
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium
        )
    }
}
