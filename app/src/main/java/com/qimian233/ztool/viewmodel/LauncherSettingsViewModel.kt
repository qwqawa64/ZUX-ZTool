package com.qimian233.ztool.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.qimian233.ztool.data.keys.PreferenceKeys
import com.qimian233.ztool.data.launcher.ExternalGridInputs
import com.qimian233.ztool.data.launcher.GridCounts
import com.qimian233.ztool.data.launcher.LauncherGridResolver
import com.qimian233.ztool.data.launcher.LauncherPreviewRepository
import com.qimian233.ztool.data.launcher.LauncherRestartResult
import com.qimian233.ztool.data.launcher.LauncherSettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class LauncherSettingsViewModel(
    private val repository: LauncherSettingsRepository,
    private val previewRepository: LauncherPreviewRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(LauncherSettingsUiState())
    val uiState: StateFlow<LauncherSettingsUiState> = _uiState.asStateFlow()

    private var externalGridInputs = ExternalGridInputs(
        systemLayoutName = null,
        providerLayouts = emptyList()
    )

    fun loadSettings() {
        try {
            _uiState.value = repository.loadState()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load launcher settings", e)
        }
        refreshExternalGridInputs()
    }

    /** Reads the launcher-side layout facts, then re-resolves the preview grid. */
    private fun refreshExternalGridInputs() {
        viewModelScope.launch(Dispatchers.IO) {
            val inputs = try {
                previewRepository.readExternalInputs()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to read launcher grid inputs", e)
                ExternalGridInputs(systemLayoutName = null, providerLayouts = emptyList())
            }
            withContext(Dispatchers.Main) {
                externalGridInputs = inputs
                applyPreviewGrid()
            }
        }
    }

    /** Cheap and main-thread safe: the resolver is pure and the external inputs are cached. */
    private fun applyPreviewGrid() {
        val state = _uiState.value
        val resolved = LauncherGridResolver.resolve(
            customGridEnabled = state.customGridSize,
            customColumns = state.customGridColumn,
            customRows = state.customGridRow,
            external = externalGridInputs
        )
        Log.d(TAG, "preview grid ${resolved.counts} from ${resolved.source}")
        _uiState.value = state.copy(previewGrid = resolved.counts)
    }

    fun setForceStopMode(mode: ForceStopMode) {
        _uiState.value = _uiState.value.copy(forceStopMode = mode)
        repository.saveForceStopMode(mode)
    }

    fun setForceStopWhitelist(packageNames: List<String>) {
        packageNames.forEach {
            Log.d(TAG, "Selected protected app package: $it")
        }
        _uiState.value = _uiState.value.copy(forceStopWhitelist = packageNames)
        repository.saveForceStopWhitelist(packageNames)
    }

    fun loadUserInstalledPackageNames(): List<String> {
        return repository.loadUserInstalledPackageNames()
    }

    fun setMoreBigDock(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(moreBigDock = enabled)
        repository.saveMoreBigDock(enabled)
    }

    fun setForceFreeformEntry(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(forceFreeformEntry = enabled)
        repository.saveForceFreeformEntry(enabled)
    }

    fun setFreeformKeepAliveEnabled(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(freeformKeepAliveEnabled = enabled)
        repository.saveFreeformKeepAliveEnabled(enabled)
    }

    fun setFreeformKeepAlivePackages(packageNames: List<String>) {
        _uiState.value = _uiState.value.copy(freeformKeepAlivePackages = packageNames)
        repository.saveFreeformKeepAlivePackages(packageNames)
    }

    fun setCustomGridSize(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(customGridSize = enabled)
        repository.saveCustomGridSize(enabled)
        applyPreviewGrid()
    }

    fun setCustomGridRow(value: Int) {
        val current = _uiState.value
        val row = value.coerceIn(LauncherSettingsRepository.GRID_MIN, LauncherSettingsRepository.GRID_MAX)
        _uiState.value = current.copy(customGridRow = row)
        repository.saveGridValues(row, current.customGridColumn)
        applyPreviewGrid()
    }

    fun setCustomGridColumn(value: Int) {
        val current = _uiState.value
        val column = value.coerceIn(LauncherSettingsRepository.GRID_MIN, LauncherSettingsRepository.GRID_MAX)
        _uiState.value = current.copy(customGridColumn = column)
        repository.saveGridValues(current.customGridRow, column)
        applyPreviewGrid()
    }

    fun setCleanSearch(value: Boolean) {
        _uiState.value = _uiState.value.copy(cleanGlobalSearch = value)
        repository.saveCleanSearch(value)
    }

    fun setRemoveSearchRecommend(value: Boolean) {
        _uiState.value = _uiState.value.copy(removeSearchRecommend = value)
        repository.saveRemoveSearchRecommend(value)
    }

    fun setRemoveHotWordView(value: Boolean) {
        _uiState.value = _uiState.value.copy(removeHotWordView = value)
        repository.saveRemoveHotWordView(value)
    }

    fun setShowRamInfo(value: Boolean) {
        _uiState.value = _uiState.value.copy(showRamInfo = value)
        repository.saveShowRamInfo(value)
    }

    fun setBeautifyRamInfo(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(beautifyRamInfo = enabled)
        repository.saveBeautifyRamInfo(enabled)
    }

    fun setLauncherNoLabelMode(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(noLabelMode = enabled)
        repository.saveLauncherNoLabelMode(enabled)
    }

    fun setLauncherDrawerNoLabelMode(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(drawerNoLabelMode = enabled)
        repository.saveLauncherDrawerNoLabelMode(enabled)
    }

    fun setCloudFolderAutoDismiss(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(cloudFolderDismiss = enabled)
        repository.saveCloudFolderAutoDismiss(enabled)
    }

    fun setLauncherHideBluePoint(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(hideBluePoint = enabled)
        repository.saveLauncherHideBluePoint(enabled)
    }

    fun setDisableRecentAppDisplay(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(disableRecentAppDisplay = enabled)
        repository.saveDisableRecentAppDisplay(enabled)
    }

    fun setLauncherBatchUninstall(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(launcherBatchUninstall = enabled)
        repository.saveLauncherBatchUninstall(enabled)
    }

    fun setBigFolderAlign(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(bigFolderAlign = enabled)
        repository.saveBigFolderAlign(enabled)
    }

    fun setAppIconUnmask(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(appIconUnmask = enabled)
        repository.saveAppIconUnmask(enabled)
    }

    fun setAppIconUnmaskDynamic(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(appIconUnmaskDynamic = enabled)
        repository.saveAppIconUnmaskDynamic(enabled)
    }

    fun setWideGrid(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(wideGrid = enabled)
        repository.saveWideGrid(enabled)
    }

    fun setWideGridSquare(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(wideGridSquare = enabled)
        repository.saveWideGridSquare(enabled)
    }

    fun setWideGridSideInset(inset: Int) {
        _uiState.value = _uiState.value.copy(wideGridSideInset = inset)
        repository.saveWideGridSideInset(inset)
    }

    /** Writes the ColorOS-like side inset for this screen; takes effect after a restart. */
    fun applyColorOsSideInsetPreset() {
        setWideGridSideInset(repository.paritySideInsetDp())
    }

    fun setBigFolderBlurGuard(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(bigFolderBlurGuard = enabled)
        repository.saveBigFolderBlurGuard(enabled)
    }

    /** Stores one big-folder hand tuning value after the repository snapped it. */
    fun setBigFolderTune(key: BigFolderTuneKey, value: Int) {
        val snapped = repository.saveBigFolderTune(key.preferenceName, value)
        _uiState.value = when (key) {
            BigFolderTuneKey.GAP_H -> _uiState.value.copy(bigFolderTuneGapH = snapped)
            BigFolderTuneKey.GAP_V -> _uiState.value.copy(bigFolderTuneGapV = snapped)
            BigFolderTuneKey.BG_X -> _uiState.value.copy(bigFolderTuneBgX = snapped)
            BigFolderTuneKey.BG_Y -> _uiState.value.copy(bigFolderTuneBgY = snapped)
            BigFolderTuneKey.SHIFT_X -> _uiState.value.copy(bigFolderTuneShiftX = snapped)
            BigFolderTuneKey.SHIFT_Y -> _uiState.value.copy(bigFolderTuneShiftY = snapped)
        }
    }

    fun setIconScaleOverride(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(iconScaleOverride = enabled)
        repository.saveIconScaleOverride(enabled)
    }

    fun setIconScaleValue(scale: Float) {
        _uiState.value = _uiState.value.copy(iconScaleValue = scale)
        repository.saveIconScaleValue(scale)
    }

    fun setDisableDockBar(enabled: Boolean) {
        val showWarning = repository.saveDisableDockBar(enabled)
        val current = _uiState.value
        _uiState.value = if (enabled) {
            current.copy(
                disableDockBar = true,
                moreBigDock = false,
                showDisableDockWarningDialog = showWarning
            )
        } else {
            current.copy(
                disableDockBar = false,
                moreBigDock = current.moreBigDock,
                showDisableDockWarningDialog = false
            )
        }
    }

    fun dismissDisableDockWarningDialog() {
        _uiState.value = _uiState.value.copy(showDisableDockWarningDialog = false)
    }

    fun confirmDisableDockWarning() {
        repository.saveDisableDockWarningConfirmed()
        _uiState.value = _uiState.value.copy(showDisableDockWarningDialog = false)
    }

    fun showRestartConfirmDialog() {
        _uiState.value = _uiState.value.copy(showRestartConfirmDialog = true)
    }

    fun dismissRestartConfirmDialog() {
        _uiState.value = _uiState.value.copy(showRestartConfirmDialog = false)
    }

    fun forceStopPackage(onResult: (LauncherRestartResult) -> Unit) {
        _uiState.value = _uiState.value.copy(showRestartConfirmDialog = false)
        viewModelScope.launch(Dispatchers.IO) {
            val result = repository.forceStopPackage()
            withContext(Dispatchers.Main) {
                onResult(result)
            }
        }
    }

    companion object {
        private const val TAG = "LauncherSettings"
    }
}

