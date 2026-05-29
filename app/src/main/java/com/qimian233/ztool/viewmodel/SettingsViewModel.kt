package com.qimian233.ztool.viewmodel

import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import com.qimian233.ztool.data.settings.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class SettingsViewModel(
    private val repository: SettingsRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(repository.loadState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    fun refresh() {
        _uiState.value = repository.loadState().copy(
            showRestoreConfirmDialog = _uiState.value.showRestoreConfirmDialog,
            showAboutDialog = _uiState.value.showAboutDialog
        )
    }

    fun backupFileName(): String = repository.backupFileName()

    fun backupConfig(uri: Uri, onResult: (Boolean) -> Unit) {
        Thread {
            val result = runCatching { repository.backupConfig(uri) }
                .onFailure { Log.e(TAG, "Config backup failed", it) }
                .getOrDefault(false)
            onResult(result)
        }.start()
    }

    fun restoreConfig(uri: Uri, onResult: (Boolean) -> Unit) {
        Thread {
            val result = runCatching { repository.restoreConfig(uri) }
                .onFailure { Log.e(TAG, "Config restore failed", it) }
                .getOrDefault(false)
            refresh()
            onResult(result)
        }.start()
    }

    fun showRestoreConfirmDialog() {
        _uiState.value = _uiState.value.copy(showRestoreConfirmDialog = true)
    }

    fun dismissRestoreConfirmDialog() {
        _uiState.value = _uiState.value.copy(showRestoreConfirmDialog = false)
    }

    fun restoreDefaultConfig() {
        repository.restoreDefaultConfig()
        _uiState.value = repository.loadState().copy(showRestoreConfirmDialog = false)
    }

    fun setLogServiceEnabled(isEnabled: Boolean) {
        _uiState.value = _uiState.value.copy(isLogServiceEnabled = isEnabled)
        repository.setLogServiceEnabled(isEnabled)
    }

    fun setDetailedLoggingEnabled(isEnabled: Boolean) {
        _uiState.value = _uiState.value.copy(isDetailedLoggingEnabled = isEnabled)
        repository.setDetailedLoggingEnabled(isEnabled)
    }

    fun setHomepageYiyanEnabled(isEnabled: Boolean) {
        _uiState.value = _uiState.value.copy(isHomepageYiyanEnabled = isEnabled)
        repository.setHomepageYiyanEnabled(isEnabled)
    }

    fun showAboutDialog() {
        _uiState.value = _uiState.value.copy(showAboutDialog = true)
    }

    fun dismissAboutDialog() {
        _uiState.value = _uiState.value.copy(showAboutDialog = false)
    }

    companion object {
        private const val TAG = "SettingsViewModel"
    }
}

data class SettingsUiState(
    val isLogServiceEnabled: Boolean = false,
    val isDetailedLoggingEnabled: Boolean = false,
    val isHomepageYiyanEnabled: Boolean = true,
    val showRestoreConfirmDialog: Boolean = false,
    val showAboutDialog: Boolean = false,
    val moduleVersion: String = ""
)
