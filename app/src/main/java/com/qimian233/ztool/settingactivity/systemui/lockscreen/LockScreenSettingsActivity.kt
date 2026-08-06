package com.qimian233.ztool.settingactivity.systemui.lockscreen

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
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import com.qimian233.ztool.data.systemui.LockScreenSettingsRepository
import com.qimian233.ztool.ui.components.SettingItem
import com.qimian233.ztool.ui.components.SettingSection
import com.qimian233.ztool.ui.components.ZToolButton
import com.qimian233.ztool.ui.components.ZToolDialog
import com.qimian233.ztool.ui.components.ZToolExtendedFloatingActionButton
import com.qimian233.ztool.ui.components.ZToolPopupMenuSettingRow
import com.qimian233.ztool.ui.components.ZToolScaffold
import com.qimian233.ztool.ui.components.ZToolSettingsList
import com.qimian233.ztool.ui.components.ZToolTextButton
import com.qimian233.ztool.ui.components.ZToolTextInputRow
import com.qimian233.ztool.ui.components.ZToolTopAppBar
import com.qimian233.ztool.ui.theme.LocalZToolColorScheme
import com.qimian233.ztool.viewmodel.ApiTestResult
import com.qimian233.ztool.viewmodel.LockScreenSettingsUiState
import com.qimian233.ztool.viewmodel.LockScreenSettingsViewModel

@Composable
fun LockScreenSettingsRoute(
    title: String,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val owner = LocalViewModelStoreOwner.current
        ?: error("LockScreenSettingsRoute requires a ViewModelStoreOwner")
    val viewModel = remember(owner) {
        ViewModelProvider(
            owner,
            LockScreenSettingsViewModelFactory(
                LockScreenSettingsRepository(context.applicationContext)
            )
        )[LockScreenSettingsViewModel::class.java]
    }

    LaunchedEffect(viewModel) {
        viewModel.loadSettings()
    }

    val uiState by viewModel.uiState.collectAsState()

    LockScreenSettingsScreen(
        title = title,
        state = uiState,
        onBack = onBack,
        onYiYanChanged = viewModel::setYiYanEnabled,
        onNativeAodChanged = viewModel::setNativeAodEnabled,
        onLenovoAodChanged = viewModel::setLenovoAodEnabled,
        onOpenLenovoAodSettings = viewModel::openLenovoAodSettings,
        onApiAddressChanged = viewModel::setApiAddress,
        onRegexChanged = viewModel::setRegex,
        onChargeWattsOptionChanged = viewModel::setChargeWattsOption,
        onShowVoltageChanged = viewModel::setShowVoltage,
        onShowCurrentChanged = viewModel::setShowCurrent,
        onShowPowerChanged = viewModel::setShowPower,
        onShowTemperatureChanged = viewModel::setShowTemperature,
        onShowIndicatorChanged = viewModel::setShowIndicator,
        onCustomFormatEnabledChanged = viewModel::setCustomFormatEnabled,
        onCustomFormatChanged = viewModel::setCustomFormat,
        onTestApi = {
            viewModel.testApiConnection {
                Toast.makeText(context, R.string.please_input_api_address, Toast.LENGTH_SHORT).show()
            }
        },
        onRestartScope = viewModel::showRestartDialog,
    )

    if (uiState.showRestartDialog) {
        val restartFailString = stringResource(R.string.restartFail)
        RestartScopeDialog(
            packageName = "com.android.systemui",
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

    if (uiState.showRootPermissionDialog) {
        RootPermissionDialog(
            onConfirm = viewModel::dismissRootPermissionDialog,
            onDoNotShowAgain = {
                viewModel.confirmSystemUiPermission()
                Toast.makeText(context, R.string.no_tip_next_time, Toast.LENGTH_SHORT).show()
            }
        )
    }

    uiState.apiTestResult?.let { result ->
        ApiTestResultDialog(
            result = result,
            onSave = {
                viewModel.saveYiYanConfiguration()
                Toast.makeText(context, R.string.configuration_saved_message, Toast.LENGTH_SHORT).show()
            },
            onDismiss = viewModel::dismissApiTestResult
        )
    }
}

private class LockScreenSettingsViewModelFactory(
    private val repository: LockScreenSettingsRepository
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(LockScreenSettingsViewModel::class.java)) {
            return LockScreenSettingsViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}

@Composable
private fun LockScreenSettingsScreen(
    title: String,
    state: LockScreenSettingsUiState,
    onBack: () -> Unit,
    onYiYanChanged: (Boolean) -> Unit,
    onNativeAodChanged: (Boolean) -> Unit,
    onLenovoAodChanged: (Boolean) -> Unit,
    onOpenLenovoAodSettings: () -> Unit,
    onApiAddressChanged: (String) -> Unit,
    onRegexChanged: (String) -> Unit,
    onTestApi: () -> Unit,
    onChargeWattsOptionChanged: (String) -> Unit,
    onShowVoltageChanged: (Boolean) -> Unit,
    onShowCurrentChanged: (Boolean) -> Unit,
    onShowPowerChanged: (Boolean) -> Unit,
    onShowTemperatureChanged: (Boolean) -> Unit,
    onShowIndicatorChanged: (Boolean) -> Unit,
    onCustomFormatEnabledChanged: (Boolean) -> Unit,
    onCustomFormatChanged: (String) -> Unit,
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
                    sections = lockScreenSettingsSections(
                        state = state,
                        onYiYanChanged = onYiYanChanged,
                        onNativeAodChanged = onNativeAodChanged,
                        onLenovoAodChanged = onLenovoAodChanged,
                        onOpenLenovoAodSettings = onOpenLenovoAodSettings,
                        onApiAddressChanged = onApiAddressChanged,
                        onRegexChanged = onRegexChanged,
                        onTestApi = onTestApi,
                        onChargeWattsOptionChanged = onChargeWattsOptionChanged,
                        onShowVoltageChanged = onShowVoltageChanged,
                        onShowCurrentChanged = onShowCurrentChanged,
                        onShowPowerChanged = onShowPowerChanged,
                        onShowTemperatureChanged = onShowTemperatureChanged,
                        onShowIndicatorChanged = onShowIndicatorChanged,
                        onCustomFormatEnabledChanged = onCustomFormatEnabledChanged,
                        onCustomFormatChanged = onCustomFormatChanged,
                    ),
                    bottomPadding = 96.dp
                )
            }
        }
    }
}

