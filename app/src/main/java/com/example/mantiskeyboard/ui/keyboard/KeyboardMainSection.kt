package com.example.mantiskeyboard.ui.keyboard

import android.graphics.PointF
import android.graphics.Typeface
import android.text.InputType
import android.view.View
import android.view.inputmethod.EditorInfo
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import com.example.mantiskeyboard.R
import com.example.mantiskeyboard.data.repository.KeyboardTheme
import com.example.mantiskeyboard.logic.managers.FeedbackManager
import com.example.mantiskeyboard.ui.keyboard.panels.CredentialsPanel
import com.example.mantiskeyboard.ui.keyboard.panels.LanguagePanel
import com.example.mantiskeyboard.service.KeyboardService
import com.example.mantiskeyboard.ui.keyboard.panels.ClipboardPanel
import com.example.mantiskeyboard.ui.keyboard.panels.EmojiPanel
import com.example.mantiskeyboard.ui.keyboard.panels.FunctionsPanel
import com.example.mantiskeyboard.ui.keyboard.components.KeyboardLayoutEngine
import com.example.mantiskeyboard.ui.keyboard.components.KeyboardView
import com.example.mantiskeyboard.viewmodel.KeyboardViewModel
import com.example.mantiskeyboard.data.repository.ThemeUtils
import kotlinx.coroutines.flow.map
import java.io.File

@Composable
fun KeyboardMainSection(
    viewModel: KeyboardViewModel,
    theme: KeyboardTheme,
    hasBackgroundImage: Boolean
) {
    val currentView by viewModel.currentView.collectAsState()
    val isEmojiSearchActive by viewModel.isEmojiSearchActive.collectAsState()
    val isCredentialsSearchActive by viewModel.isCredentialsSearchActive.collectAsState()
    val isSearchActive = isEmojiSearchActive || isCredentialsSearchActive
    val currentLocale by viewModel.currentLocale.collectAsState()
    val isSplitMode by viewModel.isSplitMode.collectAsState()
    val keyboardVisible = currentView == "keyboard" || isSearchActive

    val context = LocalContext.current
    val service = context as? KeyboardService
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
            KeyboardView(ctx).apply {
                this.sharedTypeface = nunitoTypeface
                this.symbolPages = service?.symbolPages ?: emptyMap()
                this.fontPages = service?.fontPages ?: emptyList()
                this.longPressAlternatives = service?.longPressAlternatives ?: emptyMap()
                this.language = currentLocale
                this.layoutType = layoutType
                this.isSplitMode = isSplitMode
                service?.mKeyboardView = this
                this.drawBackground = !hasBackgroundImage
                listener = object : KeyboardView.Listener {
                    override fun onChar(text: String) { viewModel.onCharTyped(text) }
                    override fun onCharWithPoint(text: String, x: Float, y: Float) {
                        service?.recordTouchPoint(text, x, y)
                    }
                    override fun onDelete() { viewModel.onDelete() }
                    override fun onEnter() { viewModel.onEnter() }
                    override fun onLongPressSelect(char: String) { viewModel.onCharTyped(char) }
                    override fun onSymbolPageChange(page: String) {}
                    override fun onLayoutUpdated(keyCenters: Map<Char, PointF>) {
                        service?.updateKeyCenters(keyCenters)
                    }
                    override fun onGesture(points: List<PointF>) {
                        service?.handleGesture(points, allKeys)
                    }
                    override fun onLiveGesture(points: List<PointF>) {
                        service?.handleLiveGesture(points, allKeys)
                    }
                    override fun onKeyClick(key: KeyboardLayoutEngine.Key) {
                        when(key.type) {
                            KeyboardLayoutEngine.KeyType.EMOJI -> viewModel.setCurrentView("emoji")
                            KeyboardLayoutEngine.KeyType.CLIPBOARD -> viewModel.setCurrentView("clipboard")
                            KeyboardLayoutEngine.KeyType.FUNCTIONS -> viewModel.setCurrentView("functions")
                            KeyboardLayoutEngine.KeyType.TOGGLE -> service?.symbolsTypedCount = 0
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
            view.isSplitMode = isSplitMode
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

            view.listener = object : KeyboardView.Listener {
                override fun onChar(text: String) { viewModel.onCharTyped(text) }
                override fun onCharWithPoint(text: String, x: Float, y: Float) {
                    service?.recordTouchPoint(text, x, y)
                }
                override fun onDelete() { viewModel.onDelete() }
                override fun onEnter() { viewModel.onEnter() }
                override fun onLongPressSelect(char: String) { viewModel.onCharTyped(char) }
                override fun onSymbolPageChange(page: String) {}
                override fun onLayoutUpdated(keyCenters: Map<Char, PointF>) {
                    service?.updateKeyCenters(keyCenters)
                }
                override fun onGesture(points: List<PointF>) {
                    service?.handleGesture(points, view.allKeys)
                }
                override fun onKeyClick(key: KeyboardLayoutEngine.Key) {
                    when(key.type) {
                        KeyboardLayoutEngine.KeyType.EMOJI -> viewModel.setCurrentView("emoji")
                        KeyboardLayoutEngine.KeyType.CLIPBOARD -> viewModel.setCurrentView("clipboard")
                        KeyboardLayoutEngine.KeyType.FUNCTIONS -> viewModel.setCurrentView("functions")
                        KeyboardLayoutEngine.KeyType.TOGGLE -> service?.symbolsTypedCount = 0
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

    val panelModifier = if (isSearchActive) Modifier.fillMaxWidth().height(180.dp) else Modifier.matchParentSize()

    AnimatedContent(
        targetState = currentView,
        transitionSpec = {
            val expressiveFloatSpring = spring<Float>(dampingRatio = 0.78f, stiffness = 450f)
            val expressiveIntOffsetSpring = spring<androidx.compose.ui.unit.IntOffset>(dampingRatio = 0.78f, stiffness = 450f)
            if (targetState != "keyboard") {
                (slideInVertically(
                    initialOffsetY = { (it * 0.15f).toInt() },
                    animationSpec = expressiveIntOffsetSpring
                ) + fadeIn(animationSpec = expressiveFloatSpring)).togetherWith(
                    fadeOut(animationSpec = tween(180)) + slideOutVertically(
                        targetOffsetY = { (it * 0.15f).toInt() },
                        animationSpec = expressiveIntOffsetSpring
                    )
                )
            } else {
                (fadeIn(animationSpec = expressiveFloatSpring)).togetherWith(
                    slideOutVertically(
                        targetOffsetY = { (it * 0.15f).toInt() },
                        animationSpec = expressiveIntOffsetSpring
                    ) + fadeOut(animationSpec = expressiveFloatSpring)
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
                viewModel = viewModel,
                bottomOffset = bottomOffset,
                navBarBottomDp = navBarBottomDp
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
