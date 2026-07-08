package com.qimian233.ztool.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import com.qimian233.ztool.data.systemui.StatusBarSettingsRepository
import com.qimian233.ztool.ui.components.normalizeArgbColorTextOrNull
import com.qimian233.ztool.ui.components.sanitizeArgbColorText
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class StatusBarSettingsViewModel(
    private val repository: StatusBarSettingsRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(loadInitialState())
    val uiState: StateFlow<StatusBarSettingsUiState> = _uiState.asStateFlow()

    fun loadSettings() {
        _uiState.value = loadInitialState()
    }

    private fun loadInitialState(): StatusBarSettingsUiState {
        try {
            return repository.loadState().withTextColorText()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load status bar settings", e)
        }
        return StatusBarSettingsUiState()
    }

    fun setDisplaySeconds(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(displaySeconds = enabled)
        repository.saveDisplaySeconds(enabled)
    }

    fun setCustomClock(enabled: Boolean) {
        val current = _uiState.value
        _uiState.value = current.copy(
            customClock = enabled,
            clockPreview = if (enabled) repository.buildClockPreview(current.clockFormat) else current.clockPreview
        )
        repository.saveCustomClock(enabled)
    }

    fun setClockFormat(format: String) {
        _uiState.value = _uiState.value.copy(
            clockFormat = format,
            clockPreview = repository.buildClockPreview(format)
        )
    }

    fun saveClockFormat() {
        repository.saveClockFormat(_uiState.value.clockFormat)
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

    fun setTextColorText(value: String) {
        _uiState.value = _uiState.value.copy(textColorText = value.sanitizeArgbColorText())
    }

    fun finishTextColorEditing() {
        val current = _uiState.value
        val normalized = current.textColorText.normalizeArgbColorTextOrNull()
            ?: current.textColor.toArgbText()
        val color = normalized.toLongOrNull(16)?.toInt() ?: current.textColor
        _uiState.value = current.copy(
            textColor = color,
            textColorText = normalized
        )
        repository.saveTextColor(color)
    }

    fun setTextBold(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(textBold = enabled)
        repository.saveTextBold(enabled)
    }

    fun setNotificationIconLimit(option: String): Boolean {
        _uiState.value = _uiState.value.copy(notificationIconLimitOption = option)
        return repository.saveNotificationIconLimit(option)
    }

    fun setNativeNotificationIcon(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(nativeNotificationIcon = enabled)
        repository.saveNativeNotificationIcon(enabled)
    }

    fun setNetworkSpeedSize(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(networkSpeedSize = enabled)
        repository.saveNetworkSpeedSize(enabled)
    }

    fun setNetworkSpeedDoubleLayer(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(networkSpeedDoubleLayer = enabled)
        repository.saveNetworkSpeedDoubleLayer(enabled)
    }

    fun setNetworkSpeedRefreshEnabled(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(networkSpeedRefreshEnabled = enabled)
        repository.saveNetworkSpeedRefreshEnabled(enabled)
    }

    fun setNetworkSpeedRefreshInterval(value: Float) {
        _uiState.value = _uiState.value.copy(networkSpeedRefreshInterval = value)
        repository.saveNetworkSpeedRefreshInterval(value)
    }

    fun setBatteryExternal(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(batteryExternal = enabled)
        repository.saveBatteryExternal(enabled)
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
        private const val TAG = "StatusBarSettingsViewModel"
    }
}

private fun StatusBarSettingsUiState.withTextColorText(): StatusBarSettingsUiState {
    return copy(textColorText = textColor.toArgbText())
}

private fun Int.toArgbText(): String {
    return "%08X".format(this)
}

data class StatusBarSettingsUiState(
    val displaySeconds: Boolean = false,
    val customClock: Boolean = false,
    val clockFormat: String = "",
    val clockPreview: String = "",
    val textSizeEnabled: Boolean = false,
    val textSize: Float = 16.0f,
    val letterSpacingEnabled: Boolean = false,
    val letterSpacing: Float = 0.1f,
    val textColorEnabled: Boolean = false,
    val textColor: Int = 0xFFFFFFFF.toInt(),
    val textColorText: String = "",
    val textBold: Boolean = false,
    val notificationIconLimitOption: String = "",
    val nativeNotificationIcon: Boolean = false,
    val networkSpeedSize: Boolean = false,
    val networkSpeedDoubleLayer: Boolean = false,
    val networkSpeedRefreshEnabled: Boolean = false,
    val networkSpeedRefreshInterval: Float = 3.0f,
    val batteryExternal: Boolean = false,
    val showFormatHelpDialog: Boolean = false,
    val showSaveSuccessDialog: Boolean = false
)
