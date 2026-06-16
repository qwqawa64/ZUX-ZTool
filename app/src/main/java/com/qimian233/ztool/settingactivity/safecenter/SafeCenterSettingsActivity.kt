package com.qimian233.ztool.settingactivity.safecenter

import android.widget.Toast
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
import com.qimian233.ztool.data.safecenter.SafeCenterRestartResult
import com.qimian233.ztool.data.safecenter.SafeCenterSettingsRepository
import com.qimian233.ztool.ui.components.SettingItem
import com.qimian233.ztool.ui.components.SettingSection
import com.qimian233.ztool.ui.components.ZToolDialog
import com.qimian233.ztool.ui.components.ZToolExtendedFloatingActionButton
import com.qimian233.ztool.ui.components.ZToolScaffold
import com.qimian233.ztool.ui.components.ZToolSettingsList
import com.qimian233.ztool.ui.components.ZToolTextButton
import com.qimian233.ztool.ui.components.ZToolTopAppBar
import com.qimian233.ztool.viewmodel.SafeCenterSettingsUiState
import com.qimian233.ztool.viewmodel.SafeCenterSettingsViewModel

@Composable
fun SafeCenterSettingsRoute(
    title: String,
    packageName: String,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val owner = LocalViewModelStoreOwner.current
        ?: error("SafeCenterSettingsRoute requires a ViewModelStoreOwner")
    val viewModel = remember(owner) {
        ViewModelProvider(
            owner,
            SafeCenterSettingsViewModelFactory(
                SafeCenterSettingsRepository(context.applicationContext)
            )
        )[SafeCenterSettingsViewModel::class.java]
    }

    LaunchedEffect(viewModel) {
        viewModel.loadSettings()
    }

    val uiState by viewModel.uiState.collectAsState()

    SafeCenterSettingsScreen(
        title = title,
        state = uiState,
        onBack = onBack,
        onRestart = viewModel::showRestartConfirmDialog,
        onDefaultEnableAutorunChanged = viewModel::setDefaultEnableAutorun,
        onDisableAllVirusScanChanged = viewModel::setDisableAllVirusScan,
        onDocumentsUiBypassChanged = viewModel::setDocumentsUiBypass
    )

    if (uiState.showRestartConfirmDialog) {
        RestartConfirmDialog(
            packageName = packageName,
            onConfirm = {
                viewModel.restartPackages(
                    packageName = packageName,
                    onResult = { result ->
                        when (result) {
                            SafeCenterRestartResult.EmptyPackageName -> {
                                Toast.makeText(
                                    context,
                                    R.string.empty_package_name_message,
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                            is SafeCenterRestartResult.Failure -> {
                                Toast.makeText(
                                    context,
                                    context.getString(R.string.restart_fail_prefix) + result.error,
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                            SafeCenterRestartResult.Success -> {
                                Toast.makeText(
                                    context,
                                    R.string.app_process_restarted_message,
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    }
                )
            },
            onDismiss = viewModel::dismissRestartConfirmDialog
        )
    }
}

internal class SafeCenterSettingsViewModelFactory(
    private val repository: SafeCenterSettingsRepository
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(SafeCenterSettingsViewModel::class.java)) {
            return SafeCenterSettingsViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}

@Composable
internal fun SafeCenterSettingsScreen(
    title: String,
    state: SafeCenterSettingsUiState,
    onBack: () -> Unit,
    onRestart: () -> Unit,
    onDefaultEnableAutorunChanged: (Boolean) -> Unit,
    onDisableAllVirusScanChanged: (Boolean) -> Unit,
    onDocumentsUiBypassChanged: (Boolean) -> Unit
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
                onClick = onRestart,
                icon = {Icon(imageVector = Icons.Rounded.Refresh, contentDescription = null)},
                text = {Text(stringResource(R.string.restart_yes))})
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
                    sections = safeCenterSettingsSections(
                        state = state,
                        onDefaultEnableAutorunChanged = onDefaultEnableAutorunChanged,
                        onDisableAllVirusScanChanged = onDisableAllVirusScanChanged,
                        onDocumentsUiBypassChanged = onDocumentsUiBypassChanged
                    ),
                    bottomPadding = 96.dp
                )
            }
        }
    }
}

@Composable
internal fun safeCenterSettingsSections(
    state: SafeCenterSettingsUiState,
    onDefaultEnableAutorunChanged: (Boolean) -> Unit,
    onDisableAllVirusScanChanged: (Boolean) -> Unit,
    onDocumentsUiBypassChanged: (Boolean) -> Unit
): List<SettingSection> {
    return listOf(
        SettingSection(
            title = stringResource(R.string.default_allow_autorun_title),
            items = listOf(
                SettingItem.Switch(
                    title = stringResource(R.string.default_allow_autorun_enable_title),
                    summary = stringResource(R.string.default_allow_autorun_enable_summary),
                    checked = state.defaultEnableAutorun,
                    onCheckedChange = onDefaultEnableAutorunChanged
                ),
            )
        ),
        SettingSection(
            title = stringResource(R.string.sec_title_function),
            items = listOf(
                SettingItem.Switch(
                    title = stringResource(R.string.disable_all_virus_scan),
                    summary = stringResource(R.string.disable_all_virus_scan_summary),
                    checked = state.disableAllVirusScan,
                    onCheckedChange = onDisableAllVirusScanChanged
                ),
                SettingItem.Switch(
                    title = stringResource(R.string.bypassDocementsUI),
                    summary = stringResource(R.string.bypassDocementsUISummary),
                    checked = state.documentsUiBypass,
                    onCheckedChange = onDocumentsUiBypassChanged
                )
            )
        )
    )
}

@Composable
internal fun RestartConfirmDialog(
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
                    ", com.android.documentsui" +
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
