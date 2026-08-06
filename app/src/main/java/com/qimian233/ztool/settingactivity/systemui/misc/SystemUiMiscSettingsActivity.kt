package com.qimian233.ztool.settingactivity.systemui.misc

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
import com.qimian233.ztool.data.systemui.SystemUiMiscSettingsRepository
import com.qimian233.ztool.ui.components.SettingItem
import com.qimian233.ztool.ui.components.SettingSection
import com.qimian233.ztool.ui.components.ZToolDialog
import com.qimian233.ztool.ui.components.ZToolExtendedFloatingActionButton
import com.qimian233.ztool.ui.components.ZToolScaffold
import com.qimian233.ztool.ui.components.ZToolSettingsList
import com.qimian233.ztool.ui.components.ZToolTextButton
import com.qimian233.ztool.ui.components.ZToolTopAppBar
import com.qimian233.ztool.data.systemui.SystemUiMiscSettingsUiState
import com.qimian233.ztool.viewmodel.SystemUiMiscSettingsViewModel

@Composable
fun SystemUiMiscSettingsRoute(
    title: String,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val owner = LocalViewModelStoreOwner.current
        ?: error("SystemUiMiscSettingsRoute requires a ViewModelStoreOwner")
    val viewModel = remember(owner) {
        ViewModelProvider(
            owner,
            SystemUiMiscSettingsViewModelFactory(
                SystemUiMiscSettingsRepository(context.applicationContext)
            )
        )[SystemUiMiscSettingsViewModel::class.java]
    }

    LaunchedEffect(viewModel) {
        viewModel.loadSettings()
    }

    val uiState by viewModel.uiState.collectAsState()

    SystemUiMiscSettingsScreen(
        title = title,
        state = uiState,
        onBack = onBack,
        onGuestModeChanged = viewModel::setGuestModeController,
        onDisableBiometricErrorVibrationChanged = viewModel::setDisableBiometricErrorVibration,
        onRestartScope = viewModel::showRestartDialog,
    )

    if (uiState.showRestartDialog) {
        val restartFailString = stringResource(R.string.restartFail)
        RestartScopeDialog(
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
}

private class SystemUiMiscSettingsViewModelFactory(
    private val repository: SystemUiMiscSettingsRepository
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(SystemUiMiscSettingsViewModel::class.java)) {
            return SystemUiMiscSettingsViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}

@Composable
private fun SystemUiMiscSettingsScreen(
    title: String,
    state: SystemUiMiscSettingsUiState,
    onBack: () -> Unit,
    onGuestModeChanged: (Boolean) -> Unit,
    onDisableBiometricErrorVibrationChanged: (Boolean) -> Unit,
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
                    sections = systemUiMiscSettingsSections(
                        state = state,
                        onGuestModeChanged = onGuestModeChanged,
                        onDisableBiometricErrorVibrationChanged = onDisableBiometricErrorVibrationChanged,
                    ),
                    bottomPadding = 96.dp
                )
            }
        }
    }
}

@Composable
private fun systemUiMiscSettingsSections(
    state: SystemUiMiscSettingsUiState,
    onGuestModeChanged: (Boolean) -> Unit,
    onDisableBiometricErrorVibrationChanged: (Boolean) -> Unit,
): List<SettingSection> {
    return listOf(
        SettingSection(
            title = stringResource(R.string.systemUIMisc),
            items = listOf(
                SettingItem.Switch(
                    title = stringResource(R.string.disable_guest_user_enable_title),
                    summary = stringResource(R.string.disable_guest_user_enable_summary),
                    checked = state.guestModeController,
                    onCheckedChange = onGuestModeChanged
                ),
                SettingItem.Switch(
                    title = stringResource(R.string.disable_biometric_error_vibration_title),
                    checked = state.disableBiometricErrorVibration,
                    onCheckedChange = onDisableBiometricErrorVibrationChanged
                )
            )
        )
    )
}

@Composable
private fun RestartScopeDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    ZToolDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.restart_xp_title)) },
        text = {
            Text(
                stringResource(R.string.restart_xp_message_header) +
                    "com.android.systemui，com.zui.wallpapersetting" +
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
