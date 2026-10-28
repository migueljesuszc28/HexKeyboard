/**
 * Convx Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.example.mantiskeyboard.ui.component

import android.app.ActivityManager
import android.os.Build
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.convx.music.ui.component.backdrop.Backdrop
import com.convx.music.ui.component.backdrop.BackdropEffectScope
import com.convx.music.ui.component.backdrop.backdrops.emptyBackdrop
import com.convx.music.ui.component.backdrop.drawBackdrop
import com.convx.music.ui.component.backdrop.effects.blur
import com.convx.music.ui.component.backdrop.effects.colorControls
import com.convx.music.ui.component.backdrop.effects.lens
import com.convx.music.ui.component.backdrop.poolLens
import com.convx.music.ui.component.backdrop.highlight.Highlight
import com.convx.music.ui.component.backdrop.highlight.HighlightStyle
import com.convx.music.ui.component.backdrop.isRenderEffectSupported
import com.convx.music.ui.component.backdrop.shadow.Shadow
import com.example.mantiskeyboard.ui.component.backdrop.backdrops.LayerBackdrop

/**
 * User-configurable parameters of the liquid glass effect, sourced from DataStore
 * preferences and distributed through [LocalGlassEffectConfig].
 */
@Stable
data class GlassEffectConfig(
    val globalEnabled: Boolean = true,
    val vibrancy: Float = 1.2f,
    /** Blur in dp applied to glass pills. */
    val blurRadius: Float = 2f,
    /** 0..1, mapped to 0..[LENS_MAX_DP] dp of lens refraction height. 0.4 = 40%. */
    val lensHeight: Float = 0.4f,
    /** 0..1, mapped to 0..[LENS_MAX_DP] dp of lens refraction amount. 0.6 = 60%. */
    val lensAmount: Float = 0.6f,
    val chromaticAberration: Boolean = false,
    val depthEffect: Boolean = false,
    /** [Color.Unspecified] means adaptive: dark grey on dark. */
    val surfaceTintColor: Color = Color.Unspecified,
    /** Specular rim ("pluck") colour. [Color.Unspecified] keeps the default white. */
    val highlightColor: Color = Color.Unspecified,
    /** Specular rim opacity, 0..1. */
    val highlightOpacity: Float = EdgeHighlightAlpha,
    /** Which rendering style every glass surface uses. */
    val style: GlassStyle = GlassStyle.LIQUID,
    val surfaceOpacity: Float = 0.5f,
    val textColor: Color = Color.White,
)

/**
 * How a glass surface is rendered.
 */
enum class GlassStyle {
    /** Blur + saturation + lens refraction + specular rim. The default. */
    LIQUID,

    /** Blur and saturation only: no lens, no rim, no chromatic aberration. */
    BLUR,

    /** Translucent tinted fill at the configured opacity. */
    TRANSPARENT,
}

/**
 * True when [Modifier.liquidGlass] should take its cheap translucent-tint path.
 */
fun shouldUseTranslucentGlassFallback(style: GlassStyle, renderEffectSupported: Boolean): Boolean =
    style == GlassStyle.TRANSPARENT || !renderEffectSupported

/**
 * Maximum lens refraction in dp when the 0..1 preference sliders are at 1.
 */
internal const val LENS_MAX_DP = 48f

/**
 * Lowest resolution fraction glass surfaces are rendered at (heavy blur hides it).
 */
internal const val MIN_GLASS_RESOLUTION_SCALE = 0.30f

/** Blur radius (dp) at or above which the minimum resolution scale is safe to use. */
internal const val FULL_QUALITY_BLUR_DP = 8f

/**
 * Resolution fraction at which a glass surface records and processes its backdrop.
 */
fun glassResolutionScale(blurRadiusDp: Float): Float {
    val t = (blurRadiusDp / FULL_QUALITY_BLUR_DP).coerceIn(0f, 1f)
    return 1f - t * (1f - MIN_GLASS_RESOLUTION_SCALE)
}

/**
 * The backdrop blur pipeline requires [android.graphics.RenderEffect] on a
 * [android.graphics.RenderNode], which is available from Android 12 (API 31).
 */
fun isGlassSupported(sdkInt: Int = Build.VERSION.SDK_INT): Boolean = sdkInt >= Build.VERSION_CODES.S

/**
 * Devices Android flags as low-RAM ([ActivityManager.isLowRamDevice]) skip glass
 * in favor of flat fallback.
 */
@Composable
fun isLowRamDevice(): Boolean {
    val context = LocalContext.current
    return remember {
        context.getSystemService(ActivityManager::class.java)?.isLowRamDevice ?: false
    }
}

/** Combines the API-level check with the low-RAM device check; use this to gate glass. */
@Composable
fun isGlassAllowed(): Boolean = isGlassSupported() && !isLowRamDevice()

/**
 * Returns true if the current theme appears dark based on surface luminance.
 */
