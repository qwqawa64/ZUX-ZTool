@file:Suppress("PackageName")

package com.qimian233.ztool.settingactivity.systemui.ControlCenter

import android.content.ClipData
import android.content.ClipboardManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.qimian233.ztool.R
import com.qimian233.ztool.data.systemui.ControlCenterSettingsRepository
import com.qimian233.ztool.ui.components.QuickHelpExample
import com.qimian233.ztool.ui.components.QuickHelpItem
import com.qimian233.ztool.ui.components.SettingItem
import com.qimian233.ztool.ui.components.SettingSection
import com.qimian233.ztool.ui.components.ZToolDialog
import com.qimian233.ztool.ui.components.ZToolQuickHelpDialog
import com.qimian233.ztool.ui.components.ZToolScaffold
import com.qimian233.ztool.ui.components.ZToolSettingsList
import com.qimian233.ztool.ui.components.ZToolSwitchRow
import com.qimian233.ztool.ui.components.ZToolTopAppBar
import com.qimian233.ztool.ui.theme.ZToolTheme
import com.qimian233.ztool.utils.applyZToolActivityTransitions
import com.qimian233.ztool.viewmodel.ControlCenterSettingsUiState
import com.qimian233.ztool.viewmodel.ControlCenterSettingsViewModel

class ControlCenterSettingsActivity : ComponentActivity() {

    private lateinit var viewModel: ControlCenterSettingsViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        applyZToolActivityTransitions()
        enableEdgeToEdge()

        val appName = intent.getStringExtra("app_name").orEmpty()
        val repository = ControlCenterSettingsRepository(applicationContext)
        viewModel = ViewModelProvider(
            this,
            ControlCenterSettingsViewModelFactory(repository)
        )[ControlCenterSettingsViewModel::class.java]
        viewModel.loadSettings()

        setContent {
            val uiState by viewModel.uiState.collectAsState()

            ZToolTheme {
                ControlCenterSettingsScreen(
                    title = appName + stringResource(R.string.control_center_settings_title_suffix),
                    state = uiState,
                    onBack = ::finish,
                    onCustomDateChanged = viewModel::setCustomDate,
                    onDateFormatChanged = viewModel::setDateFormat,
                    onSaveDateFormat = viewModel::saveDateFormat,
                    onShowFormatHelp = viewModel::showFormatHelpDialog,
                    onTextSizeEnabledChanged = viewModel::setTextSizeEnabled,
                    onTextSizeChanged = viewModel::setTextSize,
                    onLetterSpacingEnabledChanged = viewModel::setLetterSpacingEnabled,
                    onLetterSpacingChanged = viewModel::setLetterSpacing,
                    onTextColorEnabledChanged = viewModel::setTextColorEnabled,
                    onPickTextColor = viewModel::showColorPickerDialog,
                    onTextBoldChanged = viewModel::setTextBold
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
                        text = { Text(stringResource(R.string.date_format_saved_message)) },
                        confirmButton = {
                            TextButton(onClick = viewModel::dismissSaveSuccessDialog) {
                                Text(stringResource(R.string.restart_yes))
                            }
                        }
                    )
                }
            }
        }
    }

    private fun copyDateFormatExample() {
        val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText(
            getString(R.string.date_format_example),
            getString(R.string.date_format_sample)
        )
        clipboard.setPrimaryClip(clip)
        Toast.makeText(this, R.string.example_copied_message, Toast.LENGTH_SHORT).show()
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
    onPickTextColor: () -> Unit,
    onTextBoldChanged: (Boolean) -> Unit
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
                        onPickTextColor = onPickTextColor,
                        onTextBoldChanged = onTextBoldChanged
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
    onPickTextColor: () -> Unit,
    onTextBoldChanged: (Boolean) -> Unit
): List<SettingSection> {
    return listOf(
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
                            onPickTextColor = onPickTextColor,
                            onTextBoldChanged = onTextBoldChanged
                        )
                    }
                )
            )
        )
    )
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
    onPickTextColor: () -> Unit,
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
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        CustomDateConfig(
            dateFormat = state.dateFormat,
            datePreview = state.datePreview,
            textSizeEnabled = state.textSizeEnabled,
            textSize = state.textSize,
            letterSpacingEnabled = state.letterSpacingEnabled,
            letterSpacing = state.letterSpacing,
            textColorEnabled = state.textColorEnabled,
            textColor = state.textColor,
            textBold = state.textBold,
            onDateFormatChanged = onDateFormatChanged,
            onSaveDateFormat = onSaveDateFormat,
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

@Composable
private fun CustomDateConfig(
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
                value = dateFormat,
                onValueChange = onDateFormatChanged,
                modifier = Modifier.weight(1f),
                label = { Text(stringResource(R.string.ControllerDateFormat)) },
                singleLine = true
            )
            Spacer(modifier = Modifier.width(12.dp))
            Button(onClick = onSaveDateFormat) {
                Text(stringResource(R.string.save))
            }
        }
        Text(
            text = datePreview,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                    Text(stringResource(R.string.pick_color))
                }
            }
        }
        ZToolSwitchRow(
            title = stringResource(R.string.custom_clock_text_bold_title),
            summary = stringResource(R.string.useBoldDate),
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
