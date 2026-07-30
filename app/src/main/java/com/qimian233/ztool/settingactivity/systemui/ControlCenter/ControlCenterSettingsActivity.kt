@file:Suppress("PackageName")

package com.qimian233.ztool.settingactivity.systemui.ControlCenter

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import com.qimian233.ztool.ui.theme.LocalZToolColorScheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import com.qimian233.ztool.R
import com.qimian233.ztool.data.systemui.ControlCenterSettingsRepository
import com.qimian233.ztool.ui.components.QuickHelpExample
import com.qimian233.ztool.ui.components.QuickHelpItem
import com.qimian233.ztool.ui.components.SettingItem
import com.qimian233.ztool.ui.components.SettingSection
import com.qimian233.ztool.ui.components.ZToolArgbColorTextFieldRow
import com.qimian233.ztool.ui.components.ZToolButton
import com.qimian233.ztool.ui.components.ZToolDialog
import com.qimian233.ztool.ui.components.ZToolQuickHelpDialog
import com.qimian233.ztool.ui.components.ZToolPopupMenuSettingRow
import com.qimian233.ztool.ui.components.ZToolScaffold
import com.qimian233.ztool.ui.components.ZToolSettingsList
import com.qimian233.ztool.ui.components.ZToolSliderRow
import com.qimian233.ztool.ui.components.ZToolSwitchRow
import com.qimian233.ztool.ui.components.ZToolTextButton
import com.qimian233.ztool.ui.components.ZToolTextInputRow
import com.qimian233.ztool.ui.components.ZToolTopAppBar
import com.qimian233.ztool.viewmodel.ControlCenterSettingsUiState
import com.qimian233.ztool.viewmodel.ControlCenterSettingsViewModel
import kotlin.math.roundToInt

private const val QS_ROUND_CORNER_MIN_RADIUS = 0
private const val QS_ROUND_CORNER_MAX_RADIUS = 96
private const val QS_ROUND_CORNER_STEP_INCREMENTAL = 8
private const val QS_ROUND_CORNER_STEPS =
    (QS_ROUND_CORNER_MAX_RADIUS - QS_ROUND_CORNER_MIN_RADIUS) / QS_ROUND_CORNER_STEP_INCREMENTAL - 1

private fun snapToAccurateRadius(value: Float): Int {
    return ((value / QS_ROUND_CORNER_STEP_INCREMENTAL).roundToInt() * QS_ROUND_CORNER_STEP_INCREMENTAL)
        .coerceIn(QS_ROUND_CORNER_MIN_RADIUS, QS_ROUND_CORNER_MAX_RADIUS)
}

private fun snapToAccuratePercent(value: Float): Int {
    return ((value / 5f).roundToInt() * 5).coerceIn(0, 100)
}

