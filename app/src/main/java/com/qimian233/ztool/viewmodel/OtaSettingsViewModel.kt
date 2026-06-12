package com.qimian233.ztool.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.qimian233.ztool.data.ota.FirmwareFetchResult
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
        repository.ensureCustomOtaParametersEnabled()
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
                val current = _uiState.value
                _uiState.value = current.copy(
                    currentVersion = info.version,
                    currentSn = info.sn,
                    firmwareSnInput = if (current.firmwareSnInput.isEmpty() && info.sn != unknownText) {
                        info.sn
                    } else {
                        current.firmwareSnInput
                    }
                )
            }
        }
    }

    fun setDisableOtaCheck(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(disableOtaCheck = enabled)
        repository.saveDisableOtaCheck(enabled)
    }

    fun setFirmwareSnInput(value: String) {
        _uiState.value = _uiState.value.copy(firmwareSnInput = value)
    }

    fun setCustomVersion(value: String) {
        _uiState.value = _uiState.value.copy(customVersion = value)
        repository.saveCustomVersion(value)
    }

    fun setCustomDeviceId(value: String) {
        _uiState.value = _uiState.value.copy(customDeviceId = value)
        repository.saveCustomDeviceId(value)
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
                Log.e(TAG, "读取OTA信息失败", e)
                withContext(Dispatchers.Main) {
                    _uiState.value = _uiState.value.copy(
                        isFetchingOtaInfo = false,
                        errorDialogMessage = errorPrefix + e.message
                    )
                }
            }
        }
    }

    fun fetchFirmware(emptySnMessage: String) {
        val sn = _uiState.value.firmwareSnInput.trim().ifEmpty {
            repository.getMachineSn().orEmpty()
        }
        if (sn.isEmpty()) {
            _uiState.value = _uiState.value.copy(errorDialogMessage = emptySnMessage)
            return
        }

        _uiState.value = _uiState.value.copy(isFetchingFirmware = true)
        repository.fetchFirmware(sn) { result ->
            when (result) {
                is FirmwareFetchResult.Failure -> {
                    _uiState.value = _uiState.value.copy(
                        isFetchingFirmware = false,
                        errorDialogMessage = result.message
                    )
                }
                is FirmwareFetchResult.Success -> {
                    _uiState.value = _uiState.value.copy(
                        isFetchingFirmware = false,
                        firmwareResult = result.firmware
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

    fun restartScope(packageName: String, onFailure: () -> Unit) {
        _uiState.value = _uiState.value.copy(showRestartDialog = false)
        viewModelScope.launch(Dispatchers.IO) {
            val result = repository.restartScope(packageName)
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
    val changelogCopyText: String
)

data class FirmwareResult(
    val downloadUrl: String,
    val password: String,
    val platform: String,
    val method: String,
    val firstUploadTime: String,
    val lastUpdateTime: String
)

data class OtaSettingsUiState(
    val disableOtaCheck: Boolean = false,
    val customVersion: String = "",
    val customDeviceId: String = "",
    val currentVersion: String = "",
    val currentSn: String = "",
    val firmwareSnInput: String = "",
    val isFetchingOtaInfo: Boolean = false,
    val isFetchingFirmware: Boolean = false,
    val otaInfoResult: OtaInfoResult? = null,
    val firmwareResult: FirmwareResult? = null,
    val errorDialogMessage: String? = null,
    val showRestartDialog: Boolean = false
)
