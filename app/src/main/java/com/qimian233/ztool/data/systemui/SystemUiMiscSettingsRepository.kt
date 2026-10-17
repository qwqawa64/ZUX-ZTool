package com.qimian233.ztool.data.systemui

import android.content.Context
import com.qimian233.ztool.screens.features.FeatureDestination
import com.qimian233.ztool.data.keys.PreferenceKeys
import com.qimian233.ztool.utils.ModulePreferencesUtils
import com.qimian233.ztool.utils.ScopeUtils

class SystemUiMiscSettingsRepository(context: Context) {
    private val prefsUtils = ModulePreferencesUtils(context)

    fun loadState(): SystemUiMiscSettingsUiState {
        return SystemUiMiscSettingsUiState(
            guestModeController = prefsUtils.loadBooleanSetting(KEY_GUEST_MODE_CONTROLLER, false),
            disableBiometricErrorVibration = prefsUtils.loadBooleanSetting(KEY_DISABLE_BIOMETRIC_ERROR_VIBRATION, false),
            bypassFaceAuthTimeout = prefsUtils.loadBooleanSetting(KEY_BYPASS_FACE_AUTH_TIMEOUT, false),
            aospScrollCapture = prefsUtils.loadBooleanSetting(KEY_FORCE_LONG_SCREENSHOT_AOSP, false),
            shadeReboundFix = prefsUtils.loadBooleanSetting(KEY_SHADE_REBOUND_FIX, false),
            freeformFullscreenThreshold = prefsUtils.loadBooleanSetting(
                KEY_LAUNCHER_FREEFORM_FULLSCREEN_THRESHOLD,
                false
            ),
            freeformFullscreenWidthPercent = normalizeFreeformFullscreenPercent(
                prefsUtils.loadIntegerSetting(
                    KEY_LAUNCHER_FREEFORM_FULLSCREEN_WIDTH_PERCENT,
                    FREEFORM_FULLSCREEN_PERCENT_DEFAULT
                )
            ),
            freeformFullscreenHeightPercent = normalizeFreeformFullscreenPercent(
                prefsUtils.loadIntegerSetting(
                    KEY_LAUNCHER_FREEFORM_FULLSCREEN_HEIGHT_PERCENT,
                    FREEFORM_FULLSCREEN_PERCENT_DEFAULT
                )
            ),
            freeformBottomEdgeResize = prefsUtils.loadBooleanSetting(
                KEY_SYSTEM_UI_FREEFORM_BOTTOM_EDGE_RESIZE,
                false
            ),
            cornerHandleAutoHide = prefsUtils.loadBooleanSetting(
                KEY_SYSTEM_UI_CORNER_HANDLE_AUTO_HIDE,
                true
            ),
            freeformNoForehead = prefsUtils.loadBooleanSetting(
                KEY_SYSTEM_UI_FREEFORM_NO_FOREHEAD,
                false
            ),
        )
    }

    fun saveGuestModeController(enabled: Boolean) = prefsUtils.saveBooleanSetting(KEY_GUEST_MODE_CONTROLLER, enabled)
    fun saveDisableBiometricErrorVibration(enabled: Boolean) = prefsUtils.saveBooleanSetting(KEY_DISABLE_BIOMETRIC_ERROR_VIBRATION, enabled)
    fun saveBypassFaceAuthTimeout(enabled: Boolean) = prefsUtils.saveBooleanSetting(KEY_BYPASS_FACE_AUTH_TIMEOUT, enabled)
    fun saveAospScrollCapture(enabled: Boolean) = prefsUtils.saveBooleanSetting(KEY_FORCE_LONG_SCREENSHOT_AOSP, enabled)
    fun saveShadeReboundFix(enabled: Boolean) = prefsUtils.saveBooleanSetting(KEY_SHADE_REBOUND_FIX, enabled)

    fun saveFreeformFullscreenThreshold(enabled: Boolean) {
        prefsUtils.saveBooleanSetting(KEY_LAUNCHER_FREEFORM_FULLSCREEN_THRESHOLD, enabled)
    }

    fun saveFreeformFullscreenWidthPercent(percent: Int) {
        prefsUtils.saveIntegerSetting(
            KEY_LAUNCHER_FREEFORM_FULLSCREEN_WIDTH_PERCENT,
            normalizeFreeformFullscreenPercent(percent)
        )
    }

    fun saveFreeformFullscreenHeightPercent(percent: Int) {
        prefsUtils.saveIntegerSetting(
            KEY_LAUNCHER_FREEFORM_FULLSCREEN_HEIGHT_PERCENT,
            normalizeFreeformFullscreenPercent(percent)
        )
    }

    fun saveFreeformBottomEdgeResize(enabled: Boolean) {
        prefsUtils.saveBooleanSetting(KEY_SYSTEM_UI_FREEFORM_BOTTOM_EDGE_RESIZE, enabled)
    }

