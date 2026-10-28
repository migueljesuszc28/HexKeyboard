# Plan de Implementación: Animaciones y UI Material Expressive en HexKeyboard

Este documento analiza el estado actual del código del teclado en relación con la especificación de **Material 3 Expressive**, evalúa si las animaciones del teclado son auténticamente Expressive o imitaciones, y define un plan paso a paso para actualizar la interfaz y animaciones del teclado (excluyendo los componentes que ya emplean Liquid Glass).

---

## 1. Análisis de Material Expressive en las Pantallas de Ajustes

En `SettingsActivity.kt` y `LiquidGlassSettingsActivity.kt`, Material Expressive se encuentra implementado con APIs nativas y patrones de diseño modernos:

- **Tema Expresivo**: Encapsulación dentro de `MaterialExpressiveTheme` con la anotación `@OptIn(ExperimentalMaterial3ExpressiveApi::class)`.
- **Morphing de Formas Interactivas (`rememberMorphingShape`)**: Uso de resortes espaciales rápidos (`spring(dampingRatio = 0.6f, stiffness = 800f)`) para animar el radio de esquina de los elementos según su estado táctil (de estado reposo `24.dp` a estado presionado `14.dp`).
- **Contenedores Agraciados y Jerarquía Expresiva**: Uso de `SettingsCardContainer` con esquinas redondeadas continuas (`28.dp`), separadores internos (`2.dp`), `LargeTopAppBar` con comportamiento de colapso, e iconos tonales rellenados (`FilledTonalIconButton`).

---

## 2. Diagnóstico del Teclado: ¿Animaciones Reales o Imitación?

Tras auditar `KeyboardScreen.kt`, `KeyboardMainSection.kt`, `SuggestionsBar.kt`, `HexKeyboardView.kt` y los paneles del teclado, el diagnóstico es:

> **Dictamen**: La mayoría de las animaciones del teclado **imitaban el comportamiento expresivo usando curvas manuales o animaciones tradicionales (`tween`)**, sin estar conectadas formalmente al sistema o especificación de **Material 3 Expressive (`MotionScheme`)**.

### Desglose por componente:

| Componente | Animación Actual | Evaluación |
| :--- | :--- | :--- |
| **Transición de Paneles (`PanelsSection`)** | `tween(220)` y `tween(180)` lineales/cúbicos en `AnimatedContent`. | **Animación tradicional M2/M3 (No Expressive)**. Carece de dinámica de resorte. |
| **Capa 2 Panel Completo (`KeyboardScreen`)** | `spring(dampingRatio = 0.82f, stiffness = 380f)` con `scaleIn(0.93f)`. | **Imitación manual**. Aunque usa resortes, son constantes arbitrarias sin un estándar `MotionScheme`. |
| **Menú de Opciones del Portapapeles (`ClipboardOptionsMenuOverlay`)** | `scaleIn(0.80f)` con `Spring.DampingRatioMediumBouncy` y `Spring.StiffnessMediumLow`. | **Imitación parcial**. Faltan transformaciones de contenedor expresivas y morphing de bordes. |
| **Pulsación de Teclas (`HexKeyboardView` / `bounceClick`)** | `SpringAnimation` de Android y `bounceClick` con `StiffnessHigh`. | **Feedback táctil elástico**, pero desalineado de los tokens espaciales de M3 Expressive. |
| **Barra de Sugerencias (`SuggestionsBarSection`)** | `fadeIn() + expandVertically()` básico. | **Sin animación espacial de distribución**. El cambio de sugerencias salta sin morphing expresivo. |

---

## 3. Elementos del Teclado a Actualizar a Material Expressive

*(Excluyendo los botones y barras que utilizan Liquid Glass)*

1. **Menú de Opciones del Portapapeles (`ClipboardOptionsMenuOverlay`)**:
   - Rediseño hacia un menú flotante expresivo con expansión de contenedor (*Container Transform Spring*).
   - Aplicar `rememberMorphingShape` en las filas de acciones (Pegar, Fijar, Editar, Borrar).
   - Tarjeta de vista previa de texto/imagen con superficie tonal expresiva.
