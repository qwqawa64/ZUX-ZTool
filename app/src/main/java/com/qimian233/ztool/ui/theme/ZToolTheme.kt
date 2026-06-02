package com.qimian233.ztool.ui.theme

import android.os.Build
import android.content.res.Configuration
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import com.qimian233.ztool.data.theme.ThemePreferencesRepository
import top.yukonga.miuix.kmp.theme.Colors
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.darkColorScheme as miuixDarkColorScheme
import top.yukonga.miuix.kmp.theme.lightColorScheme as miuixLightColorScheme

data class ZToolThemeSpec(
    val style: FrontendStyle,
    val useExpressiveMotion: Boolean = true,
    val tabletOnly: Boolean = true
)

val LocalZToolThemeSpec = staticCompositionLocalOf {
    ZToolThemeSpec(style = FrontendStyle.Material3Expressive)
}

private val Md3eLightColors = lightColorScheme(
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

private val Md3eDarkColors = darkColorScheme(
    primary = Color(0xFFA7C8FF),
    onPrimary = Color(0xFF00315F),
    primaryContainer = Color(0xFF004786),
    onPrimaryContainer = Color(0xFFD5E3FF),
    secondary = Color(0xFFB7C8DC),
    onSecondary = Color(0xFF213143),
    secondaryContainer = Color(0xFF37485B),
    onSecondaryContainer = Color(0xFFD3E4FA),
    tertiary = Color(0xFFD1BFE6),
    onTertiary = Color(0xFF372A4A),
    tertiaryContainer = Color(0xFF4E4162),
    onTertiaryContainer = Color(0xFFECDCFF),
    background = Color(0xFF111418),
    onBackground = Color(0xFFE1E2E8),
    surface = Color(0xFF111418),
    onSurface = Color(0xFFE1E2E8),
    surfaceVariant = Color(0xFF43474E),
    onSurfaceVariant = Color(0xFFC3C6CF),
    outline = Color(0xFF8D9199),
)

private val Md3YouLightColors = lightColorScheme(
    primary = Color(0xFF00639B),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFCFE5FF),
    onPrimaryContainer = Color(0xFF001D32),
    secondary = Color(0xFF526070),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD5E4F7),
    onSecondaryContainer = Color(0xFF0E1D2A),
    tertiary = Color(0xFF68587A),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFEEDBFF),
    onTertiaryContainer = Color(0xFF231533),
    background = Color(0xFFFCFCFF),
    onBackground = Color(0xFF1A1C1E),
    surface = Color(0xFFFCFCFF),
    onSurface = Color(0xFF1A1C1E),
    surfaceVariant = Color(0xFFDEE3EB),
    onSurfaceVariant = Color(0xFF42474E),
    outline = Color(0xFF72787E),
)

private val Md3YouDarkColors = darkColorScheme(
    primary = Color(0xFF99CBFF),
    onPrimary = Color(0xFF003353),
    primaryContainer = Color(0xFF004A75),
    onPrimaryContainer = Color(0xFFCFE5FF),
    secondary = Color(0xFFB9C8DA),
    onSecondary = Color(0xFF243240),
    secondaryContainer = Color(0xFF3A4857),
    onSecondaryContainer = Color(0xFFD5E4F7),
    tertiary = Color(0xFFD3BFE6),
    onTertiary = Color(0xFF382A49),
    tertiaryContainer = Color(0xFF4F4161),
    onTertiaryContainer = Color(0xFFEEDBFF),
    background = Color(0xFF1A1C1E),
    onBackground = Color(0xFFE2E2E6),
    surface = Color(0xFF1A1C1E),
    onSurface = Color(0xFFE2E2E6),
    surfaceVariant = Color(0xFF42474E),
    onSurfaceVariant = Color(0xFFC2C7CE),
    outline = Color(0xFF8C9198),
)

