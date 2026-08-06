package com.qimian233.ztool.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.qimian233.ztool.data.systemui.LockScreenSettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class LockScreenSettingsViewModel(
    private val repository: LockScreenSettingsRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(loadInitialState())
    val uiState: StateFlow<LockScreenSettingsUiState> = _uiState.asStateFlow()

    fun loadSettings() {
        _uiState.value = loadInitialState()
    }

    private fun loadInitialState(): LockScreenSettingsUiState {
        try {
            return repository.loadState()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load lock screen settings", e)
        }
        return LockScreenSettingsUiState()
    }

    fun setYiYanEnabled(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(yiYanEnabled = enabled)
        repository.saveYiYanEnabled(enabled)
    }

    fun setNativeAodEnabled(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(nativeAod = enabled)
        repository.saveNativeAod(enabled)

        if (enabled && _uiState.value.lenovoAod) {
            repository.saveLenovoAod(false)
            _uiState.value = _uiState.value.copy(lenovoAod = false)
        }
    }

    fun setLenovoAodEnabled(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(lenovoAod = enabled)
        repository.saveLenovoAod(enabled)

        if (enabled && _uiState.value.nativeAod) {
            repository.saveNativeAod(false)
            _uiState.value = _uiState.value.copy(nativeAod = false)
        }
    }

    fun openLenovoAodSettings() {
        viewModelScope.launch(Dispatchers.IO) {
            val result = repository.openLenovoAodSettings()
            Log.d(TAG, "Lenovo AOD settings result: $result")
        }
    }

    fun setApiAddress(value: String) {
        _uiState.value = _uiState.value.copy(apiAddress = value)
    }

    fun setRegex(value: String) {
        _uiState.value = _uiState.value.copy(regex = value)
    }

    fun setChargeWattsOption(selectedOption: String) {
        _uiState.value = _uiState.value.copy(
            chargeWattsOption = selectedOption,
            showRootPermissionDialog = repository.saveChargeWattsOption(selectedOption)
        )
    }

    fun setShowVoltage(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(showVoltage = enabled)
        repository.saveShowVoltage(enabled)
    }

    fun setShowCurrent(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(showCurrent = enabled)
        repository.saveShowCurrent(enabled)
    }

    fun setShowPower(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(showPower = enabled)
        repository.saveShowPower(enabled)
    }

    fun setShowTemperature(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(showTemperature = enabled)
        repository.saveShowTemperature(enabled)
    }

    fun setShowIndicator(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(showIndicator = enabled)
        repository.saveShowIndicator(enabled)
    }

    fun setCustomFormatEnabled(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(customFormatEnabled = enabled)
        repository.saveCustomFormatEnabled(enabled)
    }

    fun setCustomFormat(value: String) {
        _uiState.value = _uiState.value.copy(customFormat = value)
        repository.saveCustomFormat(value)
    }

    fun dismissRootPermissionDialog() {
        _uiState.value = _uiState.value.copy(showRootPermissionDialog = false)
    }

    fun confirmSystemUiPermission() {
        repository.saveSystemUiPermissionConfirmed()
        _uiState.value = _uiState.value.copy(showRootPermissionDialog = false)
    }

    fun testApiConnection(onMissingApiAddress: () -> Unit) {
        val current = _uiState.value
        val apiUrl = current.apiAddress.trim()
        val regexValue = current.regex.trim()

        if (apiUrl.isEmpty()) {
            onMissingApiAddress()
            return
        }

        _uiState.value = current.copy(isTestingApi = true)
        Thread {
            val result = repository.testApi(apiUrl, regexValue)
            _uiState.value = _uiState.value.copy(
                isTestingApi = false,
                apiTestResult = result
            )
        }.start()
    }

    fun saveYiYanConfiguration() {
        val current = _uiState.value
        repository.saveYiYanConfiguration(current.apiAddress, current.regex)
        _uiState.value = current.copy(
            yiYanEnabled = true,
            apiTestResult = null
        )
    }

    fun dismissApiTestResult() {
        _uiState.value = _uiState.value.copy(apiTestResult = null)
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
        private const val TAG = "LockScreenSettingsViewModel"
    }
}

data class ApiTestResult(
    val title: String,
    val message: String,
    val success: Boolean
)

data class LockScreenSettingsUiState(
    val nativeAod: Boolean = false,
    val lenovoAod: Boolean = false,
    val yiYanEnabled: Boolean = false,
    val apiAddress: String = "",
    val regex: String = "",
    val chargeWattsOption: String = "",
    val isTestingApi: Boolean = false,
    val showRootPermissionDialog: Boolean = false,
    val apiTestResult: ApiTestResult? = null,
    // RealWatts 子开关
    val showVoltage: Boolean = false,
    val showCurrent: Boolean = false,
    val showPower: Boolean = true,
    val showTemperature: Boolean = false,
    val showIndicator: Boolean = true,
    val customFormatEnabled: Boolean = false,
    val customFormat: String = "",
    val isRestartProcessing: Boolean = false,
    val showRestartDialog: Boolean = false
)
