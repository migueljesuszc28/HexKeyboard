package com.example.hexkeyboard.ui.keyboard

import android.content.SharedPreferences
import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.preference.PreferenceManager
import com.example.hexkeyboard.data.repository.ThemeUtils
import com.example.hexkeyboard.viewmodel.KeyboardViewModel
import dev.chrisbanes.haze.HazeDefaults
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun KeyboardScreen(viewModel: KeyboardViewModel) {
    val context = LocalContext.current
    val keyboardThemeOpt by viewModel.keyboardTheme.collectAsState()
    val keyboardTheme = keyboardThemeOpt ?: return

    val parallaxOffsetState = viewModel.parallaxOffset.collectAsState()

    val hazeState = remember { HazeState() }
    val isGlassTheme = keyboardTheme.id == "glass"
    val hasBackgroundImage = keyboardTheme.backgroundImageUri != null

    var backgroundBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var blurredBitmap by remember { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(keyboardTheme.backgroundImageUri, keyboardTheme.backgroundBlur) {
        if (keyboardTheme.backgroundImageUri != null) {
            val newBitmap = withContext(Dispatchers.IO) {
                ThemeUtils.loadBitmapFromUri(context, keyboardTheme.backgroundImageUri!!)
            } as Bitmap?
            if (newBitmap != null) {
                backgroundBitmap?.recycle()
                blurredBitmap?.recycle()

                backgroundBitmap = newBitmap
                blurredBitmap = if (keyboardTheme.backgroundBlur > 0) {
                    withContext(Dispatchers.IO) {
                        ThemeUtils.blurBitmap(newBitmap!!, keyboardTheme.backgroundBlur)
                    } as Bitmap?
                } else {
                    null
                }
            }
        } else {
            backgroundBitmap?.recycle()
            blurredBitmap?.recycle()
            backgroundBitmap = null
            blurredBitmap = null
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            backgroundBitmap?.recycle()
            blurredBitmap?.recycle()
        }
    }

    val prefs = remember { PreferenceManager.getDefaultSharedPreferences(context) }
    var bottomOffset by remember { mutableIntStateOf(prefs.getInt("keyboard_bottom_offset", 35)) }

    DisposableEffect(context) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { p, key ->
            if (key == "keyboard_bottom_offset") {
                bottomOffset = p.getInt(key, 0)
            }
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .wrapContentHeight()
            .background(
                color = Color(keyboardTheme.backgroundColor),
                shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
            )
            .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
    ) {
        // Continuous Background Layer
        val img = blurredBitmap ?: backgroundBitmap
        img?.let {
            Image(
                bitmap = it.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier
                    .matchParentSize()
                    .alpha(keyboardTheme.backgroundOpacity)
                    .graphicsLayer {
                        if (keyboardTheme.parallaxEffect) {
                            val parallaxLimit = 0.10f
                            val scale = 1.0f + parallaxLimit
                            val maxShiftX = (size.width * parallaxLimit) / 2f
                            val maxShiftY = (size.height * parallaxLimit) / 2f

                            val offset = parallaxOffsetState.value
                            translationX = offset.x * maxShiftX
                            translationY = offset.y * maxShiftY
                            scaleX = scale
                            scaleY = scale
                        }
                    },
                contentScale = ContentScale.Crop
            )
        }

        if (isGlassTheme) {
            Box(modifier = Modifier.matchParentSize().hazeEffect(state = hazeState, style = HazeDefaults.style(backgroundColor = Color(keyboardTheme.backgroundColor), blurRadius = 20.dp)).background(Color(keyboardTheme.backgroundColor)))
        } else if (!hasBackgroundImage) {
            Box(modifier = Modifier.matchParentSize().background(Color(keyboardTheme.backgroundColor)))
        } else {
            Box(modifier = Modifier.matchParentSize().background(Color(keyboardTheme.backgroundColor).copy(alpha = 1f - keyboardTheme.backgroundOpacity)))
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .then(if (isGlassTheme) Modifier.hazeSource(hazeState) else Modifier)
                .navigationBarsPadding()
        ) {
            SuggestionsBarSection(viewModel, keyboardTheme)

            Box(
                modifier = Modifier.fillMaxWidth().wrapContentHeight(),
                contentAlignment = Alignment.TopCenter
            ) {
                KeyboardMainSection(viewModel, keyboardTheme, hasBackgroundImage) { _, _ -> }
                PanelsSection(viewModel, keyboardTheme)
            }

            if (bottomOffset > 0) {
                Spacer(modifier = Modifier.height(bottomOffset.dp))
            }
        }
    }
}
