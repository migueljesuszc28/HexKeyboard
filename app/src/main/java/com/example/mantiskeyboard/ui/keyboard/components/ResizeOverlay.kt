package com.example.mantiskeyboard.ui.keyboard.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.OpenWith
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.mantiskeyboard.R
import com.example.mantiskeyboard.data.repository.KeyboardTheme
import com.example.mantiskeyboard.data.repository.ThemeUtils.getKeyboardString
import com.example.mantiskeyboard.viewmodel.KeyboardViewModel

@Composable
fun ResizeOverlay(
    viewModel: KeyboardViewModel,
    theme: KeyboardTheme,
    navBarBottomDp: Int = 0,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val density = LocalDensity.current

    val tempHeight by viewModel.tempKeyboardHeight.collectAsState()
    val tempOffset by viewModel.tempBottomOffset.collectAsState()

    val currentLocaleFlow = remember(viewModel) { viewModel.currentLocale }
    val currentLocale by currentLocaleFlow.collectAsState("es")

    // Extraer colores adaptables directamente del tema del teclado
    val rawAccent = theme.keyShiftActiveColor
        ?: theme.individualKeyColors["ENTER"]
        ?: theme.keyboardIconTint
    val accentColor = if (rawAccent != 0) Color(rawAccent) else MaterialTheme.colorScheme.primary

    val rawPillBg = if (theme.popupBackgroundColor != 0) {
        theme.popupBackgroundColor
    } else if (theme.keyBackgroundSpecialColor != 0) {
        theme.keyBackgroundSpecialColor
    } else {
        theme.keyBackgroundColor
    }
    val controlContainerColor = if (rawPillBg != 0) Color(rawPillBg).copy(alpha = 0.95f) else MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.95f)

    val rawTextColor = if (theme.popupTextColor != 0) theme.popupTextColor else theme.keyTextColor
    val controlContentColor = if (rawTextColor != 0) Color(rawTextColor) else MaterialTheme.colorScheme.onSurface

    val shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    val bottomExclusionDp = (tempOffset + navBarBottomDp).dp

    // Cálculo determinista y fluido de la altura del marco verde en Compose (evita el desfase de recálculo y parpadeo)
    val heightFactor = 0.7f + (tempHeight / 100f) * 0.6f
    val frameHeightDp = (46f + 210f * heightFactor).dp

    var dragHeightStart by remember { mutableIntStateOf(tempHeight) }
    var dragHeightAccumulatedY by remember { mutableFloatStateOf(0f) }

    var dragOffsetStart by remember { mutableIntStateOf(tempOffset) }
    var dragOffsetAccumulatedY by remember { mutableFloatStateOf(0f) }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.BottomCenter
    ) {
        // Capa 1: Fondo atenuado que absorbe los toques que caigan fuera de los controles sin bloquearlos
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.35f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {
                    /* Absorbe toques en el fondo de forma no invasiva */
                }
        )

        // Capa 2: Marco verde envolvente determinista con los tiradores y botones en primer plano
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = bottomExclusionDp)
                .height(frameHeightDp)
                .clip(shape)
                .border(3.dp, accentColor, shape)
                .background(Color.Transparent),
            contentAlignment = Alignment.Center
        ) {
            // Tirador superior de arrastre de altura (Top Drag Handle)
            Surface(
                shape = CircleShape,
                color = Color.Transparent,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 4.dp)
                    .size(width = 96.dp, height = 40.dp)
                    .pointerInput(Unit) {
                        detectVerticalDragGestures(
                            onDragStart = {
                                dragHeightAccumulatedY = 0f
                                dragHeightStart = tempHeight
                            },
                            onVerticalDrag = { change, dragAmount ->
                                change.consume()
                                dragHeightAccumulatedY += dragAmount
                                val deltaSteps = ((-dragHeightAccumulatedY / density.density) / 2.5f).toInt()
                                val newHeight = (dragHeightStart + deltaSteps).coerceIn(30, 100)
                                viewModel.updateTempResizeValues(newHeight, tempOffset)
                            }
                        )
                    }
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    Box(
                        modifier = Modifier
                            .width(56.dp)
                            .height(6.dp)
                            .clip(CircleShape)
                            .background(accentColor)
                    )
                }
            }

            // Panel de Control Central Flotante Centrado (Restablecer | Move Pad | Listo)
            Row(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 1. Botón "Restablecer" (Pill Izquierda)
                Surface(
                    onClick = {
                        viewModel.resetResizeToDefaults()
                    },
                    shape = CircleShape,
                    color = controlContainerColor,
                    contentColor = controlContentColor,
                    shadowElevation = 6.dp,
                    modifier = Modifier.height(44.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = controlContentColor
                        )
                        Text(
                            text = context.getKeyboardString(R.string.btn_reset, currentLocale),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = controlContentColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                // 2. Pad de Arrastre/Movimiento Central (Botón Circular 4 Flechas)
                Surface(
                    shape = CircleShape,
                    color = controlContainerColor,
                    contentColor = controlContentColor,
                    shadowElevation = 8.dp,
                    modifier = Modifier
                        .size(48.dp)
                        .pointerInput(Unit) {
                            detectVerticalDragGestures(
                                onDragStart = {
                                    dragOffsetAccumulatedY = 0f
                                    dragOffsetStart = tempOffset
                                },
                                onVerticalDrag = { change, dragAmount ->
                                    change.consume()
                                    dragOffsetAccumulatedY += dragAmount
                                    val deltaOffset = ((-dragOffsetAccumulatedY / density.density) / 2.5f).toInt()
                                    val newOffset = (dragOffsetStart + deltaOffset).coerceIn(0, 80)
                                    viewModel.updateTempResizeValues(tempHeight, newOffset)
                                }
                            )
                        }
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        Icon(
                            imageVector = Icons.Default.OpenWith,
                            contentDescription = "Mover",
                            modifier = Modifier.size(24.dp),
                            tint = accentColor
                        )
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                // 3. Botón "Listo" (Pill Derecha)
                Surface(
                    onClick = {
                        viewModel.saveResizeAndExit(context)
                    },
                    shape = CircleShape,
                    color = controlContainerColor,
                    contentColor = controlContentColor,
                    shadowElevation = 6.dp,
                    modifier = Modifier.height(44.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = accentColor
                        )
                        Text(
                            text = context.getKeyboardString(R.string.btn_done, currentLocale),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = controlContentColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}
