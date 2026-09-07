# Implementación de Gestor de Contraseñas y Correos

El objetivo es añadir una función al teclado que permita al usuario guardar, gestionar y utilizar rápidamente correos electrónicos y contraseñas.

> [!NOTE]
> **Limitación de Google Password Manager:** Google no ofrece una API pública para que aplicaciones de terceros (como teclados) extraigan o sincronicen contraseñas directamente por motivos de seguridad. Sin embargo, la alternativa estándar y segura es **exportar las contraseñas desde Google Password Manager a un archivo CSV**, el cual nuestro teclado sí podrá leer e importar.

## User Review Required

- **Seguridad:** Las contraseñas se almacenarán encriptadas en el dispositivo utilizando `EncryptedSharedPreferences` (Android Keystore).
- **Acceso:** ¿Te gustaría que el teclado solicite huella dactilar/PIN (BiometricPrompt) antes de insertar una contraseña, o prefieres acceso directo para mayor rapidez? Por ahora el plan contempla acceso directo desde el panel del teclado.

## Proposed Changes

---

### Capa de Datos y Seguridad

#### [NEW] `app/src/main/java/com/example/hexkeyboard/data/repository/CredentialsManager.kt`
- Lógica para inicializar `EncryptedSharedPreferences`.
- Métodos CRUD (Crear, Leer, Actualizar, Borrar) para `CredentialItem` (título, usuario/correo, contraseña).
- Lógica de importación de CSV (parseando el formato estándar de Chrome/Google: `name,url,username,password`).

#### [NEW] `app/src/main/java/com/example/hexkeyboard/data/model/CredentialItem.kt`
- Clase de datos que representa una credencial.

---

### Interfaz de Ajustes (Settings)

#### [MODIFY] `app/src/main/java/com/example/hexkeyboard/ui/settings/SettingsActivity.kt`
- Nuevo apartado en los ajustes para "Gestor de Credenciales" o "Cuentas y Contraseñas".

#### [NEW] `app/src/main/java/com/example/hexkeyboard/ui/settings/CredentialsSettingsActivity.kt`
- Pantalla para listar, añadir manualmente, editar y eliminar contraseñas/correos.
- Botón para "Importar desde CSV", que abrirá el selector de archivos del sistema (SAF).

---

### Interfaz del Teclado (Paneles)

#### [NEW] `app/src/main/java/com/example/hexkeyboard/ui/keyboard/panels/CredentialsPanel.kt`
- Nuevo panel deslizable similar al `ClipboardPanel` o `EmojiPanel`.
- Mostrará una lista visual de los sitios/correos guardados.
- Al tocar un elemento, permitirá elegir si se desea insertar el correo o la contraseña en el campo de texto actual.

#### [MODIFY] `app/src/main/java/com/example/hexkeyboard/viewmodel/KeyboardViewModel.kt`
- Añadir flujo (`StateFlow`) para cargar las credenciales desde `CredentialsManager`.
- Exponer métodos para cambiar a la vista `credentials`.

#### [MODIFY] `app/src/main/java/com/example/hexkeyboard/ui/keyboard/KeyboardMainSection.kt`
- Conectar el estado `"credentials"` en el `AnimatedContent` para renderizar `CredentialsPanel`.

#### [MODIFY] `app/src/main/java/com/example/hexkeyboard/ui/keyboard/SuggestionsBar.kt` o `FunctionsPanel.kt`
- Añadir un botón (ej. ícono de llave o candado) para acceder rápidamente al panel de credenciales desde el teclado.

## Verification Plan

### Manual Verification
1. Exportar un archivo CSV de prueba (con datos dummy) desde Chrome o Google Password Manager.
2. Entrar a los Ajustes de HexKeyboard -> Cuentas y Contraseñas -> Importar CSV.
3. Verificar que los datos se parseen y muestren correctamente en la lista.
4. Abrir el teclado en un campo de texto, acceder al panel de credenciales y probar la inserción del correo y de la contraseña.
5. Verificar que al cerrar la app, los datos persistan de forma encriptada.