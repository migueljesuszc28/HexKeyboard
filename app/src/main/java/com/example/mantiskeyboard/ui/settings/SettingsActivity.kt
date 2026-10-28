package com.example.mantiskeyboard.ui.settings

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.widget.Toast
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Assignment
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.os.LocaleListCompat
import androidx.datastore.preferences.core.*
import com.example.mantiskeyboard.data.repository.ThemeUtils
import com.example.mantiskeyboard.data.repository.ThemeUtils.enableMaxRefreshRate
import com.example.mantiskeyboard.service.KeyboardService
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import com.example.mantiskeyboard.ui.settings.fontselector.FontSelectorActivity
import com.example.mantiskeyboard.R
import com.example.mantiskeyboard.ui.settings.themes.ThemeSettingsActivity
import com.example.mantiskeyboard.ui.theme.MantisKeyboardTheme

class SettingsActivity : AppCompatActivity() {

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(ThemeUtils.getAppLanguageContext(newBase))
    }

    @OptIn(ExperimentalMaterial3ExpressiveApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableMaxRefreshRate()

        // Se mantiene para el primer renderizado antes de que Compose tome el control
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
        )

        setContent {
            // 1. Obtenemos el tema guardado directamente desde el inicio del contexto de Compose
            val dataStore = ThemeUtils.getDataStore(this)
            val initialTheme = remember(this) { ThemeUtils.getAppThemeSync(this) }
            val themeValueFlow = remember { dataStore.data.map { it[ThemeUtils.APP_THEME] ?: "system" } }
            val themeValue by themeValueFlow.collectAsState(initial = initialTheme)

            // 2. Evaluamos si debe ser oscuro en base a la configuración y al sistema
            val isSystemDark = isSystemInDarkTheme()
            val isDark = when (themeValue) {
                "light" -> false
                "dark" -> true
                else -> isSystemDark
            }

            val isDynamic = themeValue == "m3_dynamic" || themeValue == "dynamic"

            // 3. Forzamos a la barra de estado y navegación a respetar el tema interno (evita íconos invisibles)
            DisposableEffect(isDark) {
                enableEdgeToEdge(
                    statusBarStyle = SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT) { isDark },
                    navigationBarStyle = SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT) { isDark }
                )
                onDispose {}
            }

            // 4. Inyectamos los estados directamente al tema principal
            MantisKeyboardTheme(
                darkTheme = isDark,
                dynamicColor = isDynamic
            ) {
                MaterialExpressiveTheme(
                    colorScheme = MaterialTheme.colorScheme,
                    typography = MaterialTheme.typography
                ) {
                    SettingsScreen(onBack = { finish() })
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Material 3 Expressive (v1.5.0-beta01)
// ---------------------------------------------------------------------------

private val GroupCornerRadius = 28.dp
private val SegmentGapHeight = 2.dp

/**
- Esquinas que se animan con un resorte "rápido espacial" al presionar,
- imitando el morph de forma de los botones expresivos.
 */
@Composable
fun rememberMorphingShape(
    interactionSource: MutableInteractionSource,
    restCorner: Dp,
    pressedCorner: Dp
): Shape {
    val pressed by interactionSource.collectIsPressedAsState()
    val corner by animateDpAsState(
        targetValue = if (pressed) pressedCorner else restCorner,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 800f),
        label = "shapeMorph"
    )
    return RoundedCornerShape(corner.value.coerceAtLeast(0f).dp)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val dataStore = ThemeUtils.getDataStore(context)

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    val backInteraction = remember { MutableInteractionSource() }
    val backShape = rememberMorphingShape(
        interactionSource = backInteraction,
        restCorner = 24.dp,
        pressedCorner = 14.dp
    )

    // Máscara de sombreado: el contenido pasa por debajo de la barra y de la barra de estado
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
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(brush = topBarMaskBrush)
            ) {
                LargeTopAppBar(
                    title = {
                        Text(
                            stringResource(R.string.settings_title),
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    navigationIcon = {
                        FilledTonalIconButton(
                            onClick = onBack,
                            shape = backShape,
                            interactionSource = backInteraction,
                            modifier = Modifier
                                .padding(start = 4.dp)
                                .size(48.dp)
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
                    ),
                    scrollBehavior = scrollBehavior
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
            item { CategoryHeader(stringResource(R.string.section_language_layout), Icons.Default.Language) }
            item {
                SettingsCardContainer {
                    val appLangValueFlow = remember { dataStore.data.map { it[ThemeUtils.APP_LANGUAGE] ?: "es" } }
                    val appLangValue by appLangValueFlow.collectAsState(initial = null)
                    var showAppLangDialog by remember { mutableStateOf(false) }

                    ListItem(
                        headlineContent = { Text(stringResource(R.string.setting_app_language), fontWeight = FontWeight.SemiBold) },
                        supportingContent = {
                            Text(when (appLangValue ?: "es") {
                                "es" -> stringResource(R.string.lang_spanish)
                                "en" -> stringResource(R.string.lang_english)
                                else -> appLangValue ?: "es"
                            })
                        },
                        leadingContent = { Icon(Icons.Default.Translate, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        modifier = Modifier.clickable { showAppLangDialog = true }
                    )

                    if (showAppLangDialog) {
                        RadioOptionDialog(
                            title = stringResource(R.string.setting_app_language_title),
                            options = listOf(
                                "es" to stringResource(R.string.lang_spanish),
                                "en" to stringResource(R.string.lang_english)
                            ),
                            selected = appLangValue ?: "es",
                            dismissLabel = stringResource(R.string.btn_cancel),
                            onSelect = { value ->
                                showAppLangDialog = false
                                scope.launch {
                                    dataStore.edit { it[ThemeUtils.APP_LANGUAGE] = value }
                                    AppCompatDelegate.setApplicationLocales(
                                        LocaleListCompat.forLanguageTags(value)
                                    )
                                    (context as? Activity)?.recreate()
                                }
                            },
                            onDismiss = { showAppLangDialog = false }
                        )
                    }

                    SettingsGap()

                    val langValueFlow = remember { dataStore.data.map { it[ThemeUtils.KEYBOARD_LANGUAGE] ?: "es" } }
                    val langValue by langValueFlow.collectAsState(initial = null)
                    var showLangDialog by remember { mutableStateOf(false) }

                    ListItem(
                        headlineContent = { Text(stringResource(R.string.setting_keyboard_language), fontWeight = FontWeight.SemiBold) },
                        supportingContent = {
                            Text(when (langValue ?: "es") {
                                "es" -> stringResource(R.string.lang_spanish)
                                "en" -> stringResource(R.string.lang_english)
                                else -> langValue ?: "es"
                            })
                        },
                        leadingContent = { Icon(Icons.Default.Language, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        modifier = Modifier.clickable { showLangDialog = true }
                    )

                    if (showLangDialog) {
                        RadioOptionDialog(
                            title = stringResource(R.string.setting_keyboard_language_title),
                            options = listOf(
                                "es" to stringResource(R.string.lang_spanish),
                                "en" to stringResource(R.string.lang_english)
                            ),
                            selected = langValue ?: "es",
                            dismissLabel = stringResource(R.string.btn_cancel),
                            onSelect = { value ->
                                showLangDialog = false
                                scope.launch {
                                    dataStore.edit { it[ThemeUtils.KEYBOARD_LANGUAGE] = value }
                                }
                            },
                            onDismiss = { showLangDialog = false }
                        )
                    }

                    SettingsGap()

                    val secLangValueFlow = remember { dataStore.data.map { it[ThemeUtils.SECONDARY_LANGUAGE] ?: "" } }
                    val secLangValue by secLangValueFlow.collectAsState(initial = null)
                    var showSecLangDialog by remember { mutableStateOf(false) }

                    ListItem(
                        headlineContent = { Text(stringResource(R.string.setting_secondary_language), fontWeight = FontWeight.SemiBold) },
                        supportingContent = {
                            Text(when (secLangValue ?: "") {
                                "es" -> stringResource(R.string.lang_spanish)
                                "en" -> stringResource(R.string.lang_english)
                                else -> stringResource(R.string.lang_disabled)
                            })
                        },
                        leadingContent = { Icon(Icons.Default.Language, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        modifier = Modifier.clickable { showSecLangDialog = true }
                    )

                    if (showSecLangDialog) {
                        RadioOptionDialog(
                            title = stringResource(R.string.setting_secondary_language_title),
                            options = listOf(
                                "" to stringResource(R.string.lang_disabled),
                                "es" to stringResource(R.string.lang_spanish),
                                "en" to stringResource(R.string.lang_english)
                            ),
                            selected = secLangValue ?: "",
                            dismissLabel = stringResource(R.string.btn_cancel),
                            onSelect = { value ->
                                showSecLangDialog = false
                                scope.launch {
                                    dataStore.edit {
                                        it[ThemeUtils.SECONDARY_LANGUAGE] = value
                                        it[ThemeUtils.MULTILINGUAL_ENABLED] = value.isNotEmpty()
                                    }
                                }
                            },
                            onDismiss = { showSecLangDialog = false }
                        )
                    }

                    SettingsGap()

                    val layoutValueFlow = remember { dataStore.data.map { it[ThemeUtils.KEYBOARD_LAYOUT_TYPE] ?: "default" } }
                    val layoutValue by layoutValueFlow.collectAsState(initial = null)
                    var showLayoutDialog by remember { mutableStateOf(false) }

                    ListItem(
                        headlineContent = { Text(stringResource(R.string.setting_layout_type), fontWeight = FontWeight.SemiBold) },
                        supportingContent = {
                            Text(when (layoutValue ?: "default") {
                                "default" -> stringResource(R.string.setting_layout_default)
                                "qwerty" -> stringResource(R.string.setting_layout_qwerty)
                                else -> layoutValue ?: "default"
                            })
                        },
                        leadingContent = { Icon(Icons.Default.Keyboard, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        modifier = Modifier.clickable { showLayoutDialog = true }
                    )

                    if (showLayoutDialog) {
                        RadioOptionDialog(
                            title = stringResource(R.string.setting_layout_type),
                            options = listOf(
                                "default" to stringResource(R.string.setting_layout_default),
                                "qwerty" to stringResource(R.string.setting_layout_qwerty)
                            ),
                            selected = layoutValue ?: "default",
                            dismissLabel = stringResource(R.string.btn_cancel),
                            onSelect = { value ->
                                showLayoutDialog = false
                                scope.launch {
                                    dataStore.edit { it[ThemeUtils.KEYBOARD_LAYOUT_TYPE] = value }
                                }
                            },
                            onDismiss = { showLayoutDialog = false }
                        )
                    }

                    SettingsGap()

                    ListItem(
                        headlineContent = { Text("Diccionario Personal", fontWeight = FontWeight.SemiBold) },
                        supportingContent = { Text("Ver y administrar palabras aprendidas") },
                        leadingContent = { Icon(Icons.Default.Book, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        modifier = Modifier.clickable {
                            context.startActivity(Intent(context, UserDictionaryActivity::class.java))
                        }
                    )

                    SettingsGap()

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
            item { CategoryHeader("Tema y Apariencia", Icons.Outlined.Palette) }
            item {
                SettingsCardContainer {
                    val themeValueFlow = remember { dataStore.data.map { it[ThemeUtils.APP_THEME] ?: "system" } }
                    val themeValue by themeValueFlow.collectAsState(initial = null)
                    var showThemeDialog by remember { mutableStateOf(false) }

                    ListItem(
                        headlineContent = { Text("Tema de la aplicación", fontWeight = FontWeight.SemiBold) },
                        supportingContent = {
                            Text(when (themeValue ?: "system") {
                                "light" -> "Claro"
                                "dark" -> "Oscuro"
                                "m3_dynamic", "dynamic" -> "Material 3 Dinámico"
                                else -> "Seguir sistema"
                            })
                        },
                        leadingContent = { Icon(Icons.Outlined.Palette, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        modifier = Modifier.clickable { showThemeDialog = true }
                    )

                    if (showThemeDialog) {
                        RadioOptionDialog(
                            title = "Seleccionar tema",
                            options = listOf(
                                "system" to "Seguir sistema",
                                "light" to "Claro",
                                "dark" to "Oscuro",
                                "m3_dynamic" to "Material 3 Dinámico"
                            ),
                            selected = if ((themeValue ?: "system") == "dynamic") "m3_dynamic" else (themeValue ?: "system"),
                            dismissLabel = "Cancelar",
                            onSelect = { value ->
                                showThemeDialog = false
                                scope.launch {
                                    ThemeUtils.saveAppTheme(context, value)
                                }
                            },
                            onDismiss = { showThemeDialog = false }
                        )
                    }

                    SettingsGap()

                    ListItem(
                        headlineContent = { Text("Personalización del Teclado", fontWeight = FontWeight.SemiBold) },
                        supportingContent = { Text("Temas, colores y previsualización") },
                        leadingContent = { Icon(painter = painterResource(R.drawable.ic_hexagon), contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        modifier = Modifier.clickable {
                            context.startActivity(Intent(context, ThemeSettingsActivity::class.java))
                        }
                    )

                    SettingsGap()

                    val fontPathFlow = remember { dataStore.data.map { it[ThemeUtils.CUSTOM_FONT_PATH] ?: "system" } }
                    val fontPath by fontPathFlow.collectAsState(initial = null)
                    ListItem(
                        headlineContent = { Text("Fuente del Teclado", fontWeight = FontWeight.SemiBold) },
                        supportingContent = {
                            Text(when (fontPath ?: "system") {
                                "system" -> "Sistema"
                                "nunito" -> "Nunito"
                                else -> (fontPath ?: "system").substringAfterLast("/")
                            })
                        },
                        leadingContent = { Icon(Icons.Default.FontDownload, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        modifier = Modifier.clickable {
                            context.startActivity(Intent(context, FontSelectorActivity::class.java))
                        }
                    )

                    SettingsGap()

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
                    SettingsGap()
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
                    SettingsGap()
                    SliderPreference(
                        title = "Escala teclado numérico",
                        subtitle = "Ajusta el tamaño de la cuadrícula de números",
                        key = ThemeUtils.NUMERIC_KEY_SIZE_SCALE,
                        defaultValue = 85,
                        min = 70,
                        max = 120,
                        context = context
                    )
                    SettingsGap()
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
            item { CategoryHeader("Sonido y Vibración", Icons.AutoMirrored.Filled.VolumeUp) }
            item {
                SettingsCardContainer {
                    SwitchPreference(
                        title = "Sonido al presionar",
                        subtitle = "Emitir un tono táctil al presionar cada tecla",
                        key = ThemeUtils.KEYBOARD_SOUND,
                        defaultValue = true,
                        icon = Icons.AutoMirrored.Filled.VolumeUp,
                        context = context
                    )
                    SettingsGap()
                    SliderPreference(
                        title = "Volumen del sonido",
                        key = ThemeUtils.KEYBOARD_SOUND_VOLUME,
                        defaultValue = 50,
                        min = 0,
                        max = 100,
                        context = context
                    )
                    SettingsGap()
                    SwitchPreference(
                        title = "Vibración al presionar",
                        subtitle = "Respuesta háptica al escribir",
                        key = ThemeUtils.KEYBOARD_VIBRATION,
                        defaultValue = true,
                        icon = Icons.Default.Vibration,
                        context = context
                    )
                    SettingsGap()
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
                        title = "Escritura por deslizamiento",
                        subtitle = "Escribir palabras deslizando el dedo por las teclas",
                        key = ThemeUtils.GESTURE_TYPING_ENABLED,
                        defaultValue = true,
                        icon = Icons.Default.Gesture,
                        context = context
                    )
                    SettingsGap()
                    SwitchPreference(
                        title = "Auto-corrección",
                        subtitle = "Corregir automáticamente palabras al presionar espacio",
                        key = ThemeUtils.AUTO_CORRECT,
                        defaultValue = true,
                        icon = Icons.Default.Spellcheck,
                        context = context
                    )
                    SettingsGap()
                    SwitchPreference(
                        title = "Deshacer corrección al borrar",
                        subtitle = "Restaurar la palabra original al pulsar retroceso",
                        key = ThemeUtils.UNDO_CORRECTION_ON_BACKSPACE,
                        defaultValue = true,
                        context = context
                    )
                    SettingsGap()
                    SwitchPreference(
                        title = "Auto-mayúsculas",
                        subtitle = "Iniciar cada oración con mayúscula",
                        key = ThemeUtils.AUTO_CAPITALIZE,
                        defaultValue = true,
                        context = context
                    )
                    SettingsGap()
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
                    SettingsGap()
                    SwitchPreference(
                        title = "Mostrar Pop-Up de tecla",
                        subtitle = "Vista previa ampliada de la tecla presionada",
                        key = ThemeUtils.SHOW_KEY_POPUP,
                        defaultValue = true,
                        icon = Icons.Default.SmartButton,
                        context = context
                    )
                    SettingsGap()
                    SliderPreference(
                        title = "Tamaño del Pop-Up",
                        key = ThemeUtils.POPUP_SCALE,
                        defaultValue = 80,
                        min = 40,
                        max = 150,
                        context = context
                    )
                    SettingsGap()
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
                    SettingsGap()
                    SwitchPreference(
                        title = "Indicadores de acentos",
                        subtitle = "Mostrar identificadores en teclas con opciones adicionales",
                        key = ThemeUtils.SHOW_LONG_PRESS_INDICATORS,
                        defaultValue = true,
                        context = context
                    )
                    SettingsGap()
                    SwitchPreference(
                        title = "Reiniciar al cerrar",
                        subtitle = "Volver a la vista principal al ocultar el teclado",
                        key = ThemeUtils.RESET_ON_CLOSE,
                        defaultValue = true,
                        icon = Icons.Default.RestartAlt,
                        context = context
                    )
                    SettingsGap()
                    SwitchPreference(
                        title = stringResource(R.string.setting_auto_floating_landscape),
                        subtitle = stringResource(R.string.setting_auto_floating_landscape_summary),
                        key = ThemeUtils.AUTO_FLOATING_IN_LANDSCAPE,
                        defaultValue = true,
                        icon = Icons.Default.ScreenRotation,
                        context = context
                    )
                }
            }

            // --- SECCIÓN 7: Portapapeles ---
            item { CategoryHeader("Portapapeles", Icons.AutoMirrored.Outlined.Assignment) }
            item {
                SettingsCardContainer {
                    SwitchPreference(
                        title = "Guardar capturas de pantalla",
                        subtitle = "Detecta capturas de pantalla recientes y las guarda en el historial",
                        key = ThemeUtils.AUTO_COPY_SCREENSHOTS,
                        defaultValue = false,
                        icon = Icons.Default.CameraAlt,
                        context = context,
                        onCheckedChange = { enabled ->
                            if (enabled && !KeyboardService.hasMediaPermission(context)) {
                                KeyboardService.requestMediaPermission(context)
                            }
                        }
                    )
                    SettingsGap()

                    SwitchPreference(
                        title = "Eliminación automática",
                        subtitle = "Borrar clips no fijados automáticamente tras el tiempo límite",
                        key = ThemeUtils.CLIPBOARD_AUTO_DELETE,
                        defaultValue = true,
                        icon = Icons.AutoMirrored.Outlined.Assignment,
                        context = context
                    )
                    SettingsGap()

                    val expiryValueFlow = remember { dataStore.data.map { it[ThemeUtils.CLIPBOARD_EXPIRY_HOURS] ?: "1" } }
                    val expiryValue by expiryValueFlow.collectAsState(initial = null)
                    var showExpiryDialog by remember { mutableStateOf(false) }

                    val expiryEntries = listOf(
                        "0.25" to "15 minutos",
                        "1" to "1 hora",
                        "3" to "3 horas",
                        "6" to "6 horas",
                        "12" to "12 horas",
                        "24" to "24 horas"
                    )

                    ListItem(
                        headlineContent = { Text("Tiempo de expiración", fontWeight = FontWeight.SemiBold) },
                        supportingContent = {
                            val index = expiryEntries.indexOfFirst { it.first == (expiryValue ?: "1") }.coerceAtLeast(0)
                            Text(expiryEntries[index].second)
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        modifier = Modifier.clickable { showExpiryDialog = true }
                    )

                    if (showExpiryDialog) {
                        RadioOptionDialog(
                            title = "Tiempo de expiración",
                            options = expiryEntries,
                            selected = expiryValue ?: "1",
                            dismissLabel = "Cancelar",
                            onSelect = { value ->
                                showExpiryDialog = false
                                scope.launch {
                                    dataStore.edit { it[ThemeUtils.CLIPBOARD_EXPIRY_HOURS] = value }
                                }
                            },
                            onDismiss = { showExpiryDialog = false }
                        )
                    }
                }
            }

            // --- RESTABLECER AJUSTES ---
            item {
                Spacer(modifier = Modifier.height(24.dp))
                var showResetDialog by remember { mutableStateOf(false) }

                val resetInteraction = remember { MutableInteractionSource() }
                val resetShape = rememberMorphingShape(
                    interactionSource = resetInteraction,
                    restCorner = 28.dp,
                    pressedCorner = 14.dp
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    FilledTonalButton(
                        onClick = { showResetDialog = true },
                        shape = resetShape,
                        interactionSource = resetInteraction,
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Restablecer todos los ajustes", fontWeight = FontWeight.Bold)
                    }
                }

                if (showResetDialog) {
                    AlertDialog(
                        onDismissRequest = { showResetDialog = false },
                        icon = { Icon(Icons.Default.Refresh, contentDescription = null) },
                        title = { Text("Restablecer ajustes", fontWeight = FontWeight.Bold) },
                        text = {
                            Text("¿Estás seguro de que deseas restablecer la configuración del teclado a sus valores predeterminados? Tus temas personalizados, palabras aprendidas e historial del portapapeles no se borrarán.")
                        },
                        confirmButton = {
                            TextButton(
                                onClick = {
                                    showResetDialog = false
                                    scope.launch {
                                        ThemeUtils.resetAllSettings(context)
                                        Toast.makeText(context, "Ajustes restablecidos a valores predeterminados", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                            ) {
                                Text("Restablecer", fontWeight = FontWeight.Bold)
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { showResetDialog = false }) {
                                Text("Cancelar")
                            }
                        }
                    )
                }
            }
        }
    }
}

/**
- Diálogo de selección única reutilizable. La opción activa se resalta con un
- contenedor tonal (secondaryContainer) con esquinas redondeadas.
 */
@Composable
private fun RadioOptionDialog(
    title: String,
    options: List<Pair<String, String>>,
    selected: String,
    dismissLabel: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(
                modifier = Modifier.selectableGroup(),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                options.forEach { (value, label) ->
                    val isSelected = value == selected
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(
                                if (isSelected) MaterialTheme.colorScheme.secondaryContainer
                                else Color.Transparent
                            )
                            .selectable(
                                selected = isSelected,
                                role = Role.RadioButton,
                                onClick = { onSelect(value) }
                            )
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = isSelected, onClick = null)
                        Spacer(Modifier.width(16.dp))
                        Text(
                            text = label,
                            color = if (isSelected) MaterialTheme.colorScheme.onSecondaryContainer
                            else MaterialTheme.colorScheme.onSurface,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(dismissLabel) }
        }
    )
}

@Composable
fun CategoryHeader(title: String, icon: ImageVector? = null) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 10.dp)
            .semantics { heading() }
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

/**
- Grupo de ajustes expresivo: contenedor de esquinas grandes (28dp) con tono
- surfaceContainer. Usa [SettingsGap] entre elementos para lograr el aspecto
- de lista segmentada.
 */
@Composable
fun SettingsCardContainer(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(GroupCornerRadius),
        color = MaterialTheme.colorScheme.surfaceContainer
    ) {
        Column(content = content)
    }
}

/**
- Separador de lista segmentada: un hueco fino que deja ver el fondo de la
- pantalla, en lugar de una línea divisoria.
 */
@Composable
fun SettingsGap() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(SegmentGapHeight)
            .background(MaterialTheme.colorScheme.background)
    )
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
    val prefs by dataStore.data.collectAsState(initial = null)

    if (prefs == null) {
        // Reservar espacio visual mientras carga para evitar redimensiones bruscas
        Box(modifier = Modifier.fillMaxWidth().height(104.dp))
        return
    }

    val value = prefs!![key] ?: defaultValue

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
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
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
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
    val isPreview = LocalInspectionMode.current
    val dataStore = ThemeUtils.getDataStore(context)
    val scope = rememberCoroutineScope()
    val prefs by dataStore.data.collectAsState(initial = null)

    if (!isPreview && prefs == null) {
        // Reservar espacio visual mientras carga
        Box(modifier = Modifier.fillMaxWidth().height(72.dp))
        return
    }

    val checked = if (isPreview) defaultValue else (prefs?.get(key) ?: defaultValue)

    val onToggle: (Boolean) -> Unit = { newValue ->
        scope.launch {
            dataStore.edit { it[key] = newValue }
            onCheckedChange?.invoke(newValue)
        }
    }

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
                onCheckedChange = null,
                thumbContent = if (checked) {
                    {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            modifier = Modifier.size(SwitchDefaults.IconSize)
                        )
                    }
                } else null
            )
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.toggleable(
            value = checked,
            role = Role.Switch,
            onValueChange = onToggle
        )
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Preview(name = "Light Mode", showBackground = true, showSystemUi = true)
@Preview(
    name = "Dark Mode",
    uiMode = Configuration.UI_MODE_NIGHT_YES,
    showBackground = true,
    showSystemUi = true
)
@Composable
fun PreviewSettingsScreen() {
    MantisKeyboardTheme {
        MaterialExpressiveTheme(
            colorScheme = MaterialTheme.colorScheme
        ) {
            SettingsScreen(onBack = {})
        }
    }
}