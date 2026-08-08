package com.qimian233.ztool.settingactivity.systemui.statusbar

import android.content.ClipData
import android.content.ClipboardManager
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
import androidx.compose.material.icons.rounded.Refresh
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
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import com.qimian233.ztool.R
import com.qimian233.ztool.data.keys.ScopeKeys
import com.qimian233.ztool.data.systemui.StatusBarSettingsRepository
import com.qimian233.ztool.ui.components.QuickHelpExample
import com.qimian233.ztool.ui.components.QuickHelpItem
import com.qimian233.ztool.ui.components.SettingItem
import com.qimian233.ztool.ui.components.SettingSection
import com.qimian233.ztool.ui.components.ZToolArgbColorTextFieldRow
import com.qimian233.ztool.ui.components.ZToolButton
import com.qimian233.ztool.ui.components.ZToolDialog
import com.qimian233.ztool.ui.components.ZToolExtendedFloatingActionButton
import com.qimian233.ztool.ui.components.ZToolPopupMenuSettingRow
import com.qimian233.ztool.ui.components.ZToolQuickHelpDialog
import com.qimian233.ztool.ui.components.ZToolScaffold
import com.qimian233.ztool.ui.components.ZToolSettingsList
import com.qimian233.ztool.ui.components.ZToolSliderRow
import com.qimian233.ztool.ui.components.ZToolSwitchRow
import com.qimian233.ztool.ui.components.ZToolTextButton
import com.qimian233.ztool.ui.components.ZToolTextInputRow
import com.qimian233.ztool.ui.components.ZToolTopAppBar
import com.qimian233.ztool.viewmodel.StatusBarSettingsUiState
import com.qimian233.ztool.viewmodel.StatusBarSettingsViewModel

@Composable
fun StatusBarSettingsRoute(
    title: String,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val owner = LocalViewModelStoreOwner.current
        ?: error("StatusBarSettingsRoute requires a ViewModelStoreOwner")
    val viewModel = remember(owner) {
        ViewModelProvider(
            owner,
            StatusBarSettingsViewModelFactory(
                StatusBarSettingsRepository(context.applicationContext)
            )
        )[StatusBarSettingsViewModel::class.java]
    }

    LaunchedEffect(viewModel) {
        viewModel.loadSettings()
    }

    val clockFormatExampleString = stringResource(R.string.clock_format_example)
    val clockFormatSampleString = stringResource(R.string.clock_format_sample)

    fun copyClockFormatExample() {
        val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText(
            clockFormatExampleString,
            clockFormatSampleString
        )
        clipboard.setPrimaryClip(clip)
        Toast.makeText(context, R.string.example_copied_message, Toast.LENGTH_SHORT).show()
    }

    val uiState by viewModel.uiState.collectAsState()

    StatusBarSettingsScreen(
        title = title,
        state = uiState,
        onBack = onBack,
        onDisplaySecondsChanged = viewModel::setDisplaySeconds,
        onCustomClockChanged = viewModel::setCustomClock,
        onClockFormatChanged = viewModel::setClockFormat,
        onSaveClockFormat = viewModel::saveClockFormat,
        onShowFormatHelp = viewModel::showFormatHelpDialog,
        onTextSizeEnabledChanged = viewModel::setTextSizeEnabled,
        onTextSizeChanged = viewModel::setTextSize,
        onLetterSpacingEnabledChanged = viewModel::setLetterSpacingEnabled,
        onLetterSpacingChanged = viewModel::setLetterSpacing,
        onTextColorEnabledChanged = viewModel::setTextColorEnabled,
        onClockTextColorChanged = viewModel::setTextColorText,
        onClockTextColorEditingFinished = viewModel::finishTextColorEditing,
        onTextBoldChanged = viewModel::setTextBold,
        onNotificationIconLimitChanged = { option ->
            if (!viewModel.setNotificationIconLimit(option)) {
                Toast.makeText(context, R.string.save_failed_message, Toast.LENGTH_SHORT).show()
            }
        },
        onNativeNotificationIconChanged = viewModel::setNativeNotificationIcon,
        onNetworkSpeedSizeChanged = viewModel::setNetworkSpeedSize,
        onNetworkSpeedDoubleLayerChanged = viewModel::setNetworkSpeedDoubleLayer,
        onNetworkSpeedRefreshEnabledChanged = viewModel::setNetworkSpeedRefreshEnabled,
        onNetworkSpeedRefreshIntervalChanged = viewModel::setNetworkSpeedRefreshInterval,
        onBatteryExternalChanged = viewModel::setBatteryExternal,
        onRestartScope = viewModel::showRestartDialog,
    )

    if (uiState.showRestartDialog) {
        val restartFailString = stringResource(R.string.restartFail)
        RestartScopeDialog(
            packageName = ScopeKeys.SYSTEM_UI.packageName,
            onConfirm = {
                viewModel.forceStopScope { success, error ->
                    if (success) {
                        Toast.makeText(context, R.string.restartSuccess, Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, restartFailString + error, Toast.LENGTH_SHORT).show()
                    }
                }
            },
            onDismiss = viewModel::dismissRestartDialog
        )
    }

    if (uiState.showFormatHelpDialog) {
        FormatHelpDialog(
            onDismiss = viewModel::dismissFormatHelpDialog,
            onCopyExample = {
                viewModel.dismissFormatHelpDialog()
                copyClockFormatExample()
            }
        )
    }

    if (uiState.showSaveSuccessDialog) {
        ZToolDialog(
            onDismissRequest = viewModel::dismissSaveSuccessDialog,
            title = { Text(stringResource(R.string.save_success_title)) },
            text = { Text(stringResource(R.string.clock_format_saved_message)) },
            confirmButton = {
                ZToolTextButton(onClick = viewModel::dismissSaveSuccessDialog, text = stringResource(R.string.confirm))
            }
        )
    }
}

