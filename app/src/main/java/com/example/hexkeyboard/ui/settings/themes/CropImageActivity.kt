package com.example.hexkeyboard.ui.settings.themes

import android.content.Context
import android.graphics.Bitmap
import android.graphics.RectF
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.hexkeyboard.data.repository.ThemeUtils
import com.example.hexkeyboard.data.repository.ThemeUtils.enableMaxRefreshRate
import com.example.hexkeyboard.ui.settings.rememberMorphingShape
import com.example.hexkeyboard.ui.theme.HexKeyboardTheme
import java.io.File
import java.io.FileOutputStream

class CropImageActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableMaxRefreshRate()
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )

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

    val suggestionsHeightDp = 46f
    val bottomOffsetDp by produceState(initialValue = 0f, context) {
        ThemeUtils.getDataStore(context).data.collect { prefs ->
            value = (prefs[ThemeUtils.KEYBOARD_BOTTOM_OFFSET] ?: 0).toFloat()
        }
    }

    // Material 3 Expressive Morphing Shapes
    val backInteraction = remember { MutableInteractionSource() }
    val backShape = rememberMorphingShape(
        interactionSource = backInteraction,
        restCorner = 24.dp,
        pressedCorner = 14.dp
    )

    val doneInteraction = remember { MutableInteractionSource() }
    val doneShape = rememberMorphingShape(
        interactionSource = doneInteraction,
        restCorner = 20.dp,
        pressedCorner = 10.dp
    )

    val topBarMaskBrush = remember {
        Brush.verticalGradient(
            colorStops = arrayOf(
                0.0f to Color.Black.copy(alpha = 0.85f),
                0.7f to Color.Black.copy(alpha = 0.40f),
                1.0f to Color.Transparent
            )
        )
    }

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

            // Crop Frame in the middle (Representing the keyboard area with M3 Expressive styling)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(keyboardAspectRatio)
                    .align(Alignment.Center)
                    .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                    .onGloballyPositioned { layoutCoordinates ->
                        val position = layoutCoordinates.positionInRoot()
                        val size = layoutCoordinates.size
                        cropImageViewInstance?.setCropRect(
                            RectF(
                                position.x,
                                position.y,
                                position.x + size.width,
                                position.y + size.height
                            )
                        )
                    }
                    .border(
                        BorderStroke(2.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.90f)),
                        RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
                    )
            ) {
                // Visual guides for Suggestions Bar and Bottom Margin
                Column(modifier = Modifier.fillMaxSize()) {
                    // Suggestions Bar area
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(suggestionsHeightDp.dp)
                            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f))
                            .border(width = 1.dp, color = MaterialTheme.colorScheme.primary.copy(alpha = 0.40f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Tune,
                                contentDescription = null,
                                tint = Color.White.copy(alpha = 0.9f),
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = "Barra de sugerencias",
                                color = Color.White.copy(alpha = 0.9f),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.weight(1f))

                    // Bottom Margin area
                    if (bottomOffsetDp > 0) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(bottomOffsetDp.dp)
                                .background(Color.White.copy(alpha = 0.12f))
                                .border(width = 1.dp, color = Color.White.copy(alpha = 0.25f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Margen inferior",
                                color = Color.White.copy(alpha = 0.75f),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }

            // Central instructions (Glass pill badge above frame)
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = Color.Black.copy(alpha = 0.65f),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.20f)),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(top = 72.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.TouchApp,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "Pellizca para escalar • Arrastra para reencuadrar",
                        color = Color.White,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            // Top Bar Expresiva con degradado protector
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(brush = topBarMaskBrush)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    FilledTonalIconButton(
                        onClick = onCancel,
                        shape = backShape,
                        interactionSource = backInteraction,
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = Color.White.copy(alpha = 0.20f),
                            contentColor = Color.White
                        ),
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Cancelar")
                    }

                    Text(
                        text = "Recortar Imagen",
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
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
                        shape = doneShape,
                        interactionSource = doneInteraction,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = "Siguiente",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

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
