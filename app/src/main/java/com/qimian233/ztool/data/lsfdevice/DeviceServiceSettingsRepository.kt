package com.qimian233.ztool.data.lsfdevice

import android.content.Context
import com.qimian233.ztool.EnhancedShellExecutor
import com.qimian233.ztool.data.keys.PreferenceKeys
import com.qimian233.ztool.screens.features.FeatureDestination
import com.qimian233.ztool.utils.ModulePreferencesUtils
import com.qimian233.ztool.utils.ScopeUtils
import com.qimian233.ztool.viewmodel.DeviceServiceRestartResult
import com.qimian233.ztool.viewmodel.DeviceServiceSettingsUiState

class DeviceServiceSettingsRepository(
    private val context: Context,
    private val shellExecutor: EnhancedShellExecutor = EnhancedShellExecutor.getInstance()
) {
    private val prefsUtils = ModulePreferencesUtils(context)

    fun loadState(): DeviceServiceSettingsUiState {
        return DeviceServiceSettingsUiState(
            disablePush = prefsUtils.loadBooleanSetting(KEY_DISABLE_PUSH, false),
            disableAutoInstall = prefsUtils.loadBooleanSetting(KEY_DISABLE_AUTO_INSTALL, false),
            disableAppListReporting =
                prefsUtils.loadBooleanSetting(KEY_DISABLE_APP_LIST_REPORTING, false),
            disableTabPushoutSdac = prefsUtils.loadBooleanSetting(KEY_DISABLE_TAB_PUSHOUT_SDAC, false)
        )
    }

    fun saveDisablePush(enabled: Boolean) {
        prefsUtils.saveBooleanSetting(KEY_DISABLE_PUSH, enabled)
    }

    fun saveDisableAutoInstall(enabled: Boolean) {
        prefsUtils.saveBooleanSetting(KEY_DISABLE_AUTO_INSTALL, enabled)
    }

    fun saveDisableAppListReporting(enabled: Boolean) {
        prefsUtils.saveBooleanSetting(KEY_DISABLE_APP_LIST_REPORTING, enabled)
    }

    fun saveDisableTabPushoutSdac(enabled: Boolean) {
        prefsUtils.saveBooleanSetting(KEY_DISABLE_TAB_PUSHOUT_SDAC, enabled)
    }

    fun restartScope(): DeviceServiceRestartResult {
        val scopes = ScopeUtils.getScopes(FeatureDestination.DeviceService)
        return when (val result = ScopeUtils.restartScope(scopes, shellExecutor)) {
            is ScopeUtils.RestartResult.Success -> DeviceServiceRestartResult.Success
            is ScopeUtils.RestartResult.PartialSuccess -> DeviceServiceRestartResult.Failure(
                "Partial failure: ${result.failed.joinToString()}"
            )
            is ScopeUtils.RestartResult.Failure -> DeviceServiceRestartResult.Failure(result.message)
        }
    }

    companion object {
        private val KEY_DISABLE_PUSH = PreferenceKeys.DISABLE_LSF_DEVICE_PUSH.name
        private val KEY_DISABLE_AUTO_INSTALL = PreferenceKeys.DISABLE_LSF_DEVICE_AUTO_INSTALL.name
        private val KEY_DISABLE_APP_LIST_REPORTING =
            PreferenceKeys.DISABLE_LSF_DEVICE_APP_LIST_REPORTING.name
        private val KEY_DISABLE_TAB_PUSHOUT_SDAC = PreferenceKeys.DISABLE_TAB_PUSHOUT_SDAC.name
    }
}
