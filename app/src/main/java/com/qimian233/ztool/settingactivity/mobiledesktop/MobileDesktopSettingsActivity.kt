package com.qimian233.ztool.settingactivity.mobiledesktop

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
import com.qimian233.ztool.data.mobiledesktop.MobileDesktopRestartResult
import com.qimian233.ztool.data.mobiledesktop.MobileDesktopSettingsRepository
import com.qimian233.ztool.ui.components.SettingItem
import com.qimian233.ztool.ui.components.SettingSection
import com.qimian233.ztool.ui.components.ZToolDialog
import com.qimian233.ztool.ui.components.ZToolExtendedFloatingActionButton
import com.qimian233.ztool.ui.components.ZToolScaffold
import com.qimian233.ztool.ui.components.ZToolSettingsList
import com.qimian233.ztool.ui.components.ZToolTextButton
import com.qimian233.ztool.ui.components.ZToolTopAppBar
import com.qimian233.ztool.viewmodel.MobileDesktopSettingsUiState
import com.qimian233.ztool.viewmodel.MobileDesktopSettingsViewModel

@Composable
fun MobileDesktopSettingsRoute(
    title: String,
    packageName: String,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val owner = LocalViewModelStoreOwner.current
        ?: error("MobileDesktopSettingsRoute requires a ViewModelStoreOwner")
    val viewModel = remember(owner) {
        ViewModelProvider(
            owner,
            MobileDesktopSettingsViewModelFactory(
                MobileDesktopSettingsRepository(context.applicationContext)
            )
        )[MobileDesktopSettingsViewModel::class.java]
    }

    LaunchedEffect(viewModel) {
        viewModel.loadSettings()
    }

    val uiState by viewModel.uiState.collectAsState()

    MobileDesktopSettingsScreen(
        title = title,
        state = uiState,
        onBack = onBack,
        onRestart = viewModel::showRestartConfirmDialog,
        onSkipExposeChanged = viewModel::setSkipExposeWarn,
        onAutoAcceptFileTransferChanged = viewModel::setAutoAcceptFileTransfer
    )

    if (uiState.showRestartConfirmDialog) {
        val restartWarnString = stringResource(R.string.restartFail)
        RestartConfirmDialog(
            packageName = packageName,
            onConfirm = {
                viewModel.restartScope { result ->
                    when (result) {
                        MobileDesktopRestartResult.Success -> {
                            Toast.makeText(context, R.string.restartSuccess, Toast.LENGTH_SHORT).show()
                        }
                        is MobileDesktopRestartResult.Failure -> {
                            Toast.makeText(
                                context,
                                restartWarnString + result.error,
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                }
            },
            onDismiss = viewModel::dismissRestartConfirmDialog
        )
    }
}

private class MobileDesktopSettingsViewModelFactory(
    private val repository: MobileDesktopSettingsRepository
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MobileDesktopSettingsViewModel::class.java)) {
            return MobileDesktopSettingsViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}

@Composable
private fun MobileDesktopSettingsScreen(
    title: String,
    state: MobileDesktopSettingsUiState,
    onSkipExposeChanged: (Boolean) -> Unit,
    onAutoAcceptFileTransferChanged: (Boolean) -> Unit,
    onBack: () -> Unit,
    onRestart: () -> Unit
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
                icon = { Icon(imageVector = Icons.Rounded.Refresh, contentDescription = null) },
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
                    sections = listOf(
                        SettingSection(
                            title = stringResource(R.string.super_connect_scope_title),
                            items = listOf(
                                SettingItem.Switch(
                                    checked = state.skipExposeWarn,
                                    onCheckedChange = onSkipExposeChanged,
                                    title = stringResource(R.string.skip_nearby_exposure_warn),
                                    summary = stringResource(R.string.skip_nearby_exposure_warn_summary)
                                ),
                                SettingItem.Switch(
                                    checked = state.autoAcceptFileTransfer,
                                    onCheckedChange = onAutoAcceptFileTransferChanged,
                                    title = stringResource(R.string.auto_accept_file_transfer),
                                    summary = stringResource(R.string.auto_accept_file_transfer_summary)
                                )
                            )
                        )
                    ),
                    bottomPadding = 96.dp
                )
            }
        }
    }
}

@Composable
private fun RestartConfirmDialog(
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
