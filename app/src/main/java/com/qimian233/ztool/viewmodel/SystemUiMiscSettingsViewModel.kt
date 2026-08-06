package com.qimian233.ztool.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.qimian233.ztool.data.systemui.SystemUiMiscSettingsRepository
import com.qimian233.ztool.data.systemui.SystemUiMiscSettingsUiState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SystemUiMiscSettingsViewModel(
    private val repository: SystemUiMiscSettingsRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(loadInitialState())
    val uiState: StateFlow<SystemUiMiscSettingsUiState> = _uiState.asStateFlow()

    fun loadSettings() {
        _uiState.value = loadInitialState()
    }

    private fun loadInitialState(): SystemUiMiscSettingsUiState {
        try {
            return repository.loadState()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load misc settings", e)
        }
        return SystemUiMiscSettingsUiState()
    }

    fun setGuestModeController(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(guestModeController = enabled)
        repository.saveGuestModeController(enabled)
    }

    fun setDisableBiometricErrorVibration(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(disableBiometricErrorVibration = enabled)
        repository.saveDisableBiometricErrorVibration(enabled)
    }

    fun showRestartDialog() {
        _uiState.value = _uiState.value.copy(showRestartDialog = true)
    }

    fun dismissRestartDialog() {
        _uiState.value = _uiState.value.copy(showRestartDialog = false)
    }

    fun forceStopScope(onResult: (Boolean, String) -> Unit) {
        if (_uiState.value.isRestartProcessing) return
        _uiState.value = _uiState.value.copy(showRestartDialog = false, isRestartProcessing = true)
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val result = repository.forceStopScope()
                withContext(Dispatchers.Main) { onResult(result.success, result.error) }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { onResult(false, e.message ?: "") }
            } finally {
                _uiState.value = _uiState.value.copy(isRestartProcessing = false)
            }
        }
    }

    companion object {
        private const val TAG = "SystemUiMiscSettingsViewModel"
    }
}
