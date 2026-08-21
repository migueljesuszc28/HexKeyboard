package com.example.hexkeyboard.ui.settings.fontselector

import android.content.Context
import android.content.res.Configuration
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.example.hexkeyboard.data.repository.ThemeUtils
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import com.example.hexkeyboard.ui.theme.HexKeyboardTheme
import com.example.hexkeyboard.ui.theme.NunitoFontFamily
import java.io.File

class FontSelectorActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
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

    LaunchedEffect(Unit) {
        if (!isPreview) {
            customFonts = FontManager.listCustomFonts(context)
        }
    }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        uris.forEach { uri ->
            val fileName = getFileName(context, uri) ?: "font_${System.currentTimeMillis()}.ttf"
            FontManager.importFont(context, uri, fileName)
        }
        customFonts = FontManager.listCustomFonts(context)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Fuente", style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Atrás")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    filePickerLauncher.launch(arrayOf(
                        "font/*", 
                        "application/x-font-ttf", 
                        "application/x-font-otf",
                        "application/zip",
                        "application/x-zip-compressed"
                    ))
                },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) {
                Icon(Icons.Default.Add, contentDescription = "Importar fuente")
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            item {
                Text(
                    "Predeterminadas",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(16.dp)
                )
            }
            item {
                FontOptionItem(
                    name = "Sistema",
                    isSelected = selectedFontPath == "system",
                    fontFamily = FontFamily.Default,
                    onClick = {
                        scope.launch {
                            dataStore.edit { it[customFontKey] = "system" }
                        }
                    }
                )
            }
            item {
                FontOptionItem(
                    name = "Nunito",
                    isSelected = selectedFontPath == "nunito",
                    fontFamily = NunitoFontFamily,
                    onClick = {
                        scope.launch {
                            dataStore.edit { it[customFontKey] = "nunito" }
                        }
                    }
                )
            }

            item {
                Text(
                    "Fuentes personalizadas",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(16.dp)
                )
            }

            items(customFonts) { fontFile ->
                val tf = remember(fontFile) { FontManager.loadTypeface(fontFile) }
                FontOptionItem(
                    name = fontFile.name,
                    isSelected = selectedFontPath == fontFile.absolutePath,
                    fontFamily = if (tf != null) FontFamily(tf) else FontFamily.Default,
                    onDelete = {
                        FontManager.deleteFont(fontFile)
                        customFonts = FontManager.listCustomFonts(context)
                        if (selectedFontPath == fontFile.absolutePath) {
                            scope.launch {
                                dataStore.edit { it[customFontKey] = "system" }
                            }
                        }
                    },
                    onClick = {
                        scope.launch {
                            dataStore.edit { it[customFontKey] = fontFile.absolutePath }
                        }
                    }
                )
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
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f) else Color.Transparent
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RadioButton(
                selected = isSelected,
                onClick = null,
                modifier = Modifier.padding(end = 16.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontFamily = fontFamily,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                )
                Text(
                    text = "ABCDEFGHIJKLMNÑOPQRSTUVWXYZ",
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = fontFamily,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
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
        }
    }
}

private fun getFileName(context: Context, uri: Uri): String? {
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
    return result
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
