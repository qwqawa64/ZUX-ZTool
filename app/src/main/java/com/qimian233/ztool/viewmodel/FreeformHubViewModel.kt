package com.qimian233.ztool.viewmodel

import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.qimian233.ztool.data.freeform.FreeformEdgeBubbleConfig
import com.qimian233.ztool.data.freeform.FreeformHubRepository
import com.qimian233.ztool.data.settings.SettingsDetailRepository
import com.qimian233.ztool.data.systemui.SystemUiMiscSettingsRepository
import com.qimian233.ztool.utils.EmbeddingConfigManager
import com.qimian233.ztool.utils.OvCommonConfigManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** State and actions behind the consolidated freeform hub screen. */
class FreeformHubViewModel(
    private val repository: FreeformHubRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(FreeformHubUiState())
    val uiState: StateFlow<FreeformHubUiState> = _uiState.asStateFlow()

    fun loadSettings() {
        viewModelScope.launch(Dispatchers.IO) {
            val state = repository.loadState()
            withContext(Dispatchers.Main) {
                _uiState.value = state
            }
        }
    }

    fun setRemoveBlacklist(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(removeBlacklist = enabled)
        repository.saveRemoveBlacklist(enabled)
    }

    fun setModuleEnabled(
        enabled: Boolean,
        onResult: (SettingsDetailModuleResult) -> Unit
    ) {
        if (enabled && repository.isModuleEnabled()) {
            _uiState.value = _uiState.value.copy(moduleEnabled = true)
            onResult(SettingsDetailModuleResult.AlreadyEnabled)
            return
        }

        _uiState.value = _uiState.value.copy(moduleEnabled = enabled)
        viewModelScope.launch(Dispatchers.IO) {
            val result = repository.setModuleEnabled(enabled)
            withContext(Dispatchers.Main) {
                if (result == SettingsDetailRepository.RESULT_SUCCESS) {
                    _uiState.value = _uiState.value.copy(moduleEnabled = enabled)
                    onResult(SettingsDetailModuleResult.Success(enabled))
                } else {
                    _uiState.value = _uiState.value.copy(moduleEnabled = !enabled)
                    onResult(SettingsDetailModuleResult.Failure(enabled, result))
                }
            }
        }
    }

    fun setEdgeBubbleEnabled(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(edgeBubbleEnabled = enabled)
        repository.saveEdgeBubbleEnabled(enabled)
    }

    fun setEdgeBubbleTriggerPercent(percent: Int) {
        val value = FreeformEdgeBubbleConfig.normalizeTriggerPercent(percent)
        _uiState.value = _uiState.value.copy(edgeBubbleTriggerPercent = value)
        repository.saveEdgeBubbleTriggerPercent(value)
    }

    fun setFloatMandatory(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(floatMandatory = enabled)
        repository.saveFloatMandatory(enabled)
    }

    fun setSplitScreenMandatory(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(splitScreenMandatory = enabled)
        repository.saveSplitScreenMandatory(enabled)
    }

    fun setFreeformFullscreenThreshold(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(freeformFullscreenThreshold = enabled)
        repository.saveFreeformFullscreenThreshold(enabled)
    }

    fun setFreeformFullscreenWidthPercent(percent: Int) {
        val value = SystemUiMiscSettingsRepository.normalizeFreeformFullscreenPercent(percent)
        _uiState.value = _uiState.value.copy(freeformFullscreenWidthPercent = value)
        repository.saveFreeformFullscreenWidthPercent(value)
    }

    fun setFreeformFullscreenHeightPercent(percent: Int) {
        val value = SystemUiMiscSettingsRepository.normalizeFreeformFullscreenPercent(percent)
        _uiState.value = _uiState.value.copy(freeformFullscreenHeightPercent = value)
        repository.saveFreeformFullscreenHeightPercent(value)
    }

    fun setFreeformBottomEdgeResize(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(freeformBottomEdgeResize = enabled)
        repository.saveFreeformBottomEdgeResize(enabled)
    }

    fun setCornerHandleAutoHide(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(cornerHandleAutoHide = enabled)
        repository.saveCornerHandleAutoHide(enabled)
    }

    fun setFreeformNoForehead(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(freeformNoForehead = enabled)
        repository.saveFreeformNoForehead(enabled)
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

    fun setForceRelativeAppFreeform(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(forceRelativeAppFreeform = enabled)
        repository.saveForceRelativeAppFreeform(enabled)
    }

    fun loadUserInstalledPackageNames(): List<String> =
        repository.loadUserInstalledPackageNames()

    fun loadFlashedConfigs(): HashSet<String> = repository.loadFlashedConfigs()

    fun loadEmbeddingConfigFiles(): List<EmbeddingConfigManager.ConfigFileInfo> =
        repository.loadEmbeddingConfigFiles()

    fun deleteEmbeddingConfigs(
        configs: List<EmbeddingConfigManager.ConfigFileInfo>,
        flashed: Set<String>
    ): Int = repository.deleteEmbeddingConfigs(configs, flashed)

    fun flashEmbeddingConfigs(
        configs: List<EmbeddingConfigManager.ConfigFileInfo>,
        onResult: (SettingsDetailConfigFlashResult) -> Unit
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                repository.flashEmbeddingConfigs(configs)
                withContext(Dispatchers.Main) {
                    onResult(SettingsDetailConfigFlashResult.Success)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    onResult(SettingsDetailConfigFlashResult.Failure(e.message.orEmpty()))
                }
            }
        }
    }

    fun restoreOriginalModule(onResult: (SettingsDetailRestoreResult) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val result = repository.restoreOriginalModule()
            withContext(Dispatchers.Main) {
                if (result == SettingsDetailRepository.RESULT_SUCCESS) {
                    _uiState.value = _uiState.value.copy(moduleEnabled = true)
                    onResult(SettingsDetailRestoreResult.Success)
                } else {
                    _uiState.value = _uiState.value.copy(moduleEnabled = repository.isModuleEnabled())
                    onResult(SettingsDetailRestoreResult.Failure(result))
                }
            }
        }
    }

    fun loadOvConfigSelection(
        mode: Int,
        onResult: (SettingsDetailOvConfigSelectionResult) -> Unit
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val result = repository.loadOvConfigSelection(mode)
                withContext(Dispatchers.Main) {
                    onResult(SettingsDetailOvConfigSelectionResult.Success(result))
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    onResult(SettingsDetailOvConfigSelectionResult.Failure(e.message.orEmpty()))
                }
            }
        }
    }

    fun saveOvConfig(
        configMap: MutableMap<String, OvCommonConfigManager.AppConfig>,
        selectedPackages: List<String>,
        mode: Int,
        onResult: (String) -> Unit
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val result = repository.saveOvConfig(configMap, selectedPackages, mode)
            withContext(Dispatchers.Main) {
                onResult(result)
            }
        }
    }

    fun showRestartDialog() {
        _uiState.value = _uiState.value.copy(showRestartDialog = true)
    }

    fun dismissRestartDialog() {
        _uiState.value = _uiState.value.copy(showRestartDialog = false)
    }

    fun restartSystem(onFailure: (String) -> Unit) {
        _uiState.value = _uiState.value.copy(showRestartDialog = false)
        viewModelScope.launch(Dispatchers.IO) {
            val result = repository.restartSystem()
            if (!result.isSuccess) {
                withContext(Dispatchers.Main) {
                    onFailure(result.error.ifBlank { result.output })
                }
            }
        }
    }
}

