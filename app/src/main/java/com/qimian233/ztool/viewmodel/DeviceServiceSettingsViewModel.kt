package com.qimian233.ztool.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.qimian233.ztool.data.lsfdevice.DeviceServiceSettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class DeviceServiceSettingsViewModel(
    private val repository: DeviceServiceSettingsRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(DeviceServiceSettingsUiState())
    val uiState: StateFlow<DeviceServiceSettingsUiState> = _uiState.asStateFlow()

    fun loadSettings() {
        _uiState.value = repository.loadState()
    }

    fun setDisablePush(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(disablePush = enabled)
        repository.saveDisablePush(enabled)
    }

    fun setDisableAutoInstall(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(disableAutoInstall = enabled)
        repository.saveDisableAutoInstall(enabled)
    }

    fun setDisableAppListReporting(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(disableAppListReporting = enabled)
        repository.saveDisableAppListReporting(enabled)
    }

    fun setDisableTabPushoutSdac(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(disableTabPushoutSdac = enabled)
        repository.saveDisableTabPushoutSdac(enabled)
    }

    fun showRestartDialog() {
        _uiState.value = _uiState.value.copy(showRestartDialog = true)
    }

    fun dismissRestartDialog() {
        _uiState.value = _uiState.value.copy(showRestartDialog = false)
    }

    fun restartScope(onFailure: () -> Unit) {
        _uiState.value = _uiState.value.copy(showRestartDialog = false)
        viewModelScope.launch(Dispatchers.IO) {
            val result = repository.restartScope()
            if (result is DeviceServiceRestartResult.Failure) {
                withContext(Dispatchers.Main) {
                    onFailure()
                }
            }
        }
    }
}

data class DeviceServiceSettingsUiState(
    val disablePush: Boolean = false,
    val disableAutoInstall: Boolean = false,
    val disableAppListReporting: Boolean = false,
    val disableTabPushoutSdac: Boolean = false,
    val showRestartDialog: Boolean = false
)

sealed interface DeviceServiceRestartResult {
    data object Success : DeviceServiceRestartResult
    data class Failure(val error: String) : DeviceServiceRestartResult
}
