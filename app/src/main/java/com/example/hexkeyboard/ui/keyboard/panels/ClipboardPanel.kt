package com.example.hexkeyboard.ui.keyboard.panels

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.convx.music.ui.component.backdrop.backdrops.layerBackdrop
import com.example.hexkeyboard.ui.component.backdrop.backdrops.rememberLayerBackdrop
import com.example.hexkeyboard.R
import com.example.hexkeyboard.data.repository.KeyboardTheme
import com.example.hexkeyboard.data.repository.ThemeUtils
import com.example.hexkeyboard.data.repository.ThemeUtils.getKeyboardString
import com.example.hexkeyboard.logic.managers.ClipboardHistoryManager
import com.example.hexkeyboard.logic.managers.ClipboardItem
import com.example.hexkeyboard.logic.managers.FeedbackManager
import com.example.hexkeyboard.ui.component.GlassEffectConfig
import com.example.hexkeyboard.ui.component.LocalAppBackdrop
import com.example.hexkeyboard.ui.component.LocalGlassEffectConfig
import com.example.hexkeyboard.ui.keyboard.components.PanelHeader
import com.example.hexkeyboard.ui.keyboard.components.rememberKeyBorderStroke
import com.example.hexkeyboard.ui.keyboard.components.rememberMorphingShape
import com.example.hexkeyboard.viewmodel.KeyboardViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun ClipboardPanel(
    history: List<ClipboardItem>, 
    onItemSelected: (ClipboardItem) -> Unit, 
    onLongPress: (ClipboardItem) -> Unit,
    onBack: () -> Unit, 
    theme: KeyboardTheme,
    viewModel: KeyboardViewModel? = null,
    bottomOffset: Int = 0,
    navBarBottomDp: Int = 0
) {
    val context = LocalContext.current
    val currentLocaleFlow = remember(viewModel) { viewModel?.currentLocale ?: kotlinx.coroutines.flow.MutableStateFlow("es") }
    val currentLocale by currentLocaleFlow.collectAsState("es")

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            ClipboardHistoryManager.cleanUpExpiredItems(context)
        }
    }

    val backdrop = rememberLayerBackdrop()
    val savedGlassConfigFlow = remember { ThemeUtils.getGlassEffectConfigFlow(context) }
    val savedGlassConfig by savedGlassConfigFlow.collectAsState(initial = GlassEffectConfig())
    val glassConfig = remember(theme, savedGlassConfig) {
        val glassTint = if (theme.id == "m3_dynamic") {
            Color(theme.backgroundColor)
        } else {
            Color(theme.keyBackgroundColor)
        }
        savedGlassConfig.copy(
            surfaceTintColor = glassTint,
            textColor = Color(theme.keyboardIconTint)
        )
    }

    val baseThemeColor = Color(theme.backgroundColor)
    val topShadowMaskBrush = remember(theme.backgroundColor) {
        Brush.verticalGradient(
            colorStops = arrayOf(
                0.0f to baseThemeColor.copy(alpha = 0.15f),
                0.6f to baseThemeColor.copy(alpha = 0.05f),
                1.0f to Color.Transparent
            )
        )
    }
    val bottomShadowMaskBrush = remember(theme.backgroundColor) {
        Brush.verticalGradient(
            colorStops = arrayOf(
                0.0f to Color.Transparent,
                0.4f to baseThemeColor.copy(alpha = 0.05f),
                1.0f to baseThemeColor.copy(alpha = 0.15f)
            )
        )
    }

    val borderStroke = rememberKeyBorderStroke(theme)
    val baseColor = Color(theme.keyBackgroundColor).copy(alpha = if (theme.keysOpacity >= 0.95f) 1.0f else theme.keysOpacity)
    val isTransparentBg = theme.keysOpacity == 0f || baseColor == Color.Transparent
    val cardColor = if (isTransparentBg) Color.Transparent else baseColor
    val cardElevation = if (isTransparentBg || theme.id == "glass") 0.dp else 2.dp

    val topPadding = 52.dp
    val bottomPadding = (70 + bottomOffset + navBarBottomDp).dp

    CompositionLocalProvider(
        LocalAppBackdrop provides backdrop,
        LocalGlassEffectConfig provides glassConfig
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Transparent)
        ) {
            // Capa del Backdrop registrada para capturar y difuminar dinámicamente las tarjetas al hacer scroll
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .layerBackdrop(backdrop)
            ) {
                if (history.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(top = topPadding, bottom = bottomPadding),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = context.getKeyboardString(R.string.clip_empty, currentLocale),
                            color = Color(theme.keyTextColor).copy(alpha = 0.7f),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                } else {
                    LazyVerticalStaggeredGrid(
                        columns = StaggeredGridCells.Fixed(2),
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            start = 12.dp,
                            end = 12.dp,
                            top = topPadding,
                            bottom = bottomPadding
                        ),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalItemSpacing = 8.dp
                    ) {
                        items(history, key = { "${it.timestamp}_${it.text.hashCode()}" }) { item ->
                            val cardInteractionSource = remember { MutableInteractionSource() }
                            val cardShape = rememberMorphingShape(
                                interactionSource = cardInteractionSource,
                                restCorner = 16.dp,
                                pressedCorner = 10.dp
                            )
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .wrapContentHeight()
                                    .combinedClickable(
                                        onClick = { 
                                            FeedbackManager.triggerFeedback(context, FeedbackManager.HapticType.KEY_CLICK)
                                            onItemSelected(item) 
                                        },
                                        onLongClick = { 
                                            FeedbackManager.triggerFeedback(context, FeedbackManager.HapticType.LONG_PRESS)
                                            onLongPress(item) 
                                        },
                                        interactionSource = cardInteractionSource,
                                        indication = null
                                    ),
                                color = cardColor,
                                shape = cardShape,
                                border = borderStroke,
                                tonalElevation = cardElevation
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp)
                                ) {
                                    if (item.isImage && !item.imageUri.isNullOrEmpty()) {
                                        Column(modifier = Modifier.fillMaxWidth()) {
                                            ClipboardImageThumbnail(
                                                imageUriString = item.imageUri,
                                                modifier = Modifier.clip(RoundedCornerShape(10.dp))
                                            )
                                            if (item.text.isNotBlank()) {
                                                Spacer(Modifier.height(6.dp))
                                                Text(
                                                    text = item.text,
                                                    maxLines = 2,
                                                    overflow = TextOverflow.Ellipsis,
                                                    color = Color(theme.keyTextColor),
                                                    style = MaterialTheme.typography.bodySmall
                                                )
                                            }
                                        }
                                    } else {
                                        Text(
                                            text = item.text,
                                            maxLines = 6,
                                            overflow = TextOverflow.Ellipsis,
                                            color = Color(theme.keyTextColor),
                                            style = MaterialTheme.typography.bodyMedium,
                                            modifier = Modifier.padding(end = if (item.isPinned) 16.dp else 0.dp)
                                        )
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

            val totalBottomHazeHeight = (bottomOffset + navBarBottomDp + 16).dp
            if (totalBottomHazeHeight > 0.dp) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(totalBottomHazeHeight)
                        .background(bottomShadowMaskBrush)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { /* Absorbe toques */ }
                )
            }

            // Máscara de Sombra discreta superior para suavizar el scroll debajo del encabezado flotante
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .height(58.dp)
                    .background(topShadowMaskBrush)
            )

            // Encabezado flotante con botón Atrás y título "Portapapeles"
            PanelHeader(
                title = context.getKeyboardString(R.string.panel_title_clipboard, currentLocale),
                theme = theme,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(start = 8.dp, end = 8.dp, top = 4.dp),
                glassConfig = glassConfig,
                onBack = onBack
            )
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
                val uri = imageUriString.toUri()
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
