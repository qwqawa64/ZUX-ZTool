package com.qimian233.ztool.data.systemui

import android.content.Context
import android.util.Log
import com.qimian233.ztool.R
import com.qimian233.ztool.hook.modules.SharedPreferencesTool.ModulePreferencesUtils
import com.qimian233.ztool.hook.modules.systemui.CustomDateFormatter
import com.qimian233.ztool.viewmodel.ControlCenterSettingsUiState
import java.util.Date

class ControlCenterSettingsRepository(
    private val context: Context
) {
    private val prefsUtils = ModulePreferencesUtils(context)
    private val zToolPrefs = ModulePreferencesUtils(context)

    fun loadState(): ControlCenterSettingsUiState {
        val loadedDateFormat = zToolPrefs.loadStringSetting(
            KEY_DATE_FORMAT,
            context.getString(R.string.default_date_format)
        )
        return ControlCenterSettingsUiState(
            customDate = prefsUtils.loadBooleanSetting(KEY_CUSTOM_DATE, false),
            dateFormat = loadedDateFormat,
            datePreview = buildDatePreview(loadedDateFormat),
            textSize = zToolPrefs.loadFloatSetting(KEY_TEXT_SIZE, 16.0f),
            textSizeEnabled = zToolPrefs.loadBooleanSetting(KEY_TEXT_SIZE_ENABLED, false),
            letterSpacing = zToolPrefs.loadFloatSetting(KEY_LETTER_SPACING, 0.1f),
            letterSpacingEnabled = zToolPrefs.loadBooleanSetting(KEY_LETTER_SPACING_ENABLED, false),
            textColor = zToolPrefs.loadIntegerSetting(KEY_TEXT_COLOR, 0xFFFFFFFF.toInt()),
            textColorEnabled = zToolPrefs.loadBooleanSetting(KEY_TEXT_COLOR_ENABLED, false),
            textBold = zToolPrefs.loadBooleanSetting(KEY_TEXT_BOLD, false)
        )
    }

    fun getDefaultDateFormat(): String {
        return context.getString(R.string.default_date_format)
    }

    fun buildDatePreview(format: String): String {
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

    fun saveCustomDate(enabled: Boolean) {
        prefsUtils.saveBooleanSetting(KEY_CUSTOM_DATE, enabled)
    }

    fun saveDateFormat(format: String) {
        zToolPrefs.saveStringSetting(KEY_DATE_FORMAT, format)
    }

    fun saveTextSizeEnabled(enabled: Boolean) {
        zToolPrefs.saveBooleanSetting(KEY_TEXT_SIZE_ENABLED, enabled)
    }

    fun saveTextSize(value: Float) {
        zToolPrefs.saveFloatSetting(KEY_TEXT_SIZE, value)
    }

    fun saveLetterSpacingEnabled(enabled: Boolean) {
        zToolPrefs.saveBooleanSetting(KEY_LETTER_SPACING_ENABLED, enabled)
    }

    fun saveLetterSpacing(value: Float) {
        zToolPrefs.saveFloatSetting(KEY_LETTER_SPACING, value)
    }

    fun saveTextColorEnabled(enabled: Boolean) {
        zToolPrefs.saveBooleanSetting(KEY_TEXT_COLOR_ENABLED, enabled)
    }

    fun saveTextColor(color: Int) {
        zToolPrefs.saveIntegerSetting(KEY_TEXT_COLOR, color)
    }

    fun saveTextBold(enabled: Boolean) {
        zToolPrefs.saveBooleanSetting(KEY_TEXT_BOLD, enabled)
    }

    companion object {
        private const val TAG = "ControlCenterSettingsRepository"
        private const val KEY_CUSTOM_DATE = "Custom_ControlCenterDate"
        private const val KEY_DATE_FORMAT = "Custom_ControlCenterDateFormat"
        private const val KEY_TEXT_SIZE = "Custom_ControlCenterDateTextSize"
        private const val KEY_TEXT_SIZE_ENABLED = "Custom_ControlCenterDateTextSizeEnabled"
        private const val KEY_LETTER_SPACING = "Custom_ControlCenterDateLetterSpacing"
        private const val KEY_LETTER_SPACING_ENABLED = "Custom_ControlCenterDateLetterSpacingEnabled"
        private const val KEY_TEXT_COLOR = "Custom_ControlCenterDateTextColor"
        private const val KEY_TEXT_COLOR_ENABLED = "Custom_ControlCenterDateTextColorEnabled"
        private const val KEY_TEXT_BOLD = "Custom_ControlCenterDateTextBold"
    }
}