2. **Barra de Sugerencias y Cápsulas (`SuggestionsBar.kt` & `KeyboardComponents.kt`)**:
   - Transición espacial continua entre candidatos con `animateContentSize(spring(...))`.
   - Cápsulas de accesos directos y sugerencias con morphing de esquinas al hacer clic/pulsación larga.
3. **Panel de Portapapeles (`ClipboardPanel.kt`)**:
   - Tarjetas del grid escalonado con morphing interactivo de bordes (`rememberMorphingShape`).
   - Badges de elemento fijado y vista previa de imagen con chips tonales expresivos.
   - Estado vacío rediseñado con tipografía e ícono expresivo.
4. **Panel de Funciones (`FunctionsPanel.kt`)**:
   - Convertir los ítems del grid en tarjetas de acción tonales expresivas con respuesta de escala y morphing.
5. **Paneles de Credenciales y Lenguajes (`CredentialsPanel.kt`, `LanguagePanel.kt`)**:
   - Agrupar ítems en tarjetas de lista expresivas idénticas a las de `SettingsActivity.kt` (`GroupCornerRadius` = `28.dp`, separadores integrados, selecciones redondeadas).
6. **Encabezados y Barras de Búsqueda de Paneles (`PanelHeader`, `EmojiSearchBar`, `CredentialsSearchBar`)**:
   - Barra de búsqueda con morphing suave de radio al enfocar/escribir y botones de acción tonales.
7. **Escheme Unificado de Movimiento (`MotionScheme` en `KeyboardScreen.kt`)**:
   - Reemplazar todas las duraciones fijas `tween()` por especificadores de resorte espaciales (`fastSpatialSpec`, `fastEffectsSpec`).

---

## 4. Plan de Implementación Paso a Paso

### Fase 1: Estandarización del Esquema de Movimiento (Motion Scheme)
- [ ] Envolver el contenido de `KeyboardScreen` en `MaterialExpressiveTheme`.
- [ ] Reemplazar las animaciones de `PanelsSection` en `KeyboardMainSection.kt` (`tween(220)`) por animaciones con resortes espaciales expresivos (`spring(dampingRatio = 0.75f, stiffness = 600f)`).
- [ ] Estandarizar la transición del panel a pantalla completa en `KeyboardScreen.kt` utilizando `MotionScheme.expressive()`.

### Fase 2: Rediseño del Menú Flotante de Opciones de Portapapeles
- [ ] Actualizar `ClipboardOptionsMenuOverlay` en `KeyboardScreen.kt`.
- [ ] Implementar `rememberMorphingShape` en las filas `ClipboardMenuRow` para que las esquinas se deformen suavemente al ser presionadas.
- [ ] Aplicar tarjetas tonales expresivas con sombras dinámicas y divisores suaves.

### Fase 3: Expresividad en Paneles de Contenido (Portapapeles, Funciones, Credenciales, Idiomas)
- [ ] **`ClipboardPanel.kt`**: Aplicar `rememberMorphingShape` en las tarjetas de recortes y rediseñar los badges.
- [ ] **`FunctionsPanel.kt`**: Convertir las celdas en celdas tonales expresivas con respuesta táctil elástica y bordes morphing.
- [ ] **`CredentialsPanel.kt` y `LanguagePanel.kt`**: Agrupar los ítems en contenedores expresivos tipo `SettingsCardContainer` con esquinas de `28.dp`.

### Fase 4: Animación Espacial en Barra de Sugerencias
- [ ] Agregar morphing de tamaño con `animateContentSize` en `SuggestionsBarSection`.
- [ ] Aplicar animaciones de resorte al aparecer o cambiar las 3 cápsulas de sugerencias.

---

## 5. Plan de Verificación

### Pruebas Automatizadas y Compilación
- Ejecutar `./gradlew assembleDebug` para garantizar cero errores de sintaxis o tipo con las APIs de Compose Material 3 Expressive.

### Verificación Visual en Dispositivo / Emulador
- Verificar la suavidad de las transiciones al abrir el portapapeles, cambiar de panel y desplegar el menú contextual de opciones.
- Comprobar que la respuesta táctil en el menú de portapapeles y las sugerencias responda con el estiramiento y morphing característico de Material Expressive.
