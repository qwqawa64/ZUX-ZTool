package com.qimian233.ztool.viewmodel

import android.content.Context
import android.util.Log
import android.widget.Toast
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.qimian233.ztool.R
import com.qimian233.ztool.XposedServiceBridge
import com.qimian233.ztool.data.advanced.AdvancedSettingsRepository
import com.qimian233.ztool.data.advanced.FirmwareFetchResult
import com.qimian233.ztool.data.advanced.HotReloadDetail
import com.qimian233.ztool.data.advanced.PcFlashFirmwareRepository
import com.qimian233.ztool.data.advanced.PersistentResetDetail
import com.qimian233.ztool.data.systemframework.FrameworkSettingsRepository
import com.qimian233.ztool.dexindex.base.DexIndexManager
import com.qimian233.ztool.dexindex.base.DexIndexProgress
import io.github.libxposed.service.HookedTarget
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class AdvancedSettingsViewModel(
    private val repository: AdvancedSettingsRepository = AdvancedSettingsRepository(),
    private val firmwareRepository: PcFlashFirmwareRepository? = null
) : ViewModel() {
    private val _uiState = MutableStateFlow(AdvancedSettingsUiState())
    val uiState: StateFlow<AdvancedSettingsUiState> = _uiState.asStateFlow()

    private val _firmwareUiState = MutableStateFlow(PcFlashFirmwareUiState())
    val firmwareUiState: StateFlow<PcFlashFirmwareUiState> = _firmwareUiState.asStateFlow()

    private val _dexIndexState = MutableStateFlow(DexIndexRefreshUiState())
    val dexIndexState: StateFlow<DexIndexRefreshUiState> = _dexIndexState.asStateFlow()

    init {
        // DexKit index progress hot-updates, shown in real time by the progress Dialog
        viewModelScope.launch {
            DexIndexManager.progress.collect { p ->
                _dexIndexState.value = _dexIndexState.value.copy(progress = p)
            }
        }
        loadCurrentSn()
    }

    fun refresh() {
        viewModelScope.launch(Dispatchers.IO) {
            val apiVersion = repository.getApiVersion()
            val runningTargets = repository.getRunningTargets()
            _uiState.value = AdvancedSettingsUiState(
                apiVersion = apiVersion,
                runningTargetCount = runningTargets.size,
                runningTargets = runningTargets,
                hotReloadInProgress = _uiState.value.hotReloadInProgress,
                showHotReloadDialog = _uiState.value.showHotReloadDialog,
                resetInProgress = _uiState.value.resetInProgress,
                showResetDialog = _uiState.value.showResetDialog,
                resetDetails = _uiState.value.resetDetails,
                resetResultSucceeded = _uiState.value.resetResultSucceeded,
                resetResultFailed = _uiState.value.resetResultFailed,
                resetResultUnsupported = _uiState.value.resetResultUnsupported,
                showDeleteOtaPackageDialog = _uiState.value.showDeleteOtaPackageDialog,
                deleteOtaPackageInProgress = _uiState.value.deleteOtaPackageInProgress,
                deleteOtaPackageStatus = _uiState.value.deleteOtaPackageStatus,
                deleteOtaPackageMessage = _uiState.value.deleteOtaPackageMessage,
                fixNightModeOverrideInProgress = _uiState.value.fixNightModeOverrideInProgress
            )
        }
    }

    fun showHotReloadConfirmDialog() {
        _uiState.value = _uiState.value.copy(showHotReloadDialog = true)
    }

    fun dismissHotReloadDialog() {
        _uiState.value = _uiState.value.copy(showHotReloadDialog = false)
    }

    fun showResetConfirmDialog() {
        _uiState.value = _uiState.value.copy(showResetDialog = true)
    }

    fun dismissResetDialog() {
        _uiState.value = _uiState.value.copy(showResetDialog = false)
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

    fun performHotReload() {
        _uiState.value = _uiState.value.copy(
            showHotReloadDialog = false,
            hotReloadInProgress = true,
            hotReloadDetails = emptyList()
        )
        if (XposedServiceBridge.getApiVersion() < 102) {
            Log.e(TAG, "Low API version, unable to perform hot reload!")
            return
        }
        repository.performHotReloadAll(
            onProgress = { target, result ->
                Log.d(TAG, "Hot reload: ${target.processName} -> ${result.status()} ${result.message() ?: ""}")
            },
            onComplete = { succeeded, failed, unsupported, died, details ->
                Log.d(TAG, "Hot reload finished: succeeded=$succeeded, failed=$failed, unsupported=$unsupported, processDied=$died")
                for (d in details) {
                    if (d.status != "SUCCEEDED") {
                        Log.w(TAG, "  [${d.status}] ${d.processName}: ${d.message}")
                    }
                }
                _uiState.value = _uiState.value.copy(
                    hotReloadInProgress = false,
                    hotReloadResultSucceeded = succeeded,
                    hotReloadResultFailed = failed,
                    hotReloadResultUnsupported = unsupported,
                    hotReloadResultDied = died,
                    hotReloadDetails = details
                )
            }
        )
    }

    fun performResetPersistentValues() {
        _uiState.value = _uiState.value.copy(
            showResetDialog = false,
            resetInProgress = true,
            resetDetails = emptyList()
        )

        repository.resetPersistentValues(
            onComplete = { succeeded, failed, unsupported, details ->
                Log.d(TAG, "Persistent value reset finished: succeeded=$succeeded, failed=$failed, unsupported=$unsupported")
                for (d in details) {
                    if (d.status != "SUCCEEDED") {
                        Log.w(TAG, "  [${d.status}] ${d.key}: ${d.message}")
                    }
                }
                _uiState.value = _uiState.value.copy(
                    resetInProgress = false,
                    resetResultSucceeded = succeeded,
                    resetResultFailed = failed,
                    resetResultUnsupported = unsupported,
                    resetDetails = details
                )
            }
        )
    }

    /** Manually refresh the DexKit index: foreground progress Dialog + Toast of the result on completion. */
    fun refreshDexIndex(context: Context) {
        if (_dexIndexState.value.refreshing) return
        viewModelScope.launch(Dispatchers.Default) {
            _dexIndexState.value = _dexIndexState.value.copy(refreshing = true, resultRes = null)
            val results = try {
                DexIndexManager.indexAll(context)
            } catch (t: Throwable) {
                Log.e(TAG, "dex index refresh failed", t)
                emptyMap()
            }
            val resultRes = if (results.isNotEmpty() && results.values.any { it }) {
                R.string.common_dex_index_refreshed
            } else {
                R.string.common_dex_index_refresh_failed
            }
            _dexIndexState.value = _dexIndexState.value.copy(
                refreshing = false,
                resultRes = resultRes
            )
        }
    }

    fun consumeDexIndexResult() {
        _dexIndexState.value = _dexIndexState.value.copy(resultRes = null)
    }

    /**
     * One-shot root fix: clears the ZUI-persisted night-mode override
     * (ui_night_mode_override_on/off) and retunes uimode so dark theme
     * auto switching ("sunset to sunrise") takes effect immediately.
     */
    fun fixNightModeOverride(context: Context) {
        if (_uiState.value.fixNightModeOverrideInProgress) return
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.value = _uiState.value.copy(fixNightModeOverrideInProgress = true)
            val repository = FrameworkSettingsRepository(context.applicationContext)
            val result = repository.fixNightModeOverride()
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
        val firmwareRepository = firmwareRepository ?: return
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
        val firmwareRepository = firmwareRepository ?: return
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
        private const val TAG = "AdvancedVM"
    }
}

data class PcFlashFirmwareUiState(
    val snInput: String = "",
    val currentSn: String = "",
    val isFetching: Boolean = false,
    val firmwareResult: com.qimian233.ztool.data.advanced.FirmwareResult? = null,
    val errorDialogMessage: String? = null
)

data class AdvancedSettingsUiState(
    val apiVersion: Int = 0,
    val runningTargetCount: Int = 0,
    val runningTargets: List<HookedTarget> = emptyList(),
    val hotReloadInProgress: Boolean = false,
    val showHotReloadDialog: Boolean = false,
    val hotReloadDetails: List<HotReloadDetail> = emptyList(),
    val hotReloadResultSucceeded: Int = 0,
    val hotReloadResultFailed: Int = 0,
    val hotReloadResultUnsupported: Int = 0,
    val hotReloadResultDied: Int = 0,
    val resetInProgress: Boolean = false,
    val showResetDialog: Boolean = false,
    val resetDetails: List<PersistentResetDetail> = emptyList(),
    val resetResultSucceeded: Int = 0,
    val resetResultFailed: Int = 0,
    val resetResultUnsupported: Int = 0,
    val showDeleteOtaPackageDialog: Boolean = false,
    val deleteOtaPackageInProgress: Boolean = false,
    val deleteOtaPackageStatus: String? = null,
    val deleteOtaPackageMessage: String? = null,
    val fixNightModeOverrideInProgress: Boolean = false
)

/** DexKit index progress and result (settings-page manual refresh path). */
data class DexIndexRefreshUiState(
    val refreshing: Boolean = false,
    val progress: DexIndexProgress = DexIndexProgress(),
    val resultRes: Int? = null,
)
