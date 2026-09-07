package com.example.hexkeyboard.ui.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.example.hexkeyboard.data.repository.ThemeUtils.enableMaxRefreshRate
import com.example.hexkeyboard.logic.managers.ClipboardHistoryManager
import com.example.hexkeyboard.logic.managers.ClipboardItem
import com.example.hexkeyboard.ui.theme.HexKeyboardTheme
import kotlinx.coroutines.launch

class ClipboardEditActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableMaxRefreshRate()
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT)
        )

        val text = intent.getStringExtra("item_text") ?: ""
        val timestamp = intent.getLongExtra("item_timestamp", 0L)
        val imageUri = intent.getStringExtra("item_image_uri")
        val mimeType = intent.getStringExtra("item_mime_type")
        val isPinned = intent.getBooleanExtra("item_is_pinned", false)

        setContent {
            HexKeyboardTheme {
                var editText by remember { mutableStateOf(text) }

                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(28.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                            tonalElevation = 6.dp,
                            shadowElevation = 12.dp
                        ) {
                            Column(
                                modifier = Modifier.padding(24.dp),
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                Text(
                                    text = "Editar",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                OutlinedTextField(
                                    value = editText,
                                    onValueChange = { editText = it },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(16.dp),
                                    maxLines = 6
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.End,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    TextButton(onClick = { finish() }) {
                                        Text("Cancelar")
                                    }
                                    Spacer(Modifier.width(8.dp))
                                    Button(
                                        onClick = {
                                            if (editText.isNotBlank()) {
                                                lifecycleScope.launch {
                                                    val trimmed = editText.trim()
                                                    val oldItem = ClipboardItem(
                                                        text = text,
                                                        imageUri = imageUri,
                                                        mimeType = mimeType,
                                                        timestamp = timestamp,
                                                        isPinned = isPinned
                                                    )
                                                    ClipboardHistoryManager.updateItem(
                                                        this@ClipboardEditActivity,
                                                        oldItem,
                                                        trimmed
                                                    )

                                                    // Update system clipboard
                                                    try {
                                                        val clipboard = getSystemService(
                                                            CLIPBOARD_SERVICE
                                                        ) as ClipboardManager
                                                        val clip = ClipData.newPlainText("Copied", trimmed)
                                                        clipboard.setPrimaryClip(clip)
                                                    } catch (_: Exception) {}

                                                    finish()
                                                }
                                            }
                                        },
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Text("Guardar")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}