    fun saveCornerHandleAutoHide(enabled: Boolean) {
        prefsUtils.saveBooleanSetting(KEY_SYSTEM_UI_CORNER_HANDLE_AUTO_HIDE, enabled)
    }

    fun saveFreeformNoForehead(enabled: Boolean) {
        prefsUtils.saveBooleanSetting(KEY_SYSTEM_UI_FREEFORM_NO_FOREHEAD, enabled)
    }

    fun forceStopScope(): ShellActionResult {
        val scopes = ScopeUtils.getScopes(FeatureDestination.SystemUi)
        return when (val result = ScopeUtils.restartScope(scopes)) {
            is ScopeUtils.RestartResult.Success -> ShellActionResult(success = true, error = "", exitCode = 0)
            is ScopeUtils.RestartResult.PartialSuccess -> ShellActionResult(success = false, error = "Partial failure: ${result.failed.joinToString()}", exitCode = -1)
            is ScopeUtils.RestartResult.Failure -> ShellActionResult(success = false, error = result.message, exitCode = -1)
        }
    }

    companion object {
        private val KEY_GUEST_MODE_CONTROLLER = PreferenceKeys.GUEST_MODE_CONTROLLER.name
        private val KEY_DISABLE_BIOMETRIC_ERROR_VIBRATION = PreferenceKeys.DISABLE_BIOMETRIC_ERROR_VIBRATION.name
        private val KEY_BYPASS_FACE_AUTH_TIMEOUT = PreferenceKeys.BYPASS_FACE_AUTH_TIMEOUT.name
        private val KEY_FORCE_LONG_SCREENSHOT_AOSP = PreferenceKeys.FORCE_LONG_SCREENSHOT_AOSP.name
        private val KEY_SHADE_REBOUND_FIX = PreferenceKeys.SHADE_REBOUND_FIX.name
        private val KEY_LAUNCHER_FREEFORM_FULLSCREEN_THRESHOLD =
            PreferenceKeys.LAUNCHER_FREEFORM_FULLSCREEN_THRESHOLD.name
        private val KEY_LAUNCHER_FREEFORM_FULLSCREEN_WIDTH_PERCENT =
            PreferenceKeys.LAUNCHER_FREEFORM_FULLSCREEN_WIDTH_PERCENT.name
        private val KEY_LAUNCHER_FREEFORM_FULLSCREEN_HEIGHT_PERCENT =
            PreferenceKeys.LAUNCHER_FREEFORM_FULLSCREEN_HEIGHT_PERCENT.name
        private val KEY_SYSTEM_UI_FREEFORM_BOTTOM_EDGE_RESIZE =
            PreferenceKeys.SYSTEM_UI_FREEFORM_BOTTOM_EDGE_RESIZE.name
        private val KEY_SYSTEM_UI_CORNER_HANDLE_AUTO_HIDE =
            PreferenceKeys.SYSTEM_UI_CORNER_HANDLE_AUTO_HIDE.name
        private val KEY_SYSTEM_UI_FREEFORM_NO_FOREHEAD =
            PreferenceKeys.SYSTEM_UI_FREEFORM_NO_FOREHEAD.name

        // Fullscreen thresholds are percentages of the current display; 100 % disables
        // that direction and both at 100 % keeps the window out of fullscreen.
        const val FREEFORM_FULLSCREEN_PERCENT_MIN = 50
        const val FREEFORM_FULLSCREEN_PERCENT_MAX = 100
        private const val FREEFORM_FULLSCREEN_PERCENT_DEFAULT = 85
        private const val FREEFORM_FULLSCREEN_PERCENT_STEP = 5

        /** Snaps a slider percentage to the 5 % step and clamps it to the slider range. */
        fun normalizeFreeformFullscreenPercent(percent: Int): Int =
            ((percent + FREEFORM_FULLSCREEN_PERCENT_STEP / 2) / FREEFORM_FULLSCREEN_PERCENT_STEP *
                FREEFORM_FULLSCREEN_PERCENT_STEP)
                .coerceIn(FREEFORM_FULLSCREEN_PERCENT_MIN, FREEFORM_FULLSCREEN_PERCENT_MAX)
    }
}

data class SystemUiMiscSettingsUiState(
    val guestModeController: Boolean = false,
    val disableBiometricErrorVibration: Boolean = false,
    val bypassFaceAuthTimeout: Boolean = false,
    val aospScrollCapture: Boolean = false,
    val shadeReboundFix: Boolean = false,
    val freeformFullscreenThreshold: Boolean = false,
    val freeformFullscreenWidthPercent: Int = 85,
    val freeformFullscreenHeightPercent: Int = 85,
    val freeformBottomEdgeResize: Boolean = false,
    val cornerHandleAutoHide: Boolean = true,
    val freeformNoForehead: Boolean = false,
    val isRestartProcessing: Boolean = false,
    val showRestartDialog: Boolean = false
)
