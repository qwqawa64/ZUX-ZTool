package com.qimian233.ztool.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import com.qimian233.ztool.data.systemframework.FrameworkSettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class FrameworkSettingsViewModel(
    private val repository: FrameworkSettingsRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(FrameworkSettingsUiState())
    val uiState: StateFlow<FrameworkSettingsUiState> = _uiState.asStateFlow()

    fun loadSettings() {
        try {
            _uiState.value = repository.loadState()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load framework settings", e)
        }
    }

    fun setKeepRotation(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(keepRotation = enabled)
        repository.saveKeepRotation(enabled)
    }

    fun setAllowGetPackages(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(allowGetPackages = enabled)
        repository.saveAllowGetPackages(enabled)
    }

    fun setDisableFlagSecure(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(disableFlagSecure = enabled)
        repository.saveDisableFlagSecure(enabled)
    }

    fun setAiInputExpand(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(
            aiInputExpand = enabled,
            aiInputSignsError = if (enabled) _uiState.value.aiInputSignsError else null
        )
        repository.saveAiInputExpand(enabled)
    }

    fun setAiInputSigns(value: String) {
        val input = value.trim()
        val error = repository.validateAiInputSigns(input)
        _uiState.value = _uiState.value.copy(
            aiInputSigns = value,
            aiInputSignsError = error
        )

        if (input.isEmpty()) {
            repository.saveAiInputSigns("")
            return
        }

        if (error == null) {
            repository.saveAiInputSigns(input)
        }
    }

    fun showAiInputInfoDialog() {
        _uiState.value = _uiState.value.copy(showAiInputInfoDialog = true)
    }

    fun dismissAiInputInfoDialog() {
        _uiState.value = _uiState.value.copy(showAiInputInfoDialog = false)
    }

    fun showRestartConfirmDialog() {
        _uiState.value = _uiState.value.copy(showRestartConfirmDialog = true)
    }

    fun dismissRestartConfirmDialog() {
        _uiState.value = _uiState.value.copy(showRestartConfirmDialog = false)
    }

    fun restartSystem(onFailure: (String) -> Unit) {
        _uiState.value = _uiState.value.copy(showRestartConfirmDialog = false)
        Thread {
            val result = repository.restartSystem()
            if (!result.success) {
                onFailure(result.error)
            }
        }.start()
    }

    companion object {
        private const val TAG = "FrameworkSettingsViewModel"
    }
}

data class FrameworkSettingsUiState(
    val keepRotation: Boolean = false,
    val allowGetPackages: Boolean = false,
    val disableFlagSecure: Boolean = false,
    val aiInputExpand: Boolean = false,
    val aiInputSigns: String = "",
    val aiInputSignsError: String? = null,
    val showAiInputInfoDialog: Boolean = false,
    val showRestartConfirmDialog: Boolean = false
)