@Composable
private fun lockScreenSettingsSections(
    state: LockScreenSettingsUiState,
    onYiYanChanged: (Boolean) -> Unit,
    onNativeAodChanged: (Boolean) -> Unit,
    onLenovoAodChanged: (Boolean) -> Unit,
    onOpenLenovoAodSettings: () -> Unit,
    onApiAddressChanged: (String) -> Unit,
    onRegexChanged: (String) -> Unit,
    onTestApi: () -> Unit,
    onChargeWattsOptionChanged: (String) -> Unit,
    onShowVoltageChanged: (Boolean) -> Unit,
    onShowCurrentChanged: (Boolean) -> Unit,
    onShowPowerChanged: (Boolean) -> Unit,
    onShowTemperatureChanged: (Boolean) -> Unit,
    onShowIndicatorChanged: (Boolean) -> Unit,
    onCustomFormatEnabledChanged: (Boolean) -> Unit,
    onCustomFormatChanged: (String) -> Unit,
): List<SettingSection> {
    val yiYanItems = buildList {
        add(
            SettingItem.Switch(
                title = stringResource(R.string.YiYanSwitchTitle),
                summary = stringResource(R.string.YiYanSummary),
                checked = state.yiYanEnabled,
                onCheckedChange = onYiYanChanged
            )
        )
        if (state.yiYanEnabled) {
            add(
                SettingItem.Custom(
                    content = {
                        YiYanConfigFields(
                            apiAddress = state.apiAddress,
                            regex = state.regex,
                            isTestingApi = state.isTestingApi,
                            onApiAddressChanged = onApiAddressChanged,
                            onRegexChanged = onRegexChanged,
                            onTestApi = onTestApi
                        )
                    }
                )
            )
        }
    }

    val aodItems = buildList {
        add(
            SettingItem.Switch(
                title = stringResource(R.string.aod_native_enable_title),
                summary = stringResource(R.string.aod_native_enable_summary),
                checked = state.nativeAod,
                onCheckedChange = onNativeAodChanged
            )
        )
        add(
            SettingItem.Switch(
                title = stringResource(R.string.aod_lenovo_enable_title),
                summary = stringResource(R.string.aod_lenovo_enable_summary),
                checked = state.lenovoAod,
                onCheckedChange = onLenovoAodChanged
            )
        )
        if (state.lenovoAod) {
            add(
                SettingItem.Action(
                    title = stringResource(R.string.aod_lenovo_activity_title),
                    summary = stringResource(R.string.aod_lenovo_activity_summary),
                    onClick = onOpenLenovoAodSettings,
                    trailingContent = {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                            contentDescription = null,
                            tint = LocalZToolColorScheme.current.onSurfaceVariant
                        )
                    }
                )
            )
        }
    }

    val chargeWattsItems = buildList {
        add(
            SettingItem.Custom(
                content = {
                    ChargeWattsSettingsContent(
                        state = state,
                        onChargeWattsOptionChanged = onChargeWattsOptionChanged,
                    )
                }
            )
        )

        // 当选择"实际功率"时展开子开关
        val isActualWatts = state.chargeWattsOption == stringResource(R.string.watt_option_actual)
        if (isActualWatts) {
            add(
                SettingItem.Switch(
                    title = stringResource(R.string.realwatts_show_power),
                    summary = stringResource(R.string.realwatts_show_power_summary),
                    checked = state.showPower,
                    onCheckedChange = onShowPowerChanged
                )
            )
            add(
                SettingItem.Switch(
                    title = stringResource(R.string.realwatts_show_voltage),
                    summary = stringResource(R.string.realwatts_show_voltage_summary),
                    checked = state.showVoltage,
                    onCheckedChange = onShowVoltageChanged
                )
            )
            add(
                SettingItem.Switch(
                    title = stringResource(R.string.realwatts_show_current),
                    summary = stringResource(R.string.realwatts_show_current_summary),
                    checked = state.showCurrent,
                    onCheckedChange = onShowCurrentChanged
                )
            )
            add(
                SettingItem.Switch(
                    title = stringResource(R.string.realwatts_show_temperature),
                    summary = stringResource(R.string.realwatts_show_temperature_summary),
                    checked = state.showTemperature,
                    onCheckedChange = onShowTemperatureChanged
                )
            )
            add(
                SettingItem.Switch(
                    title = stringResource(R.string.realwatts_show_indicator),
                    summary = stringResource(R.string.realwatts_show_indicator_summary),
                    checked = state.showIndicator,
                    onCheckedChange = onShowIndicatorChanged
                )
            )
            // 高级自定义格式
            add(
                SettingItem.Switch(
                    title = stringResource(R.string.realwatts_custom_format_enabled),
                    summary = stringResource(R.string.realwatts_custom_format_enabled_summary),
                    checked = state.customFormatEnabled,
                    onCheckedChange = onCustomFormatEnabledChanged
                )
            )
            if (state.customFormatEnabled) {
                add(
                    SettingItem.Custom(
                        content = {
                            CustomFormatInput(
                                value = state.customFormat,
                                onValueChange = onCustomFormatChanged
                            )
                        }
                    )
                )
            }
        }
    }

    return listOf(
        SettingSection(
            title = stringResource(R.string.YiYanTile),
            items = yiYanItems
        ),
        SettingSection(
            title = stringResource(R.string.aod_title),
            items = aodItems
        ),
        SettingSection(
            title = stringResource(R.string.ChargeWattsTitle),
            items = chargeWattsItems
        )
    )
}

