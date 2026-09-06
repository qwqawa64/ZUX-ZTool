package com.qimian233.ztool.ui.theme

import android.content.Context
import android.os.Build
import android.provider.Settings
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.core.content.ContextCompat
import com.materialkolor.dynamiccolor.ColorSpec
import com.materialkolor.hct.Hct
import com.materialkolor.scheme.DynamicScheme
import com.materialkolor.scheme.SchemeContent
import com.materialkolor.scheme.SchemeExpressive
import com.materialkolor.scheme.SchemeFidelity
import com.materialkolor.scheme.SchemeFruitSalad
import com.materialkolor.scheme.SchemeMonochrome
import com.materialkolor.scheme.SchemeNeutral
import com.materialkolor.scheme.SchemeRainbow
import com.materialkolor.scheme.SchemeTonalSpot
import com.materialkolor.scheme.SchemeVibrant
import org.json.JSONObject

/**
 * The single color pipeline of the app: seed color -> Material tonal palettes
 * (via material-color-utilities HCT, honoring the selected palette style and color
 * spec) -> Material3 [ColorScheme]. The Miuix front end receives the same scheme
 * through `toMiuixColors`, so both front ends always render from one derived scheme.
 */

/** Brand seed used when neither wallpaper extraction nor manual color is active. */
private val DefaultSeedColor = colorFromArgbLong(ZToolThemeSettings.DEFAULT_MANUAL_SEED_COLOR)

private const val DEFAULT_CONTRAST_LEVEL = 0.0

private const val SYSTEM_PALETTE_SETTINGS_KEY = "theme_customization_overlay_packages"
private const val SYSTEM_PALETTE_SEED_KEY = "android.theme.customization.system_palette"

/**
 * Builds the effective Material3 [ColorScheme] for [settings].
 *
 * Seed priority: manual seed color > system (wallpaper) seed > brand default.
 */
internal fun buildZToolColorScheme(
    settings: ZToolThemeSettings,
    darkTheme: Boolean,
    systemSeed: Color?
): ColorScheme {
    val seed = when {
        settings.manualColorEnabled -> colorFromArgbLong(settings.manualSeedColor)
        settings.dynamicColorEnabled -> systemSeed ?: DefaultSeedColor
        else -> DefaultSeedColor
    }
    val scheme = buildDynamicScheme(
        seed = seed,
        darkTheme = darkTheme,
        palette = settings.materialPalette,
        specVersion = effectiveMaterialColorSpec(settings.materialColorSpec, settings.materialPalette).toSpecVersion()
    )
    val colorScheme = scheme.toMaterial3ColorScheme(darkTheme)
    return if (darkTheme && settings.amoledBlackEnabled) {
        colorScheme.withAmoledBlackSurfaces()
    } else {
        colorScheme
    }
}

/**
 * Reads the seed color the system itself uses for Material You, i.e. the wallpaper
 * `system_palette` seed. Falls back to the framework accent palette on Android 12+.
 * Returns null when the platform cannot provide a seed (below Android 12 or read failure).
 */
internal fun readSystemPaletteSeed(context: Context): Color? {
    return try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val json = Settings.Secure.getString(context.contentResolver, SYSTEM_PALETTE_SETTINGS_KEY)
            val hex = json?.let { JSONObject(it).optString(SYSTEM_PALETTE_SEED_KEY, "") } ?: ""
            if (hex.isNotBlank()) {
                Color(android.graphics.Color.parseColor(if (hex.startsWith("#")) hex else "#$hex"))
            } else {
                Color(ContextCompat.getColor(context, android.R.color.system_accent1_500))
            }
        } else {
            null
        }
    } catch (_: Exception) {
        null
    }
}

/**
 * SPEC_2025 is only honored for palette styles whose implementation supports it;
 * everything else downgrades to SPEC_2021 (same rule the Miuix front end applies).
 */
