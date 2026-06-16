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

    fun loadState(): ControlCenterSettingsUiState {
        val loadedDateFormat = prefsUtils.loadStringSetting(
            KEY_DATE_FORMAT,
            context.getString(R.string.default_date_format)
        )
        return ControlCenterSettingsUiState(
            customDate = prefsUtils.loadBooleanSetting(KEY_CUSTOM_DATE, false),
            dateFormat = loadedDateFormat,
            datePreview = buildDatePreview(loadedDateFormat),
            textSize = prefsUtils.loadFloatSetting(KEY_TEXT_SIZE, 16.0f),
            textSizeEnabled = prefsUtils.loadBooleanSetting(KEY_TEXT_SIZE_ENABLED, false),
            letterSpacing = prefsUtils.loadFloatSetting(KEY_LETTER_SPACING, 0.1f),
            letterSpacingEnabled = prefsUtils.loadBooleanSetting(KEY_LETTER_SPACING_ENABLED, false),
            textColor = prefsUtils.loadIntegerSetting(KEY_TEXT_COLOR, 0xFFFFFFFF.toInt()),
            textColorEnabled = prefsUtils.loadBooleanSetting(KEY_TEXT_COLOR_ENABLED, false),
            textBold = prefsUtils.loadBooleanSetting(KEY_TEXT_BOLD, false),
            qsRoundCorner = prefsUtils.loadBooleanSetting(KEY_QS_ROUND_CORNER, false),
            qsTileRoundCornerRadius = prefsUtils.loadIntegerSetting(KEY_TILE_QS_ROUND_CORNER_RADIUS, 96),
            qsHeadUpRoundCornerRadius = prefsUtils.loadIntegerSetting(KEY_HEAD_UP_QS_ROUND_CORNER_RADIUS, 32)
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
        prefsUtils.saveStringSetting(KEY_DATE_FORMAT, format)
    }

    fun saveTextSizeEnabled(enabled: Boolean) {
        prefsUtils.saveBooleanSetting(KEY_TEXT_SIZE_ENABLED, enabled)
    }

    fun saveTextSize(value: Float) {
        prefsUtils.saveFloatSetting(KEY_TEXT_SIZE, value)
    }

    fun saveLetterSpacingEnabled(enabled: Boolean) {
        prefsUtils.saveBooleanSetting(KEY_LETTER_SPACING_ENABLED, enabled)
    }

    fun saveLetterSpacing(value: Float) {
        prefsUtils.saveFloatSetting(KEY_LETTER_SPACING, value)
    }

    fun saveTextColorEnabled(enabled: Boolean) {
        prefsUtils.saveBooleanSetting(KEY_TEXT_COLOR_ENABLED, enabled)
    }

    fun saveTextColor(color: Int) {
        prefsUtils.saveIntegerSetting(KEY_TEXT_COLOR, color)
    }

    fun saveTextBold(enabled: Boolean) {
        prefsUtils.saveBooleanSetting(KEY_TEXT_BOLD, enabled)
    }

    fun saveQsRoundCorner(enabled: Boolean) {
        prefsUtils.saveBooleanSetting(KEY_QS_ROUND_CORNER, enabled)
    }

    fun saveQsHeadUpRoundCornerRadius(value: Int) {
        prefsUtils.saveIntegerSetting(KEY_HEAD_UP_QS_ROUND_CORNER_RADIUS, value)
    }

    fun saveQsTileRoundCornerRadius(value: Int) {
        prefsUtils.saveIntegerSetting(KEY_TILE_QS_ROUND_CORNER_RADIUS, value)
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
        private const val KEY_QS_ROUND_CORNER = "qs_round_corner"
        private const val KEY_HEAD_UP_QS_ROUND_CORNER_RADIUS = "head_up_round_corner_radius"
        private const val KEY_TILE_QS_ROUND_CORNER_RADIUS = "tile_round_corner_radius"
    }
}
