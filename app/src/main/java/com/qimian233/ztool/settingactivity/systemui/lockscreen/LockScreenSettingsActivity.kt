package com.qimian233.ztool.settingactivity.systemui.lockscreen

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.qimian233.ztool.R
import com.qimian233.ztool.data.systemui.LockScreenSettingsRepository
import com.qimian233.ztool.ui.components.SettingItem
import com.qimian233.ztool.ui.components.SettingSection
import com.qimian233.ztool.ui.components.ZToolDialog
import com.qimian233.ztool.ui.components.ZToolPopupMenuSettingRow
import com.qimian233.ztool.ui.components.ZToolScaffold
import com.qimian233.ztool.ui.components.ZToolSettingsList
import com.qimian233.ztool.ui.components.ZToolTopAppBar
import com.qimian233.ztool.ui.theme.ZToolTheme
import com.qimian233.ztool.viewmodel.ApiTestResult
import com.qimian233.ztool.viewmodel.LockScreenSettingsUiState
import com.qimian233.ztool.viewmodel.LockScreenSettingsViewModel

class LockScreenSettingsActivity : ComponentActivity() {

    private lateinit var viewModel: LockScreenSettingsViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val appName = intent.getStringExtra("app_name").orEmpty()
        val repository = LockScreenSettingsRepository(applicationContext)
        viewModel = ViewModelProvider(
            this,
            LockScreenSettingsViewModelFactory(repository)
        )[LockScreenSettingsViewModel::class.java]
        viewModel.loadSettings()

        setContent {
            val uiState by viewModel.uiState.collectAsState()

            ZToolTheme {
                LockScreenSettingsScreen(
                    title = appName + stringResource(R.string.lock_screen_settings_title_suffix),
                    state = uiState,
                    onBack = ::finish,
                    onYiYanChanged = viewModel::setYiYanEnabled,
                    onApiAddressChanged = viewModel::setApiAddress,
                    onRegexChanged = viewModel::setRegex,
                    onTestApi = ::testApiConnection,
                    onChargeWattsOptionChanged = viewModel::setChargeWattsOption,
                    onRealWattsIntervalOptionChanged = viewModel::setRealWattsIntervalOption,
                    onRealWattsRefreshIntervalChanged = viewModel::setRealWattsRefreshInterval
                )

                if (uiState.showRootPermissionDialog) {
                    RootPermissionDialog(
                        onConfirm = viewModel::dismissRootPermissionDialog,
                        onDoNotShowAgain = {
                            viewModel.confirmSystemUiPermission()
                            Toast.makeText(this, R.string.no_tip_next_time, Toast.LENGTH_SHORT).show()
                        }
                    )
                }

                uiState.apiTestResult?.let { result ->
                    ApiTestResultDialog(
                        result = result,
                        onSave = {
                            viewModel.saveYiYanConfiguration()
                            Toast.makeText(this, R.string.configuration_saved_message, Toast.LENGTH_SHORT).show()
                        },
                        onDismiss = viewModel::dismissApiTestResult
                    )
                }
            }
        }
    }

    private fun testApiConnection() {
        viewModel.testApiConnection {
            Toast.makeText(this, R.string.please_input_api_address, Toast.LENGTH_SHORT).show()
        }
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
    onApiAddressChanged: (String) -> Unit,
    onRegexChanged: (String) -> Unit,
    onTestApi: () -> Unit,
    onChargeWattsOptionChanged: (String) -> Unit,
    onRealWattsIntervalOptionChanged: (String) -> Unit,
    onRealWattsRefreshIntervalChanged: (String) -> Unit
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
                    sections = lockScreenSettingsSections(
                        state = state,
                        onYiYanChanged = onYiYanChanged,
                        onApiAddressChanged = onApiAddressChanged,
                        onRegexChanged = onRegexChanged,
                        onTestApi = onTestApi,
                        onChargeWattsOptionChanged = onChargeWattsOptionChanged,
                        onRealWattsIntervalOptionChanged = onRealWattsIntervalOptionChanged,
                        onRealWattsRefreshIntervalChanged = onRealWattsRefreshIntervalChanged
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
    onApiAddressChanged: (String) -> Unit,
    onRegexChanged: (String) -> Unit,
    onTestApi: () -> Unit,
    onChargeWattsOptionChanged: (String) -> Unit,
    onRealWattsIntervalOptionChanged: (String) -> Unit,
    onRealWattsRefreshIntervalChanged: (String) -> Unit
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

    return listOf(
        SettingSection(
            title = stringResource(R.string.YiYanTile),
            items = yiYanItems
        ),
        SettingSection(
            title = stringResource(R.string.ChargeWattsTitle),
            items = listOf(
                SettingItem.Custom(
                    content = {
                        ChargeWattsSettingsContent(
                            state = state,
                            onChargeWattsOptionChanged = onChargeWattsOptionChanged,
                            onRealWattsIntervalOptionChanged = onRealWattsIntervalOptionChanged,
                            onRealWattsRefreshIntervalChanged = onRealWattsRefreshIntervalChanged
                        )
                    }
                )
            )
        )
    )
}

@Composable
private fun ChargeWattsSettingsContent(
    state: LockScreenSettingsUiState,
    onChargeWattsOptionChanged: (String) -> Unit,
    onRealWattsIntervalOptionChanged: (String) -> Unit,
    onRealWattsRefreshIntervalChanged: (String) -> Unit
) {
    ZToolPopupMenuSettingRow(
        title = stringResource(R.string.ChargeWattsEnableTitle),
        summary = stringResource(R.string.ChargeWattsSummary),
        options = stringArrayResource(R.array.watt_options).toList(),
        value = state.chargeWattsOption,
        optionLabel = { it },
        onOptionSelected = onChargeWattsOptionChanged
    )

    if (state.chargeWattsOption == stringResource(R.string.watt_option_actual)) {
        ZToolPopupMenuSettingRow(
            title = stringResource(R.string.RealWattsRefreshInterval),
            summary = stringResource(R.string.RealWattsRefreshIntervalSummary),
            options = stringArrayResource(R.array.real_watt_interval).toList(),
            value = state.realWattsIntervalOption,
            optionLabel = { it },
            onOptionSelected = onRealWattsIntervalOptionChanged
        )

        if (state.realWattsIntervalOption == stringResource(R.string.real_watt_custom_refresh_interval_enabled)) {
            OutlinedTextField(
                value = state.realWattsRefreshInterval,
                onValueChange = onRealWattsRefreshIntervalChanged,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 8.dp),
                label = {
                    Text(
                        stringResource(
                            R.string.RealWattsRefreshIntervalInputTip,
                            state.realWattsRefreshInterval.toFloatOrNull() ?: 3.0f
                        )
                    )
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
            )
        }
    }
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
            OutlinedTextField(
                value = apiAddress,
                onValueChange = onApiAddressChanged,
                modifier = Modifier.weight(1f),
                label = { Text(stringResource(R.string.api_address_hint)) },
                singleLine = true
            )
            Spacer(modifier = Modifier.width(12.dp))
            Button(
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
        OutlinedTextField(
            value = regex,
            onValueChange = onRegexChanged,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            label = { Text(stringResource(R.string.regex_label)) },
            singleLine = true
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
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDoNotShowAgain) {
                Text(stringResource(R.string.do_not_show_again))
            }
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
                TextButton(onClick = onSave) {
                    Text(stringResource(R.string.save_configuration_button))
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.restart_no))
            }
        }
    )
}
