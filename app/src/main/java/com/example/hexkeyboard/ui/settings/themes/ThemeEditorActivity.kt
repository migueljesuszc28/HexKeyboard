package com.example.hexkeyboard.ui.settings.themes

import android.app.Activity
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.hexkeyboard.data.repository.KeyboardTheme
import com.example.hexkeyboard.data.repository.ThemeUtils
import com.example.hexkeyboard.data.repository.ThemeUtils.enableMaxRefreshRate
import com.example.hexkeyboard.ui.theme.HexKeyboardTheme
import com.github.skydoves.colorpicker.compose.*
import kotlinx.coroutines.launch
import java.util.UUID

class ThemeEditorActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableMaxRefreshRate()
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT)
        )

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
                title = { 
                    Text(
                        if (initialThemeId == null) "Crear Tema" else "Editar Tema",
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
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = {
                    if (currentTheme.name.isBlank()) {
                        Toast.makeText(context, "Por favor introduce un nombre para el tema", Toast.LENGTH_SHORT).show()
                    } else {
                        scope.launch {
                            ThemeUtils.saveCustomTheme(context, currentTheme)
                            onSave()
                        }
                    }
                },
                icon = { Icon(Icons.Default.Save, contentDescription = null) },
                text = { Text("Guardar Tema", fontWeight = FontWeight.Bold) },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = CircleShape
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = padding.calculateTopPadding())
        ) {
            // Live Preview (Hero Section)
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                tonalElevation = 2.dp
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(bottom = 8.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.size(28.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Outlined.Palette,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Vista previa en vivo",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
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
            }

            // Scrollable Controls
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = padding.calculateBottomPadding() + 88.dp)
            ) {
                // Section 1: Information
                EditorSectionHeader("Información General", Icons.Default.Edit)
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    tonalElevation = 1.dp
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        OutlinedTextField(
                            value = currentTheme.name,
                            onValueChange = { currentTheme = currentTheme.copy(name = it) },
                            label = { Text("Nombre del tema") },
                            placeholder = { Text("Ej: Mi Tema Neón") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            shape = RoundedCornerShape(16.dp)
                        )
                    }
                }

                // Section 2: Colors
                EditorSectionHeader("Colores Principales", Icons.Outlined.Palette)
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    tonalElevation = 1.dp
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
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
                                ColorButton("Pulsación", currentTheme.keyBackgroundPressedColor) { 
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

                // Section 3: Individual Key Colors
                EditorSectionHeader("Colores Individuales", Icons.Default.TouchApp)
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    tonalElevation = 1.dp
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Personalizar por tecla", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                                Text("Activa esta opción para cambiar el color tecla por tecla", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Switch(checked = editIndividualKeys, onCheckedChange = { editIndividualKeys = it })
                        }
                        if (editIndividualKeys) {
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                "Toca cualquier tecla en la previsualización en vivo arriba para elegir su color individual.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold
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

                // Section 4: Image and Effects
                EditorSectionHeader("Imagen de Fondo y Efectos", Icons.Default.Image)
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    tonalElevation = 1.dp
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Imagen de fondo", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(vertical = 12.dp)
                        ) {
                            Button(
                                onClick = { pickImageLauncher.launch("image/*") },
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Default.Image, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text(if (currentTheme.backgroundImageUri == null) "Seleccionar imagen" else "Cambiar imagen")
                            }
                            if (currentTheme.backgroundImageUri != null) {
                                Spacer(modifier = Modifier.width(8.dp))
                                IconButton(onClick = { currentTheme = currentTheme.copy(backgroundImageUri = null) }) {
                                    Icon(Icons.Default.Delete, contentDescription = "Eliminar imagen", tint = MaterialTheme.colorScheme.error)
                                }
                            }
                        }

                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                        EditorSlider("Opacidad de fondo", currentTheme.backgroundOpacity, 0f..1f) {
                            currentTheme = currentTheme.copy(backgroundOpacity = it)
                        }

                        EditorSlider("Desenfoque de fondo", currentTheme.backgroundBlur, 0f..25f) {
                            currentTheme = currentTheme.copy(backgroundBlur = it)
                        }

                        EditorSlider("Desenfoque de teclas", currentTheme.keysBlur, 0f..25f) {
                            currentTheme = currentTheme.copy(keysBlur = it)
                        }

                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(vertical = 4.dp)
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Efecto paralaje", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                                Text("La imagen de fondo reacciona al movimiento del giroscopio", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Switch(
                                checked = currentTheme.parallaxEffect,
                                onCheckedChange = { currentTheme = currentTheme.copy(parallaxEffect = it) }
                            )
                        }
                    }
                }
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
fun EditorSectionHeader(title: String, icon: ImageVector) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 10.dp)
    ) {
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
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
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
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start,
            modifier = Modifier.fillMaxWidth()
        ) {
            Box(
                modifier = Modifier
                    .size(18.dp)
                    .background(
                        androidx.compose.ui.graphics.Color(color),
                        CircleShape
                    )
                    .border(
                        1.dp,
                        MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                        CircleShape
                    )
            )
            Spacer(Modifier.width(8.dp))
            Text(label, maxLines = 1, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
fun EditorSlider(label: String, value: Float, range: ClosedFloatingPointRange<Float>, onValueChange: (Float) -> Unit) {
    Column(modifier = Modifier.padding(vertical = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.secondaryContainer
            ) {
                Text(
                    text = String.format("%.2f", value),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                )
            }
        }
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
    val initialComposeColor = remember(initialColor) { androidx.compose.ui.graphics.Color(initialColor) }
    var selectedColor by remember { mutableStateOf(initialComposeColor) }

    fun formatColorHex(color: androidx.compose.ui.graphics.Color): String {
        return String.format("#%08X", color.toArgb())
    }

    fun parseColorHex(input: String): androidx.compose.ui.graphics.Color? {
        val clean = input.trim().removePrefix("#")
        return try {
            val argb = when (clean.length) {
                6 -> (0xFF000000 or clean.toLong(16)).toInt()
                8 -> clean.toLong(16).toInt()
                else -> return null
            }
            androidx.compose.ui.graphics.Color(argb)
        } catch (_: Exception) {
            null
        }
    }

    var hexInputText by remember { mutableStateOf(formatColorHex(initialComposeColor)) }
    var isHexValid by remember { mutableStateOf(true) }

    val presetColors = remember {
        listOf(
            androidx.compose.ui.graphics.Color.Transparent,
            androidx.compose.ui.graphics.Color.White,
            androidx.compose.ui.graphics.Color(0xFFE0E0E0),
            androidx.compose.ui.graphics.Color(0xFF757575),
            androidx.compose.ui.graphics.Color(0xFF212121),
            androidx.compose.ui.graphics.Color.Black,
            androidx.compose.ui.graphics.Color(0xFFF44336),
            androidx.compose.ui.graphics.Color(0xFFE91E63),
            androidx.compose.ui.graphics.Color(0xFF9C27B0),
            androidx.compose.ui.graphics.Color(0xFF2196F3),
            androidx.compose.ui.graphics.Color(0xFF00BCD4),
            androidx.compose.ui.graphics.Color(0xFF009688),
            androidx.compose.ui.graphics.Color(0xFF4CAF50),
            androidx.compose.ui.graphics.Color(0xFFFFEB3B),
            androidx.compose.ui.graphics.Color(0xFFFF9800)
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Seleccionar color",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Live Color Preview Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        .padding(8.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Original", style = MaterialTheme.typography.labelSmall)
                        Spacer(Modifier.height(4.dp))
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(initialComposeColor)
                                .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f), CircleShape)
                        )
                    }

                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Nuevo", style = MaterialTheme.typography.labelSmall)
                        Spacer(Modifier.height(4.dp))
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(selectedColor)
                                .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f), CircleShape)
                        )
                    }
                }

                // HSV Color Wheel Picker
                HsvColorPicker(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    controller = controller,
                    onColorChanged = { envelope ->
                        if (envelope.fromUser) {
                            selectedColor = envelope.color
                            hexInputText = formatColorHex(envelope.color)
                            isHexValid = true
                        }
                    },
                    initialColor = initialComposeColor
                )

                // Brightness Slider
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text("Brillo", style = MaterialTheme.typography.labelSmall)
                    Spacer(Modifier.height(2.dp))
                    BrightnessSlider(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(30.dp)
                            .clip(RoundedCornerShape(8.dp)),
                        controller = controller
                    )
                }

                // Alpha / Opacity Slider
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text("Opacidad", style = MaterialTheme.typography.labelSmall)
                    Spacer(Modifier.height(2.dp))
                    AlphaSlider(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(30.dp)
                            .clip(RoundedCornerShape(8.dp)),
                        controller = controller
                    )
                }

                // Hexadecimal Code Input
                OutlinedTextField(
                    value = hexInputText,
                    onValueChange = { input ->
                        hexInputText = input.uppercase()
                        val parsed = parseColorHex(input)
                        if (parsed != null) {
                            isHexValid = true
                            selectedColor = parsed
                            controller.selectByColor(parsed, false)
                        } else {
                            isHexValid = false
                        }
                    },
                    label = { Text("Código Hexadecimal") },
                    singleLine = true,
                    isError = !isHexValid,
                    supportingText = if (!isHexValid) {
                        { Text("Ej: #FF4285F4 o #4285F4") }
                    } else null,
                    trailingIcon = {
                        if (isHexValid) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = "Válido",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = "Inválido",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    },
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                // Preset Swatches Row
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text("Paleta rápida", style = MaterialTheme.typography.labelSmall)
                    Spacer(Modifier.height(6.dp))
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(presetColors) { color ->
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(color)
                                    .border(
                                        width = if (color == selectedColor) 2.dp else 1.dp,
                                        color = if (color == selectedColor) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                                        shape = CircleShape
                                    )
                                    .clickable {
                                        selectedColor = color
                                        hexInputText = formatColorHex(color)
                                        isHexValid = true
                                        controller.selectByColor(color, false)
                                    }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(selectedColor.toArgb()) },
                enabled = isHexValid
            ) {
                Text("Confirmar")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("Cancelar")
            }
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
