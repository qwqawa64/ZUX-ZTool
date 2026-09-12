package com.qimian233.ztool.ui.theme

import android.content.res.Configuration
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
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
    val tabletOnly: Boolean = true,
    val dynamicColorEnabled: Boolean = true,
    val manualColorEnabled: Boolean = false
)

val LocalZToolThemeSpec = staticCompositionLocalOf {
    ZToolThemeSpec(style = FrontendStyle.Material3Expressive)
}

val LocalIsPlatformDialog = staticCompositionLocalOf { false }

/** Whether the Miuix floating bottom bar (Liquid Glass) is enabled. Only meaningful in Miuix mode. */
val LocalEnableFloatingBottomBar = staticCompositionLocalOf { false }

/** Whether blur effects are enabled on the floating bottom bar. Requires Android 13+. Only meaningful when [LocalEnableFloatingBottomBar] is true. */
val LocalEnableFloatingBottomBarBlur = staticCompositionLocalOf { false }

@Composable
fun ZToolTheme(
    style: FrontendStyle = FrontendStyle.Material3Expressive,
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    settings: ZToolThemeSettings? = null,
    isPlatformDialog: Boolean = false,
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

    // The wallpaper seed is only relevant when dynamic color is on and the user has
    // not pinned a manual seed; all other paths carry their own seed.
    val systemSeed = if (effectiveSettings.dynamicColorEnabled && !effectiveSettings.manualColorEnabled) {
        remember(context) { readSystemPaletteSeed(context) }
    } else {
        null
    }

    val isMiuixStyle = effectiveSettings.frontendStyle == FrontendStyle.Miuix

    // Circuit breaker: when dynamic color is off and the Miuix front end is active,
    // the externally derived palette is bypassed entirely — the shared scheme that
    // every component (cards, settings rows, scaffold, dialogs) reads is rebuilt
    // from Miuix's built-in default palette instead of the seed-derived scheme.
    val miuixBreakerActive = isMiuixStyle && !effectiveSettings.dynamicColorEnabled
    val colorScheme = remember(effectiveSettings, effectiveDarkTheme, systemSeed) {
        if (miuixBreakerActive) {
            val miuixDefault = if (effectiveDarkTheme) {
                miuixDarkColorScheme()
            } else {
                miuixLightColorScheme()
            }
            val m3Scheme = miuixDefault.toMaterialColorScheme(darkTheme = effectiveDarkTheme)
            if (effectiveDarkTheme && effectiveSettings.amoledBlackEnabled) {
                m3Scheme.withAmoledBlackSurfaces()
            } else {
                m3Scheme
            }
        } else {
            buildZToolColorScheme(
                settings = effectiveSettings,
                darkTheme = effectiveDarkTheme,
                systemSeed = systemSeed
            )
        }
    }

    val themeSpec = ZToolThemeSpec(
        style = effectiveSettings.frontendStyle,
        dynamicColorEnabled = effectiveSettings.dynamicColorEnabled,
        manualColorEnabled = effectiveSettings.manualColorEnabled
    )
    val movableContent = remember(content) { movableContentOf(content) }

    // One pipeline for both front ends: Material3 owns the derived scheme, Miuix
    // components receive the same scheme mapped onto Miuix color roles.
    // Circuit breaker: when dynamic color is disabled, Miuix stops consuming the
    // externally derived palette and falls back to its built-in default palette.
    val miuixColors = if (effectiveSettings.dynamicColorEnabled) {
        colorScheme.toMiuixColors(darkTheme = effectiveDarkTheme)
    } else if (effectiveDarkTheme) {
        miuixDarkColorScheme()
    } else {
        miuixLightColorScheme()
    }
    val themedContent: @Composable () -> Unit = {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = MaterialTheme.typography,
        ) {
            CompositionLocalProvider(LocalZToolColorScheme provides colorScheme) {
                MiuixTheme(
                    colors = miuixColors,
                    content = {
                        if (isMiuixStyle) {
                            top.yukonga.miuix.kmp.basic.Scaffold { _ ->
                                movableContent()
                            }
                        } else {
                            movableContent()
                        }
                    }
                )
            }
        }
    }

    CompositionLocalProvider(
        LocalZToolThemeSpec provides themeSpec,
        LocalIsPlatformDialog provides isPlatformDialog,
        LocalEnableFloatingBottomBar provides effectiveSettings.enableFloatingBottomBar,
        LocalEnableFloatingBottomBarBlur provides effectiveSettings.enableFloatingBottomBarBlur,
        content = themedContent
    )
}

