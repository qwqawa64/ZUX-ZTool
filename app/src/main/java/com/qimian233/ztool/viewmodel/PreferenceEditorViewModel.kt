package com.qimian233.ztool.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.qimian233.ztool.data.preferences.PreferenceEditOutcome
import com.qimian233.ztool.data.preferences.PreferenceEditorFailureReason
import com.qimian233.ztool.data.preferences.PreferenceEditorRepository
import com.qimian233.ztool.data.preferences.PreferenceEditorTarget
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Confirmation the editor raised before executing a write/delete; rendered as
 * a dialog by the screen and resolved via [confirmPending] / [dismissPending].
 */
sealed interface PreferenceEditorPendingConfirm {
    data class UnregisteredWrite(val key: String, val rawValue: String) :
        PreferenceEditorPendingConfirm

    data class DeleteRegisteredKey(val key: String) : PreferenceEditorPendingConfirm
}

data class PreferenceEditorUiState(
    val target: PreferenceEditorTarget = PreferenceEditorTarget.XPOSED_MODULE_CONFIG,
    val keyText: String = "",
    val valueText: String = "",
    val outcome: PreferenceEditOutcome? = null,
    val pendingConfirm: PreferenceEditorPendingConfirm? = null
)

/**
 * ViewModel for the SharedPreference Editor screen. All preference I/O runs on
 * [Dispatchers.IO] through [PreferenceEditorRepository].
 */
class PreferenceEditorViewModel(
    context: Context
) : ViewModel() {

    private val repository = PreferenceEditorRepository(context.applicationContext)

    private val _uiState = MutableStateFlow(PreferenceEditorUiState())
    val uiState: StateFlow<PreferenceEditorUiState> = _uiState.asStateFlow()

    fun selectTarget(target: PreferenceEditorTarget) = _uiState.update {
        it.copy(target = target, outcome = null)
    }

    fun updateKeyText(text: String) = _uiState.update { it.copy(keyText = text) }

    fun updateValueText(text: String) = _uiState.update { it.copy(valueText = text) }

    fun read() {
        val key = currentKey() ?: return
        runOperation { repository.read(it.target, key) }
    }

    fun write() {
        val state = _uiState.value
        val key = currentKey() ?: return
        if (!repository.isRegisteredKey(state.target, key)) {
            _uiState.update {
                it.copy(
                    pendingConfirm = PreferenceEditorPendingConfirm.UnregisteredWrite(
                        key, state.valueText
                    )
                )
            }
            return
        }
        performWrite(key, state.valueText)
    }

    fun delete() {
        val state = _uiState.value
        val key = currentKey() ?: return
        if (repository.isRegisteredKey(state.target, key)) {
            _uiState.update {
                it.copy(pendingConfirm = PreferenceEditorPendingConfirm.DeleteRegisteredKey(key))
            }
            return
        }
        performDelete(key)
    }

    fun listAll() = runOperation { repository.listAll(it.target) }

    fun confirmPending() {
        val pending = _uiState.value.pendingConfirm ?: return
        _uiState.update { it.copy(pendingConfirm = null) }
        when (pending) {
            is PreferenceEditorPendingConfirm.UnregisteredWrite ->
                performWrite(pending.key, pending.rawValue, allowUnregistered = true)
            is PreferenceEditorPendingConfirm.DeleteRegisteredKey ->
                performDelete(pending.key)
        }
    }

    fun dismissPending() = _uiState.update { it.copy(pendingConfirm = null) }

    private fun performWrite(
        key: String,
        rawValue: String,
        allowUnregistered: Boolean = false
    ) {
        val target = _uiState.value.target
        runOperation { repository.write(target, key, rawValue, allowUnregistered) }
    }

    private fun performDelete(key: String) {
        val target = _uiState.value.target
        runOperation { repository.delete(target, key) }
    }

    private fun currentKey(): String? {
        val key = _uiState.value.keyText.trim()
        if (key.isEmpty()) {
            _uiState.update {
                it.copy(
                    outcome = PreferenceEditOutcome.Failure(
                        PreferenceEditorFailureReason.KEY_NOT_FOUND,
                        "key is blank"
                    )
                )
            }
            return null
        }
        return key
    }

    private fun runOperation(operation: (PreferenceEditorUiState) -> PreferenceEditOutcome) {
        viewModelScope.launch(Dispatchers.IO) {
            val outcome = operation(_uiState.value)
            _uiState.update { it.copy(outcome = outcome) }
        }
    }
}
