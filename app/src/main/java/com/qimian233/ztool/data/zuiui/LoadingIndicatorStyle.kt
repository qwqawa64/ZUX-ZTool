package com.qimian233.ztool.data.zuiui

import androidx.annotation.StringRes
import com.qimian233.ztool.R

/**
 * Loading-animation styles the hook can install in place of the ZUI spinner.
 *
 * [prefValue] is persisted in [com.qimian233.ztool.data.keys.PreferenceKeys.ZUI_LOADING_INDICATOR_STYLE];
 * the hook maps it back with [fromPref]. Adding a style means adding an entry here, a
 * string for [labelRes], and a branch in the hook's drawable factory.
 */
enum class LoadingIndicatorStyle(
    val prefValue: String,
    @param:StringRes val labelRes: Int
) {
    CUBE_GRID("cube_grid", R.string.ui_components_loading_indicator_style_cube_grid),
    CIRCULAR("circular", R.string.ui_components_loading_indicator_style_circular);

    companion object {
        val DEFAULT = CUBE_GRID

        /** Unknown or missing values degrade to [DEFAULT] so a stale pref never disables the hook. */
        fun fromPref(value: String?): LoadingIndicatorStyle =
            value?.let { stored -> entries.firstOrNull { it.prefValue == stored } } ?: DEFAULT
    }
}
