package com.example.mantiskeyboard.ui.settings

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.example.mantiskeyboard.R
import com.example.mantiskeyboard.data.repository.ThemeUtils.enableMaxRefreshRate
import com.example.mantiskeyboard.ui.theme.MantisKeyboardTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableMaxRefreshRate()
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
        )

        setContent {
            MantisKeyboardTheme {
                MainScreen(
                    onEnableClick = {
                        startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
                    },
                    onSelectClick = {
                        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
                        imm.showInputMethodPicker()
                    },
                    onSettingsClick = {
                        startActivity(Intent(this@MainActivity, SettingsActivity::class.java))
                    }
                )
            }
        }
    }
}

@Composable
fun MainScreen(
    onEnableClick: () -> Unit,
    onSelectClick: () -> Unit,
    onSettingsClick: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current
    val isPreview = LocalInspectionMode.current

    var isImeEnabled by remember { mutableStateOf(false) }
    var isImeSelected by remember { mutableStateOf(false) }

    fun checkImeState() {
        if (isPreview) return
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        val enabledList = imm?.enabledInputMethodList ?: emptyList()
        isImeEnabled = enabledList.any { it.packageName == context.packageName }

        val defaultIme = Settings.Secure.getString(context.contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)
        isImeSelected = defaultIme?.contains(context.packageName) == true
    }

    LaunchedEffect(Unit) {
        checkImeState()
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME || event == Lifecycle.Event.ON_START) {
                checkImeState()
                // Re-comprobar tras breves retrasos porque la configuración del sistema tarda unos ms en actualizarse
                scope.launch {
                    delay(250)
                    checkImeState()
                    delay(500)
                    checkImeState()
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val surfaceColor = MaterialTheme.colorScheme.surface
    val isDark = surfaceColor.luminance() < 0.5f

    val activity = context as? ComponentActivity
    DisposableEffect(isDark, activity) {
        activity?.enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT) { isDark },
            navigationBarStyle = SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT) { isDark }
        )
        onDispose {}
    }

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

    var textState by remember { mutableStateOf("") }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .imePadding()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(28.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    tonalElevation = 4.dp,
                    shadowElevation = 6.dp
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(bottom = 6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Keyboard,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Probar Teclado",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        OutlinedTextField(
                            value = textState,
                            onValueChange = { textState = it },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp),
                            placeholder = {
                                Text(
                                    "Escribe aquí para probar...",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            },
                            singleLine = true,
                            shape = RoundedCornerShape(16.dp),
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            },
                            trailingIcon = {
                                if (textState.isNotEmpty()) {
                                    IconButton(onClick = { textState = "" }) {
                                        Icon(
                                            imageVector = Icons.Default.Clear,
                                            contentDescription = "Limpiar",
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            },
                            colors = OutlinedTextFieldDefaults.colors(
                                unfocusedBorderColor = Color.Transparent,
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f),
                                focusedContainerColor = MaterialTheme.colorScheme.surface
                            )
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 16.dp,
                    bottom = 16.dp
                )
            ) {
                // --- HEROCARD / CABECERA EXPRESIVA DE BIENVENIDA ---
                item {
                    SettingsCardContainer {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primaryContainer,
                                modifier = Modifier.size(72.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        painter = painterResource(id = R.drawable.ic_hexagon),
                                        contentDescription = "Hexagon Logo",
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.size(40.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(14.dp))

                            Text(
                                text = "Mantis Keyboard",
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                textAlign = TextAlign.Center
                            )

                            Spacer(modifier = Modifier.height(4.dp))

                            Text(
                                text = "Teclado hexagonal optimizado, inteligente y expresivo",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )

                            Spacer(modifier = Modifier.height(14.dp))

                            // Badge con estado dinámico
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (isImeEnabled && isImeSelected) {
                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.8f)
                                } else {
                                    MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.6f)
                                }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = if (isImeEnabled && isImeSelected) Icons.Default.CheckCircle else Icons.Default.Warning,
                                        contentDescription = null,
                                        tint = if (isImeEnabled && isImeSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onErrorContainer,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = if (isImeEnabled && isImeSelected) "Teclado activo y listo" else "Configuración requerida",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isImeEnabled && isImeSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onErrorContainer
                                    )
                                }
                            }
                        }
                    }
                }

                // --- CONFIGURACIÓN RÁPIDA ---
                item { CategoryHeader("Configuración Rápida", Icons.Default.Tune) }
                item {
                    SettingsCardContainer {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            SetupStepButton(
                                stepNumber = "1",
                                text = "Habilitar teclado en Ajustes",
                                subtitle = if (isImeEnabled) "Habilitado en el sistema" else "Toca para habilitar en Ajustes de entrada",
                                icon = Icons.Default.Settings,
                                isCompleted = isImeEnabled,
                                isTonal = true,
                                onClick = onEnableClick
                            )

                            SetupStepButton(
                                stepNumber = "2",
                                text = "Seleccionar Mantis Keyboard",
                                subtitle = if (isImeSelected) "Seleccionado como teclado activo" else "Toca para elegir como teclado predeterminado",
                                icon = Icons.Default.Keyboard,
                                isCompleted = isImeSelected,
                                isTonal = true,
                                onClick = onSelectClick
                            )

                            SetupStepButton(
                                stepNumber = "3",
                                text = "Personalizar e Idiomas",
                                subtitle = "Temas, fuentes, idioma y cristal líquido",
                                icon = Icons.Outlined.Palette,
                                isCompleted = false,
                                isTonal = false,
                                onClick = onSettingsClick
                            )
                        }
                    }
                }
            }

            // Sombra / máscara de degradado superior sobre la barra de estado
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 16.dp)
                    .background(brush = topBarMaskBrush)
            )
        }
    }
}

