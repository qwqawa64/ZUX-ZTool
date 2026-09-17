package com.qimian233.ztool.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.qimian233.ztool.data.keys.PreferenceKeys
import com.qimian233.ztool.data.sogouime.SogouImeSettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SogouImeSettingsViewModel(
    private val repository: SogouImeSettingsRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(SogouImeSettingsUiState())
    val uiState: StateFlow<SogouImeSettingsUiState> = _uiState.asStateFlow()

    fun loadSettings() {
        _uiState.value = repository.loadState()
    }

    fun setHalfWidthPunct(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(halfWidthPunct = enabled)
        repository.saveHalfWidthPunct(enabled)
    }

    fun setHalfWidthPunctSigns(value: String) {
        val compact = value.filter { c ->
            (c.code in 0xFF01..0xFF5E || c in '!'..'~') && !c.isWhitespace()
        }
        _uiState.value = _uiState.value.copy(halfWidthPunctSigns = compact)
        repository.saveHalfWidthPunctSigns(compact)
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
            if (result is SogouImeRestartResult.Failure) {
                withContext(Dispatchers.Main) {
                    onFailure()
                }
            }
        }
    }
}

data class SogouImeSettingsUiState(
    val halfWidthPunct: Boolean = false,
    val halfWidthPunctSigns: String = PreferenceKeys.HALF_WIDTH_PUNCT_SIGNS.default,
    val showRestartDialog: Boolean = false
)

sealed interface SogouImeRestartResult {
    data object Success : SogouImeRestartResult
    data class Failure(val error: String) : SogouImeRestartResult
}
