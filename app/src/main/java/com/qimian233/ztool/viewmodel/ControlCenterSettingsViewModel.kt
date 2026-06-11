package com.qimian233.ztool.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import com.qimian233.ztool.data.systemui.ControlCenterSettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class ControlCenterSettingsViewModel(
    private val repository: ControlCenterSettingsRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(ControlCenterSettingsUiState())
    val uiState: StateFlow<ControlCenterSettingsUiState> = _uiState.asStateFlow()

    fun loadSettings() {
        try {
            _uiState.value = repository.loadState()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load control center settings", e)
        }
    }

    fun setCustomDate(enabled: Boolean) {
        val current = _uiState.value
        val nextDateFormat = if (enabled && current.dateFormat.isEmpty()) {
            repository.getDefaultDateFormat()
        } else {
            current.dateFormat
        }

        repository.saveCustomDate(enabled)
        _uiState.value = current.copy(
            customDate = enabled,
            dateFormat = nextDateFormat,
            datePreview = repository.buildDatePreview(nextDateFormat)
        )
    }

    fun setDateFormat(format: String) {
        _uiState.value = _uiState.value.copy(
            dateFormat = format,
            datePreview = repository.buildDatePreview(format)
        )
    }

    fun saveDateFormat() {
        repository.saveDateFormat(_uiState.value.dateFormat)
        _uiState.value = _uiState.value.copy(showSaveSuccessDialog = true)
    }

    fun setTextSizeEnabled(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(textSizeEnabled = enabled)
        repository.saveTextSizeEnabled(enabled)
    }

    fun setTextSize(value: Float) {
        _uiState.value = _uiState.value.copy(textSize = value)
        repository.saveTextSize(value)
    }

    fun setLetterSpacingEnabled(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(letterSpacingEnabled = enabled)
        repository.saveLetterSpacingEnabled(enabled)
    }

    fun setLetterSpacing(value: Float) {
        _uiState.value = _uiState.value.copy(letterSpacing = value)
        repository.saveLetterSpacing(value)
    }

    fun setTextColorEnabled(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(textColorEnabled = enabled)
        repository.saveTextColorEnabled(enabled)
    }

    fun setTextColor(color: Int) {
        _uiState.value = _uiState.value.copy(
            showColorPickerDialog = false,
            textColor = color
        )
        repository.saveTextColor(color)
    }

    fun setTextBold(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(textBold = enabled)
        repository.saveTextBold(enabled)
    }

    fun showFormatHelpDialog() {
        _uiState.value = _uiState.value.copy(showFormatHelpDialog = true)
    }

    fun dismissFormatHelpDialog() {
        _uiState.value = _uiState.value.copy(showFormatHelpDialog = false)
    }

    fun showColorPickerDialog() {
        _uiState.value = _uiState.value.copy(showColorPickerDialog = true)
    }

    fun dismissColorPickerDialog() {
        _uiState.value = _uiState.value.copy(showColorPickerDialog = false)
    }

    fun dismissSaveSuccessDialog() {
        _uiState.value = _uiState.value.copy(showSaveSuccessDialog = false)
    }

    companion object {
        private const val TAG = "ControlCenterSettingsViewModel"
    }
}

data class ControlCenterSettingsUiState(
    val customDate: Boolean = false,
    val dateFormat: String = "",
    val datePreview: String = "",
    val textSizeEnabled: Boolean = false,
    val textSize: Float = 16.0f,
    val letterSpacingEnabled: Boolean = false,
    val letterSpacing: Float = 0.1f,
    val textColorEnabled: Boolean = false,
    val textColor: Int = 0xFFFFFFFF.toInt(),
    val textBold: Boolean = false,
    val showFormatHelpDialog: Boolean = false,
    val showColorPickerDialog: Boolean = false,
    val showSaveSuccessDialog: Boolean = false
)
