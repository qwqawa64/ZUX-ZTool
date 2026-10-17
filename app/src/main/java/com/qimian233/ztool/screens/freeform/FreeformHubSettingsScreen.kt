package com.qimian233.ztool.screens.freeform

import android.app.AppOpsManager
import android.app.Dialog
import android.content.Intent
import android.provider.Settings
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.ScrollState
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
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import com.qimian233.ztool.R
import com.qimian233.ztool.data.freeform.FreeformHubRepository
import com.qimian233.ztool.data.systemui.SystemUiMiscSettingsRepository
import com.qimian233.ztool.screens.zuisetting.floatingwindow.FloatingWindow
import com.qimian233.ztool.ui.components.HighlightAnchorRegistry
import com.qimian233.ztool.ui.components.HighlightController
import com.qimian233.ztool.ui.components.LoadingDialog
import com.qimian233.ztool.ui.components.SettingItem
import com.qimian233.ztool.ui.components.SettingSection
import com.qimian233.ztool.ui.components.ZToolDialog
import com.qimian233.ztool.ui.components.ZToolExtendedFloatingActionButton
import com.qimian233.ztool.ui.components.ZToolRestartSystemDialog
import com.qimian233.ztool.ui.components.ZToolScaffold
import com.qimian233.ztool.ui.components.ZToolSettingsList
import com.qimian233.ztool.ui.components.ZToolTextButton
import com.qimian233.ztool.ui.components.ZToolTopAppBar
import com.qimian233.ztool.ui.components.showPlatformComposeDialog
import com.qimian233.ztool.ui.theme.LocalZToolColorScheme
import com.qimian233.ztool.utils.AppChooserDialog
import com.qimian233.ztool.utils.EmbeddingConfigManager
import com.qimian233.ztool.utils.OvCommonConfigManager
import com.qimian233.ztool.viewmodel.FreeformHubUiState
import com.qimian233.ztool.viewmodel.FreeformHubViewModel
import com.qimian233.ztool.viewmodel.SettingsDetailConfigFlashResult
import com.qimian233.ztool.viewmodel.SettingsDetailModuleResult
import com.qimian233.ztool.viewmodel.SettingsDetailOvConfigSelectionResult
import com.qimian233.ztool.viewmodel.SettingsDetailRestoreResult

/**
 * Consolidated small-window page. Every change applies after a full system reboot,
 * because the set spans framework scopes and the One Vision Magisk mount.
 */
