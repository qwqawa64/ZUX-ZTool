package com.qimian233.ztool.data.systemui

import android.content.Context
import com.qimian233.ztool.EnhancedShellExecutor
import com.qimian233.ztool.FeatureDestination
import com.qimian233.ztool.utils.ModulePreferencesUtils
import com.qimian233.ztool.utils.ScopeUtils
import com.qimian233.ztool.viewmodel.SystemUiSettingsUiState

class SystemUiSettingsRepository(
    context: Context,
    private val shellExecutor: EnhancedShellExecutor = EnhancedShellExecutor.getInstance()
) {
    private val prefsUtils = ModulePreferencesUtils(context)

    fun loadState(): SystemUiSettingsUiState {
        return SystemUiSettingsUiState()
    }

    fun forceStopScope(): ShellActionResult {
        val packages = ScopeUtils.getScopePackages(FeatureDestination.SystemUi)
        return when (val result = ScopeUtils.restartScope(packages, shellExecutor)) {
            is ScopeUtils.RestartResult.Success -> ShellActionResult(success = true, error = "", exitCode = 0)
            is ScopeUtils.RestartResult.PartialSuccess -> ShellActionResult(
                success = false,
                error = "Partial failure: ${result.failed.joinToString()}",
                exitCode = -1
            )
            is ScopeUtils.RestartResult.Failure -> ShellActionResult(
                success = false,
                error = result.message,
                exitCode = -1
            )
        }
    }

    companion object {
    }
}

data class ShellActionResult(
    val success: Boolean,
    val error: String,
    val exitCode: Int
)
