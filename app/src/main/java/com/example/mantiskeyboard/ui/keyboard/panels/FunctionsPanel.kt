package com.example.mantiskeyboard.ui.keyboard.panels

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Assignment
import androidx.compose.material.icons.automirrored.outlined.CallMerge
import androidx.compose.material.icons.automirrored.outlined.CallSplit
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.OpenWith
import androidx.compose.material.icons.outlined.AspectRatio
import androidx.compose.material.icons.outlined.FitScreen
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import kotlinx.coroutines.flow.map
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.convx.music.ui.component.backdrop.backdrops.layerBackdrop
import com.example.mantiskeyboard.ui.component.backdrop.backdrops.rememberLayerBackdrop
import com.example.mantiskeyboard.R
import com.example.mantiskeyboard.data.repository.KeyboardTheme
import com.example.mantiskeyboard.data.repository.ThemeUtils
import com.example.mantiskeyboard.data.repository.ThemeUtils.getKeyboardString
import com.example.mantiskeyboard.logic.managers.FeedbackManager
import com.example.mantiskeyboard.ui.component.GlassEffectConfig
import com.example.mantiskeyboard.ui.component.LocalAppBackdrop
import com.example.mantiskeyboard.ui.component.LocalGlassEffectConfig
import com.example.mantiskeyboard.ui.keyboard.components.PanelHeader
import com.example.mantiskeyboard.ui.keyboard.components.bounceClick
import com.example.mantiskeyboard.ui.keyboard.components.rememberKeyBorderStroke
import com.example.mantiskeyboard.ui.keyboard.components.rememberMorphingShape
import com.example.mantiskeyboard.ui.settings.themes.ThemeSettingsActivity
import com.example.mantiskeyboard.viewmodel.KeyboardViewModel

