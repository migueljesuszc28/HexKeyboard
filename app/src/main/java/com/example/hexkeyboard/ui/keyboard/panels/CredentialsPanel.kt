package com.example.hexkeyboard.ui.keyboard.panels

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.convx.music.ui.component.backdrop.backdrops.layerBackdrop
import com.convx.music.ui.component.backdrop.backdrops.rememberLayerBackdrop
import com.example.hexkeyboard.data.model.CredentialItem
import com.example.hexkeyboard.data.repository.KeyboardTheme
import com.example.hexkeyboard.data.repository.ThemeUtils
import com.example.hexkeyboard.logic.managers.FeedbackManager
import com.example.hexkeyboard.ui.component.GlassEffectConfig
import com.example.hexkeyboard.ui.component.LocalAppBackdrop
import com.example.hexkeyboard.ui.component.LocalGlassEffectConfig
import com.example.hexkeyboard.ui.keyboard.components.CredentialsSearchBar
import com.example.hexkeyboard.viewmodel.KeyboardViewModel
import kotlinx.coroutines.flow.MutableStateFlow

@Composable
fun CredentialsPanel(
    credentials: List<CredentialItem>,
    onInsertText: (String) -> Unit,
    onBack: () -> Unit = {},
    theme: KeyboardTheme,
    viewModel: KeyboardViewModel? = null,
    bottomOffset: Int = 0,
    navBarBottomDp: Int = 0
) {
    val context = LocalContext.current
    val searchQuery by (viewModel?.credentialsSearchQuery ?: MutableStateFlow("")).collectAsState()
    val isCredentialsSearchActive by (viewModel?.isCredentialsSearchActive ?: MutableStateFlow(false)).collectAsState()

    val filteredCredentials = remember(credentials, searchQuery) {
        if (searchQuery.isBlank()) {
            credentials
        } else {
            credentials.filter { item ->
                item.title.contains(searchQuery, ignoreCase = true) ||
                item.username.contains(searchQuery, ignoreCase = true)
            }
        }
    }

    val backdrop = rememberLayerBackdrop()
    val savedGlassConfigFlow = remember { ThemeUtils.getGlassEffectConfigFlow(context) }
    val savedGlassConfig by savedGlassConfigFlow.collectAsState(initial = GlassEffectConfig())
    val glassConfig = remember(theme, savedGlassConfig) {
        savedGlassConfig.copy(
            surfaceTintColor = Color(theme.keyBackgroundColor),
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

    CompositionLocalProvider(
        LocalAppBackdrop provides backdrop,
        LocalGlassEffectConfig provides glassConfig
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Transparent)
        ) {
            // Capa del Backdrop registrada para capturar y difuminar dinámicamente las tarjetas al hacer scroll
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .layerBackdrop(backdrop)
            ) {
                if (filteredCredentials.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(top = 52.dp, bottom = (70 + bottomOffset + navBarBottomDp).dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (searchQuery.isNotEmpty()) "No se encontraron contraseñas." else "No hay credenciales. Añádelas desde Ajustes.",
                            color = Color(theme.keyTextColor).copy(alpha = 0.7f),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                } else {
                    val topPadding = if (isCredentialsSearchActive) 8.dp else 52.dp
                    val bottomPadding = if (isCredentialsSearchActive) 8.dp else (70 + bottomOffset + navBarBottomDp).dp

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
                        items(filteredCredentials, key = { it.id }) { item ->
                            CredentialKeyboardCard(item = item, theme = theme, onInsert = onInsertText)
                        }
                    }
                }
            }

            if (!isCredentialsSearchActive) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .height(58.dp)
                        .background(topShadowMaskBrush)
                )
            }

            // Encabezado flotante con la barra de búsqueda Liquid Glass
            // Difumina dinámicamente las tarjetas de credenciales que se desplazan por debajo al hacer scroll
            if (viewModel != null && !isCredentialsSearchActive) {
                CredentialsSearchBar(
                    viewModel = viewModel,
                    theme = theme,
                    query = searchQuery,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(start = 8.dp, end = 8.dp, top = 4.dp),
                    glassConfig = glassConfig
                )
            }
        }
    }
}

@Composable
fun CredentialKeyboardCard(
    item: CredentialItem,
    theme: KeyboardTheme,
    onInsert: (String) -> Unit
) {
    val context = LocalContext.current
    var showPassword by remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color(theme.keyBackgroundColor),
        shape = RoundedCornerShape(12.dp),
        tonalElevation = if (theme.id == "glass") 0.dp else 2.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp)
        ) {
            Text(
                text = item.title,
                color = Color(theme.keyTextColor),
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp
            )
            Spacer(Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Botón Usuario / Correo
                Button(
                    onClick = {
                        FeedbackManager.triggerFeedback(context, FeedbackManager.HapticType.KEY_CLICK)
                        onInsert(item.username)
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(32.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(theme.keyTextColor).copy(alpha = 0.1f),
                        contentColor = Color(theme.keyTextColor)
                    ),
                    contentPadding = PaddingValues(horizontal = 8.dp)
                ) {
                    Icon(Icons.Default.Person, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(text = item.username, fontSize = 12.sp, maxLines = 1)
                }

                // Botón Contraseña
                Button(
                    onClick = {
                        FeedbackManager.triggerFeedback(context, FeedbackManager.HapticType.KEY_CLICK)
                        onInsert(item.password)
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(32.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(theme.keyTextColor).copy(alpha = 0.15f),
                        contentColor = Color(theme.keyTextColor)
                    ),
                    contentPadding = PaddingValues(horizontal = 8.dp)
                ) {
                    Icon(Icons.Default.Key, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(text = if (showPassword) item.password else "••••••••", fontSize = 12.sp, maxLines = 1)
                }
            }
        }
    }
}
