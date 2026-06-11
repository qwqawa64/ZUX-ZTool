package com.qimian233.ztool.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import com.qimian233.ztool.data.launcher.LauncherRestartResult
import com.qimian233.ztool.data.launcher.LauncherSettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class LauncherSettingsViewModel(
    private val repository: LauncherSettingsRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(LauncherSettingsUiState())
    val uiState: StateFlow<LauncherSettingsUiState> = _uiState.asStateFlow()

    fun loadSettings() {
        try {
            _uiState.value = repository.loadState()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load launcher settings", e)
        }
    }

    fun setForceStopMode(mode: ForceStopMode) {
        _uiState.value = _uiState.value.copy(forceStopMode = mode)
        repository.saveForceStopMode(mode)
    }

    fun setForceStopWhitelist(packageNames: List<String>) {
        packageNames.forEach {
            Log.d(TAG, "Selected protected app package: $it")
        }
        _uiState.value = _uiState.value.copy(forceStopWhitelist = packageNames)
        repository.saveForceStopWhitelist(packageNames)
    }

    fun loadUserInstalledPackageNames(): List<String> {
        return repository.loadUserInstalledPackageNames()
    }

    fun setMoreBigDock(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(moreBigDock = enabled)
        repository.saveMoreBigDock(enabled)
    }

    fun setCustomGridSize(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(customGridSize = enabled)
        repository.saveCustomGridSize(enabled)
    }

    fun setCustomGridRow(value: Int) {
        val current = _uiState.value
        val row = value.coerceIn(LauncherSettingsRepository.GRID_MIN, LauncherSettingsRepository.GRID_MAX)
        _uiState.value = current.copy(customGridRow = row)
        repository.saveGridValues(row, current.customGridColumn)
    }

    fun setCustomGridColumn(value: Int) {
        val current = _uiState.value
        val column = value.coerceIn(LauncherSettingsRepository.GRID_MIN, LauncherSettingsRepository.GRID_MAX)
        _uiState.value = current.copy(customGridColumn = column)
        repository.saveGridValues(current.customGridRow, column)
    }

    fun showRestartConfirmDialog() {
        _uiState.value = _uiState.value.copy(showRestartConfirmDialog = true)
    }

    fun dismissRestartConfirmDialog() {
        _uiState.value = _uiState.value.copy(showRestartConfirmDialog = false)
    }

    fun forceStopPackage(packageName: String, onResult: (LauncherRestartResult) -> Unit) {
        _uiState.value = _uiState.value.copy(showRestartConfirmDialog = false)
        Thread {
            onResult(repository.forceStopPackage(packageName))
        }.start()
    }

    companion object {
        private const val TAG = "LauncherSettings"
    }
}

enum class ForceStopMode {
    Default,
    AllApps,
    Whitelist
}

data class LauncherSettingsUiState(
    val forceStopMode: ForceStopMode = ForceStopMode.Default,
    val forceStopWhitelist: List<String> = emptyList(),
    val moreBigDock: Boolean = false,
    val customGridSize: Boolean = false,
    val customGridRow: Int = 4,
    val customGridColumn: Int = 6,
    val showRestartConfirmDialog: Boolean = false
) {
    val forceStopWhitelistCount: Int
        get() = forceStopWhitelist.size
}
