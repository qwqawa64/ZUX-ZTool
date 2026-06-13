package com.qimian233.ztool.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.qimian233.ztool.data.safecenter.SafeCenterRestartResult
import com.qimian233.ztool.data.safecenter.SafeCenterSettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SafeCenterSettingsViewModel(
    private val repository: SafeCenterSettingsRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(SafeCenterSettingsUiState())
    val uiState: StateFlow<SafeCenterSettingsUiState> = _uiState.asStateFlow()

    fun loadSettings() {
        try {
            _uiState.value = repository.loadState()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load safe center settings", e)
        }
    }

    fun setDefaultEnableAutorun(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(defaultEnableAutorun = enabled)
        repository.saveDefaultEnableAutorun(enabled)
    }

    fun setBlockSafeCenterScan(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(blockSafeCenterScan = enabled)
        repository.saveBlockSafeCenterScan(enabled)
    }

    fun setDisableAllVirusScan(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(disableAllVirusScan = enabled)
        repository.saveDisableAllVirusScan(enabled)
    }

    fun setDocumentsUiBypass(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(documentsUiBypass = enabled)
        repository.saveDocumentsUiBypass(enabled)
    }

    fun showRestartConfirmDialog() {
        if (_uiState.value.isRestartProcessing) {
            Log.d(TAG, "Restart is already processing, ignoring duplicate click")
            return
        }
        _uiState.value = _uiState.value.copy(showRestartConfirmDialog = true)
    }

    fun dismissRestartConfirmDialog() {
        _uiState.value = _uiState.value.copy(showRestartConfirmDialog = false)
    }

    fun restartPackages(
        packageName: String,
        onResult: (SafeCenterRestartResult) -> Unit
    ) {
        if (_uiState.value.isRestartProcessing) {
            Log.d(TAG, "Restart is already processing")
            return
        }

        _uiState.value = _uiState.value.copy(
            showRestartConfirmDialog = false,
            isRestartProcessing = true
        )

        viewModelScope.launch(Dispatchers.IO) {
            val result = repository.restartPackages(packageName)
            withContext(Dispatchers.Main) {
                onResult(result)
                _uiState.value = _uiState.value.copy(isRestartProcessing = false)
            }
        }
    }

    companion object {
        private const val TAG = "SafeCenterSettings"
    }
}

data class SafeCenterSettingsUiState(
    val defaultEnableAutorun: Boolean = false,
    val blockSafeCenterScan: Boolean = false,
    val disableAllVirusScan: Boolean = false,
    val documentsUiBypass: Boolean = false,
    val showRestartConfirmDialog: Boolean = false,
    val isRestartProcessing: Boolean = false
)