@Composable
fun ControlCenterSettingsRoute(
    title: String,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val owner = LocalViewModelStoreOwner.current
        ?: error("ControlCenterSettingsRoute requires a ViewModelStoreOwner")
    val viewModel = remember(owner) {
        ViewModelProvider(
            owner,
            ControlCenterSettingsViewModelFactory(
                ControlCenterSettingsRepository(context.applicationContext)
            )
        )[ControlCenterSettingsViewModel::class.java]
    }

    LaunchedEffect(viewModel) {
        viewModel.loadSettings()
    }

    val dateFormatExample = stringResource(R.string.date_format_example)
    val dateFormatSample = stringResource(R.string.date_format_sample)

    fun copyDateFormatExample() {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText(
            dateFormatExample,
            dateFormatSample
        )
        clipboard.setPrimaryClip(clip)
        Toast.makeText(context, R.string.example_copied_message, Toast.LENGTH_SHORT).show()
    }

    val uiState by viewModel.uiState.collectAsState()

    ControlCenterSettingsScreen(
        title = title,
        state = uiState,
        onBack = onBack,
        onCustomDateChanged = viewModel::setCustomDate,
        onDateFormatChanged = viewModel::setDateFormat,
        onSaveDateFormat = viewModel::saveDateFormat,
        onShowFormatHelp = viewModel::showFormatHelpDialog,
        onTextSizeEnabledChanged = viewModel::setTextSizeEnabled,
        onTextSizeChanged = viewModel::setTextSize,
        onLetterSpacingEnabledChanged = viewModel::setLetterSpacingEnabled,
        onLetterSpacingChanged = viewModel::setLetterSpacing,
        onTextColorEnabledChanged = viewModel::setTextColorEnabled,
        onControlCenterClockColorChange = viewModel::setControlCenterClockColorText,
        onFinishControlCenterClockTextColorEditing = viewModel::finishControlCenterClockColorEditing,
        onTextBoldChanged = viewModel::setTextBold,
        onQsRoundCornerChanged = viewModel::setQsRoundCorner,
        onQsHeadUpRoundCornerRadiusChanged = viewModel::setQsHeadUpRoundCornerRadius,
        onQsTileRoundCornerRadiusChanged = viewModel::setQsTileRoundCornerRadius,
        onCustomQsColorChanged = viewModel::setCustomQsColor,
        onCustomQsActiveColorTextChanged = viewModel::setCustomQsActiveColorText,
        onCustomQsActiveColorEditingFinished = viewModel::finishCustomQsActiveColorEditing,
        onCustomLabelColorChanged = viewModel::setCustomLabelColor,
        onCustomLabelActiveColorTextChanged = viewModel::setCustomLabelActiveColorText,
        onCustomLabelActiveColorEditingFinished = viewModel::finishCustomLabelActiveColorEditing,
        onCustomSecondLabelColorChanged = viewModel::setCustomSecondLabelColor,
        onCustomSecondLabelActiveColorTextChanged = viewModel::setCustomSecondLabelActiveColorText,
        onCustomSecondLabelActiveColorEditingFinished = viewModel::finishCustomSecondLabelActiveColorEditing,
        onNoTileLabelsChanged = viewModel::setNoTileLabels,
        onCustomQsColorSwitchChanged = viewModel::setCustomQsColorSwitch,
        onNotificationCenterBlurEnabledChanged = viewModel::setNotificationCenterBlurEnabled,
        onNotificationCenterBlurPercentChanged = viewModel::setNotificationCenterBlurPercent,
        onBrightnessSliderPercentageChanged = viewModel::setBrightnessSliderPercentageEnabled,
        onVolumeSliderPercentageChanged = viewModel::setVolumeSliderPercentageEnabled,
        onExpandQsPanelPortraitChanged = viewModel::setExpandQsPanelPortrait,
        onQsPanelWidthPercentChanged = viewModel::setQsPanelWidthPercent,
        onQsTileColumnsChanged = viewModel::setQsTileColumns,
        onCustomizeSliderStyleChanged = viewModel::setCustomizeSliderStyle,
        onSliderStyleValueChanged = viewModel::setSliderStyleValue
    )

    if (uiState.showFormatHelpDialog) {
        FormatHelpDialog(
            onDismiss = viewModel::dismissFormatHelpDialog,
            onCopyExample = {
                viewModel.dismissFormatHelpDialog()
                copyDateFormatExample()
            }
        )
    }

    if (uiState.showSaveSuccessDialog) {
        ZToolDialog(
            onDismissRequest = viewModel::dismissSaveSuccessDialog,
            title = { Text(stringResource(R.string.save_success_title)) },
            text = { Text(stringResource(R.string.date_format_saved_message)) },
            confirmButton = {
                ZToolTextButton(
                    onClick = viewModel::dismissSaveSuccessDialog,
                    text = stringResource(R.string.confirm)
                )
            }
        )
    }
}

private class ControlCenterSettingsViewModelFactory(
    private val repository: ControlCenterSettingsRepository
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ControlCenterSettingsViewModel::class.java)) {
            return ControlCenterSettingsViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}