@Composable
private fun ChargeWattsSettingsContent(
    state: LockScreenSettingsUiState,
    onChargeWattsOptionChanged: (String) -> Unit,
) {
    ZToolPopupMenuSettingRow(
        title = stringResource(R.string.ChargeWattsEnableTitle),
        summary = stringResource(R.string.ChargeWattsSummary),
        options = stringArrayResource(R.array.watt_options).toList(),
        value = state.chargeWattsOption,
        optionLabel = { it },
        onOptionSelected = onChargeWattsOptionChanged
    )
}

@Composable
private fun YiYanConfigFields(
    apiAddress: String,
    regex: String,
    isTestingApi: Boolean,
    onApiAddressChanged: (String) -> Unit,
    onRegexChanged: (String) -> Unit,
    onTestApi: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ZToolTextInputRow(
                value = apiAddress,
                onValueChange = onApiAddressChanged,
                modifier = Modifier.weight(1f),
                label = stringResource(R.string.api_address_hint),
                singleLine = true,
                horizontalPadding = 0.dp
            )
            Spacer(modifier = Modifier.width(12.dp))
            ZToolButton(
                onClick = onTestApi,
                enabled = !isTestingApi
            ) {
                Text(
                    if (isTestingApi) {
                        stringResource(R.string.testing_api_connection)
                    } else {
                        stringResource(R.string.test)
                    }
                )
            }
        }
        ZToolTextInputRow(
            value = regex,
            onValueChange = onRegexChanged,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            label = stringResource(R.string.regex_label),
            singleLine = true,
            horizontalPadding = 0.dp
        )
    }
}

@Composable
private fun CustomFormatInput(
    value: String,
    onValueChange: (String) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 8.dp)
    ) {
        ZToolTextInputRow(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            label = stringResource(R.string.realwatts_custom_format_label),
            singleLine = false,
            horizontalPadding = 0.dp
        )
        Text(
            text = stringResource(R.string.realwatts_custom_format_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp)
        )
    }
}

@Composable
private fun RootPermissionDialog(
    onConfirm: () -> Unit,
    onDoNotShowAgain: () -> Unit
) {
    ZToolDialog(
        onDismissRequest = onConfirm,
        title = { Text(stringResource(R.string.tooltip_content_description)) },
        text = { Text(stringResource(R.string.systemui_root_permission_required_message)) },
        confirmButton = {
            ZToolTextButton(onClick = onConfirm, text = stringResource(R.string.confirm))
        },
        dismissButton = {
            ZToolTextButton(onClick = onDoNotShowAgain, text = stringResource(R.string.do_not_show_again), isPrimary = false)
        }
    )
}

@Composable
private fun ApiTestResultDialog(
    result: ApiTestResult,
    onSave: () -> Unit,
    onDismiss: () -> Unit
) {
    ZToolDialog(
        onDismissRequest = onDismiss,
        title = { Text(result.title) },
        text = { Text(result.message) },
        confirmButton = {
            if (result.success) {
                ZToolTextButton(onClick = onSave, text = stringResource(R.string.save_configuration_button))
            }
        },
        dismissButton = {
            ZToolTextButton(onClick = onDismiss, text = stringResource(R.string.restart_no), isPrimary = false)
        }
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