@Composable
fun ZToolTheme(
    style: FrontendStyle = FrontendStyle.Material3Expressive,
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    settings: ZToolThemeSettings? = null,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val systemDarkTheme = (
        configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        ) == Configuration.UI_MODE_NIGHT_YES
    val repository = remember(context) { ThemePreferencesRepository(context.applicationContext) }
    var observedSettings by remember(settings, style, darkTheme, dynamicColor, configuration.uiMode) {
        mutableStateOf(
            settings ?: repository.loadSettings()
        )
    }

    DisposableEffect(repository, settings) {
        if (settings != null) {
            observedSettings = settings
            onDispose { }
        } else {
            observedSettings = repository.loadSettings()
            val unregister = repository.observeSettings { updatedSettings ->
                observedSettings = updatedSettings
            }
            onDispose { unregister() }
        }
    }

    val effectiveSettings = (settings ?: observedSettings).let { loadedSettings ->
        if (settings == null && style != FrontendStyle.Material3Expressive) {
            loadedSettings.copy(frontendStyle = style)
        } else {
            loadedSettings
        }
    }
    val effectiveDarkTheme = when (effectiveSettings.themeMode) {
        ThemeMode.FollowSystem -> systemDarkTheme
        ThemeMode.Light -> false
        ThemeMode.Dark -> true
    }

    val colorScheme = resolveZToolColorScheme(
        settings = effectiveSettings,
        darkTheme = effectiveDarkTheme,
        dynamicColorScheme = {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (effectiveDarkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            } else {
                null
            }
        }
    )

    val themeSpec = ZToolThemeSpec(style = effectiveSettings.frontendStyle)
    val themedContent: @Composable () -> Unit = {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = MaterialTheme.typography,
        ) {
            MiuixTheme(
                colors = colorScheme.toMiuixColors(darkTheme = effectiveDarkTheme),
                content = content
            )
        }
    }

    CompositionLocalProvider(
        LocalZToolThemeSpec provides themeSpec,
        content = themedContent
    )
}

private fun ColorScheme.toMiuixColors(darkTheme: Boolean): Colors {
    return if (darkTheme) {
        miuixDarkColorScheme(
            primary = primary,
            onPrimary = onPrimary,
            primaryVariant = primaryContainer,
            onPrimaryVariant = onPrimaryContainer,
            error = error,
            onError = onError,
            errorContainer = errorContainer,
            onErrorContainer = onErrorContainer,
            primaryContainer = primaryContainer,
            onPrimaryContainer = onPrimaryContainer,
            secondary = secondaryContainer,
            onSecondary = onSecondaryContainer,
            secondaryVariant = surfaceVariant,
            onSecondaryVariant = onSurfaceVariant,
            secondaryContainer = surfaceContainer,
            onSecondaryContainer = onSurfaceVariant,
            tertiaryContainer = tertiaryContainer,
            onTertiaryContainer = onTertiaryContainer,
            background = background,
            onBackground = onBackground,
            surface = surface,
            onSurface = onSurface,
            surfaceVariant = surfaceVariant,
            surfaceContainer = surfaceContainer,
            onSurfaceContainer = onSurface,
            surfaceContainerHigh = surfaceContainerHigh,
            onSurfaceContainerHigh = onSurfaceVariant,
            surfaceContainerHighest = surfaceContainerHighest,
            onSurfaceContainerHighest = onSurface,
            outline = outline,
            dividerLine = outlineVariant
        )
    } else {
        miuixLightColorScheme(
            primary = primary,
            onPrimary = onPrimary,
            primaryVariant = primaryContainer,
            onPrimaryVariant = onPrimaryContainer,
            error = error,
            onError = onError,
            errorContainer = errorContainer,
            onErrorContainer = onErrorContainer,
            primaryContainer = primaryContainer,
            onPrimaryContainer = onPrimaryContainer,
            secondary = secondaryContainer,
            onSecondary = onSecondaryContainer,
            secondaryVariant = surfaceVariant,
            onSecondaryVariant = onSurfaceVariant,
            secondaryContainer = surfaceContainer,
            onSecondaryContainer = onSurfaceVariant,
            tertiaryContainer = tertiaryContainer,
            onTertiaryContainer = onTertiaryContainer,
            background = background,
            onBackground = onBackground,
            surface = surface,
            onSurface = onSurface,
            surfaceVariant = surfaceVariant,
            surfaceContainer = surfaceContainer,
            onSurfaceContainer = onSurface,
            surfaceContainerHigh = surfaceContainerHigh,
            onSurfaceContainerHigh = onSurfaceVariant,
            surfaceContainerHighest = surfaceContainerHighest,
            onSurfaceContainerHighest = onSurface,
            outline = outline,
            dividerLine = outlineVariant
        )
    }
}

