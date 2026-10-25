package com.example.hexkeyboard.ui.keyboard

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import kotlinx.coroutines.CoroutineScope
import com.example.hexkeyboard.logic.managers.ClipboardItem
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Assignment
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.hexkeyboard.R
import com.example.hexkeyboard.data.repository.KeyboardTheme
import com.example.hexkeyboard.data.repository.ThemeUtils
import com.example.hexkeyboard.data.repository.ThemeUtils.getKeyboardString
import com.example.hexkeyboard.logic.managers.ClipboardHistoryManager
import com.example.hexkeyboard.logic.managers.FeedbackManager
import com.example.hexkeyboard.service.HexKeyboardService
import com.example.hexkeyboard.ui.keyboard.panels.ClipboardImageThumbnail
import com.example.hexkeyboard.ui.settings.ClipboardEditActivity
import com.example.hexkeyboard.viewmodel.KeyboardViewModel
import kotlinx.coroutines.flow.map
import com.example.hexkeyboard.ui.component.backdrop.backdrops.rememberLayerBackdrop
import com.convx.music.ui.component.backdrop.backdrops.layerBackdrop
import com.example.hexkeyboard.ui.component.LocalAppBackdrop
import com.example.hexkeyboard.ui.component.LocalGlassEffectConfig
import com.example.hexkeyboard.ui.component.GlassEffectConfig
import com.example.hexkeyboard.ui.component.liquidGlass
import com.example.hexkeyboard.ui.keyboard.components.FloatingKeyboardContainer
import com.example.hexkeyboard.ui.keyboard.components.LocalKeyBounceEnabled
import com.example.hexkeyboard.ui.keyboard.components.rememberKeyBounceEnabled
import com.example.hexkeyboard.ui.keyboard.components.rememberMorphingShape
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

    val keyboardBackdrop = rememberLayerBackdrop()
    val glassConfigFlow = remember { ThemeUtils.getGlassEffectConfigFlow(context) }
    val savedGlassConfig by glassConfigFlow.collectAsState(initial = GlassEffectConfig())
    val glassConfig = remember(keyboardTheme, savedGlassConfig) {
        val glassTint = if (keyboardTheme.id == "m3_dynamic") {
            Color(keyboardTheme.backgroundColor)
        } else {
            Color(keyboardTheme.keyBackgroundColor)
        }
        val targetOpacity = if (keyboardTheme.keysOpacity >= 0.95f) {
            1.0f
        } else {
            (savedGlassConfig.surfaceOpacity * keyboardTheme.keysOpacity).coerceIn(0.15f, 1.0f)
        }
        savedGlassConfig.copy(
            surfaceTintColor = glassTint,
            surfaceOpacity = targetOpacity,
            textColor = Color(keyboardTheme.keyTextColor)
        )
    }

    val isGlassTheme = keyboardTheme.id == "glass"
    val hasBackgroundImage = keyboardTheme.backgroundImageUri != null

    var backgroundBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var blurredBitmap by remember { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(keyboardTheme.backgroundImageUri, keyboardTheme.backgroundBlur) {
        val bgUri = keyboardTheme.backgroundImageUri
        if (bgUri != null) {
            val newBitmap = withContext(Dispatchers.IO) {
                ThemeUtils.loadBitmapFromUri(context, bgUri)
            }
            if (newBitmap != null) {
                val oldBg = backgroundBitmap
                val oldBlur = blurredBitmap

                backgroundBitmap = newBitmap
                blurredBitmap = if (keyboardTheme.backgroundBlur > 0) {
                    withContext(Dispatchers.IO) {
                        ThemeUtils.blurBitmap(newBitmap, keyboardTheme.backgroundBlur)
                    }
                } else {
                    null
                }

                oldBg?.recycle()
                oldBlur?.recycle()
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

    val bottomOffsetFlow = remember { dataStore.data.map { it[ThemeUtils.KEYBOARD_BOTTOM_OFFSET] ?: 30 } }
    val bottomOffset by bottomOffsetFlow.collectAsState(30)

    val currentView by viewModel.currentView.collectAsState()
    val isEmojiSearchActive by viewModel.isEmojiSearchActive.collectAsState()
    val isCredentialsSearchActive by viewModel.isCredentialsSearchActive.collectAsState()
    val isSearchActive = isEmojiSearchActive || isCredentialsSearchActive
    val isFullPanel = currentView == "emoji" || currentView == "credentials" || currentView == "languages" || currentView == "clipboard" || currentView == "functions"
    val clipboardItemWithOptions by viewModel.clipboardItemWithOptions.collectAsState()

    val isFloatingMode by viewModel.isFloatingMode.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.loadFloatingSettings(context)
    }

    val glassShape = remember { RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp) }
    val keyBounceEnabled = rememberKeyBounceEnabled()

    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
    val isWideScreen = configuration.screenWidthDp > 600
    val maxDockedWidth = if (isLandscape || isWideScreen) 580.dp else Dp.Unspecified

    CompositionLocalProvider(
        LocalKeyBounceEnabled provides keyBounceEnabled,
        LocalAppBackdrop provides keyboardBackdrop,
        LocalGlassEffectConfig provides glassConfig
    ) {
        if (isFloatingMode) {
            FloatingKeyboardContainer(
                viewModel = viewModel,
                theme = keyboardTheme,
                modifier = Modifier.fillMaxSize(),
                backgroundImage = blurredBitmap ?: backgroundBitmap
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .wrapContentHeight()
                ) {
                    val mainSectionAlpha by animateFloatAsState(
                        targetValue = if (isFullPanel && !isSearchActive) 0f else 1f,
                        animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing),
                        label = "main_section_fade_floating"
                    )

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
                            KeyboardMainSection(viewModel, keyboardTheme, hasBackgroundImage)
                            if (!isFullPanel || isSearchActive) {
                                PanelsSection(viewModel, keyboardTheme, 0, 0)
                            }
                        }
                    }

                    if (isFullPanel && !isSearchActive) {
                        Box(
                            modifier = Modifier
                                .matchParentSize()
                                .clip(RoundedCornerShape(20.dp))
                        ) {
                            PanelsSection(viewModel, keyboardTheme, 0, 0)
                        }
                    }

                    if (clipboardItemWithOptions != null) {
                        val currentLocale by viewModel.currentLocale.collectAsState()
                        ClipboardOptionsMenuOverlay(
                            item = clipboardItemWithOptions!!,
                            keyboardTheme = keyboardTheme,
                            currentLocale = currentLocale,
                            context = context,
                            service = service,
                            scope = scope,
                            viewModel = viewModel,
                            isFloating = true,
                            modifier = Modifier
                                .matchParentSize()
                                .clip(RoundedCornerShape(20.dp))
                        )
                    }
                }
            }
        } else {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentHeight(),
                contentAlignment = Alignment.BottomCenter
            ) {
                Box(
                    modifier = Modifier
                        .then(if (maxDockedWidth != Dp.Unspecified) Modifier.widthIn(max = maxDockedWidth) else Modifier.fillMaxWidth())
                        .wrapContentHeight()
                        .clip(glassShape)
                ) {
                // Capa 1: Registro del Backdrop (Imagen de fondo o Gradiente Ambiental para el tema Glass)
                val img = blurredBitmap ?: backgroundBitmap
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .layerBackdrop(keyboardBackdrop)
                ) {
                    if (img != null && !img.isRecycled) {
                        Image(
                            bitmap = img.asImageBitmap(),
                            contentDescription = null,
                            modifier = Modifier
                                .fillMaxSize()
                                .alpha(keyboardTheme.backgroundOpacity),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(if (isGlassTheme) Color.Transparent else Color(keyboardTheme.backgroundColor))
                        )
                    }
                }

                // Capa 2: Efecto Liquid Glass Muestreando el Backdrop Registrado
                if (isGlassTheme) {
                    val isDarkText = Color(keyboardTheme.keyTextColor).luminance() < 0.5f
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .liquidGlass(
                                config = glassConfig.copy(
                                    surfaceOpacity = 0.08f,
                                    surfaceTintColor = if (isDarkText) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.08f)
                                ),
                                shape = glassShape,
                                applyEdgeEffects = true
                            )
                    )
                } else if (hasBackgroundImage) {
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .background(Color(keyboardTheme.backgroundColor).copy(alpha = 1f - keyboardTheme.backgroundOpacity))
                    )
                }

                val currentDensity = LocalDensity.current.density
                val navBarBottomDp = with(LocalDensity.current) {
                    val insetsNavDp = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding().value.toInt()
                    if (insetsNavDp > 0) {
                        insetsNavDp
                    } else {
                        val resId = context.resources.getIdentifier("navigation_bar_height", "dimen", "android")
                        if (resId > 0) (context.resources.getDimensionPixelSize(resId) / currentDensity).toInt() else 0
                    }
                }

                val mainSectionAlpha by animateFloatAsState(
                    targetValue = if (isFullPanel && !isSearchActive) 0f else 1f,
                    animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing),
                    label = "main_section_fade"
                )

                Column(
                    modifier = Modifier.fillMaxWidth()
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
                                KeyboardMainSection(viewModel, keyboardTheme, hasBackgroundImage)
                                if (!isFullPanel || isSearchActive) {
                                    PanelsSection(viewModel, keyboardTheme, bottomOffset, navBarBottomDp)
                                }
                            }

                            val totalBottomSpacerHeight = bottomOffset + navBarBottomDp
                            if (totalBottomSpacerHeight > 0) {
                                Spacer(modifier = Modifier.fillMaxWidth().height(totalBottomSpacerHeight.dp))
                            }
                        }

                        // Capa 2: Panel de Emojis/Credenciales/Idioma a pantalla completa con Animación Material 3 Expressive y Shadow Mask
                        AnimatedContent(
                            targetState = isFullPanel && !isSearchActive,
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
                                Box(
                                    modifier = Modifier
                                        .matchParentSize()
                                        .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                                ) {
                                    // El Panel ocupa la altura total de la capa base
                                    PanelsSection(viewModel, keyboardTheme, bottomOffset, navBarBottomDp)
                                }
                            }
                        }
                    }
                }

                if (clipboardItemWithOptions != null) {
                    val currentLocale by viewModel.currentLocale.collectAsState()
                    ClipboardOptionsMenuOverlay(
                        item = clipboardItemWithOptions!!,
                        keyboardTheme = keyboardTheme,
                        currentLocale = currentLocale,
                        context = context,
                        service = service,
                        scope = scope,
                        viewModel = viewModel,
                        isFloating = false,
                        modifier = Modifier
                            .matchParentSize()
                            .clip(glassShape)
                    )
                }
            }
        }
    }
}
}

