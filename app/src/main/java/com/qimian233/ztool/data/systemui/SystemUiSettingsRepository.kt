package com.qimian233.ztool.data.systemui

import com.qimian233.ztool.EnhancedShellExecutor
import com.qimian233.ztool.screens.features.FeatureDestination
import com.qimian233.ztool.utils.ScopeUtils
import com.qimian233.ztool.viewmodel.SystemUiSettingsUiState

class SystemUiSettingsRepository(
    private val shellExecutor: EnhancedShellExecutor = EnhancedShellExecutor.getInstance()
) {

    fun loadState(): SystemUiSettingsUiState {
        return SystemUiSettingsUiState()
    }

    fun forceStopScope(): ShellActionResult {
        val scopes = ScopeUtils.getScopes(FeatureDestination.SystemUi)
        return when (val result = ScopeUtils.restartScope(scopes, shellExecutor)) {
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
}

data class ShellActionResult(
    val success: Boolean,
    val error: String,
    val exitCode: Int
)
