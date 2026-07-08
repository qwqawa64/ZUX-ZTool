package com.qimian233.ztool.data.systemui

import android.content.Context
import android.util.Log
import com.qimian233.ztool.R
import com.qimian233.ztool.hook.modules.SharedPreferencesTool.ModulePreferencesUtils
import com.qimian233.ztool.hook.modules.systemui.CustomDateFormatter
import com.qimian233.ztool.viewmodel.StatusBarSettingsUiState
import java.util.Date

class StatusBarSettingsRepository(
    private val context: Context
) {
    private val prefsUtils = ModulePreferencesUtils(context)

    fun loadState(): StatusBarSettingsUiState {
        val loadedClockFormat = prefsUtils.loadStringSetting(KEY_CUSTOM_CLOCK_FORMAT, "")
        return StatusBarSettingsUiState(
            displaySeconds = prefsUtils.loadBooleanSetting(KEY_DISPLAY_SECONDS, false),
            customClock = prefsUtils.loadBooleanSetting(KEY_CUSTOM_CLOCK, false),
            nativeNotificationIcon = prefsUtils.loadBooleanSetting(KEY_NATIVE_NOTIFICATION_ICON, false),
            networkSpeedSize = prefsUtils.loadBooleanSetting(KEY_NETWORK_SPEED_SIZE, false),
            networkSpeedDoubleLayer = prefsUtils.loadBooleanSetting(KEY_NETWORK_SPEED_DOUBLE_LAYER, false),
            networkSpeedRefreshEnabled = prefsUtils.loadBooleanSetting(KEY_NETWORK_SPEED_REFRESH_ENABLED, false),
            networkSpeedRefreshInterval = prefsUtils.loadFloatSetting(KEY_NETWORK_SPEED_REFRESH_INTERVAL, DEFAULT_REFRESH_INTERVAL),
            batteryExternal = prefsUtils.loadBooleanSetting(KEY_BATTERY_EXTERNAL, false),
            clockFormat = loadedClockFormat,
            clockPreview = buildClockPreview(loadedClockFormat),
            textSize = prefsUtils.loadFloatSetting(KEY_CLOCK_TEXT_SIZE, 16.0f),
            textSizeEnabled = prefsUtils.loadBooleanSetting(KEY_CLOCK_TEXT_SIZE_ENABLED, false),
            letterSpacing = prefsUtils.loadFloatSetting(KEY_CLOCK_LETTER_SPACING, 0.1f),
            letterSpacingEnabled = prefsUtils.loadBooleanSetting(KEY_CLOCK_LETTER_SPACING_ENABLED, false),
            textColor = prefsUtils.loadIntegerSetting(KEY_CLOCK_TEXT_COLOR, 0xFFFFFFFF.toInt()),
            textColorEnabled = prefsUtils.loadBooleanSetting(KEY_CLOCK_TEXT_COLOR_ENABLED, false),
            textBold = prefsUtils.loadBooleanSetting(KEY_CLOCK_TEXT_BOLD, false),
            notificationIconLimitOption = notifyNumSizeToOption(prefsUtils.loadIntegerSetting(KEY_NOTIFY_NUM_SIZE, 4))
        )
    }

    fun buildClockPreview(format: String): String {
        return if (format.isEmpty()) {
            context.getString(R.string.preview_default)
        } else {
            try {
                context.getString(R.string.preview_display, CustomDateFormatter.format(format, Date()))
            } catch (e: Exception) {
                Log.e(TAG, "Error formatting date: $format", e)
                context.getString(R.string.preview_invalid) +
                    "\n" +
                    context.getString(R.string.error_prefix) +
                    e.message
            }
        }
    }

    fun saveDisplaySeconds(enabled: Boolean) {
        prefsUtils.saveBooleanSetting(KEY_DISPLAY_SECONDS, enabled)
    }

    fun saveCustomClock(enabled: Boolean) {
        prefsUtils.saveBooleanSetting(KEY_CUSTOM_CLOCK, enabled)
    }

    fun saveClockFormat(format: String) {
        prefsUtils.saveStringSetting(KEY_CUSTOM_CLOCK_FORMAT, format)
    }

    fun saveTextSizeEnabled(enabled: Boolean) {
        prefsUtils.saveBooleanSetting(KEY_CLOCK_TEXT_SIZE_ENABLED, enabled)
    }

    fun saveTextSize(value: Float) {
        prefsUtils.saveFloatSetting(KEY_CLOCK_TEXT_SIZE, value)
    }

    fun saveLetterSpacingEnabled(enabled: Boolean) {
        prefsUtils.saveBooleanSetting(KEY_CLOCK_LETTER_SPACING_ENABLED, enabled)
    }

    fun saveLetterSpacing(value: Float) {
        prefsUtils.saveFloatSetting(KEY_CLOCK_LETTER_SPACING, value)
    }

    fun saveTextColorEnabled(enabled: Boolean) {
        prefsUtils.saveBooleanSetting(KEY_CLOCK_TEXT_COLOR_ENABLED, enabled)
    }

    fun saveTextColor(color: Int) {
        prefsUtils.saveIntegerSetting(KEY_CLOCK_TEXT_COLOR, color)
    }

    fun saveTextBold(enabled: Boolean) {
        prefsUtils.saveBooleanSetting(KEY_CLOCK_TEXT_BOLD, enabled)
    }

    fun saveNativeNotificationIcon(enabled: Boolean) {
        prefsUtils.saveBooleanSetting(KEY_NATIVE_NOTIFICATION_ICON, enabled)
    }

    fun saveNetworkSpeedSize(enabled: Boolean) {
        prefsUtils.saveBooleanSetting(KEY_NETWORK_SPEED_SIZE, enabled)
    }

    fun saveNetworkSpeedDoubleLayer(enabled: Boolean) {
        prefsUtils.saveBooleanSetting(KEY_NETWORK_SPEED_DOUBLE_LAYER, enabled)
    }

    fun saveNetworkSpeedRefreshEnabled(enabled: Boolean) {
        prefsUtils.saveBooleanSetting(KEY_NETWORK_SPEED_REFRESH_ENABLED, enabled)
    }

    fun saveNetworkSpeedRefreshInterval(value: Float) {
        prefsUtils.saveFloatSetting(KEY_NETWORK_SPEED_REFRESH_INTERVAL, value)
    }

    fun saveBatteryExternal(enabled: Boolean) {
        prefsUtils.saveBooleanSetting(KEY_BATTERY_EXTERNAL, enabled)
    }

    fun saveNotificationIconLimit(option: String): Boolean {
        if (option == context.getString(R.string.notify_num_default)) {
            prefsUtils.saveBooleanSetting(KEY_NOTIFICATION_ICON_LIMIT, false)
            return true
        }

        prefsUtils.saveBooleanSetting(KEY_NOTIFICATION_ICON_LIMIT, true)
        if (option == context.getString(R.string.notify_num_unlimited)) {
            prefsUtils.saveIntegerSetting(KEY_NOTIFY_NUM_SIZE, 100)
            return true
        }

        val optionValue = option.toIntOrNull()
        return if (optionValue != null) {
            prefsUtils.saveIntegerSetting(KEY_NOTIFY_NUM_SIZE, optionValue)
            true
        } else {
            Log.e(TAG, "Invalid notification number option: $option")
            false
        }
    }

    private fun notifyNumSizeToOption(value: Int): String {
        return when (value) {
            100 -> context.getString(R.string.notify_num_unlimited)
            else -> value.coerceIn(1, 14).toString()
        }
    }

    companion object {
        private const val TAG = "StatusBarSettingsRepository"
        private const val KEY_NOTIFY_NUM_SIZE = "notify_num_size"
        private const val KEY_DISPLAY_SECONDS = "StatusBarDisplay_Seconds"
        private const val KEY_CUSTOM_CLOCK = "Custom_StatusBarClock"
        private const val KEY_CUSTOM_CLOCK_FORMAT = "Custom_StatusBarClockFormat"
        private const val KEY_CLOCK_TEXT_SIZE = "Custom_StatusBarClockTextSize"
        private const val KEY_CLOCK_TEXT_SIZE_ENABLED = "Custom_StatusBarClockTextSizeEnabled"
        private const val KEY_CLOCK_LETTER_SPACING = "Custom_StatusBarClockLetterSpacing"
        private const val KEY_CLOCK_LETTER_SPACING_ENABLED = "Custom_StatusBarClockLetterSpacingEnabled"
        private const val KEY_CLOCK_TEXT_COLOR = "Custom_StatusBarClockTextColor"
        private const val KEY_CLOCK_TEXT_COLOR_ENABLED = "Custom_StatusBarClockTextColorEnabled"
        private const val KEY_CLOCK_TEXT_BOLD = "Custom_StatusBarClockTextBold"
        private const val KEY_NOTIFICATION_ICON_LIMIT = "notification_icon_limit"
        private const val KEY_NATIVE_NOTIFICATION_ICON = "NativeNotificationIcon"
        private const val KEY_NETWORK_SPEED_SIZE = "systemui_network_speed_size"
        private const val KEY_NETWORK_SPEED_DOUBLE_LAYER = "systemui_network_speed_doublelayer"
        private const val KEY_NETWORK_SPEED_REFRESH_ENABLED = "systemui_network_speed_refresh_enabled"
        private const val KEY_NETWORK_SPEED_REFRESH_INTERVAL = "systemui_network_speed_refresh_interval"
        private const val DEFAULT_REFRESH_INTERVAL = 3.0f
        private const val KEY_BATTERY_EXTERNAL = "systemui_battery_percentage"
    }
}
