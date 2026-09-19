package com.example.hexkeyboard.data.repository

import android.app.Activity
import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.os.Build
import androidx.core.graphics.ColorUtils
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.core.graphics.scale
import androidx.core.graphics.toColorInt
import androidx.core.net.toUri
import androidx.compose.ui.graphics.toArgb
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.SharedPreferencesMigration
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.example.hexkeyboard.ui.component.GlassEffectConfig
import com.example.hexkeyboard.ui.component.GlassStyle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import androidx.palette.graphics.Palette
import java.io.IOException
import kotlin.math.abs

@Serializable
data class KeyboardTheme(
    val id: String = "default",
    val name: String = "Default",
    val backgroundColor: Int = Color.WHITE,
    val keyBackgroundColor: Int = Color.WHITE,
    val keyBackgroundPressedColor: Int = Color.LTGRAY,
    val keyBackgroundSpecialColor: Int = -657931, // Corresponds to #F5F5F5
    val keyStrokeColor: Int = Color.BLACK,
    val keyStrokeWidth: Float = 1.0f,
    val keyTextColor: Int = Color.BLACK,
    val keyboardIconTint: Int = Color.BLACK,
    val keyShiftActiveColor: Int? = null,
    val keyShiftInactiveColor: Int? = null,
    val deletePressedIconColor: Int? = null,
    val popupBackgroundColor: Int = Color.WHITE,
    val popupTextColor: Int = Color.BLACK,
    val popupSelectedBackgroundColor: Int = Color.BLACK,
    val popupSelectedTextColor: Int = Color.WHITE,
    val individualKeyColors: Map<String, Int> = emptyMap(),
    val backgroundImageUri: String? = null,
    val backgroundOpacity: Float = 1.0f,
    val backgroundBlur: Float = 0f,
    val keysBlur: Float = 0f,
    val parallaxEffect: Boolean = false,
    val isKeyTextColorCustom: Boolean = false,
)

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(
    name = "settings",
    produceMigrations = { context ->
        listOf(SharedPreferencesMigration(context, context.packageName + "_preferences"))
    }
)

object ThemeUtils {
    val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    // Keys
    val APP_THEME = stringPreferencesKey("app_theme")
    val KEYBOARD_THEME = stringPreferencesKey("keyboard_theme")
    val CUSTOM_THEMES = stringPreferencesKey("custom_themes")
    
    val KEYBOARD_LANGUAGE = stringPreferencesKey("keyboard_language")
    val KEYBOARD_LAYOUT_TYPE = stringPreferencesKey("keyboard_layout_type")
    val SELECTED_SKIN_TONE = stringPreferencesKey("selected_skin_tone")
    val SELECTED_GENDER_INDEX = intPreferencesKey("selected_gender_index")
    
    val CLIPBOARD_HISTORY = stringPreferencesKey("clipboard_history_json")
    val RECENT_EMOJIS = stringPreferencesKey("recent_emojis")
    
    val KEYBOARD_VIBRATION = booleanPreferencesKey("keyboard_vibration")
    val KEYBOARD_VIBRATION_INTENSITY = intPreferencesKey("keyboard_vibration_intensity")
    val KEYBOARD_SOUND = booleanPreferencesKey("keyboard_sound")
    val KEYBOARD_SOUND_VOLUME = intPreferencesKey("keyboard_sound_volume")
    
    val KEYBOARD_HEIGHT = intPreferencesKey("keyboard_height")
    val KEYBOARD_KEY_SIZE = intPreferencesKey("keyboard_key_size")
    val KEYBOARD_BOTTOM_OFFSET = intPreferencesKey("keyboard_bottom_offset")
    val RESET_ON_CLOSE = booleanPreferencesKey("reset_on_close")
    
    val CLIPBOARD_AUTO_DELETE = booleanPreferencesKey("clipboard_auto_delete")
    val CLIPBOARD_EXPIRY_HOURS = stringPreferencesKey("clipboard_expiry_hours")

    val AUTO_CAPITALIZE = booleanPreferencesKey("auto_capitalize")
    val AUTO_CORRECT = booleanPreferencesKey("auto_correct")
    val DOUBLE_SPACE_PERIOD = booleanPreferencesKey("double_space_period")
    val UNDO_CORRECTION_ON_BACKSPACE = booleanPreferencesKey("undo_correction_on_backspace")
    val SHOW_KEY_POPUP = booleanPreferencesKey("show_key_popup")
    val KEY_BOUNCE_ANIMATION = booleanPreferencesKey("key_bounce_animation")
    val SHOW_LONG_PRESS_INDICATORS = booleanPreferencesKey("show_long_press_indicators")
    val POPUP_SCALE = intPreferencesKey("popup_scale")
    val LONG_PRESS_DURATION = intPreferencesKey("long_press_duration")
    val NUMERIC_KEY_SIZE_SCALE = intPreferencesKey("numeric_key_size_scale")

