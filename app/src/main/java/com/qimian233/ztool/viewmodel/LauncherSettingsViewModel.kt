package com.qimian233.ztool.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.qimian233.ztool.data.launcher.LauncherRestartResult
import com.qimian233.ztool.data.launcher.LauncherSettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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

    fun setCleanSearch(value: Boolean) {
        _uiState.value = _uiState.value.copy(cleanGlobalSearch = value)
        repository.saveCleanSearch(value)
    }

    fun setRemoveSearchRecommend(value: Boolean) {
        _uiState.value = _uiState.value.copy(removeSearchRecommend = value)
        repository.saveRemoveSearchRecommend(value)
    }

    fun setRemoveHotWordView(value: Boolean) {
        _uiState.value = _uiState.value.copy(removeHotWordView = value)
        repository.saveRemoveHotWordView(value)
    }

    fun setShowRamInfo(value: Boolean) {
        _uiState.value = _uiState.value.copy(showRamInfo = value)
        repository.saveShowRamInfo(value)
    }

    fun setBeautifyRamInfo(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(beautifyRamInfo = enabled)
        repository.saveBeautifyRamInfo(enabled)
    }

    fun setDisableDockBar(enabled: Boolean) {
        val showWarning = repository.saveDisableDockBar(enabled)
        val current = _uiState.value
        _uiState.value = if (enabled) {
            current.copy(
                disableDockBar = true,
                moreBigDock = false,
                showDisableDockWarningDialog = showWarning
            )
        } else {
            current.copy(
                disableDockBar = false,
                moreBigDock = current.moreBigDock,
                showDisableDockWarningDialog = false
            )
        }
    }

    fun dismissDisableDockWarningDialog() {
        _uiState.value = _uiState.value.copy(showDisableDockWarningDialog = false)
    }

    fun confirmDisableDockWarning() {
        repository.saveDisableDockWarningConfirmed()
        _uiState.value = _uiState.value.copy(showDisableDockWarningDialog = false)
    }

    fun showRestartConfirmDialog() {
        _uiState.value = _uiState.value.copy(showRestartConfirmDialog = true)
    }

    fun dismissRestartConfirmDialog() {
        _uiState.value = _uiState.value.copy(showRestartConfirmDialog = false)
    }

    fun forceStopPackage(packageName: String, onResult: (LauncherRestartResult) -> Unit) {
        _uiState.value = _uiState.value.copy(showRestartConfirmDialog = false)
        viewModelScope.launch(Dispatchers.IO) {
            val result = repository.forceStopPackage(packageName)
            withContext(Dispatchers.Main) {
                onResult(result)
            }
        }
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
    val cleanGlobalSearch: Boolean = false,
    val removeHotWordView: Boolean = false,
    val removeSearchRecommend: Boolean = false,
    val showRestartConfirmDialog: Boolean = false,
    val showRamInfo : Boolean = false,
    val beautifyRamInfo : Boolean = false,
    val disableDockBar: Boolean = false,
    val showDisableDockWarningDialog: Boolean = false
) {
    val forceStopWhitelistCount: Int
        get() = forceStopWhitelist.size
}
