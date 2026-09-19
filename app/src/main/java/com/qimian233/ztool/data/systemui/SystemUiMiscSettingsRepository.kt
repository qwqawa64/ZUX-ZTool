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
            forceLongScreenshot = prefsUtils.loadBooleanSetting(KEY_FORCE_LONG_SCREENSHOT, false),
        )
    }

    fun saveGuestModeController(enabled: Boolean) = prefsUtils.saveBooleanSetting(KEY_GUEST_MODE_CONTROLLER, enabled)
    fun saveDisableBiometricErrorVibration(enabled: Boolean) = prefsUtils.saveBooleanSetting(KEY_DISABLE_BIOMETRIC_ERROR_VIBRATION, enabled)
    fun saveBypassFaceAuthTimeout(enabled: Boolean) = prefsUtils.saveBooleanSetting(KEY_BYPASS_FACE_AUTH_TIMEOUT, enabled)
    fun saveForceLongScreenshot(enabled: Boolean) = prefsUtils.saveBooleanSetting(KEY_FORCE_LONG_SCREENSHOT, enabled)

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
        private val KEY_FORCE_LONG_SCREENSHOT = PreferenceKeys.FORCE_LONG_SCREENSHOT.name
    }
}

data class SystemUiMiscSettingsUiState(
    val guestModeController: Boolean = false,
    val disableBiometricErrorVibration: Boolean = false,
    val bypassFaceAuthTimeout: Boolean = false,
    val forceLongScreenshot: Boolean = false,
    val isRestartProcessing: Boolean = false,
    val showRestartDialog: Boolean = false
)
