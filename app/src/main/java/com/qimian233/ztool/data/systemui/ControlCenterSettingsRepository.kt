package com.qimian233.ztool.data.systemui

import android.content.Context
import android.graphics.Color
import android.util.Log
import com.qimian233.ztool.FeatureDestination
import com.qimian233.ztool.R
import com.qimian233.ztool.utils.ModulePreferencesUtils
import com.qimian233.ztool.utils.ScopeUtils
import com.qimian233.ztool.data.keys.PreferenceKeys
import com.qimian233.ztool.hook.modules.systemui.misc.CustomDateFormatter
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

    fun forceStopScope(): ShellActionResult {
        val scopes = ScopeUtils.getScopes(FeatureDestination.SystemUi)
        return when (val result = ScopeUtils.restartScope(scopes)) {
            is ScopeUtils.RestartResult.Success -> ShellActionResult(success = true, error = "", exitCode = 0)
            is ScopeUtils.RestartResult.PartialSuccess -> ShellActionResult(
                success = false,
                error = "Partial failure: ${result.failed.joinToString()}",
                exitCode = -1
            )
            is ScopeUtils.RestartResult.Failure -> ShellActionResult(
                success = false,
                error = result.message,
                exitCode = -1
            )
        }
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
        private val KEY_CUSTOM_DATE = PreferenceKeys.CUSTOM_CONTROL_CENTER_DATE.name
        private val KEY_DATE_FORMAT = PreferenceKeys.CUSTOM_CONTROL_CENTER_DATE_FORMAT.name
        private val KEY_TEXT_SIZE = PreferenceKeys.CUSTOM_CONTROL_CENTER_DATE_TEXT_SIZE.name
        private val KEY_TEXT_SIZE_ENABLED = PreferenceKeys.CUSTOM_CONTROL_CENTER_DATE_TEXT_SIZE_ENABLED.name
        private val KEY_LETTER_SPACING = PreferenceKeys.CUSTOM_CONTROL_CENTER_DATE_LETTER_SPACING.name
        private val KEY_LETTER_SPACING_ENABLED = PreferenceKeys.CUSTOM_CONTROL_CENTER_DATE_LETTER_SPACING_ENABLED.name
        private val KEY_TEXT_COLOR = PreferenceKeys.CUSTOM_CONTROL_CENTER_DATE_TEXT_COLOR.name
        private val KEY_TEXT_COLOR_ENABLED = PreferenceKeys.CUSTOM_CONTROL_CENTER_DATE_TEXT_COLOR_ENABLED.name
        private val KEY_TEXT_BOLD = PreferenceKeys.CUSTOM_CONTROL_CENTER_DATE_TEXT_BOLD.name
        private val KEY_QS_ROUND_CORNER = PreferenceKeys.QS_ROUND_CORNER.name
        private val KEY_HEAD_UP_QS_ROUND_CORNER_RADIUS = PreferenceKeys.HEAD_UP_ROUND_CORNER_RADIUS.name
        private val KEY_TILE_QS_ROUND_CORNER_RADIUS = PreferenceKeys.TILE_ROUND_CORNER_RADIUS.name
        private val KEY_CUSTOM_QS_COLOR = PreferenceKeys.CUSTOM_QS_COLOR.name
        private val KEY_CUSTOM_LABEL_COLOR = PreferenceKeys.CUSTOM_LABEL_COLOR.name
        private val KEY_CUSTOM_SECOND_LABEL_COLOR = PreferenceKeys.CUSTOM_SECOND_LABEL_COLOR.name
        private val KEY_NO_TILE_LABELS = PreferenceKeys.CONTROL_CENTER_NO_TILE_LABELS.name
        private val KEY_CUSTOM_QS_ACTIVE_COLOR_VAL = PreferenceKeys.CUSTOM_QS_ACTIVE_COLOR_VAL.name
        private val KEY_CUSTOM_LABEL_ACTIVE_COLOR_VAL = PreferenceKeys.CUSTOM_LABEL_ACTIVE_COLOR_VAL.name
        private val KEY_CUSTOM_SECOND_LABEL_ACTIVE_COLOR_VAL = PreferenceKeys.CUSTOM_SECOND_LABEL_ACTIVE_COLOR_VAL.name
        private val KEY_CUSTOM_QS_COLOR_GENERAL_SWITCH = PreferenceKeys.QS_COLOR.name
        private val KEY_NOTIFICATION_CENTER_BLUR_ENABLED = PreferenceKeys.NOTIFICATION_CENTER_BLUR.name
        private val KEY_NOTIFICATION_CENTER_BLUR_PERCENT = PreferenceKeys.NOTIFICATION_CENTER_BLUR_PERCENT.name
        private val KEY_VOLUME_SLIDER_PERCENTAGE = PreferenceKeys.VOLUME_SLIDER_PERCENTAGE.name
        private val KEY_BRIGHTNESS_SLIDER_PERCENTAGE = PreferenceKeys.BRIGHTNESS_SLIDER_PERCENTAGE.name
        private val KEY_EXPAND_QS_PANEL_PORTRAIT = PreferenceKeys.EXPAND_QS_PANEL_PORTRAIT.name
        private val KEY_QS_PANEL_WIDTH_PERCENT = PreferenceKeys.QS_PANEL_WIDTH_PERCENT.name
        private val KEY_QS_TILE_COLUMNS = PreferenceKeys.QS_TILE_COLUMNS.name
        private val KEY_CUSTOMIZE_SLIDER_STYLE = PreferenceKeys.CUSTOMIZE_SLIDER_STYLE.name
        private val KEY_CUSTOMIZE_SLIDER_STYLE_VALUE = PreferenceKeys.CUSTOMIZE_SLIDER_STYLE_VALUE.name
        private val KEY_CUSTOMIZE_SLIDER_STYLE_PREVIOUS = PreferenceKeys.CUSTOMIZE_SLIDER_STYLE_PREVIOUS.name
        private val KEY_CUSTOMIZE_SLIDER_STYLE_PREVIOUS_VALUE = PreferenceKeys.CUSTOMIZE_SLIDER_STYLE_PREVIOUS_VALUE.name
        const val QS_PANEL_WIDTH_MIN = 0
        const val QS_PANEL_WIDTH_MAX = 100
        const val DEFAULT_QS_PANEL_WIDTH_PERCENT = 80
        const val QS_TILE_COLUMNS_MIN = 0
        const val QS_TILE_COLUMNS_MAX = 10
        const val DEFAULT_QS_TILE_COLUMNS = 7
    }
}
