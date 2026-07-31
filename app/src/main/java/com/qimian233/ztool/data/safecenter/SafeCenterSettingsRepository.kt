package com.qimian233.ztool.data.safecenter

import android.content.Context
import com.qimian233.ztool.EnhancedShellExecutor
import com.qimian233.ztool.FeatureDestination
import com.qimian233.ztool.utils.ModulePreferencesUtils
import com.qimian233.ztool.data.PreferenceKeys
import com.qimian233.ztool.utils.ScopeUtils
import com.qimian233.ztool.viewmodel.SafeCenterSettingsUiState

class SafeCenterSettingsRepository(
    context: Context,
    private val shellExecutor: EnhancedShellExecutor = EnhancedShellExecutor.getInstance()
) {
    private val prefsUtils = ModulePreferencesUtils(context)

    fun loadState(): SafeCenterSettingsUiState {
        return SafeCenterSettingsUiState(
            defaultEnableAutorun = prefsUtils.loadBooleanSetting(KEY_DEFAULT_ENABLE_AUTORUN, false),
            disableAllVirusScan = prefsUtils.loadBooleanSetting(KEY_DISABLE_ALL_VIRUS_SCANS, false),
            documentsUiBypass = prefsUtils.loadBooleanSetting(KEY_DOCUMENTS_UI_BYPASS, false)
        )
    }

    fun saveDefaultEnableAutorun(enabled: Boolean) {
        prefsUtils.saveBooleanSetting(KEY_DEFAULT_ENABLE_AUTORUN, enabled)
    }

    fun saveDocumentsUiBypass(enabled: Boolean) {
        prefsUtils.saveBooleanSetting(KEY_DOCUMENTS_UI_BYPASS, enabled)
    }

    fun saveDisableAllVirusScan(enabled: Boolean) {
        prefsUtils.saveBooleanSetting(KEY_DISABLE_ALL_VIRUS_SCANS, enabled)
    }

    fun restartPackages(): SafeCenterRestartResult {
        val packages = ScopeUtils.getScopePackages(FeatureDestination.SafeCenter)
        return when (val result = ScopeUtils.restartScope(packages, shellExecutor)) {
            is ScopeUtils.RestartResult.Success -> SafeCenterRestartResult.Success
            is ScopeUtils.RestartResult.PartialSuccess -> SafeCenterRestartResult.Failure(
                "Partial failure: ${result.failed.joinToString()}"
            )
            is ScopeUtils.RestartResult.Failure -> SafeCenterRestartResult.Failure(result.message)
        }
    }

    companion object {
        private const val TAG = "SafeCenterSettings"
        private val KEY_DEFAULT_ENABLE_AUTORUN = PreferenceKeys.DEFAULT_ENABLE_AUTORUN.name
        private val KEY_DOCUMENTS_UI_BYPASS = PreferenceKeys.DOCUMENTS_UI_BYPASS.name
        private val KEY_DISABLE_ALL_VIRUS_SCANS = PreferenceKeys.DISABLE_ALL_VIRUS_SCANS.name
    }
}

sealed interface SafeCenterRestartResult {
    data object Success : SafeCenterRestartResult
    data class Failure(val error: String) : SafeCenterRestartResult
}
