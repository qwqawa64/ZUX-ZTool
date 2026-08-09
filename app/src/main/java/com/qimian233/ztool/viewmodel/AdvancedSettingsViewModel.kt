package com.qimian233.ztool.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.qimian233.ztool.data.advanced.AdvancedSettingsRepository
import com.qimian233.ztool.data.advanced.HotReloadDetail
import com.qimian233.ztool.data.advanced.PersistentResetDetail
import io.github.libxposed.service.HookedTarget
import io.github.libxposed.service.HotReloadResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class AdvancedSettingsViewModel(
    private val repository: AdvancedSettingsRepository = AdvancedSettingsRepository()
) : ViewModel() {
    private val _uiState = MutableStateFlow(AdvancedSettingsUiState())
    val uiState: StateFlow<AdvancedSettingsUiState> = _uiState.asStateFlow()

    fun refresh() {
        viewModelScope.launch(Dispatchers.IO) {
            val apiVersion = repository.getApiVersion()
            val runningTargets = repository.getRunningTargets()
            _uiState.value = AdvancedSettingsUiState(
                apiVersion = apiVersion,
                runningTargetCount = runningTargets.size,
                runningTargets = runningTargets,
                hotReloadInProgress = _uiState.value.hotReloadInProgress,
                showHotReloadDialog = _uiState.value.showHotReloadDialog,
                resetInProgress = _uiState.value.resetInProgress,
                showResetDialog = _uiState.value.showResetDialog,
                resetDetails = _uiState.value.resetDetails,
                resetResultSucceeded = _uiState.value.resetResultSucceeded,
                resetResultFailed = _uiState.value.resetResultFailed,
                resetResultUnsupported = _uiState.value.resetResultUnsupported
            )
        }
    }

    fun showHotReloadConfirmDialog() {
        _uiState.value = _uiState.value.copy(showHotReloadDialog = true)
    }

    fun dismissHotReloadDialog() {
        _uiState.value = _uiState.value.copy(showHotReloadDialog = false)
    }

    fun showResetConfirmDialog() {
        _uiState.value = _uiState.value.copy(showResetDialog = true)
    }

    fun dismissResetDialog() {
        _uiState.value = _uiState.value.copy(showResetDialog = false)
    }

    fun performHotReload() {
        _uiState.value = _uiState.value.copy(
            showHotReloadDialog = false,
            hotReloadInProgress = true,
            hotReloadDetails = emptyList()
        )

        repository.performHotReloadAll(
            onProgress = { target, result ->
                Log.d(TAG, "热重载: ${target.processName} -> ${result.status()} ${result.message() ?: ""}")
            },
            onComplete = { succeeded, failed, unsupported, died, details ->
                Log.d(TAG, "热重载完成: 成功=$succeeded, 失败=$failed, 不支持=$unsupported, 进程已死=$died")
                for (d in details) {
                    if (d.status != "SUCCEEDED") {
                        Log.w(TAG, "  [${d.status}] ${d.processName}: ${d.message}")
                    }
                }
                _uiState.value = _uiState.value.copy(
                    hotReloadInProgress = false,
                    hotReloadResultSucceeded = succeeded,
                    hotReloadResultFailed = failed,
                    hotReloadResultUnsupported = unsupported,
                    hotReloadResultDied = died,
                    hotReloadDetails = details
                )
            }
        )
    }

    fun performResetPersistentValues() {
        _uiState.value = _uiState.value.copy(
            showResetDialog = false,
            resetInProgress = true,
            resetDetails = emptyList()
        )

        repository.resetPersistentValues(
            onComplete = { succeeded, failed, unsupported, details ->
                Log.d(TAG, "重置持久化值完成: 成功=$succeeded, 失败=$failed, 不支持=$unsupported")
                for (d in details) {
                    if (d.status != "SUCCEEDED") {
                        Log.w(TAG, "  [${d.status}] ${d.key}: ${d.message}")
                    }
                }
                _uiState.value = _uiState.value.copy(
                    resetInProgress = false,
                    resetResultSucceeded = succeeded,
                    resetResultFailed = failed,
                    resetResultUnsupported = unsupported,
                    resetDetails = details
                )
            }
        )
    }

    companion object {
        private const val TAG = "AdvancedVM"
    }
}

data class AdvancedSettingsUiState(
    val apiVersion: Int = 0,
    val runningTargetCount: Int = 0,
    val runningTargets: List<HookedTarget> = emptyList(),
    val hotReloadInProgress: Boolean = false,
    val showHotReloadDialog: Boolean = false,
    val hotReloadDetails: List<HotReloadDetail> = emptyList(),
    val hotReloadResultSucceeded: Int = 0,
    val hotReloadResultFailed: Int = 0,
    val hotReloadResultUnsupported: Int = 0,
    val hotReloadResultDied: Int = 0,
    val resetInProgress: Boolean = false,
    val showResetDialog: Boolean = false,
    val resetDetails: List<PersistentResetDetail> = emptyList(),
    val resetResultSucceeded: Int = 0,
    val resetResultFailed: Int = 0,
    val resetResultUnsupported: Int = 0
)
