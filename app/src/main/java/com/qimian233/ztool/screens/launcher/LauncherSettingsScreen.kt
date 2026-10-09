package com.qimian233.ztool.screens.launcher

import android.widget.Toast
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import com.qimian233.ztool.R
import com.qimian233.ztool.data.launcher.LauncherPreviewRepository
import com.qimian233.ztool.data.launcher.LauncherRestartResult
import com.qimian233.ztool.data.launcher.LauncherSettingsRepository
import com.qimian233.ztool.ui.components.HighlightAnchorRegistry
import com.qimian233.ztool.ui.components.HighlightController
import com.qimian233.ztool.ui.components.SettingItem
import com.qimian233.ztool.ui.components.SettingSection
import com.qimian233.ztool.ui.components.ZToolDialog
import com.qimian233.ztool.ui.components.ZToolExtendedFloatingActionButton
import com.qimian233.ztool.ui.components.ZToolPopupMenuSettingRow
import com.qimian233.ztool.ui.components.ZToolScaffold
import com.qimian233.ztool.ui.components.ZToolSettingsList
import com.qimian233.ztool.ui.components.ZToolSliderRow
import com.qimian233.ztool.ui.components.ZToolTextButton
import com.qimian233.ztool.ui.components.ZToolTopAppBar
import com.qimian233.ztool.ui.theme.LocalZToolColorScheme
import com.qimian233.ztool.utils.AppChooserDialog
import com.qimian233.ztool.viewmodel.BigFolderTuneKey
import com.qimian233.ztool.viewmodel.ForceStopMode
import com.qimian233.ztool.viewmodel.LauncherSettingsUiState
import com.qimian233.ztool.viewmodel.LauncherSettingsViewModel

