package com.qimian233.ztool.data.systemui

import android.content.Context
import com.qimian233.ztool.data.PreferenceKeys
import com.qimian233.ztool.utils.ModulePreferencesUtils
import com.qimian233.ztool.utils.ScopeUtils

class SystemUiMiscSettingsRepository(private val context: Context) {
    private val prefsUtils = ModulePreferencesUtils(context)

    fun loadState(): SystemUiMiscSettingsUiState {
        return SystemUiMiscSettingsUiState(
            guestModeController = prefsUtils.loadBooleanSetting(KEY_GUEST_MODE_CONTROLLER, false),
            disableBiometricErrorVibration = prefsUtils.loadBooleanSetting(KEY_DISABLE_BIOMETRIC_ERROR_VIBRATION, false),
        )
    }

    fun saveGuestModeController(enabled: Boolean) = prefsUtils.saveBooleanSetting(KEY_GUEST_MODE_CONTROLLER, enabled)
    fun saveDisableBiometricErrorVibration(enabled: Boolean) = prefsUtils.saveBooleanSetting(KEY_DISABLE_BIOMETRIC_ERROR_VIBRATION, enabled)

    fun forceStopScope(): ShellActionResult {
        val packages = listOf("com.android.systemui", "com.zui.wallpapersetting")
        return when (val result = ScopeUtils.restartScope(packages)) {
            is ScopeUtils.RestartResult.Success -> ShellActionResult(success = true, error = "", exitCode = 0)
            is ScopeUtils.RestartResult.PartialSuccess -> ShellActionResult(success = false, error = "Partial failure: ${result.failed.joinToString()}", exitCode = -1)
            is ScopeUtils.RestartResult.Failure -> ShellActionResult(success = false, error = result.message, exitCode = -1)
        }
    }

    companion object {
        private val KEY_GUEST_MODE_CONTROLLER = PreferenceKeys.GUEST_MODE_CONTROLLER.name
        private val KEY_DISABLE_BIOMETRIC_ERROR_VIBRATION = PreferenceKeys.DISABLE_BIOMETRIC_ERROR_VIBRATION.name
    }
}

data class SystemUiMiscSettingsUiState(
    val guestModeController: Boolean = false,
    val disableBiometricErrorVibration: Boolean = false,
    val isRestartProcessing: Boolean = false,
    val showRestartDialog: Boolean = false
)