private fun resolveZToolColorScheme(
    settings: ZToolThemeSettings,
    darkTheme: Boolean,
    dynamicColorScheme: () -> ColorScheme?
): ColorScheme {
    val baseScheme = when {
        settings.manualColorEnabled -> manualColorScheme(
            seedColor = colorFromArgbLong(settings.manualSeedColor),
            darkTheme = darkTheme,
            paletteMode = settings.materialPaletteMode
        )
        settings.dynamicColorEnabled -> dynamicColorScheme()?.withMaterialPaletteMode(
            style = settings.frontendStyle,
            paletteMode = settings.materialPaletteMode,
            darkTheme = darkTheme
        ) ?: defaultColorScheme(
            style = settings.frontendStyle,
            darkTheme = darkTheme,
            paletteMode = settings.materialPaletteMode
        )
        else -> defaultColorScheme(
            style = settings.frontendStyle,
            darkTheme = darkTheme,
            paletteMode = settings.materialPaletteMode
        )
    }

    return if (darkTheme && settings.amoledBlackEnabled) {
        baseScheme.withAmoledBlackSurfaces()
    } else {
        baseScheme
    }
}

private fun defaultColorScheme(
    style: FrontendStyle,
    darkTheme: Boolean,
    paletteMode: MaterialPaletteMode
): ColorScheme {
    return when (style) {
        FrontendStyle.Material3Expressive -> when (paletteMode) {
            MaterialPaletteMode.MaterialYou2021 -> if (darkTheme) Md3YouDarkColors else Md3YouLightColors
            MaterialPaletteMode.Expressive2025 -> if (darkTheme) Md3eDarkColors else Md3eLightColors
        }
        FrontendStyle.Miuix -> if (darkTheme) Md3eDarkColors else Md3eLightColors
    }
}

private fun ColorScheme.withMaterialPaletteMode(
    style: FrontendStyle,
    paletteMode: MaterialPaletteMode,
    darkTheme: Boolean
): ColorScheme {
    if (style != FrontendStyle.Material3Expressive) {
        return this
    }

    return when (paletteMode) {
        MaterialPaletteMode.MaterialYou2021 -> withMaterialYou2021Tone(darkTheme)
        MaterialPaletteMode.Expressive2025 -> withExpressive2025Tone(darkTheme)
    }
}

private fun ColorScheme.withMaterialYou2021Tone(darkTheme: Boolean): ColorScheme {
    val neutralSurface = if (darkTheme) Color(0xFF1A1C1E) else Color(0xFFFCFCFF)
    val neutralSurfaceVariant = if (darkTheme) Color(0xFF42474E) else Color(0xFFDEE3EB)
    return copy(
        secondary = lerp(primary, if (darkTheme) Color.White else Color.Black, 0.48f),
        secondaryContainer = lerp(primaryContainer, neutralSurfaceVariant, 0.48f),
        tertiary = lerp(primary, tertiary, 0.22f),
        tertiaryContainer = lerp(primaryContainer, tertiaryContainer, 0.22f),
        background = neutralSurface,
        surface = neutralSurface,
        surfaceVariant = neutralSurfaceVariant,
        surfaceContainer = lerp(neutralSurface, neutralSurfaceVariant, if (darkTheme) 0.18f else 0.28f),
        surfaceContainerHigh = lerp(neutralSurface, neutralSurfaceVariant, if (darkTheme) 0.24f else 0.36f),
        surfaceContainerHighest = lerp(neutralSurface, neutralSurfaceVariant, if (darkTheme) 0.30f else 0.44f)
    )
}

private fun ColorScheme.withExpressive2025Tone(darkTheme: Boolean): ColorScheme {
    val accent = if (darkTheme) Color(0xFFFFB1C2) else Color(0xFF9D4058)
    val warmAccent = if (darkTheme) Color(0xFFE7C16D) else Color(0xFF765A00)
    val expressiveSurface = if (darkTheme) Color(0xFF111512) else Color(0xFFFBFDF8)
    val expressiveSurfaceVariant = if (darkTheme) Color(0xFF3F4946) else Color(0xFFDAE5E0)
    return copy(
        secondary = lerp(primary, warmAccent, 0.48f),
        secondaryContainer = lerp(primaryContainer, warmAccent, if (darkTheme) 0.32f else 0.22f),
        tertiary = lerp(primary, accent, 0.58f),
        tertiaryContainer = lerp(primaryContainer, accent, if (darkTheme) 0.38f else 0.26f),
        background = expressiveSurface,
        surface = expressiveSurface,
        surfaceVariant = expressiveSurfaceVariant,
        surfaceContainer = lerp(expressiveSurface, primaryContainer, if (darkTheme) 0.16f else 0.20f),
        surfaceContainerHigh = lerp(expressiveSurface, primaryContainer, if (darkTheme) 0.22f else 0.28f),
        surfaceContainerHighest = lerp(expressiveSurface, tertiaryContainer, if (darkTheme) 0.26f else 0.34f)
    )
}

