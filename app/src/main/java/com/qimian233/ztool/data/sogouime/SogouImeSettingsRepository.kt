package com.qimian233.ztool.data.sogouime

import android.content.Context
import com.qimian233.ztool.EnhancedShellExecutor
import com.qimian233.ztool.data.keys.PreferenceKeys
import com.qimian233.ztool.screens.features.FeatureDestination
import com.qimian233.ztool.utils.ModulePreferencesUtils
import com.qimian233.ztool.utils.ScopeUtils
import com.qimian233.ztool.viewmodel.SogouImeRestartResult
import com.qimian233.ztool.viewmodel.SogouImeSettingsUiState

class SogouImeSettingsRepository(
    private val context: Context,
    private val shellExecutor: EnhancedShellExecutor = EnhancedShellExecutor.getInstance()
) {
    private val prefsUtils = ModulePreferencesUtils(context)

    fun loadState(): SogouImeSettingsUiState {
        return SogouImeSettingsUiState(
            halfWidthPunct = prefsUtils.loadBooleanSetting(KEY_HALF_WIDTH_PUNCT, false),
            halfWidthPunctSigns = prefsUtils.loadStringSetting(
                KEY_HALF_WIDTH_PUNCT_SIGNS, DEFAULT_HALF_WIDTH_PUNCT_SIGNS
            )
        )
    }

    fun saveHalfWidthPunct(enabled: Boolean) {
        prefsUtils.saveBooleanSetting(KEY_HALF_WIDTH_PUNCT, enabled)
    }

    fun saveHalfWidthPunctSigns(value: String) {
        prefsUtils.saveStringSetting(KEY_HALF_WIDTH_PUNCT_SIGNS, value)
    }

    fun restartScope(): SogouImeRestartResult {
        val scopes = ScopeUtils.getScopes(FeatureDestination.SogouIme)
        return when (val result = ScopeUtils.restartScope(scopes, shellExecutor)) {
            is ScopeUtils.RestartResult.Success -> SogouImeRestartResult.Success
            is ScopeUtils.RestartResult.PartialSuccess -> SogouImeRestartResult.Failure(
                "Partial failure: ${result.failed.joinToString()}"
            )
            is ScopeUtils.RestartResult.Failure -> SogouImeRestartResult.Failure(result.message)
        }
    }

    companion object {
        private val KEY_HALF_WIDTH_PUNCT = PreferenceKeys.HALF_WIDTH_PUNCT.name
        private val KEY_HALF_WIDTH_PUNCT_SIGNS = PreferenceKeys.HALF_WIDTH_PUNCT_SIGNS.name
        private val DEFAULT_HALF_WIDTH_PUNCT_SIGNS = PreferenceKeys.HALF_WIDTH_PUNCT_SIGNS.default
    }
}