enum class ForceStopMode {
    Default,
    AllApps,
    Whitelist
}

/** Big-folder hand tuning sliders; each value maps to its own preference key. */
enum class BigFolderTuneKey(val preferenceName: String) {
    GAP_H(PreferenceKeys.LAUNCHER_BIG_FOLDER_TUNE_GAP_H.name),
    GAP_V(PreferenceKeys.LAUNCHER_BIG_FOLDER_TUNE_GAP_V.name),
    BG_X(PreferenceKeys.LAUNCHER_BIG_FOLDER_TUNE_BG_X.name),
    BG_Y(PreferenceKeys.LAUNCHER_BIG_FOLDER_TUNE_BG_Y.name),
    SHIFT_X(PreferenceKeys.LAUNCHER_BIG_FOLDER_TUNE_SHIFT_X.name),
    SHIFT_Y(PreferenceKeys.LAUNCHER_BIG_FOLDER_TUNE_SHIFT_Y.name)
}

data class LauncherSettingsUiState(
    val forceStopMode: ForceStopMode = ForceStopMode.Default,
    val forceStopWhitelist: List<String> = emptyList(),
    val moreBigDock: Boolean = false,
    val forceFreeformEntry: Boolean = false,
    val freeformKeepAliveEnabled: Boolean = false,
    val freeformKeepAlivePackages: List<String> = emptyList(),
    val customGridSize: Boolean = false,
    val customGridRow: Int = 4,
    val customGridColumn: Int = 6,
    val cleanGlobalSearch: Boolean = false,
    val removeHotWordView: Boolean = false,
    val removeSearchRecommend: Boolean = false,
    val showRestartConfirmDialog: Boolean = false,
    val showRamInfo : Boolean = false,
    val beautifyRamInfo : Boolean = false,
    val disableDockBar: Boolean = false,
    val showDisableDockWarningDialog: Boolean = false,
    val noLabelMode: Boolean = false,
    val drawerNoLabelMode: Boolean = false,
    val hideBluePoint: Boolean = false,
    val cloudFolderDismiss: Boolean = false,
    val disableRecentAppDisplay: Boolean = false,
    val launcherBatchUninstall: Boolean = false,
    val bigFolderAlign: Boolean = false,
    /** Host big-folder blur crash guard; on by default (key launcher_big_folder_blur_guard). */
    val bigFolderBlurGuard: Boolean = true,
    /** Big-folder geometry hand tuning in launcher-local px; see docs/research/big_folder_tuning.md. */
    val bigFolderTuneGapH: Int = 0,
    val bigFolderTuneGapV: Int = 0,
    val bigFolderTuneBgX: Int = 0,
    val bigFolderTuneBgY: Int = 0,
    val bigFolderTuneShiftX: Int = 0,
    val bigFolderTuneShiftY: Int = 0,
    val appIconUnmask: Boolean = false,
    val appIconUnmaskDynamic: Boolean = false,
    val wideGrid: Boolean = false,
    val wideGridSquare: Boolean = false,
    val wideGridSideInset: Int = 0,
    val iconScaleOverride: Boolean = false,
    val iconScaleValue: Float = 1.0f,
    /** Fallback until the launcher-side layout facts are read; see [LauncherGridResolver]. */
    val previewGrid: GridCounts = LauncherGridResolver.FALLBACK,
) {
    val forceStopWhitelistCount: Int
        get() = forceStopWhitelist.size

    val freeformKeepAlivePackagesCount: Int
        get() = freeformKeepAlivePackages.size

    /** Rendering model of the desktop preview, derived from the fields above. */
    val previewConfig: LauncherPreviewConfig
        get() = LauncherPreviewConfig(
            columns = previewGrid.columns,
            rows = previewGrid.rows,
            wideGrid = wideGrid,
            wideGridSideInset = wideGridSideInset,
            squareCells = wideGrid && wideGridSquare,
            // The icon-scale hook only applies while the override switch is on.
            iconScale = if (iconScaleOverride) iconScaleValue else 1f,
            noLabel = noLabelMode,
            bluePointVisible = !hideBluePoint,
            dockVisible = !disableDockBar,
            // Dock expansion forces 20 hotseat columns; 8 slots is the on-device dock.
            dockIconCount = if (moreBigDock) 8 else 5,
            bigFolderHooked = bigFolderAlign,
            // The tuning only reaches the launcher while the alignment hook is on.
            bigFolderTune = if (bigFolderAlign) {
                BigFolderTune(
                    gapH = bigFolderTuneGapH,
                    gapV = bigFolderTuneGapV,
                    bgX = bigFolderTuneBgX,
                    bgY = bigFolderTuneBgY,
                    shiftX = bigFolderTuneShiftX,
                    shiftY = bigFolderTuneShiftY
                )
            } else {
                BigFolderTune(0, 0, 0, 0, 0, 0)
            }
        )
}

/** Everything the desktop preview draws; geometry only, no launcher-side pixel values. */
data class LauncherPreviewConfig(
    val columns: Int,
    val rows: Int,
    val wideGrid: Boolean,
    val wideGridSideInset: Int,
    val squareCells: Boolean,
    val iconScale: Float,
    val noLabel: Boolean,
    val bluePointVisible: Boolean,
    val dockVisible: Boolean,
    val dockIconCount: Int,
    val bigFolderTune: BigFolderTune,
    /** True while the big-folder alignment hook is on, which rewrites the child grid. */
    val bigFolderHooked: Boolean,
)

/** The six big-folder hand tuning offsets, in launcher-local px. */
data class BigFolderTune(
    val gapH: Int,
    val gapV: Int,
    val bgX: Int,
    val bgY: Int,
    val shiftX: Int,
    val shiftY: Int,
)
