package com.qimian233.ztool.screens.tbengine

import android.widget.Toast
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import com.qimian233.ztool.R
import com.qimian233.ztool.data.tbengine.TbEngineSettingsRepository
import com.qimian233.ztool.ui.components.SettingItem
import com.qimian233.ztool.ui.components.SettingSection
import com.qimian233.ztool.ui.components.ZToolDialog
import com.qimian233.ztool.ui.components.ZToolExtendedFloatingActionButton
import com.qimian233.ztool.ui.components.ZToolScaffold
import com.qimian233.ztool.ui.components.ZToolSettingsList
import com.qimian233.ztool.ui.components.ZToolTextButton
import com.qimian233.ztool.ui.components.ZToolTopAppBar
import com.qimian233.ztool.viewmodel.TbEngineSettingsUiState
import com.qimian233.ztool.viewmodel.TbEngineSettingsViewModel

@Composable
fun TbEngineSettingsRoute(
    title: String,
    packageName: String,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val owner = LocalViewModelStoreOwner.current
        ?: error("TbEngineSettingsRoute requires a ViewModelStoreOwner")
    val viewModel = remember(owner) {
        ViewModelProvider(
            owner,
            TbEngineSettingsViewModelFactory(
                TbEngineSettingsRepository(context.applicationContext)
            )
        )[TbEngineSettingsViewModel::class.java]
    }

    LaunchedEffect(viewModel) {
        viewModel.loadSettings()
    }

    val uiState by viewModel.uiState.collectAsState()

    TbEngineSettingsScreen(
        title = title,
        state = uiState,
        onBack = onBack,
        onDisableAutoDownloadChanged = viewModel::setDisableAutoDownload,
        onDisableAutoInstallChanged = viewModel::setDisableAutoInstall,
        onDisableAppUpdateChanged = viewModel::setDisableAppUpdate,
        onDisablePushChanged = viewModel::setDisablePush,
        onRestartScope = viewModel::showRestartDialog
    )

    uiState.errorDialogMessage?.let { message ->
        ZToolDialog(
            onDismissRequest = viewModel::dismissErrorDialog,
            title = { Text(stringResource(R.string.common_error_title)) },
            text = { Text(message) },
            confirmButton = {
                ZToolTextButton(
                    onClick = viewModel::dismissErrorDialog,
                    text = stringResource(R.string.common_confirm)
                )
            }
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

private class TbEngineSettingsViewModelFactory(
    private val repository: TbEngineSettingsRepository
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(TbEngineSettingsViewModel::class.java)) {
            return TbEngineSettingsViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}

@Composable
private fun TbEngineSettingsScreen(
    title: String,
    state: TbEngineSettingsUiState,
    onBack: () -> Unit,
    onDisableAutoDownloadChanged: (Boolean) -> Unit,
    onDisableAutoInstallChanged: (Boolean) -> Unit,
    onDisableAppUpdateChanged: (Boolean) -> Unit,
    onDisablePushChanged: (Boolean) -> Unit,
    onRestartScope: () -> Unit
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
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 24.dp)
            ) {
                ZToolSettingsList(
                    sections = tbEngineSettingsSections(
                        state = state,
                        onDisableAutoDownloadChanged = onDisableAutoDownloadChanged,
                        onDisableAutoInstallChanged = onDisableAutoInstallChanged,
                        onDisableAppUpdateChanged = onDisableAppUpdateChanged,
                        onDisablePushChanged = onDisablePushChanged
                    ),
                    bottomPadding = 88.dp
                )
            }
        }
    }
}

@Composable
private fun tbEngineSettingsSections(
    state: TbEngineSettingsUiState,
    onDisableAutoDownloadChanged: (Boolean) -> Unit,
    onDisableAutoInstallChanged: (Boolean) -> Unit,
    onDisableAppUpdateChanged: (Boolean) -> Unit,
    onDisablePushChanged: (Boolean) -> Unit
): List<SettingSection> {
    return listOf(
        SettingSection(
            title = stringResource(R.string.tb_engine_setting_title),
            items = listOf(
                SettingItem.Switch(
                    title = stringResource(R.string.tb_engine_disable_auto_download_title),
                    summary = stringResource(R.string.tb_engine_disable_auto_download_summary),
                    checked = state.disableAutoDownload,
                    onCheckedChange = onDisableAutoDownloadChanged
                ),
                SettingItem.Switch(
                    title = stringResource(R.string.tb_engine_disable_auto_install_title),
                    summary = stringResource(R.string.tb_engine_disable_auto_install_summary),
                    checked = state.disableAutoInstall,
                    onCheckedChange = onDisableAutoInstallChanged
                ),
                SettingItem.Switch(
                    title = stringResource(R.string.tb_engine_disable_app_update_title),
                    summary = stringResource(R.string.tb_engine_disable_app_update_summary),
                    checked = state.disableAppUpdate,
                    onCheckedChange = onDisableAppUpdateChanged
                ),
                SettingItem.Switch(
                    title = stringResource(R.string.tb_engine_disable_push_title),
                    summary = stringResource(R.string.tb_engine_disable_push_summary),
                    checked = state.disablePush,
                    onCheckedChange = onDisablePushChanged
                )
            )
        ),
        SettingSection(
            title = stringResource(R.string.tb_engine_notice_title),
            items = listOf(
                SettingItem.Custom(
                    content = {
                        Text(
                            text = stringResource(R.string.tb_engine_notice_summary),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 24.dp, vertical = 16.dp)
                        )
                    }
                )
            )
        )
    )
}