@Composable
private fun ControlCenterSettingsScreen(
    title: String,
    state: ControlCenterSettingsUiState,
    onBack: () -> Unit,
    onCustomDateChanged: (Boolean) -> Unit,
    onDateFormatChanged: (String) -> Unit,
    onSaveDateFormat: () -> Unit,
    onShowFormatHelp: () -> Unit,
    onTextSizeEnabledChanged: (Boolean) -> Unit,
    onTextSizeChanged: (Float) -> Unit,
    onLetterSpacingEnabledChanged: (Boolean) -> Unit,
    onLetterSpacingChanged: (Float) -> Unit,
    onTextColorEnabledChanged: (Boolean) -> Unit,
    onTextBoldChanged: (Boolean) -> Unit,
    onQsRoundCornerChanged: (Boolean) -> Unit,
    onQsHeadUpRoundCornerRadiusChanged: (Int) -> Unit,
    onQsTileRoundCornerRadiusChanged: (Int) -> Unit,
    onCustomQsColorChanged: (Boolean) -> Unit,
    onCustomQsActiveColorTextChanged: (String) -> Unit,
    onCustomQsActiveColorEditingFinished: () -> Unit,
    onCustomLabelColorChanged: (Boolean) -> Unit,
    onCustomLabelActiveColorTextChanged: (String) -> Unit,
    onCustomLabelActiveColorEditingFinished: () -> Unit,
    onCustomSecondLabelColorChanged: (Boolean) -> Unit,
    onCustomSecondLabelActiveColorTextChanged: (String) -> Unit,
    onCustomSecondLabelActiveColorEditingFinished: () -> Unit,
    onNoTileLabelsChanged: (Boolean) -> Unit,
    onCustomQsColorSwitchChanged: (Boolean) -> Unit,
    onNotificationCenterBlurEnabledChanged: (Boolean) -> Unit,
    onNotificationCenterBlurPercentChanged: (Int) -> Unit,
    onControlCenterClockColorChange: (String) -> Unit,
    onFinishControlCenterClockTextColorEditing: () -> Unit,
    onBrightnessSliderPercentageChanged: (Boolean) -> Unit,
    onVolumeSliderPercentageChanged: (Boolean) -> Unit,
    onExpandQsPanelPortraitChanged: (Boolean) -> Unit,
    onQsPanelWidthPercentChanged: (Int) -> Unit,
    onQsTileColumnsChanged: (Int) -> Unit,
    onCustomizeSliderStyleChanged: (Boolean) -> Unit,
    onSliderStyleValueChanged: (Boolean) -> Unit,
) {
    ZToolScaffold(
        topBar = {
            ZToolTopAppBar(
                title = title,
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = null
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 960.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 24.dp)
            ) {
                ZToolSettingsList(
                    sections = controlCenterSettingsSections(
                        state = state,
                        onCustomDateChanged = onCustomDateChanged,
                        onDateFormatChanged = onDateFormatChanged,
                        onSaveDateFormat = onSaveDateFormat,
                        onShowFormatHelp = onShowFormatHelp,
                        onTextSizeEnabledChanged = onTextSizeEnabledChanged,
                        onTextSizeChanged = onTextSizeChanged,
                        onLetterSpacingEnabledChanged = onLetterSpacingEnabledChanged,
                        onLetterSpacingChanged = onLetterSpacingChanged,
                        onTextColorEnabledChanged = onTextColorEnabledChanged,
                        onTextBoldChanged = onTextBoldChanged,
                        onQsRoundCornerChanged = onQsRoundCornerChanged,
                        onQsHeadUpRoundCornerRadiusChanged = onQsHeadUpRoundCornerRadiusChanged,
                        onQsTileRoundCornerRadiusChanged = onQsTileRoundCornerRadiusChanged,
                        onCustomQsColorChanged = onCustomQsColorChanged,
                        onCustomQsActiveColorTextChanged = onCustomQsActiveColorTextChanged,
                        onCustomQsActiveColorEditingFinished = onCustomQsActiveColorEditingFinished,
                        onCustomLabelColorChanged = onCustomLabelColorChanged,
                        onCustomLabelActiveColorTextChanged = onCustomLabelActiveColorTextChanged,
                        onCustomLabelActiveColorEditingFinished = onCustomLabelActiveColorEditingFinished,
                        onCustomSecondLabelColorChanged = onCustomSecondLabelColorChanged,
                        onCustomSecondLabelActiveColorTextChanged = onCustomSecondLabelActiveColorTextChanged,
                        onCustomSecondLabelActiveColorEditingFinished = onCustomSecondLabelActiveColorEditingFinished,
                        onNoTileLabelsChanged = onNoTileLabelsChanged,
                        onCustomQsColorSwitchChanged = onCustomQsColorSwitchChanged,
                        onNotificationCenterBlurEnabledChanged = onNotificationCenterBlurEnabledChanged,
                        onNotificationCenterBlurPercentChanged = onNotificationCenterBlurPercentChanged,
                        onFinishControlCenterClockTextColorEditing = onFinishControlCenterClockTextColorEditing,
                        onControlCenterClockColorChange = onControlCenterClockColorChange,
                        onBrightnessSliderPercentageChanged = onBrightnessSliderPercentageChanged,
                        onVolumeSliderPercentageChanged = onVolumeSliderPercentageChanged,
                        onExpandQsPanelPortraitChanged = onExpandQsPanelPortraitChanged,
                        onQsPanelWidthPercentChanged = onQsPanelWidthPercentChanged,
                        onQsTileColumnsChanged = onQsTileColumnsChanged,
                        onCustomizeSliderStyleChanged = onCustomizeSliderStyleChanged,
                        onSliderStyleValueChanged = onSliderStyleValueChanged,
                    ),
                    bottomPadding = 96.dp
                )
            }
        }
    }
}

