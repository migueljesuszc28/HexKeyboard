package com.example.hexkeyboard.ui.keyboard

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.preference.PreferenceManager
import com.example.hexkeyboard.data.repository.ThemeUtils
import com.example.hexkeyboard.logic.managers.ClipboardHistoryManager
import com.example.hexkeyboard.logic.managers.FeedbackManager
import com.example.hexkeyboard.service.HexKeyboardService
import com.example.hexkeyboard.ui.settings.ClipboardEditActivity
import com.example.hexkeyboard.viewmodel.KeyboardViewModel
import kotlinx.coroutines.flow.map
import dev.chrisbanes.haze.HazeDefaults
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun KeyboardScreen(viewModel: KeyboardViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val service = context as? HexKeyboardService
    val dataStore = ThemeUtils.getDataStore(context)
    val keyboardThemeOpt by viewModel.keyboardTheme.collectAsState()
    val keyboardTheme = keyboardThemeOpt ?: return

    val parallaxOffsetState = viewModel.parallaxOffset.collectAsState()

    val hazeState = remember { HazeState() }
    val isGlassTheme = keyboardTheme.id == "glass"
    val hasBackgroundImage = keyboardTheme.backgroundImageUri != null

    var backgroundBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var blurredBitmap by remember { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(keyboardTheme.backgroundImageUri, keyboardTheme.backgroundBlur) {
        if (keyboardTheme.backgroundImageUri != null) {
            val newBitmap = withContext(Dispatchers.IO) {
                ThemeUtils.loadBitmapFromUri(context, keyboardTheme.backgroundImageUri!!)
            } as Bitmap?
            if (newBitmap != null) {
                backgroundBitmap?.recycle()
                blurredBitmap?.recycle()

                backgroundBitmap = newBitmap
                blurredBitmap = if (keyboardTheme.backgroundBlur > 0) {
                    withContext(Dispatchers.IO) {
                        ThemeUtils.blurBitmap(newBitmap!!, keyboardTheme.backgroundBlur)
                    } as Bitmap?
                } else {
                    null
                }
            }
        } else {
            backgroundBitmap?.recycle()
            blurredBitmap?.recycle()
            backgroundBitmap = null
            blurredBitmap = null
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            backgroundBitmap?.recycle()
            blurredBitmap?.recycle()
        }
    }

    val bottomOffsetFlow = remember { dataStore.data.map { it[ThemeUtils.KEYBOARD_BOTTOM_OFFSET] ?: 35 } }
    val bottomOffset by bottomOffsetFlow.collectAsState(35)

    val currentView by viewModel.currentView.collectAsState()
    val isEmojiSearchActive by viewModel.isEmojiSearchActive.collectAsState()
    val isFullPanel = currentView == "emoji" || currentView == "credentials"
    val clipboardItemWithOptions by viewModel.clipboardItemWithOptions.collectAsState()

    Box(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight()
                .background(
                    color = Color(keyboardTheme.backgroundColor),
                    shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
                )
                .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                .then(if (isGlassTheme || isFullPanel) Modifier.hazeSource(hazeState) else Modifier)
        ) {
        // Continuous Background Layer
        val img = blurredBitmap ?: backgroundBitmap
        img?.let {
            Image(
                bitmap = it.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier
                    .matchParentSize()
                    .alpha(keyboardTheme.backgroundOpacity)
                    .graphicsLayer {
                        if (keyboardTheme.parallaxEffect) {
                            val parallaxLimit = 0.10f
                            val scale = 1.0f + parallaxLimit
                            val maxShiftX = (size.width * parallaxLimit) / 2f
                            val maxShiftY = (size.height * parallaxLimit) / 2f

                            val offset = parallaxOffsetState.value
                            translationX = offset.x * maxShiftX
                            translationY = offset.y * maxShiftY
                            scaleX = scale
                            scaleY = scale
                        }
                    },
                contentScale = ContentScale.Crop
            )
        }

        if (isGlassTheme) {
            Box(modifier = Modifier.matchParentSize().hazeEffect(state = hazeState, style = HazeDefaults.style(backgroundColor = Color(keyboardTheme.backgroundColor), blurRadius = 20.dp)).background(Color(keyboardTheme.backgroundColor)))
        } else if (!hasBackgroundImage) {
            Box(modifier = Modifier.matchParentSize().background(Color(keyboardTheme.backgroundColor)))
        } else {
            Box(modifier = Modifier.matchParentSize().background(Color(keyboardTheme.backgroundColor).copy(alpha = 1f - keyboardTheme.backgroundOpacity)))
        }

        val navBarBottomDp = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding().value.toInt()

        val mainSectionAlpha by animateFloatAsState(
            targetValue = if (isFullPanel && !isEmojiSearchActive) 0f else 1f,
            animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing),
            label = "main_section_fade"
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .then(if (isFullPanel && !isEmojiSearchActive) Modifier else Modifier.navigationBarsPadding())
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentHeight()
            ) {
                // Capa 1: Columna Ancla que determina la altura total exacta (46dp + Teclado + BottomOffset)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .alpha(mainSectionAlpha)
                ) {
                    Box(modifier = Modifier.fillMaxWidth().height(46.dp)) {
                        SuggestionsBarSection(viewModel, keyboardTheme)
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .wrapContentHeight(),
                        contentAlignment = Alignment.TopCenter
                    ) {
                        KeyboardMainSection(viewModel, keyboardTheme, hasBackgroundImage) { _, _ -> }
                        if (!isFullPanel || isEmojiSearchActive) {
                            PanelsSection(viewModel, keyboardTheme, hazeState, bottomOffset, navBarBottomDp)
                        }
                    }

                    val totalBottomSpacerHeight = if (isFullPanel && !isEmojiSearchActive) (bottomOffset + navBarBottomDp) else bottomOffset
                    if (totalBottomSpacerHeight > 0) {
                        Spacer(modifier = Modifier.fillMaxWidth().height(totalBottomSpacerHeight.dp))
                    }
                }

                // Capa 2: Panel de Emojis a pantalla completa con Animación Material 3 Expressive y Shadow Mask
                AnimatedContent(
                    targetState = isFullPanel && !isEmojiSearchActive,
                    transitionSpec = {
                        if (targetState) {
                            (slideInVertically(
                                initialOffsetY = { (it * 0.18f).toInt() },
                                animationSpec = spring(
                                    dampingRatio = 0.82f,
                                    stiffness = 380f
                                )
                            ) + scaleIn(
                                initialScale = 0.93f,
                                animationSpec = spring(
                                    dampingRatio = 0.82f,
                                    stiffness = 380f
                                )
                            ) + fadeIn(animationSpec = tween(durationMillis = 260, easing = FastOutSlowInEasing))).togetherWith(
                                fadeOut(animationSpec = tween(200, easing = FastOutLinearInEasing))
                            )
                        } else {
                            fadeIn(animationSpec = tween(220, easing = FastOutSlowInEasing)).togetherWith(
                                slideOutVertically(
                                    targetOffsetY = { (it * 0.12f).toInt() },
                                    animationSpec = tween(durationMillis = 200, easing = FastOutLinearInEasing)
                                ) + scaleOut(
                                    targetScale = 0.95f,
                                    animationSpec = tween(durationMillis = 200, easing = FastOutLinearInEasing)
                                ) + fadeOut(animationSpec = tween(durationMillis = 200, easing = FastOutLinearInEasing))
                            )
                        }
                    },
                    label = "m3_expressive_emoji_transition",
                    modifier = Modifier.matchParentSize()
                ) { showEmojiPanel ->
                    if (showEmojiPanel) {
                    val baseThemeColor = Color(keyboardTheme.backgroundColor)

                    val topShadowMaskBrush = remember(keyboardTheme.backgroundColor) {
                        Brush.verticalGradient(
                            colorStops = arrayOf(
                                0.0f to baseThemeColor.copy(alpha = 0.55f),
                                0.6f to baseThemeColor.copy(alpha = 0.20f),
                                1.0f to Color.Transparent
                            )
                        )
                    }

                    val bottomShadowMaskBrush = remember(keyboardTheme.backgroundColor) {
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
                            .matchParentSize()
                            .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                    ) {
                        // 1. El Panel de Emojis ocupa la altura total de la capa base
                        PanelsSection(viewModel, keyboardTheme, hazeState, bottomOffset, navBarBottomDp)

                        // 2. Barra de Sugerencias Flotante Superior con Máscara de Sombra
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .fillMaxWidth()
                                .height(58.dp)
                                .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                                .background(topShadowMaskBrush)
                        ) {
                            Box(modifier = Modifier.fillMaxWidth().height(46.dp)) {
                                SuggestionsBarSection(viewModel, keyboardTheme)
                            }
                        }

                        // 3. Margen Inferior Flotante con Máscara de Sombra (Bloquea toques hacia los emojis)
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
                                    ) { /* Absorbe toques para evitar seleccionar emojis detrás del margen */ }
                            )
                        }
                    }
                    }
                }
            }
        }

        // Full-Keyboard Dimming Scrim & Gboard Popup Menu when long-pressing clipboard item
        if (clipboardItemWithOptions != null) {
            val item = clipboardItemWithOptions!!
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(Color.Black.copy(alpha = 0.5f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { viewModel.setClipboardItemWithOptions(null) },
                contentAlignment = Alignment.Center
            ) {
                AnimatedVisibility(
                    visible = true,
                    enter = scaleIn(
                        initialScale = 0.85f,
                        animationSpec = spring(dampingRatio = 0.82f, stiffness = 400f)
                    ) + fadeIn(animationSpec = tween(200)),
                    exit = scaleOut(
                        targetScale = 0.9f,
                        animationSpec = tween(150)
                    ) + fadeOut(animationSpec = tween(150))
                ) {
                    Surface(
                        modifier = Modifier
                            .width(220.dp)
                            .clickable(enabled = false) {},
                        shape = RoundedCornerShape(20.dp),
                        color = Color(keyboardTheme.backgroundColor),
                        tonalElevation = 8.dp,
                        shadowElevation = 16.dp
                    ) {
                        Column(
                            modifier = Modifier.padding(vertical = 8.dp)
                        ) {
                            ClipboardMenuRow(
                                icon = Icons.Default.ContentPaste,
                                label = "Pegar",
                                tint = Color(keyboardTheme.keyTextColor)
                            ) {
                                FeedbackManager.triggerFeedback(context, FeedbackManager.HapticType.KEY_CLICK)
                                viewModel.onClipboardItemClick(item)
                                viewModel.setClipboardItemWithOptions(null)
                            }

                            ClipboardMenuRow(
                                icon = if (item.isPinned) Icons.Default.PushPin else Icons.Outlined.PushPin,
                                label = if (item.isPinned) "Desfijar" else "Fijar",
                                tint = Color(keyboardTheme.keyTextColor)
                            ) {
                                FeedbackManager.triggerFeedback(context, FeedbackManager.HapticType.TICK)
                                scope.launch {
                                    ClipboardHistoryManager.togglePin(context, item)
                                    service?.refreshClipboardHistory(triggerSuggestionsUpdate = true)
                                }
                                viewModel.setClipboardItemWithOptions(null)
                            }

                            if (!item.isImage) {
                                ClipboardMenuRow(
                                    icon = Icons.Default.Edit,
                                    label = "Editar",
                                    tint = Color(keyboardTheme.keyTextColor)
                                ) {
                                    FeedbackManager.triggerFeedback(context, FeedbackManager.HapticType.TICK)
                                    val intent = Intent(context, ClipboardEditActivity::class.java).apply {
                                        putExtra("item_text", item.text)
                                        putExtra("item_timestamp", item.timestamp)
                                        putExtra("item_image_uri", item.imageUri)
                                        putExtra("item_mime_type", item.mimeType)
                                        putExtra("item_is_pinned", item.isPinned)
                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    }
                                    context.startActivity(intent)
                                    viewModel.setClipboardItemWithOptions(null)
                                }
                            }

                            ClipboardMenuRow(
                                icon = Icons.Default.Delete,
                                label = "Borrar",
                                tint = MaterialTheme.colorScheme.error
                            ) {
                                FeedbackManager.triggerFeedback(context, FeedbackManager.HapticType.DELETE)
                                scope.launch {
                                    if (service != null) {
                                        service.deleteClipboardItem(item)
                                    } else {
                                        ClipboardHistoryManager.deleteItem(context, item)
                                    }
                                }
                                viewModel.setClipboardItemWithOptions(null)
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
            fontWeight = FontWeight.Bold
        )
    }
}