private class StatusBarSettingsViewModelFactory(
    private val repository: StatusBarSettingsRepository
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(StatusBarSettingsViewModel::class.java)) {
            return StatusBarSettingsViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}

@Composable
private fun StatusBarSettingsScreen(
    title: String,
    state: StatusBarSettingsUiState,
    onBack: () -> Unit,
    onDisplaySecondsChanged: (Boolean) -> Unit,
    onCustomClockChanged: (Boolean) -> Unit,
    onClockFormatChanged: (String) -> Unit,
    onSaveClockFormat: () -> Unit,
    onShowFormatHelp: () -> Unit,
    onTextSizeEnabledChanged: (Boolean) -> Unit,
    onTextSizeChanged: (Float) -> Unit,
    onLetterSpacingEnabledChanged: (Boolean) -> Unit,
    onLetterSpacingChanged: (Float) -> Unit,
    onTextColorEnabledChanged: (Boolean) -> Unit,
    onClockTextColorChanged: (String) -> Unit,
    onClockTextColorEditingFinished: () -> Unit,
    onTextBoldChanged: (Boolean) -> Unit,
    onNotificationIconLimitChanged: (String) -> Unit,
    onNativeNotificationIconChanged: (Boolean) -> Unit,
    onNetworkSpeedSizeChanged: (Boolean) -> Unit,
    onNetworkSpeedDoubleLayerChanged: (Boolean) -> Unit,
    onNetworkSpeedRefreshEnabledChanged: (Boolean) -> Unit,
    onNetworkSpeedRefreshIntervalChanged: (Float) -> Unit,
    onBatteryExternalChanged: (Boolean) -> Unit,
    onRestartScope: () -> Unit,
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
        },
        floatingActionButton = {
            ZToolExtendedFloatingActionButton(
                onClick = onRestartScope,
                icon = { Icon(Icons.Rounded.Refresh, contentDescription = null) },
                text = { Text(stringResource(R.string.restart_yes)) }
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
                    sections = statusBarSettingsSections(
                        state = state,
                        onDisplaySecondsChanged = onDisplaySecondsChanged,
                        onCustomClockChanged = onCustomClockChanged,
                        onClockFormatChanged = onClockFormatChanged,
                        onSaveClockFormat = onSaveClockFormat,
                        onShowFormatHelp = onShowFormatHelp,
                        onTextSizeEnabledChanged = onTextSizeEnabledChanged,
                        onTextSizeChanged = onTextSizeChanged,
                        onLetterSpacingEnabledChanged = onLetterSpacingEnabledChanged,
                        onLetterSpacingChanged = onLetterSpacingChanged,
                        onTextColorEnabledChanged = onTextColorEnabledChanged,
                        onClockTextColorChanged = onClockTextColorChanged,
                        onClockTextColorEditingFinished = onClockTextColorEditingFinished,
                        onTextBoldChanged = onTextBoldChanged,
                        onNotificationIconLimitChanged = onNotificationIconLimitChanged,
                        onNativeNotificationIconChanged = onNativeNotificationIconChanged,
                        onNetworkSpeedSizeChanged = onNetworkSpeedSizeChanged,
                        onNetworkSpeedDoubleLayerChanged = onNetworkSpeedDoubleLayerChanged,
                        onNetworkSpeedRefreshEnabledChanged = onNetworkSpeedRefreshEnabledChanged,
                        onNetworkSpeedRefreshIntervalChanged = onNetworkSpeedRefreshIntervalChanged,
                        onBatteryExternalChanged = onBatteryExternalChanged
                    ),
                    bottomPadding = 96.dp
                )
            }
        }
    }
}

