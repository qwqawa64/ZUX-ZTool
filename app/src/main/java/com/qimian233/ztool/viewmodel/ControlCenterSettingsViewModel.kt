package com.qimian233.ztool.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import com.qimian233.ztool.data.systemui.ControlCenterSettingsRepository
import com.qimian233.ztool.ui.components.normalizeArgbColorTextOrNull
import com.qimian233.ztool.ui.components.sanitizeArgbColorText
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
            _uiState.value = repository.loadState().withColorText()
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
        _uiState.value = _uiState.value.copy(controlCenterTextColorEnabled = enabled)
        repository.saveTextColorEnabled(enabled)
    }

    fun finishControlCenterClockColorEditing() {
        saveColorText(
            text = _uiState.value.controlCenterTextColorText,
            fallback = ControlCenterSettingsRepository.DEFAULT_CONTROL_CENTER_DATE_COLOR
        ) { color, text ->
            _uiState.value = _uiState.value.copy(
                controlCenterTextColor = color,
                controlCenterTextColorText = text
            )
            repository.saveTextColor(color)
        }
    }

    fun setControlCenterClockColorText(value: String) {
        _uiState.value = _uiState.value.copy(controlCenterTextColorText = value.sanitizeArgbColorText())
    }

    fun setTextBold(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(textBold = enabled)
        repository.saveTextBold(enabled)
    }

    fun setQsRoundCorner(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(qsRoundCorner = enabled)
        repository.saveQsRoundCorner(enabled)
    }

    fun setQsHeadUpRoundCornerRadius(value: Int) {
        _uiState.value = _uiState.value.copy(qsHeadUpRoundCornerRadius = value)
        repository.saveQsHeadUpRoundCornerRadius(value)
    }

    fun setQsTileRoundCornerRadius(value: Int) {
        _uiState.value = _uiState.value.copy(qsTileRoundCornerRadius = value)
        repository.saveQsTileRoundCornerRadius(value)
    }

    fun setCustomQsColor(enabled: Boolean) {
        val current = _uiState.value
        _uiState.value = current.copy(customQsColor = enabled)
        repository.saveCustomQsColor(enabled)
        if (enabled) {
            repository.saveCustomQsActiveColor(current.customQsActiveColor)
        }
    }

    fun setCustomLabelColor(enabled: Boolean) {
        val current = _uiState.value
        _uiState.value = current.copy(customLabelColor = enabled)
        repository.saveCustomLabelColor(enabled)
        if (enabled) {
            repository.saveCustomLabelActiveColor(current.customLabelActiveColor)
        }
    }

    fun setCustomSecondLabelColor(enabled: Boolean) {
        val current = _uiState.value
        _uiState.value = current.copy(customSecondLabelColor = enabled)
        repository.saveCustomSecondLabelColor(enabled)
        if (enabled) {
            repository.saveCustomSecondLabelActiveColor(current.customSecondLabelActiveColor)
        }
    }

    fun setCustomQsActiveColorText(value: String) {
        _uiState.value = _uiState.value.copy(customQsActiveColorText = value.sanitizeArgbColorText())
    }

    fun setCustomLabelActiveColorText(value: String) {
        _uiState.value = _uiState.value.copy(customLabelActiveColorText = value.sanitizeArgbColorText())
    }

    fun setCustomSecondLabelActiveColorText(value: String) {
        _uiState.value = _uiState.value.copy(customSecondLabelActiveColorText = value.sanitizeArgbColorText())
    }

    fun setCustomQsColorSwitch(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(customQsColorGeneralSwitch = enabled)
        repository.saveCustomQsColorSwitch(enabled)
    }

    fun finishCustomQsActiveColorEditing() {
        saveColorText(
            text = _uiState.value.customQsActiveColorText,
            fallback = ControlCenterSettingsRepository.DEFAULT_QS_ACTIVE_COLOR
        ) { color, text ->
            _uiState.value =
                _uiState.value.copy(customQsActiveColor = color, customQsActiveColorText = text)
            repository.saveCustomQsActiveColor(color)
        }
    }

    fun finishCustomLabelActiveColorEditing() {
        saveColorText(
            text = _uiState.value.customLabelActiveColorText,
            fallback = ControlCenterSettingsRepository.DEFAULT_LABEL_ACTIVE_COLOR
        ) { color, text ->
            _uiState.value = _uiState.value.copy(
                customLabelActiveColor = color,
                customLabelActiveColorText = text
            )
            repository.saveCustomLabelActiveColor(color)
        }
    }

    fun finishCustomSecondLabelActiveColorEditing() {
        saveColorText(
            text = _uiState.value.customSecondLabelActiveColorText,
            fallback = ControlCenterSettingsRepository.DEFAULT_SECOND_LABEL_ACTIVE_COLOR
        ) { color, text ->
            _uiState.value = _uiState.value.copy(
                customSecondLabelActiveColor = color,
                customSecondLabelActiveColorText = text
            )
            repository.saveCustomSecondLabelActiveColor(color)
        }
    }

    fun showFormatHelpDialog() {
        _uiState.value = _uiState.value.copy(showFormatHelpDialog = true)
    }

    fun dismissFormatHelpDialog() {
        _uiState.value = _uiState.value.copy(showFormatHelpDialog = false)
    }

    fun dismissSaveSuccessDialog() {
        _uiState.value = _uiState.value.copy(showSaveSuccessDialog = false)
    }

    companion object {
        private const val TAG = "ControlCenterSettingsViewModel"
    }
}

