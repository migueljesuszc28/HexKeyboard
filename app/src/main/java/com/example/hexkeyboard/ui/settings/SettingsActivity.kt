package com.example.hexkeyboard.ui.settings

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.*
import com.example.hexkeyboard.data.repository.ThemeUtils
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import com.example.hexkeyboard.ui.settings.fontselector.FontSelectorActivity
import com.example.hexkeyboard.R
import com.example.hexkeyboard.ui.settings.themes.ThemeSettingsActivity
import com.example.hexkeyboard.ui.theme.HexKeyboardTheme

class SettingsActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Ajustes") },
                navigationIcon = {
                    // Usamos FilledTonalIconButton para un fondo suave circular
                    FilledTonalIconButton(
                        onClick = onBack,
                        shape = CircleShape
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Atrás"
                        )
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            item { CategoryHeader("Idioma") }
            item {
                val langValueFlow = remember { dataStore.data.map { it[ThemeUtils.KEYBOARD_LANGUAGE] ?: "es" } }
                val langValue by langValueFlow.collectAsState("es")
                var showLangDialog by remember { mutableStateOf(false) }

                ListItem(
                    headlineContent = { Text("Idioma del Teclado") },
                    supportingContent = { 
                        Text(when(langValue) {
                            "es" -> "Español"
                            "en" -> "English"
                            else -> langValue
                        })
                    },
                    leadingContent = { Icon(Icons.Default.Language, contentDescription = null) },
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
            }

            item { CategoryHeader("Distribución") }
            item {
                val layoutValueFlow = remember { dataStore.data.map { it[ThemeUtils.KEYBOARD_LAYOUT_TYPE] ?: "default" } }
                val layoutValue by layoutValueFlow.collectAsState("default")
                var showLayoutDialog by remember { mutableStateOf(false) }

                ListItem(
                    headlineContent = { Text("Distribución de Teclas") },
                    supportingContent = { 
                        Text(when(layoutValue) {
                            "default" -> "Predeterminada (Optimización Hexagonal)"
                            "qwerty" -> "QWERTY (Clásica)"
                            else -> layoutValue
                        })
                    },
                    leadingContent = { Icon(Icons.Default.Keyboard, contentDescription = null) },
                    modifier = Modifier.clickable { showLayoutDialog = true }
                )

                if (showLayoutDialog) {
                    AlertDialog(
                        onDismissRequest = { showLayoutDialog = false },
                        title = { Text("Seleccionar distribución") },
                        text = {
                            Column {
                                listOf("default" to "Predeterminada", "qwerty" to "QWERTY").forEach { (value, label) ->
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
            }
            item {
                ListItem(
                    headlineContent = { Text("Diccionario Personal") },
                    supportingContent = { Text("Ver y administrar palabras aprendidas") },
                    leadingContent = { Icon(Icons.Default.Book, contentDescription = null) },
                    modifier = Modifier.clickable {
                        context.startActivity(Intent(context, UserDictionaryActivity::class.java))
                    }
                )
            }

            item { CategoryHeader("Tema") }
            item {
                val themeValueFlow = remember { dataStore.data.map { it[ThemeUtils.APP_THEME] ?: "system" } }
                val themeValue by themeValueFlow.collectAsState("system")
                var showThemeDialog by remember { mutableStateOf(false) }

                ListItem(
                    headlineContent = { Text("Tema de la aplicación") },
                    supportingContent = { 
                        Text(when(themeValue) {
                            "light" -> "Claro"
                            "dark" -> "Oscuro"
                            else -> "Seguir sistema"
                        })
                    },
                    leadingContent = { Icon(Icons.Default.Palette, contentDescription = null) },
                    modifier = Modifier.clickable { showThemeDialog = true }
                )

                if (showThemeDialog) {
                    AlertDialog(
                        onDismissRequest = { showThemeDialog = false },
                        title = { Text("Seleccionar tema") },
                        text = {
                            Column {
                                listOf("system" to "Seguir sistema", "light" to "Claro", "dark" to "Oscuro").forEach { (value, label) ->
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
                                        RadioButton(selected = themeValue == value, onClick = null)
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
            }

            item {
                ListItem(
                    headlineContent = { Text("Personalización del Teclado") },
                    supportingContent = { Text("Temas, colores y previsualización") },
                    leadingContent = { Icon(painter = painterResource(R.drawable.ic_hexagon), contentDescription = null) },
                    modifier = Modifier.clickable {
                        context.startActivity(Intent(context, ThemeSettingsActivity::class.java))
                    }
                )
            }

            item { CategoryHeader("Apariencia") }
            item {
                val fontPathFlow = remember { dataStore.data.map { it[ThemeUtils.CUSTOM_FONT_PATH] ?: "system" } }
                val fontPath by fontPathFlow.collectAsState("system")
                ListItem(
                    headlineContent = { Text("Fuente") },
                    supportingContent = { 
                        Text(when(fontPath) {
                            "system" -> "Sistema"
                            "nunito" -> "Nunito"
                            else -> fontPath.substringAfterLast("/")
                        })
                    },
                    leadingContent = { Icon(Icons.Default.FontDownload, contentDescription = null) },
                    modifier = Modifier.clickable {
                        context.startActivity(Intent(context, FontSelectorActivity::class.java))
                    }
                )
            }
            item {
                SliderPreference(
                    title = "Altura del teclado",
                    key = ThemeUtils.KEYBOARD_HEIGHT,
                    defaultValue = 50,
                    min = 0,
                    max = 100,
                    icon = Icons.Default.Height,
                    context = context
                )
            }
            item {
                SliderPreference(
                    title = "Tamaño de las teclas",
                    key = ThemeUtils.KEYBOARD_KEY_SIZE,
                    defaultValue = 90,
                    min = 50,
                    max = 100,
                    context = context
                )
            }
            item {
                SliderPreference(
                    title = "Escala teclado numérico",
                    key = ThemeUtils.NUMERIC_KEY_SIZE_SCALE,
                    defaultValue = 85,
                    min = 70,
                    max = 120,
                    context = context
                )
            }
            item {
                SliderPreference(
                    title = "Margen inferior",
                    key = ThemeUtils.KEYBOARD_BOTTOM_OFFSET,
                    defaultValue = 30,
                    min = 0,
                    max = 100,
                    context = context
                )
            }

            item { CategoryHeader("Comportamiento") }
            item {
                SwitchPreference(
                    title = "Sonido al presionar",
                    key = ThemeUtils.KEYBOARD_SOUND,
                    defaultValue = true,
                    icon = Icons.Default.VolumeUp,
                    context = context
                )
            }
            item {
                SliderPreference(
                    title = "Volumen del sonido",
                    key = ThemeUtils.KEYBOARD_SOUND_VOLUME,
                    defaultValue = 50,
                    min = 0,
                    max = 100,
                    context = context
                )
            }
            item {
                SwitchPreference(
                    title = "Vibración al presionar",
                    key = ThemeUtils.KEYBOARD_VIBRATION,
                    defaultValue = true,
                    icon = Icons.Default.Vibration,
                    context = context
                )
            }
            item {
                SliderPreference(
                    title = "Intensidad de vibración",
                    key = ThemeUtils.KEYBOARD_VIBRATION_INTENSITY,
                    defaultValue = 30,
                    min = 0,
                    max = 100,
                    context = context
                )
            }
            item {
                SwitchPreference(
                    title = "Auto-corrección",
                    key = ThemeUtils.AUTO_CORRECT,
                    defaultValue = false,
                    context = context
                )
            }
            item {
                SwitchPreference(
                    title = "Deshacer corrección al borrar",
                    key = ThemeUtils.UNDO_CORRECTION_ON_BACKSPACE,
                    defaultValue = true,
                    context = context
                )
            }
            item {
                SwitchPreference(
                    title = "Auto-mayúsculas",
                    key = ThemeUtils.AUTO_CAPITALIZE,
                    defaultValue = true,
                    context = context
                )
            }
            item {
                SwitchPreference(
                    title = "Punto automático",
                    key = ThemeUtils.DOUBLE_SPACE_PERIOD,
                    defaultValue = true,
                    context = context
                )
            }
            item {
                SwitchPreference(
                    title = "Mostrar Pop-Up de tecla",
                    key = ThemeUtils.SHOW_KEY_POPUP,
                    defaultValue = true,
                    context = context
                )
            }
            item {
                SliderPreference(
                    title = "Tamaño del Pop-Up",
                    key = ThemeUtils.POPUP_SCALE,
                    defaultValue = 95,
                    min = 40,
                    max = 150,
                    context = context
                )
            }
            item {
                SliderPreference(
                    title = "Duración toque prolongado",
                    key = ThemeUtils.LONG_PRESS_DURATION,
                    defaultValue = 259,
                    min = 100,
                    max = 1000,
                    context = context
                )
            }
            item {
                SwitchPreference(
                    title = "Reiniciar al cerrar",
                    key = ThemeUtils.RESET_ON_CLOSE,
                    defaultValue = true,
                    context = context
                )
            }
            item {
                SwitchPreference(
                    title = "Indicadores de acentos",
                    key = ThemeUtils.SHOW_LONG_PRESS_INDICATORS,
                    defaultValue = true,
                    context = context
                )
            }

            item { CategoryHeader("Portapapeles") }
            item {
                SwitchPreference(
                    title = "Eliminación automática",
                    key = ThemeUtils.CLIPBOARD_AUTO_DELETE,
                    defaultValue = true,
                    icon = Icons.Default.ContentPaste,
                    context = context
                )
            }
            item {
                val expiryValueFlow = remember { dataStore.data.map { it[ThemeUtils.CLIPBOARD_EXPIRY_HOURS] ?: "1" } }
                val expiryValue by expiryValueFlow.collectAsState("1")
                var showExpiryDialog by remember { mutableStateOf(false) }

                ListItem(
                    headlineContent = { Text("Tiempo de expiración") },
                    supportingContent = { 
                        val labels = listOf("15 minutos", "1 hora", "3 horas", "6 horas", "12 horas", "24 horas")
                        val values = listOf("0.25", "1", "3", "6", "12", "24")
                        val index = values.indexOf(expiryValue).coerceAtLeast(0)
                        Text(labels[index])
                    },
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

            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Button(
                        onClick = {
                            scope.launch {
                                dataStore.edit { it.clear() }
                                applyAppTheme("system")
                                // Reiniciar la actividad para aplicar cambios
                                val intent = Intent(context, SettingsActivity::class.java)
                                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                                context.startActivity(intent)
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Restablecer todos los ajustes")
                    }
                }
            }
        }
    }
}

@Composable
fun CategoryHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, top = 24.dp, bottom = 8.dp)
    )
}

@Composable
fun SliderPreference(
    title: String,
    key: Preferences.Key<Int>,
    defaultValue: Int,
    min: Int,
    max: Int,
    icon: ImageVector? = null,
    context: Context
) {
    val dataStore = ThemeUtils.getDataStore(context)
    val scope = rememberCoroutineScope()
    val valueFlow = remember { dataStore.data.map { it[key] ?: defaultValue } }
    val value by valueFlow.collectAsState(defaultValue)

    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                Icon(icon, contentDescription = null, modifier = Modifier.padding(end = 16.dp))
            } else {
                Spacer(Modifier.width(40.dp))
            }
            Text(title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Text(value.toString(), style = MaterialTheme.typography.bodyMedium)
        }
        Slider(
            value = value.toFloat(),
            onValueChange = { newValue ->
                scope.launch {
                    dataStore.edit { it[key] = newValue.toInt() }
                }
            },
            valueRange = min.toFloat()..max.toFloat(),
            modifier = Modifier.padding(start = 40.dp)
        )
    }
}

@Composable
fun SwitchPreference(
    title: String,
    key: Preferences.Key<Boolean>,
    defaultValue: Boolean,
    icon: ImageVector? = null,
    context: Context,
    onCheckedChange: ((Boolean) -> Unit)? = null
) {
    val dataStore = ThemeUtils.getDataStore(context)
    val scope = rememberCoroutineScope()
    val checkedFlow = remember { dataStore.data.map { it[key] ?: defaultValue } }
    val checked by checkedFlow.collectAsState(defaultValue)

    ListItem(
        headlineContent = { Text(title) },
        leadingContent = icon?.let { { Icon(it, contentDescription = null) } },
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
        "system" -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
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
