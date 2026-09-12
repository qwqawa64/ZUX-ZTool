package com.qimian233.ztool.screens.pp

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
import com.qimian233.ztool.data.pp.ZuiPerformanceSettingsRepository
import com.qimian233.ztool.ui.components.SettingItem
import com.qimian233.ztool.ui.components.SettingSection
import com.qimian233.ztool.ui.components.ZToolDialog
import com.qimian233.ztool.ui.components.ZToolExtendedFloatingActionButton
import com.qimian233.ztool.ui.components.ZToolScaffold
import com.qimian233.ztool.ui.components.ZToolSettingsList
import com.qimian233.ztool.ui.components.ZToolTextButton
import com.qimian233.ztool.ui.components.ZToolTopAppBar
import com.qimian233.ztool.viewmodel.ZuiPerformanceSettingsUiState
import com.qimian233.ztool.viewmodel.ZuiPerformanceSettingsViewModel

@Composable
fun ZuiPerformanceSettingsRoute(
    title: String,
    packageName: String,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val owner = LocalViewModelStoreOwner.current
        ?: error("ZuiPerformanceSettingsRoute requires a ViewModelStoreOwner")
    val viewModel = remember(owner) {
        ViewModelProvider(
            owner,
            ZuiPerformanceSettingsViewModelFactory(
                ZuiPerformanceSettingsRepository(context.applicationContext)
            )
        )[ZuiPerformanceSettingsViewModel::class.java]
    }

    LaunchedEffect(viewModel) {
        viewModel.loadSettings()
    }

    val uiState by viewModel.uiState.collectAsState()

    ZuiPerformanceSettingsScreen(
        title = title,
        state = uiState,
        onBack = onBack,
        onBlockPowerPolicySyncChanged = viewModel::setBlockPowerPolicySync,
        onBlockGamePolicyUpdateChanged = viewModel::setBlockGamePolicyUpdate,
        onRestartScope = viewModel::showRestartDialog
    )

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

private class ZuiPerformanceSettingsViewModelFactory(
    private val repository: ZuiPerformanceSettingsRepository
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ZuiPerformanceSettingsViewModel::class.java)) {
            return ZuiPerformanceSettingsViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}

@Composable
private fun ZuiPerformanceSettingsScreen(
    title: String,
    state: ZuiPerformanceSettingsUiState,
    onBack: () -> Unit,
    onBlockPowerPolicySyncChanged: (Boolean) -> Unit,
    onBlockGamePolicyUpdateChanged: (Boolean) -> Unit,
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
                    sections = zuiPerformanceSettingsSections(
                        state = state,
                        onBlockPowerPolicySyncChanged = onBlockPowerPolicySyncChanged,
                        onBlockGamePolicyUpdateChanged = onBlockGamePolicyUpdateChanged
                    ),
                    bottomPadding = 88.dp
                )
            }
        }
    }
}

@Composable
private fun zuiPerformanceSettingsSections(
    state: ZuiPerformanceSettingsUiState,
    onBlockPowerPolicySyncChanged: (Boolean) -> Unit,
    onBlockGamePolicyUpdateChanged: (Boolean) -> Unit
): List<SettingSection> {
    return listOf(
        SettingSection(
            title = stringResource(R.string.zui_pp_setting_title),
            items = listOf(
                SettingItem.Switch(
                    title = stringResource(R.string.zui_pp_block_power_policy_title),
                    summary = stringResource(R.string.zui_pp_block_power_policy_summary),
                    checked = state.blockPowerPolicySync,
                    onCheckedChange = onBlockPowerPolicySyncChanged
                ),
                SettingItem.Switch(
                    title = stringResource(R.string.zui_pp_block_game_policy_title),
                    summary = stringResource(R.string.zui_pp_block_game_policy_summary),
                    checked = state.blockGamePolicyUpdate,
                    onCheckedChange = onBlockGamePolicyUpdateChanged
                )
            )
        ),
        SettingSection(
            title = stringResource(R.string.zui_pp_hint_title),
            items = listOf(
                SettingItem.Custom(
                    content = {
                        androidx.compose.material3.Text(
                            text = stringResource(R.string.zui_pp_hint_content),
                            style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                            color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                    }
                )
            )
        )
    )
}
