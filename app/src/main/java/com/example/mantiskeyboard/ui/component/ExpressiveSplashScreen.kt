package com.example.mantiskeyboard.ui.component

import android.content.res.Configuration
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.mantiskeyboard.R
import com.example.mantiskeyboard.ui.theme.MantisKeyboardTheme
import kotlinx.coroutines.delay

@Composable
fun ExpressiveSplashScreen(
    onSplashFinished: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isPreview = androidx.compose.ui.platform.LocalInspectionMode.current
    val scaleAnim = remember { Animatable(if (isPreview) 1f else 0f) }
    var textVisible by remember { mutableStateOf(isPreview) }

    val infiniteTransition = rememberInfiniteTransition(label = "pulse_rings")
    val ring1Scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.35f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200),
            repeatMode = RepeatMode.Reverse
        ),
        label = "ring1_scale"
    )
    val ring1Alpha by infiniteTransition.animateFloat(
        initialValue = 0.45f,
        targetValue = 0.10f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200),
            repeatMode = RepeatMode.Reverse
        ),
        label = "ring1_alpha"
    )

    LaunchedEffect(Unit) {
        scaleAnim.animateTo(
            targetValue = 1f,
            animationSpec = spring(
                dampingRatio = 0.62f,
                stiffness = 320f
            )
        )
        textVisible = true
        delay(800)
        onSplashFinished()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(24.dp)
        ) {
            // Logo Badge con Anillos de Luz Concéntricos en Expansión M3
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(160.dp)
            ) {
                // Anillo exterior difuminado en pulso
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = ring1Alpha),
                    modifier = Modifier
                        .size(140.dp)
                        .scale(ring1Scale)
                ) {}

                // Badge central del logo
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    tonalElevation = 6.dp,
                    shadowElevation = 10.dp,
                    modifier = Modifier
                        .size(100.dp)
                        .scale(scaleAnim.value)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_hexagon),
                            contentDescription = "Hexagon Logo",
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(56.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Título y Subtítulo Animar con Entrada Suave
            AnimatedVisibility(
                visible = textVisible,
                enter = fadeIn(animationSpec = tween(400)) + slideInVertically(
                    initialOffsetY = { 30 },
                    animationSpec = spring(dampingRatio = 0.7f, stiffness = 400f)
                )
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "Mantis Keyboard",
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = "Teclado hexagonal optimizado e inteligente",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }

            Spacer(modifier = Modifier.height(48.dp))

            // Indicador de Progreso Discreto Expresivo M3
            AnimatedVisibility(
                visible = textVisible,
                enter = fadeIn(animationSpec = tween(300))
            ) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .width(120.dp)
                        .height(4.dp)
                        .alpha(0.7f),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                )
            }
        }
    }
}

@Preview(name = "Light Mode", showBackground = true)
@Preview(name = "Dark Mode", uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true)
@Composable
fun PreviewExpressiveSplashScreen() {
    MantisKeyboardTheme {
        ExpressiveSplashScreen(onSplashFinished = {})
    }
}