package com.example.hexkeyboard.ui.keyboard

import android.content.Intent
import android.content.SharedPreferences
import android.graphics.PointF
import android.graphics.Typeface
import android.view.View
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
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
import com.example.hexkeyboard.ui.settings.PermissionActivity
import com.example.hexkeyboard.ui.settings.SettingsActivity
import java.io.File

@Composable
fun KeyboardMainSection(
    service: HexKeyboardService,
    theme: KeyboardTheme,
    hasBackgroundImage: Boolean,
    onParallax: (Float, Float) -> Unit
) {
    val currentView by service.currentView.collectAsState()
    val isEmojiSearchActive by service.isEmojiSearchActive.collectAsState()
    val currentLocale by service.currentLocale.collectAsState()
    val keyboardVisible = currentView == "keyboard" || isEmojiSearchActive

    val context = service // El servicio es un Context
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
                this.symbolPages = service.symbolPages
                this.fontPages = service.fontPages
                this.longPressAlternatives = service.longPressAlternatives
                this.language = currentLocale
                this.layoutType = prefs.getString("keyboard_layout_type", "default") ?: "default"
                service.mHexKeyboardView = this
                this.drawBackground = !hasBackgroundImage
                listener = object : HexKeyboardView.Listener {
                    override fun onChar(text: String) {
                        service.handleChar(text)
                    }
                    override fun onDelete() {
                        service.handleDelete()
                    }
                    override fun onEnter() {
                        service.handleEnter()
                    }
                    override fun onLongPressSelect(char: String) {
                        service.handleChar(char)
                    }
                    override fun onSymbolPageChange(page: String) {}
                    override fun onParallaxChange(x: Float, y: Float) { onParallax(x, y) }
                    override fun onGesture(points: List<PointF>) {
                        service.handleGesture(points, allKeys)
                    }
                    override fun onKeyClick(key: HexKeyboardView.Key) {
                        when(key.type) {
                            HexKeyboardView.KeyType.EMOJI -> service.setCurrentView("emoji")
                            HexKeyboardView.KeyType.CLIPBOARD -> service.setCurrentView("clipboard")
                            HexKeyboardView.KeyType.FUNCTIONS -> service.setCurrentView("functions")
                            HexKeyboardView.KeyType.TOGGLE -> service.symbolsTypedCount = 0
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

            view.fontPages = service.fontPages

            view.listener = object : HexKeyboardView.Listener {
                override fun onChar(text: String) { service.handleChar(text) }
                override fun onDelete() { service.handleDelete() }
                override fun onEnter() { service.handleEnter() }
                override fun onLongPressSelect(char: String) { service.handleChar(char) }
                override fun onSymbolPageChange(page: String) {}
                override fun onParallaxChange(x: Float, y: Float) { onParallax(x, y) }
                override fun onGesture(points: List<PointF>) {
                    service.handleGesture(points, view.allKeys)
                }
                override fun onKeyClick(key: HexKeyboardView.Key) {
                    when(key.type) {
                        HexKeyboardView.KeyType.EMOJI -> service.setCurrentView("emoji")
                        HexKeyboardView.KeyType.CLIPBOARD -> service.setCurrentView("clipboard")
                        HexKeyboardView.KeyType.FUNCTIONS -> service.setCurrentView("functions")
                        HexKeyboardView.KeyType.TOGGLE -> service.symbolsTypedCount = 0
                        else -> {}
                    }
                }
            }
        },
        modifier = Modifier.fillMaxWidth().then(if (isEmojiSearchActive) Modifier.padding(top = 180.dp) else Modifier)
    )
}

@Composable
fun BoxScope.PanelsSection(service: HexKeyboardService, theme: KeyboardTheme) {
    val currentView by service.currentView.collectAsState()
    val clipboardHistory by service.clipboardHistory.collectAsState()
    val isEmojiSearchActive by service.isEmojiSearchActive.collectAsState()
    val context = service // El servicio es un Context

    if (currentView != "keyboard") {
        val panelModifier = if (isEmojiSearchActive) Modifier.fillMaxWidth().height(180.dp) else Modifier.matchParentSize()
        Box(modifier = panelModifier) {
            when (currentView) {
                "emoji" -> EmojiPanel(onEmojiSelected = { char -> service.onEmojiSelected(char) }, onBack = { service.setCurrentView("keyboard") }, theme = theme)
                "clipboard" -> ClipboardPanel(
                    history = clipboardHistory,
                    onItemSelected = { item -> service.useClipboardItem(item); service.setCurrentView("keyboard") },
                    onDelete = { item -> ClipboardHistoryManager.deleteItem(context, item); service.refreshClipboardHistory() },
                    onTogglePin = { item -> ClipboardHistoryManager.togglePin(context, item); service.refreshClipboardHistory() },
                    onBack = { service.setCurrentView("keyboard") },
                    theme = theme
                )
                "functions" -> FunctionsPanel(
                    onBack = { service.setCurrentView("keyboard") },
                    onSettings = { context.startActivity(Intent(context, SettingsActivity::class.java).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }) },
                    theme = theme
                )
            }
        }
    }
}
