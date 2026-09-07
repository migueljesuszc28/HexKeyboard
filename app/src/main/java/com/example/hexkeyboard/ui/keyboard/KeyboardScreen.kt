package com.example.hexkeyboard.ui.keyboard

import android.graphics.Bitmap
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.preference.PreferenceManager
import com.example.hexkeyboard.data.repository.ThemeUtils
import com.example.hexkeyboard.viewmodel.KeyboardViewModel
import kotlinx.coroutines.flow.map
import dev.chrisbanes.haze.HazeDefaults
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun KeyboardScreen(viewModel: KeyboardViewModel) {
    val context = LocalContext.current
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
    val isEmojiPanel = currentView == "emoji"

    Box(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight()
                .background(
                    color = Color(keyboardTheme.backgroundColor),
                    shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
                )
                .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                .then(if (isGlassTheme || isEmojiPanel) Modifier.hazeSource(hazeState) else Modifier)
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

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .then(if (isEmojiPanel && !isEmojiSearchActive) Modifier else Modifier.navigationBarsPadding())
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
                        .then(if (isEmojiPanel && !isEmojiSearchActive) Modifier.alpha(0f) else Modifier)
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
                        if (!isEmojiPanel || isEmojiSearchActive) {
                            PanelsSection(viewModel, keyboardTheme, hazeState, bottomOffset, navBarBottomDp)
                        }
                    }

                    val totalBottomSpacerHeight = if (isEmojiPanel && !isEmojiSearchActive) (bottomOffset + navBarBottomDp) else bottomOffset
                    if (totalBottomSpacerHeight > 0) {
                        Spacer(modifier = Modifier.fillMaxWidth().height(totalBottomSpacerHeight.dp))
                    }
                }

                // Capa 2: Panel de Emojis a pantalla completa con Shadow Mask
                if (isEmojiPanel && !isEmojiSearchActive) {
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
                                0.4f to baseThemeColor.copy(alpha = 0.20f),
                                1.0f to baseThemeColor.copy(alpha = 0.55f)
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
}
