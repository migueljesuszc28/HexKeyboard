package com.example.hexkeyboard.ui.keyboard

import android.graphics.PointF
import android.graphics.Typeface
import android.text.InputType
import android.view.View
import android.view.inputmethod.EditorInfo
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.datastore.preferences.core.stringPreferencesKey
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
import com.example.hexkeyboard.ui.keyboard.components.HexLayoutEngine
import com.example.hexkeyboard.ui.keyboard.components.HexKeyboardView
import com.example.hexkeyboard.viewmodel.KeyboardViewModel
import com.example.hexkeyboard.data.repository.ThemeUtils
import dev.chrisbanes.haze.HazeState
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
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
    val dataStore = ThemeUtils.getDataStore(context)
    
    val fontPathFlow = remember { dataStore.data.map { it[stringPreferencesKey("custom_font_path")] ?: "system" } }
    val fontPath by fontPathFlow.collectAsState("system")
    
    val layoutTypeFlow = remember { dataStore.data.map { it[ThemeUtils.KEYBOARD_LAYOUT_TYPE] ?: "default" } }
    val layoutType by layoutTypeFlow.collectAsState("default")

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
                this.layoutType = layoutType
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
                    override fun onKeyClick(key: HexLayoutEngine.Key) {
                        when(key.type) {
                            HexLayoutEngine.KeyType.EMOJI -> viewModel.setCurrentView("emoji")
                            HexLayoutEngine.KeyType.CLIPBOARD -> viewModel.setCurrentView("clipboard")
                            HexLayoutEngine.KeyType.FUNCTIONS -> viewModel.setCurrentView("functions")
                            HexLayoutEngine.KeyType.TOGGLE -> service?.symbolsTypedCount = 0
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
            view.layoutType = layoutType
            view.drawBackground = !hasBackgroundImage
            view.visibility = if (keyboardVisible) View.VISIBLE else View.INVISIBLE

            view.fontPages = service?.fontPages ?: emptyList()

            service?.currentInputEditorInfo?.let { info ->
                val inputType = info.inputType
                val classMask = inputType and InputType.TYPE_MASK_CLASS
                view.isMultiLine = (classMask == InputType.TYPE_CLASS_TEXT) &&
                        (inputType and InputType.TYPE_TEXT_FLAG_MULTI_LINE != 0)
                val action = info.imeOptions and EditorInfo.IME_MASK_ACTION
                view.setImeAction(action)
            }

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
                override fun onKeyClick(key: HexLayoutEngine.Key) {
                    when(key.type) {
                        HexLayoutEngine.KeyType.EMOJI -> viewModel.setCurrentView("emoji")
                        HexLayoutEngine.KeyType.CLIPBOARD -> viewModel.setCurrentView("clipboard")
                        HexLayoutEngine.KeyType.FUNCTIONS -> viewModel.setCurrentView("functions")
                        HexLayoutEngine.KeyType.TOGGLE -> service?.symbolsTypedCount = 0
                        else -> {}
                    }
                }
            }
        },
        modifier = Modifier.fillMaxWidth().then(if (isEmojiSearchActive) Modifier.padding(top = 180.dp) else Modifier)
    )
}

@Composable
fun BoxScope.PanelsSection(viewModel: KeyboardViewModel, theme: KeyboardTheme, hazeState: HazeState) {
    val currentView by viewModel.currentView.collectAsState()
    val clipboardHistory by viewModel.clipboardHistory.collectAsState()
    val isEmojiSearchActive by viewModel.isEmojiSearchActive.collectAsState()
    val context = LocalContext.current
    val service = context as? HexKeyboardService

    if (currentView != "keyboard") {
        val scope = rememberCoroutineScope()
        val panelModifier = if (isEmojiSearchActive) Modifier.fillMaxWidth().height(180.dp) else Modifier.matchParentSize()
        Box(modifier = panelModifier) {
            when (currentView) {
                "emoji" -> EmojiPanel(onEmojiSelected = { char -> viewModel.onEmojiSelected(char) }, onBack = { viewModel.setCurrentView("keyboard") }, theme = theme, viewModel = viewModel, hazeState = hazeState)
                "clipboard" -> ClipboardPanel(
                    history = clipboardHistory,
                    onItemSelected = { item -> viewModel.onClipboardItemClick(item) },
                    onDelete = { item -> 
                        scope.launch {
                            ClipboardHistoryManager.deleteItem(context, item)
                            service?.refreshClipboardHistory() 
                        }
                    },
                    onTogglePin = { item -> 
                        scope.launch {
                            ClipboardHistoryManager.togglePin(context, item)
                            service?.refreshClipboardHistory() 
                        }
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
