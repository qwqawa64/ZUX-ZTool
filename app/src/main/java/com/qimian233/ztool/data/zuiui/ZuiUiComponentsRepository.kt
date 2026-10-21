package com.qimian233.ztool.data.zuiui

import android.content.Context
import com.qimian233.ztool.data.advanced.AdvancedSettingsRepository
import com.qimian233.ztool.data.keys.PreferenceKeys
import com.qimian233.ztool.utils.ModulePreferencesUtils
import com.qimian233.ztool.viewmodel.ZuiUiComponentsUiState

/**
 * Persistence and apply action for the cross-app ZUI frontend component styling.
 */
class ZuiUiComponentsRepository(
    context: Context,
    private val advancedSettings: AdvancedSettingsRepository = AdvancedSettingsRepository()
) {
    private val prefsUtils = ModulePreferencesUtils(context)

    fun loadState(): ZuiUiComponentsUiState =
        ZuiUiComponentsUiState(
            dialogSkinEnabled = prefsUtils.loadBooleanSetting(
                KEY_FILLED_BUTTONS.name, KEY_FILLED_BUTTONS.default
            ),
            hideDividerEnabled = prefsUtils.loadBooleanSetting(
                KEY_HIDE_DIVIDER.name, KEY_HIDE_DIVIDER.default
            ),
            followSystemColors = prefsUtils.loadBooleanSetting(
                KEY_FOLLOW_SYSTEM.name, KEY_FOLLOW_SYSTEM.default
            ),
            buttonRadiusDp = prefsUtils.loadIntegerSetting(KEY_RADIUS.name, KEY_RADIUS.default)
                .coerceIn(RADIUS_MIN, RADIUS_MAX),
            positiveColorText = prefsUtils.loadIntegerSetting(
                KEY_POSITIVE_COLOR.name, KEY_POSITIVE_COLOR.default
            ).opaque().toArgbText(),
            negativeColorText = prefsUtils.loadIntegerSetting(
                KEY_NEGATIVE_COLOR.name, KEY_NEGATIVE_COLOR.default
            ).opaque().toArgbText(),
            loadingIndicatorEnabled = prefsUtils.loadBooleanSetting(
                KEY_LOADING_INDICATOR.name, KEY_LOADING_INDICATOR.default
            ),
            loadingIndicatorStyle = LoadingIndicatorStyle.fromPref(
                prefsUtils.loadStringSetting(
                    KEY_LOADING_INDICATOR_STYLE.name, KEY_LOADING_INDICATOR_STYLE.default
                )
            ),
            loadingIndicatorFollowSystemColors = prefsUtils.loadBooleanSetting(
                KEY_LOADING_INDICATOR_FOLLOW_SYSTEM_COLORS.name,
                KEY_LOADING_INDICATOR_FOLLOW_SYSTEM_COLORS.default
            ),
            loadingIndicatorColorText = prefsUtils.loadIntegerSetting(
                KEY_LOADING_INDICATOR_COLOR.name, KEY_LOADING_INDICATOR_COLOR.default
            ).opaque().toArgbText(),
            switchStyleEnabled = prefsUtils.loadBooleanSetting(
                KEY_SWITCH_STYLE.name, KEY_SWITCH_STYLE.default
            ),
            switchStyle = SwitchStyle.fromPref(
                prefsUtils.loadStringSetting(
                    KEY_SWITCH_STYLE_KIND.name, KEY_SWITCH_STYLE_KIND.default
                )
            ),
            switchMark = SwitchMark.fromPref(
                prefsUtils.loadStringSetting(KEY_SWITCH_MARK.name, KEY_SWITCH_MARK.default)
            )
        )

    fun saveDialogSkinEnabled(enabled: Boolean) {
        prefsUtils.saveBooleanSetting(KEY_FILLED_BUTTONS.name, enabled)
    }

    fun saveHideDividerEnabled(enabled: Boolean) {
        prefsUtils.saveBooleanSetting(KEY_HIDE_DIVIDER.name, enabled)
    }

    fun saveFollowSystemColors(enabled: Boolean) {
        prefsUtils.saveBooleanSetting(KEY_FOLLOW_SYSTEM.name, enabled)
    }

    fun saveButtonRadius(radiusDp: Int) {
        prefsUtils.saveIntegerSetting(KEY_RADIUS.name, radiusDp.coerceIn(RADIUS_MIN, RADIUS_MAX))
    }

    fun savePositiveColor(argb: Int) {
        prefsUtils.saveIntegerSetting(KEY_POSITIVE_COLOR.name, argb.opaque())
    }

    fun saveNegativeColor(argb: Int) {
        prefsUtils.saveIntegerSetting(KEY_NEGATIVE_COLOR.name, argb.opaque())
    }

    fun saveLoadingIndicatorEnabled(enabled: Boolean) {
        prefsUtils.saveBooleanSetting(KEY_LOADING_INDICATOR.name, enabled)
    }

    fun saveLoadingIndicatorStyle(style: LoadingIndicatorStyle) {
        prefsUtils.saveStringSetting(KEY_LOADING_INDICATOR_STYLE.name, style.prefValue)
    }

    fun saveLoadingIndicatorFollowSystemColors(enabled: Boolean) {
        prefsUtils.saveBooleanSetting(KEY_LOADING_INDICATOR_FOLLOW_SYSTEM_COLORS.name, enabled)
    }

    fun saveLoadingIndicatorColor(argb: Int) {
        prefsUtils.saveIntegerSetting(KEY_LOADING_INDICATOR_COLOR.name, argb.opaque())
    }

    fun saveSwitchStyleEnabled(enabled: Boolean) {
        prefsUtils.saveBooleanSetting(KEY_SWITCH_STYLE.name, enabled)
    }

    fun saveSwitchStyle(style: SwitchStyle) {
        prefsUtils.saveStringSetting(KEY_SWITCH_STYLE_KIND.name, style.prefValue)
    }

    fun saveSwitchMark(mark: SwitchMark) {
        prefsUtils.saveStringSetting(KEY_SWITCH_MARK.name, mark.prefValue)
    }

    /** Frameworks below API 102 have no hot reload, so callers must be told instead. */
    fun supportsHotReload(): Boolean = advancedSettings.getApiVersion() >= HOT_RELOAD_MIN_API

    /**
     * Pushes the setting into every running target by hot reload, which reinstalls the
     * hooks without killing the host processes.
     */
    fun applyByHotReload(onResult: (succeeded: Int, failed: Int) -> Unit) {
        advancedSettings.performHotReloadAll(
            onProgress = { _, _ -> },
            onComplete = { succeeded, failed, _, _, _ -> onResult(succeeded, failed) }
        )
    }

    companion object {
        /** Slider bounds, shared with the ViewModel and the hook-side clamp. */
        const val RADIUS_MIN = 0
        const val RADIUS_MAX = 32

        private const val HOT_RELOAD_MIN_API = 102

        private val KEY_FILLED_BUTTONS = PreferenceKeys.ZUI_DIALOG_SKIN
        private val KEY_HIDE_DIVIDER = PreferenceKeys.ZUI_DIALOG_HIDE_DIVIDER
        private val KEY_FOLLOW_SYSTEM = PreferenceKeys.ZUI_DIALOG_FOLLOW_SYSTEM_COLORS
        private val KEY_RADIUS = PreferenceKeys.ZUI_DIALOG_BUTTON_RADIUS
        private val KEY_POSITIVE_COLOR = PreferenceKeys.ZUI_DIALOG_POSITIVE_COLOR
        private val KEY_NEGATIVE_COLOR = PreferenceKeys.ZUI_DIALOG_NEGATIVE_COLOR
        private val KEY_LOADING_INDICATOR = PreferenceKeys.ZUI_LOADING_INDICATOR
        private val KEY_LOADING_INDICATOR_STYLE = PreferenceKeys.ZUI_LOADING_INDICATOR_STYLE
        private val KEY_LOADING_INDICATOR_FOLLOW_SYSTEM_COLORS =
            PreferenceKeys.ZUI_LOADING_INDICATOR_FOLLOW_SYSTEM_COLORS
        private val KEY_LOADING_INDICATOR_COLOR = PreferenceKeys.ZUI_LOADING_INDICATOR_COLOR
        private val KEY_SWITCH_STYLE = PreferenceKeys.ZUI_SWITCH_STYLE
        private val KEY_SWITCH_STYLE_KIND = PreferenceKeys.ZUI_SWITCH_STYLE_KIND
        private val KEY_SWITCH_MARK = PreferenceKeys.ZUI_SWITCH_MARK

        /** The pills are opaque by design, so a restored translucent value is normalised. */
        private fun Int.opaque(): Int = this or 0xFF000000.toInt()

        private fun Int.toArgbText(): String = "%08X".format(this)
    }
}
