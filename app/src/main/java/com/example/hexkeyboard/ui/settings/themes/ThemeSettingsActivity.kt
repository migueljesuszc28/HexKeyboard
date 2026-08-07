package com.example.hexkeyboard.ui.settings.themes

import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.hexkeyboard.data.repository.KeyboardTheme
import com.example.hexkeyboard.data.repository.ThemeUtils
import com.example.hexkeyboard.ui.theme.HexKeyboardTheme
import kotlinx.coroutines.launch

class ThemeSettingsActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            HexKeyboardTheme {
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
    
    val currentTheme by ThemeUtils.getKeyboardThemeFlow(context).collectAsState(initial = null)
    val themes by ThemeUtils.getAllThemesFlow(context).collectAsState(initial = emptyList())
    var showDeleteDialog by remember { mutableStateOf<KeyboardTheme?>(null) }

    Scaffold(
        topBar = {
// ...
            TopAppBar(
                title = { Text("Temas del Teclado") },
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
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onCreateTheme,
                containerColor = Color(0xFF2196F3),
                contentColor = Color.White
            ) {
                Icon(Icons.Default.Add, contentDescription = "Crear Tema")
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            // Preview Section
            currentTheme?.let { theme -> 
                Box(
                    modifier = Modifier
                        .padding(16.dp)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    KeyboardPreview(
                        theme = theme,
                        modifier = Modifier
                            .fillMaxWidth()
                            .wrapContentHeight()
                    )
                }
            }

            Text(
                text = "Selecciona un tema",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(16.dp)
            )

            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(bottom = 80.dp)
            ) {
                items(themes) { theme ->
                    ThemeItem(
                        theme = theme,
                        isSelected = theme.id == currentTheme?.id,
                        onSelect = {
                            scope.launch {
                                ThemeUtils.saveThemeSelection(context, theme.id)
                            }
                        },
                        onEdit = { onEditTheme(theme.id) },
                        onDelete = { showDeleteDialog = theme }
                    )
                }
            }
        }
    }

    showDeleteDialog?.let { theme ->
        AlertDialog(
            onDismissRequest = { showDeleteDialog = null },
            title = { Text("¿Eliminar tema?") },
            text = { Text("Esta acción no se puede deshacer.") },
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

    // 1. Envolvemos todo en un Surface
    Surface(
        modifier = Modifier
            .padding(horizontal = 16.dp, vertical = 6.dp) // 2. Añadimos margen entre items
            .fillMaxWidth(),
        shape = RoundedCornerShape(16.dp), // 3. Definimos las esquinas redondeadas
        // 4. El color de fondo lo manejamos aquí
        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer
        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        onClick = onSelect // 5. Movemos el click aquí para que el efecto visual (ripple) respete la forma redondeada
    ) {
        ListItem(
            headlineContent = {
                Text(
                    text = theme.name,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                )
            },
            leadingContent = {
                Surface(
                    modifier = Modifier.size(40.dp),
                    shape = MaterialTheme.shapes.small,
                    color = Color(theme.backgroundColor),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text("⬡", color = Color(theme.keyTextColor))
                    }
                }
            },
            trailingContent = {
                if (isCustom) {
                    Row {
                        IconButton(onClick = onEdit) {
                            Icon(Icons.Default.Edit, contentDescription = "Editar")
                        }
                        IconButton(onClick = onDelete) {
                            Icon(Icons.Default.Delete, contentDescription = "Eliminar")
                        }
                    }
                } else if (isSelected) {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = "Seleccionado",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            },
            // 6. Importante: poner el color del ListItem en Transparent para que se vea el del Surface
            colors = ListItemDefaults.colors(
                containerColor = Color.Transparent
            )
        )
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
fun PreviewThemeSettingsScreen() {
    HexKeyboardTheme {
        ThemeSettingsScreen(
            onBack = {},
            onEditTheme = {},
            onCreateTheme = {}
        )
    }
}
