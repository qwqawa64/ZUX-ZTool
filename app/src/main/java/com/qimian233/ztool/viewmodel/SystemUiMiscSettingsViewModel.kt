package com.qimian233.ztool.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.qimian233.ztool.data.systemui.FirmwareFetchResult
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
        loadCurrentSn()
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

    fun setBypassFaceAuthTimeout(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(bypassFaceAuthTimeout = enabled)
        repository.saveBypassFaceAuthTimeout(enabled)
    }

    fun setAospScrollCapture(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(aospScrollCapture = enabled)
        repository.saveAospScrollCapture(enabled)
    }

    fun setShadeReboundFix(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(shadeReboundFix = enabled)
        repository.saveShadeReboundFix(enabled)
    }

    private fun loadCurrentSn() {
        viewModelScope.launch(Dispatchers.IO) {
            val sn = repository.loadCurrentSn()
            withContext(Dispatchers.Main) {
                _uiState.value = _uiState.value.copy(
                    currentSn = sn.orEmpty(),
                    firmwareSnInput = if (_uiState.value.firmwareSnInput.isEmpty() && !sn.isNullOrEmpty()) {
                        sn
                    } else {
                        _uiState.value.firmwareSnInput
                    }
                )
            }
        }
    }

    fun setFirmwareSnInput(value: String) {
        _uiState.value = _uiState.value.copy(firmwareSnInput = value)
    }

    fun fetchFirmware(emptySnMessage: String) {
        val sn = _uiState.value.firmwareSnInput.trim().ifEmpty { _uiState.value.currentSn }
        if (sn.isEmpty()) {
            _uiState.value = _uiState.value.copy(errorDialogMessage = emptySnMessage)
            return
        }

        _uiState.value = _uiState.value.copy(isFetchingFirmware = true)
        viewModelScope.launch(Dispatchers.IO) {
            val result = repository.fetchFirmware(sn)
            withContext(Dispatchers.Main) {
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
