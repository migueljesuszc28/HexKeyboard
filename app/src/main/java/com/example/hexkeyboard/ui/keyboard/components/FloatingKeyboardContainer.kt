package com.example.hexkeyboard.ui.keyboard.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.convx.music.ui.component.backdrop.backdrops.layerBackdrop
import com.example.hexkeyboard.ui.component.backdrop.backdrops.rememberLayerBackdrop
import com.example.hexkeyboard.data.repository.KeyboardTheme
import com.example.hexkeyboard.logic.managers.FeedbackManager
import com.example.hexkeyboard.ui.component.LocalGlassEffectConfig
import com.example.hexkeyboard.ui.component.liquidGlass
import com.example.hexkeyboard.viewmodel.KeyboardViewModel
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

@Composable
fun FloatingKeyboardContainer(
    viewModel: KeyboardViewModel,
    theme: KeyboardTheme,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val density = LocalDensity.current

    val offsetX by viewModel.floatingOffsetX.collectAsState()
    val offsetY by viewModel.floatingOffsetY.collectAsState()
    val scale by viewModel.floatingScale.collectAsState()

    var dragAccumX by remember { mutableFloatStateOf(0f) }
    var dragAccumY by remember { mutableFloatStateOf(0f) }

    var measuredCardWidthPx by remember { mutableFloatStateOf(0f) }
    var measuredCardHeightPx by remember { mutableFloatStateOf(0f) }

    // Estado para ocultado automático de los indicadores de redimensionamiento
    var lastInteractionTime by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var isHandlesVisible by remember { mutableStateOf(true) }

    LaunchedEffect(lastInteractionTime) {
        isHandlesVisible = true
        delay(3000)
        isHandlesVisible = false
    }

    fun notifyUserInteraction() {
        lastInteractionTime = System.currentTimeMillis()
    }

    val configuration = LocalConfiguration.current
    val screenWidthPx = with(density) { configuration.screenWidthDp.dp.toPx() }
    val screenHeightPx = with(density) { configuration.screenHeightDp.dp.toPx() }

    val navBarBottomPx = with(density) { WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding().toPx() }
    val statusBarTopPx = with(density) { WindowInsets.statusBars.asPaddingValues().calculateTopPadding().toPx() }

    val effectiveWidthPx = if (measuredCardWidthPx > 0f) measuredCardWidthPx else with(density) { (310f * scale).dp.toPx() }
    val effectiveHeightPx = if (measuredCardHeightPx > 0f) measuredCardHeightPx else with(density) { (260f * scale).dp.toPx() }

    val marginPx = with(density) { 8.dp.toPx() }
    val topMarginPx = marginPx + maxOf(statusBarTopPx, with(density) { 12.dp.toPx() })
    val bottomMarginPx = marginPx + maxOf(navBarBottomPx, with(density) { 32.dp.toPx() })

    val maxOffsetX = maxOf(0f, (screenWidthPx - effectiveWidthPx) / 2f - marginPx)
    val minOffsetX = -maxOffsetX
    val maxOffsetY = maxOf(0f, (screenHeightPx - effectiveHeightPx) / 2f - bottomMarginPx)
    val minOffsetY = -maxOf(0f, (screenHeightPx - effectiveHeightPx) / 2f - topMarginPx)

    // Animación fluida de escala y desvanecimiento para los tiradores de las esquinas
    val handleAlpha by animateFloatAsState(
        targetValue = if (isHandlesVisible) 1f else 0f,
        animationSpec = tween(durationMillis = if (isHandlesVisible) 220 else 180),
        label = "handles_alpha"
    )

    val handleScale by animateFloatAsState(
        targetValue = if (isHandlesVisible) 1f else 0.6f,
        animationSpec = if (isHandlesVisible) {
            spring(dampingRatio = 0.72f, stiffness = 500f)
        } else {
            tween(durationMillis = 180)
        },
        label = "handles_scale"
    )

    // Auto-reclamp al escalar o medir la tarjeta para no salirse jamás de la pantalla
    val clampedX = offsetX.coerceIn(minOffsetX, maxOffsetX)
    val clampedY = offsetY.coerceIn(minOffsetY, maxOffsetY)
    LaunchedEffect(clampedX, clampedY) {
        if (clampedX != offsetX || clampedY != offsetY) {
            viewModel.updateFloatingPosition(clampedX, clampedY, context)
        }
    }

    val cardShape = remember { RoundedCornerShape(20.dp) }
    val isGlassTheme = theme.id == "glass"
    val accentBlue = Color(0xFF1A73E8) // Azul de tiradores estilo Gboard

    val glassConfig = LocalGlassEffectConfig.current
    val backdrop = rememberLayerBackdrop()
    val scaledWidth = (310f * scale).dp

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Transparent),
        contentAlignment = Alignment.Center
    ) {
        // Caja de la tarjeta flotante con transformación de posición y ancho dinámico
        Box(
            modifier = Modifier
                .offset { IntOffset(clampedX.roundToInt(), clampedY.roundToInt()) }
                .width(scaledWidth)
                .wrapContentHeight()
                .padding(4.dp)
                .onGloballyPositioned { coordinates ->
                    measuredCardWidthPx = coordinates.size.width.toFloat()
                    measuredCardHeightPx = coordinates.size.height.toFloat()
                    val bounds = coordinates.boundsInWindow()
                    val rect = android.graphics.Rect(
                        bounds.left.roundToInt(),
                        bounds.top.roundToInt(),
                        bounds.right.roundToInt(),
                        bounds.bottom.roundToInt()
                    )
                    viewModel.updateFloatingCardRect(rect)
                }
        ) {
            // Tarjeta principal del teclado
            Surface(
                shape = cardShape,
                color = if (isGlassTheme) Color.Transparent else Color(theme.backgroundColor),
                tonalElevation = 8.dp,
                shadowElevation = 14.dp,
                border = BorderStroke(
                    1.5.dp,
                    Color(theme.keyStrokeColor).copy(alpha = 0.25f)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentHeight()
                    .clip(cardShape)
            ) {
                Box(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // Capa de fondo Liquid Glass / Transparencia si aplica el tema glass
                    if (isGlassTheme) {
                        val isDarkText = Color(theme.keyTextColor).luminance() < 0.5f
                        Box(
                            modifier = Modifier
                                .matchParentSize()
                                .layerBackdrop(backdrop)
                                .liquidGlass(
                                    config = glassConfig.copy(
                                        surfaceOpacity = 0.12f,
                                        surfaceTintColor = if (isDarkText) Color.White.copy(alpha = 0.12f) else Color.Black.copy(alpha = 0.12f)
                                    ),
                                    shape = cardShape,
                                    applyEdgeEffects = true
                                )
                        )
                    }

                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Contenido del teclado (Sugerencias, Teclado, Paneles)
                        content()

                        // Barra inferior de arrastre (Pill Handle estilo Gboard)
                        Spacer(modifier = Modifier.height(2.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(28.dp)
                                .pointerInput(Unit) {
                                    detectTapGestures(
                                        onDoubleTap = {
                                            notifyUserInteraction()
                                            FeedbackManager.triggerFeedback(context, FeedbackManager.HapticType.KEY_CLICK)
                                            viewModel.resetFloatingPosition(context)
                                        }
                                    )
                                }
                                .pointerInput(Unit) {
                                    detectDragGestures(
                                        onDragStart = {
                                            notifyUserInteraction()
                                            dragAccumX = offsetX
                                            dragAccumY = offsetY
                                            FeedbackManager.triggerFeedback(context, FeedbackManager.HapticType.KEY_CLICK)
                                        },
                                        onDrag = { change, dragAmount ->
                                            notifyUserInteraction()
                                            change.consume()
                                            dragAccumX = (dragAccumX + dragAmount.x).coerceIn(minOffsetX, maxOffsetX)
                                            dragAccumY = (dragAccumY + dragAmount.y).coerceIn(minOffsetY, maxOffsetY)
                                            viewModel.updateFloatingPosition(dragAccumX, dragAccumY)
                                        },
                                        onDragEnd = {
                                            notifyUserInteraction()
                                            viewModel.updateFloatingPosition(dragAccumX, dragAccumY, context)
                                        }
                                    )
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .width(48.dp)
                                    .height(5.dp)
                                    .background(
                                        Color(theme.keyTextColor).copy(alpha = 0.40f),
                                        CircleShape
                                    )
                            )
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                    }
                }
            }

            // Esquinas visuales de redimensionamiento con animación fluida de entrada/salida (Fade + Scale Spring)
            if (handleAlpha > 0f) {
                CornerHandlesOverlay(
                    accentColor = accentBlue,
                    onResizeScaleDelta = { scaleDelta ->
                        notifyUserInteraction()
                        val newScale = (scale + scaleDelta).coerceIn(0.60f, 1.20f)
                        viewModel.updateFloatingScale(newScale, context)
                    },
                    modifier = Modifier
                        .matchParentSize()
                        .graphicsLayer {
                            alpha = handleAlpha
                            scaleX = handleScale
                            scaleY = handleScale
                        }
                )
            }
        }
    }
}

@Composable
private fun CornerHandlesOverlay(
    accentColor: Color,
    onResizeScaleDelta: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val strokeWidthPx = with(LocalDensity.current) { 3.5.dp.toPx() }
    val cornerSizePx = with(LocalDensity.current) { 16.dp.toPx() }

    Box(
        modifier = modifier
    ) {
        // Esquina Inferior Izquierda (Bottom-Left)
        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .offset(x = (-12).dp, y = 12.dp)
                .size(24.dp)
                .pointerInput(Unit) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        onResizeScaleDelta((-dragAmount.x + dragAmount.y) * 0.002f)
                    }
                }
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val path = Path().apply {
                    moveTo(0f, 0f)
                    lineTo(0f, size.height - cornerSizePx)
                    quadraticTo(0f, size.height, cornerSizePx, size.height)
                    lineTo(size.width, size.height)
                }
                drawPath(
                    path = path,
                    color = accentColor,
                    style = Stroke(width = strokeWidthPx, cap = StrokeCap.Round)
                )
            }
        }

        // Esquina Inferior Derecha (Bottom-Right)
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .offset(x = 12.dp, y = 12.dp)
                .size(24.dp)
                .pointerInput(Unit) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        onResizeScaleDelta((dragAmount.x + dragAmount.y) * 0.002f)
                    }
                }
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val path = Path().apply {
                    moveTo(size.width - cornerSizePx, size.height)
                    quadraticTo(size.width, size.height, size.width, size.height - cornerSizePx)
                    lineTo(size.width, 0f)
                }
                drawPath(
                    path = path,
                    color = accentColor,
                    style = Stroke(width = strokeWidthPx, cap = StrokeCap.Round)
                )
            }
        }

        // Esquina Superior Izquierda (Top-Left)
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .offset(x = (-12).dp, y = (-12).dp)
                .size(24.dp)
                .pointerInput(Unit) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        onResizeScaleDelta((-dragAmount.x - dragAmount.y) * 0.002f)
                    }
                }
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val path = Path().apply {
                    moveTo(0f, size.height)
                    lineTo(0f, cornerSizePx)
                    quadraticTo(0f, 0f, cornerSizePx, 0f)
                    lineTo(size.width, 0f)
                }
                drawPath(
                    path = path,
                    color = accentColor,
                    style = Stroke(width = strokeWidthPx, cap = StrokeCap.Round)
                )
            }
        }

        // Esquina Superior Derecha (Top-Right)
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(x = 12.dp, y = (-12).dp)
                .size(24.dp)
                .pointerInput(Unit) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        onResizeScaleDelta((dragAmount.x - dragAmount.y) * 0.002f)
                    }
                }
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val path = Path().apply {
                    moveTo(size.width - cornerSizePx, 0f)
                    quadraticTo(size.width, 0f, size.width, cornerSizePx)
                    lineTo(size.width, size.height)
                }
                drawPath(
                    path = path,
                    color = accentColor,
                    style = Stroke(width = strokeWidthPx, cap = StrokeCap.Round)
                )
            }
        }
    }
}
