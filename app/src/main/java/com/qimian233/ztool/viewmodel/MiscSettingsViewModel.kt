package com.qimian233.ztool.viewmodel

import android.content.Context
import android.util.Log
import android.widget.Toast
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.qimian233.ztool.data.misc.FirmwareFetchResult
import com.qimian233.ztool.data.misc.MiscSettingsRepository
import com.qimian233.ztool.data.misc.PcFlashFirmwareRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MiscSettingsViewModel(
    private val context: Context,
    private val repository: MiscSettingsRepository = MiscSettingsRepository(),
    private val firmwareRepository: PcFlashFirmwareRepository = PcFlashFirmwareRepository(context.applicationContext)
) : ViewModel() {
    private val _uiState = MutableStateFlow(MiscSettingsUiState())
    val uiState: StateFlow<MiscSettingsUiState> = _uiState.asStateFlow()

    private val _firmwareUiState = MutableStateFlow(PcFlashFirmwareUiState())
    val firmwareUiState: StateFlow<PcFlashFirmwareUiState> = _firmwareUiState.asStateFlow()

    init {
        loadCurrentSn()
    }

    fun refresh() {
        viewModelScope.launch(Dispatchers.IO) {
            val apiVersion = repository.getApiVersion()
            _uiState.value = _uiState.value.copy(
                apiVersion = apiVersion,
                showDeleteOtaPackageDialog = _uiState.value.showDeleteOtaPackageDialog,
                deleteOtaPackageInProgress = _uiState.value.deleteOtaPackageInProgress,
                deleteOtaPackageStatus = _uiState.value.deleteOtaPackageStatus,
                deleteOtaPackageMessage = _uiState.value.deleteOtaPackageMessage,
                fixNightModeOverrideInProgress = _uiState.value.fixNightModeOverrideInProgress
            )
        }
    }

    fun showDeleteOtaPackageConfirmDialog() {
        _uiState.value = _uiState.value.copy(showDeleteOtaPackageDialog = true)
    }

    fun dismissDeleteOtaPackageDialog() {
        _uiState.value = _uiState.value.copy(showDeleteOtaPackageDialog = false)
    }

    fun performDeleteOtaPackage() {
        _uiState.value = _uiState.value.copy(
            showDeleteOtaPackageDialog = false,
            deleteOtaPackageInProgress = true
        )
        repository.deleteOtaPackage { status, message ->
            Log.i(TAG, "Delete /data/ota_package: [$status] $message")
            _uiState.value = _uiState.value.copy(
                deleteOtaPackageInProgress = false,
                deleteOtaPackageStatus = status,
                deleteOtaPackageMessage = message
            )
        }
    }

    fun consumeDeleteOtaPackageResult() {
        _uiState.value = _uiState.value.copy(
            deleteOtaPackageStatus = null,
            deleteOtaPackageMessage = null
        )
    }

    /**
     * One-shot root fix: clears the ZUI-persisted night-mode override
     * (ui_night_mode_override_on/off) and retunes uimode so dark theme
     * auto switching ("sunset to sunrise") takes effect immediately.
     */
    fun fixNightModeOverride() {
        if (_uiState.value.fixNightModeOverrideInProgress) return
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.value = _uiState.value.copy(fixNightModeOverrideInProgress = true)
            val result = repository.fixNightModeOverride(context.applicationContext)
            _uiState.value = _uiState.value.copy(fixNightModeOverrideInProgress = false)
            launch(Dispatchers.Main) {
                Toast.makeText(
                    context,
                    if (result.success) result.output else result.error,
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun loadCurrentSn() {
        viewModelScope.launch(Dispatchers.IO) {
            val sn = firmwareRepository.loadCurrentSn()
            _firmwareUiState.value = _firmwareUiState.value.copy(
                currentSn = sn.orEmpty(),
                snInput = if (_firmwareUiState.value.snInput.isEmpty() && !sn.isNullOrEmpty()) {
                    sn
                } else {
                    _firmwareUiState.value.snInput
                }
            )
        }
    }

    fun setFirmwareSnInput(value: String) {
        _firmwareUiState.value = _firmwareUiState.value.copy(snInput = value)
    }

    fun fetchFirmware(emptySnMessage: String) {
        val sn = _firmwareUiState.value.snInput.trim().ifEmpty { _firmwareUiState.value.currentSn }
        if (sn.isEmpty()) {
            _firmwareUiState.value = _firmwareUiState.value.copy(errorDialogMessage = emptySnMessage)
            return
        }

        _firmwareUiState.value = _firmwareUiState.value.copy(isFetching = true)
        viewModelScope.launch(Dispatchers.IO) {
            val result = firmwareRepository.fetchFirmware(sn)
            _firmwareUiState.value = when (result) {
                is FirmwareFetchResult.Failure -> _firmwareUiState.value.copy(
                    isFetching = false,
                    errorDialogMessage = result.message
                )
                is FirmwareFetchResult.Success -> _firmwareUiState.value.copy(
                    isFetching = false,
                    firmwareResult = result.firmware
                )
            }
        }
    }

    fun dismissFirmwareErrorDialog() {
        _firmwareUiState.value = _firmwareUiState.value.copy(errorDialogMessage = null)
    }

    companion object {
        private const val TAG = "MiscVM"
    }
}

data class MiscSettingsUiState(
    val apiVersion: Int = 0,
    val showDeleteOtaPackageDialog: Boolean = false,
    val deleteOtaPackageInProgress: Boolean = false,
    val deleteOtaPackageStatus: String? = null,
    val deleteOtaPackageMessage: String? = null,
    val fixNightModeOverrideInProgress: Boolean = false
)

data class PcFlashFirmwareUiState(
    val snInput: String = "",
    val currentSn: String = "",
    val isFetching: Boolean = false,
    val firmwareResult: com.qimian233.ztool.data.misc.FirmwareResult? = null,
    val errorDialogMessage: String? = null
)