@Composable
private fun controlCenterSettingsSections(
    state: ControlCenterSettingsUiState,
    onCustomDateChanged: (Boolean) -> Unit,
    onDateFormatChanged: (String) -> Unit,
    onSaveDateFormat: () -> Unit,
    onShowFormatHelp: () -> Unit,
    onTextSizeEnabledChanged: (Boolean) -> Unit,
    onTextSizeChanged: (Float) -> Unit,
    onLetterSpacingEnabledChanged: (Boolean) -> Unit,
    onLetterSpacingChanged: (Float) -> Unit,
    onTextColorEnabledChanged: (Boolean) -> Unit,
    onFinishControlCenterClockTextColorEditing: () -> Unit,
    onControlCenterClockColorChange: (String) -> Unit,
    onTextBoldChanged: (Boolean) -> Unit,
    onQsRoundCornerChanged: (Boolean) -> Unit,
    onQsHeadUpRoundCornerRadiusChanged: (Int) -> Unit,
    onQsTileRoundCornerRadiusChanged: (Int) -> Unit,
    onCustomQsColorChanged: (Boolean) -> Unit,
    onCustomQsActiveColorTextChanged: (String) -> Unit,
    onCustomQsActiveColorEditingFinished: () -> Unit,
    onCustomLabelColorChanged: (Boolean) -> Unit,
    onCustomLabelActiveColorTextChanged: (String) -> Unit,
    onCustomLabelActiveColorEditingFinished: () -> Unit,
    onCustomSecondLabelColorChanged: (Boolean) -> Unit,
    onCustomSecondLabelActiveColorTextChanged: (String) -> Unit,
    onCustomSecondLabelActiveColorEditingFinished: () -> Unit,
    onNoTileLabelsChanged: (Boolean) -> Unit,
    onCustomQsColorSwitchChanged: (Boolean) -> Unit,
    onNotificationCenterBlurEnabledChanged: (Boolean) -> Unit,
    onNotificationCenterBlurPercentChanged: (Int) -> Unit,
    onVolumeSliderPercentageChanged: (Boolean) -> Unit,
    onBrightnessSliderPercentageChanged: (Boolean) -> Unit,
    onExpandQsPanelPortraitChanged: (Boolean) -> Unit,
    onQsPanelWidthPercentChanged: (Int) -> Unit,
    onQsTileColumnsChanged: (Int) -> Unit,
    onCustomizeSliderStyleChanged: (Boolean) -> Unit,
    onSliderStyleValueChanged: (Boolean) -> Unit,
): List<SettingSection> {
    return listOf(
        SettingSection(
            title = stringResource(R.string.notification_center_background),
            items = buildList {
                add(
                    SettingItem.Switch(
                        title = stringResource(R.string.notification_center_blur_title),
                        summary = stringResource(R.string.notification_center_blur_summary),
                        checked = state.notificationCenterBlurEnabled,
                        onCheckedChange = onNotificationCenterBlurEnabledChanged
                    )
                )
                if (state.notificationCenterBlurEnabled) {
                    add(
                        SettingItem.Slider(
                            title = stringResource(R.string.notification_center_blur_strength_title),
                            summary = stringResource(R.string.notification_center_blur_strength_summary),
                            value = state.notificationCenterBlurPercent.toFloat(),
                            valueText = stringResource(
                                R.string.percent_unit,
                                state.notificationCenterBlurPercent
                            ),
                            valueRange = 0f..100f,
                            steps = 19,
                            onValueChange = { onNotificationCenterBlurPercentChanged(snapToAccuratePercent(it)) }
                        )
                    )
                }
            }
        ),
        SettingSection(
            title = stringResource(R.string.control_center_tiles),
            items = buildList {
                add(
                    SettingItem.Switch(
                        title = stringResource(R.string.custom_control_center_tile_radius),
                        summary = stringResource(R.string.custom_control_center_tile_radius_summary),
                        checked = state.qsRoundCorner,
                        onCheckedChange = onQsRoundCornerChanged,
                    )
                )
                add(
                    SettingItem.Custom(
                        content = {
                            QsRoundCornerRadius(
                                state = state,
                                onQsHeadUpRoundCornerRadiusChanged = onQsHeadUpRoundCornerRadiusChanged,
                                onQsTileRoundCornerRadiusChanged = onQsTileRoundCornerRadiusChanged
                            )
                        }
                    )
                )
                add(
                    SettingItem.Switch(
                        title = stringResource(R.string.show_brightness_slider_percentage),
                        checked = state.brightnessSliderPercentageEnabled,
                        onCheckedChange = onBrightnessSliderPercentageChanged
                    )
                )
                add(
                    SettingItem.Switch(
                        title = stringResource(R.string.show_volume_slider_percentage),
                        checked = state.volumeSliderPercentageEnabled,
                        onCheckedChange = onVolumeSliderPercentageChanged
                    )
                )
                add(
                    SettingItem.Switch(
                        title = stringResource(R.string.custom_qs_color_general_switch),
                        checked = state.customQsColorGeneralSwitch,
                        onCheckedChange = onCustomQsColorSwitchChanged
                    )
                )
                if (state.customQsColorGeneralSwitch) {
                    add(
                        SettingItem.Switch(
                            title = stringResource(R.string.custom_qs_color_title),
                            summary = stringResource(R.string.custom_qs_color_summary),
                            checked = state.customQsColor,
                            onCheckedChange = onCustomQsColorChanged
                        )
                    )
                    if (state.customQsColor) {
                        add(
                            SettingItem.Custom(
                                content = {
                                    ZToolArgbColorTextFieldRow(
                                        label = stringResource(R.string.custom_qs_active_color_title),
                                        value = state.customQsActiveColorText,
                                        onValueChange = onCustomQsActiveColorTextChanged,
                                        defaultText = "BFADD8E6",
                                        summary = stringResource(R.string.custom_qs_active_color_summary),
                                        errorText = stringResource(R.string.argb_color_input_error),
                                        onEditingFinished = onCustomQsActiveColorEditingFinished
                                    )
                                }
                            )
                        )
                    }
                    add(
                        SettingItem.Switch(
                            title = stringResource(R.string.custom_label_color_title),
                            summary = stringResource(R.string.custom_label_color_summary),
                            checked = state.customLabelColor,
                            onCheckedChange = onCustomLabelColorChanged
                        )
                    )
                    if (state.customLabelColor) {
                        add(
                            SettingItem.Custom(
                                content = {
                                    ZToolArgbColorTextFieldRow(
                                        label = stringResource(R.string.custom_label_active_color_title),
                                        value = state.customLabelActiveColorText,
                                        onValueChange = onCustomLabelActiveColorTextChanged,
                                        defaultText = "FFFFFFFF",
                                        summary = stringResource(R.string.custom_label_active_color_summary),
                                        errorText = stringResource(R.string.argb_color_input_error),
                                        onEditingFinished = onCustomLabelActiveColorEditingFinished
                                    )
                                }
                            )
                        )
                    }
                    add(
                        SettingItem.Switch(
                            title = stringResource(R.string.custom_second_label_color_title),
                            summary = stringResource(R.string.custom_second_label_color_summary),
                            checked = state.customSecondLabelColor,
                            onCheckedChange = onCustomSecondLabelColorChanged
                        )
                    )
                    if (state.customSecondLabelColor) {
                        add(
                            SettingItem.Custom(
                                content = {
                                    ZToolArgbColorTextFieldRow(
                                        label = stringResource(R.string.custom_second_label_active_color_title),
                                        value = state.customSecondLabelActiveColorText,
                                        onValueChange = onCustomSecondLabelActiveColorTextChanged,
                                        defaultText = "BFFFFFFF",
                                        summary = stringResource(R.string.custom_second_label_active_color_summary),
                                        errorText = stringResource(R.string.argb_color_input_error),
                                        onEditingFinished = onCustomSecondLabelActiveColorEditingFinished
                                    )
                                }
                            )
                        )
                    }
                    add(
                        SettingItem.Switch(
                            title = stringResource(R.string.control_center_no_tile_labels_title),
                            summary = stringResource(R.string.control_center_no_tile_labels_summary),
                            checked = state.noTileLabels,
                            onCheckedChange = onNoTileLabelsChanged
                        )
                    )
                }
            }
        ),
        SettingSection(
            title = stringResource(R.string.ControllerDate),
            items = listOf(
                SettingItem.Custom(
                    content = {
                        CustomDateSettingsContent(
                            state = state,
                            onCustomDateChanged = onCustomDateChanged,
                            onDateFormatChanged = onDateFormatChanged,
                            onSaveDateFormat = onSaveDateFormat,
                            onShowFormatHelp = onShowFormatHelp,
                            onTextSizeEnabledChanged = onTextSizeEnabledChanged,
                            onTextSizeChanged = onTextSizeChanged,
                            onLetterSpacingEnabledChanged = onLetterSpacingEnabledChanged,
                            onLetterSpacingChanged = onLetterSpacingChanged,
                            onTextColorEnabledChanged = onTextColorEnabledChanged,
                            onTextBoldChanged = onTextBoldChanged,
                            onFinishControlCenterClockTextColorEditing = onFinishControlCenterClockTextColorEditing,
                            onControlCenterClockColorChange = onControlCenterClockColorChange
                        )
                    }
                )
            )
        ),
        SettingSection(
            title = stringResource(R.string.customize_slider_style_title),
            items = buildList {
                add(
                    SettingItem.Switch(
                        title = stringResource(R.string.customize_slider_style_title),
                        summary = stringResource(R.string.customize_slider_style_summary),
                        checked = state.customizeSliderStyle,
                        onCheckedChange = onCustomizeSliderStyleChanged,
                        enabled = !state.sliderStyleForcedByQsPanel
                    )
                )
                if (state.sliderStyleForcedByQsPanel) {
                    add(
                        SettingItem.Custom(
                            content = {
                                Text(
                                    text = stringResource(R.string.slider_style_forced_by_qs_panel),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = LocalZToolColorScheme.current.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp)
                                )
                            }
                        )
                    )
                }
                if (state.customizeSliderStyle) {
                    add(
                        SettingItem.Custom(
                            content = {
                                SliderStyleDirectionRow(
                                    isVertical = state.sliderStyleIsVertical,
                                    enabled = !state.sliderStyleForcedByQsPanel,
                                    onDirectionChanged = onSliderStyleValueChanged
                                )
                            }
                        )
                    )
                }
            }
        ),
        SettingSection(
                title = stringResource(R.string.expand_qs_panel_portrait_title),
                items = buildList {
                    add(
                        SettingItem.Switch(
                            title = stringResource(R.string.expand_qs_panel_portrait_title),
                            summary = stringResource(R.string.expand_qs_panel_portrait_summary),
                            checked = state.expandQsPanelPortrait,
                            onCheckedChange = onExpandQsPanelPortraitChanged
                        )
                    )
                    if (state.expandQsPanelPortrait) {
                        add(
                            SettingItem.Slider(
                                title = stringResource(R.string.qs_panel_width_percent_title),
                                summary = stringResource(R.string.qs_panel_width_percent_summary),
                                value = state.qsPanelWidthPercent.toFloat(),
                                valueText = stringResource(
                                    R.string.percent_unit,
                                    state.qsPanelWidthPercent
                                ),
                                valueRange = 0f..100f,
                                steps = 19,
                                onValueChange = { onQsPanelWidthPercentChanged(snapToAccuratePercent(it)) }
                            )
                        )
                        add(
                            SettingItem.Slider(
                                title = stringResource(R.string.qs_tile_columns_title),
                                summary = stringResource(R.string.qs_tile_columns_summary),
                                value = state.qsTileColumns.toFloat(),
                                valueText = state.qsTileColumns.toString(),
                                valueRange = 0f..10f,
                                steps = 9,
                                onValueChange = { onQsTileColumnsChanged(it.toInt()) }
                            )
                        )
                    }
                }
            )
        )
}

