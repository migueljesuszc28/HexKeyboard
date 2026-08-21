package com.example.hexkeyboard.ui.keyboard.panels

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.hexkeyboard.logic.managers.ClipboardItem
import com.example.hexkeyboard.data.repository.KeyboardTheme
import com.example.hexkeyboard.logic.managers.FeedbackManager

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ClipboardPanel(
    history: List<ClipboardItem>, 
    onItemSelected: (String) -> Unit, 
    onDelete: (ClipboardItem) -> Unit,
    onTogglePin: (ClipboardItem) -> Unit,
    onBack: () -> Unit, 
    theme: KeyboardTheme
) {
    val context = LocalContext.current
    var itemWithOptions by remember { mutableStateOf<ClipboardItem?>(null) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Transparent)
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            if (history.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("El portapapeles está vacío", color = Color(theme.keyTextColor).copy(alpha = 0.5f), style = MaterialTheme.typography.bodyMedium)
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(history) { item ->
                        Surface(
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .combinedClickable(
                                    onClick = { 
                                        FeedbackManager.triggerFeedback(context, FeedbackManager.HapticType.KEY_CLICK)
                                        onItemSelected(item.text) 
                                    },
                                    onLongClick = { 
                                        FeedbackManager.triggerFeedback(context, FeedbackManager.HapticType.LONG_PRESS)
                                        itemWithOptions = item 
                                    }
                                ),
                            color = Color(theme.keyBackgroundColor),
                            shape = RoundedCornerShape(12.dp),
                            tonalElevation = if (theme.id == "glass") 0.dp else 2.dp
                        ) {
                            Box(Modifier.padding(12.dp)) {
                                Text(
                                    text = item.text,
                                    maxLines = 2,
                                    color = Color(theme.keyTextColor),
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.padding(end = if (item.isPinned) 20.dp else 0.dp)
                                )
                                if (item.isPinned) {
                                    Icon(
                                        Icons.Default.PushPin,
                                        contentDescription = null,
                                        tint = Color(theme.keyboardIconTint).copy(alpha = 0.6f),
                                        modifier = Modifier.size(14.dp).align(Alignment.TopEnd)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        itemWithOptions?.let { item ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable { 
                        FeedbackManager.triggerFeedback(context, FeedbackManager.HapticType.TICK)
                        itemWithOptions = null 
                    },
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    modifier = Modifier
                        .padding(24.dp)
                        .fillMaxWidth(0.9f)
                        .clickable(enabled = false) {},
                    shape = RoundedCornerShape(16.dp),
                    color = Color(theme.backgroundColor).copy(alpha = if (theme.id == "glass") 0.9f else 1.0f),
                    tonalElevation = if (theme.id == "glass") 0.dp else 8.dp,
                    shadowElevation = 12.dp
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Opciones de nota",
                            style = MaterialTheme.typography.titleMedium,
                            color = Color(theme.keyTextColor),
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = item.text,
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(theme.keyTextColor).copy(alpha = 0.7f),
                            maxLines = 2
                        )
                        Spacer(Modifier.height(20.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Button(
                                onClick = { 
                                    FeedbackManager.triggerFeedback(context, FeedbackManager.HapticType.TICK)
                                    onTogglePin(item)
                                    itemWithOptions = null 
                                },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(theme.keyBackgroundColor),
                                    contentColor = Color(theme.keyTextColor)
                                ),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(
                                    if (item.isPinned) Icons.Default.PushPin else Icons.Outlined.PushPin,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(if (item.isPinned) "Desfijar" else "Fijar", fontSize = 13.sp)
                            }
                            Button(
                                onClick = { 
                                    FeedbackManager.triggerFeedback(context, FeedbackManager.HapticType.DELETE)
                                    onDelete(item)
                                    itemWithOptions = null 
                                },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.errorContainer,
                                    contentColor = MaterialTheme.colorScheme.onErrorContainer
                                ),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Borrar", fontSize = 13.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}
