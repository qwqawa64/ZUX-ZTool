package com.qimian233.ztool.settingactivity.systemui.statusBarSetting

import android.content.ClipData
import android.content.ClipboardManager
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import com.qimian233.ztool.R
import com.qimian233.ztool.data.systemui.StatusBarSettingsRepository
import com.qimian233.ztool.ui.components.SettingItem
import com.qimian233.ztool.ui.components.SettingSection
import com.qimian233.ztool.ui.components.ZToolDialog
import com.qimian233.ztool.ui.components.QuickHelpExample
import com.qimian233.ztool.ui.components.QuickHelpItem
import com.qimian233.ztool.ui.components.ZToolPopupMenuSettingRow
import com.qimian233.ztool.ui.components.ZToolQuickHelpDialog
import com.qimian233.ztool.ui.components.ZToolScaffold
import com.qimian233.ztool.ui.components.ZToolSettingsList
import com.qimian233.ztool.ui.components.ZToolSwitchRow
import com.qimian233.ztool.ui.components.ZToolTopAppBar
import com.qimian233.ztool.ui.theme.ZToolTheme
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

    fun copyClockFormatExample() {
        val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText(
            context.getString(R.string.clock_format_example),
            context.getString(R.string.clock_format_sample)
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
        onPickTextColor = viewModel::showColorPickerDialog,
        onTextBoldChanged = viewModel::setTextBold,
        onNotificationIconLimitChanged = { option ->
            if (!viewModel.setNotificationIconLimit(option)) {
                Toast.makeText(context, R.string.save_failed_message, Toast.LENGTH_SHORT).show()
            }
        },
        onNativeNotificationIconChanged = viewModel::setNativeNotificationIcon,
        onNetworkSpeedSizeChanged = viewModel::setNetworkSpeedSize,
        onNetworkSpeedDoubleLayerChanged = viewModel::setNetworkSpeedDoubleLayer,
        onBatteryExternalChanged = viewModel::setBatteryExternal
    )

    if (uiState.showFormatHelpDialog) {
        FormatHelpDialog(
            onDismiss = viewModel::dismissFormatHelpDialog,
            onCopyExample = {
                viewModel.dismissFormatHelpDialog()
                copyClockFormatExample()
            }
        )
    }

    if (uiState.showColorPickerDialog) {
        ColorPickerDialog(
            onColorSelected = viewModel::setTextColor,
            onDismiss = viewModel::dismissColorPickerDialog
        )
    }

    if (uiState.showSaveSuccessDialog) {
        ZToolDialog(
            onDismissRequest = viewModel::dismissSaveSuccessDialog,
            title = { Text(stringResource(R.string.save_success_title)) },
            text = { Text(stringResource(R.string.clock_format_saved_message)) },
            confirmButton = {
                TextButton(onClick = viewModel::dismissSaveSuccessDialog) {
                    Text(stringResource(R.string.restart_yes))
                }
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
    onPickTextColor: () -> Unit,
    onTextBoldChanged: (Boolean) -> Unit,
    onNotificationIconLimitChanged: (String) -> Unit,
    onNativeNotificationIconChanged: (Boolean) -> Unit,
    onNetworkSpeedSizeChanged: (Boolean) -> Unit,
    onNetworkSpeedDoubleLayerChanged: (Boolean) -> Unit,
    onBatteryExternalChanged: (Boolean) -> Unit
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
                        onPickTextColor = onPickTextColor,
                        onTextBoldChanged = onTextBoldChanged,
                        onNotificationIconLimitChanged = onNotificationIconLimitChanged,
                        onNativeNotificationIconChanged = onNativeNotificationIconChanged,
                        onNetworkSpeedSizeChanged = onNetworkSpeedSizeChanged,
                        onNetworkSpeedDoubleLayerChanged = onNetworkSpeedDoubleLayerChanged,
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
    onPickTextColor: () -> Unit,
    onTextBoldChanged: (Boolean) -> Unit,
    onNotificationIconLimitChanged: (String) -> Unit,
    onNativeNotificationIconChanged: (Boolean) -> Unit,
    onNetworkSpeedSizeChanged: (Boolean) -> Unit,
    onNetworkSpeedDoubleLayerChanged: (Boolean) -> Unit,
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
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
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
                            textBold = state.textBold,
                            onClockFormatChanged = onClockFormatChanged,
                            onSaveClockFormat = onSaveClockFormat,
                            onTextSizeEnabledChanged = onTextSizeEnabledChanged,
                            onTextSizeChanged = onTextSizeChanged,
                            onLetterSpacingEnabledChanged = onLetterSpacingEnabledChanged,
                            onLetterSpacingChanged = onLetterSpacingChanged,
                            onTextColorEnabledChanged = onTextColorEnabledChanged,
                            onPickTextColor = onPickTextColor,
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
                            value = state.notificationIconLimitOption,
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
            items = listOf(
                SettingItem.Switch(
                    title = stringResource(R.string.statusBarNetworkSizeTitle),
                    summary = stringResource(R.string.statusBarNetworkSizeSummary),
                    checked = state.networkSpeedSize,
                    onCheckedChange = onNetworkSpeedSizeChanged
                ),
                SettingItem.Switch(
                    title = stringResource(R.string.statusBarNetworkSizeDoubleLayer),
                    summary = stringResource(R.string.statusBarNetworkSizeDoubleLayerSummary),
                    checked = state.networkSpeedDoubleLayer,
                    onCheckedChange = onNetworkSpeedDoubleLayerChanged
                )
            )
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
    textBold: Boolean,
    onClockFormatChanged: (String) -> Unit,
    onSaveClockFormat: () -> Unit,
    onTextSizeEnabledChanged: (Boolean) -> Unit,
    onTextSizeChanged: (Float) -> Unit,
    onLetterSpacingEnabledChanged: (Boolean) -> Unit,
    onLetterSpacingChanged: (Float) -> Unit,
    onTextColorEnabledChanged: (Boolean) -> Unit,
    onPickTextColor: () -> Unit,
    onTextBoldChanged: (Boolean) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = clockFormat,
                onValueChange = onClockFormatChanged,
                modifier = Modifier.weight(1f),
                label = { Text(stringResource(R.string.clock_format_hint)) },
                singleLine = true
            )
            Spacer(modifier = Modifier.width(12.dp))
            Button(onClick = onSaveClockFormat) {
                Text(stringResource(R.string.save))
            }
        }
        Text(
            text = clockPreview,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
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
            onCheckedChange = onTextColorEnabledChanged
        )
        if (textColorEnabled) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .background(Color(textColor), RoundedCornerShape(4.dp))
                )
                Spacer(modifier = Modifier.width(12.dp))
                Button(onClick = onPickTextColor) {
                    Text(stringResource(R.string.pick_color_button))
                }
            }
        }
        ZToolSwitchRow(
            title = stringResource(R.string.text_bold_title),
            summary = null,
            checked = textBold,
            onCheckedChange = onTextBoldChanged
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
        summary = valueLabel,
        checked = enabled,
        onCheckedChange = onEnabledChanged
    )
    Slider(
        value = value,
        onValueChange = onValueChanged,
        enabled = enabled,
        valueRange = valueRange,
        steps = steps,
        modifier = Modifier.padding(horizontal = 24.dp)
    )
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
        confirmButtonText = stringResource(R.string.restart_yes)
    )
}

@Composable
private fun ColorPickerDialog(
    onColorSelected: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    val colorValues = listOf(
        0xFFFFFFFF.toInt(), 0xFF000000.toInt(), 0xFFFF0000.toInt(),
        0xFF00FF00.toInt(), 0xFF0000FF.toInt(), 0xFFFFFF00.toInt(),
        0xFF00FFFF.toInt(), 0xFFFF00FF.toInt(), 0xFF2196F3.toInt(),
        0xFF4CAF50.toInt(), 0xFFFF9800.toInt(), 0xFF9C27B0.toInt(),
        0xFF607D8B.toInt(), 0xFFFF5722.toInt(), 0xFF795548.toInt()
    )
    val colorNames = stringArrayResource(R.array.color_names).toList()

    ZToolDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.select_font_color_title)) },
        text = {
            Column {
                colorValues.forEachIndexed { index, color ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .background(Color(color), RoundedCornerShape(4.dp))
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = colorNames.getOrElse(index) { "#%08X".format(color) },
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = { onColorSelected(color) }) {
                            Text(stringResource(R.string.confirm))
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.restart_no))
            }
        }
    )
}
