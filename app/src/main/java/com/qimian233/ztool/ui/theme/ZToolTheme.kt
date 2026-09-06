package com.qimian233.ztool.ui.theme

import android.content.res.Configuration
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
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

    val colorScheme = remember(effectiveSettings, effectiveDarkTheme, systemSeed) {
        buildZToolColorScheme(
            settings = effectiveSettings,
            darkTheme = effectiveDarkTheme,
            systemSeed = systemSeed
        )
    }

    val themeSpec = ZToolThemeSpec(
        style = effectiveSettings.frontendStyle,
        dynamicColorEnabled = effectiveSettings.dynamicColorEnabled,
        manualColorEnabled = effectiveSettings.manualColorEnabled
    )
    val isMiuixStyle = effectiveSettings.frontendStyle == FrontendStyle.Miuix
    val movableContent = remember(content) { movableContentOf(content) }

    // One pipeline for both front ends: Material3 owns the derived scheme, Miuix
    // components receive the same scheme mapped onto Miuix color roles.
    val themedContent: @Composable () -> Unit = {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = MaterialTheme.typography,
        ) {
            CompositionLocalProvider(LocalZToolColorScheme provides colorScheme) {
                MiuixTheme(
                    colors = colorScheme.toMiuixColors(darkTheme = effectiveDarkTheme),
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