data class FreeformHubUiState(
    val removeBlacklist: Boolean = false,
    val moduleEnabled: Boolean = false,
    val showZuiForceConfig: Boolean = Build.VERSION.SDK_INT >= 36,
    val forceFreeformEntryHookEnabled: Boolean = false,
    val edgeBubbleEnabled: Boolean = false,
    val edgeBubbleTriggerPercent: Int = FreeformEdgeBubbleConfig.TRIGGER_PERCENT_DEFAULT,
    val floatMandatory: Boolean = false,
    val splitScreenMandatory: Boolean = false,
    val freeformFullscreenThreshold: Boolean = false,
    val freeformFullscreenWidthPercent: Int = 85,
    val freeformFullscreenHeightPercent: Int = 85,
    val freeformBottomEdgeResize: Boolean = false,
    val cornerHandleAutoHide: Boolean = true,
    val freeformNoForehead: Boolean = false,
    val forceFreeformEntry: Boolean = false,
    val freeformKeepAliveEnabled: Boolean = false,
    val freeformKeepAlivePackages: List<String> = emptyList(),
    val forceRelativeAppFreeform: Boolean = false,
    val showRestartDialog: Boolean = false
) {
    val freeformKeepAlivePackagesCount: Int
        get() = freeformKeepAlivePackages.size
}
