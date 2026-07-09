package com.qimian233.ztool.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.qimian233.ztool.data.gametool.GameToolRestartResult
import com.qimian233.ztool.data.gametool.GameToolSettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class GameToolSettingsViewModel(
    private val repository: GameToolSettingsRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(GameToolSettingsUiState())
    val uiState: StateFlow<GameToolSettingsUiState> = _uiState.asStateFlow()

    fun loadSettings() {
        try {
            _uiState.value = repository.loadState()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load game tool settings", e)
        }
    }

    fun setDisableGameAudio(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(disableGameAudio = enabled)
        repository.saveDisableGameAudio(enabled)
    }

    fun setDisguiseDevice(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(disguiseDevice = enabled)
        repository.saveDisguiseDevice(enabled)
    }

    fun setFixCpuFrequency(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(fixCpuFrequency = enabled)
        repository.saveFixCpuFrequency(enabled)
    }

    fun setFixSocTemperature(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(fixSocTemperature = enabled)
        repository.saveFixSocTemperature(enabled)
    }

    fun setMistakeTouchMode(mode: MistakeTouchMode) {
        _uiState.value = _uiState.value.copy(mistakeTouchMode = mode)
        repository.saveMistakeTouchMode(mode)
    }

    fun setWhitelistPackages(packageNames: List<String>) {
        packageNames.forEach {
            Log.d(TAG, "Selected game package: $it")
        }
        _uiState.value = _uiState.value.copy(targetGamePackages = packageNames)
        repository.saveWhitelistPackages(packageNames)
    }

    fun loadManagedGamePackages(): List<String> {
        return repository.loadManagedGamePackages()
    }

    fun showRestartConfirmDialog() {
        _uiState.value = _uiState.value.copy(showRestartConfirmDialog = true)
    }

    fun dismissRestartConfirmDialog() {
        _uiState.value = _uiState.value.copy(showRestartConfirmDialog = false)
    }

    fun forceStopPackage(onFailure: () -> Unit) {
        _uiState.value = _uiState.value.copy(showRestartConfirmDialog = false)
        viewModelScope.launch(Dispatchers.IO) {
            val result = repository.forceStopPackage()
            if (result is GameToolRestartResult.Failure) {
                withContext(Dispatchers.Main) {
                    onFailure()
                }
            }
        }
    }

    companion object {
        private const val TAG = "GameToolSettngs"
    }
}

enum class MistakeTouchMode {
    Default,
    AllGames,
    Whitelist
}

data class GameToolSettingsUiState(
    val disableGameAudio: Boolean = false,
    val disguiseDevice: Boolean = false,
    val fixCpuFrequency: Boolean = false,
    val fixSocTemperature: Boolean = false,
    val mistakeTouchMode: MistakeTouchMode = MistakeTouchMode.Default,
    val targetGamePackages: List<String> = emptyList(),
    val showRestartConfirmDialog: Boolean = false
) {
    val whitelistCount: Int
        get() = targetGamePackages.size
}
