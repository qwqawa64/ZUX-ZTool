package com.qimian233.ztool.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import com.qimian233.ztool.data.packageinstaller.PackageInstallerSettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class PackageInstallerSettingsViewModel(
    private val repository: PackageInstallerSettingsRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(PackageInstallerSettingsUiState())
    val uiState: StateFlow<PackageInstallerSettingsUiState> = _uiState.asStateFlow()

    fun loadSettings() {
        try {
            _uiState.value = repository.loadState()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load package installer settings", e)
        }
    }

    fun setDisableScanApk(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(disableScanApk = enabled)
        repository.saveDisableScanApk(enabled)
    }

    fun setAlwaysAllowPermission(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(alwaysAllowPermission = enabled)
        repository.saveAlwaysAllowPermission(enabled)
    }

    fun setSkipWarnPage(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(skipWarnPage = enabled)
        repository.saveSkipWarnPage(enabled)
    }

    fun setDisableInstallerAd(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(disableInstallerAd = enabled)
        repository.saveDisableInstallerAd(enabled)
    }

    fun setPackageInstallerStyleHook(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(packageInstallerStyleHook = enabled)
        repository.savePackageInstallerStyleHook(enabled)
    }

    fun setDisableDeletePackage(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(disableDeletePackage = enabled)
        repository.saveDisableDeletePackage(enabled)
    }

    fun showRestartConfirmDialog() {
        _uiState.value = _uiState.value.copy(showRestartConfirmDialog = true)
    }

    fun dismissRestartConfirmDialog() {
        _uiState.value = _uiState.value.copy(showRestartConfirmDialog = false)
    }

    fun forceStopPackage(packageName: String, onFailure: () -> Unit) {
        _uiState.value = _uiState.value.copy(showRestartConfirmDialog = false)
        Thread {
            val result = repository.forceStopPackage(packageName)
            if (!result.success) {
                onFailure()
            }
        }.start()
    }

    companion object {
        private const val TAG = "PackageInstallerSettingsViewModel"
    }
}

data class PackageInstallerSettingsUiState(
    val disableScanApk: Boolean = false,
    val alwaysAllowPermission: Boolean = false,
    val skipWarnPage: Boolean = false,
    val disableInstallerAd: Boolean = false,
    val packageInstallerStyleHook: Boolean = false,
    val disableDeletePackage: Boolean = false,
    val showRestartConfirmDialog: Boolean = false
)