@Composable
private fun ClipboardOptionsMenuOverlay(
    item: com.example.hexkeyboard.logic.managers.ClipboardItem,
    keyboardTheme: KeyboardTheme,
    currentLocale: String,
    context: Context,
    service: HexKeyboardService?,
    scope: CoroutineScope,
    viewModel: KeyboardViewModel,
    isFloating: Boolean,
    modifier: Modifier = Modifier
) {
    val menuBgColor = if (keyboardTheme.keysOpacity >= 0.95f) {
        Color(keyboardTheme.keyBackgroundColor).copy(alpha = 1.0f)
    } else {
        Color(keyboardTheme.keyBackgroundColor).copy(alpha = keyboardTheme.keysOpacity.coerceAtLeast(0.85f))
    }

    Box(
        modifier = modifier
            .background(Color.Black.copy(alpha = 0.55f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { viewModel.setClipboardItemWithOptions(null) },
        contentAlignment = Alignment.Center
    ) {
        AnimatedVisibility(
            visible = true,
            enter = scaleIn(
                initialScale = 0.80f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMediumLow
                )
            ) + fadeIn(animationSpec = spring(stiffness = Spring.StiffnessMediumLow)),
            exit = scaleOut(
                targetScale = 0.85f,
                animationSpec = spring(stiffness = Spring.StiffnessHigh)
            ) + fadeOut(animationSpec = tween(120))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = if (isFloating) 6.dp else 12.dp)
                    .clickable(enabled = false) {},
                contentAlignment = Alignment.Center
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(if (isFloating) 185.dp else 195.dp),
                    horizontalArrangement = Arrangement.spacedBy(if (isFloating) 6.dp else 10.dp)
                ) {
                    // Columna Izquierda: Menú de Acciones estilo Gboard
                    Surface(
                        modifier = Modifier
                            .width(if (isFloating) 125.dp else 145.dp)
                            .fillMaxHeight(),
                        shape = RoundedCornerShape(18.dp),
                        color = menuBgColor,
                        tonalElevation = 6.dp,
                        shadowElevation = 12.dp,
                        border = BorderStroke(1.dp, Color(keyboardTheme.keyTextColor).copy(alpha = 0.12f))
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            // Row 1: Pegar
                            ClipboardMenuRow(
                                icon = Icons.AutoMirrored.Outlined.Assignment,
                                label = context.getKeyboardString(R.string.menu_paste, currentLocale),
                                tint = Color(keyboardTheme.keyTextColor)
                            ) {
                                FeedbackManager.triggerFeedback(context, FeedbackManager.HapticType.KEY_CLICK)
                                viewModel.onClipboardItemClick(item)
                                viewModel.setClipboardItemWithOptions(null)
                            }

                            HorizontalDivider(color = Color(keyboardTheme.keyTextColor).copy(alpha = 0.12f))

                            // Row 2: Fijar / Desfijar
                            ClipboardMenuRow(
                                icon = if (item.isPinned) Icons.Default.PushPin else Icons.Outlined.PushPin,
                                label = if (item.isPinned) context.getKeyboardString(R.string.menu_unpin, currentLocale) else context.getKeyboardString(R.string.menu_pin, currentLocale),
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
                                HorizontalDivider(color = Color(keyboardTheme.keyTextColor).copy(alpha = 0.12f))

                                // Row 3: Editar
                                ClipboardMenuRow(
                                    icon = Icons.Outlined.Edit,
                                    label = context.getKeyboardString(R.string.menu_edit, currentLocale),
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

                            HorizontalDivider(color = Color(keyboardTheme.keyTextColor).copy(alpha = 0.12f))

                            // Row 4: Borrar
                            ClipboardMenuRow(
                                icon = Icons.Outlined.Delete,
                                label = context.getKeyboardString(R.string.menu_delete, currentLocale),
                                tint = Color.Red
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

                    // Columna Derecha: Tarjeta de Vista Previa del Texto / Imagen Copiada
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        shape = RoundedCornerShape(18.dp),
                        color = menuBgColor,
                        tonalElevation = 6.dp,
                        shadowElevation = 12.dp,
                        border = BorderStroke(1.dp, Color(keyboardTheme.keyTextColor).copy(alpha = 0.12f))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(14.dp)
                        ) {
                            if (item.isImage && !item.imageUri.isNullOrEmpty()) {
                                ClipboardImageThumbnail(
                                    imageUriString = item.imageUri,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clip(RoundedCornerShape(12.dp))
                                )
                            } else {
                                Column(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .verticalScroll(rememberScrollState())
                                ) {
                                    Text(
                                        text = item.text,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = Color(keyboardTheme.keyTextColor),
                                        lineHeight = 20.sp
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
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val shape = rememberMorphingShape(
        interactionSource = interactionSource,
        restCorner = 14.dp,
        pressedCorner = 8.dp
    )
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.94f else 1f,
        animationSpec = spring(
            dampingRatio = 0.6f,
            stiffness = 800f
        ),
        label = "expressive_menu_row_bounce"
    )
    val rowBgColor by animateColorAsState(
        targetValue = if (isPressed) tint.copy(alpha = 0.15f) else Color.Transparent,
        animationSpec = tween(120),
        label = "expressive_menu_row_bg"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .background(rowBgColor, shape = shape)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(20.dp)
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = label,
            color = tint,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}