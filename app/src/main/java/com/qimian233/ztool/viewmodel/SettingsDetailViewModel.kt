package com.qimian233.ztool.viewmodel

import android.os.Build
import androidx.lifecycle.ViewModel
import com.qimian233.ztool.data.settings.SettingsDetailRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class SettingsDetailViewModel(
    private val repository: SettingsDetailRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(SettingsDetailUiState())
    val uiState: StateFlow<SettingsDetailUiState> = _uiState.asStateFlow()

    fun loadSettings() {
        Thread {
            _uiState.value = repository.loadState()
        }.start()
    }

    fun setRemoveBlacklist(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(removeBlacklist = enabled)
        repository.saveRemoveBlacklist(enabled)
    }

    fun setModuleEnabledFromActivity(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(moduleEnabled = enabled)
    }

    fun setFloatMandatory(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(floatMandatory = enabled)
        repository.saveForceResizableActivities(enabled)
    }

    fun setSplitScreenMandatory(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(splitScreenMandatory = enabled)
        repository.saveSplitScreenMandatory(enabled)
    }

    fun setAllowNativePermissionController(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(allowNativePermissionController = enabled)
        repository.saveAllowNativePermissionController(enabled)
    }

    fun setAllowDisableDolby(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(allowDisableDolby = enabled)
        repository.saveAllowDisableDolby(enabled)
    }

    fun setAlwaysDisplaySuggestions(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(alwaysDisplaySuggestions = enabled)
        repository.saveAlwaysDisplaySuggestions(enabled)
    }

    fun showRestartDialog() {
        _uiState.value = _uiState.value.copy(showRestartDialog = true)
    }

    fun dismissRestartDialog() {
        _uiState.value = _uiState.value.copy(showRestartDialog = false)
    }

    fun restartScope(packageName: String) {
        _uiState.value = _uiState.value.copy(showRestartDialog = false)
        Thread {
            repository.forceStopScope(packageName)
        }.start()
    }
}

data class SettingsDetailUiState(
    val removeBlacklist: Boolean = false,
    val moduleEnabled: Boolean = false,
    val floatMandatory: Boolean = false,
    val splitScreenMandatory: Boolean = false,
    val allowDisableDolby: Boolean = false,
    val allowNativePermissionController: Boolean = false,
    val alwaysDisplaySuggestions: Boolean = false,
    val showZuiForceConfig: Boolean = Build.VERSION.SDK_INT >= 36,
    val showRestartDialog: Boolean = false
)
