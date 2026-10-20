package com.qimian233.ztool.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

/** Colors of the home module-status card for one activation state. */
internal data class ModuleStatusPalette(
    val container: Color,
    val content: Color,
    val icon: Color
)

/**
 * Module-status colors for the given activation state.
 *
 * The fixed green / amber / red palette applies only while neither dynamic nor manual
 * color is enabled; any custom palette falls back to the theme's primary container.
 */
@Composable
internal fun moduleStatusPalette(
    bothActive: Boolean,
    anyActive: Boolean
): ModuleStatusPalette {
    val colorScheme = LocalZToolColorScheme.current
    val spec = LocalZToolThemeSpec.current
    val isDefaultColor = !spec.dynamicColorEnabled && !spec.manualColorEnabled
    val isDark = colorScheme.surface.luminance() < 0.5f

    return when {
        bothActive && isDefaultColor -> ModuleStatusPalette(
            container = if (isDark) Color(0xFF1B5E20).copy(alpha = 0.4f) else Color(0xFFA5D6A7),
            content = if (isDark) Color(0xFFA5D6A7) else Color(0xFF1B5E20),
            icon = if (isDark) Color(0xFF66BB6A) else Color(0xFF4CAF50)
        )
        !bothActive && anyActive -> ModuleStatusPalette(
            container = if (isDark) Color(0xFFE65100).copy(alpha = 0.35f) else Color(0xFFFFE082),
            content = if (isDark) Color(0xFFFFE082) else Color(0xFFE65100),
            icon = if (isDark) Color(0xFFFFB300) else Color(0xFFFF8F00)
        )
        !anyActive -> ModuleStatusPalette(
            container = if (isDark) Color(0xFFB71C1C).copy(alpha = 0.4f) else Color(0xFFEF9A9A),
            content = if (isDark) Color(0xFFEF9A9A) else Color(0xFFB71C1C),
            icon = if (isDark) Color(0xFFEF5350) else Color(0xFFE53935)
        )
        else -> ModuleStatusPalette(
            container = colorScheme.primaryContainer,
            content = colorScheme.onPrimaryContainer,
            icon = colorScheme.primary
        )
    }
}
