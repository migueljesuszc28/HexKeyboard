package com.example.hexkeyboard.ui.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import com.example.hexkeyboard.data.model.CredentialItem
import com.example.hexkeyboard.data.repository.CredentialsManager
import com.example.hexkeyboard.data.repository.ThemeUtils.enableMaxRefreshRate
import com.example.hexkeyboard.ui.component.ExpressiveLoadingScreen
import com.example.hexkeyboard.ui.theme.HexKeyboardTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.UUID
import kotlin.time.Duration.Companion.milliseconds

class CredentialsSettingsActivity : FragmentActivity() {

    private lateinit var importCsvLauncher: ActivityResultLauncher<Array<String>>
    private var credentialsState = mutableStateOf<List<CredentialItem>>(emptyList())
    private var isLoadingState = mutableStateOf(false)
    private var isAuthenticatedState = mutableStateOf(false)

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableMaxRefreshRate()
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
        )

        importCsvLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
            if (uri != null) {
                isLoadingState.value = true
                lifecycleScope.launch {
                    val result = CredentialsManager.importFromCsv(this@CredentialsSettingsActivity, uri)
                    result.fold(
                        onSuccess = { count ->
                            Toast.makeText(this@CredentialsSettingsActivity, "Se importaron $count credenciales", Toast.LENGTH_SHORT).show()
                        },
                        onFailure = { err ->
                            Toast.makeText(this@CredentialsSettingsActivity, "Error al importar: ${err.localizedMessage}", Toast.LENGTH_LONG).show()
                        }
                    )
                    val list = CredentialsManager.getAllCredentials(this@CredentialsSettingsActivity)
                    credentialsState.value = list
                    isLoadingState.value = false
                }
            }
        }

        loadCredentials()
        checkAndAuthenticate()

        setContent {
            HexKeyboardTheme {
                var showAddDialog by remember { mutableStateOf(false) }
                var editingCredential by remember { mutableStateOf<CredentialItem?>(null) }
                var searchQuery by remember { mutableStateOf("") }
                var isSearchActive by remember { mutableStateOf(false) }

                val surfaceColor = MaterialTheme.colorScheme.surface
                val isDark = surfaceColor.luminance() < 0.5f

                val topBarMaskBrush = remember(isDark, surfaceColor) {
                    if (isDark) {
                        Brush.verticalGradient(
                            colorStops = arrayOf(
                                0.0f to Color.Black.copy(alpha = 0.55f),
                                0.6f to Color.Black.copy(alpha = 0.20f),
                                1.0f to Color.Transparent
                            )
                        )
                    } else {
                        Brush.verticalGradient(
                            colorStops = arrayOf(
                                0.0f to surfaceColor.copy(alpha = 0.92f),
                                0.6f to surfaceColor.copy(alpha = 0.50f),
                                1.0f to Color.Transparent
                            )
                        )
                    }
                }

                val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
                val isAuthenticated = isAuthenticatedState.value
                val list = credentialsState.value
                val filteredList = remember(list, searchQuery) {
                    if (searchQuery.isBlank()) list
                    else list.filter {
                        it.title.contains(searchQuery, true) || it.username.contains(searchQuery, true)
                    }
                }

                Scaffold(
                    modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
                    topBar = {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(brush = topBarMaskBrush)
                        ) {
                            Column {
                                TopAppBar(
                                    title = { Text("Cuentas y Contraseñas", fontWeight = FontWeight.Bold) },
                                    navigationIcon = {
                                        FilledTonalIconButton(
                                            onClick = { finish() },
                                            shape = CircleShape
                                        ) {
                                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                                        }
                                    },
                                    actions = {
                                        if (isAuthenticated) {
                                            FilledTonalIconButton(
                                                onClick = {
                                                    importCsvLauncher.launch(arrayOf("text/comma-separated-values", "text/csv", "application/csv", "text/plain"))
                                                },
                                                shape = CircleShape
                                            ) {
                                                Icon(Icons.Default.UploadFile, contentDescription = "Importar CSV Google")
                                            }
                                            Spacer(Modifier.width(8.dp))
                                            FilledTonalIconButton(
                                                onClick = {
                                                    isSearchActive = !isSearchActive
                                                    if (!isSearchActive) searchQuery = ""
                                                },
                                                shape = CircleShape
                                            ) {
                                                Icon(
                                                    if (isSearchActive) Icons.Default.Close else Icons.Default.Search,
                                                    contentDescription = "Buscar"
                                                )
                                            }
                                        }
                                    },
                                    colors = TopAppBarDefaults.topAppBarColors(
                                        containerColor = Color.Transparent,
                                        scrolledContainerColor = Color.Transparent
                                    ),
                                    scrollBehavior = scrollBehavior
                                )

                                AnimatedVisibility(
                                    visible = isAuthenticated && isSearchActive,
                                    enter = expandVertically() + fadeIn(),
                                    exit = shrinkVertically() + fadeOut()
                                ) {
                                    TextField(
                                        value = searchQuery,
                                        onValueChange = { searchQuery = it },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 16.dp, vertical = 8.dp),
                                        placeholder = { Text("Buscar sitio o correo...") },
                                        leadingIcon = { Icon(Icons.Default.Search, null) },
                                        trailingIcon = {
                                            if (searchQuery.isNotEmpty()) {
                                                IconButton(onClick = { searchQuery = "" }) {
                                                    Icon(Icons.Default.Clear, null)
                                                }
                                            }
                                        },
                                        colors = TextFieldDefaults.colors(
                                            focusedIndicatorColor = Color.Transparent,
                                            unfocusedIndicatorColor = Color.Transparent,
                                            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow
                                        ),
                                        shape = RoundedCornerShape(16.dp),
                                        singleLine = true
                                    )
                                }
                            }
                        }
                    },
                    floatingActionButton = {
                        if (isAuthenticated) {
                            FloatingActionButton(
                                onClick = { showAddDialog = true },
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary,
                                shape = CircleShape
                            ) {
                                Icon(Icons.Default.Add, contentDescription = "Añadir credencial")
                            }
                        }
                    }
                ) { padding ->
                    val requireBiometricKeyboard by CredentialsManager.getRequireBiometricKeyboardFlow(LocalContext.current).collectAsState(initial = false)

                    if (!isAuthenticated) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(padding),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.padding(24.dp)
                            ) {
                                Surface(
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    modifier = Modifier.size(72.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            Icons.Default.Lock,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                            modifier = Modifier.size(36.dp)
                                        )
                                    }
                                }
                                Spacer(Modifier.height(16.dp))
                                Text(
                                    text = "Acceso Protegido",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    text = "Autentícate con tu huella dactilar, rostro o PIN para ver tus contraseñas",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                )
                                Spacer(Modifier.height(24.dp))
                                Button(
                                    onClick = { checkAndAuthenticate() },
                                    shape = RoundedCornerShape(16.dp)
                                ) {
                                    Icon(Icons.Default.Fingerprint, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text("Desbloquear")
                                }
                            }
                        }
                    } else {
                        val isLoading = isLoadingState.value

                        AnimatedContent(
                            targetState = isLoading,
                            transitionSpec = {
                                fadeIn(animationSpec = tween(300)) togetherWith fadeOut(animationSpec = tween(300))
                            },
                            label = "credentials_loading_transition"
                        ) { loading ->
                            if (loading) {
                                ExpressiveLoadingScreen(
                                    title = "Cargando contraseñas",
                                    subtitle = "Desencriptando credenciales de forma segura...",
                                    icon = Icons.Default.Key,
                                    modifier = Modifier.padding(padding)
                                )
                            } else {
                                LazyColumn(
                                    modifier = Modifier.fillMaxSize(),
                                    contentPadding = PaddingValues(
                                        start = 16.dp,
                                        end = 16.dp,
                                        top = padding.calculateTopPadding() + 8.dp,
                                        bottom = padding.calculateBottomPadding() + 80.dp
                                    ),
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    item {
                                        Surface(
                                            modifier = Modifier.fillMaxWidth(),
                                            shape = RoundedCornerShape(20.dp),
                                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                                            tonalElevation = 1.dp
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(16.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Surface(
                                                    shape = CircleShape,
                                                    color = MaterialTheme.colorScheme.primaryContainer,
                                                    modifier = Modifier.size(36.dp)
                                                ) {
                                                    Box(contentAlignment = Alignment.Center) {
                                                        Icon(
                                                            Icons.Default.Fingerprint,
                                                            contentDescription = null,
                                                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                                            modifier = Modifier.size(20.dp)
                                                        )
                                                    }
                                                }
                                                Spacer(Modifier.width(12.dp))
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(
                                                        text = "Requerir biometría en el teclado",
                                                        fontWeight = FontWeight.Bold,
                                                        fontSize = 14.sp,
                                                        color = MaterialTheme.colorScheme.onSurface
                                                    )
                                                    Text(
                                                        text = "Solicita huella o PIN al abrir las contraseñas en el teclado",
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                                Switch(
                                                    checked = requireBiometricKeyboard,
                                                    onCheckedChange = { enabled ->
                                                        lifecycleScope.launch {
                                                            CredentialsManager.setRequireBiometricKeyboard(this@CredentialsSettingsActivity, enabled)
                                                        }
                                                    }
                                                )
                                            }
                                        }
                                    }

                                    if (list.isEmpty()) {
                                        item {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(vertical = 48.dp),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                    Icon(Icons.Default.Key, contentDescription = null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.outline)
                                                    Spacer(Modifier.height(12.dp))
                                                    Text("No hay credenciales guardadas", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.outline)
                                                    Spacer(Modifier.height(4.dp))
                                                    Text("Importa un archivo CSV de Google o añade una nueva", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outlineVariant)
                                                }
                                            }
                                        }
                                    } else if (filteredList.isEmpty()) {
                                        item {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(vertical = 32.dp),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text("No se encontraron resultados para \"$searchQuery\"", color = MaterialTheme.colorScheme.outline)
                                            }
                                        }
                                    } else {
                                        items(filteredList, key = { it.id }) { item ->
                                            CredentialCard(
                                                item = item,
                                                onEdit = { editingCredential = item },
                                                onDelete = {
                                                    lifecycleScope.launch {
                                                        CredentialsManager.deleteCredential(this@CredentialsSettingsActivity, item.id)
                                                        loadCredentials()
                                                    }
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    if (showAddDialog || editingCredential != null) {
                        CredentialDialog(
                            initialItem = editingCredential,
                            onDismiss = {
                                showAddDialog = false
                                editingCredential = null
                            },
                            onSave = { newItem ->
                                lifecycleScope.launch {
                                    CredentialsManager.saveCredential(this@CredentialsSettingsActivity, newItem)
                                    loadCredentials()
                                    showAddDialog = false
                                    editingCredential = null
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    private fun checkAndAuthenticate() {
        val biometricManager = BiometricManager.from(this)
        val authenticators = BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL

        if (biometricManager.canAuthenticate(authenticators) == BiometricManager.BIOMETRIC_SUCCESS) {
            showBiometricPrompt()
        } else {
            isAuthenticatedState.value = true
        }
    }

    private fun showBiometricPrompt() {
        val executor = ContextCompat.getMainExecutor(this)
        val biometricPrompt = BiometricPrompt(
            this,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    isAuthenticatedState.value = true
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    isAuthenticatedState.value = false
                }
            }
        )

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Acceso a Contraseñas")
            .setSubtitle("Autentícate para ver tus credenciales")
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL)
            .build()

        biometricPrompt.authenticate(promptInfo)
    }

    private fun loadCredentials() {
        lifecycleScope.launch {
            var isCompleted = false
            val loadingJob = launch {
                delay(150.milliseconds)
                if (!isCompleted) {
                    isLoadingState.value = true
                }
            }
            val list = CredentialsManager.getAllCredentials(this@CredentialsSettingsActivity)
            isCompleted = true
            loadingJob.cancel()
            credentialsState.value = list
            isLoadingState.value = false
        }
    }
}

@Composable
fun CredentialCard(
    item: CredentialItem,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val context = LocalContext.current
    var showPassword by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Default.Key,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = item.title,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = item.username,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                Row(
                    modifier = Modifier.wrapContentWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    IconButton(onClick = onEdit) {
                        Icon(Icons.Default.Edit, contentDescription = "Editar", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Default.Delete, contentDescription = "Borrar", tint = MaterialTheme.colorScheme.error)
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (showPassword) item.password else "••••••••••••",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    IconButton(onClick = { showPassword = !showPassword }) {
                        Icon(
                            if (showPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = if (showPassword) "Ocultar" else "Ver",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    IconButton(onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val clip = ClipData.newPlainText("Contraseña", item.password)
                        clipboard.setPrimaryClip(clip)
                        Toast.makeText(context, "Contraseña copiada al portapapeles", Toast.LENGTH_SHORT).show()
                    }) {
                        Icon(
                            Icons.Default.ContentCopy,
                            contentDescription = "Copiar contraseña",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun CredentialDialog(
    initialItem: CredentialItem?,
    onDismiss: () -> Unit,
    onSave: (CredentialItem) -> Unit
) {
    var title by remember { mutableStateOf(initialItem?.title ?: "") }
    var username by remember { mutableStateOf(initialItem?.username ?: "") }
    var password by remember { mutableStateOf(initialItem?.password ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initialItem == null) "Nueva Credencial" else "Editar Credencial", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text("Título / Sitio (ej. Google)") },
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = username,
                        onValueChange = { username = it },
                        label = { Text("Correo / Usuario") },
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text("Contraseña") },
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (title.isNotBlank() && username.isNotBlank() && password.isNotBlank()) {
                        onSave(
                            CredentialItem(
                                id = initialItem?.id ?: UUID.randomUUID().toString(),
                                title = title.trim(),
                                username = username.trim(),
                                password = password
                            )
                        )
                    }
                },
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Guardar")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        },
        shape = RoundedCornerShape(28.dp)
    )
}