    // Liquid Glass Keys
    val GLASS_GLOBAL_ENABLED = booleanPreferencesKey("glass_global_enabled")
    val GLASS_STYLE = stringPreferencesKey("glass_style")
    val GLASS_BLUR_RADIUS = floatPreferencesKey("glass_blur_radius")
    val GLASS_VIBRANCY = floatPreferencesKey("glass_vibrancy")
    val GLASS_LENS_HEIGHT = floatPreferencesKey("glass_lens_height")
    val GLASS_LENS_AMOUNT = floatPreferencesKey("glass_lens_amount")
    val GLASS_SURFACE_OPACITY = floatPreferencesKey("glass_surface_opacity")
    val GLASS_DEPTH_EFFECT = booleanPreferencesKey("glass_depth_effect")
    val GLASS_CHROMATIC_ABERRATION = booleanPreferencesKey("glass_chromatic_aberration")
    val CUSTOM_FONT_PATH = stringPreferencesKey("custom_font_path")

    fun getDataStore(context: Context) = context.dataStore

    fun Activity.enableMaxRefreshRate() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                val maxMode = display?.supportedModes?.maxByOrNull { it.refreshRate }
                if (maxMode != null) {
                    window.attributes = window.attributes.apply {
                        preferredDisplayModeId = maxMode.modeId
                    }
                }
            } catch (_: Exception) {}
        }
    }

    /**
     * Trigger manual para forzar la recomposición del tema cuando algo externo
     * al DataStore cambia (p. ej. el fondo de pantalla / Material You).
     *
     * Llama a [notifyDynamicColorsChanged] desde tu InputMethodService cuando
     * detectes un cambio de wallpaper (Intent.ACTION_WALLPAPER_CHANGED) o en
     * onConfigurationChanged(), para que el tema "m3_dynamic" se recalcule.
     */
    private val themeRefreshTrigger = MutableSharedFlow<Unit>(replay = 1).apply {
        tryEmit(Unit)
    }

    fun notifyDynamicColorsChanged() {
        themeRefreshTrigger.tryEmit(Unit)
    }

    fun getAppThemeFlow(context: Context): Flow<String> = context.dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it[APP_THEME] ?: "system" }



    fun getKeyboardThemeFlow(context: Context): Flow<KeyboardTheme> = combine(
        context.dataStore.data.catch { exception ->
            if (exception is IOException) emit(emptyPreferences()) else throw exception
        },
        themeRefreshTrigger,
    ) { prefs, _ -> prefs }
        .map { prefs ->
            val themeId = prefs[KEYBOARD_THEME] ?: "default"
            getThemeById(context, themeId, prefs)
        }

    private fun getThemeById(context: Context, themeId: String, prefs: Preferences): KeyboardTheme {
        return when (themeId) {
            "default" -> getDefaultTheme(context)
            "terminal" -> {
                val isDark = (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
                val base = getDefaultTheme(context)
                base.copy(
                    id = "terminal",
                    name = "Terminal (Tokyo Night)",
                    backgroundColor = if (isDark) "#0F111A".toColorInt() else Color.WHITE,
                    keyBackgroundColor = if (isDark) "#1A1B26".toColorInt() else "#F1F3F5".toColorInt(),
                    keyBackgroundSpecialColor = if (isDark) "#1A1B26".toColorInt() else "#F1F3F5".toColorInt(),
                    popupBackgroundColor = if (isDark) "#1A1B26".toColorInt() else "#F1F3F5".toColorInt(),
                    popupSelectedBackgroundColor = "#7AA2F7".toColorInt(),
                    popupSelectedTextColor = if (isDark) Color.BLACK else Color.WHITE,
                    keyStrokeColor = Color.TRANSPARENT,
                    keyShiftActiveColor = Color.LTGRAY,
                    keyShiftInactiveColor = if (isDark) "#1A1B26".toColorInt() else "#F1F3F5".toColorInt(),
                    deletePressedIconColor = "#F7768E".toColorInt(),
                    keyTextColor = if (isDark) "#C0CAF5".toColorInt() else Color.BLACK,
                    keyboardIconTint = "#7AA2F7".toColorInt(),
                    individualKeyColors = mapOf(
                        "ENTER" to "#7AA2F7".toColorInt(),
                        "DELETE" to "#FF3030".toColorInt()
                    )
                )
            }
            "light" -> KeyboardTheme(
                id = "light",
                name = "Claro",
                backgroundColor = Color.WHITE,
                keyBackgroundColor = Color.WHITE,
                keyBackgroundPressedColor = Color.WHITE,
                keyBackgroundSpecialColor = Color.WHITE,
                keyStrokeColor = Color.BLACK,
                keyStrokeWidth = 2.0f,
                keyTextColor = Color.BLACK,
                keyboardIconTint = Color.BLACK,
                keyShiftActiveColor = "#2196F3".toColorInt(),
                keyShiftInactiveColor = Color.WHITE,
                deletePressedIconColor = Color.RED,
                popupBackgroundColor = Color.WHITE,
                popupTextColor = Color.BLACK,
                popupSelectedBackgroundColor = Color.BLACK,
                popupSelectedTextColor = Color.WHITE,
            )
            "dark" -> KeyboardTheme(
                id = "dark",
                name = "Oscuro",
                backgroundColor = "#000000".toColorInt(),
                keyBackgroundColor = "#000000".toColorInt(), //"#1A1A1A"
                keyBackgroundPressedColor = "#000000".toColorInt(),
                keyBackgroundSpecialColor = "#000000".toColorInt(),
                keyStrokeColor = "#FFFFFF".toColorInt(),
                keyStrokeWidth = 2.0f,
                keyTextColor = Color.WHITE,
                keyboardIconTint = Color.WHITE,
                keyShiftActiveColor = "#2196F3".toColorInt(),
                keyShiftInactiveColor = "#000000".toColorInt(),
                deletePressedIconColor = Color.RED,
                popupBackgroundColor = "#000000".toColorInt(),
                popupTextColor = Color.WHITE,
                popupSelectedBackgroundColor = Color.WHITE,
                popupSelectedTextColor = Color.BLACK,
            )
            "glass" -> {
                val isDark = (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
                val glassAccent = "#2196F3".toColorInt()
                val redglassAccent = "#FF0000".toColorInt()


                if (isDark) {
                    KeyboardTheme(
                        id = "glass",
                        name = "Glass",
                        backgroundColor = Color.TRANSPARENT,
                        keyBackgroundColor = "#5C5C5C".toColorInt(),
                        keyBackgroundPressedColor = "#5C5C5C".toColorInt(),
                        keyBackgroundSpecialColor = "#5C5C5C".toColorInt(),
                        keyStrokeColor = Color.TRANSPARENT,
                        keyTextColor = Color.WHITE,
                        keyboardIconTint = Color.WHITE,
                        keyShiftActiveColor = "#FFFFFF".toColorInt(),
                        keyShiftInactiveColor = "#5C5C5C".toColorInt(),
                        popupBackgroundColor = "#5C5C5C".toColorInt(),
                        popupTextColor = Color.WHITE,
                        popupSelectedBackgroundColor = Color.BLACK,
                        popupSelectedTextColor = Color.WHITE,
                        deletePressedIconColor = Color.RED,
                        individualKeyColors = mapOf(
                            "ENTER" to glassAccent,
                            "DELETE" to redglassAccent
                        )

                    )
                } else {
                    KeyboardTheme(
                        id = "glass",
                        name = "Glass",
                        backgroundColor = Color.TRANSPARENT,        // Fondo claro semitransparente
                        keyBackgroundColor = "#FFFFFF".toColorInt(),    // Teclas blancas traslúcidas
                        keyBackgroundPressedColor = "#FFFFFF".toColorInt(),
                        keyBackgroundSpecialColor = "#FFFFFF".toColorInt(),
                        keyStrokeColor = Color.TRANSPARENT,
                        keyTextColor = Color.BLACK,
                        keyboardIconTint = Color.BLACK,
                        keyShiftActiveColor = "#000000".toColorInt(),
                        keyShiftInactiveColor = "#FFFFFF".toColorInt(),
                        popupBackgroundColor = "#FFFFFF".toColorInt(),
                        popupTextColor = Color.BLACK,
                        popupSelectedBackgroundColor = Color.BLACK,
                        popupSelectedTextColor = Color.WHITE,
                        deletePressedIconColor = Color.RED,
                        individualKeyColors = mapOf(
                            "ENTER" to glassAccent,
                            "DELETE" to redglassAccent
                        )
                    )
                }
            }
            "m3_dynamic" -> getDynamicTheme(context)
            "linux" -> KeyboardTheme(
                id = "linux",
                name = "Linux",
                backgroundColor = Color.BLACK,
                keyBackgroundColor = Color.BLACK,
                keyBackgroundPressedColor = Color.BLACK,
                keyBackgroundSpecialColor = Color.BLACK,
                keyStrokeColor = "#00FF00".toColorInt(), // Verde Neón
                keyStrokeWidth = 2.0f,
                keyTextColor = "#00FF00".toColorInt(),
                keyboardIconTint = "#00FF00".toColorInt(),
                keyShiftActiveColor = "#00FF00".toColorInt(),
                keyShiftInactiveColor = Color.BLACK,
                deletePressedIconColor = Color.RED,
                popupBackgroundColor = Color.BLACK,
                popupTextColor = "#00FF00".toColorInt(),
                popupSelectedBackgroundColor = "#00FF00".toColorInt(),
                popupSelectedTextColor = Color.BLACK
            )
            else -> {
                if (themeId.startsWith("custom_")) {
                    val customThemesJson = prefs[CUSTOM_THEMES] ?: "[]"
                    val customThemes: List<KeyboardTheme> = try {
                        json.decodeFromString(customThemesJson)
                    } catch (_: Exception) { emptyList() }
                    customThemes.find { it.id == themeId } ?: getDefaultTheme(context)
                } else {
                    getDefaultTheme(context)
                }
            }
        }
    }

    private fun getDefaultTheme(context: Context): KeyboardTheme {
        val isDark = (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        return if (isDark) {
            KeyboardTheme(
                id = "default",
                name = "Predeterminado",
                backgroundColor = Color.BLACK,
                keyBackgroundColor = "#323236".toColorInt(),
                keyBackgroundPressedColor = "#323236".toColorInt(),
                keyBackgroundSpecialColor = "#323236".toColorInt(),
                keyStrokeColor = Color.TRANSPARENT,
                keyTextColor = Color.WHITE,
                keyboardIconTint = Color.WHITE,
                keyShiftActiveColor = "#F7F2F2".toColorInt(),
                keyShiftInactiveColor = "#323236".toColorInt(),
                deletePressedIconColor = "#E31212".toColorInt(),
                popupBackgroundColor = "#323236".toColorInt(),
                popupTextColor = Color.WHITE,
                popupSelectedBackgroundColor = Color.WHITE,
                popupSelectedTextColor = Color.BLACK,
                individualKeyColors = mapOf(
                    "ENTER" to "#1976D2".toColorInt(),
                    "DELETE" to "#E31212".toColorInt(),
                    "SHIFT" to "#323236".toColorInt() // Will be updated dynamically for active state if needed, or kept as spec
                )
            )
        } else {
            KeyboardTheme(
                id = "default",
                name = "Predeterminado",
                backgroundColor = Color.WHITE,
                keyBackgroundColor = "#F1F3F5".toColorInt(),
                keyBackgroundPressedColor = "#F1F3F5".toColorInt(),
                keyBackgroundSpecialColor = "#F1F3F5".toColorInt(),
                keyStrokeColor = Color.TRANSPARENT,
                keyTextColor = Color.BLACK,
                keyboardIconTint = Color.BLACK,
                keyShiftActiveColor = Color.BLACK,
                keyShiftInactiveColor = "#F1F3F5".toColorInt(),
                deletePressedIconColor = Color.RED,
                individualKeyColors = mapOf(
                    "ENTER" to "#2196F3".toColorInt(),
                    "DELETE" to Color.RED,
                    "SHIFT" to "#F1F3F5".toColorInt()
                )
            )
        }
    }

    /**
     * Genera el tema Material You dinámico.
     */
    private fun getDynamicTheme(context: Context): KeyboardTheme {
        val isDark = (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        
        // Usamos los esquemas de Compose para obtener colores precisos de Material 3
        val colorScheme = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (isDark) dynamicDarkColorScheme(context)
            else dynamicLightColorScheme(context)
        } else {
            // Fallback si no hay soporte dinámico
            return getDefaultTheme(context)
        }

        val backgroundColor = colorScheme.surface.toArgb()
        val keyBackgroundColor = colorScheme.surfaceContainerHigh.toArgb()
        val actionkeybackground = colorScheme.secondaryContainer.toArgb()
        val keyBackgroundPressedColor = colorScheme.surfaceContainer.toArgb()
        val keyTextColor = colorScheme.onSurface.toArgb()
        val keyboardIconTint = colorScheme.onSurfaceVariant.toArgb()
        val keyShiftActiveColor = colorScheme.primary.toArgb()
        val colorOnPrimary = colorScheme.onPrimary.toArgb()

        return KeyboardTheme(
            id = "m3_dynamic",
            name = "Material 3 Dinámico",
            backgroundColor = backgroundColor,
            keyBackgroundColor = keyBackgroundColor,
            keyBackgroundPressedColor = keyBackgroundPressedColor,
            keyBackgroundSpecialColor = keyBackgroundColor,
            keyStrokeColor = Color.TRANSPARENT,
            keyTextColor = keyTextColor,
            keyboardIconTint = keyboardIconTint,
            keyShiftActiveColor = keyShiftActiveColor,
            popupBackgroundColor = keyBackgroundColor,
            popupTextColor = keyTextColor,
            popupSelectedBackgroundColor = keyShiftActiveColor,
            popupSelectedTextColor = colorOnPrimary,
            keyShiftInactiveColor = actionkeybackground,
            deletePressedIconColor = Color.RED,
            individualKeyColors = mapOf(
                "ENTER" to actionkeybackground,
                "DELETE" to actionkeybackground,
                "SHIFT" to actionkeybackground,
                "123" to actionkeybackground,
                "?123" to actionkeybackground,
                "→" to actionkeybackground,
                "FONT_PAGE" to actionkeybackground
            )
        )
    }

    suspend fun saveThemeSelection(context: Context, themeId: String) {
        context.dataStore.edit { it[KEYBOARD_THEME] = themeId }
    }

    suspend fun saveAppTheme(context: Context, theme: String) {
        context.dataStore.edit { it[APP_THEME] = theme }
    }

    fun getAllThemesFlow(context: Context): Flow<List<KeyboardTheme>> = combine(
        context.dataStore.data.catch { if (it is IOException) emit(emptyPreferences()) else throw it },
        themeRefreshTrigger
    ) { prefs, _ -> prefs }
        .map { prefs ->
            val themes = mutableListOf<KeyboardTheme>()
            themes.add(getDefaultTheme(context))
            // Re-generar los predefinidos
            themes.add(getThemeById(context, "light", prefs))
            themes.add(getThemeById(context, "dark", prefs))
            themes.add(getThemeById(context, "glass", prefs))
            themes.add(getThemeById(context, "m3_dynamic", prefs))
            themes.add(getThemeById(context, "terminal", prefs))
            themes.add(getThemeById(context, "linux", prefs))

            val customThemesJson = prefs[CUSTOM_THEMES] ?: "[]"
            val customThemes: List<KeyboardTheme> = try {
                json.decodeFromString(customThemesJson)
            } catch (_: Exception) { emptyList() }
            themes.addAll(customThemes)
            themes
        }

    suspend fun getAllThemes(context: Context): List<KeyboardTheme> = withContext(Dispatchers.IO) {
        val prefs = context.dataStore.data.first()
        val themes = mutableListOf<KeyboardTheme>()
        themes.add(getDefaultTheme(context))
        // Re-generar los predefinidos
        themes.add(getThemeById(context, "light", prefs))
        themes.add(getThemeById(context, "dark", prefs))
        themes.add(getThemeById(context, "glass", prefs))
        themes.add(getThemeById(context, "m3_dynamic", prefs))
        themes.add(getThemeById(context, "terminal", prefs))
        themes.add(getThemeById(context, "linux", prefs))

        val customThemesJson = prefs[CUSTOM_THEMES] ?: "[]"
        val customThemes: List<KeyboardTheme> = try {
            json.decodeFromString(customThemesJson)
        } catch (_: Exception) { emptyList() }
        themes.addAll(customThemes)
        themes
    }

    suspend fun saveCustomTheme(context: Context, theme: KeyboardTheme) {
        context.dataStore.edit { prefs ->
            val customThemesJson = prefs[CUSTOM_THEMES] ?: "[]"
            val customThemes: MutableList<KeyboardTheme> = try {
                json.decodeFromString<List<KeyboardTheme>>(customThemesJson).toMutableList()
            } catch (_: Exception) { mutableListOf() }

            val existingIndex = customThemes.indexOfFirst { it.id == theme.id }
            if (existingIndex >= 0) {
                customThemes[existingIndex] = theme
            } else {
                customThemes.add(theme)
            }

            prefs[CUSTOM_THEMES] = json.encodeToString(customThemes)
        }
    }

    suspend fun deleteCustomTheme(context: Context, themeId: String) {
        context.dataStore.edit { prefs ->
            val customThemesJson = prefs[CUSTOM_THEMES] ?: "[]"
            val customThemes: MutableList<KeyboardTheme> = try {
                json.decodeFromString<List<KeyboardTheme>>(customThemesJson).toMutableList()
            } catch (_: Exception) { mutableListOf() }

            customThemes.removeAll { it.id == themeId }

            prefs[CUSTOM_THEMES] = json.encodeToString(customThemes)
            if (prefs[KEYBOARD_THEME] == themeId) {
                prefs[KEYBOARD_THEME] = "default"
            }
        }
    }

    fun loadBitmapFromUri(context: Context, uriString: String): Bitmap? {
        return try {
            val uri = uriString.toUri()

            // 1. Obtener dimensiones originales
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, options)
            }

            // 2. Calcular inSampleSize para no exceder un tamaño razonable (ej. 1280px)
            val maxDim = 1280
            var inSampleSize = 1
            if ((options.outHeight > maxDim) || (options.outWidth > maxDim)) {
                val halfHeight = options.outHeight / 2
                val halfWidth = options.outWidth / 2
                while ((halfHeight / inSampleSize >= maxDim) && (halfWidth / inSampleSize >= maxDim)) {
                    inSampleSize *= 2
                }
            }

            // 3. Decodificar con el tamaño reducido
            val decodeOptions = BitmapFactory.Options().apply {
                this.inSampleSize = inSampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                BitmapFactory.decodeStream(inputStream, null, decodeOptions)
            }
        } catch (_: Exception) {
            null
        }
    }

    fun blurBitmap(bitmap: Bitmap, radius: Float): Bitmap {
        if (radius <= 0) return bitmap

        val scale = if (radius > 15) 0.3f else 0.5f
        val w = (bitmap.width * scale).toInt().coerceAtLeast(1)
        val h = (bitmap.height * scale).toInt().coerceAtLeast(1)

        val small = bitmap.scale(w, h, filter = true)
        val blurred = small.copy(Bitmap.Config.ARGB_8888, true)
        small.recycle()

        val r = (radius * scale).toInt().coerceAtLeast(1)
        stackBlur(blurred, r)

        val result = blurred.scale(width = bitmap.width, height = bitmap.height, filter = true)
        blurred.recycle()

        return result
    }

    private fun stackBlur(bitmap: Bitmap, radius: Int) {
        val w = bitmap.width
        val h = bitmap.height
        val pix = IntArray(w * h)
        bitmap.getPixels(pix, 0, w, 0, 0, w, h)

        val wm = w - 1
        val hm = h - 1
        val wh = w * h
        val div = radius + radius + 1

        val r = IntArray(wh)
        val g = IntArray(wh)
        val b = IntArray(wh)
        var rsum: Int; var gsum: Int; var bsum: Int
        var p: Int; var yp: Int; var yi: Int; var yw: Int

        val vmin = IntArray(maxOf(w, h))
        val vmax = IntArray(maxOf(w, h))

        val divSum = (radius + 1) * (radius + 1)
        val dv = IntArray(256 * divSum)
        for (i in 0 until 256 * divSum) {
            dv[i] = i / divSum
        }

        yw = 0
        yi = 0

        val stack = Array(div) { IntArray(3) }
        var stackpointer: Int; var stackstart: Int; var sir: IntArray; var rbs: Int
        val r1 = radius + 1
        var routsum: Int; var goutsum: Int; var boutsum: Int
        var rinsum: Int; var ginsum: Int; var binsum: Int

        for (y in 0 until h) {
            rinsum = 0; ginsum = 0; binsum = 0
            routsum = 0; goutsum = 0; boutsum = 0
            rsum = 0; gsum = 0; bsum = 0
            for (i in -radius..radius) {
                p = pix[yi + minOf(wm, maxOf(i, 0))]
                sir = stack[i + radius]
                sir[0] = (p shr 16) and 0xff
                sir[1] = (p shr 8) and 0xff
                sir[2] = p and 0xff
                rbs = r1 - abs(i)
                rsum += sir[0] * rbs
                gsum += sir[1] * rbs
                bsum += sir[2] * rbs
                if (i > 0) {
                    rinsum += sir[0]
                    ginsum += sir[1]
                    binsum += sir[2]
                } else {
                    routsum += sir[0]
                    goutsum += sir[1]
                    boutsum += sir[2]
                }
            }
            stackpointer = radius

            for (x in 0 until w) {
                r[yi] = dv[rsum]
                g[yi] = dv[gsum]
                b[yi] = dv[bsum]

                rsum -= routsum
                gsum -= goutsum
                bsum -= boutsum

                stackstart = stackpointer - radius + div
                sir = stack[stackstart % div]

                routsum -= sir[0]
                goutsum -= sir[1]
                boutsum -= sir[2]

                if (y == 0) {
                    vmax[x] = minOf(x + radius + 1, wm)
                }
                p = pix[yw + vmax[x]]

                sir[0] = (p shr 16) and 0xff
                sir[1] = (p shr 8) and 0xff
                sir[2] = p and 0xff

                rinsum += sir[0]
                ginsum += sir[1]
                binsum += sir[2]

                rsum += rinsum
                gsum += ginsum
                bsum += binsum

                stackpointer = (stackpointer + 1) % div
                sir = stack[stackpointer % div]

                routsum += sir[0]
                goutsum += sir[1]
                boutsum += sir[2]

                rinsum -= sir[0]
                ginsum -= sir[1]
                binsum -= sir[2]

                yi++
            }
            yw += w
        }

        for (x in 0 until w) {
            rinsum = 0; ginsum = 0; binsum = 0
            routsum = 0; goutsum = 0; boutsum = 0
            rsum = 0; gsum = 0; bsum = 0
            yp = -radius * w
            for (i in -radius..radius) {
                yi = maxOf(0, yp) + x
                sir = stack[i + radius]
                sir[0] = r[yi]
                sir[1] = g[yi]
                sir[2] = b[yi]
                rbs = r1 - abs(i)
                rsum += r[yi] * rbs
                gsum += g[yi] * rbs
                bsum += b[yi] * rbs
                if (i > 0) {
                    rinsum += sir[0]
                    ginsum += sir[1]
                    binsum += sir[2]
                } else {
                    routsum += sir[0]
                    goutsum += sir[1]
                    boutsum += sir[2]
                }
                if (i < hm) {
                    yp += w
                }
            }
            yi = x
            stackpointer = radius
            for (y in 0 until h) {
                pix[yi] = (0xff shl 24) or (dv[rsum] shl 16) or (dv[gsum] shl 8) or dv[bsum]
                rsum -= routsum
                gsum -= goutsum
                bsum -= boutsum
                stackstart = stackpointer - radius + div
                sir = stack[stackstart % div]
                routsum -= sir[0]
                goutsum -= sir[1]
                boutsum -= sir[2]
                if (x == 0) {
                    vmin[y] = minOf(y + r1, hm) * w
                }
                p = x + vmin[y]
                sir[0] = r[p]
                sir[1] = g[p]
                sir[2] = b[p]
                rinsum += sir[0]
                ginsum += sir[1]
                binsum += sir[2]
                rsum += rinsum
                gsum += ginsum
                bsum += binsum
                stackpointer = (stackpointer + 1) % div
                sir = stack[stackpointer % div]
                routsum += sir[0]
                goutsum += sir[1]
                boutsum += sir[2]
                rinsum -= sir[0]
                ginsum -= sir[1]
                binsum -= sir[2]
                yi += w
            }
        }
        bitmap.setPixels(pix, 0, w, 0, 0, w, h)
    }

    fun computeKeyboardAspectRatio(context: Context, hPref: Int = 50, ksPref: Int = 90, boPref: Int = 0): Float {
        val f = 0.7f + (hPref / 100f) * 0.6f
        val keyScale = ksPref / 100f

        val r = (1000f / 8.0f) / 1.73205f
        val rows = 4f
        val dr = r * keyScale
        val dry = dr * f
        val tp = r * 0.05f * f
        val keysHeight = tp + 2 * dry + rows * r * 1.5f * f + r * 0.15f * f

        val density = context.resources.displayMetrics.density
        val screenWidthPx = context.resources.displayMetrics.widthPixels
        val screenWidthDp = screenWidthPx / density

        val unitsPerDp = 1000f / screenWidthDp
        val suggestionsHeightUnits = 46f * unitsPerDp
        val bottomMarginUnits = boPref * unitsPerDp

        val totalHeight = keysHeight + suggestionsHeightUnits + bottomMarginUnits

        return 1000f / totalHeight
    }

    suspend fun getKeyboardAspectRatio(context: Context): Float = withContext(Dispatchers.IO) {
        val prefs = context.dataStore.data.first()
        val hPref = prefs[KEYBOARD_HEIGHT] ?: 50
        val ksPref = prefs[KEYBOARD_KEY_SIZE] ?: 90
        val boPref = prefs[KEYBOARD_BOTTOM_OFFSET] ?: 0
        computeKeyboardAspectRatio(context, hPref, ksPref, boPref)
    }

    /**
     * Genera colores dinámicos basados en un Bitmap usando Palette.
     */
    fun extractDynamicTheme(bitmap: Bitmap, baseTheme: KeyboardTheme): KeyboardTheme {
        val palette = Palette.from(bitmap).generate()
        
        val isDarkImage = ColorUtils.calculateLuminance(palette.getDominantColor(Color.GRAY)) < 0.5
        
        // Colores de Palette (vibrant, muted, etc.)
        val dominant = palette.getDominantColor(if (isDarkImage) Color.BLACK else Color.WHITE)
        val vibrant = palette.getVibrantColor(dominant)
        val lightVibrant = palette.getLightVibrantColor(vibrant)
        val darkVibrant = palette.getDarkVibrantColor(vibrant)
        
        // Decidir colores según la luminancia de la imagen
        val keyBgColor = if (isDarkImage) {
            ColorUtils.blendARGB(dominant, Color.BLACK, 0.4f)
        } else {
            ColorUtils.blendARGB(dominant, Color.WHITE, 0.6f)
        }
        
        val textColor = if (isDarkImage) Color.WHITE else Color.BLACK
        val iconTint = if (isDarkImage) lightVibrant else darkVibrant
        
        // Color para el Shift activo (usamos un tono vibrante)
        val shiftActive = if (isDarkImage) lightVibrant else vibrant
        
        // Color para el ENTER (usamos un tono contrastado)
        val enterColor = if (isDarkImage) vibrant else darkVibrant

        return baseTheme.copy(
            backgroundColor = Color.TRANSPARENT,
            keyBackgroundColor = ColorUtils.setAlphaComponent(keyBgColor, 180),
            keyBackgroundPressedColor = ColorUtils.setAlphaComponent(keyBgColor, 230),
            keyBackgroundSpecialColor = ColorUtils.setAlphaComponent(keyBgColor, 200),
            keyTextColor = textColor,
            keyboardIconTint = iconTint,
            keyShiftActiveColor = shiftActive,
            keyShiftInactiveColor = ColorUtils.setAlphaComponent(keyBgColor, 200),
            popupBackgroundColor = ColorUtils.blendARGB(keyBgColor, if (isDarkImage) Color.BLACK else Color.WHITE, 0.2f),
            popupTextColor = textColor,
            popupSelectedBackgroundColor = shiftActive,
            popupSelectedTextColor = if (ColorUtils.calculateLuminance(shiftActive) > 0.5) Color.BLACK else Color.WHITE,
            individualKeyColors = mapOf(
                "ENTER" to enterColor,
                "DELETE" to Color.parseColor("#E31212")
            ),
            isKeyTextColorCustom = true
        )
    }

    fun getGlassEffectConfigFlow(context: Context): Flow<GlassEffectConfig> = context.dataStore.data.map { prefs ->
        val enabled = prefs[GLASS_GLOBAL_ENABLED] ?: true
        val styleStr = prefs[GLASS_STYLE] ?: "LIQUID"
        val style = try { GlassStyle.valueOf(styleStr) } catch (_: Exception) { GlassStyle.LIQUID }
        val blurRadius = prefs[GLASS_BLUR_RADIUS] ?: 2f
        val vibrancy = prefs[GLASS_VIBRANCY] ?: 1.2f
        val lensHeight = prefs[GLASS_LENS_HEIGHT] ?: 0.4f
        val lensAmount = prefs[GLASS_LENS_AMOUNT] ?: 0.6f
        val surfaceOpacity = prefs[GLASS_SURFACE_OPACITY] ?: 0.5f
        val depthEffect = prefs[GLASS_DEPTH_EFFECT] ?: false
        val chromaticAberration = prefs[GLASS_CHROMATIC_ABERRATION] ?: false

        GlassEffectConfig(
            globalEnabled = enabled,
            style = style,
            blurRadius = blurRadius,
            vibrancy = vibrancy,
            lensHeight = lensHeight,
            lensAmount = lensAmount,
            surfaceOpacity = surfaceOpacity,
            depthEffect = depthEffect,
            chromaticAberration = chromaticAberration
        )
    }

    suspend fun saveGlassEffectConfig(context: Context, config: GlassEffectConfig) {
        context.dataStore.edit { prefs ->
            prefs[GLASS_GLOBAL_ENABLED] = config.globalEnabled
            prefs[GLASS_STYLE] = config.style.name
            prefs[GLASS_BLUR_RADIUS] = config.blurRadius
            prefs[GLASS_VIBRANCY] = config.vibrancy
            prefs[GLASS_LENS_HEIGHT] = config.lensHeight
            prefs[GLASS_LENS_AMOUNT] = config.lensAmount
            prefs[GLASS_SURFACE_OPACITY] = config.surfaceOpacity
            prefs[GLASS_DEPTH_EFFECT] = config.depthEffect
            prefs[GLASS_CHROMATIC_ABERRATION] = config.chromaticAberration
        }
    }

    suspend fun resetGlassEffectConfig(context: Context) {
        context.dataStore.edit { prefs ->
            prefs.remove(GLASS_GLOBAL_ENABLED)
            prefs.remove(GLASS_STYLE)
            prefs.remove(GLASS_BLUR_RADIUS)
            prefs.remove(GLASS_VIBRANCY)
            prefs.remove(GLASS_LENS_HEIGHT)
            prefs.remove(GLASS_LENS_AMOUNT)
            prefs.remove(GLASS_SURFACE_OPACITY)
            prefs.remove(GLASS_DEPTH_EFFECT)
            prefs.remove(GLASS_CHROMATIC_ABERRATION)
        }
    }
}