@Composable
fun FunctionsPanel(
    onBack: () -> Unit,
    onSettings: () -> Unit,
    theme: KeyboardTheme,
    viewModel: KeyboardViewModel? = null,
    bottomOffset: Int = 0,
    navBarBottomDp: Int = 0,
) {
    val context = LocalContext.current
    val currentLocaleFlow = remember(viewModel) { viewModel?.currentLocale ?: kotlinx.coroutines.flow.MutableStateFlow("es") }
    val currentLocale by currentLocaleFlow.collectAsState("es")
    val isEn = currentLocale == "en"

    val isFloatingFlow = remember(viewModel) { viewModel?.isFloatingMode ?: kotlinx.coroutines.flow.MutableStateFlow(false) }
    val isFloatingMode by isFloatingFlow.collectAsState(false)

    val isSplitFlow = remember(viewModel) { viewModel?.isSplitMode ?: kotlinx.coroutines.flow.MutableStateFlow(false) }
    val isSplitMode by isSplitFlow.collectAsState(false)

    val dataStore = remember { ThemeUtils.getDataStore(context) }
    val layoutTypeFlow = remember { dataStore.data.map { it[ThemeUtils.KEYBOARD_LAYOUT_TYPE] ?: "default" } }
    val layoutType by layoutTypeFlow.collectAsState("default")
    val isQwerty = layoutType == "qwerty"

    val backdrop = rememberLayerBackdrop()
    val savedGlassConfigFlow = remember { ThemeUtils.getGlassEffectConfigFlow(context) }
    val savedGlassConfig by savedGlassConfigFlow.collectAsState(initial = GlassEffectConfig())
    val glassConfig = remember(theme, savedGlassConfig) {
        val glassTint = if (theme.id == "m3_dynamic") {
            Color(theme.backgroundColor)
        } else {
            Color(theme.keyBackgroundColor)
        }
        savedGlassConfig.copy(
            surfaceTintColor = glassTint,
            textColor = Color(theme.keyboardIconTint),
        )
    }

    val baseThemeColor = Color(theme.backgroundColor)
    val topShadowMaskBrush = remember(theme.backgroundColor) {
        Brush.verticalGradient(
            colorStops = arrayOf(
                0.0f to baseThemeColor.copy(alpha = 0.15f),
                0.6f to baseThemeColor.copy(alpha = 0.05f),
                1.0f to Color.Transparent
            )
        )
    }
    val bottomShadowMaskBrush = remember(theme.backgroundColor) {
        Brush.verticalGradient(
            colorStops = arrayOf(
                0.0f to Color.Transparent,
                0.4f to baseThemeColor.copy(alpha = 0.05f),
                1.0f to baseThemeColor.copy(alpha = 0.15f)
            )
        )
    }

    val topPadding = 52.dp
    val bottomPadding = (70 + bottomOffset + navBarBottomDp).dp

    CompositionLocalProvider(
        LocalAppBackdrop provides backdrop,
        LocalGlassEffectConfig provides glassConfig
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Transparent)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .layerBackdrop(backdrop)
            ) {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(4),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = 12.dp,
                        end = 12.dp,
                        top = topPadding,
                        bottom = bottomPadding
                    ),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    item {
                        FunctionItem(
                            icon = Icons.Rounded.Settings,
                            label = if (isEn) "Settings" else "Ajustes",
                            onClick = onSettings,
                            theme = theme
                        )
                    }
                    item {
                        FunctionItem(
                            icon = Icons.Outlined.Palette,
                            label = if (isEn) "Themes" else "Temas",
                            onClick = {
                                context.startActivity(Intent(context, ThemeSettingsActivity::class.java).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) })
                            },
                            theme = theme
                        )
                    }
                    item {
                        FunctionItem(
                            icon = Icons.AutoMirrored.Outlined.Assignment,
                            label = if (isEn) "Clipboard" else "Portapapeles",
                            onClick = {
                                viewModel?.setCurrentView("clipboard", context)
                            },
                            theme = theme
                        )
                    }
                    item {
                        FunctionItem(
                            icon = Icons.Default.Key,
                            label = if (isEn) "Passwords" else "Contraseñas",
                            onClick = {
                                viewModel?.setCurrentView("credentials", context)
                            },
                            theme = theme
                        )
                    }
                    item {
                        FunctionItem(
                            icon = Icons.Default.Language,
                            label = if (isEn) "Language" else "Idioma",
                            onClick = {
                                viewModel?.setCurrentView("languages", context)
                            },
                            theme = theme
                        )
                    }
                    item {
                        FunctionItem(
                            icon = if (isQwerty) painterResource(R.drawable.ic_hexagon) else painterResource(R.drawable.ic_keyboard),
                            label = if (isQwerty) {
                                context.getKeyboardString(R.string.fn_layout_hexagonal, currentLocale)
                            } else {
                                context.getKeyboardString(R.string.fn_layout_qwerty, currentLocale)
                            },
                            onClick = {
                                viewModel?.toggleLayoutType(context)
                            },
                            theme = theme
                        )
                    }
                    item {
                        FunctionItem(
                            icon = if (isFloatingMode) Icons.Outlined.FitScreen else Icons.Outlined.AspectRatio,
                            label = if (isFloatingMode) {
                                context.getKeyboardString(R.string.floating_keyboard_dock, currentLocale)
                            } else {
                                context.getKeyboardString(R.string.floating_keyboard_title, currentLocale)
                            },
                            onClick = {
                                viewModel?.toggleFloatingMode(context)
                            },
                            theme = theme
                        )
                    }
                    item {
                        FunctionItem(
                            icon = if (isSplitMode) Icons.AutoMirrored.Outlined.CallMerge else Icons.AutoMirrored.Outlined.CallSplit,
                            label = if (isSplitMode) {
                                context.getKeyboardString(R.string.split_keyboard_unsplit, currentLocale)
                            } else {
                                context.getKeyboardString(R.string.fn_split_keyboard, currentLocale)
                            },
                            onClick = {
                                viewModel?.toggleSplitMode(context)
                            },
                            theme = theme
                        )
                    }
                    item {
                        FunctionItem(
                            icon = Icons.Default.OpenWith,
                            label = context.getKeyboardString(R.string.fn_resize, currentLocale),
                            onClick = {
                                viewModel?.startResizeMode(context)
                            },
                            theme = theme
                        )
                    }
                }
            }

            val totalBottomHazeHeight = (bottomOffset + navBarBottomDp + 16).dp
            if (totalBottomHazeHeight > 0.dp) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(totalBottomHazeHeight)
                        .background(bottomShadowMaskBrush)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { /* Absorbe toques */ }
                )
            }

            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .height(58.dp)
                    .background(topShadowMaskBrush)
            )

            PanelHeader(
                title = context.getKeyboardString(R.string.panel_title_functions, currentLocale),
                theme = theme,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(start = 8.dp, end = 8.dp, top = 4.dp),
                glassConfig = glassConfig,
                onBack = onBack
            )
        }
    }
}

@Composable
fun FunctionItem(
    icon: Any, 
    label: String, 
    onClick: () -> Unit, 
    onLongClick: (() -> Unit)? = null,
    theme: KeyboardTheme
) {
    val context = LocalContext.current
    val interactionSource = remember { MutableInteractionSource() }
    val shape = rememberMorphingShape(
        interactionSource = interactionSource,
        restCorner = 18.dp,
        pressedCorner = 10.dp
    )
    val bgColor = if (theme.keysOpacity >= 0.95f) Color(theme.keyBackgroundColor).copy(alpha = 1.0f) else Color(theme.keyBackgroundColor).copy(alpha = theme.keysOpacity)
    val iconColor = Color(theme.keyboardIconTint)
    val textColor = Color(theme.keyTextColor)

    val borderStroke = rememberKeyBorderStroke(theme)

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth()
    ) {
        Surface(
            shape = shape,
            color = bgColor,
            border = borderStroke,
            modifier = Modifier
                .size(56.dp)
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = {
                        FeedbackManager.triggerFeedback(context, FeedbackManager.HapticType.KEY_CLICK)
                        onClick()
                    }
                )
        ) {
            Box(contentAlignment = Alignment.Center) {
                when (icon) {
                    is ImageVector -> Icon(icon, contentDescription = null, tint = iconColor)
                    is Painter -> Icon(icon, contentDescription = null, tint = iconColor, modifier = Modifier.size(24.dp))
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = textColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
    }
}
