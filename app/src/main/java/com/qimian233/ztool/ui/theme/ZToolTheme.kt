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
            darkTheme = darkTheme
        )
        settings.dynamicColorEnabled -> dynamicColorScheme() ?: defaultColorScheme(
            style = settings.frontendStyle,
            darkTheme = darkTheme
        )
        else -> defaultColorScheme(
            style = settings.frontendStyle,
            darkTheme = darkTheme
        )
    }

    return if (darkTheme && settings.amoledBlackEnabled) {
        baseScheme.withAmoledBlackSurfaces()
    } else {
        baseScheme
    }
}

private fun defaultColorScheme(style: FrontendStyle, darkTheme: Boolean): ColorScheme {
    return when (style) {
        FrontendStyle.Material3Expressive,
        FrontendStyle.Miuix -> if (darkTheme) Md3eDarkColors else Md3eLightColors
    }
}

private fun manualColorScheme(seedColor: Color, darkTheme: Boolean): ColorScheme {
    val primary = if (darkTheme) lerp(seedColor, Color.White, 0.35f) else seedColor
    val secondary = lerp(primary, if (darkTheme) Color.White else Color.Black, 0.35f)
    val tertiary = lerp(primary, Color(0xFF9C27B0), 0.35f)
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