@Composable
fun glassUsesDarkAppearance(): Boolean =
    MaterialTheme.colorScheme.surface.luminance() <= 0.5f

/**
 * Resolves configured or adaptive text color for glass chrome.
 */
@Composable
fun GlassEffectConfig.resolvedTextColor(
    activeTint: Color = Color.Unspecified,
): Color {
    if (textColor.isSpecified) return textColor
    val isDark = glassUsesDarkAppearance()
    val effectiveColor = if (activeTint.isSpecified) activeTint else if (isDark) Color(0xFF1E1E22) else Color(0xFFFAFAFA)
    return if (effectiveColor.luminance() > 0.5f) Color(0xFF1A1A1A) else Color.White
}

/**
 * Maps the user-facing vibrancy preference (0..2, default 1) to a saturation multiplier.
 */
fun glassSaturation(vibrancy: Float): Float = 1f + 0.5f * vibrancy.coerceIn(0f, 2f)

/**
 * A content colour guaranteed to read against the glass it sits on.
 */
fun glassContentColorFor(behind: Color, tint: Color, opacity: Float): Color {
    val effective = if (tint.isSpecified) {
        lerp(behind, tint, opacity.coerceIn(0f, 1f))
    } else {
        behind
    }
    return if (effective.luminance() > 0.5f) Color(0xFF1A1A1A) else Color.White
}

private val EdgeHighlightWidth = 0.8f.dp
private const val EdgeHighlightAlpha = 0.55f

private const val HighlightAngleMin = 25f
private const val HighlightAngleMax = 65f
private const val HighlightAngleFrozen = (HighlightAngleMin + HighlightAngleMax) / 2f

/** Length of the light-to-dark glass crossfade in ms. */
private const val GlassThemeFadeMillis = 280

private val DarkRimWidth = 1.6.dp
private const val DarkRimAlpha = 0.32f

val LocalGlassEffectConfig = staticCompositionLocalOf { GlassEffectConfig() }

/** The backdrop content (app UI) that glass surfaces sample from. */
val LocalAppBackdrop = staticCompositionLocalOf<Backdrop> { emptyBackdrop() }

/** Processed backdrop shared by surfaces with matching blur and vibrancy settings. */
@Stable
class PooledGlass(
    val backdrop: LayerBackdrop,
    val blurRadiusDp: Float,
    val vibrancy: Float,
)

val LocalPooledGlass = staticCompositionLocalOf<PooledGlass?> { null }

/** True while app-level motion is active. Freeze nested captures during movement. */
val LocalAppBackdropFrozen = staticCompositionLocalOf<() -> Boolean> { { false } }

/** Reactive state mirror used to pause animations when backdrop capture is frozen. */
val LocalAppMotionFrozen = staticCompositionLocalOf<State<Boolean>> {
    object : State<Boolean> {
        override val value: Boolean get() = false
    }
}

/** Optional cache key for reusing glass output over looping video backdrops. */
val LocalBackdropLoopBucket = staticCompositionLocalOf<(() -> Int)?> { null }

/**
 * Draws a subtle dark outer rim stroke around glass surfaces for high-contrast definition.
 */
private fun Modifier.outerDarkRim(
    shape: Shape,
    width: Dp,
    alpha: Float,
): Modifier = drawWithContent {
    drawContent()
    if (alpha <= 0f) return@drawWithContent
    val strokeWidth = width.toPx()
    val outline = shape.createOutline(size, layoutDirection, this)
    drawOutline(
        outline = outline,
        color = Color.Black.copy(alpha = alpha),
        style = Stroke(width = strokeWidth)
    )
}

/**
 * Renders this composable as a liquid glass surface sampling [LocalAppBackdrop].
 */
