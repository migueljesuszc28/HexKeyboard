package com.example.mantiskeyboard.ui.settings.themes

import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.mantiskeyboard.data.repository.KeyboardTheme
import com.example.mantiskeyboard.data.repository.ThemeUtils
import com.example.mantiskeyboard.data.repository.ThemeUtils.enableMaxRefreshRate
import com.example.mantiskeyboard.ui.theme.MantisKeyboardTheme
import kotlinx.coroutines.launch

// Importamos las funciones utilitarias de Material 3 Expressive que ya definiste en SettingsActivity
import com.example.mantiskeyboard.ui.settings.CategoryHeader
import com.example.mantiskeyboard.ui.settings.SettingsCardContainer
import com.example.mantiskeyboard.ui.settings.SettingsGap
import com.example.mantiskeyboard.ui.settings.rememberMorphingShape

class ThemeSettingsActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableMaxRefreshRate()
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
        )

        setContent {
            MantisKeyboardTheme {
                ThemeSettingsScreen(
                    onBack = { finish() },
                    onEditTheme = { themeId ->
                        val intent = Intent(this@ThemeSettingsActivity, ThemeEditorActivity::class.java).apply {
                            putExtra("theme_id", themeId)
                        }
                        startActivity(intent)
                    },
                    onCreateTheme = {
                        startActivity(Intent(this@ThemeSettingsActivity, ThemeEditorActivity::class.java))
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThemeSettingsScreen(
    onBack: () -> Unit,
    onEditTheme: (String) -> Unit,
    onCreateTheme: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    val currentTheme by ThemeUtils.getKeyboardThemeFlow(context).collectAsState(initial = null)
    val themes by ThemeUtils.getAllThemesFlow(context).collectAsState(initial = emptyList())
    var showDeleteDialog by remember { mutableStateOf<KeyboardTheme?>(null) }

    val systemThemes = remember(themes) { themes.filter { !it.id.startsWith("custom_") } }
    val customThemes = remember(themes) { themes.filter { it.id.startsWith("custom_") } }

    // Morphing Shapes usando tu función importada
    val backInteraction = remember { MutableInteractionSource() }
    val backShape = rememberMorphingShape(backInteraction, 24.dp, 14.dp)

    val fabInteraction = remember { MutableInteractionSource() }
    val fabShape = rememberMorphingShape(fabInteraction, 28.dp, 14.dp)

    // Máscara superior corregida sin arrayOf()
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

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Box(modifier = Modifier.fillMaxWidth().background(brush = topBarMaskBrush)) {
                LargeTopAppBar(
                    title = {
                        Text("Temas del Teclado", fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent, scrolledContainerColor = Color.Transparent),
                    scrollBehavior = scrollBehavior
                )
            }
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onCreateTheme,
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("Crear Tema", fontWeight = FontWeight.Bold) },
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
            // Live Preview Hero Section
            currentTheme?.let { theme ->
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
                                        text = "Tema activo: ${theme.name}",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                KeyboardPreview(
                                    theme = theme,
                                    modifier = Modifier.fillMaxWidth().wrapContentHeight()
                                )
                            }
                        }
                    }
                }
            }

            // Temas Predeterminados
            item { CategoryHeader("Temas Predeterminados", Icons.Outlined.Palette) }
            item {
                SettingsCardContainer {
                    systemThemes.forEachIndexed { index, theme ->
                        ThemeItem(
                            theme = theme,
                            isSelected = theme.id == currentTheme?.id,
                            onSelect = { scope.launch { ThemeUtils.saveThemeSelection(context, theme.id) } },
                            onEdit = { onEditTheme(theme.id) },
                            onDelete = { showDeleteDialog = theme }
                        )
                        if (index < systemThemes.lastIndex) SettingsGap()
                    }
                }
            }

            // Temas Personalizados
            item { CategoryHeader("Mis Temas", Icons.Default.Brush) }
            item {
                SettingsCardContainer {
                    if (customThemes.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Aún no has creado temas personalizados.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        customThemes.forEachIndexed { index, theme ->
                            ThemeItem(
                                theme = theme,
                                isSelected = theme.id == currentTheme?.id,
                                onSelect = { scope.launch { ThemeUtils.saveThemeSelection(context, theme.id) } },
                                onEdit = { onEditTheme(theme.id) },
                                onDelete = { showDeleteDialog = theme }
                            )
                            if (index < customThemes.lastIndex) SettingsGap()
                        }
                    }
                }
            }
        }
    }

    // Diálogo de eliminación
    showDeleteDialog?.let { theme ->
        AlertDialog(
            onDismissRequest = { showDeleteDialog = null },
            title = { Text("¿Eliminar tema?", fontWeight = FontWeight.Bold) },
            text = { Text("Se eliminará el tema '${theme.name}'. Esta acción no se puede deshacer.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            ThemeUtils.deleteCustomTheme(context, theme.id)
                            showDeleteDialog = null
                        }
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Eliminar")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = null }) { Text("Cancelar") }
            }
        )
    }
}

@Composable
fun ThemeItem(
    theme: KeyboardTheme,
    isSelected: Boolean,
    onSelect: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val isCustom = theme.id.startsWith("custom_")

    ListItem(
        headlineContent = {
            Text(
                text = theme.name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                color = if (isSelected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface
            )
        },
        leadingContent = {
            Surface(
                modifier = Modifier.size(40.dp),
                shape = RoundedCornerShape(10.dp),
                color = Color(theme.backgroundColor),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text("⬡", color = Color(theme.keyTextColor), fontWeight = FontWeight.Bold)
                }
            }
        },
        trailingContent = {
            if (isCustom) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (isSelected) {
                        Icon(
                            Icons.Default.Check,
                            contentDescription = "Seleccionado",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(end = 12.dp).size(20.dp)
                        )
                    }
                    IconButton(onClick = onEdit) {
                        Icon(Icons.Default.Edit, contentDescription = "Editar", tint = MaterialTheme.colorScheme.primary)
                    }
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Default.Delete, contentDescription = "Eliminar", tint = MaterialTheme.colorScheme.error)
                    }
                }
            } else if (isSelected) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary
                ) {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = "Seleccionado",
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.padding(4.dp).size(16.dp)
                    )
                }
            }
        },
        colors = ListItemDefaults.colors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f) else Color.Transparent
        ),
        modifier = Modifier.clickable(onClick = onSelect)
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
fun PreviewThemeSettingsScreen() {
    MantisKeyboardTheme {
        ThemeSettingsScreen(
            onBack = {},
            onEditTheme = {},
            onCreateTheme = {}
        )
    }
}