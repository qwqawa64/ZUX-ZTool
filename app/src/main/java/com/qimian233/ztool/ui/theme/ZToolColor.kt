package com.qimian233.ztool.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * CompositionLocal that provides the canonical [ColorScheme] regardless of whether
 * the current frontend style is Material3Expressive or Miuix.
 *
 * ZToolTheme derives one Material3 scheme from the seed color for both styles and
 * provides it here, so common (non-Miuix) components always read the same colours
 * the Miuix components are mapped from. Shared components must read this instead
 * of [androidx.compose.material3.MaterialTheme.colorScheme].
 */
val LocalZToolColorScheme = staticCompositionLocalOf<ColorScheme> {
    // Crash-guard fallback; ZToolTheme always provides the derived scheme in practice.
    lightColorScheme()
}
