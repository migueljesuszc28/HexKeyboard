package com.example.hexkeyboard.ui.settings.themes

import android.app.Activity
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.hexkeyboard.data.repository.KeyboardTheme
import com.example.hexkeyboard.data.repository.ThemeUtils
import com.example.hexkeyboard.ui.theme.HexKeyboardTheme
import com.github.skydoves.colorpicker.compose.*
import kotlinx.coroutines.launch
import java.util.UUID

class ThemeEditorActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val themeId = intent.getStringExtra("theme_id")

        setContent {
            HexKeyboardTheme {
                ThemeEditorScreen(
                    initialThemeId = themeId,
                    onBack = { finish() },
                    onSave = { finish() }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ThemeEditorScreen(
    initialThemeId: String?,
    onBack: () -> Unit,
    onSave: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    var currentTheme by remember { 
        mutableStateOf(KeyboardTheme().copy(
            id = "custom_" + UUID.randomUUID().toString(),
            name = ""
        )) 
    }

    LaunchedEffect(initialThemeId) {
        if (initialThemeId != null) {
            val theme = ThemeUtils.getAllThemes(context).find { it.id == initialThemeId }
            if (theme != null) {
                currentTheme = theme
            }
        }
    }

    var editIndividualKeys by remember { mutableStateOf(false) }
    var showColorPickerFor by remember { mutableStateOf<((Int) -> Unit)?>(null) }
    var initialColorForPicker by remember { mutableIntStateOf(Color.WHITE) }

    val cropLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val uriString = result.data?.getStringExtra("cropped_uri")
            if (uriString != null) {
                scope.launch {
                    val bitmap = ThemeUtils.loadBitmapFromUri(context, uriString)
                    if (bitmap != null) {
                        currentTheme = ThemeUtils.extractDynamicTheme(bitmap, currentTheme).copy(
                            backgroundImageUri = uriString,
                            backgroundColor = Color.TRANSPARENT
                        )
                    } else {
                        currentTheme = currentTheme.copy(
                            backgroundImageUri = uriString,
                            backgroundColor = Color.TRANSPARENT
                        )
                    }
                }
            }
        }
    }

    val pickImageLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            val intent = Intent(context, CropImageActivity::class.java).apply {
                putExtra("image_uri", it.toString())
                putExtra("current_theme", ThemeUtils.json.encodeToString(currentTheme))
            }
            cropLauncher.launch(intent)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (initialThemeId == null) "Crear Tema" else "Editar Tema") },
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
                },
                actions = {
                    // El botón de guardar ahora es un FAB para un estilo más Material 3 Expressive
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = {
                    if (currentTheme.name.isBlank()) {
                        Toast.makeText(context, "Por favor introduce un nombre", Toast.LENGTH_SHORT).show()
                    } else {
                        scope.launch {
                            ThemeUtils.saveCustomTheme(context, currentTheme)
                            onSave()
                        }
                    }
                },
                icon = { Icon(Icons.Default.Save, null) },
                text = { Text("Guardar Tema") },
                containerColor = androidx.compose.ui.graphics.Color(0xFF2196F3),
                contentColor = androidx.compose.ui.graphics.Color.White,
                shape = MaterialTheme.shapes.extraLarge
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            // Preview (Fijo arriba)
            Box(
                modifier = Modifier
                    .padding(16.dp)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                KeyboardPreview(
                    theme = currentTheme,
                    modifier = Modifier
                        .fillMaxWidth()
                        .wrapContentHeight(),
                    onKeyClick = { key ->
                        if (editIndividualKeys) {
                            initialColorForPicker = currentTheme.individualKeyColors[key.value] ?: currentTheme.keyBackgroundColor
                            showColorPickerFor = { color ->
                                val newMap = currentTheme.individualKeyColors.toMutableMap()
                                newMap[key.value] = color
                                currentTheme = currentTheme.copy(individualKeyColors = newMap)
                            }
                        }
                    }
                )
            }

            // Controles (Scrollable)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp)
            ) {
                OutlinedTextField(
                    value = currentTheme.name,
                    onValueChange = { currentTheme = currentTheme.copy(name = it) },
                    label = { Text("Nombre del tema") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp)
                )

                Spacer(Modifier.height(24.dp))

                Text("Colores principales", style = MaterialTheme.typography.titleMedium)
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                ) {
                    Column(
                        modifier = Modifier.padding(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(modifier = Modifier.weight(1f)) {
                                ColorButton("Fondo", currentTheme.backgroundColor) { 
                                    initialColorForPicker = currentTheme.backgroundColor
                                    showColorPickerFor = { currentTheme = currentTheme.copy(backgroundColor = it) } 
                                }
                            }
                            Box(modifier = Modifier.weight(1f)) {
                                ColorButton("Teclas", currentTheme.keyBackgroundColor) { 
                                    initialColorForPicker = currentTheme.keyBackgroundColor
                                    showColorPickerFor = { currentTheme = currentTheme.copy(keyBackgroundColor = it) } 
                                }
                            }
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(modifier = Modifier.weight(1f)) {
                                ColorButton("Texto", currentTheme.keyTextColor) { 
                                    initialColorForPicker = currentTheme.keyTextColor
                                    showColorPickerFor = { currentTheme = currentTheme.copy(keyTextColor = it, isKeyTextColorCustom = true) } 
                                }
                            }
                            Box(modifier = Modifier.weight(1f)) {
                                ColorButton("Bordes", currentTheme.keyStrokeColor) { 
                                    initialColorForPicker = currentTheme.keyStrokeColor
                                    showColorPickerFor = { currentTheme = currentTheme.copy(keyStrokeColor = it) } 
                                }
                            }
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(modifier = Modifier.weight(1f)) {
                                ColorButton("Presión", currentTheme.keyBackgroundPressedColor) { 
                                    initialColorForPicker = currentTheme.keyBackgroundPressedColor
                                    showColorPickerFor = { currentTheme = currentTheme.copy(keyBackgroundPressedColor = it) } 
                                }
                            }
                            Box(modifier = Modifier.weight(1f)) {
                                ColorButton("Iconos", currentTheme.keyboardIconTint) { 
                                    initialColorForPicker = currentTheme.keyboardIconTint
                                    showColorPickerFor = { currentTheme = currentTheme.copy(keyboardIconTint = it) } 
                                }
                            }
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(modifier = Modifier.weight(1f)) {
                                ColorButton("Popup", currentTheme.popupBackgroundColor) { 
                                    initialColorForPicker = currentTheme.popupBackgroundColor
                                    showColorPickerFor = { currentTheme = currentTheme.copy(popupBackgroundColor = it) } 
                                }
                            }
                            Box(modifier = Modifier.weight(1f)) {
                                ColorButton("Popup Texto", currentTheme.popupTextColor) { 
                                    initialColorForPicker = currentTheme.popupTextColor
                                    showColorPickerFor = { currentTheme = currentTheme.copy(popupTextColor = it) }
                                }
                            }
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(modifier = Modifier.weight(1f)) {
                                ColorButton("Shift Activo", currentTheme.keyShiftActiveColor ?: currentTheme.keyTextColor) {
                                    initialColorForPicker = currentTheme.keyShiftActiveColor ?: currentTheme.keyTextColor
                                    showColorPickerFor = { currentTheme = currentTheme.copy(keyShiftActiveColor = it) }
                                }
                            }
                            Box(modifier = Modifier.weight(1f)) {
                                ColorButton("Shift Inactivo", currentTheme.keyShiftInactiveColor ?: currentTheme.keyBackgroundSpecialColor) {
                                    initialColorForPicker = currentTheme.keyShiftInactiveColor ?: currentTheme.keyBackgroundSpecialColor
                                    showColorPickerFor = { currentTheme = currentTheme.copy(keyShiftInactiveColor = it) }
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))
                
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = if (editIndividualKeys) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    )
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Colores individuales", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                            Switch(checked = editIndividualKeys, onCheckedChange = { editIndividualKeys = it })
                        }
                        if (editIndividualKeys) {
                            Text(
                                "Toca una tecla en la previsualización superior para cambiar su color.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                            if (currentTheme.individualKeyColors.isNotEmpty()) {
                                TextButton(
                                    onClick = { currentTheme = currentTheme.copy(individualKeyColors = emptyMap()) },
                                    modifier = Modifier.align(Alignment.End)
                                ) {
                                    Text("Restablecer todas")
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(24.dp))
                Text("Personalización visual", style = MaterialTheme.typography.titleMedium)
                
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("Imagen de fondo", style = MaterialTheme.typography.titleSmall)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(vertical = 8.dp)
                        ) {
                            Button(onClick = { pickImageLauncher.launch("image/*") }) {
                                Icon(Icons.Default.Image, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text(if (currentTheme.backgroundImageUri == null) "Seleccionar imagen" else "Cambiar imagen")
                            }
                            if (currentTheme.backgroundImageUri != null) {
                                IconButton(onClick = { currentTheme = currentTheme.copy(backgroundImageUri = null) }) {
                                    Icon(Icons.Default.Delete, contentDescription = "Eliminar imagen", tint = MaterialTheme.colorScheme.error)
                                }
                            }
                        }

                        SliderWithLabel("Opacidad fondo", currentTheme.backgroundOpacity, 0f..1f) {
                            currentTheme = currentTheme.copy(backgroundOpacity = it)
                        }

                        SliderWithLabel("Desenfoque fondo", currentTheme.backgroundBlur, 0f..25f) {
                            currentTheme = currentTheme.copy(backgroundBlur = it)
                        }

                        SliderWithLabel("Desenfoque teclas", currentTheme.keysBlur, 0f..25f) {
                            currentTheme = currentTheme.copy(keysBlur = it)
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Efecto paralaje")
                            Spacer(Modifier.weight(1f))
                            Switch(
                                checked = currentTheme.parallaxEffect,
                                onCheckedChange = { currentTheme = currentTheme.copy(parallaxEffect = it) }
                            )
                        }
                    }
                }

                // Espacio extra al final para que el FAB no tape los controles
                Spacer(Modifier.height(100.dp))
            }
        }
    }

    showColorPickerFor?.let { onSelected ->
        ColorPickerDialog(
            initialColor = initialColorForPicker,
            onDismiss = { showColorPickerFor = null },
            onConfirm = { 
                onSelected(it)
                showColorPickerFor = null 
            }
        )
    }
}

