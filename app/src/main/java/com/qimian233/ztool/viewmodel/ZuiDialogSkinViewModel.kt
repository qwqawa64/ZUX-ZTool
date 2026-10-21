package com.qimian233.ztool.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.qimian233.ztool.data.zuiui.PendingScopeApp
import com.qimian233.ztool.data.zuiui.ScopeRequestOutcome
import com.qimian233.ztool.data.zuiui.ZuiComponentScanManager
import com.qimian233.ztool.data.zuiui.ZuiComponentScanRepository
import com.qimian233.ztool.data.zuiui.ZuiDialogSkinRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ZuiDialogSkinViewModel(
    private val repository: ZuiDialogSkinRepository,
    private val scanRepository: ZuiComponentScanRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ZuiDialogSkinUiState())
    val uiState: StateFlow<ZuiDialogSkinUiState> = _uiState.asStateFlow()

    private val _scanState = MutableStateFlow(ZuiComponentScanUiState())
    val scanState: StateFlow<ZuiComponentScanUiState> = _scanState.asStateFlow()

    private var lastManualRuns = 0
    private var cachedPendingFor: List<String>? = null

    init {
        // The manager is a singleton, so a scan started before this page existed still reports here.
        viewModelScope.launch {
            ZuiComponentScanManager.state.collect { manager ->
                val manualFinished = manager.completedManualRuns > lastManualRuns
                lastManualRuns = manager.completedManualRuns
                val pending = pendingFor(manager.zuiPackages)
                _scanState.value = ZuiComponentScanUiState(
                    running = manager.running,
                    scanned = manager.scanned,
                    total = manager.total,
                    hasScannedOnce = manager.hasScannedOnce,
                    scannedApps = manager.scannedApps,
                    zuiApps = manager.zuiPackages.size,
                    pending = pending,
                    showRecommendationDialog =
                        _scanState.value.showRecommendationDialog || manualFinished
                )
            }
        }
    }

    fun loadSettings() {
        _uiState.value = repository.loadState()
    }

    fun setDialogSkinEnabled(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(dialogSkinEnabled = enabled)
        repository.saveDialogSkinEnabled(enabled)
    }

    fun setHideDividerEnabled(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(hideDividerEnabled = enabled)
        repository.saveHideDividerEnabled(enabled)
    }

    fun applyChanges(onUnsupported: () -> Unit, onResult: (HotReloadOutcome) -> Unit) {
        if (!repository.supportsHotReload()) {
            onUnsupported()
            return
        }
        repository.applyByHotReload { succeeded, failed ->
            onResult(HotReloadOutcome(succeeded, failed))
        }
    }

    /** Incremental refresh on page entry; the manager ignores it until the user has scanned once. */
    fun onEnterPage() {
        scanRepository.refreshStale()
    }

    fun startScan() = scanRepository.startScan()

    fun cancelScan() = scanRepository.cancelScan()

    fun openRecommendations() {
        _scanState.value = _scanState.value.copy(showRecommendationDialog = true)
    }

    fun dismissRecommendations() {
        _scanState.value = _scanState.value.copy(showRecommendationDialog = false)
    }

    fun applyRecommendedScope(onResult: (ScopeRequestOutcome) -> Unit) {
        val packages = _scanState.value.pending.map { it.packageName }
        if (packages.isEmpty()) return
        scanRepository.requestScope(packages) { outcome ->
            if (outcome is ScopeRequestOutcome.Approved) refreshPending()
            onResult(outcome)
        }
    }

    private fun pendingFor(zuiPackages: List<String>): List<PendingScopeApp> {
        if (zuiPackages == cachedPendingFor) return _scanState.value.pending
        cachedPendingFor = zuiPackages
        return scanRepository.pendingApps(zuiPackages)
    }

    private fun refreshPending() {
        val zuiPackages = ZuiComponentScanManager.state.value.zuiPackages
        cachedPendingFor = zuiPackages
        _scanState.value = _scanState.value.copy(
            pending = scanRepository.pendingApps(zuiPackages)
        )
    }
}

data class ZuiDialogSkinUiState(
    val dialogSkinEnabled: Boolean = false,
    val hideDividerEnabled: Boolean = false
)

/** Targets the module managed to reinstall itself into, and those it did not. */
data class HotReloadOutcome(
    val succeeded: Int,
    val failed: Int
)

/** Scan progress plus the apps it recommends adding to the module scope. */
data class ZuiComponentScanUiState(
    val running: Boolean = false,
    val scanned: Int = 0,
    val total: Int = 0,
    val hasScannedOnce: Boolean = false,
    val scannedApps: Int = 0,
    val zuiApps: Int = 0,
    val pending: List<PendingScopeApp> = emptyList(),
    val showRecommendationDialog: Boolean = false
)
