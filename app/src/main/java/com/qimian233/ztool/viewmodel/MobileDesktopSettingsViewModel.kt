package com.qimian233.ztool.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.qimian233.ztool.data.mobiledesktop.MobileDesktopRestartResult
import com.qimian233.ztool.data.mobiledesktop.MobileDesktopSettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MobileDesktopSettingsViewModel(
    private val repository: MobileDesktopSettingsRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(MobileDesktopSettingsUiState())
    val uiState: StateFlow<MobileDesktopSettingsUiState> = _uiState.asStateFlow()

    fun loadSettings() {
        try {
            _uiState.value = repository.loadState()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load mobile desktop settings", e)
        }
    }

    fun setSkipExposeWarn(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(skipExposeWarn = enabled)
        repository.saveSkipExposeWarn(enabled)
    }

    fun setAutoAcceptFileTransfer(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(autoAcceptFileTransfer = enabled)
        repository.saveAutoAcceptFileTransfer(enabled)
    }

    fun showRestartConfirmDialog() {
        _uiState.value = _uiState.value.copy(showRestartConfirmDialog = true)
    }

    fun dismissRestartConfirmDialog() {
        _uiState.value = _uiState.value.copy(showRestartConfirmDialog = false)
    }

    fun restartScope(packageName: String, onResult: (MobileDesktopRestartResult) -> Unit) {
        _uiState.value = _uiState.value.copy(showRestartConfirmDialog = false)
        viewModelScope.launch(Dispatchers.IO) {
            val result = repository.restartScope(packageName)
            withContext(Dispatchers.Main) {
                onResult(result)
            }
        }
    }

    companion object {
        private const val TAG = "MobileDesktopSettings"
    }
}

data class MobileDesktopSettingsUiState(
    val showRestartConfirmDialog: Boolean = false,
    val skipExposeWarn: Boolean = false,
    val autoAcceptFileTransfer: Boolean = false,
)