/**
 * Inverse of [ColorScheme.toMiuixColors]: rebuilds a Material3 [ColorScheme] from a
 * Miuix palette. Used by the circuit-breaker path so shared components consume the
 * Miuix built-in default palette when dynamic color is disabled. Roles the Miuix
 * palette does not carry are filled with their closest neighbours.
 */
private fun Colors.toMaterialColorScheme(darkTheme: Boolean): ColorScheme {
    return if (darkTheme) {
        darkColorScheme(
            primary = primary,
            onPrimary = onPrimary,
            primaryContainer = primaryContainer,
            onPrimaryContainer = onPrimaryContainer,
            inversePrimary = onPrimary,
            secondary = secondary,
            onSecondary = onSecondary,
            secondaryContainer = secondaryContainer,
            onSecondaryContainer = onSecondaryContainer,
            tertiary = tertiaryContainer,
            onTertiary = onTertiaryContainer,
            tertiaryContainer = tertiaryContainer,
            onTertiaryContainer = onTertiaryContainer,
            background = background,
            onBackground = onBackground,
            surface = surface,
            onSurface = onSurface,
            surfaceVariant = surfaceVariant,
            onSurfaceVariant = onSurfaceContainer,
            surfaceDim = surface,
            surfaceBright = surfaceContainer,
            surfaceContainerLowest = background,
            surfaceContainerLow = surfaceContainer,
            surfaceContainer = surfaceContainer,
            surfaceContainerHigh = surfaceContainerHigh,
            surfaceContainerHighest = surfaceContainerHighest,
            surfaceTint = primary,
            inverseSurface = onSurface,
            inverseOnSurface = surface,
            outline = outline,
            outlineVariant = dividerLine,
            scrim = Color.Black,
            error = error,
            onError = onError,
            errorContainer = errorContainer,
            onErrorContainer = onErrorContainer
        )
    } else {
        lightColorScheme(
            primary = primary,
            onPrimary = onPrimary,
            primaryContainer = primaryContainer,
            onPrimaryContainer = onPrimaryContainer,
            inversePrimary = onPrimary,
            secondary = secondary,
            onSecondary = onSecondary,
            secondaryContainer = secondaryContainer,
            onSecondaryContainer = onSecondaryContainer,
            tertiary = tertiaryContainer,
            onTertiary = onTertiaryContainer,
            tertiaryContainer = tertiaryContainer,
            onTertiaryContainer = onTertiaryContainer,
            background = background,
            onBackground = onBackground,
            surface = surface,
            onSurface = onSurface,
            surfaceVariant = surfaceVariant,
            onSurfaceVariant = onSurfaceContainer,
            surfaceDim = surface,
            surfaceBright = surfaceContainer,
            surfaceContainerLowest = background,
            surfaceContainerLow = surfaceContainer,
            surfaceContainer = surfaceContainer,
            surfaceContainerHigh = surfaceContainerHigh,
            surfaceContainerHighest = surfaceContainerHighest,
            surfaceTint = primary,
            inverseSurface = onSurface,
            inverseOnSurface = surface,
            outline = outline,
            outlineVariant = dividerLine,
            scrim = Color.Black,
            error = error,
            onError = onError,
            errorContainer = errorContainer,
            onErrorContainer = onErrorContainer
        )
    }
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
