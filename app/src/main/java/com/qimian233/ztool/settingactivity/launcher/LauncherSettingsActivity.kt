package com.qimian233.ztool.settingactivity.launcher

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import com.qimian233.ztool.R
import com.qimian233.ztool.data.launcher.LauncherRestartResult
import com.qimian233.ztool.data.launcher.LauncherSettingsRepository
import com.qimian233.ztool.ui.components.SettingItem
import com.qimian233.ztool.ui.components.SettingSection
import com.qimian233.ztool.ui.components.ZToolDialog
import com.qimian233.ztool.ui.components.ZToolExtendedFloatingActionButton
import com.qimian233.ztool.ui.components.ZToolPopupMenuSettingRow
import com.qimian233.ztool.ui.components.ZToolScaffold
import com.qimian233.ztool.ui.components.ZToolSettingsList
import com.qimian233.ztool.ui.components.ZToolSlider
import com.qimian233.ztool.ui.components.ZToolTextButton
import com.qimian233.ztool.ui.components.ZToolTopAppBar
import com.qimian233.ztool.utils.AppChooserDialog
import com.qimian233.ztool.viewmodel.ForceStopMode
import com.qimian233.ztool.viewmodel.LauncherSettingsUiState
import com.qimian233.ztool.viewmodel.LauncherSettingsViewModel

@Composable
fun LauncherSettingsRoute(
    title: String,
    packageName: String,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val owner = LocalViewModelStoreOwner.current
        ?: error("LauncherSettingsRoute requires a ViewModelStoreOwner")
    val viewModel = remember(owner) {
        ViewModelProvider(
            owner,
            LauncherSettingsViewModelFactory(
                LauncherSettingsRepository(context.applicationContext)
            )
        )[LauncherSettingsViewModel::class.java]
    }

    LaunchedEffect(viewModel) {
        viewModel.loadSettings()
    }

    val uiState by viewModel.uiState.collectAsState()
    val forceStopTitleString = stringResource(R.string.force_stop_title)

    LauncherSettingsScreen(
        title = title,
        state = uiState,
        onBack = onBack,
        onRestart = viewModel::showRestartConfirmDialog,
        onForceStopModeChanged = viewModel::setForceStopMode,
        onSelectForceStopWhitelist = {
            val activity = context as? android.app.Activity
            if (activity != null) {
                AppChooserDialog.show(
                    activity,
                    viewModel.loadUserInstalledPackageNames(),
                    uiState.forceStopWhitelist,
                    forceStopTitleString,
                    object : AppChooserDialog.AppSelectionCallback {
                        override fun onSelected(selectedApps: List<AppChooserDialog.AppInfo>) {
                            viewModel.setForceStopWhitelist(selectedApps.map { it.packageName })
                        }

                        override fun onCancel() = Unit
                    }
                )
            }
        },
        onMoreBigDockChanged = viewModel::setMoreBigDock,
        onCustomGridSizeChanged = viewModel::setCustomGridSize,
        onCustomGridRowChanged = viewModel::setCustomGridRow,
        onCustomGridColumnChanged = viewModel::setCustomGridColumn,
        onCleanSearchChanged = viewModel::setCleanSearch,
        onRemoveSearchRecommendationChanged = viewModel::setRemoveSearchRecommend,
        onRemoveHotWordViewChanged = viewModel::setRemoveHotWordView,
        onShowRamInfoChanged = viewModel::setShowRamInfo,
        onBeautifyRamInfoChanged = viewModel::setBeautifyRamInfo,
        onDisableDockBarChanged = viewModel::setDisableDockBar
    )

    if (uiState.showRestartConfirmDialog) {
        RestartConfirmDialog(
            packageName = packageName,
            onConfirm = {
                viewModel.forceStopPackage(
                    packageName = packageName,
                    onResult = { result ->
                        when (result) {
                            LauncherRestartResult.EmptyPackageName -> Unit
                            is LauncherRestartResult.Failure -> {
                                Toast.makeText(context, R.string.force_stop_fail, Toast.LENGTH_SHORT).show()
                            }
                            LauncherRestartResult.Success -> {
                                Toast.makeText(context, R.string.force_stop_success, Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                )
            },
            onDismiss = viewModel::dismissRestartConfirmDialog
        )
    }

    if (uiState.showDisableDockWarningDialog) {
        DisableDockWarningDialog(
            onConfirm = viewModel::dismissDisableDockWarningDialog,
            onDoNotShowAgain = {
                viewModel.confirmDisableDockWarning()
                Toast.makeText(context, R.string.no_tip_next_time, Toast.LENGTH_SHORT).show()
            }
        )
    }
}

private class LauncherSettingsViewModelFactory(
    private val repository: LauncherSettingsRepository
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(LauncherSettingsViewModel::class.java)) {
            return LauncherSettingsViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}

@Composable
private fun LauncherSettingsScreen(
    title: String,
    state: LauncherSettingsUiState,
    onBack: () -> Unit,
    onRestart: () -> Unit,
    onForceStopModeChanged: (ForceStopMode) -> Unit,
    onSelectForceStopWhitelist: () -> Unit,
    onMoreBigDockChanged: (Boolean) -> Unit,
    onCustomGridSizeChanged: (Boolean) -> Unit,
    onCustomGridRowChanged: (Int) -> Unit,
    onCustomGridColumnChanged: (Int) -> Unit,
    onCleanSearchChanged: (Boolean) -> Unit,
    onRemoveSearchRecommendationChanged: (Boolean) -> Unit,
    onRemoveHotWordViewChanged: (Boolean) -> Unit,
    onShowRamInfoChanged: (Boolean) -> Unit,
    onBeautifyRamInfoChanged: (Boolean) -> Unit,
    onDisableDockBarChanged: (Boolean) -> Unit,
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
                    sections = launcherSettingsSections(
                        state = state,
                        onForceStopModeChanged = onForceStopModeChanged,
                        onSelectForceStopWhitelist = onSelectForceStopWhitelist,
                        onMoreBigDockChanged = onMoreBigDockChanged,
                        onCustomGridSizeChanged = onCustomGridSizeChanged,
                        onCustomGridRowChanged = onCustomGridRowChanged,
                        onCustomGridColumnChanged = onCustomGridColumnChanged,
                        onCleanSearchChanged = onCleanSearchChanged,
                        onRemoveSearchRecommendationChanged = onRemoveSearchRecommendationChanged,
                        onRemoveHotWordViewChanged = onRemoveHotWordViewChanged,
                        onShowRamInfoChanged = onShowRamInfoChanged,
                        onBeautifyRamInfoChanged = onBeautifyRamInfoChanged,
                        onDisableDockBarChanged = onDisableDockBarChanged
                    ),
                    bottomPadding = 96.dp
                )
            }
        }
    }
}