@Composable
private fun SliderStyleDirectionRow(
    isVertical: Boolean,
    enabled: Boolean,
    onDirectionChanged: (Boolean) -> Unit
) {
    val options = listOf(
        false to stringResource(R.string.slider_style_horizontal),
        true to stringResource(R.string.slider_style_vertical)
    )
    val selectedLabel = options.first { it.first == isVertical }.second

    ZToolPopupMenuSettingRow(
        title = stringResource(R.string.slider_style_direction_title),
        value = selectedLabel,
        options = options,
        optionLabel = { it.second },
        onOptionSelected = { (vertical, _) -> onDirectionChanged(vertical) },
        enabled = enabled
    )
}

@Composable
private fun QsRoundCornerRadius(
    state: ControlCenterSettingsUiState,
    onQsHeadUpRoundCornerRadiusChanged: (Int) -> Unit,
    onQsTileRoundCornerRadiusChanged: (Int) -> Unit
) {
    if (state.qsRoundCorner) {
        Column(modifier = Modifier
            .fillMaxWidth()) {
            ZToolSliderRow(
                title = stringResource(R.string.head_up_corner_radius),
                value = state.qsHeadUpRoundCornerRadius.toFloat(),
                onValueChange = {
                    onQsHeadUpRoundCornerRadiusChanged(
                        snapToAccurateRadius(it)
                    )
                },
                steps = QS_ROUND_CORNER_STEPS,
                valueRange = 0.0f..96.0f,
                valueText = state.qsHeadUpRoundCornerRadius.toString() + "dp",
            )
            ZToolSliderRow(
                title = stringResource(R.string.normal_tile_corner_radius),
                value = state.qsTileRoundCornerRadius.toFloat(),
                onValueChange = {
                    onQsTileRoundCornerRadiusChanged(
                        snapToAccurateRadius(it)
                    )
                },
                steps = QS_ROUND_CORNER_STEPS,
                valueRange = 0.0f..96.0f,
                valueText = state.qsTileRoundCornerRadius.toString() + "dp"
            )
        }
    }
}

