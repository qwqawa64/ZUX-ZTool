package com.qimian233.ztool.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.qimian233.ztool.data.ota.OtaRestartResult
import com.qimian233.ztool.data.ota.OtaSettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class OtaSettingsViewModel(
    private val repository: OtaSettingsRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(OtaSettingsUiState())
    val uiState: StateFlow<OtaSettingsUiState> = _uiState.asStateFlow()

    fun initialize(unknownText: String) {
        loadSettings()
        loadCurrentDeviceInfo(unknownText)
    }

    fun loadSettings() {
        _uiState.value = repository.loadState()
    }

    fun loadCurrentDeviceInfo(unknownText: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val info = repository.loadCurrentDeviceInfo()
            withContext(Dispatchers.Main) {
                _uiState.value = _uiState.value.copy(
                    currentVersion = info.version
                )
            }
        }
    }

    fun setDisableOtaCheck(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(disableOtaCheck = enabled)
        repository.saveDisableOtaCheck(enabled)
    }

    fun setDisableAutoOtaInstall(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(noAutoOtaInstall = enabled)
        repository.saveNoAutoNightInstall(enabled)
    }

    fun setBlockOtaInstallDialog(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(blockOtaInstallDialog = enabled)
        repository.saveBlockOtaInstallDialog(enabled)
    }

    fun setHideOtaUpdate(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(hideOtaUpdateHint = enabled)
        repository.saveHideOtaUpdateHint(enabled)
    }

    fun setDisableOtaNotificationAndRedDot(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(disableOtaNotificationAndRedDot = enabled)
        repository.saveBlockOtaNotificationAndRedDot(enabled)
    }

    fun fetchOtaInfo(errorPrefix: String) {
        _uiState.value = _uiState.value.copy(isFetchingOtaInfo = true)
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val result = repository.fetchOtaInfo()
                withContext(Dispatchers.Main) {
                    _uiState.value = _uiState.value.copy(
                        otaInfoResult = result,
                        isFetchingOtaInfo = false
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to read OTA info", e)
                withContext(Dispatchers.Main) {
                    _uiState.value = _uiState.value.copy(
                        isFetchingOtaInfo = false,
                        errorDialogMessage = errorPrefix + e.message
                    )
                }
            }
        }
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
            if (result is OtaRestartResult.Failure) {
                withContext(Dispatchers.Main) {
                    onFailure()
                }
            }
        }
    }

    companion object {
        private const val TAG = "OtaSettings"
    }
}

data class OtaInfoResult(
    val fromVersion: String,
    val toVersion: String,
    val downloadUrl: String,
    val formattedSize: String,
    val md5: String,
    val changelog: String,
    val changelogCopyText: String,
    val isNewVersionAvailable: Boolean,
)

data class OtaSettingsUiState(
    val disableOtaCheck: Boolean = false,
    val currentVersion: String = "",
    val isFetchingOtaInfo: Boolean = false,
    val otaInfoResult: OtaInfoResult? = null,
    val errorDialogMessage: String? = null,
    val showRestartDialog: Boolean = false,
    val hideOtaUpdateHint: Boolean = false,
    val noAutoOtaInstall: Boolean = false,
    val disableOtaNotificationAndRedDot: Boolean = false,
    val blockOtaInstallDialog: Boolean = false,
)
