package com.qimian233.ztool.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.qimian233.ztool.data.systemui.SystemUiSettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class SystemUiSettingsViewModel(
    private val repository: SystemUiSettingsRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(SystemUiSettingsUiState())
    val uiState: StateFlow<SystemUiSettingsUiState> = _uiState.asStateFlow()

    fun loadSettings() {
        // UiState has no persisted fields; kept for consistency
    }

    fun showRestartDialog() {
        if (!_uiState.value.isRestartProcessing) {
            _uiState.value = _uiState.value.copy(showRestartDialog = true)
        }
    }

    fun dismissRestartDialog() {
        _uiState.value = _uiState.value.copy(showRestartDialog = false)
    }

    fun forceStopScope(onResult: (Boolean, String) -> Unit) {
        if (_uiState.value.isRestartProcessing) return

        _uiState.value = _uiState.value.copy(
            showRestartDialog = false,
            isRestartProcessing = true
        )

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val result = repository.forceStopScope()
                withContext(Dispatchers.Main) {
                    onResult(result.success, result.error)
                }
                Log.d(TAG, "Force stop result: success=${result.success}")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to force stop: ${e.message}")
                withContext(Dispatchers.Main) {
                    onResult(false, e.message.orEmpty())
                }
            } finally {
                withContext(Dispatchers.Main) {
                    _uiState.value = _uiState.value.copy(isRestartProcessing = false)
                }
            }
        }
    }

    companion object {
        private const val TAG = "SystemUiSettingsViewModel"
    }
}

data class SystemUiSettingsUiState(
    val isRestartProcessing: Boolean = false,
    val showRestartDialog: Boolean = false
)
