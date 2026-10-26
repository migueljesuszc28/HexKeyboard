package com.example.hexkeyboard.ui.keyboard.panels

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
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
import kotlin.time.Duration.Companion.milliseconds

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

    LaunchedEffect(gridState.isScrollInProgress) {
        if (gridState.isScrollInProgress) {
            val service = context as? HexKeyboardService
            service?.mHexKeyboardView?.hidePopup()
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            val service = context as? HexKeyboardService
            service?.mHexKeyboardView?.hidePopup()
        }
    }

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
        LocalGlassEffectConfig provides glassConfig
    ) {
        Box(
            modifier = Modifier.fillMaxSize()
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
    val flatVariations = remember(variationGrid) { variationGrid.flatten() }

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
            .pointerInput(displayEmoji, hasVariations, longPressTimeoutMs) {
                awaitPointerEventScope {
                    while (true) {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        if (down.isConsumed) continue

                        service?.mHexKeyboardView?.hidePopup()

                        isPressed = true
                        val downTime = System.currentTimeMillis()
                        val downPos = down.position

                        var isLongPressActive = false

                        val longPressJob = scope.launch {
                            delay(longPressTimeoutMs.milliseconds)
                            if (isActive && hasVariations) {
                                val keyboardView = service?.mHexKeyboardView
                                val cellCoords = coordsHolder.coords
                                if (keyboardView != null && cellCoords != null && cellCoords.isAttached) {
                                    FeedbackManager.triggerFeedback(context, FeedbackManager.HapticType.LONG_PRESS)

                                    val cellPosInWindow = cellCoords.positionInWindow()
                                    val cellW = cellCoords.size.width.toFloat()
                                    val cellH = cellCoords.size.height.toFloat()

                                    val screenX = cellPosInWindow.x + cellW / 2f
                                    val screenY = cellPosInWindow.y + cellH / 2f

                                    keyboardView.showPopupForAlternatives(
                                        screenX = screenX,
                                        screenY = screenY,
                                        rx = cellW / 2f,
                                        ry = cellH / 2f,
                                        mainSymbol = displayEmoji,
                                        alternatives = flatVariations
                                    ) { pickedVariant ->
                                        FeedbackManager.triggerFeedback(context, FeedbackManager.HapticType.KEY_CLICK)
                                        service.saveStickyVariant(canonical, pickedVariant)
                                        viewModel?.saveRecentEmoji(context, pickedVariant)
                                        currentOnEmojiSelected(pickedVariant)
                                    }
                                    isLongPressActive = true
                                }
                            }
                        }

                        val pointerId = down.id
                        var isUpOrCancelled = false

                        try {
                            while (!isUpOrCancelled) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == pointerId }
                                if (change == null) break

                                if (change.pressed) {
                                    if (!isLongPressActive) {
                                        val dist = (change.position - downPos).getDistance()
                                        if (dist > viewConfiguration.touchSlop) {
                                            longPressJob.cancel()
                                        }
                                    } else {
                                        change.consume()
                                        val cellCoords = coordsHolder.coords
                                        if (cellCoords != null && cellCoords.isAttached) {
                                            val cellPosInWindow = cellCoords.positionInWindow()
                                            val screenX = cellPosInWindow.x + change.position.x
                                            val screenY = cellPosInWindow.y + change.position.y
                                            service?.mHexKeyboardView?.onLongPressDrag(screenX, screenY)
                                        }
                                    }
                                } else {
                                    isUpOrCancelled = true
                                    longPressJob.cancel()
                                    if (isLongPressActive) {
                                        change.consume()
                                        service?.mHexKeyboardView?.onLongPressRelease()
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