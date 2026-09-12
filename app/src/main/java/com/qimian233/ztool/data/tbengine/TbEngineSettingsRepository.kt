package com.qimian233.ztool.data.tbengine

import android.content.Context
import com.qimian233.ztool.EnhancedShellExecutor
import com.qimian233.ztool.R
import com.qimian233.ztool.data.keys.PreferenceKeys
import com.qimian233.ztool.screens.features.FeatureDestination
import com.qimian233.ztool.utils.ModulePreferencesUtils
import com.qimian233.ztool.utils.ScopeUtils
import com.qimian233.ztool.viewmodel.TbEngineSettingsUiState
import com.qimian233.ztool.viewmodel.TbEngineRestartResult

class TbEngineSettingsRepository(
    private val context: Context,
    private val shellExecutor: EnhancedShellExecutor = EnhancedShellExecutor.getInstance()
) {
    private val prefsUtils = ModulePreferencesUtils(context)

    fun loadState(): TbEngineSettingsUiState {
        return TbEngineSettingsUiState(
            disableAutoDownload = prefsUtils.loadBooleanSetting(KEY_DISABLE_AUTO_DOWNLOAD, false),
            disableAutoInstall = prefsUtils.loadBooleanSetting(KEY_DISABLE_AUTO_INSTALL, false),
            disableAppUpdate = prefsUtils.loadBooleanSetting(KEY_DISABLE_APP_UPDATE, false),
            disablePush = prefsUtils.loadBooleanSetting(KEY_DISABLE_PUSH, false)
        )
    }

    fun saveDisableAutoDownload(enabled: Boolean) {
        prefsUtils.saveBooleanSetting(KEY_DISABLE_AUTO_DOWNLOAD, enabled)
    }

    fun saveDisableAutoInstall(enabled: Boolean) {
        prefsUtils.saveBooleanSetting(KEY_DISABLE_AUTO_INSTALL, enabled)
    }

    fun saveDisableAppUpdate(enabled: Boolean) {
        prefsUtils.saveBooleanSetting(KEY_DISABLE_APP_UPDATE, enabled)
    }

    fun saveDisablePush(enabled: Boolean) {
        prefsUtils.saveBooleanSetting(KEY_DISABLE_PUSH, enabled)
    }

    fun restartScope(): TbEngineRestartResult {
        val scopes = ScopeUtils.getScopes(FeatureDestination.TbEngine)
        return when (val result = ScopeUtils.restartScope(scopes, shellExecutor)) {
            is ScopeUtils.RestartResult.Success -> TbEngineRestartResult.Success
            is ScopeUtils.RestartResult.PartialSuccess -> TbEngineRestartResult.Failure(
                "Partial failure: ${result.failed.joinToString()}"
            )
            is ScopeUtils.RestartResult.Failure -> TbEngineRestartResult.Failure(result.message)
        }
    }

    companion object {
        private val KEY_DISABLE_AUTO_DOWNLOAD = PreferenceKeys.DISABLE_TB_ENGINE_AUTO_DOWNLOAD.name
        private val KEY_DISABLE_AUTO_INSTALL = PreferenceKeys.DISABLE_TB_ENGINE_AUTO_INSTALL.name
        private val KEY_DISABLE_APP_UPDATE = PreferenceKeys.DISABLE_TB_ENGINE_APP_UPDATE.name
        private val KEY_DISABLE_PUSH = PreferenceKeys.DISABLE_TB_ENGINE_PUSH.name
    }
}