@Composable
private fun statusBarSettingsSections(
    state: StatusBarSettingsUiState,
    onDisplaySecondsChanged: (Boolean) -> Unit,
    onCustomClockChanged: (Boolean) -> Unit,
    onClockFormatChanged: (String) -> Unit,
    onSaveClockFormat: () -> Unit,
    onShowFormatHelp: () -> Unit,
    onTextSizeEnabledChanged: (Boolean) -> Unit,
    onTextSizeChanged: (Float) -> Unit,
    onLetterSpacingEnabledChanged: (Boolean) -> Unit,
    onLetterSpacingChanged: (Float) -> Unit,
    onTextColorEnabledChanged: (Boolean) -> Unit,
    onClockTextColorChanged: (String) -> Unit,
    onClockTextColorEditingFinished: () -> Unit,
    onTextBoldChanged: (Boolean) -> Unit,
    onNotificationIconLimitChanged: (String) -> Unit,
    onNativeNotificationIconChanged: (Boolean) -> Unit,
    onNetworkSpeedSizeChanged: (Boolean) -> Unit,
    onNetworkSpeedDoubleLayerChanged: (Boolean) -> Unit,
    onNetworkSpeedRefreshEnabledChanged: (Boolean) -> Unit,
    onNetworkSpeedRefreshIntervalChanged: (Float) -> Unit,
    onBatteryExternalChanged: (Boolean) -> Unit
): List<SettingSection> {
    val clockItems = buildList {
        add(
            SettingItem.Switch(
                title = stringResource(R.string.display_seconds_title),
                summary = stringResource(R.string.display_seconds_summary),
                checked = state.displaySeconds,
                onCheckedChange = onDisplaySecondsChanged
            )
        )
        add(
            SettingItem.Custom(
                content = {
                    ZToolSwitchRow(
                        title = stringResource(R.string.custom_clock_title),
                        summary = stringResource(R.string.custom_clock_summary),
                        checked = state.customClock,
                        onCheckedChange = onCustomClockChanged
                    )
                    if (state.customClock) {
                        IconButton(
                            onClick = onShowFormatHelp,
                            modifier = Modifier.padding(start = 12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Info,
                                contentDescription = null,
                                tint = LocalZToolColorScheme.current.onSurfaceVariant
                            )
                        }
                        CustomClockConfig(
                            clockFormat = state.clockFormat,
                            clockPreview = state.clockPreview,
                            textSizeEnabled = state.textSizeEnabled,
                            textSize = state.textSize,
                            letterSpacingEnabled = state.letterSpacingEnabled,
                            letterSpacing = state.letterSpacing,
                            textColorEnabled = state.textColorEnabled,
                            textColor = state.textColor,
                            textColorText = state.textColorText,
                            textBold = state.textBold,
                            onClockFormatChanged = onClockFormatChanged,
                            onSaveClockFormat = onSaveClockFormat,
                            onTextSizeEnabledChanged = onTextSizeEnabledChanged,
                            onTextSizeChanged = onTextSizeChanged,
                            onLetterSpacingEnabledChanged = onLetterSpacingEnabledChanged,
                            onLetterSpacingChanged = onLetterSpacingChanged,
                            onTextColorEnabledChanged = onTextColorEnabledChanged,
                            onClockTextColorChanged = onClockTextColorChanged,
                            onClockTextColorEditingFinished = onClockTextColorEditingFinished,
                            onTextBoldChanged = onTextBoldChanged
                        )
                    }
                }
            )
        )
    }

    return listOf(
        SettingSection(
            title = stringResource(R.string.status_bar_clock_settings_title),
            items = clockItems
        ),
        SettingSection(
            title = stringResource(R.string.status_bar_notification_settings_title),
            items = listOf(
                SettingItem.Custom(
                    content = {
                        ZToolPopupMenuSettingRow(
                            title = stringResource(R.string.notification_icon_limit_title),
                            summary = stringResource(R.string.notification_icon_limit_summary),
                            options = stringArrayResource(R.array.notify_num_size_options).toList(),
                            value = stringResource(R.string.notification_icon_number_to_show, state.notificationIconLimitOption),
                            optionLabel = { it },
                            onOptionSelected = onNotificationIconLimitChanged
                        )
                    }
                ),
                SettingItem.Switch(
                    title = stringResource(R.string.notification_icon_native_title),
                    summary = stringResource(R.string.notification_icon_native_summary),
                    checked = state.nativeNotificationIcon,
                    onCheckedChange = onNativeNotificationIconChanged
                )
            )
        ),
        SettingSection(
            title = stringResource(R.string.statusBarNetworkTitle),
            items = buildList {
                add(
                    SettingItem.Switch(
                        title = stringResource(R.string.statusBarNetworkSizeTitle),
                        summary = stringResource(R.string.statusBarNetworkSizeSummary),
                        checked = state.networkSpeedSize,
                        onCheckedChange = onNetworkSpeedSizeChanged
                    )
                )
                add(
                    SettingItem.Switch(
                        title = stringResource(R.string.statusBarNetworkSizeDoubleLayer),
                        summary = stringResource(R.string.statusBarNetworkSizeDoubleLayerSummary),
                        checked = state.networkSpeedDoubleLayer,
                        onCheckedChange = onNetworkSpeedDoubleLayerChanged
                    )
                )
                add(
                    SettingItem.Switch(
                        title = stringResource(R.string.statusBarNetworkRefreshTitle),
                        summary = stringResource(R.string.statusBarNetworkRefreshSummary),
                        checked = state.networkSpeedRefreshEnabled,
                        onCheckedChange = onNetworkSpeedRefreshEnabledChanged
                    )
                )
                if (state.networkSpeedRefreshEnabled) {
                    add(
                        SettingItem.Custom(
                            content = {
                                ZToolSliderRow(
                                    title = stringResource(R.string.statusBarNetworkRefreshIntervalTitle),
                                    value = state.networkSpeedRefreshInterval,
                                    onValueChange = onNetworkSpeedRefreshIntervalChanged,
                                    valueRange = 0f..10f,
                                    steps = 19,
                                    valueText = String.format("%.1f s", state.networkSpeedRefreshInterval),
                                    horizontalPadding = 24.dp,
                                    modifier = Modifier.padding(horizontal = 0.dp)
                                )
                            }
                        )
                    )
                }
            }
        ),
        SettingSection(
            title = stringResource(R.string.statusBarBatteryTitle),
            items = listOf(
                SettingItem.Switch(
                    title = stringResource(R.string.syatusBatteryExternalTitle),
                    summary = stringResource(R.string.syatusBatteryExternalSummary),
                    checked = state.batteryExternal,
                    onCheckedChange = onBatteryExternalChanged
                )
            )
        )
    )
}
@Composable
private fun CustomClockConfig(
    clockFormat: String,
    clockPreview: String,
    textSizeEnabled: Boolean,
    textSize: Float,
    letterSpacingEnabled: Boolean,
    letterSpacing: Float,
    textColorEnabled: Boolean,
    textColor: Int,
    textColorText: String,
    textBold: Boolean,
    onClockFormatChanged: (String) -> Unit,
    onSaveClockFormat: () -> Unit,
    onTextSizeEnabledChanged: (Boolean) -> Unit,
    onTextSizeChanged: (Float) -> Unit,
    onLetterSpacingEnabledChanged: (Boolean) -> Unit,
    onLetterSpacingChanged: (Float) -> Unit,
    onTextColorEnabledChanged: (Boolean) -> Unit,
    onClockTextColorChanged: (String) -> Unit,
    onClockTextColorEditingFinished: () -> Unit,
    onTextBoldChanged: (Boolean) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ZToolTextInputRow(
                value = clockFormat,
                onValueChange = onClockFormatChanged,
                modifier = Modifier.weight(1f),
                label = stringResource(R.string.clock_format_hint),
                singleLine = true,
                horizontalPadding = 0.dp
            )
            Spacer(modifier = Modifier.width(16.dp))
            ZToolButton(onClick = onSaveClockFormat) {
                Text(stringResource(R.string.save))
            }
        }
        Text(
            text = clockPreview,
            style = MaterialTheme.typography.bodyMedium,
            color = LocalZToolColorScheme.current.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp)
        )

        SliderSettingRow(
            title = stringResource(R.string.text_size_title),
            valueLabel = stringResource(R.string.sp_unit, textSize),
            enabled = textSizeEnabled,
            value = textSize,
            valueRange = 10f..30f,
            steps = 39,
            onEnabledChanged = onTextSizeEnabledChanged,
            onValueChanged = onTextSizeChanged
        )
        SliderSettingRow(
            title = stringResource(R.string.letter_spacing_title),
            valueLabel = "%.1f".format(letterSpacing),
            enabled = letterSpacingEnabled,
            value = letterSpacing,
            valueRange = 0f..2f,
            steps = 19,
            onEnabledChanged = onLetterSpacingEnabledChanged,
            onValueChanged = onLetterSpacingChanged
        )
        ZToolSwitchRow(
            title = stringResource(R.string.text_color_title),
            summary = "#%08X".format(textColor),
            checked = textColorEnabled,
            onCheckedChange = onTextColorEnabledChanged,
            padding = 0.dp
        )
        if (textColorEnabled) {
            ZToolArgbColorTextFieldRow(
                label = stringResource(R.string.select_font_color_title),
                value = textColorText,
                onValueChange = onClockTextColorChanged,
                defaultText = "FFFFFFFF",
                summary = stringResource(R.string.custom_qs_active_color_summary),
                errorText = stringResource(R.string.argb_color_input_error),
                onEditingFinished = onClockTextColorEditingFinished
            )
        }
        ZToolSwitchRow(
            title = stringResource(R.string.text_bold_title),
            summary = null,
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
            modifier = Modifier.padding(horizontal = 0.dp),
            horizontalPadding = 0.dp,
            verticalPadding = 0.dp
        )
    }
}