@Composable
private fun CustomDateSettingsContent(
    state: ControlCenterSettingsUiState,
    onCustomDateChanged: (Boolean) -> Unit,
    onDateFormatChanged: (String) -> Unit,
    onSaveDateFormat: () -> Unit,
    onShowFormatHelp: () -> Unit,
    onTextSizeEnabledChanged: (Boolean) -> Unit,
    onTextSizeChanged: (Float) -> Unit,
    onLetterSpacingEnabledChanged: (Boolean) -> Unit,
    onLetterSpacingChanged: (Float) -> Unit,
    onTextColorEnabledChanged: (Boolean) -> Unit,
    onControlCenterClockColorChange: (String) -> Unit,
    onFinishControlCenterClockTextColorEditing: () -> Unit,
    onTextBoldChanged: (Boolean) -> Unit
) {
    ZToolSwitchRow(
        title = stringResource(R.string.CustomDateSettingTitle),
        summary = stringResource(R.string.CustomDateSettingSummary),
        checked = state.customDate,
        onCheckedChange = onCustomDateChanged
    )
    if (state.customDate) {
        IconButton(
            onClick = onShowFormatHelp,
            modifier = Modifier.padding(start = 12.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.Info,
                contentDescription = stringResource(R.string.tooltip_content_description),
                tint = LocalZToolColorScheme.current.onSurfaceVariant
            )
        }
        CustomDateConfig(
            state = state,
            dateFormat = state.dateFormat,
            datePreview = state.datePreview,
            textSizeEnabled = state.textSizeEnabled,
            textSize = state.textSize,
            letterSpacingEnabled = state.letterSpacingEnabled,
            letterSpacing = state.letterSpacing,
            textColorEnabled = state.controlCenterTextColorEnabled,
            textColor = state.controlCenterTextColor,
            textBold = state.textBold,
            onDateFormatChanged = onDateFormatChanged,
            onSaveDateFormat = onSaveDateFormat,
            onTextSizeEnabledChanged = onTextSizeEnabledChanged,
            onTextSizeChanged = onTextSizeChanged,
            onLetterSpacingEnabledChanged = onLetterSpacingEnabledChanged,
            onLetterSpacingChanged = onLetterSpacingChanged,
            onTextColorEnabledChanged = onTextColorEnabledChanged,
            onControlCenterClockColorChange = onControlCenterClockColorChange,
            onFinishControlCenterClockTextColorEditing = onFinishControlCenterClockTextColorEditing,
            onTextBoldChanged = onTextBoldChanged
        )
    }
}

