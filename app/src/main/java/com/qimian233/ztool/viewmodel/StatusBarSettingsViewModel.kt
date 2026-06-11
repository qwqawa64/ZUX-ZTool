package com.qimian233.ztool.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import com.qimian233.ztool.data.systemui.StatusBarSettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class StatusBarSettingsViewModel(
    private val repository: StatusBarSettingsRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(StatusBarSettingsUiState())
    val uiState: StateFlow<StatusBarSettingsUiState> = _uiState.asStateFlow()

    fun loadSettings() {
        try {
            _uiState.value = repository.loadState()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load status bar settings", e)
        }
    }

    fun setDisplaySeconds(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(displaySeconds = enabled)
        repository.saveBooleanSetting(KEY_DISPLAY_SECONDS, enabled)
    }

    fun setCustomClock(enabled: Boolean) {
        val current = _uiState.value
        _uiState.value = current.copy(
            customClock = enabled,
            clockPreview = if (enabled) repository.buildClockPreview(current.clockFormat) else current.clockPreview
        )
        repository.saveBooleanSetting(KEY_CUSTOM_CLOCK, enabled)
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

    fun setNotificationIconLimit(option: String): Boolean {
        _uiState.value = _uiState.value.copy(notificationIconLimitOption = option)
        return repository.saveNotificationIconLimit(option)
    }

    fun setNativeNotificationIcon(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(nativeNotificationIcon = enabled)
        repository.saveBooleanSetting(KEY_NATIVE_NOTIFICATION_ICON, enabled)
    }

    fun setNetworkSpeedSize(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(networkSpeedSize = enabled)
        repository.saveBooleanSetting(KEY_NETWORK_SPEED_SIZE, enabled)
    }

    fun setNetworkSpeedDoubleLayer(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(networkSpeedDoubleLayer = enabled)
        repository.saveBooleanSetting(KEY_NETWORK_SPEED_DOUBLE_LAYER, enabled)
    }

    fun setBatteryExternal(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(batteryExternal = enabled)
        repository.saveBooleanSetting(KEY_BATTERY_EXTERNAL, enabled)
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
        private const val TAG = "StatusBarSettingsViewModel"
        private const val KEY_DISPLAY_SECONDS = "StatusBarDisplay_Seconds"
        private const val KEY_CUSTOM_CLOCK = "Custom_StatusBarClock"
        private const val KEY_NATIVE_NOTIFICATION_ICON = "NativeNotificationIcon"
        private const val KEY_NETWORK_SPEED_SIZE = "systemui_network_speed_size"
        private const val KEY_NETWORK_SPEED_DOUBLE_LAYER = "systemui_network_speed_doublelayer"
        private const val KEY_BATTERY_EXTERNAL = "systemui_battery_percentage"
    }
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
    val textBold: Boolean = false,
    val notificationIconLimitOption: String = "",
    val nativeNotificationIcon: Boolean = false,
    val networkSpeedSize: Boolean = false,
    val networkSpeedDoubleLayer: Boolean = false,
    val batteryExternal: Boolean = false,
    val showFormatHelpDialog: Boolean = false,
    val showColorPickerDialog: Boolean = false,
    val showSaveSuccessDialog: Boolean = false
)
