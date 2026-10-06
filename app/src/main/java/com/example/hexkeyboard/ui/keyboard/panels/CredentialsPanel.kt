package com.example.hexkeyboard.ui.keyboard.panels

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
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
import com.example.hexkeyboard.R
import com.example.hexkeyboard.data.model.CredentialItem
import com.example.hexkeyboard.data.repository.CredentialsManager
import com.example.hexkeyboard.data.repository.KeyboardTheme
import com.example.hexkeyboard.data.repository.ThemeUtils
import com.example.hexkeyboard.data.repository.ThemeUtils.getKeyboardString
import com.example.hexkeyboard.logic.managers.FeedbackManager
import com.example.hexkeyboard.ui.component.GlassEffectConfig
import com.example.hexkeyboard.ui.component.LocalAppBackdrop
import com.example.hexkeyboard.ui.component.LocalGlassEffectConfig
import com.example.hexkeyboard.ui.keyboard.components.CredentialsSearchBar
import com.example.hexkeyboard.ui.keyboard.components.PanelHeader
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
    val currentLocaleFlow = remember(viewModel) { viewModel?.currentLocale ?: MutableStateFlow("es") }
    val currentLocale by currentLocaleFlow.collectAsState("es")

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

    val requireBiometricKeyboard by CredentialsManager.getRequireBiometricKeyboardFlow(context).collectAsState(initial = false)
    val isKeyboardUnlocked by CredentialsManager.isKeyboardUnlocked.collectAsState()
    val isLocked = requireBiometricKeyboard && !isKeyboardUnlocked

    val listState = rememberLazyListState()

    LaunchedEffect(filteredCredentials, isLocked) {
        if (!isLocked && filteredCredentials.isNotEmpty()) {
            listState.scrollToItem(0)
        }
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
                if (isLocked) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(top = 44.dp, bottom = (bottomOffset + navBarBottomDp + 8).dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp)
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = Color(theme.keyTextColor).copy(alpha = 0.12f),
                                modifier = Modifier.size(42.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        Icons.Default.Lock,
                                        contentDescription = null,
                                        tint = Color(theme.keyTextColor),
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = "Contraseñas bloqueadas",
                                color = Color(theme.keyTextColor),
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = "Requiere autenticación para ver credenciales",
                                color = Color(theme.keyTextColor).copy(alpha = 0.7f),
                                fontSize = 12.sp,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                            Spacer(Modifier.height(10.dp))
                            Button(
                                onClick = {
                                    val intent = Intent(context, com.example.hexkeyboard.ui.settings.BiometricAuthActivity::class.java).apply {
                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    }
                                    context.startActivity(intent)
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(theme.keyTextColor).copy(alpha = 0.18f),
                                    contentColor = Color(theme.keyTextColor)
                                ),
                                shape = RoundedCornerShape(12.dp),
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp)
                            ) {
                                Icon(Icons.Default.Fingerprint, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Desbloquear", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                } else if (filteredCredentials.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(top = 52.dp, bottom = (70 + bottomOffset + navBarBottomDp).dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (searchQuery.isNotEmpty()) {
                                context.getKeyboardString(R.string.cred_search_empty, currentLocale)
                            } else {
                                context.getKeyboardString(R.string.cred_empty, currentLocale)
                            },
                            color = Color(theme.keyTextColor).copy(alpha = 0.7f),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                } else {
                    val topPadding = if (isCredentialsSearchActive) 8.dp else 52.dp
                    val bottomPadding = if (isCredentialsSearchActive) 8.dp else (70 + bottomOffset + navBarBottomDp).dp

                    LazyColumn(
                        state = listState,
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

            // Encabezado flotante: PanelHeader al estar bloqueado, CredentialsSearchBar al estar desbloqueado
            if (viewModel != null && !isCredentialsSearchActive) {
                if (isLocked) {
                    PanelHeader(
                        title = context.getKeyboardString(R.string.panel_title_credentials, currentLocale),
                        theme = theme,
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(start = 8.dp, end = 8.dp, top = 4.dp),
                        glassConfig = glassConfig,
                        onBack = { viewModel.setCurrentView("keyboard") }
                    )
                } else {
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
