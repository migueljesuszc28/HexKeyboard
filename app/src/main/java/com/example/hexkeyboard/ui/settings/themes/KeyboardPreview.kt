package com.example.hexkeyboard.ui.settings.themes

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.example.hexkeyboard.data.repository.ThemeUtils
import android.view.ViewGroup
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.datastore.preferences.core.emptyPreferences
import com.example.hexkeyboard.data.repository.KeyboardTheme
import com.example.hexkeyboard.ui.keyboard.components.HexKeyboardView
import com.example.hexkeyboard.ui.keyboard.components.HexLayoutEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
@Composable
fun KeyboardPreview(
    theme: KeyboardTheme,
    modifier: Modifier = Modifier,
    onKeyClick: ((HexLayoutEngine.Key) -> Unit)? = null
) {
    val context = LocalContext.current
    var backgroundImage by remember(theme.backgroundImageUri) { mutableStateOf<Bitmap?>(null) }
    var backgroundBlurImage by remember(theme.backgroundImageUri, theme.backgroundBlur) { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(theme.backgroundImageUri, theme.backgroundBlur) {
        if (theme.backgroundImageUri != null) {
            withContext(Dispatchers.IO) {
                try {
                    val uri = Uri.parse(theme.backgroundImageUri)
                    val inputStream = context.contentResolver.openInputStream(uri)
                    val original = BitmapFactory.decodeStream(inputStream)
                    backgroundImage = original
                    backgroundBlurImage = if (theme.backgroundBlur > 0) ThemeUtils.blurBitmap(original, theme.backgroundBlur) else null
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        } else {
            backgroundImage = null
            backgroundBlurImage = null
        }
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
            .background(androidx.compose.ui.graphics.Color(theme.backgroundColor))
    ) {
        // Continuous Background Image Layer (GBoard style)
        val img = backgroundBlurImage ?: backgroundImage
        if (img != null) {
            Image(
                bitmap = img.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier
                    .matchParentSize()
                    .alpha(theme.backgroundOpacity),
                contentScale = ContentScale.Crop
            )
        }

        Column(modifier = Modifier.fillMaxWidth()) {
            // Mock Suggestions Bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(46.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Hex", color = androidx.compose.ui.graphics.Color(theme.keyTextColor).copy(alpha = 0.6f))
                    Text("Keyboard", color = androidx.compose.ui.graphics.Color(theme.keyTextColor).copy(alpha = 0.6f))
                    Text("Preview", color = androidx.compose.ui.graphics.Color(theme.keyTextColor).copy(alpha = 0.6f))
                }
            }

            // Keyboard Keys
            AndroidView(
                factory = { ctx ->
                    HexKeyboardView(ctx).apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT
                        )
                        listener = object : HexKeyboardView.Listener {
                            override fun onChar(text: String) {}
                            override fun onDelete() {}
                            override fun onEnter() {}
                            override fun onLongPressSelect(char: String) {}
                            override fun onSymbolPageChange(page: String) {}
                            override fun onKeyClick(key: HexLayoutEngine.Key) {
                                onKeyClick?.invoke(key)
                            }
                        }
                    }
                },
                update = { view ->
                    if (view.keyboardTheme != theme) {
                        view.keyboardTheme = theme
                        view.drawBackground = false
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )
            
            // Bottom Margin
            val prefs by ThemeUtils.getDataStore(context).data.collectAsState(initial = emptyPreferences())
            val bottomOffset = prefs[ThemeUtils.KEYBOARD_BOTTOM_OFFSET] ?: 0
            
            if (bottomOffset > 0) {
                Spacer(modifier = Modifier.height(bottomOffset.dp))
            }
        }
    }
}
