package com.qimian233.ztool.viewmodel

import android.os.Build
import android.net.Uri
import androidx.lifecycle.ViewModel
import com.qimian233.ztool.data.settings.OvConfigSelection
import com.qimian233.ztool.data.settings.SettingsDetailRepository
import com.qimian233.ztool.utils.EmbeddingConfigManager
import com.qimian233.ztool.utils.OvCommonConfigManager
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class SettingsDetailViewModel(
    private val repository: SettingsDetailRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(SettingsDetailUiState())
    val uiState: StateFlow<SettingsDetailUiState> = _uiState.asStateFlow()
    private var currentSelectedFontFile: File? = null

    fun loadSettings() {
        Thread {
            _uiState.value = repository.loadState()
        }.start()
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
        Thread {
            val result = repository.setModuleEnabled(enabled)
            if (result == SettingsDetailRepository.RESULT_SUCCESS) {
                _uiState.value = _uiState.value.copy(moduleEnabled = enabled)
                onResult(SettingsDetailModuleResult.Success(enabled))
            } else {
                _uiState.value = _uiState.value.copy(moduleEnabled = !enabled)
                onResult(SettingsDetailModuleResult.Failure(enabled, result))
            }
        }.start()
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

    fun setAlwaysDisplaySuggestions(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(alwaysDisplaySuggestions = enabled)
        repository.saveAlwaysDisplaySuggestions(enabled)
    }

    fun showRestartDialog() {
        _uiState.value = _uiState.value.copy(showRestartDialog = true)
    }

    fun dismissRestartDialog() {
        _uiState.value = _uiState.value.copy(showRestartDialog = false)
    }

    fun restartScope(packageName: String) {
        _uiState.value = _uiState.value.copy(showRestartDialog = false)
        Thread {
            repository.forceStopScope(packageName)
        }.start()
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
        Thread {
            try {
                repository.flashEmbeddingConfigs(configs)
                onResult(SettingsDetailConfigFlashResult.Success)
            } catch (e: Exception) {
                onResult(SettingsDetailConfigFlashResult.Failure(e.message.orEmpty()))
            }
        }.start()
    }

    fun restoreOriginalModule(onResult: (SettingsDetailRestoreResult) -> Unit) {
        Thread {
            val result = repository.restoreOriginalModule()
            if (result == SettingsDetailRepository.RESULT_SUCCESS) {
                _uiState.value = _uiState.value.copy(moduleEnabled = true)
                onResult(SettingsDetailRestoreResult.Success)
            } else {
                _uiState.value = _uiState.value.copy(moduleEnabled = repository.isModuleEnabled())
                onResult(SettingsDetailRestoreResult.Failure(result))
            }
        }.start()
    }

    fun prepareFontImport(
        uri: Uri,
        onResult: (SettingsDetailFontPreparationResult) -> Unit
    ) {
        Thread {
            try {
                val preparation = repository.prepareFontImport(uri)
                currentSelectedFontFile = preparation.file
                onResult(SettingsDetailFontPreparationResult.Success(preparation.originalFileName))
            } catch (e: Exception) {
                onResult(SettingsDetailFontPreparationResult.Failure(e.message.orEmpty()))
            }
        }.start()
    }

    fun installFont(
        fontName: String,
        fontDescription: String,
        onResult: (SettingsDetailFontInstallResult) -> Unit
    ) {
        Thread {
            try {
                repository.installFont(
                    fontFile = currentSelectedFontFile,
                    fontName = fontName,
                    fontDescription = fontDescription
                )
                onResult(SettingsDetailFontInstallResult.Success)
            } catch (e: Exception) {
                onResult(SettingsDetailFontInstallResult.Failure(e.message.orEmpty()))
            }
        }.start()
    }

    fun loadOvConfigSelection(
        mode: Int,
        onResult: (SettingsDetailOvConfigSelectionResult) -> Unit
    ) {
        Thread {
            try {
                onResult(SettingsDetailOvConfigSelectionResult.Success(repository.loadOvConfigSelection(mode)))
            } catch (e: Exception) {
                onResult(SettingsDetailOvConfigSelectionResult.Failure(e.message.orEmpty()))
            }
        }.start()
    }

    fun saveOvConfig(
        configMap: MutableMap<String, OvCommonConfigManager.AppConfig>,
        selectedPackages: List<String>,
        mode: Int,
        onResult: (String) -> Unit
    ) {
        Thread {
            onResult(repository.saveOvConfig(configMap, selectedPackages, mode))
        }.start()
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
    val alwaysDisplaySuggestions: Boolean = false,
    val showZuiForceConfig: Boolean = Build.VERSION.SDK_INT >= 36,
    val showRestartDialog: Boolean = false
)