@Composable
private fun FormatHelpDialog(
    onDismiss: () -> Unit,
    onCopyExample: () -> Unit
) {
    ZToolQuickHelpDialog(
        title = stringResource(R.string.clock_format_help_title),
        summary = stringResource(R.string.clock_format_quick_help_summary),
        quickLabel = stringResource(R.string.quick_help_lookup_title),
        examplesLabel = stringResource(R.string.quick_help_examples_title),
        items = listOf(
            QuickHelpItem("HH", stringResource(R.string.format_help_hour_24)),
            QuickHelpItem("mm", stringResource(R.string.format_help_minute)),
            QuickHelpItem("ss", stringResource(R.string.format_help_second)),
            QuickHelpItem("E", stringResource(R.string.format_help_weekday)),
            QuickHelpItem("N", stringResource(R.string.format_help_lunar_date)),
            QuickHelpItem("T", stringResource(R.string.format_help_period))
        ),
        examples = listOf(
            QuickHelpExample(
                stringResource(R.string.format_help_clock_pattern_short),
                stringResource(R.string.format_help_clock_example_short)
            ),
            QuickHelpExample(
                stringResource(R.string.format_help_clock_pattern_seconds),
                stringResource(R.string.format_help_clock_example_seconds)
            ),
            QuickHelpExample(
                stringResource(R.string.format_help_clock_pattern_lunar),
                stringResource(R.string.format_help_clock_example_lunar)
            )
        ),
        note = stringResource(R.string.clock_format_quick_help_note),
        onDismiss = onDismiss,
        onCopyExample = onCopyExample,
        copyButtonText = stringResource(R.string.copy_example_button),
        confirmButtonText = stringResource(R.string.confirm)
    )
}

@Composable
private fun RestartScopeDialog(
    packageName: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    ZToolDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.restart_xp_title)) },
        text = {
            Text(
                stringResource(R.string.restart_xp_message_header) +
                    packageName +
                    stringResource(R.string.restart_xp_message)
            )
        },
        confirmButton = {
            ZToolTextButton(onClick = onConfirm, text = stringResource(R.string.restart_yes))
        },
        dismissButton = {
            ZToolTextButton(onClick = onDismiss, text = stringResource(R.string.restart_no), isPrimary = false)
        }
    )
}