@Composable
private fun launcherSettingsSections(
    state: LauncherSettingsUiState,
    onForceStopModeChanged: (ForceStopMode) -> Unit,
    onSelectForceStopWhitelist: () -> Unit,
    onMoreBigDockChanged: (Boolean) -> Unit,
    onCustomGridSizeChanged: (Boolean) -> Unit,
    onCustomGridRowChanged: (Int) -> Unit,
    onCustomGridColumnChanged: (Int) -> Unit,
    onCleanSearchChanged: (Boolean) -> Unit,
    onRemoveSearchRecommendationChanged: (Boolean) -> Unit,
    onRemoveHotWordViewChanged: (Boolean) -> Unit,
    onShowRamInfoChanged: (Boolean) -> Unit,
    onBeautifyRamInfoChanged: (Boolean) -> Unit,
    onDisableDockBarChanged: (Boolean) -> Unit,
): List<SettingSection> {
    val forceStopItems = buildList {
        add(
            SettingItem.Custom(
                content = {
                    ForceStopModeRow(
                        selectedMode = state.forceStopMode,
                        onModeChanged = onForceStopModeChanged
                    )
                }
            )
        )
        if (state.forceStopMode == ForceStopMode.Whitelist) {
            add(
                SettingItem.Custom(
                    content = {
                        WhitelistRow(
                            whitelistCount = state.forceStopWhitelistCount,
                            onClick = onSelectForceStopWhitelist
                        )
                    }
                )
            )
        }
    }

    val launcherLayoutItems = buildList {
        add(
            SettingItem.Switch(
                title = stringResource(R.string.customGridTitle),
                summary = stringResource(R.string.customGridSummary),
                checked = state.customGridSize,
                onCheckedChange = onCustomGridSizeChanged
            )
        )
        if (state.customGridSize) {
            add(
                SettingItem.Custom(
                    content = {
                        GridSliderRows(
                            row = state.customGridRow,
                            column = state.customGridColumn,
                            onRowChanged = onCustomGridRowChanged,
                            onColumnChanged = onCustomGridColumnChanged
                        )
                    }
                )
            )
        }
    }

    val cleanGlobalSearchLayoutItems = buildList {
        add(
            SettingItem.Switch(
                title = stringResource(R.string.clean_search),
                summary = stringResource(R.string.clean_search_summary),
                checked = state.cleanGlobalSearch,
                onCheckedChange = onCleanSearchChanged
            )
        )
        if (state.cleanGlobalSearch) {
            add(
                SettingItem.Switch(
                    title = stringResource(R.string.remove_search_recommend),
                    checked = state.removeSearchRecommend,
                    onCheckedChange = onRemoveSearchRecommendationChanged
                )
            )
            add(
                SettingItem.Switch(
                    title = stringResource(R.string.remove_hot_word_view),
                    checked = state.removeHotWordView,
                    onCheckedChange = onRemoveHotWordViewChanged
                )
            )
        }
    }

    val ramInfoLayoutItems = buildList {
        add(
            SettingItem.Switch(
                title = stringResource(R.string.show_ram_info),
                summary = stringResource(R.string.show_ram_info_summary),
                checked = state.showRamInfo,
                onCheckedChange = onShowRamInfoChanged
            )
        )
        if (state.showRamInfo) {
            add(
                SettingItem.Switch(
                    title = stringResource(R.string.beautify_ram_info),
                    summary = stringResource(R.string.beautify_ram_info_summary),
                    checked = state.beautifyRamInfo,
                    onCheckedChange = onBeautifyRamInfoChanged
                )
            )
        }
    }

    val dockBarLayoutItems = buildList {
        add(
            SettingItem.Switch(
                title = stringResource(R.string.disable_dock_bar_title),
                summary = stringResource(R.string.disable_dock_bar_summary),
                checked = state.disableDockBar,
                onCheckedChange = onDisableDockBarChanged
            )
        )
        if (!state.disableDockBar) {
            add(
                SettingItem.Switch(
                    title = stringResource(R.string.moreBig_dockTitle),
                    summary = stringResource(R.string.moreBig_dockSummary),
                    checked = state.moreBigDock,
                    onCheckedChange = onMoreBigDockChanged
                )
            )
        }
    }

    return listOf(
        SettingSection(
            title = stringResource(R.string.disable_force_stop_title),
            items = forceStopItems
        ),
        SettingSection(
            title = stringResource(R.string.dock_Title),
            items = dockBarLayoutItems
        ),
        SettingSection(
            title = stringResource(R.string.recent_task),
            items = ramInfoLayoutItems
        ),
        SettingSection(
            title = stringResource(R.string.global_search),
            items = cleanGlobalSearchLayoutItems
        ),
        SettingSection(
            title = stringResource(R.string.customLauncherLayoutTitle),
            items = launcherLayoutItems
        ),
    )
}