@Composable
fun FreeformHubSettingsRoute(
    title: String,
    onBack: () -> Unit,
    onOpenStrategySearch: () -> Unit,
    targetId: String? = null
) {
    val context = LocalContext.current
    val activity = context as? android.app.Activity
    val owner = LocalViewModelStoreOwner.current
        ?: error("FreeformHubSettingsRoute requires a ViewModelStoreOwner")
    val viewModel = remember(owner) {
        ViewModelProvider(
            owner,
            FreeformHubViewModelFactory(FreeformHubRepository(context.applicationContext))
        )[FreeformHubViewModel::class.java]
    }

    val gotItButtonText = stringResource(R.string.settings_got_it_button)
    val savingConfigText = stringResource(R.string.settings_saving_config)
    val saveSuccessTitleText = stringResource(R.string.settings_success_title)
    val saveSuccessMessageText = stringResource(R.string.settings_save_success_message)
    val errorTitleText = stringResource(R.string.common_error_title)
    val errorPrefixText = stringResource(R.string.common_error_prefix)
    val configSuffixText = stringResource(R.string.settings_config_suffix)
    val tipTitleText = stringResource(R.string.settings_tip_title)
    val installModuleFirstText = stringResource(R.string.settings_install_module_first)
    val flashingConfigText = stringResource(R.string.settings_flashing_config)
    val confirmRestoreTitleText = stringResource(R.string.settings_confirm_restore_title)
    val confirmRestoreMessageText = stringResource(R.string.settings_confirm_restore_message)
    val confirmButtonText = stringResource(R.string.settings_confirm_button)
    val restartNoText = stringResource(R.string.common_restart_no)
    val restoringModuleText = stringResource(R.string.settings_restoring_module)
    val restoreSuccessMessageText = stringResource(R.string.settings_restore_success_message)
    val installingModuleText = stringResource(R.string.settings_installing_module)
    val removingModuleText = stringResource(R.string.settings_removing_module)
    val installSuccessText = stringResource(R.string.settings_install_success_message)
    val removeSuccessText = stringResource(R.string.settings_remove_success_message)
    val zuiForceSplitTitleText = stringResource(R.string.settings_zui_force_split_title)
    val zuiForceFreeformTitleText = stringResource(R.string.settings_zui_force_freeform_title)
    val zuiForceFixedTitleText = stringResource(R.string.settings_zui_force_fixed_title)

    var floatingWindow by remember { mutableStateOf<FloatingWindow?>(null) }
    var loadingDialog by remember { mutableStateOf<LoadingDialog?>(null) }

    fun showMessageDialog(dialogTitle: String, message: String) {
        if (activity == null) return
        showPlatformComposeDialog(activity, width = ViewGroup.LayoutParams.WRAP_CONTENT) { dialog ->
            SimpleMessageDialogContent(
                title = dialogTitle,
                message = message,
                confirmText = gotItButtonText,
                onConfirm = { dialog.dismiss() }
            )
        }
    }

    fun showConfirmDialog(
        dialogTitle: String,
        message: String,
        confirmText: String,
        dismissText: String,
        onConfirm: () -> Unit
    ) {
        if (activity == null) return
        showPlatformComposeDialog(activity, width = ViewGroup.LayoutParams.WRAP_CONTENT) { dialog ->
            SimpleMessageDialogContent(
                title = dialogTitle,
                message = message,
                confirmText = confirmText,
                dismissText = dismissText,
                onConfirm = {
                    dialog.dismiss()
                    onConfirm()
                },
                onDismiss = { dialog.dismiss() }
            )
        }
    }

    fun showComposeDialog(content: @Composable () -> Unit): Dialog? {
        return activity?.let {
            showPlatformComposeDialog(it, width = ViewGroup.LayoutParams.WRAP_CONTENT) {
                content()
            }
        }
    }

    fun hideFloatingWindow() {
        floatingWindow?.hide()
        if (floatingWindow != null) {
            floatingWindow = null
            Toast.makeText(
                context,
                R.string.settings_floating_window_closed,
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    fun showFloatingWindow() {
        val hostActivity = activity as? androidx.activity.ComponentActivity ?: return
        if (floatingWindow != null) {
            hideFloatingWindow()
            return
        }
        floatingWindow = FloatingWindow.create(hostActivity)
        Toast.makeText(
            context,
            R.string.settings_floating_window_started,
            Toast.LENGTH_SHORT
        ).show()
    }

    fun hasUsageStatsPermission(): Boolean {
        val appOps = context.getSystemService(android.content.Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = appOps.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            android.os.Process.myUid(),
            context.packageName
        )
        return mode == AppOpsManager.MODE_ALLOWED
    }

    lateinit var requestOverlayPermission: () -> Unit
    fun startFloatingWindow() {
        if (!Settings.canDrawOverlays(context)) {
            requestOverlayPermission()
            return
        }
        if (!hasUsageStatsPermission()) {
            Toast.makeText(
                context,
                R.string.settings_request_usage_stats_permission,
                Toast.LENGTH_LONG
            ).show()
            context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
            return
        }
        showFloatingWindow()
    }

    val overlayPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (Settings.canDrawOverlays(context)) {
            startFloatingWindow()
        } else {
            Toast.makeText(
                context,
                R.string.settings_overlay_permission_denied,
                Toast.LENGTH_SHORT
            ).show()
        }
    }
    requestOverlayPermission = {
        overlayPermissionLauncher.launch(
            Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                "package:${context.packageName}".toUri()
            )
        )
    }

    fun saveOvConfig(
        configMap: MutableMap<String, OvCommonConfigManager.AppConfig>,
        selectedApps: List<AppChooserDialog.AppInfo>,
        mode: Int
    ) {
        loadingDialog = LoadingDialog(context).also { it.show(savingConfigText) }
        viewModel.saveOvConfig(
            configMap = configMap,
            selectedPackages = selectedApps.map { it.packageName },
            mode = mode
        ) { result ->
            activity?.runOnUiThread {
                loadingDialog?.dismiss()
                if (result == "success") {
                    showMessageDialog(saveSuccessTitleText, saveSuccessMessageText)
                } else {
                    showMessageDialog(errorTitleText, errorPrefixText + result)
                }
            }
        }
    }

    fun showConfigSelectionDialog(configs: List<EmbeddingConfigManager.ConfigFileInfo>) {
        if (activity == null) return
        if (configs.isEmpty()) {
            Toast.makeText(context, R.string.settings_no_config_files_prompt, Toast.LENGTH_SHORT).show()
            return
        }
        val flashedConfigs = viewModel.loadFlashedConfigs()
        lateinit var dialog: Dialog
        val shownDialog = showComposeDialog {
            EmbeddingConfigSelectionDialogContent(
                configs = configs,
                flashedConfigs = flashedConfigs,
                configLabel = { config ->
                    config.timestamp + " " + config.appName + configSuffixText
                },
                onAlreadyFlashedClick = {
                    Toast.makeText(
                        context,
                        R.string.settings_config_already_flashed,
                        Toast.LENGTH_SHORT
                    ).show()
                },
                onDelete = { selectedConfigs ->
                    val count = viewModel.deleteEmbeddingConfigs(selectedConfigs, flashedConfigs)
                    Toast.makeText(
                        context,
                        context.resources.getString(R.string.settings_delete_success, count),
                        Toast.LENGTH_SHORT
                    ).show()
                    dialog.dismiss()
                    showConfigSelectionDialog(viewModel.loadEmbeddingConfigFiles())
                },
                onFlash = { selectedConfigs ->
                    dialog.dismiss()
                    if (!viewModel.uiState.value.moduleEnabled) {
                        showMessageDialog(tipTitleText, installModuleFirstText)
                        return@EmbeddingConfigSelectionDialogContent
                    }
                    loadingDialog = LoadingDialog(context).also { it.show(flashingConfigText) }
                    viewModel.flashEmbeddingConfigs(selectedConfigs) { result ->
                        activity.runOnUiThread {
                            loadingDialog?.dismiss()
                            when (result) {
                                SettingsDetailConfigFlashResult.Success ->
                                    showMessageDialog(saveSuccessTitleText, saveSuccessMessageText)

                                is SettingsDetailConfigFlashResult.Failure -> showMessageDialog(
                                    errorTitleText,
                                    context.resources.getString(
                                        R.string.settings_flash_failed_message,
                                        result.message
                                    )
                                )
                            }
                        }
                    }
                },
                onRestore = {
                    dialog.dismiss()
                    showConfirmDialog(
                        dialogTitle = confirmRestoreTitleText,
                        message = confirmRestoreMessageText,
                        confirmText = confirmButtonText,
                        dismissText = restartNoText
                    ) {
                        loadingDialog = LoadingDialog(context).also { it.show(restoringModuleText) }
                        viewModel.restoreOriginalModule { result ->
                            activity.runOnUiThread {
                                loadingDialog?.dismiss()
                                when (result) {
                                    SettingsDetailRestoreResult.Success -> showMessageDialog(
                                        saveSuccessTitleText,
                                        restoreSuccessMessageText
                                    )

                                    is SettingsDetailRestoreResult.Failure -> showMessageDialog(
                                        errorTitleText,
                                        result.message
                                    )
                                }
                            }
                        }
                    }
                },
                onCancel = { dialog.dismiss() }
            )
        }
        if (shownDialog != null) {
            dialog = shownDialog
        }
    }

    fun openOvConfigDialog(mode: Int, dialogTitle: String) {
        if (activity == null) return
        viewModel.loadOvConfigSelection(mode) { result ->
            activity.runOnUiThread {
                when (result) {
                    is SettingsDetailOvConfigSelectionResult.Success -> AppChooserDialog.show(
                        activity,
                        result.selection.allPackages,
                        result.selection.selectedPackages,
                        dialogTitle,
                        object : AppChooserDialog.AppSelectionCallback {
                            override fun onSelected(selectedApps: List<AppChooserDialog.AppInfo>) {
                                saveOvConfig(result.selection.configMap, selectedApps, mode)
                            }

                            override fun onCancel() = Unit
                        }
                    )

                    is SettingsDetailOvConfigSelectionResult.Failure -> showMessageDialog(
                        errorTitleText,
                        errorPrefixText + result.message
                    )
                }
            }
        }
    }

    fun handleModuleSwitch(isChecked: Boolean) {
        if (activity == null) return
        loadingDialog = LoadingDialog(context).also {
            it.show(if (isChecked) installingModuleText else removingModuleText)
        }
        viewModel.setModuleEnabled(isChecked) { result ->
            activity.runOnUiThread {
                loadingDialog?.dismiss()
                when (result) {
                    SettingsDetailModuleResult.AlreadyEnabled -> Unit
                    is SettingsDetailModuleResult.Success -> showMessageDialog(
                        tipTitleText,
                        if (result.enabled) installSuccessText else removeSuccessText
                    )

                    is SettingsDetailModuleResult.Failure -> showMessageDialog(
                        errorTitleText,
                        if (result.requestedEnabled) {
                            context.resources.getString(
                                R.string.settings_install_failed_message,
                                result.message
                            )
                        } else {
                            context.resources.getString(
                                R.string.settings_remove_failed_message,
                                result.message
                            )
                        }
                    )
                }
            }
        }
    }

    LaunchedEffect(viewModel) {
        viewModel.loadSettings()
    }

    DisposableEffect(Unit) {
        onDispose {
            loadingDialog?.dismiss()
            floatingWindow?.hide()
        }
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
        FreeformHubSettingsScreen(
            title = title,
            state = uiState,
            onBack = onBack,
            onRemoveBlacklistChanged = viewModel::setRemoveBlacklist,
            onModuleEnabledChanged = ::handleModuleSwitch,
            onStartFloatingWindow = ::startFloatingWindow,
            onOpenConfigSelection = {
                showConfigSelectionDialog(viewModel.loadEmbeddingConfigFiles())
            },
            onOpenStrategySearch = onOpenStrategySearch,
            onZuiForceSplit = {
                openOvConfigDialog(
                    OvCommonConfigManager.MODE_SPLIT_SCREEN,
                    zuiForceSplitTitleText
                )
            },
            onZuiForceFreeform = {
                openOvConfigDialog(
                    OvCommonConfigManager.MODE_FREEFORM_FREE,
                    zuiForceFreeformTitleText
                )
            },
            onZuiForceFixed = {
                openOvConfigDialog(
                    OvCommonConfigManager.MODE_FREEFORM_FIXED,
                    zuiForceFixedTitleText
                )
            },
            onEdgeBubbleChanged = viewModel::setEdgeBubbleEnabled,
            onFloatMandatoryChanged = viewModel::setFloatMandatory,
            onSplitScreenMandatoryChanged = viewModel::setSplitScreenMandatory,
            onFreeformFullscreenThresholdChanged = viewModel::setFreeformFullscreenThreshold,
            onFreeformFullscreenWidthPercentChanged = viewModel::setFreeformFullscreenWidthPercent,
            onFreeformFullscreenHeightPercentChanged = viewModel::setFreeformFullscreenHeightPercent,
            onFreeformBottomEdgeResizeChanged = viewModel::setFreeformBottomEdgeResize,
            onCornerHandleAutoHideChanged = viewModel::setCornerHandleAutoHide,
            onFreeformNoForeheadChanged = viewModel::setFreeformNoForehead,
            onForceFreeformEntryChanged = viewModel::setForceFreeformEntry,
            onFreeformKeepAliveEnabledChanged = viewModel::setFreeformKeepAliveEnabled,
            onSelectKeepAlivePackages = {
                val host = context as? android.app.Activity
                if (host != null) {
                    AppChooserDialog.show(
                        host,
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
            onForceRelativeAppFreeformChanged = viewModel::setForceRelativeAppFreeform,
            onRestartSystem = viewModel::showRestartDialog,
            scrollState = scrollState,
            highlightRegistry = highlightRegistry
        )
    }

    if (uiState.showRestartDialog) {
        val restartFailPrefix = stringResource(R.string.common_restart_fail_prefix)
        ZToolRestartSystemDialog(
            title = stringResource(R.string.system_framework_restart_system_title),
            message = stringResource(R.string.system_framework_restart_system_message),
            onConfirm = {
                viewModel.restartSystem { error ->
                    Toast.makeText(
                        context,
                        restartFailPrefix + error,
                        Toast.LENGTH_SHORT
                    ).show()
                }
            },
            onDismiss = viewModel::dismissRestartDialog
        )
    }
}

private class FreeformHubViewModelFactory(
    private val repository: FreeformHubRepository
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(FreeformHubViewModel::class.java)) {
            return FreeformHubViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}

@Composable
private fun FreeformHubSettingsScreen(
    title: String,
    state: FreeformHubUiState,
    onBack: () -> Unit,
    onRemoveBlacklistChanged: (Boolean) -> Unit,
    onModuleEnabledChanged: (Boolean) -> Unit,
    onStartFloatingWindow: () -> Unit,
    onOpenConfigSelection: () -> Unit,
    onOpenStrategySearch: () -> Unit,
    onZuiForceSplit: () -> Unit,
    onZuiForceFreeform: () -> Unit,
    onZuiForceFixed: () -> Unit,
    onEdgeBubbleChanged: (Boolean) -> Unit,
    onFloatMandatoryChanged: (Boolean) -> Unit,
    onSplitScreenMandatoryChanged: (Boolean) -> Unit,
    onFreeformFullscreenThresholdChanged: (Boolean) -> Unit,
    onFreeformFullscreenWidthPercentChanged: (Int) -> Unit,
    onFreeformFullscreenHeightPercentChanged: (Int) -> Unit,
    onFreeformBottomEdgeResizeChanged: (Boolean) -> Unit,
    onCornerHandleAutoHideChanged: (Boolean) -> Unit,
    onFreeformNoForeheadChanged: (Boolean) -> Unit,
    onForceFreeformEntryChanged: (Boolean) -> Unit,
    onFreeformKeepAliveEnabledChanged: (Boolean) -> Unit,
    onSelectKeepAlivePackages: () -> Unit,
    onForceRelativeAppFreeformChanged: (Boolean) -> Unit,
    onRestartSystem: () -> Unit,
    scrollState: ScrollState,
    highlightRegistry: HighlightAnchorRegistry
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
                onClick = onRestartSystem,
                icon = { Icon(Icons.Rounded.Refresh, contentDescription = null) },
                text = { Text(stringResource(R.string.freeform_hub_restart_button)) }
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
                    sections = freeformHubSections(
                        state = state,
                        onRemoveBlacklistChanged = onRemoveBlacklistChanged,
                        onModuleEnabledChanged = onModuleEnabledChanged,
                        onStartFloatingWindow = onStartFloatingWindow,
                        onOpenConfigSelection = onOpenConfigSelection,
                        onOpenStrategySearch = onOpenStrategySearch,
                        onZuiForceSplit = onZuiForceSplit,
                        onZuiForceFreeform = onZuiForceFreeform,
                        onZuiForceFixed = onZuiForceFixed,
                        onEdgeBubbleChanged = onEdgeBubbleChanged,
                        onFloatMandatoryChanged = onFloatMandatoryChanged,
                        onSplitScreenMandatoryChanged = onSplitScreenMandatoryChanged,
                        onFreeformFullscreenThresholdChanged = onFreeformFullscreenThresholdChanged,
                        onFreeformFullscreenWidthPercentChanged = onFreeformFullscreenWidthPercentChanged,
                        onFreeformFullscreenHeightPercentChanged = onFreeformFullscreenHeightPercentChanged,
                        onFreeformBottomEdgeResizeChanged = onFreeformBottomEdgeResizeChanged,
                        onCornerHandleAutoHideChanged = onCornerHandleAutoHideChanged,
                        onFreeformNoForeheadChanged = onFreeformNoForeheadChanged,
                        onForceFreeformEntryChanged = onForceFreeformEntryChanged,
                        onFreeformKeepAliveEnabledChanged = onFreeformKeepAliveEnabledChanged,
                        onSelectKeepAlivePackages = onSelectKeepAlivePackages,
                        onForceRelativeAppFreeformChanged = onForceRelativeAppFreeformChanged,
                    ),
                    bottomPadding = 96.dp,
                    highlightRegistry = highlightRegistry
                )
            }
        }
    }
}

