package com.qimian233.ztool.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.qimian233.ztool.data.systemui.SystemUiSettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SystemUiSettingsViewModel(
    private val repository: SystemUiSettingsRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(SystemUiSettingsUiState())
    val uiState: StateFlow<SystemUiSettingsUiState> = _uiState.asStateFlow()

    fun loadSettings() {
        Thread {
            try {
                _uiState.value = repository.loadState()
                Log.d(TAG, "Settings loaded")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load settings: ${e.message}")
            }
        }.start()
    }

    fun setNativeAodEnabled(
        enabled: Boolean,
        onLenovoAodDisabled: () -> Unit,
        onFailure: (String) -> Unit
    ) {
        if (_uiState.value.isAodSwitchProcessing) {
            Log.d(TAG, "AOD switch is processing, ignore duplicate operation")
            return
        }

        _uiState.value = _uiState.value.copy(
            nativeAod = enabled,
            isAodSwitchProcessing = true
        )
        repository.saveNativeAod(enabled)

        if (repository.isLenovoAodEnabled()) {
            repository.saveLenovoAod(false)
            _uiState.value = _uiState.value.copy(lenovoAod = false)
            onLenovoAodDisabled()
        }

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val result = repository.setNativeAodEnabled(enabled)
                Log.d(TAG, "Native AOD command result: success=${result.success}, exitCode=${result.exitCode}")
                if (!result.success) {
                    withContext(Dispatchers.Main) {
                        _uiState.value = _uiState.value.copy(nativeAod = !enabled)
                        onFailure(result.error)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to set native AOD: ${e.message}")
                withContext(Dispatchers.Main) {
                    _uiState.value = _uiState.value.copy(nativeAod = !enabled)
                    onFailure(e.message.orEmpty())
                }
            } finally {
                withContext(Dispatchers.Main) {
                    _uiState.value = _uiState.value.copy(isAodSwitchProcessing = false)
                }
            }
        }
    }

    fun setLenovoAodEnabled(enabled: Boolean) {
        if (_uiState.value.isAodSwitchProcessing) {
            Log.d(TAG, "AOD switch is processing, ignore duplicate operation")
            return
        }

        _uiState.value = _uiState.value.copy(
            lenovoAod = enabled,
            isAodSwitchProcessing = true
        )
        repository.saveLenovoAod(enabled)

        Thread {
            if (repository.isNativeAodEnabled()) {
                repository.setNativeAodEnabled(false)
                _uiState.value = _uiState.value.copy(nativeAod = false)
            }
            _uiState.value = _uiState.value.copy(isAodSwitchProcessing = false)
        }.start()
    }

    fun openLenovoAodSettings() {
        Thread {
            val result = repository.openLenovoAodSettings()
            Log.d(TAG, "Lenovo AOD settings result: $result")
        }.start()
    }

    fun setNoChargeAnimation(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(noChargeAnimation = enabled)
        repository.saveNoChargeAnimation(enabled)
    }

    fun setChargeAnimationFix(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(chargeAnimationFix = enabled)
        repository.saveChargeAnimationFix(enabled)
    }

    fun setGuestModeController(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(guestModeController = enabled)
        repository.saveGuestModeController(enabled)
    }

    fun showRestartDialog() {
        if (!_uiState.value.isRestartProcessing) {
            _uiState.value = _uiState.value.copy(showRestartDialog = true)
        }
    }

    fun dismissRestartDialog() {
        _uiState.value = _uiState.value.copy(showRestartDialog = false)
    }

    fun forceStopScope(packageName: String, onResult: (Boolean, String) -> Unit) {
        if (packageName.isEmpty() || _uiState.value.isRestartProcessing) return

        _uiState.value = _uiState.value.copy(
            showRestartDialog = false,
            isRestartProcessing = true
        )

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val result = repository.forceStop(packageName)
                withContext(Dispatchers.Main) {
                    onResult(result.success, result.error)
                }
                Log.d(TAG, "Force stop app result: success=${result.success}")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to force stop app: ${e.message}")
                withContext(Dispatchers.Main) {
                    onResult(false, e.message.orEmpty())
                }
            } finally {
                withContext(Dispatchers.Main) {
                    _uiState.value = _uiState.value.copy(isRestartProcessing = false)
                }
            }
        }

        Thread {
            try {
                val result = repository.forceStopWallpaperSettings()
                Log.d(TAG, "Force stop wallpaper settings result: success=${result.success}")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to force stop wallpaper settings: ${e.message}")
            }
        }.start()
    }

    companion object {
        private const val TAG = "SystemUiSettingsViewModel"
    }
}

data class SystemUiSettingsUiState(
    val nativeAod: Boolean = false,
    val lenovoAod: Boolean = false,
    val noChargeAnimation: Boolean = false,
    val chargeAnimationFix: Boolean = false,
    val guestModeController: Boolean = false,
    val isAodSwitchProcessing: Boolean = false,
    val isRestartProcessing: Boolean = false,
    val showRestartDialog: Boolean = false
)