@Composable
fun LauncherSettingsRoute(
    title: String,
    packageName: String,
    onBack: () -> Unit,
    targetId: String? = null
) {
    val context = LocalContext.current
    val owner = LocalViewModelStoreOwner.current
        ?: error("LauncherSettingsRoute requires a ViewModelStoreOwner")
    val viewModel = remember(owner) {
        ViewModelProvider(
            owner,
            LauncherSettingsViewModelFactory(
                LauncherSettingsRepository(context.applicationContext),
                LauncherPreviewRepository(context.applicationContext)
            )
        )[LauncherSettingsViewModel::class.java]
    }

    LaunchedEffect(viewModel) {
        viewModel.loadSettings()
    }

    val uiState by viewModel.uiState.collectAsState()
    val forceStopTitleString = stringResource(R.string.launcher_force_stop_title)
    val scrollState = rememberScrollState()
    val highlightRegistry = remember { HighlightAnchorRegistry() }

    HighlightController(
        highlightTargetId = targetId,
        scrollState = scrollState,
        registry = highlightRegistry,
        onConsumed = { }
    ) {
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
            onDisableDockBarChanged = viewModel::setDisableDockBar,
            onLauncherNoLabelModeChanged = viewModel::setLauncherNoLabelMode,
            onLauncherDrawerNoLabelModeChanged = viewModel::setLauncherDrawerNoLabelMode,
            onLauncherHideBluePointChanged = viewModel::setLauncherHideBluePoint,
            onCloudFolderDismissChanged = viewModel::setCloudFolderAutoDismiss,
            onDisableRecentAppDisplayChanged = viewModel::setDisableRecentAppDisplay,
            onForceFreeformEntryChanged = viewModel::setForceFreeformEntry,
            onFreeformKeepAliveEnabledChanged = viewModel::setFreeformKeepAliveEnabled,
            onSelectKeepAlivePackages = {
                val activity = context as? android.app.Activity
                if (activity != null) {
                    AppChooserDialog.show(
                        activity,
                        viewModel.loadUserInstalledPackageNames(),
                        uiState.freeformKeepAlivePackages,
                        context.getString(R.string.launcher_freeform_keep_alive_title),
                        object : AppChooserDialog.AppSelectionCallback {
                            override fun onSelected(selectedApps: List<AppChooserDialog.AppInfo>) {
                                viewModel.setFreeformKeepAlivePackages(
                                    selectedApps.map { it.packageName })
                            }

                            override fun onCancel() = Unit
                        }
                    )
                }
            },
            onLauncherBatchUninstallChanged = viewModel::setLauncherBatchUninstall,
            onBigFolderAlignChanged = viewModel::setBigFolderAlign,
            onBigFolderBlurGuardChanged = viewModel::setBigFolderBlurGuard,
            onBigFolderTuneChanged = viewModel::setBigFolderTune,
            onAppIconUnmaskChanged = viewModel::setAppIconUnmask,
            onAppIconUnmaskDynamicChanged = viewModel::setAppIconUnmaskDynamic,
            onWideGridChanged = viewModel::setWideGrid,
            onWideGridSquareChanged = viewModel::setWideGridSquare,
            onWideGridSideInsetChanged = viewModel::setWideGridSideInset,
            onWideGridParityPreset = viewModel::applyColorOsSideInsetPreset,
            onIconScaleOverrideChanged = viewModel::setIconScaleOverride,
            onIconScaleValueChanged = viewModel::setIconScaleValue,
            scrollState = scrollState,
            highlightRegistry = highlightRegistry
        )
    }

    if (uiState.showRestartConfirmDialog) {
        RestartConfirmDialog(
            packageName = packageName,
            onConfirm = {
                viewModel.forceStopPackage(
                    onResult = { result ->
                        when (result) {
                            is LauncherRestartResult.Failure -> {
                                Toast.makeText(context, R.string.launcher_force_stop_fail, Toast.LENGTH_SHORT).show()
                            }
                            LauncherRestartResult.Success -> {
                                Toast.makeText(context, R.string.launcher_force_stop_success, Toast.LENGTH_SHORT).show()
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
                Toast.makeText(context, R.string.common_no_tip_next_time, Toast.LENGTH_SHORT).show()
            }
        )
    }
}

private class LauncherSettingsViewModelFactory(
    private val repository: LauncherSettingsRepository,
    private val previewRepository: LauncherPreviewRepository
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(LauncherSettingsViewModel::class.java)) {
            return LauncherSettingsViewModel(repository, previewRepository) as T
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
    onLauncherNoLabelModeChanged: (Boolean) -> Unit,
    onLauncherDrawerNoLabelModeChanged: (Boolean) -> Unit,
    onLauncherHideBluePointChanged: (Boolean) -> Unit,
    onCloudFolderDismissChanged: (Boolean) -> Unit,
    onDisableRecentAppDisplayChanged: (Boolean) -> Unit,
    onForceFreeformEntryChanged: (Boolean) -> Unit,
    onFreeformKeepAliveEnabledChanged: (Boolean) -> Unit,
    onSelectKeepAlivePackages: () -> Unit,
    onLauncherBatchUninstallChanged: (Boolean) -> Unit,
    onBigFolderAlignChanged: (Boolean) -> Unit,
    onBigFolderBlurGuardChanged: (Boolean) -> Unit,
    onBigFolderTuneChanged: (BigFolderTuneKey, Int) -> Unit,
    onAppIconUnmaskChanged: (Boolean) -> Unit,
    onAppIconUnmaskDynamicChanged: (Boolean) -> Unit,
    onWideGridChanged: (Boolean) -> Unit,
    onWideGridSquareChanged: (Boolean) -> Unit,
    onWideGridSideInsetChanged: (Int) -> Unit,
    onWideGridParityPreset: () -> Unit,
    onIconScaleOverrideChanged: (Boolean) -> Unit,
    onIconScaleValueChanged: (Float) -> Unit,
    scrollState: ScrollState,
    highlightRegistry: HighlightAnchorRegistry,
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
                text = {Text(stringResource(R.string.common_restart_yes))})
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
                LauncherPreviewCard(
                    config = state.previewConfig,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
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
                        onDisableDockBarChanged = onDisableDockBarChanged,
                        onLauncherNoLabelModeChanged = onLauncherNoLabelModeChanged,
                        onLauncherDrawerNoLabelModeChanged = onLauncherDrawerNoLabelModeChanged,
                        onLauncherHideBluePointChanged = onLauncherHideBluePointChanged,
                        onCloudFolderDismissChanged = onCloudFolderDismissChanged,
                        onDisableRecentAppDisplayChanged = onDisableRecentAppDisplayChanged,
                        onForceFreeformEntryChanged = onForceFreeformEntryChanged,
                        onFreeformKeepAliveEnabledChanged = onFreeformKeepAliveEnabledChanged,
                        onSelectKeepAlivePackages = onSelectKeepAlivePackages,
                        onLauncherBatchUninstallChanged = onLauncherBatchUninstallChanged,
                        onBigFolderAlignChanged = onBigFolderAlignChanged,
                        onBigFolderBlurGuardChanged = onBigFolderBlurGuardChanged,
            onBigFolderTuneChanged = onBigFolderTuneChanged,
                        onAppIconUnmaskChanged = onAppIconUnmaskChanged,
                        onAppIconUnmaskDynamicChanged = onAppIconUnmaskDynamicChanged,
                        onWideGridChanged = onWideGridChanged,
                        onWideGridSquareChanged = onWideGridSquareChanged,
                        onWideGridSideInsetChanged = onWideGridSideInsetChanged,
                        onWideGridParityPreset = onWideGridParityPreset,
                        onIconScaleOverrideChanged = onIconScaleOverrideChanged,
                        onIconScaleValueChanged = onIconScaleValueChanged,
                    ),
                    bottomPadding = 96.dp,
                    highlightRegistry = highlightRegistry
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
    onLauncherNoLabelModeChanged: (Boolean) -> Unit,
    onLauncherDrawerNoLabelModeChanged: (Boolean) -> Unit,
    onLauncherHideBluePointChanged: (Boolean) -> Unit,
    onCloudFolderDismissChanged: (Boolean) -> Unit,
    onDisableRecentAppDisplayChanged: (Boolean) -> Unit,
    onForceFreeformEntryChanged: (Boolean) -> Unit,
    onFreeformKeepAliveEnabledChanged: (Boolean) -> Unit,
    onSelectKeepAlivePackages: () -> Unit,
    onLauncherBatchUninstallChanged: (Boolean) -> Unit,
    onBigFolderAlignChanged: (Boolean) -> Unit,
    onBigFolderBlurGuardChanged: (Boolean) -> Unit,
    onBigFolderTuneChanged: (BigFolderTuneKey, Int) -> Unit,
    onAppIconUnmaskChanged: (Boolean) -> Unit,
    onAppIconUnmaskDynamicChanged: (Boolean) -> Unit,
    onWideGridChanged: (Boolean) -> Unit,
    onWideGridSquareChanged: (Boolean) -> Unit,
    onWideGridSideInsetChanged: (Int) -> Unit,
    onWideGridParityPreset: () -> Unit,
    onIconScaleOverrideChanged: (Boolean) -> Unit,
    onIconScaleValueChanged: (Float) -> Unit,
): List<SettingSection> {

    val launcherLayoutItems = buildList {
        add(
            SettingItem.Switch(
                title = stringResource(R.string.launcher_launcher_no_label_mode_title),
                checked = state.noLabelMode,
                onCheckedChange = onLauncherNoLabelModeChanged,
                key = "launcher_no_label_mode"
            )
        )
        add(
            SettingItem.Switch(
                title = stringResource(R.string.launcher_launcher_drawer_no_label_mode_title),
                checked = state.drawerNoLabelMode,
                onCheckedChange = onLauncherDrawerNoLabelModeChanged,
                key = "launcher_drawer_no_label_mode"
            )
        )
        add(
            SettingItem.Switch(
                title = stringResource(R.string.launcher_launcher_hide_blue_point_title),
                checked = state.hideBluePoint,
                onCheckedChange = onLauncherHideBluePointChanged,
                key = "launcher_hide_blue_point"
            )
        )
        add(
            SettingItem.Switch(
                title = stringResource(R.string.launcher_cloud_folder_auto_dismiss_title),
                checked = state.cloudFolderDismiss,
                onCheckedChange = onCloudFolderDismissChanged,
                key = "launcher_cloud_folder_auto_dismiss"
            )
        )
        add(
            SettingItem.Switch(
                title = stringResource(R.string.launcher_custom_grid_title),
                summary = stringResource(R.string.launcher_custom_grid_summary),
                checked = state.customGridSize,
                onCheckedChange = onCustomGridSizeChanged,
                key = "launcher_custom_grid"
            )
        )
        if (state.customGridSize) {
            add(
                SettingItem.Custom(
                    content = {
                        GridSliderRow(
                            label = stringResource(R.string.launcher_input_row_number_here),
                            value = state.customGridRow,
                            onValueChanged = onCustomGridRowChanged
                        )
                    },
                    key = "launcher_custom_grid_row"
                )
            )
            add(
                SettingItem.Custom(
                    content = {
                        GridSliderRow(
                            label = stringResource(R.string.launcher_input_column_number_here),
                            value = state.customGridColumn,
                            onValueChanged = onCustomGridColumnChanged
                        )
                    },
                    key = "launcher_custom_grid_column"
                )
            )
        }
        add(
            SettingItem.Switch(
                title = stringResource(R.string.launcher_wide_grid_title),
                summary = stringResource(R.string.launcher_wide_grid_summary),
                checked = state.wideGrid,
                onCheckedChange = onWideGridChanged,
                key = "launcher_wide_grid"
            )
        )
        if (state.wideGrid) {
            add(
                SettingItem.Switch(
                    title = stringResource(R.string.launcher_wide_grid_square_title),
                    summary = stringResource(R.string.launcher_wide_grid_square_summary),
                    checked = state.wideGridSquare,
                    onCheckedChange = onWideGridSquareChanged,
                    key = "launcher_wide_grid_square"
                )
            )
            add(
                SettingItem.Slider(
                    title = stringResource(R.string.launcher_wide_grid_side_inset_title),
                    summary = stringResource(R.string.launcher_wide_grid_side_inset_summary),
                    value = state.wideGridSideInset.toFloat(),
                    valueText = stringResource(
                        R.string.launcher_wide_grid_side_inset_value,
                        state.wideGridSideInset
                    ),
                    valueRange = LauncherSettingsRepository.WIDE_GRID_INSET_MIN.toFloat()..
                        LauncherSettingsRepository.WIDE_GRID_INSET_MAX.toFloat(),
                    steps = LauncherSettingsRepository.WIDE_GRID_INSET_MAX -
                        LauncherSettingsRepository.WIDE_GRID_INSET_MIN - 1,
                    enabled = !state.wideGridSquare,
                    onValueChange = { onWideGridSideInsetChanged(it.toInt()) },
                    key = "launcher_wide_grid_side_inset"
                )
            )
            add(
                SettingItem.Action(
                    title = stringResource(R.string.launcher_wide_grid_parity_preset_title),
                    summary = stringResource(R.string.launcher_wide_grid_parity_preset_summary),
                    onClick = onWideGridParityPreset,
                    enabled = !state.wideGridSquare,
                    key = "launcher_wide_grid_parity_preset"
                )
            )
        }
        add(
            SettingItem.Switch(
                title = stringResource(R.string.launcher_icon_scale_override_title),
                summary = stringResource(R.string.launcher_icon_scale_override_summary),
                checked = state.iconScaleOverride,
                onCheckedChange = onIconScaleOverrideChanged,
                key = "launcher_icon_scale_override"
            )
        )
        if (state.iconScaleOverride) {
            add(
                SettingItem.Slider(
                    title = stringResource(R.string.launcher_icon_scale_value_title),
                    value = state.iconScaleValue,
                    valueText = String.format(java.util.Locale.US, "%.1f", state.iconScaleValue),
                    valueRange = LauncherSettingsRepository.ICON_SCALE_MIN..
                        LauncherSettingsRepository.ICON_SCALE_MAX,
                    // 0.1 step from 0.5 to 3.0 -> 25 intermediate steps
                    steps = 25,
                    onValueChange = {
                        onIconScaleValueChanged((it * 10).toInt() / 10f)
                    },
                    key = "launcher_icon_scale_value"
                )
            )
        }
        add(
            SettingItem.Switch(
                title = stringResource(R.string.launcher_big_folder_align_title),
                summary = stringResource(R.string.launcher_big_folder_align_summary),
                checked = state.bigFolderAlign,
                onCheckedChange = onBigFolderAlignChanged,
                key = "launcher_big_folder_align"
            )
        )
        if (state.bigFolderAlign) {
            // Six independent hand tuning offsets; order follows docs/research/big_folder_tuning.md.
            val tuneRange = LauncherSettingsRepository.BIG_FOLDER_TUNE_MIN.toFloat()..
                LauncherSettingsRepository.BIG_FOLDER_TUNE_MAX.toFloat()
            val tuneSteps = (LauncherSettingsRepository.BIG_FOLDER_TUNE_MAX -
                LauncherSettingsRepository.BIG_FOLDER_TUNE_MIN) /
                LauncherSettingsRepository.TUNE_STEP - 1
            add(
                SettingItem.Slider(
                    title = stringResource(R.string.launcher_big_folder_tune_gap_h_title),
                    summary = stringResource(R.string.launcher_big_folder_tune_summary),
                    value = state.bigFolderTuneGapH.toFloat(),
                    valueText = stringResource(
                        R.string.launcher_big_folder_tune_value, state.bigFolderTuneGapH
                    ),
                    valueRange = tuneRange,
                    steps = tuneSteps,
                    onValueChange = {
                        onBigFolderTuneChanged(BigFolderTuneKey.GAP_H, it.toInt())
                    },
                    key = "launcher_big_folder_tune_gap_h"
                )
            )
            add(
                SettingItem.Slider(
                    title = stringResource(R.string.launcher_big_folder_tune_gap_v_title),
                    summary = stringResource(R.string.launcher_big_folder_tune_summary),
                    value = state.bigFolderTuneGapV.toFloat(),
                    valueText = stringResource(
                        R.string.launcher_big_folder_tune_value, state.bigFolderTuneGapV
                    ),
                    valueRange = tuneRange,
                    steps = tuneSteps,
                    onValueChange = {
                        onBigFolderTuneChanged(BigFolderTuneKey.GAP_V, it.toInt())
                    },
                    key = "launcher_big_folder_tune_gap_v"
                )
            )
            add(
                SettingItem.Slider(
                    title = stringResource(R.string.launcher_big_folder_tune_bg_x_title),
                    summary = stringResource(R.string.launcher_big_folder_tune_summary),
                    value = state.bigFolderTuneBgX.toFloat(),
                    valueText = stringResource(
                        R.string.launcher_big_folder_tune_value, state.bigFolderTuneBgX
                    ),
                    valueRange = tuneRange,
                    steps = tuneSteps,
                    onValueChange = {
                        onBigFolderTuneChanged(BigFolderTuneKey.BG_X, it.toInt())
                    },
                    key = "launcher_big_folder_tune_bg_x"
                )
            )
            add(
                SettingItem.Slider(
                    title = stringResource(R.string.launcher_big_folder_tune_bg_y_title),
                    summary = stringResource(R.string.launcher_big_folder_tune_summary),
                    value = state.bigFolderTuneBgY.toFloat(),
                    valueText = stringResource(
                        R.string.launcher_big_folder_tune_value, state.bigFolderTuneBgY
                    ),
                    valueRange = tuneRange,
                    steps = tuneSteps,
                    onValueChange = {
                        onBigFolderTuneChanged(BigFolderTuneKey.BG_Y, it.toInt())
                    },
                    key = "launcher_big_folder_tune_bg_y"
                )
            )
            add(
                SettingItem.Slider(
                    title = stringResource(R.string.launcher_big_folder_tune_shift_x_title),
                    summary = stringResource(R.string.launcher_big_folder_tune_summary),
                    value = state.bigFolderTuneShiftX.toFloat(),
                    valueText = stringResource(
                        R.string.launcher_big_folder_tune_value, state.bigFolderTuneShiftX
                    ),
                    valueRange = tuneRange,
                    steps = tuneSteps,
                    onValueChange = {
                        onBigFolderTuneChanged(BigFolderTuneKey.SHIFT_X, it.toInt())
                    },
                    key = "launcher_big_folder_tune_shift_x"
                )
            )
            add(
                SettingItem.Slider(
                    title = stringResource(R.string.launcher_big_folder_tune_shift_y_title),
                    summary = stringResource(R.string.launcher_big_folder_tune_summary),
                    value = state.bigFolderTuneShiftY.toFloat(),
                    valueText = stringResource(
                        R.string.launcher_big_folder_tune_value, state.bigFolderTuneShiftY
                    ),
                    valueRange = tuneRange,
                    steps = tuneSteps,
                    onValueChange = {
                        onBigFolderTuneChanged(BigFolderTuneKey.SHIFT_Y, it.toInt())
                    },
                    key = "launcher_big_folder_tune_shift_y"
                )
            )
        }
        add(
            SettingItem.Switch(
                title = stringResource(R.string.launcher_big_folder_blur_guard_title),
                summary = stringResource(R.string.launcher_big_folder_blur_guard_summary),
                checked = state.bigFolderBlurGuard,
                onCheckedChange = onBigFolderBlurGuardChanged,
                key = "launcher_big_folder_blur_guard"
            )
        )
        add(
            SettingItem.Switch(
                title = stringResource(R.string.launcher_app_icon_unmask_title),
                summary = stringResource(R.string.launcher_app_icon_unmask_summary),
                checked = state.appIconUnmask,
                onCheckedChange = onAppIconUnmaskChanged,
                key = "launcher_app_icon_unmask"
            )
        )
        if (state.appIconUnmask) {
            add(
                SettingItem.Switch(
                    title = stringResource(R.string.launcher_app_icon_unmask_dynamic_title),
                    summary = stringResource(R.string.launcher_app_icon_unmask_dynamic_summary),
                    checked = state.appIconUnmaskDynamic,
                    onCheckedChange = onAppIconUnmaskDynamicChanged,
                    key = "launcher_app_icon_unmask_dynamic"
                )
            )
        }
    }

    val dockBarLayoutItems = buildList {
        add(
            SettingItem.Switch(
                title = stringResource(R.string.launcher_disable_recent_app_display),
                checked = state.disableRecentAppDisplay,
                onCheckedChange = onDisableRecentAppDisplayChanged,
                key = "launcher_disable_recent_app_display"
            )
        )
        if (!state.disableDockBar) {
            add(
                SettingItem.Switch(
                    title = stringResource(R.string.launcher_larger_dock_title),
                    checked = state.moreBigDock,
                    onCheckedChange = onMoreBigDockChanged,
                    key = "launcher_larger_dock"
                )
            )
        }
        add(
            SettingItem.Switch(
                title = stringResource(R.string.launcher_disable_dock_bar_title),
                summary = stringResource(R.string.launcher_disable_dock_bar_summary),
                checked = state.disableDockBar,
                onCheckedChange = onDisableDockBarChanged,
                key = "launcher_disable_dock_bar"
            )
        )
    }

    val launcherMiscItems = buildList {
        add(
            SettingItem.Custom(
                content = {
                    ForceStopModeRow(
                        selectedMode = state.forceStopMode,
                        onModeChanged = onForceStopModeChanged
                    )
                },
                key = "launcher_force_stop_mode"
            )
        )
        if (state.forceStopMode == ForceStopMode.Whitelist) {
            add(
                SettingItem.Custom(
                    key = "launcher_force_stop_whitelist",
                    content = {
                        WhitelistRow(
                            whitelistCount = state.forceStopWhitelistCount,
                            onClick = onSelectForceStopWhitelist
                        )
                    }
                )
            )
        }
        add(
            SettingItem.Switch(
                title = stringResource(R.string.launcher_force_freeform_entry_title),
                summary = stringResource(R.string.launcher_force_freeform_entry_summary),
                checked = state.forceFreeformEntry,
                onCheckedChange = onForceFreeformEntryChanged,
                key = "launcher_force_freeform_entry"
            )
        )
        add(
            SettingItem.Switch(
                title = stringResource(R.string.launcher_freeform_keep_alive_title),
                summary = stringResource(R.string.launcher_freeform_keep_alive_summary),
                checked = state.freeformKeepAliveEnabled,
                onCheckedChange = onFreeformKeepAliveEnabledChanged,
                key = "freeform_keep_alive_enabled"
            )
        )
        if (state.freeformKeepAliveEnabled) {
            add(
                SettingItem.Custom(
                    key = "freeform_keep_alive_packages",
                    content = {
                        WhitelistRow(
                            whitelistCount = state.freeformKeepAlivePackagesCount,
                            onClick = onSelectKeepAlivePackages,
                            labelRes = R.string.launcher_freeform_keep_alive_apps_summary
                        )
                    }
                )
            )
        }
        add(
            SettingItem.Switch(
                title = stringResource(R.string.launcher_show_ram_info),
                summary = stringResource(R.string.launcher_show_ram_info_summary),
                checked = state.showRamInfo,
                onCheckedChange = onShowRamInfoChanged,
                key = "launcher_show_ram_info"
            )
        )
        if (state.showRamInfo) {
            add(
                SettingItem.Switch(
                    title = stringResource(R.string.launcher_beautify_ram_info),
                    summary = stringResource(R.string.launcher_beautify_ram_info_summary),
                    checked = state.beautifyRamInfo,
                    onCheckedChange = onBeautifyRamInfoChanged,
                    key = "launcher_beautify_ram_info"
                )
            )
        }
        add(
            SettingItem.Switch(
                title = stringResource(R.string.launcher_launcher_batch_uninstall),
                checked = state.launcherBatchUninstall,
                onCheckedChange = onLauncherBatchUninstallChanged,
                key = "launcher_batch_uninstall"
            )
        )
        add(
            SettingItem.Switch(
                title = stringResource(R.string.launcher_clean_search),
                checked = state.cleanGlobalSearch,
                onCheckedChange = onCleanSearchChanged,
                key = "launcher_clean_search"
            )
        )
        if (state.cleanGlobalSearch) {
            add(
                SettingItem.Switch(
                    title = stringResource(R.string.launcher_remove_search_recommend),
                    checked = state.removeSearchRecommend,
                    onCheckedChange = onRemoveSearchRecommendationChanged,
                    key = "launcher_remove_search_recommend"
                )
            )
            add(
                SettingItem.Switch(
                    title = stringResource(R.string.launcher_remove_hot_word_view),
                    checked = state.removeHotWordView,
                    onCheckedChange = onRemoveHotWordViewChanged,
                    key = "launcher_remove_hot_word_view"
                )
            )
        }
    }

    return listOf(
        SettingSection(
            title = stringResource(R.string.launcher_layout_title),
            items = launcherLayoutItems
        ),
        SettingSection(
            title = stringResource(R.string.launcher_dock_title),
            items = dockBarLayoutItems
        ),
        SettingSection(
            title = stringResource(R.string.launcher_misc_title),
            items = launcherMiscItems
        ),
    )
}

@Composable
private fun ForceStopModeRow(
    selectedMode: ForceStopMode,
    onModeChanged: (ForceStopMode) -> Unit
) {
    val options = listOf(
        ForceStopMode.Default to stringResource(R.string.common_select_default),
        ForceStopMode.AllApps to stringResource(R.string.launcher_select_all_app),
        ForceStopMode.Whitelist to stringResource(R.string.common_select_white_list)
    )
    val selectedLabel = options.first { it.first == selectedMode }.second

    ZToolPopupMenuSettingRow(
        title = stringResource(R.string.launcher_disable_force_stop_enable_title),
        summary = stringResource(R.string.launcher_disable_force_stop_enable_summary),
        value = selectedLabel,
        options = options,
        optionLabel = { it.second },
        onOptionSelected = { (mode, _) -> onModeChanged(mode) }
    )
}

@Composable
private fun WhitelistRow(
    whitelistCount: Int,
    onClick: () -> Unit,
    labelRes: Int = R.string.launcher_protected_apps_summary
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = stringResource(labelRes, whitelistCount),
            style = MaterialTheme.typography.bodyMedium,
            color = LocalZToolColorScheme.current.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Icon(
            imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
            contentDescription = null,
            tint = LocalZToolColorScheme.current.onSurfaceVariant
        )
    }
}

@Composable
private fun GridSliderRow(
    label: String,
    value: Int,
    onValueChanged: (Int) -> Unit
) {
    ZToolSliderRow(
        title = label,
        value = value.toFloat(),
        valueText = value.toString(),
        onValueChange = { onValueChanged(it.toInt()) },
        valueRange = 3f..10f,
        steps = 6,
    )
}

@Composable
private fun RestartConfirmDialog(
    packageName: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    ZToolDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.common_restart_xp_title)) },
        text = {
            Text(
                stringResource(R.string.common_restart_xp_message_header) +
                    packageName +
                    stringResource(R.string.common_restart_xp_message)
            )
        },
        confirmButton = {
            ZToolTextButton(onClick = onConfirm, text = stringResource(R.string.common_restart_yes))
        },
        dismissButton = {
            ZToolTextButton(onClick = onDismiss, text = stringResource(R.string.common_restart_no), isPrimary = false)
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
        title = { Text(stringResource(R.string.launcher_disable_dock_warning_title)) },
        text = { Text(stringResource(R.string.launcher_disable_dock_warning_message)) },
        confirmButton = {
            ZToolTextButton(onClick = onConfirm, text = stringResource(R.string.common_confirm))
        },
        dismissButton = {
            ZToolTextButton(onClick = onDoNotShowAgain, text = stringResource(R.string.common_do_not_show_again), isPrimary = false)
        }
    )
}
