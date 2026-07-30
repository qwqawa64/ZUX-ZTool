package com.qimian233.ztool.data.systemui

import android.content.Context
import android.graphics.Color
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
            controlCenterTextColor = prefsUtils.loadIntegerSetting(KEY_TEXT_COLOR, 0xFFFFFFFF.toInt()),
            controlCenterTextColorEnabled = prefsUtils.loadBooleanSetting(KEY_TEXT_COLOR_ENABLED, false),
            textBold = prefsUtils.loadBooleanSetting(KEY_TEXT_BOLD, false),
            qsRoundCorner = prefsUtils.loadBooleanSetting(KEY_QS_ROUND_CORNER, false),
            qsTileRoundCornerRadius = prefsUtils.loadIntegerSetting(KEY_TILE_QS_ROUND_CORNER_RADIUS, 96),
            qsHeadUpRoundCornerRadius = prefsUtils.loadIntegerSetting(KEY_HEAD_UP_QS_ROUND_CORNER_RADIUS, 32),
            customQsColor = prefsUtils.loadBooleanSetting(KEY_CUSTOM_QS_COLOR, false),
            customLabelColor = prefsUtils.loadBooleanSetting(KEY_CUSTOM_LABEL_COLOR, false),
            customSecondLabelColor = prefsUtils.loadBooleanSetting(KEY_CUSTOM_SECOND_LABEL_COLOR, false),
            noTileLabels = prefsUtils.loadBooleanSetting(KEY_NO_TILE_LABELS, false),
            customQsActiveColor = prefsUtils.loadIntegerSetting(KEY_CUSTOM_QS_ACTIVE_COLOR_VAL, DEFAULT_QS_ACTIVE_COLOR),
            customLabelActiveColor = prefsUtils.loadIntegerSetting(KEY_CUSTOM_LABEL_ACTIVE_COLOR_VAL, DEFAULT_LABEL_ACTIVE_COLOR),
            customSecondLabelActiveColor = prefsUtils.loadIntegerSetting(
                KEY_CUSTOM_SECOND_LABEL_ACTIVE_COLOR_VAL,
                DEFAULT_SECOND_LABEL_ACTIVE_COLOR
            ),
            customQsColorGeneralSwitch = prefsUtils.loadBooleanSetting(KEY_CUSTOM_QS_COLOR_GENERAL_SWITCH, false),
            notificationCenterBlurEnabled = prefsUtils.loadBooleanSetting(KEY_NOTIFICATION_CENTER_BLUR_ENABLED, false),
            notificationCenterBlurPercent = prefsUtils.loadIntegerSetting(
                KEY_NOTIFICATION_CENTER_BLUR_PERCENT,
                DEFAULT_NOTIFICATION_CENTER_BLUR_PERCENT
            ).coerceIn(NOTIFICATION_CENTER_BLUR_MIN_PERCENT, NOTIFICATION_CENTER_BLUR_MAX_PERCENT),
            brightnessSliderPercentageEnabled = prefsUtils.loadBooleanSetting(KEY_BRIGHTNESS_SLIDER_PERCENTAGE, false),
            volumeSliderPercentageEnabled = prefsUtils.loadBooleanSetting(KEY_VOLUME_SLIDER_PERCENTAGE, false),
            expandQsPanelPortrait = prefsUtils.loadBooleanSetting(KEY_EXPAND_QS_PANEL_PORTRAIT, false),
            qsPanelWidthPercent = prefsUtils.loadIntegerSetting(KEY_QS_PANEL_WIDTH_PERCENT, DEFAULT_QS_PANEL_WIDTH_PERCENT),
            qsTileColumns = prefsUtils.loadIntegerSetting(KEY_QS_TILE_COLUMNS, DEFAULT_QS_TILE_COLUMNS),
            customizeSliderStyle = prefsUtils.loadBooleanSetting(KEY_CUSTOMIZE_SLIDER_STYLE, false),
            sliderStyleIsVertical = prefsUtils.loadBooleanSetting(KEY_CUSTOMIZE_SLIDER_STYLE_VALUE, false),
            sliderStyleForcedByQsPanel = prefsUtils.loadBooleanSetting(KEY_EXPAND_QS_PANEL_PORTRAIT, false)
                    && prefsUtils.loadBooleanSetting(KEY_CUSTOMIZE_SLIDER_STYLE, false)
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

    fun saveCustomQsColor(enabled: Boolean) {
        prefsUtils.saveBooleanSetting(KEY_CUSTOM_QS_COLOR, enabled)
    }

    fun saveCustomLabelColor(enabled: Boolean) {
        prefsUtils.saveBooleanSetting(KEY_CUSTOM_LABEL_COLOR, enabled)
    }

    fun saveCustomSecondLabelColor(enabled: Boolean) {
        prefsUtils.saveBooleanSetting(KEY_CUSTOM_SECOND_LABEL_COLOR, enabled)
    }

    fun saveNoTileLabels(enabled: Boolean) {
        prefsUtils.saveBooleanSetting(KEY_NO_TILE_LABELS, enabled)
    }

    fun saveCustomQsActiveColor(color: Int) {
        prefsUtils.saveIntegerSetting(KEY_CUSTOM_QS_ACTIVE_COLOR_VAL, color)
    }

    fun saveCustomLabelActiveColor(color: Int) {
        prefsUtils.saveIntegerSetting(KEY_CUSTOM_LABEL_ACTIVE_COLOR_VAL, color)
    }

    fun saveCustomSecondLabelActiveColor(color: Int) {
        prefsUtils.saveIntegerSetting(KEY_CUSTOM_SECOND_LABEL_ACTIVE_COLOR_VAL, color)
    }

    fun saveCustomQsColorSwitch(enabled: Boolean) {
        prefsUtils.saveBooleanSetting(KEY_CUSTOM_QS_COLOR_GENERAL_SWITCH, enabled)
    }

    fun saveNotificationCenterBlurEnabled(enabled: Boolean) {
        prefsUtils.saveBooleanSetting(KEY_NOTIFICATION_CENTER_BLUR_ENABLED, enabled)
    }

    fun saveNotificationCenterBlurPercent(value: Int) {
        prefsUtils.saveIntegerSetting(
            KEY_NOTIFICATION_CENTER_BLUR_PERCENT,
            value.coerceIn(NOTIFICATION_CENTER_BLUR_MIN_PERCENT, NOTIFICATION_CENTER_BLUR_MAX_PERCENT)
        )
    }

    fun saveVolumeSliderPercentageEnabled(enabled: Boolean) {
        prefsUtils.saveBooleanSetting(KEY_VOLUME_SLIDER_PERCENTAGE, enabled)
    }

    fun saveBrightnessSliderPercentageEnabled(enabled: Boolean) {
        prefsUtils.saveBooleanSetting(KEY_BRIGHTNESS_SLIDER_PERCENTAGE, enabled)
    }

    fun saveExpandQsPanelPortrait(enabled: Boolean) {
        if (enabled) {
            // 备份当前的 slider style 偏好
            val currentStyle = prefsUtils.loadBooleanSetting(KEY_CUSTOMIZE_SLIDER_STYLE, false)
            val currentValue = prefsUtils.loadBooleanSetting(KEY_CUSTOMIZE_SLIDER_STYLE_VALUE, false)
            prefsUtils.saveBooleanSetting(KEY_CUSTOMIZE_SLIDER_STYLE_PREVIOUS, currentStyle)
            prefsUtils.saveBooleanSetting(KEY_CUSTOMIZE_SLIDER_STYLE_PREVIOUS_VALUE, currentValue)
            // 强制启用 SliderStyleHook 并设为水平
            prefsUtils.saveBooleanSetting(KEY_CUSTOMIZE_SLIDER_STYLE, true)
            prefsUtils.saveBooleanSetting(KEY_CUSTOMIZE_SLIDER_STYLE_VALUE, false)
        } else {
            // 还原之前的 slider style 偏好
            val previousStyle = prefsUtils.loadBooleanSetting(KEY_CUSTOMIZE_SLIDER_STYLE_PREVIOUS, false)
            val previousValue = prefsUtils.loadBooleanSetting(KEY_CUSTOMIZE_SLIDER_STYLE_PREVIOUS_VALUE, false)
            prefsUtils.saveBooleanSetting(KEY_CUSTOMIZE_SLIDER_STYLE, previousStyle)
            prefsUtils.saveBooleanSetting(KEY_CUSTOMIZE_SLIDER_STYLE_VALUE, previousValue)
        }
        prefsUtils.saveBooleanSetting(KEY_EXPAND_QS_PANEL_PORTRAIT, enabled)
    }

    fun saveCustomizeSliderStyle(enabled: Boolean) {
        prefsUtils.saveBooleanSetting(KEY_CUSTOMIZE_SLIDER_STYLE, enabled)
    }

    fun saveSliderStyleValue(isVertical: Boolean) {
        prefsUtils.saveBooleanSetting(KEY_CUSTOMIZE_SLIDER_STYLE_VALUE, isVertical)
    }

    fun saveQsPanelWidthPercent(value: Int) {
        prefsUtils.saveIntegerSetting(
            KEY_QS_PANEL_WIDTH_PERCENT,
            value.coerceIn(QS_PANEL_WIDTH_MIN, QS_PANEL_WIDTH_MAX)
        )
    }

    fun saveQsTileColumns(value: Int) {
        prefsUtils.saveIntegerSetting(
            KEY_QS_TILE_COLUMNS,
            value.coerceIn(QS_TILE_COLUMNS_MIN, QS_TILE_COLUMNS_MAX)
        )
    }

    companion object {
        private const val TAG = "ControlCenterSettingsRepository"
        const val NOTIFICATION_CENTER_BLUR_MIN_PERCENT = 0
        const val NOTIFICATION_CENTER_BLUR_MAX_PERCENT = 100
        const val DEFAULT_NOTIFICATION_CENTER_BLUR_PERCENT = 0
        val DEFAULT_QS_ACTIVE_COLOR: Int = Color.argb(0xbf, 0xad, 0xd8, 0xe6)
        val DEFAULT_LABEL_ACTIVE_COLOR: Int = Color.argb(0xff, 0xff, 0xff, 0xff)
        val DEFAULT_SECOND_LABEL_ACTIVE_COLOR: Int = Color.argb(0xbf, 0xff, 0xff, 0xff)
        val DEFAULT_CONTROL_CENTER_DATE_COLOR: Int = Color.argb(0xff, 0xff, 0xff, 0xff)
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
        private const val KEY_CUSTOM_QS_COLOR = "custom_qs_color"
        private const val KEY_CUSTOM_LABEL_COLOR = "custom_label_color"
        private const val KEY_CUSTOM_SECOND_LABEL_COLOR = "custom_second_label_color"
        private const val KEY_NO_TILE_LABELS = "control_center_no_tile_labels"
        private const val KEY_CUSTOM_QS_ACTIVE_COLOR_VAL = "custom_qs_active_color_val"
        private const val KEY_CUSTOM_LABEL_ACTIVE_COLOR_VAL = "custom_label_active_color_val"
        private const val KEY_CUSTOM_SECOND_LABEL_ACTIVE_COLOR_VAL = "custom_second_label_active_color_val"
        private const val KEY_CUSTOM_QS_COLOR_GENERAL_SWITCH = "qs_color"
        private const val KEY_NOTIFICATION_CENTER_BLUR_ENABLED = "notification_center_blur"
        private const val KEY_NOTIFICATION_CENTER_BLUR_PERCENT = "notification_center_blur_percent"
        private const val KEY_VOLUME_SLIDER_PERCENTAGE = "volume_slider_percentage"
        private const val KEY_BRIGHTNESS_SLIDER_PERCENTAGE = "brightness_slider_percentage"
        private const val KEY_EXPAND_QS_PANEL_PORTRAIT = "expand_qs_panel_portrait"
        private const val KEY_QS_PANEL_WIDTH_PERCENT = "qs_panel_width_percent"
        private const val KEY_QS_TILE_COLUMNS = "qs_tile_columns"
        private const val KEY_CUSTOMIZE_SLIDER_STYLE = "customize_slider_style"
        private const val KEY_CUSTOMIZE_SLIDER_STYLE_VALUE = "customize_slider_style_value"
        private const val KEY_CUSTOMIZE_SLIDER_STYLE_PREVIOUS = "customize_slider_style_previous"
        private const val KEY_CUSTOMIZE_SLIDER_STYLE_PREVIOUS_VALUE = "customize_slider_style_previous_value"
        const val QS_PANEL_WIDTH_MIN = 0
        const val QS_PANEL_WIDTH_MAX = 100
        const val DEFAULT_QS_PANEL_WIDTH_PERCENT = 80
        const val QS_TILE_COLUMNS_MIN = 0
        const val QS_TILE_COLUMNS_MAX = 10
        const val DEFAULT_QS_TILE_COLUMNS = 7
    }
}
