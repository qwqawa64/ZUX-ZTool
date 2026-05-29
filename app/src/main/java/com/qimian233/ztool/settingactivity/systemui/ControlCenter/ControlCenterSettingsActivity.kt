package com.qimian233.ztool.settingactivity.systemui.ControlCenter

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.qimian233.ztool.R
import com.qimian233.ztool.hook.modules.SharedPreferencesTool.ModulePreferencesUtils
import com.qimian233.ztool.hook.modules.systemui.CustomDateFormatter
import com.qimian233.ztool.ui.components.ZToolSwitchRow
import com.qimian233.ztool.ui.theme.ZToolTheme
import java.util.Date

class ControlCenterSettingsActivity : ComponentActivity() {

    private lateinit var prefsUtils: ModulePreferencesUtils
    private lateinit var zToolPrefs: ModulePreferencesUtils

    private var customDate by mutableStateOf(false)
    private var dateFormat by mutableStateOf("")
    private var datePreview by mutableStateOf("")
    private var textSizeEnabled by mutableStateOf(false)
    private var textSize by mutableStateOf(16.0f)
    private var letterSpacingEnabled by mutableStateOf(false)
    private var letterSpacing by mutableStateOf(0.1f)
    private var textColorEnabled by mutableStateOf(false)
    private var textColor by mutableStateOf(0xFFFFFFFF.toInt())
    private var textBold by mutableStateOf(false)
    private var showFormatHelpDialog by mutableStateOf(false)
    private var showColorPickerDialog by mutableStateOf(false)
    private var showSaveSuccessDialog by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val appName = intent.getStringExtra("app_name").orEmpty()
        prefsUtils = ModulePreferencesUtils(this)
        zToolPrefs = ModulePreferencesUtils(this)
        loadSettings()

