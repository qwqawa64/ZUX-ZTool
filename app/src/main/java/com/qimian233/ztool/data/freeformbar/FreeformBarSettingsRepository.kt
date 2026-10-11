package com.qimian233.ztool.data.freeformbar

import android.content.Context
import com.qimian233.ztool.EnhancedShellExecutor
import com.qimian233.ztool.data.keys.PreferenceKeys
import com.qimian233.ztool.screens.features.FeatureDestination
import com.qimian233.ztool.utils.ModulePreferencesUtils
import com.qimian233.ztool.utils.ScopeUtils
import com.qimian233.ztool.viewmodel.FreeformBarRestartResult
import com.qimian233.ztool.viewmodel.FreeformBarSettingsUiState

/** Persistence for the freeform sidebar drawer settings in `xposed_module_config`. */
class FreeformBarSettingsRepository(
    context: Context,
    private val shellExecutor: EnhancedShellExecutor = EnhancedShellExecutor.getInstance()
) {
    private val prefsUtils = ModulePreferencesUtils(context)

    fun loadState(): FreeformBarSettingsUiState {
        return FreeformBarSettingsUiState(
            enhanceEnabled = prefsUtils.loadBooleanSetting(
                KEY_ENHANCE, PreferenceKeys.SIDEBAR_DRAWER_ENHANCE.default
            ),
            drawerSlots = prefsUtils.loadIntegerSetting(
                KEY_SLOTS, PreferenceKeys.SIDEBAR_DRAWER_SLOTS.default
            ).coerceIn(SLOTS_MIN, SLOTS_MAX),
            recentSlots = prefsUtils.loadIntegerSetting(
                KEY_RECENT_SLOTS, PreferenceKeys.SIDEBAR_DRAWER_RECENT_SLOTS.default
            ).coerceIn(RECENT_SLOTS_MIN, RECENT_SLOTS_MAX)
        )
    }

    fun saveEnhance(enabled: Boolean) {
        prefsUtils.saveBooleanSetting(KEY_ENHANCE, enabled)
    }

    /** Stores the drawer capacity, clamped to the range the UI offers. */
    fun saveSlots(count: Int) {
        prefsUtils.saveIntegerSetting(KEY_SLOTS, count.coerceIn(SLOTS_MIN, SLOTS_MAX))
    }

    /** Stores the recent-app slot count, clamped to the range the UI offers. */
    fun saveRecentSlots(count: Int) {
        prefsUtils.saveIntegerSetting(
            KEY_RECENT_SLOTS, count.coerceIn(RECENT_SLOTS_MIN, RECENT_SLOTS_MAX)
        )
    }

    fun restartScope(): FreeformBarRestartResult {
        val scopes = ScopeUtils.getScopes(FeatureDestination.FreeformBar)
        return when (val result = ScopeUtils.restartScope(scopes, shellExecutor)) {
            is ScopeUtils.RestartResult.Success -> FreeformBarRestartResult.Success
            is ScopeUtils.RestartResult.PartialSuccess -> FreeformBarRestartResult.Failure(
                "Partial failure: ${result.failed.joinToString()}"
            )
            is ScopeUtils.RestartResult.Failure -> FreeformBarRestartResult.Failure(result.message)
        }
    }

    companion object {
        const val SLOTS_MIN = 6
        const val SLOTS_MAX = 16
        const val RECENT_SLOTS_MIN = 0
        const val RECENT_SLOTS_MAX = 3

        private val KEY_ENHANCE = PreferenceKeys.SIDEBAR_DRAWER_ENHANCE.name
        private val KEY_SLOTS = PreferenceKeys.SIDEBAR_DRAWER_SLOTS.name
        private val KEY_RECENT_SLOTS = PreferenceKeys.SIDEBAR_DRAWER_RECENT_SLOTS.name
    }
}