@Composable
private fun CustomDateConfig(
    state: ControlCenterSettingsUiState,
    dateFormat: String,
    datePreview: String,
    textSizeEnabled: Boolean,
    textSize: Float,
    letterSpacingEnabled: Boolean,
    letterSpacing: Float,
    textColorEnabled: Boolean,
    textColor: Int,
    textBold: Boolean,
    onDateFormatChanged: (String) -> Unit,
    onSaveDateFormat: () -> Unit,
    onTextSizeEnabledChanged: (Boolean) -> Unit,
    onTextSizeChanged: (Float) -> Unit,
    onLetterSpacingEnabledChanged: (Boolean) -> Unit,
    onLetterSpacingChanged: (Float) -> Unit,
    onTextColorEnabledChanged: (Boolean) -> Unit,
    onControlCenterClockColorChange: (String) -> Unit,
    onFinishControlCenterClockTextColorEditing: () -> Unit,
    onTextBoldChanged: (Boolean) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ZToolTextInputRow(
                value = dateFormat,
                onValueChange = onDateFormatChanged,
                modifier = Modifier.weight(1f),
                label = stringResource(R.string.ControllerDateFormat),
                horizontalPadding = 0.dp,
                singleLine = true
            )
            Spacer(modifier = Modifier.width(12.dp))
            ZToolButton(onClick = onSaveDateFormat) {
                Text(stringResource(R.string.save))
            }
        }
        Text(
            text = datePreview,
            style = MaterialTheme.typography.bodyMedium,
            color = LocalZToolColorScheme.current.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp)
        )

        SliderSettingRow(
            title = stringResource(R.string.custom_clock_text_size_title),
            valueLabel = stringResource(R.string.sp_unit, textSize),
            enabled = textSizeEnabled,
            value = textSize,
            valueRange = 10f..30f,
            steps = 39,
            onEnabledChanged = onTextSizeEnabledChanged,
            onValueChanged = onTextSizeChanged
        )
        SliderSettingRow(
            title = stringResource(R.string.custom_clock_letter_spacing_title),
            valueLabel = "%.1f".format(letterSpacing),
            enabled = letterSpacingEnabled,
            value = letterSpacing,
            valueRange = 0f..2f,
            steps = 19,
            onEnabledChanged = onLetterSpacingEnabledChanged,
            onValueChanged = onLetterSpacingChanged
        )
        ZToolSwitchRow(
            title = stringResource(R.string.custom_clock_text_color_title),
            summary = "#%08X".format(textColor),
            checked = textColorEnabled,
            onCheckedChange = onTextColorEnabledChanged,
            padding = 0.dp
        )
        if (textColorEnabled) {
            ZToolArgbColorTextFieldRow(
                label = stringResource(R.string.select_font_color_title),
                value = state.controlCenterTextColorText,
                onValueChange = onControlCenterClockColorChange,
                defaultText = "FFFFFFFF",
                summary = stringResource(R.string.custom_qs_active_color_summary),
                errorText = stringResource(R.string.argb_color_input_error),
                onEditingFinished = onFinishControlCenterClockTextColorEditing
            )
        }
        ZToolSwitchRow(
            title = stringResource(R.string.custom_clock_text_bold_title),
            summary = stringResource(R.string.useBoldDate),
            checked = textBold,
            onCheckedChange = onTextBoldChanged,
            padding = 0.dp
        )
    }
}