@Composable
fun SetupStepButton(
    stepNumber: String,
    text: String,
    subtitle: String? = null,
    icon: ImageVector,
    isCompleted: Boolean = false,
    isTonal: Boolean = true,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val buttonShape = rememberMorphingShape(
        interactionSource = interactionSource,
        restCorner = 20.dp,
        pressedCorner = 10.dp
    )

    val modifier = Modifier
        .fillMaxWidth()
        .height(64.dp)

    if (isTonal) {
        FilledTonalButton(
            onClick = onClick,
            modifier = modifier,
            shape = buttonShape,
            interactionSource = interactionSource,
            contentPadding = PaddingValues(horizontal = 16.dp)
        ) {
            StepButtonContent(stepNumber, text, subtitle, icon, isCompleted)
        }
    } else {
        Button(
            onClick = onClick,
            modifier = modifier,
            shape = buttonShape,
            interactionSource = interactionSource,
            contentPadding = PaddingValues(horizontal = 16.dp)
        ) {
            StepButtonContent(stepNumber, text, subtitle, icon, isCompleted)
        }
    }
}

@Composable
fun StepButtonContent(
    stepNumber: String,
    text: String,
    subtitle: String?,
    icon: ImageVector,
    isCompleted: Boolean
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        Surface(
            shape = CircleShape,
            color = if (isCompleted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface.copy(alpha = 0.3f),
            modifier = Modifier.size(32.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                if (isCompleted) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Completado",
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                } else {
                    Text(
                        text = stepNumber,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = text,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Spacer(modifier = Modifier.width(8.dp))
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(20.dp)
        )
    }
}

@Preview(name = "Light Mode", showBackground = true, showSystemUi = true)
@Preview(name = "Dark Mode", uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true, showSystemUi = true)
@Composable
fun PreviewMainScreen() {
    MantisKeyboardTheme {
        MainScreen(
            onEnableClick = {},
            onSelectClick = {},
            onSettingsClick = {}
        )
    }
}