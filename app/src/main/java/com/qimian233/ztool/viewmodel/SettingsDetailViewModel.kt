package com.qimian233.ztool.viewmodel

import android.os.Build
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.qimian233.ztool.data.settings.CustomizeAboutDeviceInfoRepository
import com.qimian233.ztool.data.settings.OvConfigSelection
import com.qimian233.ztool.data.settings.SettingsDetailRepository
import com.qimian233.ztool.utils.EmbeddingConfigManager
import com.qimian233.ztool.utils.OvCommonConfigManager
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SettingsDetailViewModel(
    private val repository: SettingsDetailRepository,
    private val aboutDeviceInfoRepository: CustomizeAboutDeviceInfoRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(SettingsDetailUiState())
    val uiState: StateFlow<SettingsDetailUiState> = _uiState.asStateFlow()
    private var currentSelectedFontFile: File? = null

    fun loadSettings() {
        viewModelScope.launch(Dispatchers.IO) {
            val state = repository.loadState()
            withContext(Dispatchers.Main) {
                _uiState.value = state.copy(
                    aboutDeviceInfoState = aboutDeviceInfoRepository.loadState()
                )
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

    fun setFloatMandatory(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(floatMandatory = enabled)
        repository.saveForceResizableActivities(enabled)
    }

    fun setSplitScreenMandatory(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(splitScreenMandatory = enabled)
        repository.saveSplitScreenMandatory(enabled)
    }

    fun setAllowNativePermissionController(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(allowNativePermissionController = enabled)
        repository.saveAllowNativePermissionController(enabled)
    }

    fun setAllowDisableDolby(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(allowDisableDolby = enabled)
        repository.saveAllowDisableDolby(enabled)
    }

    fun setAppDetails(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(appDetail = enabled)
        repository.saveAppDetails(enabled)
    }

    fun setAboutDeviceInfoEnabled(enabled: Boolean) {
        aboutDeviceInfoRepository.setEnabled(enabled)
        _uiState.value = _uiState.value.copy(
            aboutDeviceInfoState = _uiState.value.aboutDeviceInfoState.copy(enabled = enabled)
        )
    }

    fun setAboutDeviceInfoModelEnabled(enabled: Boolean) {
        aboutDeviceInfoRepository.setModelEnabled(enabled)
        _uiState.value = _uiState.value.copy(
            aboutDeviceInfoState = _uiState.value.aboutDeviceInfoState.copy(modelEnabled = enabled)
        )
    }

    fun setAboutDeviceInfoCpuEnabled(enabled: Boolean) {
        aboutDeviceInfoRepository.setCpuEnabled(enabled)
        _uiState.value = _uiState.value.copy(
            aboutDeviceInfoState = _uiState.value.aboutDeviceInfoState.copy(cpuEnabled = enabled)
        )
    }

    fun setAboutDeviceInfoRamEnabled(enabled: Boolean) {
        aboutDeviceInfoRepository.setRamEnabled(enabled)
        _uiState.value = _uiState.value.copy(
            aboutDeviceInfoState = _uiState.value.aboutDeviceInfoState.copy(ramEnabled = enabled)
        )
    }

    fun setAboutDeviceInfoRomEnabled(enabled: Boolean) {
        aboutDeviceInfoRepository.setRomEnabled(enabled)
        _uiState.value = _uiState.value.copy(
            aboutDeviceInfoState = _uiState.value.aboutDeviceInfoState.copy(romEnabled = enabled)
        )
    }

    fun setAboutDeviceInfoSoftwareEnabled(enabled: Boolean) {
        aboutDeviceInfoRepository.setSoftwareEnabled(enabled)
        _uiState.value = _uiState.value.copy(
            aboutDeviceInfoState = _uiState.value.aboutDeviceInfoState.copy(softwareEnabled = enabled)
        )
    }

    fun setAboutDeviceInfoHeaderEnabled(enabled: Boolean) {
        aboutDeviceInfoRepository.setHeaderEnabled(enabled)
        _uiState.value = _uiState.value.copy(
            aboutDeviceInfoState = _uiState.value.aboutDeviceInfoState.copy(headerEnabled = enabled)
        )
    }

    fun setAboutDeviceInfoModel(value: String) {
        aboutDeviceInfoRepository.setModel(value)
        _uiState.value = _uiState.value.copy(
            aboutDeviceInfoState = _uiState.value.aboutDeviceInfoState.copy(model = value)
        )
    }

    fun setAboutDeviceInfoCpu(value: String) {
        aboutDeviceInfoRepository.setCpu(value)
        _uiState.value = _uiState.value.copy(
            aboutDeviceInfoState = _uiState.value.aboutDeviceInfoState.copy(cpu = value)
        )
    }

    fun setAboutDeviceInfoRam(value: String) {
        aboutDeviceInfoRepository.setRam(value)
        _uiState.value = _uiState.value.copy(
            aboutDeviceInfoState = _uiState.value.aboutDeviceInfoState.copy(ram = value)
        )
    }

    fun setAboutDeviceInfoRom(value: String) {
        aboutDeviceInfoRepository.setRom(value)
        _uiState.value = _uiState.value.copy(
            aboutDeviceInfoState = _uiState.value.aboutDeviceInfoState.copy(rom = value)
        )
    }

    fun setAboutDeviceInfoSoftware(value: String) {
        aboutDeviceInfoRepository.setSoftware(value)
        _uiState.value = _uiState.value.copy(
            aboutDeviceInfoState = _uiState.value.aboutDeviceInfoState.copy(software = value)
        )
    }

    fun saveAboutDeviceInfoHeaderImage(uri: Uri): Boolean {
        return aboutDeviceInfoRepository.saveDeviceHeaderImage(repository.context, uri)
    }

    fun showRestartDialog() {
        _uiState.value = _uiState.value.copy(showRestartDialog = true)
    }

    fun dismissRestartDialog() {
        _uiState.value = _uiState.value.copy(showRestartDialog = false)
    }

    fun restartScope() {
        _uiState.value = _uiState.value.copy(showRestartDialog = false)
        viewModelScope.launch(Dispatchers.IO) {
            repository.forceStopScope()
        }
    }

    fun loadFlashedConfigs(): HashSet<String> = repository.loadFlashedConfigs()

    fun loadEmbeddingConfigFiles(): List<EmbeddingConfigManager.ConfigFileInfo> {
        return repository.loadEmbeddingConfigFiles()
    }

    fun deleteEmbeddingConfigs(
        configs: List<EmbeddingConfigManager.ConfigFileInfo>,
        flashed: Set<String>
    ): Int {
        return repository.deleteEmbeddingConfigs(configs, flashed)
    }

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

    fun prepareFontImport(
        uri: Uri,
        onResult: (SettingsDetailFontPreparationResult) -> Unit
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val preparation = repository.prepareFontImport(uri)
                currentSelectedFontFile = preparation.file
                withContext(Dispatchers.Main) {
                    onResult(SettingsDetailFontPreparationResult.Success(preparation.originalFileName))
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    onResult(SettingsDetailFontPreparationResult.Failure(e.message.orEmpty()))
                }
            }
        }
    }

    fun installFont(
        fontName: String,
        fontDescription: String,
        onResult: (SettingsDetailFontInstallResult) -> Unit
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                repository.installFont(
                    fontFile = currentSelectedFontFile,
                    fontName = fontName,
                    fontDescription = fontDescription
                )
                withContext(Dispatchers.Main) {
                    onResult(SettingsDetailFontInstallResult.Success)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    onResult(SettingsDetailFontInstallResult.Failure(e.message.orEmpty()))
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
}

sealed interface SettingsDetailModuleResult {
    data object AlreadyEnabled : SettingsDetailModuleResult
    data class Success(val enabled: Boolean) : SettingsDetailModuleResult
    data class Failure(val requestedEnabled: Boolean, val message: String) : SettingsDetailModuleResult
}

sealed interface SettingsDetailRestoreResult {
    data object Success : SettingsDetailRestoreResult
    data class Failure(val message: String) : SettingsDetailRestoreResult
}

sealed interface SettingsDetailConfigFlashResult {
    data object Success : SettingsDetailConfigFlashResult
    data class Failure(val message: String) : SettingsDetailConfigFlashResult
}

sealed interface SettingsDetailFontPreparationResult {
    data class Success(val originalFileName: String?) : SettingsDetailFontPreparationResult
    data class Failure(val message: String) : SettingsDetailFontPreparationResult
}

sealed interface SettingsDetailFontInstallResult {
    data object Success : SettingsDetailFontInstallResult
    data class Failure(val message: String) : SettingsDetailFontInstallResult
}

sealed interface SettingsDetailOvConfigSelectionResult {
    data class Success(val selection: OvConfigSelection) : SettingsDetailOvConfigSelectionResult
    data class Failure(val message: String) : SettingsDetailOvConfigSelectionResult
}

data class SettingsDetailUiState(
    val removeBlacklist: Boolean = false,
    val moduleEnabled: Boolean = false,
    val floatMandatory: Boolean = false,
    val splitScreenMandatory: Boolean = false,
    val allowDisableDolby: Boolean = false,
    val allowNativePermissionController: Boolean = false,
    val showZuiForceConfig: Boolean = Build.VERSION.SDK_INT >= 36,
    val showRestartDialog: Boolean = false,
    val appDetail: Boolean = false,
    val allowAddingLanguages: Boolean = false,
    val aboutDeviceInfoState: com.qimian233.ztool.data.settings.CustomizeAboutDeviceInfoState = com.qimian233.ztool.data.settings.CustomizeAboutDeviceInfoState(),
)