@Composable
private fun freeformHubSections(
    state: FreeformHubUiState,
    onRemoveBlacklistChanged: (Boolean) -> Unit,
    onModuleEnabledChanged: (Boolean) -> Unit,
    onStartFloatingWindow: () -> Unit,
    onOpenConfigSelection: () -> Unit,
    onOpenStrategySearch: () -> Unit,
    onZuiForceSplit: () -> Unit,
    onZuiForceFreeform: () -> Unit,
    onZuiForceFixed: () -> Unit,
    onEdgeBubbleChanged: (Boolean) -> Unit,
    onFloatMandatoryChanged: (Boolean) -> Unit,
    onSplitScreenMandatoryChanged: (Boolean) -> Unit,
    onFreeformFullscreenThresholdChanged: (Boolean) -> Unit,
    onFreeformFullscreenWidthPercentChanged: (Int) -> Unit,
    onFreeformFullscreenHeightPercentChanged: (Int) -> Unit,
    onFreeformBottomEdgeResizeChanged: (Boolean) -> Unit,
    onCornerHandleAutoHideChanged: (Boolean) -> Unit,
    onFreeformNoForeheadChanged: (Boolean) -> Unit,
    onForceFreeformEntryChanged: (Boolean) -> Unit,
    onFreeformKeepAliveEnabledChanged: (Boolean) -> Unit,
    onSelectKeepAlivePackages: () -> Unit,
    onForceRelativeAppFreeformChanged: (Boolean) -> Unit,
): List<SettingSection> {
    return buildList {
        add(
            SettingSection(
                title = stringResource(R.string.settings_embedding_setting_title),
                items = listOf(
                    SettingItem.Switch(
                        key = "settings_detail_remove_blacklist",
                        title = stringResource(R.string.settings_embedding_setting_remove_blacklist),
                        summary = stringResource(R.string.settings_embedding_setting_remove_blacklist_summary),
                        checked = state.removeBlacklist,
                        onCheckedChange = onRemoveBlacklistChanged
                    ),
                    SettingItem.Switch(
                        key = "settings_detail_role_module",
                        title = stringResource(R.string.settings_role_module_title),
                        summary = stringResource(R.string.settings_role_module_summary),
                        checked = state.moduleEnabled,
                        onCheckedChange = onModuleEnabledChanged
                    ),
                    freeformHubActionItem(
                        key = "settings_detail_custom_landscape_view",
                        title = stringResource(R.string.settings_custom_landscape_view),
                        summary = stringResource(R.string.settings_custom_landscape_view_summary),
                        onClick = onStartFloatingWindow,
                        icon = {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.OpenInNew,
                                contentDescription = null,
                                tint = LocalZToolColorScheme.current.onSurfaceVariant
                            )
                        }
                    ),
                    freeformHubActionItem(
                        key = "settings_detail_custom_landscape_result",
                        title = stringResource(R.string.settings_custom_landscape_result_title),
                        summary = stringResource(R.string.settings_custom_landscape_result_summary),
                        onClick = onOpenConfigSelection
                    ),
                    freeformHubActionItem(
                        key = "settings_detail_yi_shi_jie_rules",
                        title = stringResource(R.string.settings_yi_shi_jie_rules),
                        summary = stringResource(R.string.settings_yi_shi_jie_rules_summary),
                        onClick = onOpenStrategySearch
                    )
                )
            )
        )

        add(
            SettingSection(
                title = stringResource(
                    if (state.showZuiForceConfig) {
                        R.string.settings_zui_force_config_title
                    } else {
                        R.string.settings_embedding_title
                    }
                ),
                items = if (state.showZuiForceConfig) {
                    buildList {
                        add(
                            SettingItem.Custom(
                                key = "deco_freeform_hub_force_config_summary",
                                content = {
                                    Text(
                                        text = stringResource(R.string.settings_zui_force_config_summary),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = LocalZToolColorScheme.current.onSurfaceVariant,
                                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
                                    )
                                }
                            )
                        )
                        if (state.forceFreeformEntryHookEnabled) {
                            add(
                                SettingItem.Custom(
                                    key = "deco_freeform_hub_force_config_hook_warning",
                                    content = {
                                        Text(
                                            text = stringResource(R.string.settings_zui_force_config_hook_warning),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = LocalZToolColorScheme.current.onSurfaceVariant,
                                            modifier = Modifier.padding(
                                                horizontal = 24.dp,
                                                vertical = 8.dp
                                            )
                                        )
                                    }
                                )
                            )
                        }
                        add(
                            freeformHubActionItem(
                                key = "settings_detail_zui_force_split",
                                title = stringResource(R.string.settings_zui_force_split_title),
                                summary = stringResource(R.string.settings_zui_force_split_summary),
                                onClick = onZuiForceSplit
                            )
                        )
                        add(
                            freeformHubActionItem(
                                key = "settings_detail_zui_force_freeform",
                                title = stringResource(R.string.settings_zui_force_freeform_title),
                                summary = stringResource(R.string.settings_zui_force_freeform_summary),
                                onClick = onZuiForceFreeform
                            )
                        )
                        add(
                            freeformHubActionItem(
                                key = "settings_detail_zui_force_fixed",
                                title = stringResource(R.string.settings_zui_force_fixed_title),
                                summary = stringResource(R.string.settings_zui_force_fixed_summary),
                                onClick = onZuiForceFixed
                            )
                        )
                        add(
                            SettingItem.Switch(
                                key = "settings_detail_edge_bubble",
                                title = stringResource(R.string.settings_edge_bubble_title),
                                summary = stringResource(R.string.settings_edge_bubble_summary),
                                checked = state.edgeBubbleEnabled,
                                onCheckedChange = onEdgeBubbleChanged
                            )
                        )
                        if (state.edgeBubbleEnabled) {
                            add(
                                SettingItem.Custom(
                                    key = "deco_freeform_hub_edge_bubble_hint",
                                    content = {
                                        Text(
                                            text = stringResource(R.string.settings_edge_bubble_hint),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = LocalZToolColorScheme.current.onSurfaceVariant,
                                            modifier = Modifier.padding(
                                                horizontal = 24.dp,
                                                vertical = 8.dp
                                            )
                                        )
                                    }
                                )
                            )
                        }
                    }
                } else {
                    listOf(
                        SettingItem.Switch(
                            key = "settings_detail_float_app_mandatory",
                            title = stringResource(R.string.settings_float_app_mandatory),
                            summary = stringResource(R.string.settings_float_app_mandatory_summary),
                            checked = state.floatMandatory,
                            onCheckedChange = onFloatMandatoryChanged
                        ),
                        SettingItem.Switch(
                            key = "settings_detail_split_screen_mandatory",
                            title = stringResource(R.string.settings_split_screen_mandatory_title),
                            summary = stringResource(R.string.settings_split_screen_mandatory_summary),
                            checked = state.splitScreenMandatory,
                            onCheckedChange = onSplitScreenMandatoryChanged
                        )
                    )
                }
            )
        )

        add(
            SettingSection(
                title = stringResource(R.string.freeform_hub_system_ui_title),
                items = buildList {
                    add(
                        SettingItem.Switch(
                            key = "system_ui_misc_freeform_fullscreen_threshold",
                            title = stringResource(R.string.system_ui_misc_freeform_fullscreen_threshold_title),
                            summary = stringResource(R.string.system_ui_misc_freeform_fullscreen_threshold_summary),
                            checked = state.freeformFullscreenThreshold,
                            onCheckedChange = onFreeformFullscreenThresholdChanged
                        )
                    )
                    if (state.freeformFullscreenThreshold) {
                        add(
                            SettingItem.Switch(
                                key = "system_ui_misc_freeform_bottom_edge_resize",
                                title = stringResource(R.string.system_ui_misc_freeform_bottom_edge_resize_title),
                                summary = stringResource(R.string.system_ui_misc_freeform_bottom_edge_resize_summary),
                                checked = state.freeformBottomEdgeResize,
                                onCheckedChange = onFreeformBottomEdgeResizeChanged
                            )
                        )
                        add(
                            SettingItem.Custom(
                                key = "deco_freeform_hub_fullscreen_preview",
                                content = {
                                    FreeformFullscreenPreview(
                                        widthPercent = state.freeformFullscreenWidthPercent,
                                        heightPercent = state.freeformFullscreenHeightPercent
                                    )
                                }
                            )
                        )
                        add(
                            SettingItem.Slider(
                                key = "system_ui_misc_freeform_fullscreen_width",
                                title = stringResource(R.string.system_ui_misc_freeform_fullscreen_width_title),
                                summary = stringResource(R.string.system_ui_misc_freeform_fullscreen_width_summary),
                                value = state.freeformFullscreenWidthPercent.toFloat(),
                                valueText = stringResource(
                                    R.string.system_ui_misc_freeform_fullscreen_percent_label,
                                    state.freeformFullscreenWidthPercent
                                ),
                                valueRange = SystemUiMiscSettingsRepository.FREEFORM_FULLSCREEN_PERCENT_MIN.toFloat()..
                                    SystemUiMiscSettingsRepository.FREEFORM_FULLSCREEN_PERCENT_MAX.toFloat(),
                                steps = 9,
                                onValueChange = {
                                    onFreeformFullscreenWidthPercentChanged(it.toInt())
                                }
                            )
                        )
                        add(
                            SettingItem.Slider(
                                key = "system_ui_misc_freeform_fullscreen_height",
                                title = stringResource(R.string.system_ui_misc_freeform_fullscreen_height_title),
                                summary = stringResource(R.string.system_ui_misc_freeform_fullscreen_height_summary),
                                value = state.freeformFullscreenHeightPercent.toFloat(),
                                valueText = stringResource(
                                    R.string.system_ui_misc_freeform_fullscreen_percent_label,
                                    state.freeformFullscreenHeightPercent
                                ),
                                valueRange = SystemUiMiscSettingsRepository.FREEFORM_FULLSCREEN_PERCENT_MIN.toFloat()..
                                    SystemUiMiscSettingsRepository.FREEFORM_FULLSCREEN_PERCENT_MAX.toFloat(),
                                steps = 9,
                                onValueChange = {
                                    onFreeformFullscreenHeightPercentChanged(it.toInt())
                                }
                            )
                        )
                    }
                    add(
                        SettingItem.Switch(
                            key = "system_ui_misc_freeform_no_forehead",
                            title = stringResource(R.string.system_ui_misc_freeform_no_forehead_title),
                            summary = stringResource(R.string.system_ui_misc_freeform_no_forehead_summary),
                            checked = state.freeformNoForehead,
                            onCheckedChange = onFreeformNoForeheadChanged
                        )
                    )
                    add(
                        SettingItem.Switch(
                            key = "system_ui_misc_corner_handle_auto_hide",
                            title = stringResource(R.string.system_ui_misc_corner_handle_auto_hide_title),
                            summary = stringResource(R.string.system_ui_misc_corner_handle_auto_hide_summary),
                            checked = state.cornerHandleAutoHide,
                            onCheckedChange = onCornerHandleAutoHideChanged
                        )
                    )
                }
            )
        )

        add(
            SettingSection(
                title = stringResource(R.string.freeform_hub_launcher_title),
                items = buildList {
                    add(
                        SettingItem.Switch(
                            key = "launcher_force_freeform_entry",
                            title = stringResource(R.string.launcher_force_freeform_entry_title),
                            summary = stringResource(R.string.launcher_force_freeform_entry_summary),
                            checked = state.forceFreeformEntry,
                            onCheckedChange = onForceFreeformEntryChanged
                        )
                    )
                    add(
                        SettingItem.Switch(
                            key = "freeform_keep_alive_enabled",
                            title = stringResource(R.string.launcher_freeform_keep_alive_title),
                            summary = stringResource(R.string.launcher_freeform_keep_alive_summary),
                            checked = state.freeformKeepAliveEnabled,
                            onCheckedChange = onFreeformKeepAliveEnabledChanged
                        )
                    )
                    if (state.freeformKeepAliveEnabled) {
                        add(
                            SettingItem.Custom(
                                key = "freeform_keep_alive_packages",
                                content = {
                                    KeepAlivePackagesRow(
                                        packageCount = state.freeformKeepAlivePackagesCount,
                                        onClick = onSelectKeepAlivePackages
                                    )
                                }
                            )
                        )
                    }
                }
            )
        )

        add(
            SettingSection(
                title = stringResource(R.string.freeform_hub_relative_launch_title),
                items = listOf(
                    SettingItem.Switch(
                        key = "framework_force_relative_app_freeform",
                        title = stringResource(R.string.system_framework_force_relative_app_freeform_title),
                        checked = state.forceRelativeAppFreeform,
                        onCheckedChange = onForceRelativeAppFreeformChanged
                    )
                )
            )
        )
    }
}

