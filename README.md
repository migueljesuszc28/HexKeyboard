<p align="center">
  <img src="art/logo.png" width="128" height="128" alt="HexKeyboard Logo" />
</p>

<h1 align="center">HexKeyboard</h1>

<p align="center">
  Un teclado virtual nativo para Android con disposición hexagonal ergonómica, predicción local y personalización avanzada.
</p>

<p align="center">
  <!-- Reemplaza TU_USUARIO por tu nombre de usuario en GitHub -->
  <a href="https://github.com/migueljesuszc28/hexkeyboard/releases/latest">
    <img src="https://img.shields.io/github/v/release/migueljesuszc28/hexkeyboard?style=flat-square&color=blue" alt="Latest Release" />
  </a>
  <img src="https://img.shields.io/badge/Platform-Android-brightgreen?style=flat-square&logo=android" alt="Platform Android" />
  <img src="https://img.shields.io/badge/Kotlin-Jetpack%20Compose-purple?style=flat-square&logo=kotlin" alt="Kotlin & Compose" />
  <img src="https://img.shields.io/badge/License-MIT-green?style=flat-square" alt="License" />
</p>

---

## 📱 Capturas de Pantalla

<p align="center">
  <img src="art/screenshots/keyboard_main.png" width="28%" alt="Vista Principal Hexagonal" />
  &nbsp;&nbsp;&nbsp;&nbsp;
  <img src="art/screenshots/emoji_panel.png" width="28%" alt="Panel de Emojis" />
  &nbsp;&nbsp;&nbsp;&nbsp;
  <img src="art/screenshots/settings.png" width="28%" alt="Ajustes y Personalización" />
</p>

---

## ✨ Características Principales

- ⬡ **Distribución Hexagonal Ergonómica:** Diseño geométrico optimizado para facilitar la escritura con pulgares y reducir errores de pulsación.
- ⚡ **Desarrollado en Jetpack Compose:** Interfaz fluida, moderna y reactiva construida completamente en Kotlin nativo.
- 🔒 **Privacidad Total (Sin Conexión):** Todo el procesamiento de texto, predicciones y aprendizaje de palabras ocurre estrictamente de forma local en tu dispositivo, sin telemetría ni acceso a internet.
- 💡 **Barra de Candidatos y Predicción:** Motor de sugerencias contextuales rápido e integrado.
- 😊 **Panel de Emojis Dedicado:** Navegación organizada por categorías y selector rápido.
- 🎨 **Soporte de Temas Visuales:** Adaptable a modo claro, oscuro y personalización dinámica del estilo de las teclas.

---

## 📥 Descarga e Instalación

Puedes descargar el archivo `.apk` de la versión estable más reciente directamente desde la sección de lanzamientos:

<p align="center">
  <a href="https://github.com/migueljesuszc28/hexkeyboard/releases/latest">
    <img src="https://img.shields.io/badge/Descargar%20APK-Última%20Versión-success?style=for-the-badge&logo=android" alt="Descargar APK" />
  </a>
</p>

### Pasos para activar el teclado:
1. Instala el archivo `app-release.apk` en tu dispositivo.
2. Abre **Ajustes** > **Sistema** > **Idiomas y entrada** > **Teclado en pantalla**.
3. Activa **HexKeyboard**.
4. Selecciónalo como método de entrada predeterminado.

---

## 🛠️ Tecnologías y Requisitos

- **Lenguaje:** Kotlin
- **UI:** Jetpack Compose (Compose Multiplatform / Android Toolkit)
- **Min SDK:** Android 8.0 (API 26) o superior
- **Target SDK:** Android 14+ (API 34+)
- **Herramienta de compilación:** Gradle con Kotlin DSL (`build.gradle.kts`)

---

## 💻 Compilación Local

Si deseas clonar el proyecto y compilarlo tú mismo en Android Studio:

```bash
# 1. Clonar el repositorio
git clone [https://github.com/TU_USUARIO/hexkeyboard.git](https://github.com/TU_USUARIO/hexkeyboard.git)

# 2. Entrar a la carpeta
cd hexkeyboard

# 3. Compilar APK de depuración
./gradlew assembleDebug
