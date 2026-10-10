package com.example.hexkeyboard.ui.keyboard.panels

import android.content.Intent
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Language
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.convx.music.ui.component.backdrop.backdrops.layerBackdrop
import com.convx.music.ui.component.backdrop.backdrops.rememberLayerBackdrop
import com.example.hexkeyboard.R
import com.example.hexkeyboard.data.repository.KeyboardTheme
import com.example.hexkeyboard.data.repository.ThemeUtils
import com.example.hexkeyboard.data.repository.ThemeUtils.getKeyboardString
import com.example.hexkeyboard.logic.managers.FeedbackManager
import com.example.hexkeyboard.ui.component.GlassEffectConfig
import com.example.hexkeyboard.ui.component.LocalAppBackdrop
import com.example.hexkeyboard.ui.component.LocalGlassEffectConfig
import com.example.hexkeyboard.ui.keyboard.components.PanelHeader
import com.example.hexkeyboard.ui.keyboard.components.bounceClick
import com.example.hexkeyboard.ui.keyboard.components.rememberKeyBorderStroke
import com.example.hexkeyboard.ui.settings.themes.ThemeSettingsActivity
import com.example.hexkeyboard.viewmodel.KeyboardViewModel

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
                0.0f to baseThemeColor.copy(alpha = 0.40f),
                0.6f to baseThemeColor.copy(alpha = 0.15f),
                1.0f to Color.Transparent
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
                }
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
    val bgColor = if (theme.keysOpacity >= 0.95f) Color(theme.keyBackgroundColor).copy(alpha = 1.0f) else Color(theme.keyBackgroundColor).copy(alpha = theme.keysOpacity)
    val iconColor = Color(theme.keyboardIconTint)
    val textColor = Color(theme.keyTextColor)

    val borderStroke = rememberKeyBorderStroke(theme)

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxWidth()
            .bounceClick(
                onClick = {
                    FeedbackManager.triggerFeedback(context, FeedbackManager.HapticType.KEY_CLICK)
                    onClick()
                },
                onLongClick = onLongClick
            )
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = bgColor,
            border = borderStroke,
            modifier = Modifier.size(56.dp)
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
