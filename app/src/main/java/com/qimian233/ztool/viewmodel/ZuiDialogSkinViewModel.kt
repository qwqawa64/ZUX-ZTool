package com.qimian233.ztool.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.qimian233.ztool.data.keys.PreferenceKeys
import com.qimian233.ztool.data.zuiui.PendingScopeApp
import com.qimian233.ztool.data.zuiui.ScopeRequestOutcome
import com.qimian233.ztool.data.zuiui.ZuiComponentScanManager
import com.qimian233.ztool.data.zuiui.ZuiComponentScanRepository
import com.qimian233.ztool.data.zuiui.ZuiDialogSkinRepository
import com.qimian233.ztool.ui.components.normalizeArgbColorTextOrNull
import com.qimian233.ztool.ui.components.sanitizeArgbColorText
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

    fun setFollowSystemColors(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(followSystemColors = enabled)
        repository.saveFollowSystemColors(enabled)
    }

    /** Live slider feedback; [commitButtonRadius] writes only when the drag ends. */
    fun setButtonRadius(radiusDp: Int) {
        _uiState.value = _uiState.value.copy(
            buttonRadiusDp = radiusDp.coerceIn(
                ZuiDialogSkinRepository.RADIUS_MIN, ZuiDialogSkinRepository.RADIUS_MAX
            )
        )
    }

    fun commitButtonRadius() {
        repository.saveButtonRadius(_uiState.value.buttonRadiusDp)
    }

    fun setPositiveColorText(value: String) {
        _uiState.value = _uiState.value.copy(positiveColorText = value.sanitizeArgbColorText())
    }

    fun setNegativeColorText(value: String) {
        _uiState.value = _uiState.value.copy(negativeColorText = value.sanitizeArgbColorText())
    }

    fun commitPositiveColor() {
        val text = _uiState.value.positiveColorText
        val argb = parseOpaqueColor(text) ?: return
        repository.savePositiveColor(argb)
        _uiState.value = _uiState.value.copy(positiveColorText = argb.toArgbText())
    }

    fun commitNegativeColor() {
        val text = _uiState.value.negativeColorText
        val argb = parseOpaqueColor(text) ?: return
        repository.saveNegativeColor(argb)
        _uiState.value = _uiState.value.copy(negativeColorText = argb.toArgbText())
    }

    /** 8-digit hex after normalisation; alpha is forced opaque to match the hook. */
    private fun parseOpaqueColor(text: String): Int? {
        val normalized = text.normalizeArgbColorTextOrNull() ?: return null
        return normalized.toLong(16).toInt() or 0xFF000000.toInt()
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
    val hideDividerEnabled: Boolean = false,
    val followSystemColors: Boolean = PreferenceKeys.ZUI_DIALOG_FOLLOW_SYSTEM_COLORS.default,
    val buttonRadiusDp: Int = PreferenceKeys.ZUI_DIALOG_BUTTON_RADIUS.default,
    val positiveColorText: String = PreferenceKeys.ZUI_DIALOG_POSITIVE_COLOR.default.toArgbText(),
    val negativeColorText: String = PreferenceKeys.ZUI_DIALOG_NEGATIVE_COLOR.default.toArgbText()
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

private fun Int.toArgbText(): String = "%08X".format(this)
