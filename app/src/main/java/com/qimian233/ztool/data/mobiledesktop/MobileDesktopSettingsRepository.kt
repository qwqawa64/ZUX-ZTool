package com.qimian233.ztool.data.mobiledesktop

import android.content.Context
import com.qimian233.ztool.EnhancedShellExecutor
import com.qimian233.ztool.FeatureDestination
import com.qimian233.ztool.utils.ModulePreferencesUtils
import com.qimian233.ztool.data.PreferenceKeys
import com.qimian233.ztool.utils.ScopeUtils
import com.qimian233.ztool.viewmodel.MobileDesktopSettingsUiState

class MobileDesktopSettingsRepository(
    context: Context,
    private val shellExecutor: EnhancedShellExecutor = EnhancedShellExecutor.getInstance()
) {

    private val prefs = ModulePreferencesUtils(context)
    fun restartScope(): MobileDesktopRestartResult {
        val packages = ScopeUtils.getScopePackages(FeatureDestination.MobileDesktop)
        return when (val result = ScopeUtils.restartScope(packages, shellExecutor)) {
            is ScopeUtils.RestartResult.Success -> MobileDesktopRestartResult.Success
            is ScopeUtils.RestartResult.PartialSuccess -> MobileDesktopRestartResult.Failure(
                "Partial failure: ${result.failed.joinToString()}"
            )
            is ScopeUtils.RestartResult.Failure -> MobileDesktopRestartResult.Failure(result.message)
        }
    }

    fun loadState(): MobileDesktopSettingsUiState {
        return MobileDesktopSettingsUiState(
            skipExposeWarn = prefs.loadBooleanSetting(KEY_SKIP_EXPOSE_WARN, false),
            autoAcceptFileTransfer = prefs.loadBooleanSetting(KEY_AUTO_ACCEPT_FILE_TRANSFER, false),
            disableNearbyShareAutoShutdown = prefs.loadBooleanSetting(KEY_DISABLE_NEARBY_SHARE_AUTO_SHUTDOWN, false)
        )
    }

    fun saveSkipExposeWarn(enabled: Boolean) {
        prefs.saveBooleanSetting(KEY_SKIP_EXPOSE_WARN, enabled)
    }

    fun saveAutoAcceptFileTransfer(enabled: Boolean) {
        prefs.saveBooleanSetting(KEY_AUTO_ACCEPT_FILE_TRANSFER, enabled)
    }

    fun saveDisableNearbyShareAutoShutdown(enabled: Boolean) {
        prefs.saveBooleanSetting(KEY_DISABLE_NEARBY_SHARE_AUTO_SHUTDOWN, enabled)
    }

    companion object {
        private const val TAG = "MobileDesktopSettings"
        private val KEY_SKIP_EXPOSE_WARN = PreferenceKeys.BYPASS_SHARE_WARNING.name
        private val KEY_AUTO_ACCEPT_FILE_TRANSFER = PreferenceKeys.AUTO_ACCEPT_FILE_TRANSFER.name
        private val KEY_DISABLE_NEARBY_SHARE_AUTO_SHUTDOWN = PreferenceKeys.DISABLE_NEARBY_SHARE_COUNTDOWN.name
    }
}

sealed interface MobileDesktopRestartResult {
    data object Success : MobileDesktopRestartResult
    data class Failure(val error: String) : MobileDesktopRestartResult
}
