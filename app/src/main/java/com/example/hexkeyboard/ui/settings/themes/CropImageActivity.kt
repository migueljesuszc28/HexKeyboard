package com.example.hexkeyboard.ui.settings.themes

import android.content.Context
import android.graphics.Bitmap
import android.graphics.RectF
import android.net.Uri
import android.os.Bundle
import android.preference.PreferenceManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.hexkeyboard.data.repository.ThemeUtils
import com.example.hexkeyboard.ui.theme.HexKeyboardTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.example.hexkeyboard.data.repository.ThemeUtils.enableMaxRefreshRate
import java.io.File
import java.io.FileOutputStream

class CropImageActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableMaxRefreshRate()
        enableEdgeToEdge()

        val imageUriString = intent.getStringExtra("image_uri")
        if (imageUriString == null) {
            finish()
            return
        }
        val imageUri = Uri.parse(imageUriString)

        setContent {
            HexKeyboardTheme {
                CropImageScreen(
                    imageUri = imageUri,
                    onCancel = { finish() },
                    onDone = { croppedUri ->
                        intent.putExtra("cropped_uri", croppedUri.toString())
                        setResult(RESULT_OK, intent)
                        finish()
                    }
                )
            }
        }
    }
}

@Composable
fun CropImageScreen(
    imageUri: Uri,
    onCancel: () -> Unit,
    onDone: (Uri) -> Unit
) {
    val context = LocalContext.current
    var cropImageViewInstance by remember { mutableStateOf<CropImageView?>(null) }
    
    val keyboardAspectRatio by produceState(initialValue = 1.0f, context) {
        value = ThemeUtils.getKeyboardAspectRatio(context)
    }
    val density = LocalContext.current.resources.displayMetrics.density
    val screenWidthDp = LocalContext.current.resources.displayMetrics.widthPixels / density
    
    // Alturas en DP para dibujar la silueta
    val suggestionsHeightDp = 46f
    val bottomOffsetDp by produceState(initialValue = 0f, context) {
        ThemeUtils.getDataStore(context).data.collect { prefs ->
            value = (prefs[ThemeUtils.KEYBOARD_BOTTOM_OFFSET] ?: 0).toFloat()
        }
    }

    // GBoard-like Crop: Image moves, fixed frame in center
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color.Black
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Main Crop View (The image)
            AndroidView(
                factory = { ctx ->
                    CropImageView(ctx).apply {
                        setImageUri(imageUri)
                        cropImageViewInstance = this
                    }
                },
                modifier = Modifier.fillMaxSize()
            )

            // Crop Frame in the middle (Representing the keyboard area)
            Box(
                modifier = Modifier
                    .fillMaxWidth() // GBoard-style: full width
                    .aspectRatio(keyboardAspectRatio) // Usar el aspect ratio real del teclado
                    .align(Alignment.Center)
                    .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)) // Redondeo superior
                    .onGloballyPositioned { layoutCoordinates ->
                        // Calculate its position relative to the root to set the crop rect in the view
                        val position = layoutCoordinates.positionInRoot()
                        val size = layoutCoordinates.size
                        cropImageViewInstance?.setCropRect(RectF(
                            position.x,
                            position.y,
                            position.x + size.width,
                            position.y + size.height
                        ))
                    }
                    .border(BorderStroke(2.dp, Color.White.copy(alpha = 0.8f)), RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
            ) {
                // Visual guides for Suggestions Bar and Bottom Margin
                Column(modifier = Modifier.fillMaxSize()) {
                    // Suggestions Bar area
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(suggestionsHeightDp.dp)
                            .background(Color.White.copy(alpha = 0.1f))
                            .border(width = 0.5.dp, color = Color.White.copy(alpha = 0.3f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Barra de sugerencias", color = Color.White.copy(alpha = 0.5f), fontSize = 10.sp)
                    }
                    
                    Spacer(modifier = Modifier.weight(1f))
                    
                    // Bottom Margin area
                    if (bottomOffsetDp > 0) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(bottomOffsetDp.dp)
                                .background(Color.White.copy(alpha = 0.1f))
                                .border(width = 0.5.dp, color = Color.White.copy(alpha = 0.3f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("Margen inferior", color = Color.White.copy(alpha = 0.5f), fontSize = 10.sp)
                        }
                    }
                }
            }

            // Custom GBoard-style Top Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 4.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                FilledTonalIconButton(
                    onClick = onCancel,
                    shape = CircleShape,
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = Color.White.copy(alpha = 0.2f), // Fondo sutil
                        contentColor = Color.White
                    )
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Cancelar")
                }
                Text(
                    text = "Ajustar imagen",
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Normal
                )
                Button(
                    onClick = {
                        val cropped = cropImageViewInstance?.getCroppedBitmap()
                        if (cropped != null) {
                            val savedUri = saveBitmap(context, cropped)
                            if (savedUri != null) {
                                onDone(savedUri)
                            }
                        }
                    },
                    // Definimos esquinas redondeadas (forma de píldora)
                    shape = RoundedCornerShape(24.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.White.copy(alpha = 0.2f), // Fondo sutil
                        contentColor = Color.White
                    ),
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp)
                ) {
                    Text(
                        "SIGUIENTE",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                }
            }

            // Central instructions (above the frame)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .padding(top = 100.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    "Pellizca para ampliar",
                    color = Color.White.copy(alpha = 0.9f),
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    "Arrastra para mover",
                    color = Color.White.copy(alpha = 0.9f),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

// Add extension for positionInRoot if needed (it was used before)
// In Compose 1.4+ it's in androidx.compose.ui.layout
private fun LayoutCoordinates.positionInRoot(): Offset {
    return this.localToRoot(Offset.Zero)
}

private fun saveBitmap(context: Context, bitmap: Bitmap): Uri? {
    val file = File(context.filesDir, "keyboard_bg_${System.currentTimeMillis()}.jpg")
    return try {
        val out = FileOutputStream(file)
        bitmap.compress(Bitmap.CompressFormat.JPEG, 95, out)
        out.flush()
        out.close()
        Uri.fromFile(file)
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}

@Preview(name = "Light Mode", showBackground = true, showSystemUi = true)
@Composable
fun PreviewCropImageScreen() {
    HexKeyboardTheme {
        CropImageScreen(
            imageUri = Uri.EMPTY,
            onCancel = {},
            onDone = {}
        )
    }
}