private fun manualColorScheme(
    seedColor: Color,
    darkTheme: Boolean,
    paletteMode: MaterialPaletteMode
): ColorScheme {
    val primary = if (darkTheme) lerp(seedColor, Color.White, 0.35f) else seedColor
    val secondaryBlend = if (paletteMode == MaterialPaletteMode.MaterialYou2021) 0.48f else 0.35f
    val tertiaryBlend = if (paletteMode == MaterialPaletteMode.MaterialYou2021) 0.24f else 0.35f
    val secondary = lerp(primary, if (darkTheme) Color.White else Color.Black, secondaryBlend)
    val tertiary = lerp(primary, Color(0xFF9C27B0), tertiaryBlend)
    val surface = if (darkTheme) Color(0xFF111418) else Color(0xFFFAF8FF)
    val onSurface = if (darkTheme) Color(0xFFE1E2E8) else Color(0xFF191C20)

    return if (darkTheme) {
        darkColorScheme(
            primary = primary,
            onPrimary = contentColorFor(primary),
            primaryContainer = lerp(primary, Color.Black, 0.45f),
            onPrimaryContainer = Color(0xFFEAF1FF),
            secondary = secondary,
            onSecondary = contentColorFor(secondary),
            secondaryContainer = lerp(secondary, Color.Black, 0.45f),
            onSecondaryContainer = Color(0xFFEAF1FF),
            tertiary = tertiary,
            onTertiary = contentColorFor(tertiary),
            tertiaryContainer = lerp(tertiary, Color.Black, 0.45f),
            onTertiaryContainer = Color(0xFFFFEAF8),
            background = surface,
            onBackground = onSurface,
            surface = surface,
            onSurface = onSurface,
            surfaceVariant = Color(0xFF43474E),
            onSurfaceVariant = Color(0xFFC3C6CF),
            outline = Color(0xFF8D9199)
        )
    } else {
        lightColorScheme(
            primary = primary,
            onPrimary = contentColorFor(primary),
            primaryContainer = lerp(primary, Color.White, 0.82f),
            onPrimaryContainer = lerp(primary, Color.Black, 0.65f),
            secondary = secondary,
            onSecondary = contentColorFor(secondary),
            secondaryContainer = lerp(secondary, Color.White, 0.82f),
            onSecondaryContainer = lerp(secondary, Color.Black, 0.65f),
            tertiary = tertiary,
            onTertiary = contentColorFor(tertiary),
            tertiaryContainer = lerp(tertiary, Color.White, 0.82f),
            onTertiaryContainer = lerp(tertiary, Color.Black, 0.65f),
            background = surface,
            onBackground = onSurface,
            surface = surface,
            onSurface = onSurface,
            surfaceVariant = Color(0xFFDFE2EB),
            onSurfaceVariant = Color(0xFF43474E),
            outline = Color(0xFF73777F)
        )
    }
}

private fun ColorScheme.withAmoledBlackSurfaces(): ColorScheme {
    val black = Color.Black
    val lowSurface = Color(0xFF050505)
    val highSurface = Color(0xFF0A0A0A)
    return copy(
        background = black,
        surface = black,
        surfaceDim = black,
        surfaceBright = highSurface,
        surfaceContainerLowest = black,
        surfaceContainerLow = lowSurface,
        surfaceContainer = lowSurface,
        surfaceContainerHigh = highSurface,
        surfaceContainerHighest = highSurface
    )
}

private fun contentColorFor(color: Color): Color {
    return if (color.luminance() > 0.5f) Color.Black else Color.White
}

private fun colorFromArgbLong(value: Long): Color {
    return Color((value and 0xFFFFFFFFL).toInt())
}
