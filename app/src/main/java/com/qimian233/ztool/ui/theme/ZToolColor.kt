package com.qimian233.ztool.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import top.yukonga.miuix.kmp.theme.Colors

/**
 * CompositionLocal that provides the canonical [ColorScheme] regardless of whether
 * the current frontend style is Material3Expressive or Miuix.
 *
 * In Material3Expressive style this delegates to [androidx.compose.material3.MaterialTheme.colorScheme].
 * In Miuix style this is mapped from [top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme]
 * so that common (non-Miuix) components read the same colours as Miuix components.
 */
val LocalZToolColorScheme = staticCompositionLocalOf<ColorScheme> {
    // Fallback: a neutral Material3Expressive light scheme (should never be visible in practice).
    lightColorScheme(
        primary = Color(0xFF1D5FA8),
        onPrimary = Color.White,
        primaryContainer = Color(0xFFD5E3FF),
        onPrimaryContainer = Color(0xFF001C3B),
        secondary = Color(0xFF4F6074),
        onSecondary = Color.White,
        secondaryContainer = Color(0xFFD3E4FA),
        onSecondaryContainer = Color(0xFF0B1D2F),
        tertiary = Color(0xFF66587A),
        onTertiary = Color.White,
        tertiaryContainer = Color(0xFFECDCFF),
        onTertiaryContainer = Color(0xFF211533),
        background = Color(0xFFFAF8FF),
        onBackground = Color(0xFF191C20),
        surface = Color(0xFFFAF8FF),
        onSurface = Color(0xFF191C20),
        surfaceVariant = Color(0xFFDFE2EB),
        onSurfaceVariant = Color(0xFF43474E),
        outline = Color(0xFF73777F),
    )
}

/**
 * Converts a Miuix [Colors] instance to a Material3 [ColorScheme].
 *
 * The mapping preserves as much fidelity as possible while filling in Material-only
 * tokens (surface containers, inverse colours, scrim, etc.) with reasonable defaults
 * derived from the available Miuix colours.
 */
fun Colors.toMaterialColorScheme(darkTheme: Boolean): ColorScheme {
    val tertiary = lerp(primary, secondary, 0.45f)
    val onTertiary = contentColorFor(tertiary)
    val surfaceDim = lerp(surface, Color.Black, if (darkTheme) 0.15f else 0.05f)
    val surfaceBright = lerp(surface, Color.White, if (darkTheme) 0.05f else 0.15f)
    val inverseSurface = if (darkTheme) Color(0xFFE1E2E8) else Color(0xFF191C20)
    val inverseOnSurface = if (darkTheme) Color(0xFF191C20) else Color(0xFFE1E2E8)
    val inversePrimary = lerp(primary, inverseSurface, 0.55f)

    return if (darkTheme) {
        darkColorScheme(
            primary = primary,
            onPrimary = onPrimary,
            primaryContainer = primaryContainer,
            onPrimaryContainer = onPrimaryContainer,
            inversePrimary = inversePrimary,
            secondary = secondary,
            onSecondary = onSecondary,
            secondaryContainer = secondaryContainer,
            onSecondaryContainer = onSecondaryContainer,
            tertiary = tertiary,
            onTertiary = onTertiary,
            tertiaryContainer = tertiaryContainer,
            onTertiaryContainer = onTertiaryContainer,
            background = background,
            onBackground = onBackground,
            surface = surface,
            onSurface = onSurface,
            surfaceVariant = surfaceVariant,
            onSurfaceVariant = onSurfaceVariantSummary,
            surfaceDim = surfaceDim,
            surfaceBright = surfaceBright,
            surfaceContainerLowest = surface,
            surfaceContainerLow = lerp(surface, surfaceContainer, 0.45f),
            surfaceContainer = surfaceContainer,
            surfaceContainerHigh = surfaceContainerHigh,
            surfaceContainerHighest = surfaceContainerHighest,
            inverseSurface = inverseSurface,
            inverseOnSurface = inverseOnSurface,
            error = error,
            onError = onError,
            errorContainer = errorContainer,
            onErrorContainer = onErrorContainer,
            outline = outline,
            outlineVariant = dividerLine,
            scrim = Color.Black.copy(alpha = 0.32f),
        )
    } else {
        lightColorScheme(
            primary = primary,
            onPrimary = onPrimary,
            primaryContainer = primaryContainer,
            onPrimaryContainer = onPrimaryContainer,
            inversePrimary = inversePrimary,
            secondary = secondary,
            onSecondary = onSecondary,
            secondaryContainer = secondaryContainer,
            onSecondaryContainer = onSecondaryContainer,
            tertiary = tertiary,
            onTertiary = onTertiary,
            tertiaryContainer = tertiaryContainer,
            onTertiaryContainer = onTertiaryContainer,
            background = background,
            onBackground = onBackground,
            surface = surface,
            onSurface = onSurface,
            surfaceVariant = surfaceVariant,
            onSurfaceVariant = onSurfaceVariantSummary,
            surfaceDim = surfaceDim,
            surfaceBright = surfaceBright,
            surfaceContainerLowest = surface,
            surfaceContainerLow = lerp(surface, surfaceContainer, 0.45f),
            surfaceContainer = surfaceContainer,
            surfaceContainerHigh = surfaceContainerHigh,
            surfaceContainerHighest = surfaceContainerHighest,
            inverseSurface = inverseSurface,
            inverseOnSurface = inverseOnSurface,
            error = error,
            onError = onError,
            errorContainer = errorContainer,
            onErrorContainer = onErrorContainer,
            outline = outline,
            outlineVariant = dividerLine,
            scrim = Color.Black.copy(alpha = 0.32f),
        )
    }
}

internal fun contentColorFor(color: Color): Color {
    return if (color.luminance() > 0.5f) Color.Black else Color.White
}

private fun Color.luminance(): Float {
    val r = red
    val g = green
    val b = blue
    return 0.2126f * r + 0.7152f * g + 0.0722f * b
}
