package com.qimian233.ztool.viewmodel

import androidx.lifecycle.ViewModel
import com.qimian233.ztool.data.zuiui.ZuiDialogSkinRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class ZuiDialogSkinViewModel(
    private val repository: ZuiDialogSkinRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ZuiDialogSkinUiState())
    val uiState: StateFlow<ZuiDialogSkinUiState> = _uiState.asStateFlow()

    fun loadSettings() {
        _uiState.value = repository.loadState()
    }

    fun setDialogSkinEnabled(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(dialogSkinEnabled = enabled)
        repository.saveDialogSkinEnabled(enabled)
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
}

data class ZuiDialogSkinUiState(
    val dialogSkinEnabled: Boolean = false
)

/** Targets the module managed to reinstall itself into, and those it did not. */
data class HotReloadOutcome(
    val succeeded: Int,
    val failed: Int
)
