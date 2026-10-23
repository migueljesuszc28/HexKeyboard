package com.example.hexkeyboard.ui.settings.themes

import android.app.Activity
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
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
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.hexkeyboard.data.repository.KeyboardTheme
import com.example.hexkeyboard.data.repository.ThemeUtils
import com.example.hexkeyboard.ui.theme.HexKeyboardTheme
import com.example.hexkeyboard.data.repository.ThemeUtils.enableMaxRefreshRate
import com.github.skydoves.colorpicker.compose.*
import kotlinx.coroutines.launch
import java.util.UUID

// ---------------------------------------------------------------------------
// Material 3 Expressive Utils
// ---------------------------------------------------------------------------
private val GroupCornerRadius = 28.dp
private val SegmentGapHeight = 2.dp

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
// ---------------------------------------------------------------------------

class ThemeEditorActivity : ComponentActivity() {

    @OptIn(ExperimentalMaterial3ExpressiveApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableMaxRefreshRate()
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
        )

        val themeId = intent.getStringExtra("theme_id")

        setContent {
            HexKeyboardTheme {
                MaterialExpressiveTheme(
                    colorScheme = MaterialTheme.colorScheme
                ) {
                    ThemeEditorScreen(
                        initialThemeId = themeId,
                        onBack = { finish() },
                        onSave = { finish() }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThemeEditorScreen(
    initialThemeId: String?,
    onBack: () -> Unit,
    onSave: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

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
    var initialColorForPicker by remember { mutableIntStateOf(android.graphics.Color.WHITE) }

    val cropLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val uriString = result.data?.getStringExtra("cropped_uri")
            if (uriString != null) {
                scope.launch {
                    val bitmap = ThemeUtils.loadBitmapFromUri(context, uriString)
                    if (bitmap != null) {
                        currentTheme = ThemeUtils.extractDynamicTheme(bitmap, currentTheme).copy(
                            backgroundImageUri = uriString,
                            backgroundColor = android.graphics.Color.TRANSPARENT
                        )
                    } else {
                        currentTheme = currentTheme.copy(
                            backgroundImageUri = uriString,
                            backgroundColor = android.graphics.Color.TRANSPARENT
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

    // Expressive Back Button Morphing Shape
    val backInteraction = remember { MutableInteractionSource() }
    val backShape = rememberMorphingShape(
        interactionSource = backInteraction,
        restCorner = 24.dp,
        pressedCorner = 14.dp
    )

    // Expressive FAB Morphing Shape
    val fabInteraction = remember { MutableInteractionSource() }
    val fabShape = rememberMorphingShape(
        interactionSource = fabInteraction,
        restCorner = 28.dp,
        pressedCorner = 12.dp
    )

    // Máscara de degradado superior (Efecto cristal)
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
                            if (initialThemeId == null) "Crear Tema" else "Editar Tema",
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
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Atrás")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent,
                        scrolledContainerColor = Color.Transparent
                    ),
                    scrollBehavior = scrollBehavior
                )
            }
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
                shape = fabShape,
                interactionSource = fabInteraction
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = padding.calculateTopPadding()),
            contentPadding = PaddingValues(
                bottom = padding.calculateBottomPadding() + 88.dp
            )
        ) {
            // Live Preview (Hero Section)
            stickyHeader {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.background)
                        .padding(top = 8.dp, bottom = 8.dp)
                ) {
                    SettingsCardContainer {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(bottom = 12.dp)
                            ) {
                                Icon(
                                    Icons.Outlined.Palette,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
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
                }
            }

            // Section 1: Information
            item { CategoryHeader("Información General", Icons.Default.Edit) }
            item {
                SettingsCardContainer {
                    Box(modifier = Modifier.padding(16.dp)) {
                        OutlinedTextField(
                            value = currentTheme.name,
                            onValueChange = { currentTheme = currentTheme.copy(name = it) },
                            label = { Text("Nombre del tema") },
                            placeholder = { Text("Ej: Mi Tema Neón") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            shape = RoundedCornerShape(16.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                unfocusedContainerColor = Color.Transparent,
                                focusedContainerColor = Color.Transparent
                            )
                        )
                    }
                }
            }

            // Section 2: Colors
            item { CategoryHeader("Colores Principales", Icons.Outlined.Palette) }
            item {
                SettingsCardContainer {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Box(modifier = Modifier.weight(1f)) {
                                ExpressiveColorButton("Fondo", currentTheme.backgroundColor) {
                                    initialColorForPicker = currentTheme.backgroundColor
                                    showColorPickerFor = { currentTheme = currentTheme.copy(backgroundColor = it) }
                                }
                            }
                            Box(modifier = Modifier.weight(1f)) {
                                ExpressiveColorButton("Teclas", currentTheme.keyBackgroundColor) {
                                    initialColorForPicker = currentTheme.keyBackgroundColor
                                    showColorPickerFor = { currentTheme = currentTheme.copy(keyBackgroundColor = it) }
                                }
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Box(modifier = Modifier.weight(1f)) {
                                ExpressiveColorButton("Texto", currentTheme.keyTextColor) {
                                    initialColorForPicker = currentTheme.keyTextColor
                                    showColorPickerFor = { currentTheme = currentTheme.copy(keyTextColor = it, isKeyTextColorCustom = true) }
                                }
                            }
                            Box(modifier = Modifier.weight(1f)) {
                                ExpressiveColorButton("Bordes", currentTheme.keyStrokeColor) {
                                    initialColorForPicker = currentTheme.keyStrokeColor
                                    showColorPickerFor = { currentTheme = currentTheme.copy(keyStrokeColor = it) }
                                }
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Box(modifier = Modifier.weight(1f)) {
                                ExpressiveColorButton("Pulsación", currentTheme.keyBackgroundPressedColor) {
                                    initialColorForPicker = currentTheme.keyBackgroundPressedColor
                                    showColorPickerFor = { currentTheme = currentTheme.copy(keyBackgroundPressedColor = it) }
                                }
                            }
                            Box(modifier = Modifier.weight(1f)) {
                                ExpressiveColorButton("Iconos", currentTheme.keyboardIconTint) {
                                    initialColorForPicker = currentTheme.keyboardIconTint
                                    showColorPickerFor = { currentTheme = currentTheme.copy(keyboardIconTint = it) }
                                }
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Box(modifier = Modifier.weight(1f)) {
                                ExpressiveColorButton("Popup", currentTheme.popupBackgroundColor) {
                                    initialColorForPicker = currentTheme.popupBackgroundColor
                                    showColorPickerFor = { currentTheme = currentTheme.copy(popupBackgroundColor = it) }
                                }
                            }
                            Box(modifier = Modifier.weight(1f)) {
                                ExpressiveColorButton("Popup Texto", currentTheme.popupTextColor) {
                                    initialColorForPicker = currentTheme.popupTextColor
                                    showColorPickerFor = { currentTheme = currentTheme.copy(popupTextColor = it) }
                                }
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Box(modifier = Modifier.weight(1f)) {
                                ExpressiveColorButton("Shift Activo", currentTheme.keyShiftActiveColor ?: currentTheme.keyTextColor) {
                                    initialColorForPicker = currentTheme.keyShiftActiveColor ?: currentTheme.keyTextColor
                                    showColorPickerFor = { currentTheme = currentTheme.copy(keyShiftActiveColor = it) }
                                }
                            }
                            Box(modifier = Modifier.weight(1f)) {
                                ExpressiveColorButton("Shift Inactivo", currentTheme.keyShiftInactiveColor ?: currentTheme.keyBackgroundSpecialColor) {
                                    initialColorForPicker = currentTheme.keyShiftInactiveColor ?: currentTheme.keyBackgroundSpecialColor
                                    showColorPickerFor = { currentTheme = currentTheme.copy(keyShiftInactiveColor = it) }
                                }
                            }
                        }
                    }
                }
            }

            // Section 3: Individual Key Colors
            item { CategoryHeader("Colores Individuales", Icons.Default.TouchApp) }
            item {
                SettingsCardContainer {
                    SwitchPreferenceItem(
                        title = "Personalizar por tecla",
                        subtitle = "Toca una tecla en la vista previa para cambiar su color",
                        checked = editIndividualKeys,
                        onCheckedChange = { editIndividualKeys = it },
                        icon = Icons.Default.TouchApp
                    )

                    if (editIndividualKeys && currentTheme.individualKeyColors.isNotEmpty()) {
                        SettingsGap()
                        Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), contentAlignment = Alignment.CenterEnd) {
                            TextButton(onClick = { currentTheme = currentTheme.copy(individualKeyColors = emptyMap()) }) {
                                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Restablecer colores")
                            }
                        }
                    }
                }
            }

            // Section 4: Image and Effects
            item { CategoryHeader("Imagen y Efectos", Icons.Default.Image) }
            item {
                SettingsCardContainer {
                    ListItem(
                        headlineContent = { Text(if (currentTheme.backgroundImageUri == null) "Fondo sin imagen" else "Imagen seleccionada", fontWeight = FontWeight.SemiBold) },
                        supportingContent = { Text("Selecciona o recorta una foto de tu galería") },
                        leadingContent = { Icon(Icons.Default.Image, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        modifier = Modifier.clickable { pickImageLauncher.launch("image/*") }
                    )

                    if (currentTheme.backgroundImageUri != null) {
                        SettingsGap()
                        ListItem(
                            headlineContent = { Text("Eliminar imagen", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.SemiBold) },
                            leadingContent = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                            modifier = Modifier.clickable { currentTheme = currentTheme.copy(backgroundImageUri = null) }
                        )
                    }

                    SettingsGap()
                    ExpressiveSliderSetting("Opacidad de fondo", currentTheme.backgroundOpacity, 0f..1f) {
                        currentTheme = currentTheme.copy(backgroundOpacity = it)
                    }
                    SettingsGap()
                    ExpressiveSliderSetting("Desenfoque de fondo", currentTheme.backgroundBlur, 0f..25f) {
                        currentTheme = currentTheme.copy(backgroundBlur = it)
                    }
                    SettingsGap()
                    ExpressiveSliderSetting("Desenfoque de teclas", currentTheme.keysBlur, 0f..25f) {
                        currentTheme = currentTheme.copy(keysBlur = it)
                    }
                    SettingsGap()
                    ExpressiveSliderSetting("Opacidad de teclas", currentTheme.keysOpacity, 0f..1f) {
                        currentTheme = currentTheme.copy(keysOpacity = it)
                    }
                    SettingsGap()
                    ExpressiveSliderSetting("Grosor de borde", currentTheme.keyStrokeWidth, 0f..10f) {
                        currentTheme = currentTheme.copy(keyStrokeWidth = it)
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

// ---------------------------------------------------------------------------
// Componentes Adaptados al Estilo Expressive
// ---------------------------------------------------------------------------

@Composable
fun SwitchPreferenceItem(
    title: String,
    subtitle: String? = null,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    icon: ImageVector? = null
) {
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
            onValueChange = onCheckedChange
        )
    )
}

@Composable
fun ExpressiveColorButton(label: String, color: Int, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val shape = rememberMorphingShape(
        interactionSource = interactionSource,
        restCorner = 16.dp,
        pressedCorner = 8.dp
    )

    FilledTonalButton(
        onClick = onClick,
        interactionSource = interactionSource,
        shape = shape,
        modifier = Modifier.fillMaxWidth().height(48.dp),
        contentPadding = PaddingValues(horizontal = 12.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start,
            modifier = Modifier.fillMaxWidth()
        ) {
            Box(
                modifier = Modifier
                    .size(20.dp)
                    .background(Color(color), CircleShape)
                    .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f), CircleShape)
            )
            Spacer(Modifier.width(10.dp))
            Text(label, maxLines = 1, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
fun ExpressiveSliderSetting(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f)
            )
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.secondaryContainer,
                modifier = Modifier.padding(start = 8.dp)
            ) {
                Text(
                    text = String.format("%.2f", value),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = range,
            modifier = Modifier
                .padding(top = 2.dp)
                .fillMaxWidth()
        )
    }
}

// ---------------------------------------------------------------------------
// El ColorPickerDialog mantiene la lógica pero se le añade RoundedCornerShape(28.dp) para consistencia
// ---------------------------------------------------------------------------
@Composable
fun ColorPickerDialog(
    initialColor: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit
) {
    val controller = rememberColorPickerController()
    val initialComposeColor = remember(initialColor) { Color(initialColor) }
    var selectedColor by remember { mutableStateOf(initialComposeColor) }

    fun formatColorHex(color: Color): String = String.format("#%08X", color.toArgb())

    fun parseColorHex(input: String): Color? {
        val clean = input.trim().removePrefix("#")
        return try {
            val argb = when (clean.length) {
                6 -> (0xFF000000 or clean.toLong(16)).toInt()
                8 -> clean.toLong(16).toInt()
                else -> return null
            }
            Color(argb)
        } catch (_: Exception) {
            null
        }
    }

    var hexInputText by remember { mutableStateOf(formatColorHex(initialComposeColor)) }
    var isHexValid by remember { mutableStateOf(true) }

    val presetColors = remember {
        listOf(
            Color.Transparent, Color.White, Color(0xFFE0E0E0), Color(0xFF757575),
            Color(0xFF212121), Color.Black, Color(0xFFF44336), Color(0xFFE91E63),
            Color(0xFF9C27B0), Color(0xFF2196F3), Color(0xFF00BCD4), Color(0xFF009688),
            Color(0xFF4CAF50), Color(0xFFFFEB3B), Color(0xFFFF9800)
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(28.dp),
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
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Original", style = MaterialTheme.typography.labelSmall)
                        Spacer(Modifier.height(6.dp))
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .background(initialComposeColor, CircleShape)
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
                        Spacer(Modifier.height(6.dp))
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .background(selectedColor, CircleShape)
                                .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f), CircleShape)
                        )
                    }
                }

                HsvColorPicker(
                    modifier = Modifier.fillMaxWidth().height(200.dp),
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

                Column(modifier = Modifier.fillMaxWidth()) {
                    Text("Brillo", style = MaterialTheme.typography.labelSmall)
                    Spacer(Modifier.height(2.dp))
                    BrightnessSlider(
                        modifier = Modifier.fillMaxWidth().height(30.dp).clip(RoundedCornerShape(16.dp)),
                        controller = controller
                    )
                }

                Column(modifier = Modifier.fillMaxWidth()) {
                    Text("Opacidad", style = MaterialTheme.typography.labelSmall)
                    Spacer(Modifier.height(2.dp))
                    AlphaSlider(
                        modifier = Modifier.fillMaxWidth().height(30.dp).clip(RoundedCornerShape(16.dp)),
                        controller = controller
                    )
                }

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
                    supportingText = if (!isHexValid) { { Text("Ej: #FF4285F4 o #4285F4") } } else null,
                    trailingIcon = {
                        if (isHexValid) {
                            Icon(Icons.Default.CheckCircle, contentDescription = "Válido", tint = MaterialTheme.colorScheme.primary)
                        } else {
                            Icon(Icons.Default.Warning, contentDescription = "Inválido", tint = MaterialTheme.colorScheme.error)
                        }
                    },
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp)
                )

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
                                    .size(36.dp)
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
            Button(onClick = { onConfirm(selectedColor.toArgb()) }, enabled = isHexValid) {
                Text("Confirmar")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
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