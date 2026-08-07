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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.preference.PreferenceManager
import kotlinx.coroutines.launch
import com.example.hexkeyboard.ui.settings.fontselector.FontSelectorActivity
import com.example.hexkeyboard.R
import com.example.hexkeyboard.data.repository.ThemeUtils
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
    val prefs = remember { PreferenceManager.getDefaultSharedPreferences(context) }

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
                var langValue by remember { mutableStateOf(prefs.getString("keyboard_language", "es") ?: "es") }
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
                                                langValue = value
                                                prefs.edit().putString("keyboard_language", value).apply()
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
                var layoutValue by remember { mutableStateOf(prefs.getString("keyboard_layout_type", "default") ?: "default") }
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
                                                layoutValue = value
                                                prefs.edit().putString("keyboard_layout_type", value).apply()
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
                var themeValue by remember { mutableStateOf(prefs.getString("app_theme", "system") ?: "system") }
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
                                                themeValue = value
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
                ListItem(
                    headlineContent = { Text("Fuente") },
                    supportingContent = { 
                        val fontPath = prefs.getString("custom_font_path", "system")
                        Text(when(fontPath) {
                            "system" -> "Sistema"
                            "nunito" -> "Nunito"
                            else -> fontPath?.substringAfterLast("/") ?: "Personalizada"
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
                    key = "keyboard_height",
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
                    key = "keyboard_key_size",
                    defaultValue = 90,
                    min = 50,
                    max = 100,
                    context = context
                )
            }
            item {
                SliderPreference(
                    title = "Escala teclado numérico",
                    key = "numeric_key_size_scale",
                    defaultValue = 85,
                    min = 70,
                    max = 120,
                    context = context
                )
            }
            item {
                SliderPreference(
                    title = "Margen inferior",
                    key = "keyboard_bottom_offset",
                    defaultValue = 35,
                    min = 0,
                    max = 100,
                    context = context
                )
            }

            item { CategoryHeader("Comportamiento") }
            item {
                SwitchPreference(
                    title = "Sonido al presionar",
                    key = "keyboard_sound",
                    defaultValue = true,
                    icon = Icons.Default.VolumeUp,
                    context = context
                )
            }
            item {
                SliderPreference(
                    title = "Volumen del sonido",
                    key = "keyboard_sound_volume",
                    defaultValue = 50,
                    min = 0,
                    max = 100,
                    context = context
                )
            }
            item {
                SwitchPreference(
                    title = "Vibración al presionar",
                    key = "keyboard_vibration",
                    defaultValue = true,
                    icon = Icons.Default.Vibration,
                    context = context
                )
            }
            item {
                SliderPreference(
                    title = "Intensidad de vibración",
                    key = "keyboard_vibration_intensity",
                    defaultValue = 30,
                    min = 0,
                    max = 100,
                    context = context
                )
            }
            item {
                SwitchPreference(
                    title = "Auto-corrección",
                    key = "auto_correct",
                    defaultValue = false,
                    context = context
                )
            }
            item {
                SwitchPreference(
                    title = "Deshacer corrección al borrar",
                    key = "undo_correction_on_backspace",
                    defaultValue = true,
                    context = context
                )
            }
            item {
                SwitchPreference(
                    title = "Auto-mayúsculas",
                    key = "auto_capitalize",
                    defaultValue = true,
                    context = context
                )
            }
            item {
                SwitchPreference(
                    title = "Punto automático",
                    key = "double_space_period",
                    defaultValue = true,
                    context = context
                )
            }
            item {
                SwitchPreference(
                    title = "Mostrar Pop-Up de tecla",
                    key = "show_key_popup",
                    defaultValue = true,
                    context = context
                )
            }
            item {
                SliderPreference(
                    title = "Tamaño del Pop-Up",
                    key = "popup_scale",
                    defaultValue = 95,
                    min = 40,
                    max = 150,
                    context = context
                )
            }
            item {
                SliderPreference(
                    title = "Duración toque prolongado",
                    key = "long_press_duration",
                    defaultValue = 259,
                    min = 100,
                    max = 1000,
                    context = context
                )
            }
            item {
                SwitchPreference(
                    title = "Reiniciar al cerrar",
                    key = "reset_on_close",
                    defaultValue = true,
                    context = context
                )
            }
            item {
                SwitchPreference(
                    title = "Indicadores de acentos",
                    key = "show_long_press_indicators",
                    defaultValue = true,
                    context = context
                )
            }

            item { CategoryHeader("Portapapeles") }
            item {
                SwitchPreference(
                    title = "Eliminación automática",
                    key = "clipboard_auto_delete",
                    defaultValue = true,
                    icon = Icons.Default.ContentPaste,
                    context = context
                )
            }
            item {
                var expiryValue by remember { mutableStateOf(prefs.getString("clipboard_expiry_hours", "1") ?: "1") }
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
                                                expiryValue = value
                                                prefs.edit().putString("clipboard_expiry_hours", value).apply()
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
                            prefs.edit().clear().apply()
                            applyAppTheme("system")
                            // Reiniciar la actividad para aplicar cambios
                            val intent = Intent(context, SettingsActivity::class.java)
                            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                            context.startActivity(intent)
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
    key: String,
    defaultValue: Int,
    min: Int,
    max: Int,
    icon: ImageVector? = null,
    context: Context
) {
    val prefs = remember { PreferenceManager.getDefaultSharedPreferences(context) }
    var value by remember { mutableIntStateOf(prefs.getInt(key, defaultValue)) }

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
            onValueChange = { value = it.toInt() },
            onValueChangeFinished = { prefs.edit().putInt(key, value).apply() },
            valueRange = min.toFloat()..max.toFloat(),
            modifier = Modifier.padding(start = if (icon != null) 40.dp else 40.dp)
        )
    }
}

@Composable
fun SwitchPreference(
    title: String,
    key: String,
    defaultValue: Boolean,
    icon: ImageVector? = null,
    context: Context,
    onCheckedChange: ((Boolean) -> Unit)? = null
) {
    val prefs = remember { PreferenceManager.getDefaultSharedPreferences(context) }
    var checked by remember { mutableStateOf(prefs.getBoolean(key, defaultValue)) }

    ListItem(
        headlineContent = { Text(title) },
        leadingContent = icon?.let { { Icon(it, contentDescription = null) } },
        trailingContent = {
            Switch(
                checked = checked,
                onCheckedChange = {
                    checked = it
                    prefs.edit().putBoolean(key, it).apply()
                    onCheckedChange?.invoke(it)
                }
            )
        },
        modifier = Modifier.clickable {
            checked = !checked
            prefs.edit().putBoolean(key, checked).apply()
            onCheckedChange?.invoke(checked)
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