@Composable
fun Modifier.liquidGlass(
    config: GlassEffectConfig,
    shape: CornerBasedShape = RoundedCornerShape(0.dp),
    applyEdgeEffects: Boolean = true,
    blurRadiusDp: Float = config.blurRadius,
    highlightAlpha: Float = EdgeHighlightAlpha,
    backdropScale: Float = glassResolutionScale(blurRadiusDp),
    frozen: () -> Boolean = LocalAppBackdropFrozen.current,
    loopBucket: (() -> Int)? = LocalBackdropLoopBucket.current,
    outerShadow: Boolean = true,
): Modifier {
    val glassAllowed = isGlassAllowed()
    val pool = LocalPooledGlass.current
    val usePool = pool != null &&
            blurRadiusDp == pool.blurRadiusDp &&
            config.vibrancy == pool.vibrancy &&
            !config.chromaticAberration
    val backdrop = if (usePool) pool.backdrop else if (glassAllowed) LocalAppBackdrop.current else null
    val density = LocalDensity.current
    val resolutionScale = if (usePool) 1f else backdropScale.coerceIn(0.05f, 1f)
    val blurPx = with(density) { blurRadiusDp.dp.toPx() } * resolutionScale
    val saturation = glassSaturation(config.vibrancy)
    val lensHeightPx = with(density) { (config.lensHeight * LENS_MAX_DP).dp.toPx() } * resolutionScale
    val lensAmountPx = with(density) { (config.lensAmount * LENS_MAX_DP).dp.toPx() } * resolutionScale

    val isDark = glassUsesDarkAppearance()
    val baseSurfaceColor = if (isDark) Color(0xFF1E1E22) else Color(0xFFFAFAFA)
    val surfaceTintColor = if (config.surfaceTintColor.isSpecified) {
        config.surfaceTintColor
    } else if (isDark) {
        Color(0xFF4A4A4E)
    } else {
        Color(0xFFFAFAFA)
    }

    if (!glassAllowed || shouldUseTranslucentGlassFallback(config.style, isRenderEffectSupported()) || backdrop == null) {
        val fallbackColor = if (config.surfaceTintColor.isSpecified) config.surfaceTintColor else baseSurfaceColor
        return this
            .clip(shape)
            .background(fallbackColor.copy(alpha = config.surfaceOpacity.coerceIn(0.1f, 1f)))
    }

    val glassFade = tween<Color>(GlassThemeFadeMillis)
    val animBaseColor = animateColorAsState(baseSurfaceColor.copy(alpha = 0.15f), glassFade, label = "glassBase")
    val animTintColor = animateColorAsState(surfaceTintColor.copy(alpha = config.surfaceOpacity.coerceIn(0f, 1f)), glassFade, label = "glassTint")

    val animatedHighlightAngle = HighlightAngleFrozen
    val shapeBlock: () -> Shape = remember(shape) { { shape } }
    val plainBlur = config.style == GlassStyle.BLUR

    val effectsBlock: BackdropEffectScope.() -> Unit = remember(
        saturation,
        blurPx,
        applyEdgeEffects,
        plainBlur,
        lensHeightPx,
        lensAmountPx,
        config.depthEffect,
        config.chromaticAberration,
        usePool,
    ) {
        {
            if (!usePool && saturation != 1f) {
                colorControls(saturation = saturation)
            }
            if (!usePool && blurPx > 0f) {
                blur(blurPx)
            }
            if (!plainBlur &&
                applyEdgeEffects &&
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                (lensHeightPx > 0f || lensAmountPx > 0f)
            ) {
                if (usePool) {
                    poolLens(
                        refractionHeight = lensHeightPx,
                        refractionAmount = lensAmountPx,
                        depthEffect = config.depthEffect,
                    )
                } else {
                    lens(
                        refractionHeight = lensHeightPx,
                        refractionAmount = lensAmountPx,
                        depthEffect = config.depthEffect,
                        chromaticAberration = config.chromaticAberration,
                    )
                }
            }
        }
    }

    val rimColor = if (config.highlightColor.isSpecified) config.highlightColor else Color.White
    val rimAlpha = (highlightAlpha * (config.highlightOpacity / EdgeHighlightAlpha)).coerceIn(0f, 1f)
    val animRimColor = animateColorAsState(rimColor.copy(alpha = rimAlpha), glassFade, label = "glassRim")

    val highlightBlock: (() -> Highlight?)? = remember(applyEdgeEffects, plainBlur, animRimColor.value, animatedHighlightAngle) {
        if (applyEdgeEffects && !plainBlur) {
            {
                Highlight(
                    width = EdgeHighlightWidth,
                    style = HighlightStyle.Default(
                        color = animRimColor.value,
                        angle = animatedHighlightAngle,
                    ),
                )
            }
        } else {
            null
        }
    }

    val shadowBlock: (() -> Shadow?)? = remember(applyEdgeEffects, usePool, outerShadow) {
        if (applyEdgeEffects && !usePool && outerShadow) ({ Shadow.Default }) else null
    }

    val darkRimAlpha = (DarkRimAlpha * (config.highlightOpacity / EdgeHighlightAlpha)).coerceIn(0f, 1f)

    val surfaceBlock: DrawScope.() -> Unit = remember(animBaseColor.value, animTintColor.value) {
        {
            if (animBaseColor.value.alpha > 0f) {
                drawRect(color = animBaseColor.value, size = size)
            }
            if (animTintColor.value.alpha > 0f) {
                drawRect(color = animTintColor.value, size = size)
            }
        }
    }

    return drawBackdrop(
        backdrop = backdrop,
        shape = shapeBlock,
        effects = effectsBlock,
        highlight = highlightBlock,
        shadow = shadowBlock,
        onDrawSurface = surfaceBlock,
        backdropScale = resolutionScale,
        frozen = frozen,
        loopBucket = loopBucket,
    ).then(
        if (applyEdgeEffects && !plainBlur && darkRimAlpha > 0f) {
            Modifier.outerDarkRim(
                shape = shape,
                width = DarkRimWidth,
                alpha = darkRimAlpha,
            )
        } else {
            Modifier
        }
    )
}