@Composable
fun ColorButton(label: String, color: Int, onClick: () -> Unit) {
    FilledTonalButton(
        onClick = onClick, 
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        contentPadding = PaddingValues(horizontal = 8.dp),
        shape = MaterialTheme.shapes.medium
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start,
            modifier = Modifier.fillMaxWidth()
        ) {
            Box(
                modifier = Modifier
                    .size(16.dp)
                    .background(
                        androidx.compose.ui.graphics.Color(color),
                        MaterialTheme.shapes.extraSmall
                    )
                    .border(
                        1.dp,
                        MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                        MaterialTheme.shapes.extraSmall
                    )
            )
            Spacer(Modifier.width(8.dp))
            Text(label, maxLines = 1, style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
fun SliderWithLabel(label: String, value: Float, range: ClosedFloatingPointRange<Float>, onValueChange: (Float) -> Unit) {
    Column(modifier = Modifier.padding(vertical = 8.dp)) {
        Text("$label: ${String.format("%.2f", value)}")
        Slider(value = value, onValueChange = onValueChange, valueRange = range)
    }
}

@Composable
fun ColorPickerDialog(
    initialColor: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit
) {
    val controller = rememberColorPickerController()
    var selectedColor by remember { mutableStateOf(androidx.compose.ui.graphics.Color(initialColor)) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Selecciona un color") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(350.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                HsvColorPicker(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(250.dp)
                        .padding(10.dp),
                    controller = controller,
                    onColorChanged = { envelope ->
                        selectedColor = envelope.color
                    },
                    initialColor = androidx.compose.ui.graphics.Color(initialColor)
                )
                
                BrightnessSlider(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(10.dp)
                        .height(35.dp),
                    controller = controller
                )
                
                AlphaSlider(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(10.dp)
                        .height(35.dp),
                    controller = controller
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(selectedColor.toArgb()) }) { Text("Confirmar") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        }
    )
}

@Preview(name = "Light Mode", showBackground = true, showSystemUi = true)
@Preview(
    name = "Dark Mode",
    uiMode = Configuration.UI_MODE_NIGHT_YES,
    showBackground = true,
    showSystemUi = true
)
@Composable
fun PreviewThemeEditorScreen() {
    HexKeyboardTheme {
        ThemeEditorScreen(
            initialThemeId = null,
            onBack = {},
            onSave = {}
        )
    }
}
