package com.example.hexkeyboard.ui.keyboard

import android.content.SharedPreferences
import android.graphics.PointF
import android.graphics.Typeface
import android.view.View
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.res.ResourcesCompat
import androidx.preference.PreferenceManager
import com.example.hexkeyboard.R
import com.example.hexkeyboard.data.repository.KeyboardTheme
import com.example.hexkeyboard.logic.managers.ClipboardHistoryManager
import com.example.hexkeyboard.service.HexKeyboardService
import com.example.hexkeyboard.ui.keyboard.panels.ClipboardPanel
import com.example.hexkeyboard.ui.keyboard.panels.EmojiPanel
import com.example.hexkeyboard.ui.keyboard.panels.FunctionsPanel
import com.example.hexkeyboard.ui.keyboard.components.HexKeyboardView
import com.example.hexkeyboard.viewmodel.KeyboardViewModel
import java.io.File

@Composable
fun KeyboardMainSection(
    viewModel: KeyboardViewModel,
    theme: KeyboardTheme,
    hasBackgroundImage: Boolean,
    onParallax: (Float, Float) -> Unit
) {
    val currentView by viewModel.currentView.collectAsState()
    val isEmojiSearchActive by viewModel.isEmojiSearchActive.collectAsState()
    val currentLocale by viewModel.currentLocale.collectAsState()
    val keyboardVisible = currentView == "keyboard" || isEmojiSearchActive

    val context = LocalContext.current
    val service = context as? HexKeyboardService
    val prefs = remember { PreferenceManager.getDefaultSharedPreferences(context) }
    var fontPath by remember { mutableStateOf(prefs.getString("custom_font_path", "system") ?: "system") }

    DisposableEffect(prefs) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { p, key ->
            if (key == "custom_font_path") {
                fontPath = p.getString(key, "system") ?: "system"
            }
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    val nunitoTypeface = remember(fontPath) {
        when (fontPath) {
            "nunito" -> ResourcesCompat.getFont(context, R.font.nunito) ?: Typeface.DEFAULT
            "system" -> Typeface.DEFAULT
            else -> {
                val file = File(fontPath)
                if (file.exists()) {
                    try {
                        Typeface.createFromFile(file)
                    } catch (_: Exception) {
                        Typeface.DEFAULT
                    }
                } else {
                    Typeface.DEFAULT
                }
            }
        }
    }

    AndroidView(
        factory = { ctx ->
            HexKeyboardView(ctx).apply {
                this.sharedTypeface = nunitoTypeface
                this.symbolPages = service?.symbolPages ?: emptyMap()
                this.fontPages = service?.fontPages ?: emptyList()
                this.longPressAlternatives = service?.longPressAlternatives ?: emptyMap()
                this.language = currentLocale
                this.layoutType = prefs.getString("keyboard_layout_type", "default") ?: "default"
                service?.mHexKeyboardView = this
                this.drawBackground = !hasBackgroundImage
                listener = object : HexKeyboardView.Listener {
                    override fun onChar(text: String) { viewModel.onCharTyped(text) }
                    override fun onDelete() { viewModel.onDelete() }
                    override fun onEnter() { viewModel.onEnter() }
                    override fun onLongPressSelect(char: String) { viewModel.onCharTyped(char) }
                    override fun onSymbolPageChange(page: String) {}
                    override fun onParallaxChange(x: Float, y: Float) { onParallax(x, y) }
                    override fun onGesture(points: List<PointF>) {
                        service?.handleGesture(points, allKeys)
                    }
                    override fun onKeyClick(key: HexKeyboardView.Key) {
                        when(key.type) {
                            HexKeyboardView.KeyType.EMOJI -> viewModel.setCurrentView("emoji")
                            HexKeyboardView.KeyType.CLIPBOARD -> viewModel.setCurrentView("clipboard")
                            HexKeyboardView.KeyType.FUNCTIONS -> viewModel.setCurrentView("functions")
                            HexKeyboardView.KeyType.TOGGLE -> service?.symbolsTypedCount = 0
                            else -> {}
                        }
                    }
                }
            }
        },
        update = { view ->
            view.sharedTypeface = nunitoTypeface
            view.keyboardTheme = theme
            view.language = currentLocale
            view.drawBackground = !hasBackgroundImage
            view.visibility = if (keyboardVisible) View.VISIBLE else View.INVISIBLE

            view.fontPages = service?.fontPages ?: emptyList()

            view.listener = object : HexKeyboardView.Listener {
                override fun onChar(text: String) { viewModel.onCharTyped(text) }
                override fun onDelete() { viewModel.onDelete() }
                override fun onEnter() { viewModel.onEnter() }
                override fun onLongPressSelect(char: String) { viewModel.onCharTyped(char) }
                override fun onSymbolPageChange(page: String) {}
                override fun onParallaxChange(x: Float, y: Float) { onParallax(x, y) }
                override fun onGesture(points: List<PointF>) {
                    service?.handleGesture(points, view.allKeys)
                }
                override fun onKeyClick(key: HexKeyboardView.Key) {
                    when(key.type) {
                        HexKeyboardView.KeyType.EMOJI -> viewModel.setCurrentView("emoji")
                        HexKeyboardView.KeyType.CLIPBOARD -> viewModel.setCurrentView("clipboard")
                        HexKeyboardView.KeyType.FUNCTIONS -> viewModel.setCurrentView("functions")
                        HexKeyboardView.KeyType.TOGGLE -> service?.symbolsTypedCount = 0
                        else -> {}
                    }
                }
            }
        },
        modifier = Modifier.fillMaxWidth().then(if (isEmojiSearchActive) Modifier.padding(top = 180.dp) else Modifier)
    )
}

@Composable
fun BoxScope.PanelsSection(viewModel: KeyboardViewModel, theme: KeyboardTheme) {
    val currentView by viewModel.currentView.collectAsState()
    val clipboardHistory by viewModel.clipboardHistory.collectAsState()
    val isEmojiSearchActive by viewModel.isEmojiSearchActive.collectAsState()
    val context = LocalContext.current
    val service = context as? HexKeyboardService

    if (currentView != "keyboard") {
        val panelModifier = if (isEmojiSearchActive) Modifier.fillMaxWidth().height(180.dp) else Modifier.matchParentSize()
        Box(modifier = panelModifier) {
            when (currentView) {
                "emoji" -> EmojiPanel(onEmojiSelected = { char -> viewModel.onEmojiSelected(char) }, onBack = { viewModel.setCurrentView("keyboard") }, theme = theme, viewModel = viewModel)
                "clipboard" -> ClipboardPanel(
                    history = clipboardHistory,
                    onItemSelected = { item -> viewModel.onClipboardItemClick(item) },
                    onDelete = { item -> 
                        ClipboardHistoryManager.deleteItem(context, item)
                        service?.refreshClipboardHistory() 
                    },
                    onTogglePin = { item -> 
                        ClipboardHistoryManager.togglePin(context, item)
                        service?.refreshClipboardHistory() 
                    },
                    onBack = { viewModel.setCurrentView("keyboard") },
                    theme = theme
                )
                "functions" -> FunctionsPanel(
                    onBack = { viewModel.setCurrentView("keyboard") },
                    onSettings = { viewModel.onSettingsClick() },
                    theme = theme,
                    viewModel = viewModel
                )
            }
        }
    }
}
