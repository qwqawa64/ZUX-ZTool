package com.qimian233.ztool.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.qimian233.ztool.data.tbengine.TbEngineSettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class TbEngineSettingsViewModel(
    private val repository: TbEngineSettingsRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(TbEngineSettingsUiState())
    val uiState: StateFlow<TbEngineSettingsUiState> = _uiState.asStateFlow()

    fun loadSettings() {
        _uiState.value = repository.loadState()
    }

    fun setDisableAutoDownload(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(disableAutoDownload = enabled)
        repository.saveDisableAutoDownload(enabled)
    }

    fun setDisableAutoInstall(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(disableAutoInstall = enabled)
        repository.saveDisableAutoInstall(enabled)
    }

    fun setDisableAppUpdate(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(disableAppUpdate = enabled)
        repository.saveDisableAppUpdate(enabled)
    }

    fun setDisablePush(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(disablePush = enabled)
        repository.saveDisablePush(enabled)
    }

    fun dismissErrorDialog() {
        _uiState.value = _uiState.value.copy(errorDialogMessage = null)
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
            if (result is TbEngineRestartResult.Failure) {
                withContext(Dispatchers.Main) {
                    onFailure()
                }
            }
        }
    }
}

data class TbEngineSettingsUiState(
    val disableAutoDownload: Boolean = false,
    val disableAutoInstall: Boolean = false,
    val disableAppUpdate: Boolean = false,
    val disablePush: Boolean = false,
    val errorDialogMessage: String? = null,
    val showRestartDialog: Boolean = false
)

sealed interface TbEngineRestartResult {
    data object Success : TbEngineRestartResult
    data class Failure(val error: String) : TbEngineRestartResult
}