        setContent {
            ZToolTheme {
                ControlCenterSettingsScreen(
                    title = appName + stringResource(R.string.control_center_settings_title_suffix),
                    customDate = customDate,
                    dateFormat = dateFormat,
                    datePreview = datePreview,
                    textSizeEnabled = textSizeEnabled,
                    textSize = textSize,
                    letterSpacingEnabled = letterSpacingEnabled,
                    letterSpacing = letterSpacing,
                    textColorEnabled = textColorEnabled,
                    textColor = textColor,
                    textBold = textBold,
                    onBack = ::finish,
                    onCustomDateChanged = ::handleCustomDateChanged,
                    onDateFormatChanged = {
                        dateFormat = it
                        updateDatePreview(it)
                    },
                    onSaveDateFormat = ::saveDateFormat,
                    onShowFormatHelp = { showFormatHelpDialog = true },
                    onTextSizeEnabledChanged = {
                        textSizeEnabled = it
                        zToolPrefs.saveBooleanSetting("Custom_ControlCenterDateTextSizeEnabled", it)
                    },
                    onTextSizeChanged = {
                        textSize = it
                        zToolPrefs.saveFloatSetting("Custom_ControlCenterDateTextSize", it)
                    },
                    onLetterSpacingEnabledChanged = {
                        letterSpacingEnabled = it
                        zToolPrefs.saveBooleanSetting("Custom_ControlCenterDateLetterSpacingEnabled", it)
                    },
                    onLetterSpacingChanged = {
                        letterSpacing = it
                        zToolPrefs.saveFloatSetting("Custom_ControlCenterDateLetterSpacing", it)
                    },
                    onTextColorEnabledChanged = {
                        textColorEnabled = it
                        zToolPrefs.saveBooleanSetting("Custom_ControlCenterDateTextColorEnabled", it)
                    },
                    onPickTextColor = { showColorPickerDialog = true },
                    onTextBoldChanged = {
                        textBold = it
                        zToolPrefs.saveBooleanSetting("Custom_ControlCenterDateTextBold", it)
                    }
                )

                if (showFormatHelpDialog) {
                    FormatHelpDialog(
                        onDismiss = { showFormatHelpDialog = false },
                        onCopyExample = {
                            showFormatHelpDialog = false
                            copyDateFormatExample()
                        }
                    )
                }

                if (showColorPickerDialog) {
                    ColorPickerDialog(
                        onColorSelected = {
                            showColorPickerDialog = false
                            textColor = it
                            zToolPrefs.saveIntegerSetting("Custom_ControlCenterDateTextColor", it)
                        },
                        onDismiss = { showColorPickerDialog = false }
                    )
                }

                if (showSaveSuccessDialog) {
                    AlertDialog(
                        onDismissRequest = { showSaveSuccessDialog = false },
                        title = { Text(stringResource(R.string.save_success_title)) },
                        text = { Text(stringResource(R.string.date_format_saved_message)) },
                        confirmButton = {
                            TextButton(onClick = { showSaveSuccessDialog = false }) {
                                Text(stringResource(R.string.restart_yes))
                            }
                        }
                    )
                }
            }
        }
    }

    private fun loadSettings() {
        customDate = prefsUtils.loadBooleanSetting("Custom_ControlCenterDate", false)
        dateFormat = zToolPrefs.loadStringSetting(
            "Custom_ControlCenterDateFormat",
            getString(R.string.default_date_format)
        )
        textSize = zToolPrefs.loadFloatSetting("Custom_ControlCenterDateTextSize", 16.0f)
        textSizeEnabled = zToolPrefs.loadBooleanSetting("Custom_ControlCenterDateTextSizeEnabled", false)
        letterSpacing = zToolPrefs.loadFloatSetting("Custom_ControlCenterDateLetterSpacing", 0.1f)
        letterSpacingEnabled = zToolPrefs.loadBooleanSetting(
            "Custom_ControlCenterDateLetterSpacingEnabled",
            false
        )
        textColor = zToolPrefs.loadIntegerSetting(
            "Custom_ControlCenterDateTextColor",
            0xFFFFFFFF.toInt()
        )
        textColorEnabled = zToolPrefs.loadBooleanSetting("Custom_ControlCenterDateTextColorEnabled", false)
        textBold = zToolPrefs.loadBooleanSetting("Custom_ControlCenterDateTextBold", false)
        updateDatePreview(dateFormat)
    }

    private fun handleCustomDateChanged(isEnabled: Boolean) {
        customDate = isEnabled
        prefsUtils.saveBooleanSetting("Custom_ControlCenterDate", isEnabled)
        if (isEnabled && dateFormat.isEmpty()) {
            dateFormat = zToolPrefs.loadStringSetting(
                "Custom_ControlCenterDateFormat",
                getString(R.string.default_date_format)
            )
        }
        updateDatePreview(dateFormat)
    }

    private fun saveDateFormat() {
        Log.d(TAG, "保存的格式：$dateFormat")
        zToolPrefs.saveStringSetting("Custom_ControlCenterDateFormat", dateFormat)
        showSaveSuccessDialog = true
    }

    private fun updateDatePreview(format: String) {
        datePreview = if (format.isEmpty()) {
            getString(R.string.preview_default)
        } else {
            try {
                getString(R.string.preview_display, CustomDateFormatter.format(format, Date()))
            } catch (e: Exception) {
                Log.e(TAG, "Error formatting date: $format", e)
                getString(R.string.preview_invalid) + "\n" + getString(R.string.error_prefix) + e.message
            }
        }
    }

    private fun copyDateFormatExample() {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText(
            getString(R.string.date_format_example),
            getString(R.string.date_format_sample)
        )
        clipboard.setPrimaryClip(clip)
        Toast.makeText(this, R.string.example_copied_message, Toast.LENGTH_SHORT).show()
    }

    companion object {
        private const val TAG = "ControlCenterSettings"
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ControlCenterSettingsScreen(
    title: String,
    customDate: Boolean,
    dateFormat: String,
    datePreview: String,
    textSizeEnabled: Boolean,
    textSize: Float,
    letterSpacingEnabled: Boolean,
    letterSpacing: Float,
    textColorEnabled: Boolean,
    textColor: Int,
    textBold: Boolean,
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
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = title,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = null
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer
                )
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
                SettingsCard(title = stringResource(R.string.ControllerDate)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ZToolSwitchRow(
                            title = stringResource(R.string.CustomDateSettingTitle),
                            summary = stringResource(R.string.CustomDateSettingSummary),
                            checked = customDate,
                            onCheckedChange = onCustomDateChanged,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = onShowFormatHelp,
                            modifier = Modifier.padding(end = 12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Info,
                                contentDescription = stringResource(R.string.tooltip_content_description),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    if (customDate) {
                        CustomDateConfig(
                            dateFormat = dateFormat,
                            datePreview = datePreview,
                            textSizeEnabled = textSizeEnabled,
                            textSize = textSize,
                            letterSpacingEnabled = letterSpacingEnabled,
                            letterSpacing = letterSpacing,
                            textColorEnabled = textColorEnabled,
                            textColor = textColor,
                            textBold = textBold,
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
            }
        }
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
private fun SettingsCard(
    title: String,
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(PaddingValues(horizontal = 24.dp, vertical = 8.dp))
            )
            content()
        }
    }
}

@Composable
private fun FormatHelpDialog(
    onDismiss: () -> Unit,
    onCopyExample: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.date_format_help_title)) },
        text = { Text(stringResource(R.string.clock_format_help_content)) },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.restart_yes))
            }
        },
        dismissButton = {
            TextButton(onClick = onCopyExample) {
                Text(stringResource(R.string.copy_example_button))
            }
        }
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

    AlertDialog(
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