@Composable
private fun SliderSettingRow(
    title: String,
    valueLabel: String,
    enabled: Boolean,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
    onEnabledChanged: (Boolean) -> Unit,
    onValueChanged: (Float) -> Unit
) {
    ZToolSwitchRow(
        title = title,
        checked = enabled,
        onCheckedChange = onEnabledChanged,
        padding = 0.dp
    )
    if (enabled) {
        ZToolSliderRow(
            value = value,
            valueText = valueLabel,
            onValueChange = onValueChanged,
            enabled = enabled,
            valueRange = valueRange,
            steps = steps,
            horizontalPadding = 0.dp,
            modifier = Modifier.padding(horizontal = 0.dp)
        )
    }
}

@Composable
private fun FormatHelpDialog(
    onDismiss: () -> Unit,
    onCopyExample: () -> Unit
) {
    ZToolQuickHelpDialog(
        title = stringResource(R.string.date_format_help_title),
        summary = stringResource(R.string.date_format_quick_help_summary),
        quickLabel = stringResource(R.string.quick_help_lookup_title),
        examplesLabel = stringResource(R.string.quick_help_examples_title),
        items = listOf(
            QuickHelpItem("yyyy", stringResource(R.string.format_help_year)),
            QuickHelpItem("MM", stringResource(R.string.format_help_month)),
            QuickHelpItem("dd", stringResource(R.string.format_help_day)),
            QuickHelpItem("E", stringResource(R.string.format_help_weekday)),
            QuickHelpItem("N", stringResource(R.string.format_help_lunar_date)),
            QuickHelpItem("J", stringResource(R.string.format_help_solar_term))
        ),
        examples = listOf(
            QuickHelpExample(
                stringResource(R.string.format_help_date_pattern_full),
                stringResource(R.string.format_help_date_example_full)
            ),
            QuickHelpExample(
                stringResource(R.string.format_help_date_pattern_short),
                stringResource(R.string.format_help_date_example_short)
            ),
            QuickHelpExample(
                stringResource(R.string.format_help_date_pattern_time),
                stringResource(R.string.format_help_date_example_time)
            )
        ),
        note = stringResource(R.string.date_format_quick_help_note),
        onDismiss = onDismiss,
        onCopyExample = onCopyExample,
        copyButtonText = stringResource(R.string.copy_example_button),
        confirmButtonText = stringResource(R.string.confirm)
    )
}