@Composable
private fun freeformHubActionItem(
    title: String,
    summary: String,
    onClick: () -> Unit,
    key: String,
    icon: (@Composable () -> Unit)? = null
): SettingItem {
    return SettingItem.Action(
        key = key,
        title = title,
        summary = summary,
        onClick = onClick,
        trailingContent = {
            if (icon != null) {
                icon()
            } else {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                    contentDescription = null,
                    tint = LocalZToolColorScheme.current.onSurfaceVariant
                )
            }
        }
    )
}

@Composable
private fun KeepAlivePackagesRow(
    packageCount: Int,
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
            text = stringResource(R.string.launcher_freeform_keep_alive_apps_summary, packageCount),
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

private enum class ThresholdWinner { Width, Height, Both, None }

@Composable
private fun FreeformFullscreenPreview(
    widthPercent: Int,
    heightPercent: Int
) {
    val configuration = LocalConfiguration.current
    val widthEnabled = widthPercent < 100
    val heightEnabled = heightPercent < 100
    val widthRemaining = if (widthEnabled) {
        (widthPercent - 50).coerceAtLeast(0) * configuration.screenWidthDp
    } else {
        Int.MAX_VALUE
    }
    val heightRemaining = if (heightEnabled) {
        (heightPercent - 50).coerceAtLeast(0) * configuration.screenHeightDp
    } else {
        Int.MAX_VALUE
    }
    val winner = when {
        !widthEnabled && !heightEnabled -> ThresholdWinner.None
        !widthEnabled -> ThresholdWinner.Height
        !heightEnabled -> ThresholdWinner.Width
        widthRemaining < heightRemaining -> ThresholdWinner.Width
        heightRemaining < widthRemaining -> ThresholdWinner.Height
        else -> ThresholdWinner.Both
    }

    Text(
        text = stringResource(
            when (winner) {
                ThresholdWinner.Width -> R.string.system_ui_misc_freeform_fullscreen_preview_width
                ThresholdWinner.Height -> R.string.system_ui_misc_freeform_fullscreen_preview_height
                ThresholdWinner.Both -> R.string.system_ui_misc_freeform_fullscreen_preview_both
                ThresholdWinner.None -> R.string.system_ui_misc_freeform_fullscreen_preview_disabled
            }
        ),
        style = MaterialTheme.typography.bodySmall,
        color = LocalZToolColorScheme.current.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
    )
}

@Composable
private fun SimpleMessageDialogContent(
    title: String,
    message: String,
    confirmText: String,
    dismissText: String? = null,
    onConfirm: () -> Unit,
    onDismiss: (() -> Unit)? = null
) {
    ZToolDialog(
        onDismissRequest = { onDismiss?.invoke() ?: onConfirm() },
        title = { Text(text = title) },
        text = { Text(text = message) },
        confirmButton = {
            ZToolTextButton(onClick = onConfirm, text = confirmText)
        },
        dismissButton = if (dismissText != null && onDismiss != null) {
            {
                ZToolTextButton(
                    onClick = onDismiss,
                    text = dismissText,
                    isPrimary = false
                )
            }
        } else null
    )
}