private fun saveColorText(
    text: String,
    fallback: Int,
    update: (color: Int, text: String) -> Unit
) {
    val normalized = text.normalizeArgbColorTextOrNull() ?: fallback.toArgbText()
    val color = normalized.toLongOrNull(16)?.toInt() ?: fallback
    update(color, normalized)
}

private fun ControlCenterSettingsUiState.withColorText(): ControlCenterSettingsUiState {
    return copy(
        customQsActiveColorText = customQsActiveColor.toArgbText(),
        customLabelActiveColorText = customLabelActiveColor.toArgbText(),
        customSecondLabelActiveColorText = customSecondLabelActiveColor.toArgbText()
    )
}

private fun Int.toArgbText(): String {
    return "%08X".format(this)
}

data class ControlCenterSettingsUiState(
    val customDate: Boolean = false,
    val dateFormat: String = "",
    val datePreview: String = "",
    val textSizeEnabled: Boolean = false,
    val textSize: Float = 16.0f,
    val letterSpacingEnabled: Boolean = false,
    val letterSpacing: Float = 0.1f,
    val controlCenterTextColorEnabled: Boolean = false,
    val controlCenterTextColor: Int = 0xFFFFFFFF.toInt(),
    val controlCenterTextColorText: String = "",
    val textBold: Boolean = false,
    val showFormatHelpDialog: Boolean = false,
    val showColorPickerDialog: Boolean = false,
    val showSaveSuccessDialog: Boolean = false,
    val qsRoundCorner: Boolean = false,
    val qsHeadUpRoundCornerRadius: Int = 32,
    val qsTileRoundCornerRadius: Int = 96,
    val customQsColor: Boolean = false,
    val customLabelColor: Boolean = false,
    val customSecondLabelColor: Boolean = false,
    val customQsActiveColor: Int = ControlCenterSettingsRepository.DEFAULT_QS_ACTIVE_COLOR,
    val customLabelActiveColor: Int = ControlCenterSettingsRepository.DEFAULT_LABEL_ACTIVE_COLOR,
    val customSecondLabelActiveColor: Int = ControlCenterSettingsRepository.DEFAULT_SECOND_LABEL_ACTIVE_COLOR,
    val customQsActiveColorText: String = "",
    val customLabelActiveColorText: String = "",
    val customSecondLabelActiveColorText: String = "",
    val customQsColorGeneralSwitch: Boolean = false,
)
