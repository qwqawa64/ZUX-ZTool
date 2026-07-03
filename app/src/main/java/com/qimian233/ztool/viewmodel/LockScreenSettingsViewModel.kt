package com.qimian233.ztool.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import com.qimian233.ztool.data.systemui.LockScreenSettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class LockScreenSettingsViewModel(
    private val repository: LockScreenSettingsRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(LockScreenSettingsUiState())
    val uiState: StateFlow<LockScreenSettingsUiState> = _uiState.asStateFlow()

    fun loadSettings() {
        try {
            _uiState.value = repository.loadState()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load lock screen settings", e)
        }
    }

    fun setYiYanEnabled(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(yiYanEnabled = enabled)
        repository.saveYiYanEnabled(enabled)
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
    val yiYanEnabled: Boolean = false,
    val apiAddress: String = "",
    val regex: String = "",
    val chargeWattsOption: String = "",
    val isTestingApi: Boolean = false,
    val showRootPermissionDialog: Boolean = false,
    val apiTestResult: ApiTestResult? = null
)
