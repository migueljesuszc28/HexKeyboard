package com.example.hexkeyboard.ui.settings

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.*
import com.example.hexkeyboard.data.repository.ThemeUtils
import com.example.hexkeyboard.data.repository.ThemeUtils.enableMaxRefreshRate
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import com.example.hexkeyboard.ui.settings.fontselector.FontSelectorActivity
import com.example.hexkeyboard.R
import com.example.hexkeyboard.ui.settings.themes.ThemeSettingsActivity
import com.example.hexkeyboard.ui.theme.HexKeyboardTheme

class SettingsActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableMaxRefreshRate()
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
        )

        setContent {
            HexKeyboardTheme {
                SettingsScreen(onBack = { finish() })
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val dataStore = ThemeUtils.getDataStore(context)

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
                            "Ajustes",
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
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                top = padding.calculateTopPadding() + 8.dp,
                bottom = padding.calculateBottomPadding() + 32.dp
            )
        ) {
            // --- SECCIÓN 1: Idioma y Distribución ---
            item { CategoryHeader("Idioma y Distribución", Icons.Default.Language) }
            item {
                SettingsCardContainer {
                    val langValueFlow = remember { dataStore.data.map { it[ThemeUtils.KEYBOARD_LANGUAGE] ?: "es" } }
                    val langValue by langValueFlow.collectAsState("es")
                    var showLangDialog by remember { mutableStateOf(false) }

                    ListItem(
                        headlineContent = { Text("Idioma del Teclado", fontWeight = FontWeight.SemiBold) },
                        supportingContent = {
                            Text(when(langValue) {
                                "es" -> "Español"
                                "en" -> "English"
                                else -> langValue
                            })
                        },
                        leadingContent = { Icon(Icons.Default.Language, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        modifier = Modifier.clickable { showLangDialog = true }
                    )

                    if (showLangDialog) {
                        AlertDialog(
                            onDismissRequest = { showLangDialog = false },
                            title = { Text("Seleccionar idioma") },
                            text = {
                                Column {
                                    listOf("es" to "Español", "en" to "English").forEach { (value, label) ->
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable {
                                                    scope.launch {
                                                        dataStore.edit { it[ThemeUtils.KEYBOARD_LANGUAGE] = value }
                                                    }
                                                    showLangDialog = false
                                                }
                                                .padding(16.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            RadioButton(selected = langValue == value, onClick = null)
                                            Spacer(Modifier.width(16.dp))
                                            Text(label)
                                        }
                                    }
                                }
                            },
                            confirmButton = {
                                TextButton(onClick = { showLangDialog = false }) { Text("Cancelar") }
                            }
                        )
                    }

                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                    val layoutValueFlow = remember { dataStore.data.map { it[ThemeUtils.KEYBOARD_LAYOUT_TYPE] ?: "default" } }
                    val layoutValue by layoutValueFlow.collectAsState("default")
                    var showLayoutDialog by remember { mutableStateOf(false) }

                    ListItem(
                        headlineContent = { Text("Distribución de Teclas", fontWeight = FontWeight.SemiBold) },
                        supportingContent = {
                            Text(when(layoutValue) {
                                "default" -> "Predeterminada (Optimización Hexagonal)"
                                "qwerty" -> "QWERTY (Clásica)"
                                else -> layoutValue
                            })
                        },
                        leadingContent = { Icon(Icons.Default.Keyboard, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        modifier = Modifier.clickable { showLayoutDialog = true }
                    )

                    if (showLayoutDialog) {
                        AlertDialog(
                            onDismissRequest = { showLayoutDialog = false },
                            title = { Text("Seleccionar distribución") },
                            text = {
                                Column {
                                    listOf("default" to "Predeterminada (Hexagonal)", "qwerty" to "QWERTY (Clásica)").forEach { (value, label) ->
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable {
                                                    scope.launch {
                                                        dataStore.edit { it[ThemeUtils.KEYBOARD_LAYOUT_TYPE] = value }
                                                    }
                                                    showLayoutDialog = false
                                                }
                                                .padding(16.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            RadioButton(selected = layoutValue == value, onClick = null)
                                            Spacer(Modifier.width(16.dp))
                                            Text(label)
                                        }
                                    }
                                }
                            },
                            confirmButton = {
                                TextButton(onClick = { showLayoutDialog = false }) { Text("Cancelar") }
                            }
                        )
                    }

                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                    ListItem(
                        headlineContent = { Text("Diccionario Personal", fontWeight = FontWeight.SemiBold) },
                        supportingContent = { Text("Ver y administrar palabras aprendidas") },
                        leadingContent = { Icon(Icons.Default.Book, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        modifier = Modifier.clickable {
                            context.startActivity(Intent(context, UserDictionaryActivity::class.java))
                        }
                    )

                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                    ListItem(
                        headlineContent = { Text("Cuentas y Contraseñas", fontWeight = FontWeight.SemiBold) },
                        supportingContent = { Text("Gestionar credenciales e importar CSV") },
                        leadingContent = { Icon(Icons.Default.Key, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        modifier = Modifier.clickable {
                            context.startActivity(Intent(context, CredentialsSettingsActivity::class.java))
                        }
                    )
                }
            }

            // --- SECCIÓN 2: Tema y Apariencia ---
            item { CategoryHeader("Tema y Apariencia", Icons.Default.Palette) }
            item {
                SettingsCardContainer {
                    val themeValueFlow = remember { dataStore.data.map { it[ThemeUtils.APP_THEME] ?: "system" } }
                    val themeValue by themeValueFlow.collectAsState("system")
                    var showThemeDialog by remember { mutableStateOf(false) }

                    ListItem(
                        headlineContent = { Text("Tema de la aplicación", fontWeight = FontWeight.SemiBold) },
                        supportingContent = {
                            Text(when(themeValue) {
                                "light" -> "Claro"
                                "dark" -> "Oscuro"
                                "m3_dynamic", "dynamic" -> "Material 3 Dinámico"
                                else -> "Seguir sistema"
                            })
                        },
                        leadingContent = { Icon(Icons.Default.Palette, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        modifier = Modifier.clickable { showThemeDialog = true }
                    )

                    if (showThemeDialog) {
                        AlertDialog(
                            onDismissRequest = { showThemeDialog = false },
                            title = { Text("Seleccionar tema") },
                            text = {
                                Column {
                                    listOf(
                                        "system" to "Seguir sistema",
                                        "light" to "Claro",
                                        "dark" to "Oscuro",
                                        "m3_dynamic" to "Material 3 Dinámico"
                                    ).forEach { (value, label) ->
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable {
                                                    scope.launch {
                                                        ThemeUtils.saveAppTheme(context, value)
                                                    }
                                                    applyAppTheme(value)
                                                    showThemeDialog = false
                                                }
                                                .padding(16.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            RadioButton(
                                                selected = themeValue == value || (value == "m3_dynamic" && themeValue == "dynamic"),
                                                onClick = null
                                            )
                                            Spacer(Modifier.width(16.dp))
                                            Text(label)
                                        }
                                    }
                                }
                            },
                            confirmButton = {
                                TextButton(onClick = { showThemeDialog = false }) { Text("Cancelar") }
                            }
                        )
                    }

                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                    ListItem(
                        headlineContent = { Text("Personalización del Teclado", fontWeight = FontWeight.SemiBold) },
                        supportingContent = { Text("Temas, colores y previsualización") },
                        leadingContent = { Icon(painter = painterResource(R.drawable.ic_hexagon), contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        modifier = Modifier.clickable {
                            context.startActivity(Intent(context, ThemeSettingsActivity::class.java))
                        }
                    )

                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                    val fontPathFlow = remember { dataStore.data.map { it[ThemeUtils.CUSTOM_FONT_PATH] ?: "system" } }
                    val fontPath by fontPathFlow.collectAsState("system")
                    ListItem(
                        headlineContent = { Text("Fuente del Teclado", fontWeight = FontWeight.SemiBold) },
                        supportingContent = {
                            Text(when(fontPath) {
                                "system" -> "Sistema"
                                "nunito" -> "Nunito"
                                else -> fontPath.substringAfterLast("/")
                            })
                        },
                        leadingContent = { Icon(Icons.Default.FontDownload, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        modifier = Modifier.clickable {
                            context.startActivity(Intent(context, FontSelectorActivity::class.java))
                        }
                    )

                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                    ListItem(
                        headlineContent = { Text("Efecto Liquid Glass", fontWeight = FontWeight.SemiBold) },
                        supportingContent = { Text("Ajustar desenfoque, refracción, vibrancia y estilo de cristal") },
                        leadingContent = { Icon(Icons.Default.WaterDrop, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        modifier = Modifier.clickable {
                            context.startActivity(Intent(context, LiquidGlassSettingsActivity::class.java))
                        }
                    )
                }
            }

            // --- SECCIÓN 3: Dimensiones y Tamaño ---
            item { CategoryHeader("Dimensiones y Tamaño", Icons.Default.Height) }
            item {
                SettingsCardContainer {
                    SliderPreference(
                        title = "Altura del teclado",
                        subtitle = "Ajusta la altura total del área de teclas",
                        key = ThemeUtils.KEYBOARD_HEIGHT,
                        defaultValue = 50,
                        min = 0,
                        max = 100,
                        icon = Icons.Default.Height,
                        context = context
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                    SliderPreference(
                        title = "Tamaño de las teclas",
                        subtitle = "Escala visual de los hexágonos y caracteres",
                        key = ThemeUtils.KEYBOARD_KEY_SIZE,
                        defaultValue = 90,
                        min = 50,
                        max = 100,
                        icon = Icons.Default.FitScreen,
                        context = context
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                    SliderPreference(
                        title = "Escala teclado numérico",
                        subtitle = "Ajusta el tamaño de la cuadrícula de números",
                        key = ThemeUtils.NUMERIC_KEY_SIZE_SCALE,
                        defaultValue = 85,
                        min = 70,
                        max = 120,
                        context = context
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                    SliderPreference(
                        title = "Margen inferior",
                        subtitle = "Espacio entre el teclado y la barra de navegación",
                        key = ThemeUtils.KEYBOARD_BOTTOM_OFFSET,
                        defaultValue = 30,
                        min = 0,
                        max = 100,
                        context = context
                    )
                }
            }

            // --- SECCIÓN 4: Sonido y Vibración ---
            item { CategoryHeader("Sonido y Vibración", Icons.Default.VolumeUp) }
            item {
                SettingsCardContainer {
                    SwitchPreference(
                        title = "Sonido al presionar",
                        subtitle = "Emitir un tono táctil al presionar cada tecla",
                        key = ThemeUtils.KEYBOARD_SOUND,
                        defaultValue = true,
                        icon = Icons.Default.VolumeUp,
                        context = context
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                    SliderPreference(
                        title = "Volumen del sonido",
                        key = ThemeUtils.KEYBOARD_SOUND_VOLUME,
                        defaultValue = 50,
                        min = 0,
                        max = 100,
                        context = context
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                    SwitchPreference(
                        title = "Vibración al presionar",
                        subtitle = "Respuesta háptica al escribir",
                        key = ThemeUtils.KEYBOARD_VIBRATION,
                        defaultValue = true,
                        icon = Icons.Default.Vibration,
                        context = context
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                    SliderPreference(
                        title = "Intensidad de vibración",
                        key = ThemeUtils.KEYBOARD_VIBRATION_INTENSITY,
                        defaultValue = 30,
                        min = 0,
                        max = 100,
                        context = context
                    )
                }
            }

            // --- SECCIÓN 5: Escritura y Corrección ---
            item { CategoryHeader("Escritura y Corrección", Icons.Default.Spellcheck) }
            item {
                SettingsCardContainer {
                    SwitchPreference(
                        title = "Auto-corrección",
                        subtitle = "Corregir automáticamente palabras al presionar espacio",
                        key = ThemeUtils.AUTO_CORRECT,
                        defaultValue = false,
                        icon = Icons.Default.Spellcheck,
                        context = context
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                    SwitchPreference(
                        title = "Deshacer corrección al borrar",
                        subtitle = "Restaurar la palabra original al pulsar retroceso",
                        key = ThemeUtils.UNDO_CORRECTION_ON_BACKSPACE,
                        defaultValue = true,
                        context = context
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                    SwitchPreference(
                        title = "Auto-mayúsculas",
                        subtitle = "Iniciar cada oración con mayúscula",
                        key = ThemeUtils.AUTO_CAPITALIZE,
                        defaultValue = true,
                        context = context
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                    SwitchPreference(
                        title = "Punto automático",
                        subtitle = "Insertar punto y espacio al presionar dos veces espacio",
                        key = ThemeUtils.DOUBLE_SPACE_PERIOD,
                        defaultValue = true,
                        context = context
                    )
                }
            }

            // --- SECCIÓN 6: Ajustes Avanzados ---
            item { CategoryHeader("Ajustes Avanzados", Icons.Default.Tune) }
            item {
                SettingsCardContainer {
                    SwitchPreference(
                        title = "Animación de toque teclas",
                        subtitle = "Efecto elástico al presionar las teclas",
                        key = ThemeUtils.KEY_BOUNCE_ANIMATION,
                        defaultValue = true,
                        icon = Icons.Default.TouchApp,
                        context = context
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                    SwitchPreference(
                        title = "Mostrar Pop-Up de tecla",
                        subtitle = "Vista previa ampliada de la tecla presionada",
                        key = ThemeUtils.SHOW_KEY_POPUP,
                        defaultValue = true,
                        icon = Icons.Default.SmartButton,
                        context = context
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                    SliderPreference(
                        title = "Tamaño del Pop-Up",
                        key = ThemeUtils.POPUP_SCALE,
                        defaultValue = 95,
                        min = 40,
                        max = 150,
                        context = context
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                    SliderPreference(
                        title = "Duración toque prolongado",
                        subtitle = "Tiempo para acceder a caracteres alternativos",
                        key = ThemeUtils.LONG_PRESS_DURATION,
                        defaultValue = 259,
                        min = 100,
                        max = 1000,
                        unit = " ms",
                        icon = Icons.Default.Timer,
                        context = context
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                    SwitchPreference(
                        title = "Indicadores de acentos",
                        subtitle = "Mostrar identificadores en teclas con opciones adicionales",
                        key = ThemeUtils.SHOW_LONG_PRESS_INDICATORS,
                        defaultValue = true,
                        context = context
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                    SwitchPreference(
                        title = "Reiniciar al cerrar",
                        subtitle = "Volver a la vista principal al ocultar el teclado",
                        key = ThemeUtils.RESET_ON_CLOSE,
                        defaultValue = true,
                        icon = Icons.Default.RestartAlt,
                        context = context
                    )
                }
            }

            // --- SECCIÓN 7: Portapapeles ---
            item { CategoryHeader("Portapapeles", Icons.Default.ContentPaste) }
            item {
                SettingsCardContainer {
                    SwitchPreference(
                        title = "Eliminación automática",
                        subtitle = "Borrar clips no fijados automáticamente tras el tiempo límite",
                        key = ThemeUtils.CLIPBOARD_AUTO_DELETE,
                        defaultValue = true,
                        icon = Icons.Default.ContentPaste,
                        context = context
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                    val expiryValueFlow = remember { dataStore.data.map { it[ThemeUtils.CLIPBOARD_EXPIRY_HOURS] ?: "1" } }
                    val expiryValue by expiryValueFlow.collectAsState("1")
                    var showExpiryDialog by remember { mutableStateOf(false) }

                    ListItem(
                        headlineContent = { Text("Tiempo de expiración", fontWeight = FontWeight.SemiBold) },
                        supportingContent = {
                            val labels = listOf("15 minutos", "1 hora", "3 horas", "6 horas", "12 horas", "24 horas")
                            val values = listOf("0.25", "1", "3", "6", "12", "24")
                            val index = values.indexOf(expiryValue).coerceAtLeast(0)
                            Text(labels[index])
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        modifier = Modifier.clickable { showExpiryDialog = true }
                    )

                    if (showExpiryDialog) {
                        AlertDialog(
                            onDismissRequest = { showExpiryDialog = false },
                            title = { Text("Tiempo de expiración") },
                            text = {
                                Column {
                                    val entries = listOf(
                                        "0.25" to "15 minutos",
                                        "1" to "1 hora",
                                        "3" to "3 horas",
                                        "6" to "6 horas",
                                        "12" to "12 horas",
                                        "24" to "24 horas"
                                    )
                                    entries.forEach { (value, label) ->
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable {
                                                    scope.launch {
                                                        dataStore.edit { it[ThemeUtils.CLIPBOARD_EXPIRY_HOURS] = value }
                                                    }
                                                    showExpiryDialog = false
                                                }
                                                .padding(16.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            RadioButton(selected = expiryValue == value, onClick = null)
                                            Spacer(Modifier.width(16.dp))
                                            Text(label)
                                        }
                                    }
                                }
                            },
                            confirmButton = {
                                TextButton(onClick = { showExpiryDialog = false }) { Text("Cancelar") }
                            }
                        )
                    }
                }
            }

            // --- RESTABLECER AJUSTES ---
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
                                dataStore.edit { it.clear() }
                                applyAppTheme("system")
                                val intent = Intent(context, SettingsActivity::class.java)
                                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                                context.startActivity(intent)
                            }
                        },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        modifier = Modifier.fillMaxWidth().height(50.dp),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Restablecer todos los ajustes", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun CategoryHeader(title: String, icon: ImageVector? = null) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 10.dp)
    ) {
        if (icon != null) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(32.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
fun SettingsCardContainer(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 1.dp
    ) {
        Column(modifier = Modifier.padding(vertical = 4.dp)) {
            content()
        }
    }
}

@Composable
fun SliderPreference(
    title: String,
    subtitle: String? = null,
    key: Preferences.Key<Int>,
    defaultValue: Int,
    min: Int,
    max: Int,
    unit: String = "",
    icon: ImageVector? = null,
    context: Context
) {
    val dataStore = ThemeUtils.getDataStore(context)
    val scope = rememberCoroutineScope()
    val prefs by dataStore.data.collectAsState(initial = emptyPreferences())
    val value = prefs[key] ?: defaultValue

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
                    modifier = Modifier
                        .size(22.dp)
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
                    text = "$value$unit",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                )
            }
        }
        Slider(
            value = value.toFloat(),
            onValueChange = { newValue ->
                scope.launch {
                    dataStore.edit { it[key] = newValue.toInt() }
                }
            },
            valueRange = min.toFloat()..max.toFloat(),
            modifier = Modifier
                .padding(start = 38.dp, top = 2.dp)
                .fillMaxWidth()
        )
    }
}

@Composable
fun SwitchPreference(
    title: String,
    subtitle: String? = null,
    key: Preferences.Key<Boolean>,
    defaultValue: Boolean,
    icon: ImageVector? = null,
    context: Context,
    onCheckedChange: ((Boolean) -> Unit)? = null
) {
    val dataStore = ThemeUtils.getDataStore(context)
    val scope = rememberCoroutineScope()
    val prefs by dataStore.data.collectAsState(initial = emptyPreferences())
    val checked = prefs[key] ?: defaultValue

    ListItem(
        headlineContent = {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold
            )
        },
        supportingContent = subtitle?.let {
            {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        leadingContent = icon?.let {
            {
                Icon(
                    imageVector = it,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp)
                )
            }
        },
        trailingContent = {
            Switch(
                checked = checked,
                onCheckedChange = { newValue ->
                    scope.launch {
                        dataStore.edit { it[key] = newValue }
                        onCheckedChange?.invoke(newValue)
                    }
                }
            )
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.clickable {
            val newValue = !checked
            scope.launch {
                dataStore.edit { it[key] = newValue }
                onCheckedChange?.invoke(newValue)
            }
        }
    )
}

fun applyAppTheme(themeValue: String) {
    when (themeValue) {
        "light" -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
        "dark" -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
        else -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
    }
}

@Preview(name = "Light Mode", showBackground = true, showSystemUi = true)
@Preview(
    name = "Dark Mode",
    uiMode = Configuration.UI_MODE_NIGHT_YES,
    showBackground = true,
    showSystemUi = true
)
@Composable
fun PreviewSettingsScreen() {
    HexKeyboardTheme {
        SettingsScreen(onBack = {})
    }
}