@Composable
private fun ForceStopModeRow(
    selectedMode: ForceStopMode,
    onModeChanged: (ForceStopMode) -> Unit
) {
    val options = listOf(
        ForceStopMode.Default to stringResource(R.string.SelectDefault),
        ForceStopMode.AllApps to stringResource(R.string.SelectAllAPP),
        ForceStopMode.Whitelist to stringResource(R.string.SelectWhiteList)
    )
    val selectedLabel = options.first { it.first == selectedMode }.second

    ZToolPopupMenuSettingRow(
        title = stringResource(R.string.disable_force_stop_enable_title),
        summary = stringResource(R.string.disable_force_stop_enable_summary),
        value = selectedLabel,
        options = options,
        optionLabel = { it.second },
        onOptionSelected = { (mode, _) -> onModeChanged(mode) }
    )
}

@Composable
private fun WhitelistRow(
    whitelistCount: Int,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = stringResource(R.string.protected_apps_summary, whitelistCount),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Icon(
            imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun GridSliderRows(
    row: Int,
    column: Int,
    onRowChanged: (Int) -> Unit,
    onColumnChanged: (Int) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 16.dp)
    ) {
        GridSliderRow(
            label = stringResource(R.string.inputRowNumberHere),
            value = row,
            onValueChanged = onRowChanged
        )
        Spacer(modifier = Modifier.height(32.dp))
        GridSliderRow(
            label = stringResource(R.string.inputColumnNumberHere),
            value = column,
            onValueChanged = onColumnChanged
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.launcher_grid_current_option, row, column),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(vertical = 8.dp)
        )
    }
}

@Composable
private fun GridSliderRow(
    label: String,
    value: Int,
    onValueChanged: (Int) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.width(64.dp)
        )
        ZToolSlider(
            value = value.toFloat(),
            onValueChange = { onValueChanged(it.toInt()) },
            valueRange = 3f..10f,
            steps = 6,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = value.toString(),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier
                .width(40.dp)
                .padding(start = 12.dp)
        )
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

@Composable
private fun DisableDockWarningDialog(
    onConfirm: () -> Unit,
    onDoNotShowAgain: () -> Unit
) {
    ZToolDialog(
        onDismissRequest = onConfirm,
        title = { Text(stringResource(R.string.disable_dock_warning_title)) },
        text = { Text(stringResource(R.string.disable_dock_warning_message)) },
        confirmButton = {
            ZToolTextButton(onClick = onConfirm, text = stringResource(R.string.confirm))
        },
        dismissButton = {
            ZToolTextButton(onClick = onDoNotShowAgain, text = stringResource(R.string.do_not_show_again), isPrimary = false)
        }
    )
}
