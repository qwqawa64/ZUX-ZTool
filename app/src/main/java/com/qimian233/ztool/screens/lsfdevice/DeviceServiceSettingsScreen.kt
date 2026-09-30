package com.qimian233.ztool.screens.lsfdevice

import android.widget.Toast
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import com.qimian233.ztool.data.lsfdevice.DeviceServiceSettingsRepository
import com.qimian233.ztool.ui.components.HighlightAnchorRegistry
import com.qimian233.ztool.ui.components.HighlightController
import com.qimian233.ztool.ui.components.SettingItem
import com.qimian233.ztool.ui.components.SettingSection
import com.qimian233.ztool.ui.components.ZToolDialog
import com.qimian233.ztool.ui.components.ZToolExtendedFloatingActionButton
import com.qimian233.ztool.ui.components.ZToolScaffold
import com.qimian233.ztool.ui.components.ZToolSettingsList
import com.qimian233.ztool.ui.components.ZToolTextButton
import com.qimian233.ztool.ui.components.ZToolTopAppBar
import com.qimian233.ztool.viewmodel.DeviceServiceSettingsUiState
import com.qimian233.ztool.viewmodel.DeviceServiceSettingsViewModel

@Composable
fun DeviceServiceSettingsRoute(
    title: String,
    packageName: String,
    onBack: () -> Unit,
    targetId: String? = null
) {
    val context = LocalContext.current
    val owner = LocalViewModelStoreOwner.current
        ?: error("DeviceServiceSettingsRoute requires a ViewModelStoreOwner")
    val viewModel = remember(owner) {
        ViewModelProvider(
            owner,
            DeviceServiceSettingsViewModelFactory(
                DeviceServiceSettingsRepository(context.applicationContext)
            )
        )[DeviceServiceSettingsViewModel::class.java]
    }

    LaunchedEffect(viewModel) {
        viewModel.loadSettings()
    }

    val uiState by viewModel.uiState.collectAsState()
    val scrollState = rememberScrollState()
    val highlightRegistry = remember { HighlightAnchorRegistry() }

    HighlightController(
        highlightTargetId = targetId,
        scrollState = scrollState,
        registry = highlightRegistry,
        onConsumed = { }
    ) {
        DeviceServiceSettingsScreen(
            title = title,
            state = uiState,
            onBack = onBack,
            onDisablePushChanged = viewModel::setDisablePush,
            onDisableAutoInstallChanged = viewModel::setDisableAutoInstall,
            onDisableAppListReportingChanged = viewModel::setDisableAppListReporting,
            onRestartScope = viewModel::showRestartDialog,
            scrollState = scrollState,
            highlightRegistry = highlightRegistry
        )
    }

    if (uiState.showRestartDialog) {
        ZToolDialog(
            onDismissRequest = viewModel::dismissRestartDialog,
            title = { Text(stringResource(R.string.common_restart_xp_title)) },
            text = {
                Text(
                    stringResource(R.string.common_restart_xp_message_header) +
                            packageName + " " +
                            stringResource(R.string.common_restart_xp_message)
                )
            },
            confirmButton = {
                ZToolTextButton(
                    onClick = {
                        viewModel.restartScope(
                            onFailure = {
                                Toast.makeText(
                                    context,
                                    R.string.system_update_restart_failed,
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        )
                    },
                    text = stringResource(R.string.common_restart_yes)
                )
            },
            dismissButton = {
                ZToolTextButton(
                    onClick = viewModel::dismissRestartDialog,
                    text = stringResource(R.string.common_restart_no),
                    isPrimary = false
                )
            }
        )
    }
}

private class DeviceServiceSettingsViewModelFactory(
    private val repository: DeviceServiceSettingsRepository
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(DeviceServiceSettingsViewModel::class.java)) {
            return DeviceServiceSettingsViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}

@Composable
private fun DeviceServiceSettingsScreen(
    title: String,
    state: DeviceServiceSettingsUiState,
    onBack: () -> Unit,
    onDisablePushChanged: (Boolean) -> Unit,
    onDisableAutoInstallChanged: (Boolean) -> Unit,
    onDisableAppListReportingChanged: (Boolean) -> Unit,
    onRestartScope: () -> Unit,
    scrollState: ScrollState,
    highlightRegistry: HighlightAnchorRegistry
) {
    ZToolScaffold(
        topBar = {
            ZToolTopAppBar(
                title = title,
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = null)
                    }
                }
            )
        },
        floatingActionButton = {
            ZToolExtendedFloatingActionButton(
                onClick = onRestartScope,
                icon = { Icon(Icons.Rounded.Refresh, contentDescription = null) },
                text = { Text(stringResource(R.string.common_restart_yes)) }
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
                    .verticalScroll(scrollState)
                    .padding(horizontal = 24.dp, vertical = 24.dp)
            ) {
                ZToolSettingsList(
                    sections = deviceServiceSettingsSections(
                        state = state,
                        onDisablePushChanged = onDisablePushChanged,
                        onDisableAutoInstallChanged = onDisableAutoInstallChanged,
                        onDisableAppListReportingChanged = onDisableAppListReportingChanged
                    ),
                    bottomPadding = 88.dp,
                    highlightRegistry = highlightRegistry
                )
            }
        }
    }
}

@Composable
private fun deviceServiceSettingsSections(
    state: DeviceServiceSettingsUiState,
    onDisablePushChanged: (Boolean) -> Unit,
    onDisableAutoInstallChanged: (Boolean) -> Unit,
    onDisableAppListReportingChanged: (Boolean) -> Unit
): List<SettingSection> {
    return listOf(
        SettingSection(
            title = stringResource(R.string.device_service_setting_title),
            items = listOf(
                SettingItem.Switch(
                    title = stringResource(R.string.device_service_disable_push_title),
                    summary = stringResource(R.string.device_service_disable_push_summary),
                    checked = state.disablePush,
                    onCheckedChange = onDisablePushChanged,
                    key = "device_service_disable_push"
                ),
                SettingItem.Switch(
                    title = stringResource(R.string.device_service_disable_auto_install_title),
                    summary = stringResource(R.string.device_service_disable_auto_install_summary),
                    checked = state.disableAutoInstall,
                    onCheckedChange = onDisableAutoInstallChanged,
                    key = "device_service_disable_auto_install"
                ),
                SettingItem.Switch(
                    title = stringResource(R.string.device_service_disable_app_list_reporting_title),
                    summary = stringResource(R.string.device_service_disable_app_list_reporting_summary),
                    checked = state.disableAppListReporting,
                    onCheckedChange = onDisableAppListReportingChanged,
                    key = "device_service_disable_app_list_reporting"
                )
            )
        )
    )
}
