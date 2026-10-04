package com.example.hexkeyboard.ui.keyboard

import android.graphics.PointF
import android.graphics.Typeface
import android.text.InputType
import android.view.View
import android.view.inputmethod.EditorInfo
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
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
import com.example.hexkeyboard.logic.managers.FeedbackManager
import com.example.hexkeyboard.ui.keyboard.panels.CredentialsPanel
import com.example.hexkeyboard.ui.keyboard.panels.LanguagePanel
import com.example.hexkeyboard.service.HexKeyboardService
import com.example.hexkeyboard.ui.keyboard.panels.ClipboardPanel
import com.example.hexkeyboard.ui.keyboard.panels.EmojiPanel
import com.example.hexkeyboard.ui.keyboard.panels.FunctionsPanel
import com.example.hexkeyboard.ui.keyboard.components.HexLayoutEngine
import com.example.hexkeyboard.ui.keyboard.components.HexKeyboardView
import com.example.hexkeyboard.viewmodel.KeyboardViewModel
import com.example.hexkeyboard.data.repository.ThemeUtils
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
    val isCredentialsSearchActive by viewModel.isCredentialsSearchActive.collectAsState()
    val isSearchActive = isEmojiSearchActive || isCredentialsSearchActive
    val currentLocale by viewModel.currentLocale.collectAsState()
    val keyboardVisible = currentView == "keyboard" || isSearchActive

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
                try {
                    val file = File(fontPath)
                    if (file.exists() && file.length() > 0) {
                        Typeface.createFromFile(file) ?: Typeface.DEFAULT
                    } else {
                        Typeface.DEFAULT
                    }
                } catch (_: Exception) {
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
                    override fun onLiveGesture(points: List<PointF>) {
                        service?.handleLiveGesture(points, allKeys)
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
                val effectiveAction = service.getEffectiveImeAction(info)
                val inputType = info.inputType
                val classMask = inputType and InputType.TYPE_MASK_CLASS
                
                val isTextClass = classMask == InputType.TYPE_CLASS_TEXT
                val hasMultiLineFlag = (inputType and InputType.TYPE_TEXT_FLAG_MULTI_LINE) != 0
                val isMultiLine = isTextClass && hasMultiLineFlag && (effectiveAction == EditorInfo.IME_ACTION_NONE || effectiveAction == EditorInfo.IME_ACTION_UNSPECIFIED)
                
                view.setImeAction(effectiveAction)
                view.isMultiLine = isMultiLine
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
        modifier = Modifier.fillMaxWidth().then(if (isSearchActive) Modifier.padding(top = 180.dp) else Modifier)
    )
}

@Composable
fun BoxScope.PanelsSection(viewModel: KeyboardViewModel, theme: KeyboardTheme, bottomOffset: Int = 0, navBarBottomDp: Int = 0) {
    val currentView by viewModel.currentView.collectAsState()
    val clipboardHistory by viewModel.clipboardHistory.collectAsState()
    val isEmojiSearchActive by viewModel.isEmojiSearchActive.collectAsState()
    val isCredentialsSearchActive by viewModel.isCredentialsSearchActive.collectAsState()
    val isSearchActive = isEmojiSearchActive || isCredentialsSearchActive
    val context = LocalContext.current
    val service = context as? HexKeyboardService

    val scope = rememberCoroutineScope()
    val panelModifier = if (isSearchActive) Modifier.fillMaxWidth().height(180.dp) else Modifier.matchParentSize()

    AnimatedContent(
        targetState = currentView,
        transitionSpec = {
            if (targetState != "keyboard") {
                (slideInVertically(
                    initialOffsetY = { (it * 0.15f).toInt() },
                    animationSpec = tween(220)
                ) + fadeIn(animationSpec = tween(220))).togetherWith(
                    fadeOut(animationSpec = tween(180)) + slideOutVertically(
                        targetOffsetY = { (it * 0.15f).toInt() },
                        animationSpec = tween(180)
                    )
                )
            } else {
                (fadeIn(animationSpec = tween(200))).togetherWith(
                    slideOutVertically(
                        targetOffsetY = { (it * 0.15f).toInt() },
                        animationSpec = tween(180)
                    ) + fadeOut(animationSpec = tween(180))
                )
            }
        },
        label = "panel_animation",
        modifier = panelModifier
    ) { view ->
        when (view) {
            "emoji" -> EmojiPanel(
                onEmojiSelected = { char -> viewModel.onEmojiSelected(char) }, 
                onBack = { viewModel.setCurrentView("keyboard") }, 
                theme = theme, 
                viewModel = viewModel, 
                bottomOffset = bottomOffset,
                navBarBottomDp = navBarBottomDp
            )
            "clipboard" -> ClipboardPanel(
                history = clipboardHistory,
                onItemSelected = { item -> viewModel.onClipboardItemClick(item) },
                onDelete = { item -> 
                    if (service != null) {
                        service.deleteClipboardItem(item)
                    } else {
                        scope.launch {
                            ClipboardHistoryManager.deleteItem(context, item)
                        }
                    }
                },
                onTogglePin = { item -> 
                    scope.launch {
                        ClipboardHistoryManager.togglePin(context, item)
                        service?.refreshClipboardHistory(triggerSuggestionsUpdate = true) 
                    }
                },
                onLongPress = { item -> viewModel.setClipboardItemWithOptions(item) },
                onBack = { viewModel.setCurrentView("keyboard") },
                theme = theme,
                viewModel = viewModel,
                bottomOffset = bottomOffset,
                navBarBottomDp = navBarBottomDp
            )
            "functions" -> FunctionsPanel(
                onBack = { viewModel.setCurrentView("keyboard") },
                onSettings = { viewModel.onSettingsClick() },
                theme = theme,
                viewModel = viewModel
            )
            "credentials" -> {
                val credentials by viewModel.credentials.collectAsState()
                LaunchedEffect(Unit) {
                    viewModel.loadCredentials(context)
                }
                CredentialsPanel(
                    credentials = credentials,
                    onInsertText = { text ->
                        FeedbackManager.triggerFeedback(context, FeedbackManager.HapticType.KEY_CLICK)
                        viewModel.onActionRequested?.invoke(KeyboardViewModel.Action.InsertText(text))
                        viewModel.setCurrentView("keyboard")
                    },
                    onBack = { viewModel.setCurrentView("keyboard") },
                    theme = theme,
                    viewModel = viewModel,
                    bottomOffset = bottomOffset,
                    navBarBottomDp = navBarBottomDp
                )
            }
            "languages" -> LanguagePanel(
                onBack = { viewModel.setCurrentView("keyboard") },
                theme = theme,
                viewModel = viewModel,
                bottomOffset = bottomOffset,
                navBarBottomDp = navBarBottomDp
            )
            else -> Box(modifier = Modifier.fillMaxSize())
        }
    }
}
