package com.example.hexkeyboard.ui.keyboard.panels

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.hexkeyboard.data.repository.KeyboardTheme
import com.example.hexkeyboard.logic.managers.ClipboardItem
import com.example.hexkeyboard.logic.managers.FeedbackManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ClipboardPanel(
    history: List<ClipboardItem>, 
    onItemSelected: (ClipboardItem) -> Unit, 
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
                    Text("El portapapeles está vacío", color = Color(theme.keyTextColor), style = MaterialTheme.typography.bodyMedium)
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
                                        onItemSelected(item) 
                                    },
                                    onLongClick = { 
                                        FeedbackManager.triggerFeedback(context, FeedbackManager.HapticType.LONG_PRESS)
                                        itemWithOptions = item 
                                    },
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null
                                ),
                            color = Color(theme.keyBackgroundColor),
                            shape = RoundedCornerShape(12.dp),
                            tonalElevation = if (theme.id == "glass") 0.dp else 2.dp
                        ) {
                            Box(Modifier.padding(8.dp)) {
                                Column(modifier = Modifier.fillMaxWidth()) {
                                    if (item.isImage && !item.imageUri.isNullOrEmpty()) {
                                        ClipboardImageThumbnail(
                                            imageUriString = item.imageUri,
                                            modifier = Modifier.clip(RoundedCornerShape(8.dp))
                                        )
                                        if (item.text.isNotBlank()) {
                                            Spacer(Modifier.height(4.dp))
                                            Text(
                                                text = item.text,
                                                maxLines = 1,
                                                color = Color(theme.keyTextColor),
                                                style = MaterialTheme.typography.bodySmall
                                            )
                                        }
                                    } else {
                                        Text(
                                            text = item.text,
                                            maxLines = 2,
                                            color = Color(theme.keyTextColor),
                                            style = MaterialTheme.typography.bodyMedium,
                                            modifier = Modifier.padding(end = if (item.isPinned) 18.dp else 0.dp)
                                        )
                                    }
                                }

                                if (item.isPinned) {
                                    Icon(
                                        Icons.Default.PushPin,
                                        contentDescription = null,
                                        tint = Color(theme.keyboardIconTint),
                                        modifier = Modifier
                                            .size(14.dp)
                                            .align(Alignment.TopEnd)
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
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { 
                        FeedbackManager.triggerFeedback(context, FeedbackManager.HapticType.TICK)
                        itemWithOptions = null 
                    },
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    modifier = Modifier
                        .padding(12.dp)
                        .fillMaxWidth(0.92f)
                        .clickable(enabled = false) {},
                    shape = RoundedCornerShape(16.dp),
                    color = Color(theme.backgroundColor).copy(alpha = if (theme.id == "glass") 0.9f else 1.0f),
                    tonalElevation = if (theme.id == "glass") 0.dp else 8.dp,
                    shadowElevation = 12.dp
                ) {
                    Column(
                        modifier = Modifier
                            .padding(12.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        Text(
                            text = if (item.isImage) "Opciones de imagen" else "Opciones de nota",
                            style = MaterialTheme.typography.titleSmall,
                            color = Color(theme.keyTextColor),
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(Modifier.height(6.dp))

                        if (item.isImage && !item.imageUri.isNullOrEmpty()) {
                            ClipboardImageThumbnail(
                                imageUriString = item.imageUri,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(70.dp)
                                    .clip(RoundedCornerShape(8.dp))
                            )
                        } else {
                            Text(
                                text = item.text,
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(theme.keyTextColor),
                                maxLines = 2
                            )
                        }

                        Spacer(Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Surface(
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null
                                    ) {
                                        FeedbackManager.triggerFeedback(context, FeedbackManager.HapticType.TICK)
                                        onTogglePin(item)
                                        itemWithOptions = null
                                    },
                                color = Color(theme.keyBackgroundColor),
                                contentColor = Color(theme.keyTextColor),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(vertical = 8.dp),
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        if (item.isPinned) Icons.Default.PushPin else Icons.Outlined.PushPin,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(if (item.isPinned) "Desfijar" else "Fijar", fontSize = 13.sp)
                                }
                            }
                            Surface(
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null
                                    ) {
                                        FeedbackManager.triggerFeedback(context, FeedbackManager.HapticType.DELETE)
                                        onDelete(item)
                                        itemWithOptions = null
                                    },
                                color = MaterialTheme.colorScheme.errorContainer,
                                contentColor = MaterialTheme.colorScheme.onErrorContainer,
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(vertical = 8.dp),
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically
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
}

@Composable
fun ClipboardImageThumbnail(imageUriString: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var bitmap by remember(imageUriString) { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(imageUriString) {
        withContext(Dispatchers.IO) {
            try {
                val uri = Uri.parse(imageUriString)
                context.contentResolver.openInputStream(uri)?.use { inputStream ->
                    val options = BitmapFactory.Options().apply {
                        inSampleSize = 2 // Scale down for thumbnail
                    }
                    bitmap = BitmapFactory.decodeStream(inputStream, null, options)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    val loadedBitmap = bitmap
    if (loadedBitmap != null) {
        Image(
            bitmap = loadedBitmap.asImageBitmap(),
            contentDescription = "Imagen copiada",
            contentScale = ContentScale.Crop,
            modifier = modifier
                .fillMaxWidth()
                .height(90.dp)
        )
    } else {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .height(90.dp)
                .background(Color.Gray.copy(alpha = 0.2f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.Image, contentDescription = null, tint = Color.Gray)
        }
    }
}