private fun effectiveMaterialColorSpec(
    colorSpec: MaterialColorSpec,
    palette: MaterialPalette
): MaterialColorSpec {
    if (colorSpec == MaterialColorSpec.Spec2021) {
        return MaterialColorSpec.Spec2021
    }
    return if (palette.supportsSpec2025) MaterialColorSpec.Spec2025 else MaterialColorSpec.Spec2021
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

private fun MaterialColorSpec.toSpecVersion(): ColorSpec.SpecVersion = when (this) {
    MaterialColorSpec.Spec2021 -> ColorSpec.SpecVersion.SPEC_2021
    MaterialColorSpec.Spec2025 -> ColorSpec.SpecVersion.SPEC_2025
}

private fun buildDynamicScheme(
    seed: Color,
    darkTheme: Boolean,
    palette: MaterialPalette,
    specVersion: ColorSpec.SpecVersion
): DynamicScheme {
    val sourceHct = Hct.fromInt(seed.toArgb())
    val platform = DynamicScheme.Platform.PHONE
    return when (palette) {
        MaterialPalette.TonalSpot -> SchemeTonalSpot(
            sourceColorHct = sourceHct,
            isDark = darkTheme,
            contrastLevel = DEFAULT_CONTRAST_LEVEL,
            specVersion = specVersion,
            platform = platform
        )
        MaterialPalette.Neutral -> SchemeNeutral(
            sourceColorHct = sourceHct,
            isDark = darkTheme,
            contrastLevel = DEFAULT_CONTRAST_LEVEL,
            specVersion = specVersion,
            platform = platform
        )
        MaterialPalette.Vibrant -> SchemeVibrant(
            sourceColorHct = sourceHct,
            isDark = darkTheme,
            contrastLevel = DEFAULT_CONTRAST_LEVEL,
            specVersion = specVersion,
            platform = platform
        )
        MaterialPalette.Expressive -> SchemeExpressive(
            sourceColorHct = sourceHct,
            isDark = darkTheme,
            contrastLevel = DEFAULT_CONTRAST_LEVEL,
            specVersion = specVersion,
            platform = platform
        )
        MaterialPalette.Rainbow -> SchemeRainbow(
            sourceColorHct = sourceHct,
            isDark = darkTheme,
            contrastLevel = DEFAULT_CONTRAST_LEVEL,
            specVersion = specVersion,
            platform = platform
        )
        MaterialPalette.FruitSalad -> SchemeFruitSalad(
            sourceColorHct = sourceHct,
            isDark = darkTheme,
            contrastLevel = DEFAULT_CONTRAST_LEVEL,
            specVersion = specVersion,
            platform = platform
        )
        MaterialPalette.MonoChrome -> SchemeMonochrome(
            sourceColorHct = sourceHct,
            isDark = darkTheme,
            contrastLevel = DEFAULT_CONTRAST_LEVEL,
            specVersion = specVersion,
            platform = platform
        )
        MaterialPalette.Fidelity -> SchemeFidelity(
            sourceColorHct = sourceHct,
            isDark = darkTheme,
            contrastLevel = DEFAULT_CONTRAST_LEVEL,
            specVersion = specVersion,
            platform = platform
        )
        MaterialPalette.Content -> SchemeContent(
            sourceColorHct = sourceHct,
            isDark = darkTheme,
            contrastLevel = DEFAULT_CONTRAST_LEVEL,
            specVersion = specVersion,
            platform = platform
        )
    }
}

/**
 * Maps every Material3 role straight from the spec-defined [DynamicScheme] tokens,
 * so no role is hand-tuned and the output follows the selected spec version exactly.
 */
private fun DynamicScheme.toMaterial3ColorScheme(darkTheme: Boolean): ColorScheme {
    return if (darkTheme) {
        darkColorScheme(
            primary = Color(primary),
            onPrimary = Color(onPrimary),
            primaryContainer = Color(primaryContainer),
            onPrimaryContainer = Color(onPrimaryContainer),
            inversePrimary = Color(inversePrimary),
            secondary = Color(secondary),
            onSecondary = Color(onSecondary),
            secondaryContainer = Color(secondaryContainer),
            onSecondaryContainer = Color(onSecondaryContainer),
            tertiary = Color(tertiary),
            onTertiary = Color(onTertiary),
            tertiaryContainer = Color(tertiaryContainer),
            onTertiaryContainer = Color(onTertiaryContainer),
            background = Color(background),
            onBackground = Color(onBackground),
            surface = Color(surface),
            onSurface = Color(onSurface),
            surfaceVariant = Color(surfaceVariant),
            onSurfaceVariant = Color(onSurfaceVariant),
            surfaceDim = Color(surfaceDim),
            surfaceBright = Color(surfaceBright),
            surfaceContainerLowest = Color(surfaceContainerLowest),
            surfaceContainerLow = Color(surfaceContainerLow),
            surfaceContainer = Color(surfaceContainer),
            surfaceContainerHigh = Color(surfaceContainerHigh),
            surfaceContainerHighest = Color(surfaceContainerHighest),
            surfaceTint = Color(surfaceTint),
            inverseSurface = Color(inverseSurface),
            inverseOnSurface = Color(inverseOnSurface),
            outline = Color(outline),
            outlineVariant = Color(outlineVariant),
            scrim = Color(scrim),
            error = Color(error),
            onError = Color(onError),
            errorContainer = Color(errorContainer),
            onErrorContainer = Color(onErrorContainer)
        )
    } else {
        lightColorScheme(
            primary = Color(primary),
            onPrimary = Color(onPrimary),
            primaryContainer = Color(primaryContainer),
            onPrimaryContainer = Color(onPrimaryContainer),
            inversePrimary = Color(inversePrimary),
            secondary = Color(secondary),
            onSecondary = Color(onSecondary),
            secondaryContainer = Color(secondaryContainer),
            onSecondaryContainer = Color(onSecondaryContainer),
            tertiary = Color(tertiary),
            onTertiary = Color(onTertiary),
            tertiaryContainer = Color(tertiaryContainer),
            onTertiaryContainer = Color(onTertiaryContainer),
            background = Color(background),
            onBackground = Color(onBackground),
            surface = Color(surface),
            onSurface = Color(onSurface),
            surfaceVariant = Color(surfaceVariant),
            onSurfaceVariant = Color(onSurfaceVariant),
            surfaceDim = Color(surfaceDim),
            surfaceBright = Color(surfaceBright),
            surfaceContainerLowest = Color(surfaceContainerLowest),
            surfaceContainerLow = Color(surfaceContainerLow),
            surfaceContainer = Color(surfaceContainer),
            surfaceContainerHigh = Color(surfaceContainerHigh),
            surfaceContainerHighest = Color(surfaceContainerHighest),
            surfaceTint = Color(surfaceTint),
            inverseSurface = Color(inverseSurface),
            inverseOnSurface = Color(inverseOnSurface),
            outline = Color(outline),
            outlineVariant = Color(outlineVariant),
            scrim = Color(scrim),
            error = Color(error),
            onError = Color(onError),
            errorContainer = Color(errorContainer),
            onErrorContainer = Color(onErrorContainer)
        )
    }
}

/** Pure-black surface treatment for AMOLED panels; accent roles stay untouched. */
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

internal fun colorFromArgbLong(value: Long): Color {
    return Color((value and 0xFFFFFFFFL).toInt())
}
