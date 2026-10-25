package com.example.hexkeyboard.ui.keyboard.panels

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.automirrored.outlined.Backspace
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.convx.music.ui.component.backdrop.backdrops.layerBackdrop
import com.example.hexkeyboard.ui.component.backdrop.backdrops.rememberLayerBackdrop
import com.example.hexkeyboard.R
import com.example.hexkeyboard.data.repository.EmojiProvider
import com.example.hexkeyboard.data.repository.KeyboardTheme
import com.example.hexkeyboard.data.repository.ThemeUtils
import com.example.hexkeyboard.logic.managers.FeedbackManager
import com.example.hexkeyboard.service.HexKeyboardService
import com.example.hexkeyboard.ui.component.GlassEffectConfig
import com.example.hexkeyboard.ui.component.LocalAppBackdrop
import com.example.hexkeyboard.ui.component.LocalGlassEffectConfig
import com.example.hexkeyboard.ui.component.glassContentColorFor
import com.example.hexkeyboard.ui.component.liquidGlass
import com.example.hexkeyboard.ui.keyboard.components.EmojiSearchBar
import com.example.hexkeyboard.ui.keyboard.components.bounceClick
import com.example.hexkeyboard.viewmodel.KeyboardViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.time.Duration.Companion.milliseconds

// ─────────────────────────────────────────────────────────────────────────────
// Popup de variaciones (tono de piel / género)
//
// IMPORTANTE: el popup YA NO es un PopupWindow (otra ventana del sistema). Se dibuja como
// un overlay dentro del mismo árbol de Compose del panel de emojis. Así la celda, el dedo y
// el popup comparten exactamente el mismo sistema de coordenadas (el del panel) y no hay que
// convertir entre "ventana de Compose", "ventana del IME" y "pantalla", que es lo que se
// desfasaba al reabrir el panel.
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Estado del popup. Todas las posiciones están en píxeles, en coordenadas LOCALES AL PANEL.
 */
@Stable
class EmojiVariationPopupState {
    var visible by mutableStateOf(false)
        private set
    var showId by mutableStateOf(0)
        private set
    var grid by mutableStateOf<List<List<String>>>(emptyList())
        private set
    var origin by mutableStateOf(Offset.Zero)
        private set
    var selected by mutableStateOf<String?>(null)
    var canonical: String = ""
        private set
    var onPick: ((String) -> Unit)? = null
        private set

    /** Referencia viva al panel (NO una posición cacheada). */
    var panelCoords: LayoutCoordinates? = null

    fun show(
        cellTopLeft: Offset,
        cellBottomRight: Offset,
        panelSize: IntSize,
        density: Float,
        grid: List<List<String>>,
        initialSelected: String,
        canonical: String,
        onPick: (String) -> Unit
    ) {
        val cols = grid.firstOrNull()?.size ?: 1
        val rows = grid.size

        // Medidas exactas del contenido: items 44dp, separación 4dp, padding 8dp.
        val popupW = (cols * 44f + (cols - 1).coerceAtLeast(0) * 4f + 16f) * density
        val popupH = (rows * 44f + (rows - 1).coerceAtLeast(0) * 4f + 16f) * density
        val margin = 8f * density
        val gap = 8f * density

        val cellCenterX = (cellTopLeft.x + cellBottomRight.x) / 2f
        val maxX = (panelSize.width - margin - popupW).coerceAtLeast(margin)
        val x = (cellCenterX - popupW / 2f).coerceIn(margin, maxX)

        val above = cellTopLeft.y - popupH - gap
        val rawY = if (above >= margin) above else cellBottomRight.y + gap
        val maxY = (panelSize.height - margin - popupH).coerceAtLeast(margin)
        val y = rawY.coerceIn(margin, maxY)

        this.grid = grid
        this.origin = Offset(x, y)
        this.selected = initialSelected
        this.canonical = canonical
        this.onPick = onPick
        this.showId += 1
        this.visible = true
    }

