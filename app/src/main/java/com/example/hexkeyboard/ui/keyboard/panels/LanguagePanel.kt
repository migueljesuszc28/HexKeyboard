package com.example.hexkeyboard.ui.keyboard.panels

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.edit
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
import com.example.hexkeyboard.viewmodel.KeyboardViewModel
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

@Composable
fun LanguagePanel(
    onBack: () -> Unit = {},
    theme: KeyboardTheme,
    viewModel: KeyboardViewModel? = null,
    bottomOffset: Int = 0,
    navBarBottomDp: Int = 0
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val dataStore = remember { ThemeUtils.getDataStore(context) }

    val primaryLangFlow = remember { dataStore.data.map { it[ThemeUtils.KEYBOARD_LANGUAGE] ?: "es" } }
    val primaryLang by primaryLangFlow.collectAsState("es")

    val secLangFlow = remember { dataStore.data.map { it[ThemeUtils.SECONDARY_LANGUAGE] ?: "" } }
    val secLang by secLangFlow.collectAsState("")

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
            textColor = Color(theme.keyboardIconTint)
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

    val borderStroke = rememberKeyBorderStroke(theme)
    val baseColor = Color(theme.keyBackgroundColor)
    val isTransparentBg = baseColor.alpha == 0f || baseColor == Color.Transparent
    val cardColor = if (isTransparentBg) Color.Transparent else baseColor
    val cardElevation = if (isTransparentBg || theme.id == "glass") 0.dp else 2.dp

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
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = 12.dp,
                        end = 12.dp,
                        top = topPadding,
                        bottom = bottomPadding
                    ),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item {
                        Text(
                            text = context.getKeyboardString(R.string.lang_primary, primaryLang),
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(theme.keyTextColor).copy(alpha = 0.7f),
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(start = 4.dp, top = 2.dp, bottom = 2.dp)
                        )
                    }

                    items(listOf("es" to context.getKeyboardString(R.string.lang_spanish, primaryLang), "en" to context.getKeyboardString(R.string.lang_english, primaryLang))) { (code, name) ->
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .bounceClick(
                                    onClick = {
                                        FeedbackManager.triggerFeedback(context, FeedbackManager.HapticType.KEY_CLICK)
                                        scope.launch {
                                            dataStore.edit { prefs ->
                                                prefs[ThemeUtils.KEYBOARD_LANGUAGE] = code
                                            }
                                        }
                                    }
                                ),
                            color = cardColor,
                            shape = RoundedCornerShape(12.dp),
                            border = borderStroke,
                            tonalElevation = cardElevation
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = name,
                                    color = Color(theme.keyTextColor),
                                    fontSize = 14.sp,
                                    fontWeight = if (primaryLang == code) FontWeight.Bold else FontWeight.Normal
                                )
                                RadioButton(
                                    selected = primaryLang == code,
                                    onClick = null,
                                    colors = RadioButtonDefaults.colors(
                                        selectedColor = Color(theme.keyTextColor),
                                        unselectedColor = Color(theme.keyTextColor).copy(alpha = 0.5f)
                                    )
                                )
                            }
                        }
                    }

                    item {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = context.getKeyboardString(R.string.lang_secondary, primaryLang),
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(theme.keyTextColor).copy(alpha = 0.7f),
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(start = 4.dp, top = 2.dp, bottom = 2.dp)
                        )
                    }

                    items(listOf("" to context.getKeyboardString(R.string.lang_disabled, primaryLang), "es" to context.getKeyboardString(R.string.lang_spanish, primaryLang), "en" to context.getKeyboardString(R.string.lang_english, primaryLang))) { (code, name) ->
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .bounceClick(
                                    onClick = {
                                        FeedbackManager.triggerFeedback(context, FeedbackManager.HapticType.KEY_CLICK)
                                        scope.launch {
                                            dataStore.edit { prefs ->
                                                prefs[ThemeUtils.SECONDARY_LANGUAGE] = code
                                                prefs[ThemeUtils.MULTILINGUAL_ENABLED] = code.isNotEmpty()
                                            }
                                        }
                                    }
                                ),
                            color = cardColor,
                            shape = RoundedCornerShape(12.dp),
                            border = borderStroke,
                            tonalElevation = cardElevation
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = name,
                                    color = Color(theme.keyTextColor),
                                    fontSize = 14.sp,
                                    fontWeight = if (secLang == code) FontWeight.Bold else FontWeight.Normal
                                )
                                RadioButton(
                                    selected = secLang == code,
                                    onClick = null,
                                    colors = RadioButtonDefaults.colors(
                                        selectedColor = Color(theme.keyTextColor),
                                        unselectedColor = Color(theme.keyTextColor).copy(alpha = 0.5f)
                                    )
                                )
                            }
                        }
                    }
                }
            }

            // Máscara de Sombra discreta superior para suavizar el scroll debajo de la barra de sugerencias
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .height(58.dp)
                    .background(topShadowMaskBrush)
            )

            // Encabezado flotante con botón Atrás y título "Idioma"
            PanelHeader(
                title = context.getKeyboardString(R.string.panel_title_languages, primaryLang),
                theme = theme,
                glassConfig = glassConfig,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(start = 8.dp, end = 8.dp, top = 4.dp),
                onBack = onBack
            )
        }
    }
}
