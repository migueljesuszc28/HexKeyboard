package com.example.hexkeyboard.ui.component

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.hexkeyboard.data.repository.ThemeUtils

@Composable
fun ExpressiveLoadingScreen(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    icon: ImageVector? = null,
    showSkeletons: Boolean = true
) {
    val context = LocalContext.current
    val appTheme by ThemeUtils.getAppThemeFlow(context).collectAsState(initial = "system")
    val isM3Theme = remember(appTheme) { appTheme == "m3_dynamic" || appTheme == "dynamic" }

    val infiniteTransition = rememberInfiniteTransition(label = "expressive_loading")

    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.90f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )

    val haloAlpha by infiniteTransition.animateFloat(
        initialValue = 0.20f,
        targetValue = 0.60f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1400, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "halo_alpha"
    )

    val shimmerTranslate by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1300, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmer_translate"
    )

    val primaryColor = MaterialTheme.colorScheme.primary
    val containerColor = if (isM3Theme) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh
    val onContainerColor = if (isM3Theme) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
    val haloColor = if (isM3Theme) MaterialTheme.colorScheme.secondaryContainer.copy(alpha = haloAlpha) else primaryColor.copy(alpha = haloAlpha * 0.25f)

    val shimmerBrush = remember(shimmerTranslate, primaryColor) {
        Brush.linearGradient(
            colors = listOf(
                primaryColor.copy(alpha = 0.05f),
                primaryColor.copy(alpha = 0.18f),
                primaryColor.copy(alpha = 0.05f)
            ),
            start = androidx.compose.ui.geometry.Offset(shimmerTranslate - 300f, shimmerTranslate - 300f),
            end = androidx.compose.ui.geometry.Offset(shimmerTranslate, shimmerTranslate)
        )
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Contenedor de Halo e Indicador Expresivo
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(140.dp)
            ) {
                // Anillo de Halo exterior
                Box(
                    modifier = Modifier
                        .size(130.dp)
                        .scale(pulseScale)
                        .clip(CircleShape)
                        .background(haloColor)
                )

                // Anillo interior de progreso
                CircularProgressIndicator(
                    modifier = Modifier.size(96.dp),
                    color = primaryColor,
                    strokeWidth = 4.dp,
                    trackColor = primaryColor.copy(alpha = 0.12f)
                )

                // Contenedor del Icono
                Surface(
                    shape = RoundedCornerShape(28.dp),
                    color = containerColor,
                    tonalElevation = 6.dp,
                    shadowElevation = 4.dp,
                    modifier = Modifier
                        .size(64.dp)
                        .scale(pulseScale)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        if (icon != null) {
                            Icon(
                                imageVector = icon,
                                contentDescription = null,
                                tint = onContainerColor,
                                modifier = Modifier.size(30.dp)
                            )
                        } else {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                color = onContainerColor,
                                strokeWidth = 2.5.dp
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Título Expresivo
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )

            if (!subtitle.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }

            if (showSkeletons) {
                Spacer(modifier = Modifier.height(32.dp))
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    repeat(3) {
                        SkeletonCardItem(shimmerBrush = shimmerBrush)
                    }
                }
            }
        }
    }
}

@Composable
private fun SkeletonCardItem(shimmerBrush: Brush) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 1.dp
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(shimmerBrush)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                )
                Column(
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.55f)
                            .height(14.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f))
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.35f)
                            .height(10.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f))
                    )
                }
            }
        }
    }
}
