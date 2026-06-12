package com.qimian233.ztool.ui.theme

import android.os.Build
import android.content.res.Configuration
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.padding
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
import androidx.compose.runtime.movableContentOf
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
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeColorSpec
import top.yukonga.miuix.kmp.theme.ThemeController
import top.yukonga.miuix.kmp.theme.ThemePaletteStyle
import top.yukonga.miuix.kmp.theme.darkColorScheme as miuixDarkColorScheme
import top.yukonga.miuix.kmp.theme.lightColorScheme as miuixLightColorScheme

data class ZToolThemeSpec(
    val style: FrontendStyle,
    val useExpressiveMotion: Boolean = true,
    val tabletOnly: Boolean = true,
    val dynamicColorEnabled: Boolean = true,
    val manualColorEnabled: Boolean = false
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

    val themeSpec = ZToolThemeSpec(
        style = effectiveSettings.frontendStyle,
        dynamicColorEnabled = effectiveSettings.dynamicColorEnabled,
        manualColorEnabled = effectiveSettings.manualColorEnabled
    )
    val isMiuixStyle = effectiveSettings.frontendStyle == FrontendStyle.Miuix
    val movableContent = remember(content) { movableContentOf(content) }
    
    val themedContent: @Composable () -> Unit = {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = MaterialTheme.typography,
        ) {
            if (isMiuixStyle) {
                val miuixMode = when (effectiveSettings.themeMode) {
                    ThemeMode.FollowSystem -> if (effectiveSettings.dynamicColorEnabled || effectiveSettings.manualColorEnabled) ColorSchemeMode.MonetSystem else ColorSchemeMode.System
                    ThemeMode.Light -> if (effectiveSettings.dynamicColorEnabled || effectiveSettings.manualColorEnabled) ColorSchemeMode.MonetLight else ColorSchemeMode.Light
                    ThemeMode.Dark -> if (effectiveSettings.dynamicColorEnabled || effectiveSettings.manualColorEnabled) ColorSchemeMode.MonetDark else ColorSchemeMode.Dark
                }

                val miuixPalette = when (effectiveSettings.materialPalette) {
                    MaterialPalette.TonalSpot -> ThemePaletteStyle.TonalSpot
                    MaterialPalette.Neutral -> ThemePaletteStyle.Neutral
                    MaterialPalette.Vibrant -> ThemePaletteStyle.Vibrant
                    MaterialPalette.Expressive -> ThemePaletteStyle.Expressive
                    MaterialPalette.Rainbow -> ThemePaletteStyle.Rainbow
                    MaterialPalette.FruitSalad -> ThemePaletteStyle.FruitSalad
                    MaterialPalette.MonoChrome -> ThemePaletteStyle.Monochrome
                    MaterialPalette.Fidelity -> ThemePaletteStyle.Fidelity
                    MaterialPalette.Content -> ThemePaletteStyle.Content
                }

                val miuixSpec = when (effectiveSettings.materialColorSpec) {
                    MaterialColorSpec.Spec2021 -> ThemeColorSpec.Spec2021
                    MaterialColorSpec.Spec2025 -> ThemeColorSpec.Spec2025
                }

                val miuixKeyColor = if (effectiveSettings.manualColorEnabled) {
                    colorFromArgbLong(effectiveSettings.manualSeedColor)
                } else null

                val controller = remember(miuixMode, miuixKeyColor, miuixPalette, miuixSpec) {
                    ThemeController(
                        colorSchemeMode = miuixMode,
                        keyColor = miuixKeyColor,
                        paletteStyle = miuixPalette,
                        colorSpec = miuixSpec
                    )
                }

                MiuixTheme(
                    controller = controller,
                    content = { 
                        movableContent()
                    }
                )
            } else {
                MiuixTheme(
                    colors = colorScheme.toMiuixColors(darkTheme = effectiveDarkTheme),
                    content = { movableContent() }
                )
            }
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
            colorSpec = settings.materialColorSpec,
            palette = settings.materialPalette
        )
        settings.dynamicColorEnabled -> dynamicColorScheme()?.withMaterialPalette(
            colorSpec = settings.materialColorSpec,
            palette = settings.materialPalette,
            darkTheme = darkTheme
        ) ?: defaultColorScheme(
            style = settings.frontendStyle,
            darkTheme = darkTheme,
            colorSpec = settings.materialColorSpec,
            palette = settings.materialPalette
        )
        else -> defaultColorScheme(
            style = settings.frontendStyle,
            darkTheme = darkTheme,
            colorSpec = settings.materialColorSpec,
            palette = settings.materialPalette
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
    colorSpec: MaterialColorSpec,
    palette: MaterialPalette
): ColorScheme {
    return when (style) {
        FrontendStyle.Material3Expressive -> defaultBaseColorScheme(
            colorSpec = effectiveMaterialColorSpec(colorSpec, palette),
            darkTheme = darkTheme
        ).withMaterialPalette(colorSpec, palette, darkTheme)
        FrontendStyle.Miuix -> defaultBaseColorScheme(
            colorSpec = effectiveMaterialColorSpec(colorSpec, palette),
            darkTheme = darkTheme
        ).withMaterialPalette(colorSpec, palette, darkTheme)
    }
}

private fun defaultBaseColorScheme(
    colorSpec: MaterialColorSpec,
    darkTheme: Boolean
): ColorScheme {
    return when (colorSpec) {
        MaterialColorSpec.Spec2021 -> if (darkTheme) Md3YouDarkColors else Md3YouLightColors
        MaterialColorSpec.Spec2025 -> if (darkTheme) Md3eDarkColors else Md3eLightColors
    }
}

private fun effectiveMaterialColorSpec(
    colorSpec: MaterialColorSpec,
    palette: MaterialPalette
): MaterialColorSpec {
    if (colorSpec == MaterialColorSpec.Spec2021) {
        return MaterialColorSpec.Spec2021
    }

    return if (palette.supportsSpec2025) {
        MaterialColorSpec.Spec2025
    } else {
        MaterialColorSpec.Spec2021
    }
}

private val MaterialPalette.supportsSpec2025: Boolean
    get() = when (this) {
        MaterialPalette.TonalSpot,
        MaterialPalette.Neutral,
        MaterialPalette.Vibrant,
        MaterialPalette.Expressive -> true
        MaterialPalette.Rainbow,
        MaterialPalette.FruitSalad,
        MaterialPalette.MonoChrome,
        MaterialPalette.Fidelity,
        MaterialPalette.Content -> false
    }

private fun ColorScheme.withMaterialPalette(
    colorSpec: MaterialColorSpec,
    palette: MaterialPalette,
    darkTheme: Boolean
): ColorScheme {
    return when (palette) {
        MaterialPalette.TonalSpot -> withTonalSpotTone(darkTheme, effectiveMaterialColorSpec(colorSpec, palette))
        MaterialPalette.Neutral -> withNeutralTone(darkTheme)
        MaterialPalette.Vibrant -> withVibrantTone(darkTheme)
        MaterialPalette.Expressive -> withExpressiveTone(darkTheme, effectiveMaterialColorSpec(colorSpec, palette))
        MaterialPalette.Rainbow -> withRainbowTone(darkTheme)
        MaterialPalette.FruitSalad -> withFruitSaladTone(darkTheme)
        MaterialPalette.MonoChrome -> withMonoChromeTone(darkTheme)
        MaterialPalette.Fidelity -> withFidelityTone(darkTheme)
        MaterialPalette.Content -> withContentTone(darkTheme)
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

private fun ColorScheme.withTonalSpotTone(
    darkTheme: Boolean,
    colorSpec: MaterialColorSpec
): ColorScheme {
    return when (colorSpec) {
        MaterialColorSpec.Spec2021 -> withMaterialYou2021Tone(darkTheme)
        MaterialColorSpec.Spec2025 -> withExpressive2025Tone(darkTheme)
    }
}

private fun ColorScheme.withNeutralTone(darkTheme: Boolean): ColorScheme {
    val neutralSurface = if (darkTheme) Color(0xFF17181A) else Color(0xFFFCFCFD)
    val neutralVariant = if (darkTheme) Color(0xFF45474A) else Color(0xFFE1E3E6)
    val neutralAccent = if (darkTheme) Color(0xFFC6C6CA) else Color(0xFF5E6267)
    return copy(
        primary = lerp(primary, neutralAccent, 0.58f),
        primaryContainer = lerp(primaryContainer, neutralVariant, 0.54f),
        secondary = lerp(secondary, neutralAccent, 0.72f),
        secondaryContainer = lerp(secondaryContainer, neutralVariant, 0.68f),
        tertiary = lerp(tertiary, neutralAccent, 0.68f),
        tertiaryContainer = lerp(tertiaryContainer, neutralVariant, 0.64f),
        background = neutralSurface,
        surface = neutralSurface,
        surfaceVariant = neutralVariant,
        surfaceContainer = lerp(neutralSurface, neutralVariant, if (darkTheme) 0.16f else 0.24f),
        surfaceContainerHigh = lerp(neutralSurface, neutralVariant, if (darkTheme) 0.22f else 0.32f),
        surfaceContainerHighest = lerp(neutralSurface, neutralVariant, if (darkTheme) 0.28f else 0.40f)
    )
}

private fun ColorScheme.withVibrantTone(darkTheme: Boolean): ColorScheme {
    val hotAccent = if (darkTheme) Color(0xFFFFB1C8) else Color(0xFFA7335F)
    val coolAccent = if (darkTheme) Color(0xFF8ED8FF) else Color(0xFF00658A)
    val surfaceBase = if (darkTheme) Color(0xFF121317) else Color(0xFFFFF8FB)
    return copy(
        secondary = lerp(primary, hotAccent, 0.52f),
        secondaryContainer = lerp(primaryContainer, hotAccent, if (darkTheme) 0.30f else 0.18f),
        tertiary = lerp(primary, coolAccent, 0.62f),
        tertiaryContainer = lerp(primaryContainer, coolAccent, if (darkTheme) 0.34f else 0.20f),
        background = surfaceBase,
        surface = surfaceBase,
        surfaceContainer = lerp(surfaceBase, primaryContainer, if (darkTheme) 0.18f else 0.22f),
        surfaceContainerHigh = lerp(surfaceBase, secondaryContainer, if (darkTheme) 0.22f else 0.30f),
        surfaceContainerHighest = lerp(surfaceBase, tertiaryContainer, if (darkTheme) 0.26f else 0.36f)
    )
}

private fun ColorScheme.withExpressiveTone(
    darkTheme: Boolean,
    colorSpec: MaterialColorSpec
): ColorScheme {
    return when (colorSpec) {
        MaterialColorSpec.Spec2021 -> withMaterialYou2021Tone(darkTheme)
        MaterialColorSpec.Spec2025 -> withExpressive2025Tone(darkTheme)
    }
}

private fun ColorScheme.withRainbowTone(darkTheme: Boolean): ColorScheme {
    val greenAccent = if (darkTheme) Color(0xFF9DD67D) else Color(0xFF3E6F00)
    val violetAccent = if (darkTheme) Color(0xFFD9B8FF) else Color(0xFF72529B)
    val surfaceBase = if (darkTheme) Color(0xFF111511) else Color(0xFFFCFCEF)
    return copy(
        secondary = lerp(primary, greenAccent, 0.62f),
        secondaryContainer = lerp(primaryContainer, greenAccent, if (darkTheme) 0.34f else 0.22f),
        tertiary = lerp(primary, violetAccent, 0.58f),
        tertiaryContainer = lerp(primaryContainer, violetAccent, if (darkTheme) 0.36f else 0.24f),
        background = surfaceBase,
        surface = surfaceBase,
        surfaceVariant = lerp(surfaceVariant, greenAccent, if (darkTheme) 0.18f else 0.12f),
        surfaceContainer = lerp(surfaceBase, secondaryContainer, if (darkTheme) 0.16f else 0.22f),
        surfaceContainerHigh = lerp(surfaceBase, primaryContainer, if (darkTheme) 0.20f else 0.28f),
        surfaceContainerHighest = lerp(surfaceBase, tertiaryContainer, if (darkTheme) 0.24f else 0.34f)
    )
}

private fun ColorScheme.withFruitSaladTone(darkTheme: Boolean): ColorScheme {
    val limeAccent = if (darkTheme) Color(0xFFB8D96B) else Color(0xFF5D6F00)
    val berryAccent = if (darkTheme) Color(0xFFFFB0B6) else Color(0xFF9A4048)
    val surfaceBase = if (darkTheme) Color(0xFF15140F) else Color(0xFFFFFAEF)
    return copy(
        primary = lerp(primary, limeAccent, 0.20f),
        primaryContainer = lerp(primaryContainer, limeAccent, if (darkTheme) 0.22f else 0.14f),
        secondary = lerp(primary, limeAccent, 0.58f),
        secondaryContainer = lerp(primaryContainer, limeAccent, if (darkTheme) 0.34f else 0.24f),
        tertiary = lerp(primary, berryAccent, 0.58f),
        tertiaryContainer = lerp(primaryContainer, berryAccent, if (darkTheme) 0.36f else 0.24f),
        background = surfaceBase,
        surface = surfaceBase,
        surfaceContainer = lerp(surfaceBase, primaryContainer, if (darkTheme) 0.18f else 0.24f),
        surfaceContainerHigh = lerp(surfaceBase, secondaryContainer, if (darkTheme) 0.24f else 0.34f),
        surfaceContainerHighest = lerp(surfaceBase, tertiaryContainer, if (darkTheme) 0.28f else 0.40f)
    )
}

private fun ColorScheme.withMonoChromeTone(darkTheme: Boolean): ColorScheme {
    val neutralSurface = if (darkTheme) Color(0xFF111111) else Color(0xFFFCFCFC)
    val neutralVariant = if (darkTheme) Color(0xFF444444) else Color(0xFFE2E2E2)
    val accent = if (darkTheme) Color(0xFFE2E2E2) else Color(0xFF4D4D4D)
    return copy(
        primary = accent,
        primaryContainer = if (darkTheme) Color(0xFF333333) else Color(0xFFE8E8E8),
        secondary = if (darkTheme) Color(0xFFC6C6C6) else Color(0xFF5F5F5F),
        secondaryContainer = if (darkTheme) Color(0xFF3F3F3F) else Color(0xFFE0E0E0),
        tertiary = if (darkTheme) Color(0xFFDBDBDB) else Color(0xFF525252),
        tertiaryContainer = if (darkTheme) Color(0xFF363636) else Color(0xFFE5E5E5),
        background = neutralSurface,
        surface = neutralSurface,
        surfaceVariant = neutralVariant,
        surfaceContainer = lerp(neutralSurface, neutralVariant, if (darkTheme) 0.18f else 0.26f),
        surfaceContainerHigh = lerp(neutralSurface, neutralVariant, if (darkTheme) 0.24f else 0.34f),
        surfaceContainerHighest = lerp(neutralSurface, neutralVariant, if (darkTheme) 0.30f else 0.42f),
        outline = if (darkTheme) Color(0xFF8E8E8E) else Color(0xFF747474)
    )
}

private fun ColorScheme.withFidelityTone(darkTheme: Boolean): ColorScheme {
    val surfaceBase = if (darkTheme) Color(0xFF111418) else Color(0xFFFCFBFF)
    return copy(
        secondary = lerp(primary, secondary, 0.28f),
        secondaryContainer = lerp(primaryContainer, secondaryContainer, 0.28f),
        tertiary = lerp(primary, tertiary, 0.22f),
        tertiaryContainer = lerp(primaryContainer, tertiaryContainer, 0.22f),
        background = surfaceBase,
        surface = surfaceBase,
        surfaceContainer = lerp(surfaceBase, primaryContainer, if (darkTheme) 0.12f else 0.18f),
        surfaceContainerHigh = lerp(surfaceBase, primaryContainer, if (darkTheme) 0.18f else 0.26f),
        surfaceContainerHighest = lerp(surfaceBase, primaryContainer, if (darkTheme) 0.24f else 0.34f)
    )
}

private fun ColorScheme.withContentTone(darkTheme: Boolean): ColorScheme {
    val surfaceBase = if (darkTheme) Color(0xFF111418) else Color(0xFFFBFCFF)
    return copy(
        secondary = lerp(primary, secondary, 0.46f),
        secondaryContainer = lerp(primaryContainer, secondaryContainer, 0.46f),
        tertiary = lerp(primary, tertiary, 0.42f),
        tertiaryContainer = lerp(primaryContainer, tertiaryContainer, 0.42f),
        background = surfaceBase,
        surface = surfaceBase,
        surfaceVariant = lerp(surfaceVariant, primaryContainer, if (darkTheme) 0.14f else 0.12f),
        surfaceContainer = lerp(surfaceBase, primaryContainer, if (darkTheme) 0.14f else 0.20f),
        surfaceContainerHigh = lerp(surfaceBase, secondaryContainer, if (darkTheme) 0.18f else 0.28f),
        surfaceContainerHighest = lerp(surfaceBase, tertiaryContainer, if (darkTheme) 0.22f else 0.34f)
    )
}

private fun manualColorScheme(
    seedColor: Color,
    darkTheme: Boolean,
    colorSpec: MaterialColorSpec,
    palette: MaterialPalette
): ColorScheme {
    val paletteColors = manualPaletteColors(
        seedColor = seedColor,
        darkTheme = darkTheme,
        colorSpec = effectiveMaterialColorSpec(colorSpec, palette),
        palette = palette
    )
    val primary = paletteColors.primary
    val secondary = paletteColors.secondary
    val tertiary = paletteColors.tertiary
    val surface = paletteColors.surface
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

private data class ManualPaletteColors(
    val primary: Color,
    val secondary: Color,
    val tertiary: Color,
    val surface: Color
)

private fun manualPaletteColors(
    seedColor: Color,
    darkTheme: Boolean,
    colorSpec: MaterialColorSpec,
    palette: MaterialPalette
): ManualPaletteColors {
    val primary = if (darkTheme) lerp(seedColor, Color.White, 0.35f) else seedColor
    val darkNeutral = if (darkTheme) Color.White else Color.Black
    val monoPrimary = if (darkTheme) Color(0xFFE2E2E2) else Color(0xFF4D4D4D)

    return when (palette) {
        MaterialPalette.TonalSpot -> if (colorSpec == MaterialColorSpec.Spec2025) {
            ManualPaletteColors(
                primary = primary,
                secondary = lerp(primary, if (darkTheme) Color(0xFFE7C16D) else Color(0xFF765A00), 0.48f),
                tertiary = lerp(primary, if (darkTheme) Color(0xFFFFB1C2) else Color(0xFF9D4058), 0.58f),
                surface = if (darkTheme) Color(0xFF111512) else Color(0xFFFBFDF8)
            )
        } else ManualPaletteColors(
            primary = primary,
            secondary = lerp(primary, darkNeutral, 0.48f),
            tertiary = lerp(primary, Color(0xFF9C27B0), 0.24f),
            surface = if (darkTheme) Color(0xFF111418) else Color(0xFFFAF8FF)
        )
        MaterialPalette.Expressive -> if (colorSpec == MaterialColorSpec.Spec2025) ManualPaletteColors(
            primary = primary,
            secondary = lerp(primary, if (darkTheme) Color(0xFFE7C16D) else Color(0xFF765A00), 0.48f),
            tertiary = lerp(primary, if (darkTheme) Color(0xFFFFB1C2) else Color(0xFF9D4058), 0.58f),
            surface = if (darkTheme) Color(0xFF111512) else Color(0xFFFBFDF8)
        ) else ManualPaletteColors(
            primary = primary,
            secondary = lerp(primary, darkNeutral, 0.48f),
            tertiary = lerp(primary, Color(0xFF9C27B0), 0.24f),
            surface = if (darkTheme) Color(0xFF111418) else Color(0xFFFAF8FF)
        )
        MaterialPalette.Neutral -> ManualPaletteColors(
            primary = lerp(primary, if (darkTheme) Color.White else Color.Black, 0.45f),
            secondary = lerp(primary, darkNeutral, 0.70f),
            tertiary = lerp(primary, darkNeutral, 0.62f),
            surface = if (darkTheme) Color(0xFF17181A) else Color(0xFFFCFCFD)
        )
        MaterialPalette.Vibrant -> ManualPaletteColors(
            primary = primary,
            secondary = lerp(primary, if (darkTheme) Color(0xFFFFB1C8) else Color(0xFFA7335F), 0.52f),
            tertiary = lerp(primary, if (darkTheme) Color(0xFF8ED8FF) else Color(0xFF00658A), 0.62f),
            surface = if (darkTheme) Color(0xFF121317) else Color(0xFFFFF8FB)
        )
        MaterialPalette.Rainbow -> ManualPaletteColors(
            primary = primary,
            secondary = lerp(primary, if (darkTheme) Color(0xFF9DD67D) else Color(0xFF3E6F00), 0.62f),
            tertiary = lerp(primary, if (darkTheme) Color(0xFFD9B8FF) else Color(0xFF72529B), 0.58f),
            surface = if (darkTheme) Color(0xFF111511) else Color(0xFFFCFCEF)
        )
        MaterialPalette.FruitSalad -> ManualPaletteColors(
            primary = lerp(primary, if (darkTheme) Color(0xFFB8D96B) else Color(0xFF5D6F00), 0.20f),
            secondary = lerp(primary, if (darkTheme) Color(0xFFB8D96B) else Color(0xFF5D6F00), 0.58f),
            tertiary = lerp(primary, if (darkTheme) Color(0xFFFFB0B6) else Color(0xFF9A4048), 0.58f),
            surface = if (darkTheme) Color(0xFF15140F) else Color(0xFFFFFAEF)
        )
        MaterialPalette.MonoChrome -> ManualPaletteColors(
            primary = monoPrimary,
            secondary = if (darkTheme) Color(0xFFC6C6C6) else Color(0xFF5F5F5F),
            tertiary = if (darkTheme) Color(0xFFDBDBDB) else Color(0xFF525252),
            surface = if (darkTheme) Color(0xFF111111) else Color(0xFFFCFCFC)
        )
        MaterialPalette.Fidelity -> ManualPaletteColors(
            primary = primary,
            secondary = lerp(primary, darkNeutral, 0.26f),
            tertiary = lerp(primary, Color(0xFF9C27B0), 0.18f),
            surface = if (darkTheme) Color(0xFF111418) else Color(0xFFFCFBFF)
        )
        MaterialPalette.Content -> ManualPaletteColors(
            primary = primary,
            secondary = lerp(primary, darkNeutral, 0.42f),
            tertiary = lerp(primary, Color(0xFF9C27B0), 0.38f),
            surface = if (darkTheme) Color(0xFF111418) else Color(0xFFFBFCFF)
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
