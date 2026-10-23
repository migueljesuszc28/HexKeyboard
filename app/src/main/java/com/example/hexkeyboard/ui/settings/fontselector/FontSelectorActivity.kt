package com.example.hexkeyboard.ui.settings.fontselector

import android.content.Context
import android.content.res.Configuration
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FontDownload
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.example.hexkeyboard.data.repository.ThemeUtils
import com.example.hexkeyboard.data.repository.ThemeUtils.enableMaxRefreshRate
import com.example.hexkeyboard.ui.component.ExpressiveLoadingScreen
import com.example.hexkeyboard.ui.theme.HexKeyboardTheme
import com.example.hexkeyboard.ui.theme.NunitoFontFamily
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.time.Duration.Companion.milliseconds

// Importamos las funciones utilitarias de Material 3 Expressive
import com.example.hexkeyboard.ui.settings.CategoryHeader
import com.example.hexkeyboard.ui.settings.SettingsCardContainer
import com.example.hexkeyboard.ui.settings.SettingsGap
import com.example.hexkeyboard.ui.settings.rememberMorphingShape

class FontSelectorActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableMaxRefreshRate()
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
        )
        setContent {
            HexKeyboardTheme {
                FontSelectorScreen(onBack = { finish() })
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FontSelectorScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val isPreview = LocalInspectionMode.current
    val dataStore = ThemeUtils.getDataStore(context)
    val customFontKey = stringPreferencesKey("custom_font_path")

    val selectedFontPathFlow = remember { dataStore.data.map { it[customFontKey] ?: "system" } }
    val selectedFontPath by selectedFontPathFlow.collectAsState("system")
    var customFonts by remember { mutableStateOf(emptyList<File>()) }
    var isLoading by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (!isPreview) {
            isLoading = true
            withContext(Dispatchers.IO) {
                val list = FontManager.listCustomFonts(context)
                customFonts = list
                val currentPath = dataStore.data.first()[customFontKey] ?: "system"
                if (currentPath != "system" && currentPath != "nunito") {
                    val file = File(currentPath)
                    if (!file.exists() || file.length() == 0L) {
                        dataStore.edit { it[customFontKey] = "system" }
                    }
                }
            }
            isLoading = false
        }
    }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) {
            isLoading = true
            scope.launch {
                withContext(Dispatchers.IO) {
                    uris.forEach { uri ->
                        val fileName = getFileName(context, uri) ?: "font_${System.currentTimeMillis()}.ttf"
                        FontManager.importFont(context, uri, fileName)
                    }
                    customFonts = FontManager.listCustomFonts(context)
                }
                isLoading = false
            }
        }
    }

    var searchQuery by remember { mutableStateOf("") }
    var isSearchActive by remember { mutableStateOf(false) }

    val filteredCustomFonts = remember(customFonts, searchQuery) {
        if (searchQuery.isBlank()) customFonts
        else customFonts.filter { it.name.contains(searchQuery, ignoreCase = true) }
    }

    val showDefaultSystem = remember(searchQuery) {
        searchQuery.isBlank() || "sistema".contains(searchQuery, ignoreCase = true)
    }

    val showDefaultNunito = remember(searchQuery) {
        searchQuery.isBlank() || "nunito".contains(searchQuery, ignoreCase = true)
    }

    // Morphing Shapes
    val backInteraction = remember { MutableInteractionSource() }
    val backShape = rememberMorphingShape(backInteraction, 24.dp, 14.dp)

    val searchInteraction = remember { MutableInteractionSource() }
    val searchShape = rememberMorphingShape(searchInteraction, 24.dp, 14.dp)

    val fabInteraction = remember { MutableInteractionSource() }
    val fabShape = rememberMorphingShape(fabInteraction, 28.dp, 14.dp)

    val surfaceColor = MaterialTheme.colorScheme.surface
    val isDark = surfaceColor.luminance() < 0.5f

    val topBarMaskBrush = remember(isDark, surfaceColor) {
        if (isDark) {
            Brush.verticalGradient(
                0.0f to Color.Black.copy(alpha = 0.55f),
                0.6f to Color.Black.copy(alpha = 0.20f),
                1.0f to Color.Transparent
            )
        } else {
            Brush.verticalGradient(
                0.0f to surfaceColor.copy(alpha = 0.92f),
                0.6f to surfaceColor.copy(alpha = 0.50f),
                1.0f to Color.Transparent
            )
        }
    }

    // Comportamiento de Scroll contraíble idéntico a SettingsActivity
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(brush = topBarMaskBrush)
            ) {
                Column {
                    // Cambiamos TopAppBar por LargeTopAppBar
                    LargeTopAppBar(
                        title = {
                            Text(
                                "Fuente del Teclado",
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
                                modifier = Modifier.padding(start = 4.dp).size(48.dp)
                            ) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Atrás")
                            }
                        },
                        actions = {
                            FilledTonalIconButton(
                                onClick = {
                                    isSearchActive = !isSearchActive
                                    if (!isSearchActive) searchQuery = ""
                                },
                                shape = searchShape,
                                interactionSource = searchInteraction,
                                modifier = Modifier.padding(end = 4.dp).size(48.dp)
                            ) {
                                Icon(
                                    if (isSearchActive) Icons.Default.Close else Icons.Default.Search,
                                    contentDescription = "Buscar"
                                )
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = Color.Transparent,
                            scrolledContainerColor = Color.Transparent
                        ),
                        scrollBehavior = scrollBehavior
                    )

                    AnimatedVisibility(
                        visible = isSearchActive,
                        enter = expandVertically() + fadeIn(),
                        exit = shrinkVertically() + fadeOut()
                    ) {
                        TextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            placeholder = { Text("Buscar fuente...") },
                            leadingIcon = { Icon(Icons.Default.Search, null) },
                            trailingIcon = {
                                if (searchQuery.isNotEmpty()) {
                                    IconButton(onClick = { searchQuery = "" }) {
                                        Icon(Icons.Default.Clear, null)
                                    }
                                }
                            },
                            colors = TextFieldDefaults.colors(
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent,
                                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow
                            ),
                            shape = RoundedCornerShape(24.dp), // Ajustado al estilo Expressive
                            singleLine = true
                        )
                    }
                }
            }
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = {
                    filePickerLauncher.launch(arrayOf(
                        "font/*",
                        "application/x-font-ttf",
                        "application/x-font-otf",
                        "application/zip",
                        "application/x-zip-compressed"
                    ))
                },
                icon = { Icon(Icons.Default.Add, contentDescription = "Importar fuente") },
                text = { Text("Importar fuente", fontWeight = FontWeight.Bold) },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = fabShape,
                interactionSource = fabInteraction
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        AnimatedContent(
            targetState = isLoading,
            transitionSpec = {
                fadeIn(animationSpec = tween(300)) togetherWith fadeOut(animationSpec = tween(300))
            },
            label = "fonts_loading_transition"
        ) { loading ->
            if (loading) {
                ExpressiveLoadingScreen(
                    title = "Cargando fuentes",
                    subtitle = "Escaneando catálogo de fuentes...",
                    icon = Icons.Default.FontDownload,
                    modifier = Modifier.padding(padding)
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        top = padding.calculateTopPadding(),
                        bottom = padding.calculateBottomPadding() + 88.dp
                    )
                ) {
                    // FUENTES PREDETERMINADAS
                    if (showDefaultSystem || showDefaultNunito) {
                        item { CategoryHeader("Fuentes Predeterminadas", Icons.Default.FontDownload) }
                        item {
                            SettingsCardContainer {
                                if (showDefaultSystem) {
                                    FontOptionItem(
                                        name = "Sistema",
                                        isSelected = selectedFontPath == "system",
                                        fontFamily = FontFamily.Default,
                                        onClick = { scope.launch { dataStore.edit { it[customFontKey] = "system" } } }
                                    )
                                }
                                if (showDefaultSystem && showDefaultNunito) {
                                    SettingsGap()
                                }
                                if (showDefaultNunito) {
                                    FontOptionItem(
                                        name = "Nunito",
                                        isSelected = selectedFontPath == "nunito",
                                        fontFamily = NunitoFontFamily,
                                        onClick = { scope.launch { dataStore.edit { it[customFontKey] = "nunito" } } }
                                    )
                                }
                            }
                        }
                    }

                    // FUENTES PERSONALIZADAS
                    if (customFonts.isNotEmpty() || (searchQuery.isNotBlank() && filteredCustomFonts.isNotEmpty())) {
                        item { CategoryHeader("Fuentes Personalizadas", Icons.Default.Add) }
                        item {
                            SettingsCardContainer {
                                if (filteredCustomFonts.isEmpty()) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(24.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = if (searchQuery.isBlank()) "No has importado fuentes (.ttf / .otf). Usa el botón inferior para añadir tus propias fuentes." else "No se encontraron fuentes personalizadas para \"$searchQuery\"",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                } else {
                                    val customFontTypefaces = remember(filteredCustomFonts) { filteredCustomFonts.associateWith { FontManager.loadTypeface(it) } }
                                    filteredCustomFonts.forEachIndexed { index, fontFile ->
                                        val tf = customFontTypefaces[fontFile]
                                        FontOptionItem(
                                            name = fontFile.name,
                                            isSelected = selectedFontPath == fontFile.absolutePath,
                                            fontFamily = if (tf != null) FontFamily(tf) else FontFamily.Default,
                                            onDelete = {
                                                scope.launch {
                                                    withContext(Dispatchers.IO) {
                                                        FontManager.deleteFont(fontFile)
                                                        val list = FontManager.listCustomFonts(context)
                                                        customFonts = list
                                                    }
                                                    if (selectedFontPath == fontFile.absolutePath) {
                                                        dataStore.edit { it[customFontKey] = "system" }
                                                    }
                                                }
                                            },
                                            onClick = {
                                                scope.launch { dataStore.edit { it[customFontKey] = fontFile.absolutePath } }
                                            }
                                        )
                                        if (index < filteredCustomFonts.lastIndex) {
                                            SettingsGap()
                                        }
                                    }
                                }
                            }
                        }
                    } else if (searchQuery.isNotBlank() && !showDefaultSystem && !showDefaultNunito) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 48.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "No se encontraron resultados para \"$searchQuery\"",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.outline
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
fun FontOptionItem(
    name: String,
    isSelected: Boolean,
    fontFamily: FontFamily,
    onDelete: (() -> Unit)? = null,
    onClick: () -> Unit
) {
    ListItem(
        headlineContent = {
            Text(
                text = name,
                style = MaterialTheme.typography.bodyLarge,
                fontFamily = fontFamily,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                color = if (isSelected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface
            )
        },
        supportingContent = {
            Text(
                text = "ABCDEFGHIJKLMNÑOPQRSTUVWXYZ 1234567890",
                style = MaterialTheme.typography.labelSmall,
                fontFamily = fontFamily,
                color = if (isSelected) MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        leadingContent = {
            RadioButton(
                selected = isSelected,
                onClick = null
            )
        },
        trailingContent = {
            if (onDelete != null) {
                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Eliminar",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        },
        colors = ListItemDefaults.colors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f) else Color.Transparent
        ),
        modifier = Modifier.clickable(onClick = onClick)
    )
}

private suspend fun getFileName(context: Context, uri: Uri): String? = withContext(Dispatchers.IO) {
    var result: String? = null
    if (uri.scheme == "content") {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index != -1) result = cursor.getString(index)
            }
        }
    }
    if (result == null) {
        result = uri.path
        val cut = result?.lastIndexOf('/') ?: -1
        if (cut != -1) result = result?.substring(cut + 1)
    }
    result
}

@Preview(name = "Light Mode", showBackground = true, showSystemUi = true)
@Preview(
    name = "Dark Mode",
    uiMode = Configuration.UI_MODE_NIGHT_YES,
    showBackground = true,
    showSystemUi = true
)
@Composable
fun PreviewFontSelectorScreen() {
    HexKeyboardTheme {
        FontSelectorScreen(onBack = {})
    }
}