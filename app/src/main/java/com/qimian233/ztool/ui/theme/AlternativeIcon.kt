package com.qimian233.ztool.ui.theme

import androidx.annotation.DrawableRes
import androidx.compose.runtime.staticCompositionLocalOf
import com.qimian233.ztool.R

/**
 * Whether the alternative brand artwork ("使用替代图标") is active. Provided by
 * [ZToolTheme] from [ZToolThemeSettings.useAlternativeIcon] so any screen can
 * resolve the right logo without touching preferences directly.
 */
val LocalUseAlternativeIcon = staticCompositionLocalOf { false }

/**
 * Resolves the in-app brand logo used by the Firstrun splash page and the About
 * header for the current icon choice. The launcher icon is switched separately
 * through the launcher activity-alias state.
 */
@DrawableRes
fun appLogoRes(useAlternativeIcon: Boolean): Int =
    if (useAlternativeIcon) R.drawable.splash_logo_alt else R.drawable.splash_logo
