package com.qimian233.ztool.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.qimian233.ztool.data.pp.ZuiPerformanceSettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ZuiPerformanceSettingsViewModel(
    private val repository: ZuiPerformanceSettingsRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(ZuiPerformanceSettingsUiState())
    val uiState: StateFlow<ZuiPerformanceSettingsUiState> = _uiState.asStateFlow()

    fun loadSettings() {
        _uiState.value = repository.loadState()
    }

    fun setBlockPowerPolicySync(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(blockPowerPolicySync = enabled)
        repository.saveBlockPowerPolicySync(enabled)
    }

    fun setBlockGamePolicyUpdate(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(blockGamePolicyUpdate = enabled)
        repository.saveBlockGamePolicyUpdate(enabled)
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
            if (result is ZuiPerformanceRestartResult.Failure) {
                withContext(Dispatchers.Main) {
                    onFailure()
                }
            }
        }
    }
}

data class ZuiPerformanceSettingsUiState(
    val blockPowerPolicySync: Boolean = false,
    val blockGamePolicyUpdate: Boolean = false,
    val showRestartDialog: Boolean = false
)

sealed interface ZuiPerformanceRestartResult {
    data object Success : ZuiPerformanceRestartResult
    data class Failure(val error: String) : ZuiPerformanceRestartResult
}
