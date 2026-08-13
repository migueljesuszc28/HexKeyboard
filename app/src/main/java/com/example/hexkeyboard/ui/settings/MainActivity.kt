package com.example.hexkeyboard.ui.settings

import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.hexkeyboard.R
import com.example.hexkeyboard.ui.theme.HexKeyboardTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        setContent {
            HexKeyboardTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    MainScreen(
                        modifier = Modifier.padding(innerPadding),
                        onEnableClick = {
                            startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
                        },
                        onSelectClick = {
                            val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
                            imm.showInputMethodPicker()
                        },
                        onSettingsClick = {
                            startActivity(Intent(this@MainActivity, SettingsActivity::class.java))
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun MainScreen(
    modifier: Modifier = Modifier,
    onEnableClick: () -> Unit,
    onSelectClick: () -> Unit,
    onSettingsClick: () -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top
    ) {
        Spacer(modifier = Modifier.height(32.dp))
        
//        Text(
//            text = "⬡",
//            fontSize = 84.sp,
//            color = MaterialTheme.colorScheme.primary,
//            modifier = Modifier.padding(bottom = 12.dp)
//        )

        Icon(
            painter = painterResource(id = R.drawable.ic_hexagon),
            contentDescription = "icono de hexágono",
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .padding(bottom = 12.dp)
                .size(84.dp)
        )

        Text(
            text = "Hex Keyboard",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(bottom = 48.dp)
        )

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 32.dp),
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Configuración rápida",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 4.dp)
                )

                ButtonWithIcon(
                    text = "1 · Habilitar teclado",
                    icon = Icons.Default.Settings,
                    onClick = onEnableClick,
                    isTonal = true
                )

                ButtonWithIcon(
                    text = "2 · Activar Hex",
                    icon = Icons.Default.Keyboard,
                    onClick = onSelectClick,
                    isTonal = true
                )

                ButtonWithIcon(
                    text = "3 · Ajustes avanzados",
                    icon = Icons.Default.Palette,
                    onClick = onSettingsClick,
                    isTonal = false
                )
            }
        }

        var textState by remember { mutableStateOf("") }

        OutlinedTextField(
            value = textState,
            onValueChange = { textState = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.test_keyboard_hint)) },
            shape = MaterialTheme.shapes.large,
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = null
                )
            }
        )
    }
}

@Preview(name = "Light Mode", showBackground = true, showSystemUi = true)
@Preview(name = "Dark Mode", uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true, showSystemUi = true)
@Composable
fun PreviewMainScreen() {
    HexKeyboardTheme {
        MainScreen(
            onEnableClick = {},
            onSelectClick = {},
            onSettingsClick = {}
        )
    }
}

@Composable
fun ButtonWithIcon(
    text: String,
    icon: ImageVector,
    onClick: () -> Unit,
    isTonal: Boolean
) {
    val modifier = Modifier
        .fillMaxWidth()
        .height(64.dp)
        
    if (isTonal) {
        FilledTonalButton(
            onClick = onClick,
            modifier = modifier,
            contentPadding = PaddingValues(horizontal = 24.dp)
        ) {
            ButtonContent(text, icon)
        }
    } else {
        Button(
            onClick = onClick,
            modifier = modifier,
            contentPadding = PaddingValues(horizontal = 24.dp)
        ) {
            ButtonContent(text, icon)
        }
    }
}

@Composable
fun ButtonContent(text: String, icon: ImageVector) {
    Icon(
        imageVector = icon,
        contentDescription = null,
        modifier = Modifier.size(ButtonDefaults.IconSize)
    )
    Spacer(Modifier.size(ButtonDefaults.IconSpacing))
    Text(text)
}
