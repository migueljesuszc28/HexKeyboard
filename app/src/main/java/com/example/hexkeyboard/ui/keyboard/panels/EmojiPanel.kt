package com.example.hexkeyboard.ui.keyboard.panels

import android.content.Context
import android.graphics.drawable.ColorDrawable
import android.view.Gravity
import android.view.View
import android.widget.PopupWindow
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.automirrored.outlined.Backspace
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.preference.PreferenceManager
import androidx.datastore.preferences.core.edit
import com.example.hexkeyboard.data.repository.ThemeUtils
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.tween
import com.example.hexkeyboard.R
import com.example.hexkeyboard.data.repository.EmojiProvider
import com.example.hexkeyboard.data.repository.KeyboardTheme
import com.example.hexkeyboard.service.HexKeyboardService
import com.example.hexkeyboard.logic.managers.FeedbackManager
import dev.chrisbanes.haze.HazeDefaults
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeEffect
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlin.math.*

import com.example.hexkeyboard.viewmodel.KeyboardViewModel

@Composable
fun EmojiPanel(
    onEmojiSelected: (String) -> Unit,
    onBack: () -> Unit,
    theme: KeyboardTheme,
    viewModel: KeyboardViewModel? = null,
    hazeState: HazeState? = null,
    bottomOffset: Int = 0,
    navBarBottomDp: Int = 0
) {
    val context = LocalContext.current
    val searchQuery by (viewModel?.emojiSearchQuery ?: MutableStateFlow("")).collectAsState()
    val skinTone by (viewModel?.selectedSkinTone ?: MutableStateFlow("")).collectAsState()
    val genderIndex by (viewModel?.selectedGenderIndex ?: MutableStateFlow(0)).collectAsState()
    val emojiList by (viewModel?.filteredEmojiList ?: MutableStateFlow(emptyList())).collectAsState()
    
    LaunchedEffect(Unit) {
        ThemeUtils.getDataStore(context).data.map { it[ThemeUtils.RECENT_EMOJIS] ?: "" }
            .collect { json ->
                val list = json.split(",").filter { it.isNotEmpty() }
                viewModel?.updateRecentEmojis(list)
            }
    }

    val gridState = rememberLazyGridState()
    val scope = rememberCoroutineScope()

    // Lógica para mostrar/ocultar la barra de categorías según el scroll
    var isCategoriesVisible by remember { mutableStateOf(true) }

    LaunchedEffect(gridState) {
        var lastScrollIndex = 0
        var lastScrollOffset = 0
        
        snapshotFlow { Pair(gridState.firstVisibleItemIndex, gridState.firstVisibleItemScrollOffset) }
            .collect { (currentIndex, currentOffset) ->
                if (gridState.isScrollInProgress) {
                    val indexDiff = currentIndex - lastScrollIndex
                    val offsetDiff = currentOffset - lastScrollOffset
                    if (indexDiff > 0 || (indexDiff == 0 && offsetDiff > 40)) {
                        if (isCategoriesVisible) isCategoriesVisible = false
                    } else if (indexDiff < 0 || offsetDiff < -40) {
                        if (!isCategoriesVisible) isCategoriesVisible = true
                    }
                }
                lastScrollIndex = currentIndex
                lastScrollOffset = currentOffset
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

    Box(modifier = Modifier.fillMaxSize()) {
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

        LazyVerticalGrid(
            state = gridState,
            columns = GridCells.Adaptive(minSize = 44.dp),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = 46.dp, bottom = (70 + bottomOffset + navBarBottomDp).dp)
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
                            emoji = item.code,
                            canonical = item.canonical,
                            family = item.family,
                            skinTone = skinTone,
                            genderIndex = genderIndex,
                            onSkinToneSelected = { viewModel?.setSkinTone(it) },
                            onGenderSelected = { viewModel?.setGenderIndex(it) },
                            onEmojiSelected = { finalEmoji ->
                                FeedbackManager.triggerFeedback(context, FeedbackManager.HapticType.KEY_CLICK)
                                viewModel?.saveRecentEmoji(context, finalEmoji)
                                onEmojiSelected(finalEmoji)
                            },
                            theme = theme
                        )
                    }
                }
            }
        }

        if (searchQuery.isEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .padding(start = 8.dp, end = 8.dp, bottom = (bottomOffset + navBarBottomDp + 8).dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.End
            ) {
                AnimatedVisibility(
                    visible = isCategoriesVisible,
                    enter = slideInVertically(
                        initialOffsetY = { it / 2 },
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioLowBouncy,
                            stiffness = Spring.StiffnessMediumLow
                        )
                    ) + fadeIn(animationSpec = tween(300)) + scaleIn(initialScale = 0.9f),
                    exit = slideOutVertically(
                        targetOffsetY = { it / 2 },
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioNoBouncy,
                            stiffness = Spring.StiffnessMedium
                        )
                    ) + fadeOut(animationSpec = tween(200)) + scaleOut(targetScale = 0.9f),
                    modifier = Modifier.weight(1f)
                ) {
                    EmojiCategoryTabs(
                        modifier = Modifier.fillMaxWidth(),
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
                        hazeState = hazeState
                    )
                }

                if (isCategoriesVisible) {
                    Spacer(modifier = Modifier.width(8.dp))
                }

                EmojiDeleteButton(
                    onDelete = {
                        val service = context as? HexKeyboardService
                        service?.handleDelete()
                    },
                    theme = theme,
                    hazeState = hazeState
                )
            }
        }
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
fun EmojiCategoryTabs(selectedTabIndex: Int, onCategoryClick: (String) -> Unit, theme: KeyboardTheme, modifier: Modifier = Modifier, hazeState: HazeState? = null) {
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
    val selectedColor = activeColor
    val unselectedColor = Color(theme.keyboardIconTint).copy(alpha = 0.5f)

    val backgroundColor = Color(theme.keyBackgroundColor)
    
    Surface(
        modifier = modifier
            .height(38.dp)
            .then(
                if (hazeState != null) {
                    Modifier.hazeEffect(state = hazeState, style = HazeDefaults.style(backgroundColor = backgroundColor, blurRadius = 25.dp))
                } else Modifier
            ),
        color = backgroundColor.copy(alpha = if (hazeState != null) 0.95f else 1f),
        shape = RoundedCornerShape(20.dp),
        shadowElevation = 2.dp
    ) {
        @OptIn(ExperimentalMaterial3Api::class)
        CompositionLocalProvider(LocalRippleConfiguration provides null) {
            ScrollableTabRow(
                selectedTabIndex = selectedTabIndex,
                containerColor = Color.Transparent,
                contentColor = selectedColor,
                edgePadding = 4.dp,
                modifier = Modifier.fillMaxWidth(),
                divider = {},
                indicator = {}
            ) {
                EmojiProvider.categories.forEachIndexed { index, category ->
                    val isSelected = index == selectedTabIndex
                    val tint = if (isSelected) selectedColor else unselectedColor
                    Tab(
                        selected = isSelected,
                        onClick = {
                            FeedbackManager.triggerFeedback(context, FeedbackManager.HapticType.KEY_CLICK)
                            onCategoryClick(category.name)
                        },
                        unselectedContentColor = unselectedColor,
                        selectedContentColor = selectedColor,
                        icon = {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .then(
                                        if (isSelected) Modifier.background(selectedColor.copy(alpha = 0.18f))
                                        else Modifier
                                    )
                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                val iconData = categoryIcons[category.name]
                                when (iconData) {
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
fun EmojiDeleteButton(onDelete: () -> Unit, theme: KeyboardTheme, hazeState: HazeState? = null) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(targetValue = if (isPressed) 0.85f else 1f, animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessHigh), label = "delete_bounce")

    LaunchedEffect(isPressed) {
        if (isPressed) {
            delay(400)
            var count = 0
            while (isActive) {
                onDelete()
                count++
                val nextInterval = when {
                    count < 5 -> 80L
                    count < 15 -> 60L
                    else -> 45L
                }
                delay(nextInterval)
            }
        }
    }

    val backgroundColor = Color(theme.keyBackgroundColor)

    Box(
        modifier = Modifier
            .size(45.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .shadow(2.dp, CircleShape)
            .clip(CircleShape)
            .then(
                if (hazeState != null) {
                    Modifier.hazeEffect(state = hazeState, style = HazeDefaults.style(backgroundColor = backgroundColor, blurRadius = 25.dp))
                } else Modifier
            )
            .background(backgroundColor.copy(alpha = if (hazeState != null) 0.95f else 1f))
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
            tint = if (isPressed) Color.Red else Color(theme.keyboardIconTint),
            modifier = Modifier.size(24.dp).offset(x = (-1).dp)
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun EmojiItem(
    emoji: String,
    canonical: String,
    family: EmojiProvider.EmojiFamily,
    skinTone: String,
    genderIndex: Int,
    onSkinToneSelected: (String) -> Unit,
    onGenderSelected: (Int) -> Unit,
    onEmojiSelected: (String) -> Unit,
    theme: KeyboardTheme
) {
    val context = LocalContext.current
    val service = context as? HexKeyboardService
    val parentView = LocalView.current

    // Sincronización Global Total (Estilo Gboard):
    // Los emojis reaccionan dinámicamente al tono y género global usando la familia pre-calculada
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

    // Almacena las coordenadas reales de la celda sin desencadenar recomposiciones durante el scroll
    val cellCoords = remember { floatArrayOf(0f, 0f, 0f, 0f) }

    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .onGloballyPositioned { coordinates ->
                val pos = coordinates.positionInWindow()
                cellCoords[0] = pos.x
                cellCoords[1] = pos.y
                cellCoords[2] = coordinates.size.width.toFloat()
                cellCoords[3] = coordinates.size.height.toFloat()
            }
            .clip(RoundedCornerShape(12.dp))
            .combinedClickable(
                onClick = {
                    FeedbackManager.triggerFeedback(context, FeedbackManager.HapticType.KEY_CLICK)
                    onEmojiSelected(displayEmoji)
                },
                onLongClick = {
                    if (hasVariations) {
                        FeedbackManager.triggerFeedback(context, FeedbackManager.HapticType.LONG_PRESS)
                        showEmojiVariationPopupWindow(
                            context = context,
                            anchorView = parentView,
                            cellX = cellCoords[0],
                            cellY = cellCoords[1],
                            cellWidth = cellCoords[2],
                            cellHeight = cellCoords[3],
                            variationGrid = variationGrid,
                            displayEmoji = displayEmoji,
                            canonical = canonical,
                            theme = theme,
                            onEmojiSelected = onEmojiSelected,
                            service = service
                        )
                    }
                }
            ),
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

fun showEmojiVariationPopupWindow(
    context: Context,
    anchorView: View,
    cellX: Float,
    cellY: Float,
    cellWidth: Float,
    cellHeight: Float,
    variationGrid: List<List<String>>,
    displayEmoji: String,
    canonical: String,
    theme: KeyboardTheme,
    onEmojiSelected: (String) -> Unit,
    service: HexKeyboardService?
) {
    val density = context.resources.displayMetrics.density
    val screenWidth = context.resources.displayMetrics.widthPixels
    val marginPx = (12 * density).toInt()

    val cols = variationGrid.firstOrNull()?.size ?: 1
    val rows = variationGrid.size

    val itemSizePx = (46 * density).toInt()
    val popupWidthPx = (cols * itemSizePx + (16 * density)).toInt()
    val popupHeightPx = (rows * itemSizePx + (16 * density)).toInt()

    val globalCellX = cellX
    val globalCellY = cellY

    val cellCenterX = globalCellX + (cellWidth / 2f)
    val idealLeftX = cellCenterX - (popupWidthPx / 2f)
    val clampedLeftX = idealLeftX.coerceIn(marginPx.toFloat(), (screenWidth - marginPx - popupWidthPx).toFloat().coerceAtLeast(marginPx.toFloat()))

    val topStatusBarMarginPx = (24 * density).toInt()
    val locationOnScreen = IntArray(2)
    anchorView.getLocationOnScreen(locationOnScreen)
    val windowTopOnScreen = locationOnScreen[1]
    val minAllowedYInWindow = if (windowTopOnScreen > 0) {
        -windowTopOnScreen + topStatusBarMarginPx
    } else {
        -1000
    }

    val idealTopY = (globalCellY - popupHeightPx - (8 * density)).toInt()

    val targetY = if (idealTopY >= minAllowedYInWindow) {
        idealTopY
    } else {
        (globalCellY + cellHeight + (8 * density)).toInt()
    }

    var popupWindow: PopupWindow? = null

    val composeView = ComposeView(context).apply {
        if (service != null) {
            setViewTreeLifecycleOwner(service)
            setViewTreeViewModelStoreOwner(service)
            setViewTreeSavedStateRegistryOwner(service)
        }
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        setContent {
            var popupVisible by remember { mutableStateOf(false) }
            LaunchedEffect(Unit) { popupVisible = true }

            val popupScale by animateFloatAsState(targetValue = if (popupVisible) 1f else 0.8f, label = "popup_scale")
            val popupAlpha by animateFloatAsState(targetValue = if (popupVisible) 1f else 0f, label = "popup_alpha")

            Surface(
                color = Color(theme.backgroundColor),
                shape = RoundedCornerShape(20.dp),
                shadowElevation = 10.dp,
                tonalElevation = 8.dp,
                border = BorderStroke(1.dp, Color(theme.keyTextColor).copy(alpha = 0.12f)),
                modifier = Modifier.graphicsLayer {
                    scaleX = popupScale
                    scaleY = popupScale
                    alpha = popupAlpha
                }
            ) {
                Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    variationGrid.forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            row.forEach { variant ->
                                VariationItem(
                                    emoji = variant,
                                    isSelected = (displayEmoji == variant),
                                    theme = theme,
                                    onClick = {
                                        FeedbackManager.triggerFeedback(context, FeedbackManager.HapticType.KEY_CLICK)
                                        service?.saveStickyVariant(canonical, variant)
                                        onEmojiSelected(variant)
                                        try { if (popupWindow?.isShowing == true) popupWindow?.dismiss() } catch (_: Exception) {}
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    popupWindow = PopupWindow(composeView, popupWidthPx, popupHeightPx).apply {
        elevation = 20f
        isTouchable = true
        isOutsideTouchable = true
        setBackgroundDrawable(ColorDrawable(android.graphics.Color.TRANSPARENT))
        inputMethodMode = PopupWindow.INPUT_METHOD_NOT_NEEDED
        isClippingEnabled = false // PERMITE FLOTAR LIBREMENTE FUERA Y POR ENCIMA DEL TECLADO IME
        animationStyle = 0
    }

    if (service != null) {
        composeView.setViewTreeLifecycleOwner(service)
        composeView.setViewTreeViewModelStoreOwner(service)
        composeView.setViewTreeSavedStateRegistryOwner(service)
    }

    popupWindow.showAtLocation(anchorView, Gravity.NO_GRAVITY, clampedLeftX.toInt(), targetY)

    if (service != null) {
        composeView.rootView?.let { root ->
            root.setViewTreeLifecycleOwner(service)
            root.setViewTreeViewModelStoreOwner(service)
            root.setViewTreeSavedStateRegistryOwner(service)
        }
    }
}

@Composable
fun VariationItem(emoji: String, isSelected: Boolean, theme: KeyboardTheme, onClick: () -> Unit) {
    val highlightColor = remember(theme) {
        val shiftColor = theme.keyShiftActiveColor
        if (shiftColor != null && shiftColor != theme.keyboardIconTint) {
            Color(shiftColor)
        } else if (theme.id.contains("dark") || theme.id == "terminal") {
            Color(0xFF4285F4)
        } else {
            Color(0xFF1A73E8)
        }
    }

    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(HexagonShape())
            .background(if (isSelected) highlightColor else Color.Transparent)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(emoji, fontSize = 26.sp)
    }
}

class HexagonShape : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val path = Path().apply {
            val radius = (min(size.width, size.height) / 2f); val cx = size.width / 2f; val cy = size.height / 2f
            for (i in 0 until 6) {
                val angle = i * PI / 3.0; val x = cx + (radius * cos(angle).toFloat()); val y = cy + (radius * sin(angle).toFloat())
                if (i == 0) moveTo(x, y) else lineTo(x, y)
            }
            close()
        }
        return Outline.Generic(path)
    }
}