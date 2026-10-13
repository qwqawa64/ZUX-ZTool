package com.qimian233.ztool.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.qimian233.ztool.data.freeformbar.FreeformBarSettingsRepository
import com.qimian233.ztool.data.keys.PreferenceKeys
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class FreeformBarSettingsViewModel(
    private val repository: FreeformBarSettingsRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(FreeformBarSettingsUiState())
    val uiState: StateFlow<FreeformBarSettingsUiState> = _uiState.asStateFlow()

    fun loadSettings() {
        _uiState.value = repository.loadState()
    }

    fun setEnhance(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(enhanceEnabled = enabled)
        repository.saveEnhance(enabled)
    }

    fun setEditable(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(editable = enabled)
        repository.saveEditable(enabled)
    }

    fun setAllAppsSections(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(allAppsSections = enabled)
        repository.saveAllAppsSections(enabled)
    }

    fun setAllAppsSearch(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(allAppsSearch = enabled)
        repository.saveAllAppsSearch(enabled)
    }

    fun setDrawerSlots(count: Int) {
        val slots = count.coerceIn(
            FreeformBarSettingsRepository.SLOTS_MIN,
            FreeformBarSettingsRepository.SLOTS_MAX
        )
        _uiState.value = _uiState.value.copy(drawerSlots = slots)
        repository.saveSlots(slots)
    }

    fun setRecentSlots(count: Int) {
        val slots = count.coerceIn(
            FreeformBarSettingsRepository.RECENT_SLOTS_MIN,
            FreeformBarSettingsRepository.RECENT_SLOTS_MAX
        )
        _uiState.value = _uiState.value.copy(recentSlots = slots)
        repository.saveRecentSlots(slots)
    }

    fun showRestartDialog() {
        _uiState.value = _uiState.value.copy(showRestartDialog = true)
    }

    fun dismissRestartDialog() {
        _uiState.value = _uiState.value.copy(showRestartDialog = false)
    }

    fun restartScope(onFailure: () -> Unit) {
        _uiState.value = _uiState.value.copy(showRestartDialog = false)
        viewModelScope.launch(Dispatchers.IO) {
            val result = repository.restartScope()
            if (result is FreeformBarRestartResult.Failure) {
                withContext(Dispatchers.Main) {
                    onFailure()
                }
            }
        }
    }
}

data class FreeformBarSettingsUiState(
    val enhanceEnabled: Boolean = PreferenceKeys.SIDEBAR_DRAWER_ENHANCE.default,
    val editable: Boolean = PreferenceKeys.SIDEBAR_DRAWER_EDITABLE.default,
    val drawerSlots: Int = PreferenceKeys.SIDEBAR_DRAWER_SLOTS.default,
    val recentSlots: Int = PreferenceKeys.SIDEBAR_DRAWER_RECENT_SLOTS.default,
    val allAppsSections: Boolean = PreferenceKeys.SIDEBAR_ALLAPPS_SECTIONS.default,
    val allAppsSearch: Boolean = PreferenceKeys.SIDEBAR_ALLAPPS_SEARCH.default,
    val showRestartDialog: Boolean = false
)

sealed interface FreeformBarRestartResult {
    data object Success : FreeformBarRestartResult
    data class Failure(val error: String) : FreeformBarRestartResult
}
