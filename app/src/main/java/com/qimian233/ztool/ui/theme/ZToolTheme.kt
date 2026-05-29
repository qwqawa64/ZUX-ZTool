package com.qimian233.ztool.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color
import android.os.Build

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
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val colorScheme = when (style) {
        FrontendStyle.Material3Expressive -> {
            if (dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            } else {
                if (darkTheme) Md3eDarkColors else Md3eLightColors
            }
        }
        FrontendStyle.Miuix -> if (darkTheme) Md3eDarkColors else Md3eLightColors
    }

    androidx.compose.runtime.CompositionLocalProvider(
        LocalZToolThemeSpec provides ZToolThemeSpec(style = style)
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = MaterialTheme.typography,
            content = content
        )
    }
}