    fun dismiss() {
        visible = false
    }

    /**
     * @param panelPos posición del dedo en coordenadas locales al panel.
     * @return true si cambió la variante seleccionada.
     */
    fun updateSelection(panelPos: Offset, density: Float): Boolean {
        val g = grid
        if (!visible || g.isEmpty()) return false

        val cols = g.firstOrNull()?.size ?: 1
        val rows = g.size
        val pitch = 48f * density   // 44dp item + 4dp separación
        val pad = 8f * density

        val relX = panelPos.x - origin.x - pad
        val relY = panelPos.y - origin.y - pad

        val col = if (relX < 0) 0 else (relX / pitch).toInt().coerceIn(0, cols - 1)
        val row = if (relY < 0) 0 else (relY / pitch).toInt().coerceIn(0, rows - 1)

        val variant = g.getOrNull(row)?.getOrNull(col)
        if (variant != null && selected != variant) {
            selected = variant
            return true
        }
        return false
    }
}

private val LocalEmojiVariationPopup = staticCompositionLocalOf<EmojiVariationPopupState?> { null }

@Composable
fun EmojiPanel(
    onEmojiSelected: (String) -> Unit,
    onBack: () -> Unit,
    theme: KeyboardTheme,
    viewModel: KeyboardViewModel? = null,
    bottomOffset: Int = 0,
    navBarBottomDp: Int = 0
) {
    val context = LocalContext.current
    val searchQuery by (viewModel?.emojiSearchQuery ?: MutableStateFlow("")).collectAsState()
    val isEmojiSearchActive by (viewModel?.isEmojiSearchActive ?: MutableStateFlow(false)).collectAsState()
    val skinTone by (viewModel?.selectedSkinTone ?: MutableStateFlow("")).collectAsState()
    val genderIndex by (viewModel?.selectedGenderIndex ?: MutableStateFlow(0)).collectAsState()
    val emojiList by (viewModel?.filteredEmojiList ?: MutableStateFlow(emptyList())).collectAsState()

    val popupState = remember { EmojiVariationPopupState() }

    // Si cambia la búsqueda / tono / género, el popup abierto ya no tiene sentido.
    LaunchedEffect(searchQuery, isEmojiSearchActive, skinTone, genderIndex) {
        popupState.dismiss()
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
                0.3f to baseThemeColor.copy(alpha = 0.40f),
                1.0f to baseThemeColor.copy(alpha = 0.82f)
            )
        )
    }

    LaunchedEffect(Unit) {
        ThemeUtils.getDataStore(context).data.map { it[ThemeUtils.RECENT_EMOJIS] ?: "" }
            .collect { json ->
                val list = json.split(",").filter { it.isNotEmpty() }
                viewModel?.updateRecentEmojis(list)
            }
    }

    val gridState = rememberLazyGridState()
    val scope = rememberCoroutineScope()

    val currentCategoryIndex by remember(emojiList) {
        derivedStateOf {
            val firstIndex = gridState.firstVisibleItemIndex
            if (firstIndex >= emojiList.size) 0
            else {
                when (val currentItem = emojiList.getOrNull(firstIndex)) {
                    is EmojiProvider.EmojiGridItem.Header -> currentItem.categoryIndex
                    is EmojiProvider.EmojiGridItem.Emoji -> currentItem.categoryIndex
                    null -> 0
                }
            }
        }
    }

    CompositionLocalProvider(
        LocalAppBackdrop provides backdrop,
        LocalGlassEffectConfig provides glassConfig,
        LocalEmojiVariationPopup provides popupState
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .onGloballyPositioned { popupState.panelCoords = it }
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .layerBackdrop(backdrop)
            ) {
                if (searchQuery.isNotEmpty() && emojiList.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Default.SearchOff,
                                contentDescription = null,
                                modifier = Modifier.size(48.dp),
                                tint = Color(theme.keyTextColor)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "No se encontraron emojis",
                                color = Color(theme.keyTextColor),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }

                val topPadding = if (isEmojiSearchActive) 8.dp else 52.dp
                val bottomPadding = if (isEmojiSearchActive) 8.dp else (70 + bottomOffset + navBarBottomDp).dp
                LazyVerticalGrid(
                    state = gridState,
                    columns = GridCells.Adaptive(minSize = 44.dp),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(top = topPadding, bottom = bottomPadding)
                ) {
                    items(
                        items = emojiList,
                        key = { item ->
                            when(item) {
                                is EmojiProvider.EmojiGridItem.Header -> "header_${item.name}"
                                is EmojiProvider.EmojiGridItem.Emoji -> "${item.category}_${item.code}"
                            }
                        },
                        span = { item -> GridItemSpan(if (item is EmojiProvider.EmojiGridItem.Header) maxLineSpan else 1) },
                        contentType = { item -> if (item is EmojiProvider.EmojiGridItem.Header) "header" else "emoji" }
                    ) { item ->
                        when (item) {
                            is EmojiProvider.EmojiGridItem.Header -> EmojiHeader(name = item.name, theme = theme)
                            is EmojiProvider.EmojiGridItem.Emoji -> {
                                EmojiItem(
                                    canonical = item.canonical,
                                    family = item.family,
                                    skinTone = skinTone,
                                    genderIndex = genderIndex,
                                    onEmojiSelected = { finalEmoji ->
                                        FeedbackManager.triggerFeedback(context, FeedbackManager.HapticType.KEY_CLICK)
                                        viewModel?.saveRecentEmoji(context, finalEmoji)
                                        onEmojiSelected(finalEmoji)
                                    },
                                    theme = theme,
                                    viewModel = viewModel
                                )
                            }
                        }
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

            if (!isEmojiSearchActive) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .height(58.dp)
                        .background(topShadowMaskBrush)
                )
            }

            if (viewModel != null && !isEmojiSearchActive) {
                EmojiSearchBar(
                    viewModel = viewModel,
                    theme = theme,
                    query = searchQuery,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(start = 8.dp, end = 8.dp, top = 4.dp),
                    glassConfig = glassConfig,
                    showBackButton = false
                )
            }

            if (searchQuery.isEmpty() && !isEmojiSearchActive) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .padding(start = 8.dp, end = 8.dp, bottom = (bottomOffset + navBarBottomDp + 0).dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    EmojiBackButton(
                        onBack = onBack,
                        theme = theme,
                        glassConfig = glassConfig
                    )

                    Spacer(modifier = Modifier.width(6.dp))

                    EmojiCategoryTabs(
                        modifier = Modifier.weight(1f),
                        selectedTabIndex = currentCategoryIndex,
                        onCategoryClick = { categoryName ->
                            val index = emojiList.indexOfFirst { it is EmojiProvider.EmojiGridItem.Header && it.name == categoryName }
                            if (index >= 0) {
                                scope.launch {
                                    gridState.scrollToItem(index)
                                }
                            }
                        },
                        theme = theme,
                        glassConfig = glassConfig
                    )

                    Spacer(modifier = Modifier.width(6.dp))

                    EmojiDeleteButton(
                        onDelete = {
                            val service = context as? HexKeyboardService
                            service?.handleDelete()
                        },
                        theme = theme,
                        glassConfig = glassConfig
                    )
                }
            }

            // Overlay del popup de variaciones: SIEMPRE el último hijo para quedar por encima de todo.
            EmojiVariationPopupLayer(state = popupState, theme = theme)
        }
    }
}

