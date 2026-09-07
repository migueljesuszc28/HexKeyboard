package com.example.hexkeyboard.ui.keyboard

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.graphics.toColorInt
import com.example.hexkeyboard.data.repository.KeyboardTheme
import com.example.hexkeyboard.logic.managers.ClipboardItem
import com.example.hexkeyboard.ui.keyboard.components.HexKeyboardView
import com.example.hexkeyboard.ui.keyboard.panels.ClipboardPanel
import com.example.hexkeyboard.ui.keyboard.panels.EmojiPanel
import com.example.hexkeyboard.ui.keyboard.panels.FunctionsPanel
import com.example.hexkeyboard.ui.theme.HexKeyboardTheme

private val PreviewLightTheme = KeyboardTheme(
    id = "light",
    backgroundColor = "#F5F5F5".toColorInt(),
    keyBackgroundColor = android.graphics.Color.WHITE,
    keyTextColor = android.graphics.Color.BLACK,
    keyboardIconTint = "#5F6368".toColorInt(),
    keyShiftActiveColor = "#1A73E8".toColorInt()
)

private val PreviewDarkTheme = KeyboardTheme(
    id = "dark",
    backgroundColor = "#121212".toColorInt(),
    keyBackgroundSpecialColor = "#2C2C2C".toColorInt(),
    keyBackgroundColor = "#2C2C2C".toColorInt(),
    keyTextColor = android.graphics.Color.WHITE,
    keyboardIconTint = "#E8EAED".toColorInt(),
    keyShiftActiveColor = "#8AB4F8".toColorInt()
)

@Preview(name = "Keyboard - Light", showBackground = true)
@Preview(name = "Keyboard - Dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
fun PreviewHexKeyboardView() {
    val isDark = isSystemInDarkTheme()
    val theme = if (isDark) PreviewDarkTheme else PreviewLightTheme

    HexKeyboardTheme {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = Color(theme.backgroundColor),
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Box(modifier = Modifier.fillMaxWidth().height(280.dp)) {
                    AndroidView(
                        factory = { ctx ->
                            HexKeyboardView(ctx).apply {
                                this.symbolPages = mapOf("basic" to listOf("!", "@", "#"))
                                this.longPressAlternatives = emptyMap()
                                this.keyboardTheme = theme
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

@Preview(name = "Emoji Panel - Light", showBackground = true)
@Preview(name = "Emoji Panel - Dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
fun PreviewEmojiPanel() {
    val isDark = isSystemInDarkTheme()
    val theme = if (isDark) PreviewDarkTheme else PreviewLightTheme

    HexKeyboardTheme {
        Box(modifier = Modifier.fillMaxWidth().height(280.dp).background(Color(theme.backgroundColor))) {
            EmojiPanel(
                onEmojiSelected = {},
                onBack = {},
                theme = theme
            )
        }
    }
}

@Preview(name = "Clipboard Panel - Light", showBackground = true)
@Preview(name = "Clipboard Panel - Dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
fun PreviewClipboardPanel() {
    val isDark = isSystemInDarkTheme()
    val theme = if (isDark) PreviewDarkTheme else PreviewLightTheme

    HexKeyboardTheme {
        Box(modifier = Modifier.fillMaxWidth().height(280.dp).background(Color(theme.backgroundColor))) {
            ClipboardPanel(
                history = listOf(
                    ClipboardItem("Hola"),
                    ClipboardItem("Este es un mensaje de prueba", isPinned = true),
                    ClipboardItem("Hex Keyboard es genial")
                ),
                onItemSelected = {},
                onDelete = {},
                onTogglePin = {},
                onLongPress = {},
                onBack = {},
                theme = theme
            )
        }
    }
}

@Preview(name = "Functions Panel - Light", showBackground = true)
@Preview(name = "Functions Panel - Dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
fun PreviewFunctionsPanel() {
    val isDark = isSystemInDarkTheme()
    val theme = if (isDark) PreviewDarkTheme else PreviewLightTheme

    HexKeyboardTheme {
        Box(modifier = Modifier.fillMaxWidth().height(280.dp).background(Color(theme.backgroundColor))) {
            FunctionsPanel(
                onBack = {},
                onSettings = {},
                theme = theme
            )
        }
    }
}
