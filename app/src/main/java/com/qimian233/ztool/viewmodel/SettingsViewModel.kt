package com.qimian233.ztool.viewmodel

import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.qimian233.ztool.data.settings.SettingsRepository
import com.qimian233.ztool.ui.theme.FrontendStyle
import com.qimian233.ztool.ui.theme.MaterialColorSpec
import com.qimian233.ztool.ui.theme.MaterialPalette
import com.qimian233.ztool.ui.theme.ThemeMode
import com.qimian233.ztool.ui.theme.ZToolThemeSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SettingsViewModel(
    private val repository: SettingsRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(repository.loadState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    fun refresh() {
        _uiState.value = repository.loadState().copy(
            showRestoreConfirmDialog = _uiState.value.showRestoreConfirmDialog
        )
    }

    fun backupFileName(): String = repository.backupFileName()

    fun backupConfig(uri: Uri, onResult: (Boolean) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val result = runCatching { repository.backupConfig(uri) }
                .onFailure { Log.e(TAG, "Config backup failed", it) }
                .getOrDefault(false)
            withContext(Dispatchers.Main) {
                onResult(result)
            }
        }
    }

    fun restoreConfig(uri: Uri, onResult: (Boolean) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val result = runCatching { repository.restoreConfig(uri) }
                .onFailure { Log.e(TAG, "Config restore failed", it) }
                .getOrDefault(false)
            withContext(Dispatchers.Main) {
                refresh()
                onResult(result)
            }
        }
    }

    fun restoreDefaultConfig() {
        repository.restoreDefaultConfig()
        _uiState.value = repository.loadState().copy(showRestoreConfirmDialog = false)
    }

    fun setDetailedLoggingEnabled(isEnabled: Boolean) {
        _uiState.value = _uiState.value.copy(isDetailedLoggingEnabled = isEnabled)
        repository.setDetailedLoggingEnabled(isEnabled)
    }

    fun setDisplayEntryInSettings(isEnabled: Boolean) {
        _uiState.value = _uiState.value.copy(isEntryDisplayedInSettings = isEnabled)
        repository.setEntryInSettingsEnabled(isEnabled)
    }

    fun setHomepageYiyanEnabled(isEnabled: Boolean) {
        _uiState.value = _uiState.value.copy(isHomepageYiyanEnabled = isEnabled)
        repository.setHomepageYiyanEnabled(isEnabled)
    }

    fun setLsposedServiceProtector(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(lsposedServiceProtector = enabled)
        repository.saveLsposedServiceProtector(enabled)
    }

    fun setFrontendStyle(style: FrontendStyle) {
        repository.setFrontendStyle(style)
        _uiState.value = _uiState.value.copy(
            themeSettings = _uiState.value.themeSettings.copy(frontendStyle = style)
        )
    }

    fun setThemeMode(mode: ThemeMode) {
        repository.setThemeMode(mode)
        _uiState.value = _uiState.value.copy(
            themeSettings = _uiState.value.themeSettings.copy(themeMode = mode)
        )
    }

    fun setMaterialColorSpec(spec: MaterialColorSpec) {
        repository.setMaterialColorSpec(spec)
        _uiState.value = _uiState.value.copy(
            themeSettings = _uiState.value.themeSettings.copy(materialColorSpec = spec)
        )
    }

    fun setMaterialPalette(palette: MaterialPalette) {
        repository.setMaterialPalette(palette)
        _uiState.value = _uiState.value.copy(
            themeSettings = _uiState.value.themeSettings.copy(materialPalette = palette)
        )
    }

    fun setDynamicColorEnabled(enabled: Boolean) {
        repository.setDynamicColorEnabled(enabled)
        _uiState.value = _uiState.value.copy(
            themeSettings = _uiState.value.themeSettings.copy(dynamicColorEnabled = enabled)
        )
    }

    fun setAmoledBlackEnabled(enabled: Boolean) {
        repository.setAmoledBlackEnabled(enabled)
        _uiState.value = _uiState.value.copy(
            themeSettings = _uiState.value.themeSettings.copy(amoledBlackEnabled = enabled)
        )
    }

    fun setPredictiveBackGestureEnabled(enabled: Boolean) {
        repository.setPredictiveBackGestureEnabled(enabled)
        _uiState.value = _uiState.value.copy(
            themeSettings = _uiState.value.themeSettings.copy(predictiveBackGestureEnabled = enabled)
        )
    }

    fun setEnableFloatingBottomBar(enabled: Boolean) {
        repository.setEnableFloatingBottomBar(enabled)
        _uiState.value = _uiState.value.copy(
            themeSettings = _uiState.value.themeSettings.copy(enableFloatingBottomBar = enabled)
        )
    }

    fun setEnableFloatingBottomBarBlur(enabled: Boolean) {
        repository.setEnableFloatingBottomBarBlur(enabled)
        _uiState.value = _uiState.value.copy(
            themeSettings = _uiState.value.themeSettings.copy(enableFloatingBottomBarBlur = enabled)
        )
    }

    fun setManualColorEnabled(enabled: Boolean) {
        repository.setManualColorEnabled(enabled)
        _uiState.value = _uiState.value.copy(
            themeSettings = _uiState.value.themeSettings.copy(manualColorEnabled = enabled)
        )
    }

    fun setManualSeedColorText(text: String) {
        val parsed = repository.parseSeedColor(text)
        if (parsed == null) {
            _uiState.value = _uiState.value.copy(
                manualSeedColorText = text,
                manualSeedColorError = text.isNotBlank()
            )
            return
        }

        repository.setManualSeedColor(parsed)
        _uiState.value = _uiState.value.copy(
            themeSettings = _uiState.value.themeSettings.copy(manualSeedColor = parsed),
            manualSeedColorText = text,
            manualSeedColorError = false
        )
    }

    fun finishManualSeedColorEditing() {
        val state = _uiState.value
        val parsed = repository.parseSeedColor(state.manualSeedColorText)
        val normalized = repository.formatSeedColor(parsed ?: state.themeSettings.manualSeedColor)
        _uiState.value = state.copy(
            manualSeedColorText = normalized,
            manualSeedColorError = false
        )
    }

    fun exportFileName(): String = repository.exportFileName()

    fun exportLogsToUri(uri: Uri, onResult: (Boolean, String?) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val result = repository.exportLogsToUri(uri)
                withContext(Dispatchers.Main) {
                    onResult(result, null)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to export logs", e)
                withContext(Dispatchers.Main) {
                    onResult(false, e.message)
                }
            }
        }
    }

    fun deleteAllLogs(onResult: (Boolean) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                repository.deleteAllLogs()
                withContext(Dispatchers.Main) { onResult(true) }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to delete all logs", e)
                withContext(Dispatchers.Main) { onResult(false) }
            }
        }
    }

    companion object {
        private const val TAG = "SettingsViewModel"
    }
}

data class SettingsUiState(
    val isDetailedLoggingEnabled: Boolean = false,
    val isEntryDisplayedInSettings: Boolean = false,
    val isHomepageYiyanEnabled: Boolean = true,
    val showRestoreConfirmDialog: Boolean = false,
    val versionName: String = "",
    val commitCount: Int = 0,
    val commitHash: String = "",
    val themeSettings: ZToolThemeSettings = ZToolThemeSettings(),
    val manualSeedColorText: String = "#%08X".format(ZToolThemeSettings.DEFAULT_MANUAL_SEED_COLOR),
    val manualSeedColorError: Boolean = false,
    val lsposedServiceProtector: Boolean = false,
)