@Composable
fun EmojiBackButton(
    onBack: () -> Unit,
    theme: KeyboardTheme,
    glassConfig: GlassEffectConfig = LocalGlassEffectConfig.current
) {
    val context = LocalContext.current
    val buttonShape = CircleShape
    val iconColor = glassContentColorFor(
        behind = Color(theme.backgroundColor),
        tint = glassConfig.surfaceTintColor,
        opacity = glassConfig.surfaceOpacity
    )

    Box(
        modifier = Modifier
            .size(45.dp)
            .bounceClick(
                onClick = {
                    FeedbackManager.triggerFeedback(context, FeedbackManager.HapticType.KEY_CLICK)
                    onBack()
                }
            )
            .liquidGlass(
                config = glassConfig,
                shape = buttonShape,
                highlightAlpha = 0.3f
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Default.Keyboard,
            contentDescription = "Volver",
            tint = iconColor,
            modifier = Modifier.size(24.dp)
        )
    }
}

@Composable
fun EmojiHeader(name: String, theme: KeyboardTheme) {
    val textColor = Color(theme.keyTextColor)

    Text(
        text = name,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Medium,
        color = textColor,
        modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 8.dp)
    )
}

@Composable
fun EmojiCategoryTabs(
    selectedTabIndex: Int,
    onCategoryClick: (String) -> Unit,
    theme: KeyboardTheme,
    modifier: Modifier = Modifier,
    glassConfig: GlassEffectConfig = LocalGlassEffectConfig.current
) {
    val context = LocalContext.current
    val categoryIcons = remember {
        mapOf(
            "Recientes" to Icons.Default.History, "Principales" to R.drawable.ic_emoji,
            "Personas" to Icons.Default.EmojiPeople, "Animales" to Icons.Default.Pets,
            "Comida" to Icons.Default.Restaurant, "Actividades" to Icons.Default.EmojiEvents,
            "Lugares" to Icons.Default.Public, "Objetos" to Icons.Default.Lightbulb,
            "Símbolos" to Icons.Default.EmojiSymbols, "Banderas" to Icons.Default.Flag
        )
    }

    val activeColor = remember(theme) {
        val shiftColor = theme.keyShiftActiveColor
        if (shiftColor != null && shiftColor != theme.keyboardIconTint) {
            Color(shiftColor)
        } else if (theme.keyTextColor != theme.keyboardIconTint) {
            Color(theme.keyTextColor)
        } else if (theme.id.contains("dark") || theme.id == "terminal") {
            Color(0xFF4285F4)
        } else {
            Color(0xFF1A73E8)
        }
    }
    val unselectedColor = Color(theme.keyboardIconTint).copy(alpha = 0.5f)
    val categoryShape = remember { RoundedCornerShape(20.dp) }

    Surface(
        modifier = modifier
            .height(38.dp)
            .liquidGlass(
                config = glassConfig,
                shape = categoryShape,
                highlightAlpha = 0.3f
            ),
        color = Color.Transparent,
        shape = categoryShape,
        shadowElevation = 0.dp
    ) {
        @OptIn(ExperimentalMaterial3Api::class)
        CompositionLocalProvider(LocalRippleConfiguration provides null) {
            SecondaryScrollableTabRow(
                selectedTabIndex = selectedTabIndex,
                containerColor = Color.Transparent,
                contentColor = activeColor,
                edgePadding = 4.dp,
                modifier = Modifier.fillMaxWidth(),
                divider = {},
                indicator = {}
            ) {
                EmojiProvider.categories.forEachIndexed { index, category ->
                    val isSelected = index == selectedTabIndex
                    val tint = if (isSelected) activeColor else unselectedColor
                    Tab(
                        selected = isSelected,
                        onClick = {
                            FeedbackManager.triggerFeedback(context, FeedbackManager.HapticType.KEY_CLICK)
                            onCategoryClick(category.name)
                        },
                        unselectedContentColor = unselectedColor,
                        selectedContentColor = activeColor,
                        icon = {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .then(
                                        if (isSelected) Modifier.background(activeColor.copy(alpha = 0.18f))
                                        else Modifier
                                    )
                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                when (val iconData = categoryIcons[category.name]) {
                                    is ImageVector -> Icon(imageVector = iconData, contentDescription = null, modifier = Modifier.size(20.dp), tint = tint)
                                    is Int -> Icon(painter = painterResource(iconData), contentDescription = null, modifier = Modifier.size(20.dp), tint = tint)
                                    else -> Text(category.icon, fontSize = 18.sp, color = tint)
                                }
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun EmojiDeleteButton(
    onDelete: () -> Unit,
    theme: KeyboardTheme,
    glassConfig: GlassEffectConfig = LocalGlassEffectConfig.current
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.85f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessHigh),
        label = "delete_bounce"
    )

    LaunchedEffect(isPressed) {
        if (isPressed) {
            delay(400.milliseconds)
            var count = 0
            while (isActive) {
                onDelete()
                count++
                val nextInterval = when {
                    count < 5 -> 80L
                    count < 15 -> 60L
                    else -> 45L
                }
                delay(nextInterval.milliseconds)
            }
        }
    }

    val buttonShape = CircleShape
    val iconColor = if (isPressed) Color.Red else {
        glassContentColorFor(
            behind = Color(theme.backgroundColor),
            tint = glassConfig.surfaceTintColor,
            opacity = glassConfig.surfaceOpacity
        )
    }

    Box(
        modifier = Modifier
            .size(45.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .liquidGlass(
                config = glassConfig,
                shape = buttonShape,
                highlightAlpha = 0.3f
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onDelete
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = if (isPressed) Icons.AutoMirrored.Filled.Backspace else Icons.AutoMirrored.Outlined.Backspace,
            contentDescription = "Borrar",
            tint = iconColor,
            modifier = Modifier.size(24.dp).offset(x = (-1).dp)
        )
    }
}

private class CellCoordsHolder {
    var coords: LayoutCoordinates? = null
}

@Composable
fun EmojiItem(
    canonical: String,
    family: EmojiProvider.EmojiFamily,
    skinTone: String,
    genderIndex: Int,
    onEmojiSelected: (String) -> Unit,
    theme: KeyboardTheme,
    viewModel: KeyboardViewModel? = null
) {
    val context = LocalContext.current
    val service = context as? HexKeyboardService
    val scope = rememberCoroutineScope()
    val viewConfiguration = LocalViewConfiguration.current
    val density = LocalDensity.current.density
    val popupState = LocalEmojiVariationPopup.current

    // Evita capturar versiones viejas dentro del pointerInput.
    val currentOnEmojiSelected by rememberUpdatedState(onEmojiSelected)

    val longPressTimeoutFlow = remember {
        ThemeUtils.getDataStore(context).data.map { it[ThemeUtils.LONG_PRESS_DURATION] ?: 259 }
    }
    val longPressTimeoutMs by longPressTimeoutFlow.collectAsState(259)

    val displayEmoji = remember(canonical, skinTone, genderIndex) {
        val gendered = when (genderIndex) {
            1 -> family.male ?: family.neutral ?: family.female
            2 -> family.female ?: family.neutral ?: family.male
            else -> family.neutral ?: family.male ?: family.female
        } ?: canonical
        EmojiProvider.applySkinTone(gendered, skinTone)
    }

    val variationGrid = remember(canonical) { EmojiProvider.getEmojiVariationGrid(canonical) }
    val hasVariations = remember(canonical) { EmojiProvider.hasVariations(canonical) }

    // Referencia viva a la celda. NUNCA se cachean posiciones: se consultan en el momento de usarlas.
    val coordsHolder = remember { CellCoordsHolder() }

    var isPressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.85f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessHigh),
        label = "emoji_bounce"
    )

    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .onGloballyPositioned { coordinates ->
                coordsHolder.coords = coordinates
            }
            .clip(RoundedCornerShape(12.dp))
            .pointerInput(displayEmoji, hasVariations, longPressTimeoutMs, popupState) {
                awaitPointerEventScope {
                    while (true) {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        if (down.isConsumed) continue

                        // Un nuevo toque en la cuadrícula cierra cualquier popup abierto.
                        popupState?.dismiss()

                        isPressed = true
                        val downTime = System.currentTimeMillis()
                        val downPos = down.position
                        var isLongPressActive = false
                        var hasDraggedSinceLongPress = false

                        val longPressJob = scope.launch {
                            delay(longPressTimeoutMs.milliseconds)
                            if (isActive && hasVariations) {
                                val host = popupState
                                val panel = host?.panelCoords
                                val cell = coordsHolder.coords
                                if (host == null || panel == null || cell == null ||
                                    !panel.isAttached || !cell.isAttached
                                ) {
                                    return@launch
                                }

                                FeedbackManager.triggerFeedback(context, FeedbackManager.HapticType.LONG_PRESS)

                                // Celda expresada en coordenadas LOCALES AL PANEL (mismo espacio donde se dibuja el popup).
                                val topLeft = panel.localPositionOf(cell, Offset.Zero)
                                val bottomRight = panel.localPositionOf(
                                    cell,
                                    Offset(cell.size.width.toFloat(), cell.size.height.toFloat())
                                )

                                host.show(
                                    cellTopLeft = topLeft,
                                    cellBottomRight = bottomRight,
                                    panelSize = panel.size,
                                    density = density,
                                    grid = variationGrid,
                                    initialSelected = displayEmoji,
                                    canonical = canonical,
                                    onPick = { picked -> currentOnEmojiSelected(picked) }
                                )
                                isLongPressActive = true
                            }
                        }

                        val pointerId = down.id
                        var isUpOrCancelled = false

                        try {
                            while (!isUpOrCancelled) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == pointerId }
                                if (change == null) {
                                    break
                                }

                                if (change.pressed) {
                                    val dist = (change.position - downPos).getDistance()
                                    if (!isLongPressActive && dist > viewConfiguration.touchSlop) {
                                        longPressJob.cancel()
                                    }
                                    if (isLongPressActive) {
                                        if (dist > viewConfiguration.touchSlop) {
                                            hasDraggedSinceLongPress = true
                                        }
                                        val host = popupState
                                        val panel = host?.panelCoords
                                        val cell = coordsHolder.coords
                                        if (host != null && hasDraggedSinceLongPress &&
                                            panel != null && cell != null &&
                                            panel.isAttached && cell.isAttached
                                        ) {
                                            // Posición del dedo en coordenadas locales al panel.
                                            val fingerInPanel = panel.localPositionOf(cell, change.position)
                                            if (host.updateSelection(fingerInPanel, density)) {
                                                FeedbackManager.triggerFeedback(context, FeedbackManager.HapticType.KEY_CLICK)
                                            }
                                        }
                                        change.consume()
                                    }
                                } else {
                                    if (isLongPressActive) {
                                        change.consume()
                                    }
                                    isUpOrCancelled = true
                                    longPressJob.cancel()

                                    if (isLongPressActive) {
                                        if (hasDraggedSinceLongPress) {
                                            val selected = popupState?.selected
                                            if (selected != null) {
                                                FeedbackManager.triggerFeedback(context, FeedbackManager.HapticType.KEY_CLICK)
                                                service?.saveStickyVariant(canonical, selected)
                                                viewModel?.saveRecentEmoji(context, selected)
                                                currentOnEmojiSelected(selected)
                                            }
                                            popupState?.dismiss()
                                        } else {
                                            // Interacción híbrida: si soltó sin arrastrar, el popup se queda abierto
                                            // para que el usuario pueda tocar una variante.
                                        }
                                    } else {
                                        val duration = System.currentTimeMillis() - downTime
                                        val dist = (change.position - downPos).getDistance()
                                        if (duration < longPressTimeoutMs && dist <= viewConfiguration.touchSlop) {
                                            FeedbackManager.triggerFeedback(context, FeedbackManager.HapticType.KEY_CLICK)
                                            viewModel?.saveRecentEmoji(context, displayEmoji)
                                            currentOnEmojiSelected(displayEmoji)
                                        }
                                    }
                                }
                            }
                        } finally {
                            isPressed = false
                            longPressJob.cancel()
                        }
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Text(text = displayEmoji, fontSize = 35.sp, lineHeight = 42.sp)

        if (hasVariations) {
            Box(modifier = Modifier.fillMaxSize().padding(4.dp), contentAlignment = Alignment.BottomEnd) {
                Canvas(modifier = Modifier.size(6.dp)) {
                    val path = Path().apply {
                        moveTo(size.width, 0f); lineTo(size.width, size.height); lineTo(0f, size.height); close()
                    }
                    drawPath(path = path, color = Color(theme.keyTextColor).copy(alpha = 0.3f))
                }
            }
        }
    }
}

/**
 * Overlay del popup: un "scrim" invisible que cierra el popup al tocar fuera, más el popup en sí.
 * Debe llamarse como ÚLTIMO hijo del Box raíz del panel.
 */
@Composable
private fun EmojiVariationPopupLayer(
    state: EmojiVariationPopupState,
    theme: KeyboardTheme
) {
    if (!state.visible) return

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(state) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    state.dismiss()
                }
            }
    )

    // key(showId): cada vez que se abre un popup nuevo se reinicia su animación de entrada.
    key(state.showId) {
        EmojiVariationPopupContent(state = state, theme = theme)
    }
}

@Composable
private fun EmojiVariationPopupContent(
    state: EmojiVariationPopupState,
    theme: KeyboardTheme
) {
    val context = LocalContext.current
    val service = context as? HexKeyboardService

    var popupVisible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { popupVisible = true }

    val popupScale by animateFloatAsState(targetValue = if (popupVisible) 1f else 0.8f, label = "popup_scale")
    val popupAlpha by animateFloatAsState(targetValue = if (popupVisible) 1f else 0f, label = "popup_alpha")

    val grid = state.grid
    val currentSelectedVariant = state.selected

    Surface(
        color = Color(theme.popupBackgroundColor),
        shape = RoundedCornerShape(20.dp),
        shadowElevation = 10.dp,
        border = if (theme.keyStrokeWidth > 0f) BorderStroke(1.dp, Color(theme.keyStrokeColor)) else null,
        modifier = Modifier
            .offset { IntOffset(state.origin.x.roundToInt(), state.origin.y.roundToInt()) }
            .graphicsLayer {
                scaleX = popupScale
                scaleY = popupScale
                alpha = popupAlpha
            }
    ) {
        Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            grid.forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    row.forEach { variant ->
                        VariationItem(
                            emoji = variant,
                            isSelected = (currentSelectedVariant == variant),
                            theme = theme,
                            onClick = {
                                FeedbackManager.triggerFeedback(context, FeedbackManager.HapticType.KEY_CLICK)
                                service?.saveStickyVariant(state.canonical, variant)
                                state.onPick?.invoke(variant)
                                state.dismiss()
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun VariationItem(emoji: String, isSelected: Boolean, theme: KeyboardTheme, onClick: () -> Unit) {
    val highlightColor = remember(theme) {
        if (theme.id.contains("dark") || theme.id == "terminal") {
            Color(0xFF4285F4)
        } else {
            Color(theme.popupSelectedBackgroundColor)
        }
    }

    val textColor = remember(theme, isSelected) {
        if (isSelected) Color(theme.popupSelectedTextColor) else Color(theme.popupTextColor)
    }

    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(RoundedCornerShape(11.dp))
            .background(if (isSelected) highlightColor else Color.Transparent)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(emoji, fontSize = 26.sp, color = textColor)
    }
}