package com.qimian233.ztool.data.zuiui

import androidx.annotation.StringRes
import com.qimian233.ztool.R

/**
 * Switch looks the hook can install for `zui.widget.Switch`.
 *
 * [prefValue] is persisted in `PreferenceKeys.ZUI_SWITCH_STYLE_KIND`; the hook maps it back
 * with [fromPref]. Adding a look means an entry here, a label resource, and a hook branch.
 */
enum class SwitchStyle(
    val prefValue: String,
    @param:StringRes val labelRes: Int
) {
    INDICATOR("indicator", R.string.ui_components_switch_style_indicator),
    M3("m3", R.string.ui_components_switch_style_m3);

    companion object {
        val DEFAULT = INDICATOR

        /** Unknown or missing values degrade to [DEFAULT] so a stale pref never disables the hook. */
        fun fromPref(value: String?): SwitchStyle =
            value?.let { stored -> entries.firstOrNull { it.prefValue == stored } } ?: DEFAULT
    }
}

/**
 * Glyphs the selected look may draw: check when on, cross when off, or both.
 *
 * `NONE` keeps the stock indicator or a plain M3 thumb. [prefValue] is persisted in
 * `PreferenceKeys.ZUI_SWITCH_MARK`.
 */
enum class SwitchMark(
    val prefValue: String,
    @param:StringRes val labelRes: Int
) {
    NONE("none", R.string.ui_components_switch_mark_none),
    CHECK("check", R.string.ui_components_switch_mark_check),
    CROSS("cross", R.string.ui_components_switch_mark_cross),
    BOTH("both", R.string.ui_components_switch_mark_both);

    companion object {
        val DEFAULT = CHECK

        /** Unknown or missing values degrade to [DEFAULT] so a stale pref never disables the hook. */
        fun fromPref(value: String?): SwitchMark =
            value?.let { stored -> entries.firstOrNull { it.prefValue == stored } } ?: DEFAULT
    }
}
