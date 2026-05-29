package com.qimian233.ztool.settingactivity.systemui.statusBarSetting

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.SharedPreferences
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.qimian233.ztool.R
import com.qimian233.ztool.hook.modules.SharedPreferencesTool.ModulePreferencesUtils
import com.qimian233.ztool.hook.modules.systemui.CustomDateFormatter
import com.qimian233.ztool.ui.components.ZToolDropdownField
import com.qimian233.ztool.ui.components.ZToolSettingsDivider
import com.qimian233.ztool.ui.components.ZToolSwitchRow
import com.qimian233.ztool.ui.theme.ZToolTheme
import java.util.Date

class StatusBarSettingsActivity : ComponentActivity() {

    private lateinit var prefsUtils: ModulePreferencesUtils
    private lateinit var zToolPrefs: ModulePreferencesUtils
    private lateinit var notifyNumSizePrefs: SharedPreferences

    private var uiState by mutableStateOf(StatusBarSettingsUiState())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val appName = intent.getStringExtra("app_name").orEmpty()
        prefsUtils = ModulePreferencesUtils(this)
        zToolPrefs = ModulePreferencesUtils(this)
        notifyNumSizePrefs = getNotifyNumSizeShared()
        loadSettings()

        setContent {
            ZToolTheme {
                StatusBarSettingsScreen(
                    title = appName + stringResource(R.string.status_bar_settings_title_suffix),
                    state = uiState,
                    onBack = ::finish,
                    onDisplaySecondsChanged = {
                        uiState = uiState.copy(displaySeconds = it)
                        saveSettings("StatusBarDisplay_Seconds", it)
                    },
                    onCustomClockChanged = {
                        uiState = uiState.copy(customClock = it)
                        saveSettings("Custom_StatusBarClock", it)
                        if (it) {
                            updateClockPreview(uiState.clockFormat)
                        }
                    },
                    onClockFormatChanged = {
                        uiState = uiState.copy(clockFormat = it)
                        updateClockPreview(it)
                    },
                    onSaveClockFormat = ::saveClockFormat,
                    onShowFormatHelp = { uiState = uiState.copy(showFormatHelpDialog = true) },
                    onTextSizeEnabledChanged = {
                        uiState = uiState.copy(textSizeEnabled = it)
                        zToolPrefs.saveBooleanSetting("Custom_StatusBarClockTextSizeEnabled", it)
                    },
                    onTextSizeChanged = {
                        uiState = uiState.copy(textSize = it)
                        zToolPrefs.saveFloatSetting("Custom_StatusBarClockTextSize", it)
                    },
                    onLetterSpacingEnabledChanged = {
                        uiState = uiState.copy(letterSpacingEnabled = it)
                        zToolPrefs.saveBooleanSetting("Custom_StatusBarClockLetterSpacingEnabled", it)
                    },
                    onLetterSpacingChanged = {
                        uiState = uiState.copy(letterSpacing = it)
                        zToolPrefs.saveFloatSetting("Custom_StatusBarClockLetterSpacing", it)
                    },
                    onTextColorEnabledChanged = {
                        uiState = uiState.copy(textColorEnabled = it)
                        zToolPrefs.saveBooleanSetting("Custom_StatusBarClockTextColorEnabled", it)
                    },
                    onPickTextColor = { uiState = uiState.copy(showColorPickerDialog = true) },
                    onTextBoldChanged = {
                        uiState = uiState.copy(textBold = it)
                        zToolPrefs.saveBooleanSetting("Custom_StatusBarClockTextBold", it)
                    },
                    onNotificationIconLimitChanged = ::handleNotificationIconLimitChanged,
                    onNativeNotificationIconChanged = {
                        uiState = uiState.copy(nativeNotificationIcon = it)
                        saveSettings("NativeNotificationIcon", it)
                    },
                    onNetworkSpeedSizeChanged = {
                        uiState = uiState.copy(networkSpeedSize = it)
                        saveSettings("systemui_network_speed_size", it)
                    },
                    onNetworkSpeedDoubleLayerChanged = {
                        uiState = uiState.copy(networkSpeedDoubleLayer = it)
                        saveSettings("systemui_network_speed_doublelayer", it)
                    },
                    onBatteryExternalChanged = {
                        uiState = uiState.copy(batteryExternal = it)
                        saveSettings("systemui_battery_percentage", it)
                    }
                )

                if (uiState.showFormatHelpDialog) {
                    FormatHelpDialog(
                        onDismiss = { uiState = uiState.copy(showFormatHelpDialog = false) },
                        onCopyExample = {
                            uiState = uiState.copy(showFormatHelpDialog = false)
                            copyClockFormatExample()
                        }
                    )
                }

                if (uiState.showColorPickerDialog) {
                    ColorPickerDialog(
                        onColorSelected = {
                            uiState = uiState.copy(
                                showColorPickerDialog = false,
                                textColor = it
                            )
                            zToolPrefs.saveIntegerSetting("Custom_StatusBarClockTextColor", it)
                        },
                        onDismiss = { uiState = uiState.copy(showColorPickerDialog = false) }
                    )
                }

                if (uiState.showSaveSuccessDialog) {
                    AlertDialog(
                        onDismissRequest = { uiState = uiState.copy(showSaveSuccessDialog = false) },
                        title = { Text(stringResource(R.string.save_success_title)) },
                        text = { Text(stringResource(R.string.clock_format_saved_message)) },
                        confirmButton = {
                            TextButton(onClick = { uiState = uiState.copy(showSaveSuccessDialog = false) }) {
                                Text(stringResource(R.string.restart_yes))
                            }
                        }
                    )
                }
            }
        }
    }

    private fun loadSettings() {
        val loadedClockFormat = zToolPrefs.loadStringSetting("Custom_StatusBarClockFormat", "")
        uiState = uiState.copy(
            displaySeconds = prefsUtils.loadBooleanSetting("StatusBarDisplay_Seconds", false),
            customClock = prefsUtils.loadBooleanSetting("Custom_StatusBarClock", false),
            nativeNotificationIcon = prefsUtils.loadBooleanSetting("NativeNotificationIcon", false),
            networkSpeedSize = prefsUtils.loadBooleanSetting("systemui_network_speed_size", false),
            networkSpeedDoubleLayer = prefsUtils.loadBooleanSetting("systemui_network_speed_doublelayer", false),
            batteryExternal = prefsUtils.loadBooleanSetting("systemui_battery_percentage", false),
            clockFormat = loadedClockFormat,
            textSize = zToolPrefs.loadFloatSetting("Custom_StatusBarClockTextSize", 16.0f),
            textSizeEnabled = zToolPrefs.loadBooleanSetting("Custom_StatusBarClockTextSizeEnabled", false),
            letterSpacing = zToolPrefs.loadFloatSetting("Custom_StatusBarClockLetterSpacing", 0.1f),
            letterSpacingEnabled = zToolPrefs.loadBooleanSetting("Custom_StatusBarClockLetterSpacingEnabled", false),
            textColor = zToolPrefs.loadIntegerSetting("Custom_StatusBarClockTextColor", 0xFFFFFFFF.toInt()),
            textColorEnabled = zToolPrefs.loadBooleanSetting("Custom_StatusBarClockTextColorEnabled", false),
            textBold = zToolPrefs.loadBooleanSetting("Custom_StatusBarClockTextBold", false),
            notificationIconLimitOption = notifyNumSizeToOption(notifyNumSizePrefs.getInt("notify_num_size", 4))
        )
        updateClockPreview(loadedClockFormat)
    }

    private fun saveClockFormat() {
        zToolPrefs.saveStringSetting("Custom_StatusBarClockFormat", uiState.clockFormat)
        uiState = uiState.copy(showSaveSuccessDialog = true)
    }

    private fun updateClockPreview(format: String) {
        val preview = if (format.isEmpty()) {
            getString(R.string.preview_default)
        } else {
            try {
                getString(R.string.preview_display, CustomDateFormatter.format(format, Date()))
            } catch (e: Exception) {
                Log.e(TAG, "Error formatting date: $format", e)
                getString(R.string.preview_invalid) + "\n" + getString(R.string.error_prefix) + e.message
            }
        }
        uiState = uiState.copy(clockPreview = preview)
    }

    private fun handleNotificationIconLimitChanged(option: String) {
        uiState = uiState.copy(notificationIconLimitOption = option)
        if (option == getString(R.string.notify_num_default)) {
            saveSettings("notification_icon_limit", false)
            return
        }

        saveSettings("notification_icon_limit", true)
        if (option == getString(R.string.notify_num_unlimited)) {
            notifyNumSizePrefs.edit().putInt("notify_num_size", 100).apply()
        } else {
            option.toIntOrNull()?.let {
                notifyNumSizePrefs.edit().putInt("notify_num_size", it).apply()
            } ?: run {
                Log.e(TAG, "Invalid notification number option: $option")
                Toast.makeText(this, R.string.save_failed_message, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun notifyNumSizeToOption(value: Int): String {
        return when (value) {
            100 -> getString(R.string.notify_num_unlimited)
            else -> value.coerceIn(1, 14).toString()
        }
    }

    private fun copyClockFormatExample() {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText(
            getString(R.string.clock_format_example),
            getString(R.string.clock_format_sample)
        )
        clipboard.setPrimaryClip(clip)
        Toast.makeText(this, R.string.example_copied_message, Toast.LENGTH_SHORT).show()
    }

    private fun saveSettings(moduleName: String, newValue: Boolean) {
        prefsUtils.saveBooleanSetting(moduleName, newValue)
    }

    @SuppressLint("WorldReadableFiles")
    private fun getNotifyNumSizeShared(): SharedPreferences {
        return try {
            val moduleContext = createPackageContext(
                "com.qimian233.ztool",
                Context.CONTEXT_IGNORE_SECURITY
            )
            moduleContext.getSharedPreferences("StatusBar_notifyNumSize", Context.MODE_WORLD_READABLE)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get module preferences, using fallback", e)
            getSharedPreferences("StatusBar_notifyNumSize", Context.MODE_WORLD_READABLE)
        }
    }

    companion object {
        private const val TAG = "StatusBarSettings"
    }
}

private data class StatusBarSettingsUiState(
    val displaySeconds: Boolean = false,
    val customClock: Boolean = false,
    val clockFormat: String = "",
    val clockPreview: String = "",
    val textSizeEnabled: Boolean = false,
    val textSize: Float = 16.0f,
    val letterSpacingEnabled: Boolean = false,
    val letterSpacing: Float = 0.1f,
    val textColorEnabled: Boolean = false,
    val textColor: Int = 0xFFFFFFFF.toInt(),
    val textBold: Boolean = false,
    val notificationIconLimitOption: String = "",
    val nativeNotificationIcon: Boolean = false,
    val networkSpeedSize: Boolean = false,
    val networkSpeedDoubleLayer: Boolean = false,
    val batteryExternal: Boolean = false,
    val showFormatHelpDialog: Boolean = false,
    val showColorPickerDialog: Boolean = false,
    val showSaveSuccessDialog: Boolean = false
)

@OptIn(ExperimentalMaterial3Api::class)
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
                SettingsCard(title = stringResource(R.string.status_bar_clock_settings_title)) {
                    ZToolSwitchRow(
                        title = stringResource(R.string.display_seconds_title),
                        summary = stringResource(R.string.display_seconds_summary),
                        checked = state.displaySeconds,
                        onCheckedChange = onDisplaySecondsChanged
                    )
                    ZToolSettingsDivider()
                    ZToolSwitchRow(
                        title = stringResource(R.string.custom_clock_title),
                        summary = stringResource(R.string.custom_clock_summary),
                        checked = state.customClock,
                        onCheckedChange = onCustomClockChanged
                    )
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
                    if (state.customClock) {
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

                Spacer(modifier = Modifier.height(16.dp))

                SettingsCard(title = stringResource(R.string.status_bar_notification_settings_title)) {
                    DropdownSettingRow(
                        title = stringResource(R.string.notification_icon_limit_title),
                        summary = stringResource(R.string.notification_icon_limit_summary),
                        options = stringArrayResource(R.array.notify_num_size_options).toList(),
                        selectedOption = state.notificationIconLimitOption,
                        onOptionSelected = onNotificationIconLimitChanged
                    )
                    ZToolSettingsDivider()
                    ZToolSwitchRow(
                        title = stringResource(R.string.notification_icon_native_title),
                        summary = stringResource(R.string.notification_icon_native_summary),
                        checked = state.nativeNotificationIcon,
                        onCheckedChange = onNativeNotificationIconChanged
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                SettingsCard(title = stringResource(R.string.statusBarNetworkTitle)) {
                    ZToolSwitchRow(
                        title = stringResource(R.string.statusBarNetworkSizeTitle),
                        summary = stringResource(R.string.statusBarNetworkSizeSummary),
                        checked = state.networkSpeedSize,
                        onCheckedChange = onNetworkSpeedSizeChanged
                    )
                    ZToolSettingsDivider()
                    ZToolSwitchRow(
                        title = stringResource(R.string.statusBarNetworkSizeDoubleLayer),
                        summary = stringResource(R.string.statusBarNetworkSizeDoubleLayerSummary),
                        checked = state.networkSpeedDoubleLayer,
                        onCheckedChange = onNetworkSpeedDoubleLayerChanged
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                SettingsCard(title = stringResource(R.string.statusBarBatteryTitle)) {
                    ZToolSwitchRow(
                        title = stringResource(R.string.syatusBatteryExternalTitle),
                        summary = stringResource(R.string.syatusBatteryExternalSummary),
                        checked = state.batteryExternal,
                        onCheckedChange = onBatteryExternalChanged
                    )
                }
            }
        }
    }
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DropdownSettingRow(
    title: String,
    summary: String,
    options: List<String>,
    selectedOption: String,
    onOptionSelected: (String) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = summary,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
        Spacer(modifier = Modifier.width(16.dp))
        ZToolDropdownField(
            label = "",
            value = selectedOption,
            options = options,
            optionLabel = { it },
            onOptionSelected = onOptionSelected,
            modifier = Modifier.widthIn(min = 132.dp, max = 180.dp)
        )
    }
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
        title = { Text(stringResource(R.string.clock_format_help_title)) },
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
