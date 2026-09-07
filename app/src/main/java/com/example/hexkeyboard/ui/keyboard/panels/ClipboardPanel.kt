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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.hexkeyboard.data.repository.KeyboardTheme
import com.example.hexkeyboard.logic.managers.ClipboardHistoryManager
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
    onLongPress: (ClipboardItem) -> Unit,
    onBack: () -> Unit, 
    theme: KeyboardTheme
) {
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            ClipboardHistoryManager.cleanUpExpiredItems(context)
        }
    }

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
                                        onLongPress(item) 
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
    }
}

@Composable
fun ClipboardMenuRow(
    icon: ImageVector,
    label: String,
    tint: Color,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(22.dp)
        )
        Text(
            text = label,
            color = tint,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium
        )
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