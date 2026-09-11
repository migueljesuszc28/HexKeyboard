package com.example.hexkeyboard.ui.settings

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.convx.music.ui.component.backdrop.backdrops.layerBackdrop
import com.convx.music.ui.component.backdrop.backdrops.rememberLayerBackdrop
import com.example.hexkeyboard.data.repository.ThemeUtils
import com.example.hexkeyboard.data.repository.ThemeUtils.enableMaxRefreshRate
import com.example.hexkeyboard.ui.component.GlassEffectConfig
import com.example.hexkeyboard.ui.component.GlassStyle
import com.example.hexkeyboard.ui.component.LocalAppBackdrop
import com.example.hexkeyboard.ui.component.LocalGlassEffectConfig
import com.example.hexkeyboard.ui.component.liquidGlass
import com.example.hexkeyboard.ui.theme.HexKeyboardTheme
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

class LiquidGlassSettingsActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableMaxRefreshRate()
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
        )

        setContent {
            HexKeyboardTheme {
                LiquidGlassSettingsScreen(onBack = { finish() })
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LiquidGlassSettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val glassConfigFlow = remember { ThemeUtils.getGlassEffectConfigFlow(context) }
    val currentConfig by glassConfigFlow.collectAsState(initial = GlassEffectConfig())

    val surfaceColor = MaterialTheme.colorScheme.surface
    val isDark = surfaceColor.luminance() < 0.5f

    val topBarMaskBrush = remember(isDark, surfaceColor) {
        if (isDark) {
            Brush.verticalGradient(
                colorStops = arrayOf(
                    0.0f to Color.Black.copy(alpha = 0.55f),
                    0.6f to Color.Black.copy(alpha = 0.20f),
                    1.0f to Color.Transparent
                )
            )
        } else {
            Brush.verticalGradient(
                colorStops = arrayOf(
                    0.0f to surfaceColor.copy(alpha = 0.92f),
                    0.6f to surfaceColor.copy(alpha = 0.50f),
                    1.0f to Color.Transparent
                )
            )
        }
    }

    Scaffold(
        topBar = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(brush = topBarMaskBrush)
            ) {
                TopAppBar(
                    title = {
                        Text(
                            "Efecto Liquid Glass",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                    },
                    navigationIcon = {
                        FilledTonalIconButton(
                            onClick = onBack,
                            shape = CircleShape
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Atrás"
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent,
                        scrolledContainerColor = Color.Transparent
                    )
                )
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = padding.calculateTopPadding())
        ) {
            // --- HEROS PREVIEW SECTION (Fijo en la parte superior) ---
            LiquidGlassLivePreviewCard(config = currentConfig)

            // --- LISTA DESPLAZABLE DE AJUSTES ---
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentPadding = PaddingValues(
                    top = 4.dp,
                    bottom = padding.calculateBottomPadding() + 32.dp
                )
            ) {
                // --- SECCIÓN 1: Estado y Estilo del Cristal ---
                item { CategoryHeader("Estado y Estilo del Cristal", Icons.Default.AutoAwesome) }
                item {
                    SettingsCardContainer {
                        SwitchPreference(
                            title = "Efecto Liquid Glass",
                            subtitle = "Activar efecto de cristal en la interfaz del teclado",
                            key = ThemeUtils.GLASS_GLOBAL_ENABLED,
                            defaultValue = true,
                            icon = Icons.Default.WaterDrop,
                            context = context
                        )

                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                        var showStyleDialog by remember { mutableStateOf(false) }

                        ListItem(
                            headlineContent = { Text("Estilo del Cristal", fontWeight = FontWeight.SemiBold) },
                            supportingContent = {
                                Text(
                                    when (currentConfig.style) {
                                        GlassStyle.LIQUID -> "Cristal Líquido Real (Lente, Brillo y Refracción)"
                                        GlassStyle.BLUR -> "Difuminado Esmerilado (Solo Blur Suave)"
                                        GlassStyle.TRANSPARENT -> "Translúcido Tintado Simple"
                                    }
                                )
                            },
                            leadingContent = { Icon(Icons.Default.Style, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                            modifier = Modifier.clickable { showStyleDialog = true }
                        )

                        if (showStyleDialog) {
                            AlertDialog(
                                onDismissRequest = { showStyleDialog = false },
                                title = { Text("Seleccionar estilo de cristal") },
                                text = {
                                    Column {
                                        listOf(
                                            GlassStyle.LIQUID to "Cristal Líquido Real (Refracción M3)",
                                            GlassStyle.BLUR to "Difuminado Esmerilado (Blur Suave)",
                                            GlassStyle.TRANSPARENT to "Translúcido Tintado Simple"
                                        ).forEach { (styleOption, label) ->
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clickable {
                                                        scope.launch {
                                                            ThemeUtils.saveGlassEffectConfig(
                                                                context,
                                                                currentConfig.copy(style = styleOption)
                                                            )
                                                        }
                                                        showStyleDialog = false
                                                    }
                                                    .padding(16.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                RadioButton(
                                                    selected = currentConfig.style == styleOption,
                                                    onClick = null
                                                )
                                                Spacer(Modifier.width(16.dp))
                                                Text(label)
                                            }
                                        }
                                    }
                                },
                                confirmButton = {
                                    TextButton(onClick = { showStyleDialog = false }) { Text("Cancelar") }
                                }
                            )
                        }
                    }
                }

                // --- SECCIÓN 2: Desenfoque y Refracción ---
                item { CategoryHeader("Desenfoque y Refracción", Icons.Default.BlurOn) }
                item {
                    SettingsCardContainer {
                        FloatSliderPreference(
                            title = "Radio de desenfoque (Blur)",
                            subtitle = "Intensidad del difuminado de fondo",
                            value = currentConfig.blurRadius,
                            min = 0f,
                            max = 50f,
                            unit = " dp",
                            icon = Icons.Default.BlurOn,
                            onValueChange = { newValue ->
                                scope.launch {
                                    ThemeUtils.saveGlassEffectConfig(
                                        context,
                                        currentConfig.copy(blurRadius = newValue)
                                    )
                                }
                            }
                        )

                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                        FloatSliderPreference(
                            title = "Vibrancia / Saturación",
                            subtitle = "Intensidad de color reflejada a través del cristal",
                            value = currentConfig.vibrancy * 100f / 1.2f,
                            min = 0f,
                            max = 166f,
                            unit = "%",
                            icon = Icons.Default.ColorLens,
                            onValueChange = { newValue ->
                                val vibrancyVal = (newValue / 100f) * 1.2f
                                scope.launch {
                                    ThemeUtils.saveGlassEffectConfig(
                                        context,
                                        currentConfig.copy(vibrancy = vibrancyVal)
                                    )
                                }
                            }
                        )

                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                        FloatSliderPreference(
                            title = "Altura de lente (Refracción)",
                            subtitle = "Curvatura 3D en los bordes de la lente",
                            value = currentConfig.lensHeight * 100f,
                            min = 0f,
                            max = 100f,
                            unit = "%",
                            icon = Icons.Default.Lens,
                            onValueChange = { newValue ->
                                scope.launch {
                                    ThemeUtils.saveGlassEffectConfig(
                                        context,
                                        currentConfig.copy(lensHeight = newValue / 100f)
                                    )
                                }
                            }
                        )

                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                        FloatSliderPreference(
                            title = "Cantidad de lente",
                            subtitle = "Magnitud del efecto de refracción óptica",
                            value = currentConfig.lensAmount * 100f,
                            min = 0f,
                            max = 100f,
                            unit = "%",
                            icon = Icons.Default.FitScreen,
                            onValueChange = { newValue ->
                                scope.launch {
                                    ThemeUtils.saveGlassEffectConfig(
                                        context,
                                        currentConfig.copy(lensAmount = newValue / 100f)
                                    )
                                }
                            }
                        )
                    }
                }

                // --- SECCIÓN 3: Superficie y Efectos 3D ---
                item { CategoryHeader("Superficie y Efectos 3D", Icons.Default.Layers) }
                item {
                    SettingsCardContainer {
                        FloatSliderPreference(
                            title = "Opacidad de superficie",
                            subtitle = "Transparencia de la capa de color sobre el cristal",
                            value = currentConfig.surfaceOpacity * 100f,
                            min = 0f,
                            max = 100f,
                            unit = "%",
                            icon = Icons.Default.Opacity,
                            onValueChange = { newValue ->
                                scope.launch {
                                    ThemeUtils.saveGlassEffectConfig(
                                        context,
                                        currentConfig.copy(surfaceOpacity = newValue / 100f)
                                    )
                                }
                            }
                        )

                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                        SwitchPreference(
                            title = "Efecto de profundidad",
                            subtitle = "Relieve tridimensional avanzado en bordes",
                            key = ThemeUtils.GLASS_DEPTH_EFFECT,
                            defaultValue = false,
                            icon = Icons.Default.ViewInAr,
                            context = context
                        )

                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                        SwitchPreference(
                            title = "Aberración cromática",
                            subtitle = "Dispersión prismática de color en los contornos",
                            key = ThemeUtils.GLASS_CHROMATIC_ABERRATION,
                            defaultValue = false,
                            icon = Icons.Default.FilterVintage,
                            context = context
                        )
                    }
                }

                // --- RESTABLECER AJUSTES DE CRISTAL ---
                item {
                    Spacer(modifier = Modifier.height(24.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        OutlinedButton(
                            onClick = {
                                scope.launch {
                                    ThemeUtils.resetGlassEffectConfig(context)
                                }
                            },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                            modifier = Modifier.fillMaxWidth().height(50.dp),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Restablecer ajustes de Liquid Glass", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun LiquidGlassLivePreviewCard(config: GlassEffectConfig) {
    val backdrop = rememberLayerBackdrop()

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Visibility,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "Previsualización en Vivo",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(Modifier.height(10.dp))

            CompositionLocalProvider(
                LocalAppBackdrop provides backdrop,
                LocalGlassEffectConfig provides config
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(150.dp)
                        .clip(RoundedCornerShape(20.dp))
                ) {
                    // Capa 1: Fondo colorido con filas de emojis que pasan DIRECTAMENTE detrás del cristal
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .layerBackdrop(backdrop)
                            .background(
                                brush = Brush.linearGradient(
                                    colors = listOf(
                                        Color(0xFF673AB7),
                                        Color(0xFF2196F3),
                                        Color(0xFF00CCD6),
                                        Color(0xFFFF4081)
                                    )
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp, vertical = 12.dp),
                            verticalArrangement = Arrangement.SpaceEvenly,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                Text("🎨", fontSize = 28.sp)
                                Text("🦄", fontSize = 28.sp)
                                Text("⭐", fontSize = 28.sp)
                                Text("🌈", fontSize = 28.sp)
                                Text("🔮", fontSize = 28.sp)
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                Text("😀", fontSize = 30.sp)
                                Text("🚀", fontSize = 30.sp)
                                Text("🔥", fontSize = 30.sp)
                                Text("✨", fontSize = 30.sp)
                                Text("💎", fontSize = 30.sp)
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                Text("🎉", fontSize = 28.sp)
                                Text("🐱", fontSize = 28.sp)
                                Text("🍕", fontSize = 28.sp)
                                Text("🍩", fontSize = 28.sp)
                                Text("🌺", fontSize = 28.sp)
                            }
                        }
                    }

                    // Capa 2: Elementos de Liquid Glass Flotando Directamente Sobre los Emojis
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(14.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            // Pestaña flotante de cristal
                            val categoryShape = remember { RoundedCornerShape(20.dp) }
                            Box(
                                modifier = Modifier
                                    .height(42.dp)
                                    .weight(1f)
                                    .liquidGlass(
                                        config = config,
                                        shape = categoryShape,
                                        highlightAlpha = 0.35f
                                    )
                                    .padding(horizontal = 14.dp),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Icon(Icons.Default.History, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                                    Icon(Icons.Default.Pets, contentDescription = null, tint = Color.White.copy(alpha = 0.6f), modifier = Modifier.size(20.dp))
                                    Icon(Icons.Default.Restaurant, contentDescription = null, tint = Color.White.copy(alpha = 0.6f), modifier = Modifier.size(20.dp))
                                    Icon(Icons.Default.EmojiEvents, contentDescription = null, tint = Color.White.copy(alpha = 0.6f), modifier = Modifier.size(20.dp))
                                }
                            }

                            Spacer(Modifier.width(12.dp))

                            // Botón flotante de cristal
                            val buttonShape = CircleShape
                            Box(
                                modifier = Modifier
                                    .size(45.dp)
                                    .liquidGlass(
                                        config = config,
                                        shape = buttonShape,
                                        highlightAlpha = 0.35f
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Filled.Backspace,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun FloatSliderPreference(
    title: String,
    subtitle: String? = null,
    value: Float,
    min: Float,
    max: Float,
    unit: String = "",
    icon: ImageVector? = null,
    onValueChange: (Float) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(16.dp))
            } else {
                Spacer(modifier = Modifier.width(38.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.secondaryContainer,
                modifier = Modifier.padding(start = 8.dp)
            ) {
                Text(
                    text = "${value.roundToInt()}$unit",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                )
            }
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = min..max,
            modifier = Modifier
                .padding(start = 38.dp, top = 2.dp)
                .fillMaxWidth()
        )
    }
}
