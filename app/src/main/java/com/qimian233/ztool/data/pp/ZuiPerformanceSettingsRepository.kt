package com.qimian233.ztool.data.pp

import android.content.Context
import com.qimian233.ztool.EnhancedShellExecutor
import com.qimian233.ztool.data.keys.PreferenceKeys
import com.qimian233.ztool.screens.features.FeatureDestination
import com.qimian233.ztool.utils.ModulePreferencesUtils
import com.qimian233.ztool.utils.ScopeUtils
import com.qimian233.ztool.viewmodel.ZuiPerformanceRestartResult
import com.qimian233.ztool.viewmodel.ZuiPerformanceSettingsUiState

class ZuiPerformanceSettingsRepository(
    private val context: Context,
    private val shellExecutor: EnhancedShellExecutor = EnhancedShellExecutor.getInstance()
) {
    private val prefsUtils = ModulePreferencesUtils(context)

    fun loadState(): ZuiPerformanceSettingsUiState {
        return ZuiPerformanceSettingsUiState(
            blockPowerPolicySync = prefsUtils.loadBooleanSetting(KEY_BLOCK_POWER_POLICY_SYNC, false),
            blockGamePolicyUpdate = prefsUtils.loadBooleanSetting(KEY_BLOCK_GAME_POLICY_UPDATE, false)
        )
    }

    fun saveBlockPowerPolicySync(enabled: Boolean) {
        prefsUtils.saveBooleanSetting(KEY_BLOCK_POWER_POLICY_SYNC, enabled)
    }

    fun saveBlockGamePolicyUpdate(enabled: Boolean) {
        prefsUtils.saveBooleanSetting(KEY_BLOCK_GAME_POLICY_UPDATE, enabled)
    }

    fun restartScope(): ZuiPerformanceRestartResult {
        val scopes = ScopeUtils.getScopes(FeatureDestination.ZuiPerformance)
        return when (val result = ScopeUtils.restartScope(scopes, shellExecutor)) {
            is ScopeUtils.RestartResult.Success -> ZuiPerformanceRestartResult.Success
            is ScopeUtils.RestartResult.PartialSuccess -> ZuiPerformanceRestartResult.Failure(
                "Partial failure: ${result.failed.joinToString()}"
            )
            is ScopeUtils.RestartResult.Failure -> ZuiPerformanceRestartResult.Failure(result.message)
        }
    }

    companion object {
        private val KEY_BLOCK_POWER_POLICY_SYNC = PreferenceKeys.PP_BLOCK_POWER_POLICY_SYNC.name
        private val KEY_BLOCK_GAME_POLICY_UPDATE = PreferenceKeys.PP_BLOCK_GAME_POLICY_UPDATE.name
    }
}
