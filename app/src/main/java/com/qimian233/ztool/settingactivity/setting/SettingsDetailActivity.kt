package com.qimian233.ztool.settingactivity.setting

import android.app.AppOpsManager
import android.app.Dialog
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Add
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
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import com.qimian233.ztool.LoadingDialog
import com.qimian233.ztool.R
import com.qimian233.ztool.data.settings.CustomizeAboutDeviceInfoRepository
import com.qimian233.ztool.data.settings.SettingsDetailRepository
import com.qimian233.ztool.settingactivity.safecenter.RestartConfirmDialog
import com.qimian233.ztool.settingactivity.setting.floatingwindow.FloatingWindow
import com.qimian233.ztool.ui.components.DIALOG_BUTTON_VERTICAL_ARRANGEMENT
import com.qimian233.ztool.ui.components.SettingItem
import com.qimian233.ztool.ui.components.SettingSection
import com.qimian233.ztool.ui.components.ZToolCheckbox
import com.qimian233.ztool.ui.components.ZToolDialog
import com.qimian233.ztool.ui.components.ZToolExtendedFloatingActionButton
import com.qimian233.ztool.ui.components.ZToolScaffold
import com.qimian233.ztool.ui.components.ZToolSettingsList
import com.qimian233.ztool.ui.components.ZToolTextButton
import com.qimian233.ztool.ui.components.ZToolTextInputRow
import com.qimian233.ztool.ui.components.ZToolTopAppBar
import com.qimian233.ztool.ui.components.showPlatformComposeDialog
import com.qimian233.ztool.utils.AppChooserDialog
import com.qimian233.ztool.utils.EmbeddingConfigManager
import com.qimian233.ztool.utils.OvCommonConfigManager
import com.qimian233.ztool.viewmodel.SettingsDetailConfigFlashResult
import com.qimian233.ztool.viewmodel.SettingsDetailFontInstallResult
import com.qimian233.ztool.viewmodel.SettingsDetailFontPreparationResult
import com.qimian233.ztool.viewmodel.SettingsDetailModuleResult
import com.qimian233.ztool.viewmodel.SettingsDetailOvConfigSelectionResult
import com.qimian233.ztool.viewmodel.SettingsDetailRestoreResult
import com.qimian233.ztool.viewmodel.SettingsDetailUiState
import com.qimian233.ztool.viewmodel.SettingsDetailViewModel

@Composable
fun SettingsDetailRoute(
    title: String,
    packageName: String,
    onBack: () -> Unit,
    onOpenStrategySearch: () -> Unit
) {
    val context = LocalContext.current
    val activity = context as? android.app.Activity
    val owner = LocalViewModelStoreOwner.current
        ?: error("SettingsDetailRoute requires a ViewModelStoreOwner")
    val viewModel = remember(owner) {
        ViewModelProvider(
            owner,
            SettingsDetailViewModelFactory(
                SettingsDetailRepository(context.applicationContext),
                CustomizeAboutDeviceInfoRepository(context.applicationContext)
            )
        )[SettingsDetailViewModel::class.java]
    }
    val gotItButtonText = stringResource(R.string.got_it_button)
    val savingConfigText = stringResource(R.string.saving_config)
    val saveSuccessTitleText = stringResource(R.string.success_title)
    val saveSuccessMessageText = stringResource(R.string.save_success_message)
    val errorTitleText = stringResource(R.string.error_title)
    val errorPrefixText = stringResource(R.string.error_prefix)
    val configSuffixText = stringResource(R.string.config_suffix)
    val tipTitleText = stringResource(R.string.tip_title)
    val installModuleFirstText = stringResource(R.string.install_module_first)
    val flashingConfigText = stringResource(R.string.flashing_config)
    val confirmRestoreTitleText = stringResource(R.string.confirm_restore_title)
    val confirmRestoreMessageText = stringResource(R.string.confirm_restore_message)
    val confirmButtonText = stringResource(R.string.confirm_button)
    val restartNoText = stringResource(R.string.restart_no)
    val restoringModuleText = stringResource(R.string.restoring_module)
    val restoreSuccessMessageText = stringResource(R.string.restore_success_message)
    val importingFontText = stringResource(R.string.importing_font)
    val importSuccessTitleText = stringResource(R.string.import_success_title)
    val importSuccessMessageText = stringResource(R.string.import_success_message)
    val importFailedTitleText = stringResource(R.string.import_failed_title)
    val preparingFontFileText = stringResource(R.string.preparing_font_file)
    val unknownFontFilenameText = stringResource(R.string.unknown_font_filename)
    val selectTtfFileText = stringResource(R.string.select_ttf_file)
    val zuiForceSplitTitleText = stringResource(R.string.zui_force_split_title)
    val zuiForceFreeformTitleText = stringResource(R.string.zui_force_freeform_title)
    val zuiForceFixedTitleText = stringResource(R.string.zui_force_fixed_title)
    val installingModuleText = stringResource(R.string.installing_module)
    val removingModuleText = stringResource(R.string.removing_module)
    val installSuccessText = stringResource(R.string.install_success_message)
    val removeSuccessText = stringResource(R.string.remove_success_message)
    val aboutDeviceInfoImageSavedText = stringResource(R.string.about_device_info_image_saved)
    val aboutDeviceInfoImageSaveFailedText = stringResource(R.string.about_device_info_image_save_failed)
    var floatingWindow by remember { mutableStateOf<FloatingWindow?>(null) }
    var loadingDialog by remember { mutableStateOf<LoadingDialog?>(null) }
    val aboutDeviceInfoImageLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            val saved = viewModel.saveAboutDeviceInfoHeaderImage(uri)
            Toast.makeText(
                context,
                if (saved) aboutDeviceInfoImageSavedText else aboutDeviceInfoImageSaveFailedText,
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    fun showMessageDialog(
        dialogTitle: String,
        message: String
    ) {
        if (activity == null) return
        showPlatformComposeDialog(activity, width = ViewGroup.LayoutParams.WRAP_CONTENT) { dialog ->
            SimpleSettingsDetailDialogContent(
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
            SimpleSettingsDetailDialogContent(
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
            Toast.makeText(context, R.string.floating_window_closed, Toast.LENGTH_SHORT).show()
        }
    }

    fun showFloatingWindow() {
        val hostActivity = activity as? androidx.activity.ComponentActivity ?: return
        if (floatingWindow != null) {
            hideFloatingWindow()
            return
        }
        floatingWindow = FloatingWindow.create(hostActivity)
        Toast.makeText(context, R.string.floating_window_started, Toast.LENGTH_SHORT).show()
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
            Toast.makeText(context, R.string.request_usage_stats_permission, Toast.LENGTH_LONG).show()
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
            Toast.makeText(context, R.string.overlay_permission_denied, Toast.LENGTH_SHORT).show()
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
        loadingDialog = LoadingDialog(context).also {
            it.show(savingConfigText)
        }
        viewModel.saveOvConfig(
            configMap = configMap,
            selectedPackages = selectedApps.map { it.packageName },
            mode = mode
        ) { result ->
            activity?.runOnUiThread {
                loadingDialog?.dismiss()
                if (result == "success") {
                    showMessageDialog(
                        dialogTitle = saveSuccessTitleText,
                        message = saveSuccessMessageText
                    )
                } else {
                    showMessageDialog(
                        dialogTitle = errorTitleText,
                        message = errorPrefixText + result
                    )
                }
            }
        }
    }

    fun showConfigSelectionDialog(configs: List<EmbeddingConfigManager.ConfigFileInfo>) {
        if (activity == null) return
        if (configs.isEmpty()) {
            Toast.makeText(context, R.string.no_config_files_prompt, Toast.LENGTH_SHORT).show()
            return
        }
        val flashedConfigs = viewModel.loadFlashedConfigs()
        lateinit var dialog: Dialog
        val shownDialog = showComposeDialog {
            ConfigSelectionDialogContent(
                configs = configs,
                flashedConfigs = flashedConfigs,
                configLabel = { config ->
                    config.timestamp + " " + config.appName + configSuffixText
                },
                onAlreadyFlashedClick = {
                    Toast.makeText(context, R.string.config_already_flashed, Toast.LENGTH_SHORT).show()
                },
                onDelete = { selectedConfigs ->
                    val count = viewModel.deleteEmbeddingConfigs(selectedConfigs, flashedConfigs)
                    Toast.makeText(
                        context,
                        context.resources.getString(R.string.delete_success, count),
                        Toast.LENGTH_SHORT
                    ).show()
                    dialog.dismiss()
                    showConfigSelectionDialog(viewModel.loadEmbeddingConfigFiles())
                },
                onFlash = { selectedConfigs ->
                    dialog.dismiss()
                    if (!viewModel.uiState.value.moduleEnabled) {
                        showMessageDialog(
                            dialogTitle = tipTitleText,
                            message = installModuleFirstText
                        )
                        return@ConfigSelectionDialogContent
                    }
                    loadingDialog = LoadingDialog(context).also {
                        it.show(flashingConfigText)
                    }
                    viewModel.flashEmbeddingConfigs(selectedConfigs) { result ->
                        activity.runOnUiThread {
                            loadingDialog?.dismiss()
                            when (result) {
                                SettingsDetailConfigFlashResult.Success -> {
                                    showMessageDialog(
                                        dialogTitle = saveSuccessTitleText,
                                        message = saveSuccessMessageText
                                    )
                                }
                                is SettingsDetailConfigFlashResult.Failure -> {
                                    showMessageDialog(
                                        dialogTitle = errorTitleText,
                                        message = context.resources.getString(R.string.flash_failed_message, result.message)
                                    )
                                }
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
                        loadingDialog = LoadingDialog(context).also {
                            it.show(restoringModuleText)
                        }
                        viewModel.restoreOriginalModule { result ->
                            activity.runOnUiThread {
                                loadingDialog?.dismiss()
                                when (result) {
                                    SettingsDetailRestoreResult.Success -> {
                                        showMessageDialog(
                                            dialogTitle = saveSuccessTitleText,
                                            message = restoreSuccessMessageText
                                        )
                                    }
                                    is SettingsDetailRestoreResult.Failure -> {
                                        showMessageDialog(
                                            dialogTitle = errorTitleText,
                                            message = result.message
                                        )
                                    }
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
                    is SettingsDetailOvConfigSelectionResult.Success -> {
                        AppChooserDialog.show(
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
                    }
                    is SettingsDetailOvConfigSelectionResult.Failure -> {
                        showMessageDialog(
                            dialogTitle = errorTitleText,
                            message = errorPrefixText + result.message
                        )
                    }
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
                    is SettingsDetailModuleResult.Success -> {
                        showMessageDialog(
                            dialogTitle = tipTitleText,
                            message = if (result.enabled) installSuccessText else removeSuccessText
                        )
                    }
                    is SettingsDetailModuleResult.Failure -> {
                        showMessageDialog(
                            dialogTitle = errorTitleText,
                            message = if (result.requestedEnabled) {
                                context.resources.getString(R.string.install_failed_message, result.message)
                            } else {
                                context.resources.getString(R.string.remove_failed_message, result.message)
                            }
                        )
                    }
                }
            }
        }
    }

    lateinit var handleFontSelection: (Uri?) -> Unit
    val fontPickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val data = result.data
        if (result.resultCode == android.app.Activity.RESULT_OK && data != null) {
            handleFontSelection(data.data)
        }
    }

    fun startFontImport(fontName: String, fontDescription: String) {
        if (activity == null) return
        loadingDialog = LoadingDialog(context).also {
            it.show(importingFontText)
        }

        viewModel.installFont(fontName, fontDescription) { result ->
            activity.runOnUiThread {
                loadingDialog?.dismiss()
                when (result) {
                    SettingsDetailFontInstallResult.Success -> {
                        showMessageDialog(
                        dialogTitle = importSuccessTitleText,
                        message = importSuccessMessageText
                    )
                }
                    is SettingsDetailFontInstallResult.Failure -> {
                        showMessageDialog(
                            dialogTitle = importFailedTitleText,
                            message = result.message
                        )
                    }
                }
            }
        }
    }

    fun showFontInputDialog(originalFileName: String) {
        lateinit var dialog: Dialog
        val shownDialog = showComposeDialog {
            FontInputDialogContent(
                originalDescription = stringResource(R.string.default_font_description, originalFileName),
                onConfirm = { name, description ->
                    if (name.isNotEmpty() && description.isNotEmpty()) {
                        dialog.dismiss()
                        startFontImport(name, description)
                    }
                },
                onCancel = { dialog.dismiss() }
            )
        }
        if (shownDialog != null) {
            dialog = shownDialog
        }
    }

    handleFontSelection = { uri ->
        if (uri != null && activity != null) {
            loadingDialog = LoadingDialog(context).also {
                it.show(preparingFontFileText)
            }
            viewModel.prepareFontImport(uri) { result ->
                activity.runOnUiThread {
                    loadingDialog?.dismiss()
                    when (result) {
                        is SettingsDetailFontPreparationResult.Success -> {
                            showFontInputDialog(
                                result.originalFileName ?: unknownFontFilenameText
                            )
                        }
                        is SettingsDetailFontPreparationResult.Failure -> {
                            Toast.makeText(context, "File Error: " + result.message, Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        }
    }

    fun startFontImportProcess() {
        val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
            type = "*/*"
            addCategory(Intent.CATEGORY_OPENABLE)
            putExtra(
                Intent.EXTRA_MIME_TYPES,
                arrayOf("font/ttf", "application/x-font-ttf", "application/octet-stream")
            )
        }
        try {
            fontPickerLauncher.launch(
                Intent.createChooser(intent, selectTtfFileText)
            )
        } catch (_: Exception) {
            Toast.makeText(context, R.string.no_file_manager_found, Toast.LENGTH_SHORT).show()
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

    SettingsDetailScreen(
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
        onFloatMandatoryChanged = viewModel::setFloatMandatory,
        onSplitScreenMandatoryChanged = viewModel::setSplitScreenMandatory,
        onImportFont = ::startFontImportProcess,
        onAllowNativePermissionControllerChanged = viewModel::setAllowNativePermissionController,
        onAllowDisableDolbyChanged = viewModel::setAllowDisableDolby,
        onAppDetailsChanged = viewModel::setAppDetails,
        aboutDeviceInfoState = uiState.aboutDeviceInfoState,
        onAboutDeviceInfoEnabledChanged = viewModel::setAboutDeviceInfoEnabled,
        onAboutDeviceInfoModelEnabledChanged = viewModel::setAboutDeviceInfoModelEnabled,
        onAboutDeviceInfoCpuEnabledChanged = viewModel::setAboutDeviceInfoCpuEnabled,
        onAboutDeviceInfoRamEnabledChanged = viewModel::setAboutDeviceInfoRamEnabled,
        onAboutDeviceInfoRomEnabledChanged = viewModel::setAboutDeviceInfoRomEnabled,
        onAboutDeviceInfoSoftwareEnabledChanged = viewModel::setAboutDeviceInfoSoftwareEnabled,
        onAboutDeviceInfoHeaderEnabledChanged = viewModel::setAboutDeviceInfoHeaderEnabled,
        onAboutDeviceInfoModelChanged = viewModel::setAboutDeviceInfoModel,
        onAboutDeviceInfoCpuChanged = viewModel::setAboutDeviceInfoCpu,
        onAboutDeviceInfoRamChanged = viewModel::setAboutDeviceInfoRam,
        onAboutDeviceInfoRomChanged = viewModel::setAboutDeviceInfoRom,
        onAboutDeviceInfoSoftwareChanged = viewModel::setAboutDeviceInfoSoftware,
        onAboutDeviceInfoHeaderSelected = { aboutDeviceInfoImageLauncher.launch(arrayOf("image/*")) },
        onRestartScope = viewModel::showRestartDialog
    )

    if (uiState.showRestartDialog) {
        RestartConfirmDialog(
            packageName = packageName,
            onConfirm = {
                viewModel.restartScope(packageName)
            },
            onDismiss = viewModel::dismissRestartDialog
        )
    }
}

@Composable
private fun SimpleSettingsDetailDialogContent(
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

private class SettingsDetailViewModelFactory(
    private val repository: SettingsDetailRepository,
    private val aboutDeviceInfoRepository: CustomizeAboutDeviceInfoRepository
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(SettingsDetailViewModel::class.java)) {
            return SettingsDetailViewModel(repository, aboutDeviceInfoRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}

@Composable
private fun SettingsDetailScreen(
    title: String,
    state: SettingsDetailUiState,
    onBack: () -> Unit,
    onRemoveBlacklistChanged: (Boolean) -> Unit,
    onModuleEnabledChanged: (Boolean) -> Unit,
    onStartFloatingWindow: () -> Unit,
    onOpenConfigSelection: () -> Unit,
    onOpenStrategySearch: () -> Unit,
    onZuiForceSplit: () -> Unit,
    onZuiForceFreeform: () -> Unit,
    onZuiForceFixed: () -> Unit,
    onFloatMandatoryChanged: (Boolean) -> Unit,
    onSplitScreenMandatoryChanged: (Boolean) -> Unit,
    onImportFont: () -> Unit,
    onAllowNativePermissionControllerChanged: (Boolean) -> Unit,
    onAllowDisableDolbyChanged: (Boolean) -> Unit,
    onAppDetailsChanged: (Boolean) -> Unit,
    aboutDeviceInfoState: com.qimian233.ztool.data.settings.CustomizeAboutDeviceInfoState,
    onAboutDeviceInfoEnabledChanged: (Boolean) -> Unit,
    onAboutDeviceInfoModelEnabledChanged: (Boolean) -> Unit,
    onAboutDeviceInfoCpuEnabledChanged: (Boolean) -> Unit,
    onAboutDeviceInfoRamEnabledChanged: (Boolean) -> Unit,
    onAboutDeviceInfoRomEnabledChanged: (Boolean) -> Unit,
    onAboutDeviceInfoSoftwareEnabledChanged: (Boolean) -> Unit,
    onAboutDeviceInfoHeaderEnabledChanged: (Boolean) -> Unit,
    onAboutDeviceInfoModelChanged: (String) -> Unit,
    onAboutDeviceInfoCpuChanged: (String) -> Unit,
    onAboutDeviceInfoRamChanged: (String) -> Unit,
    onAboutDeviceInfoRomChanged: (String) -> Unit,
    onAboutDeviceInfoSoftwareChanged: (String) -> Unit,
    onAboutDeviceInfoHeaderSelected: (Uri) -> Unit,
    onRestartScope: () -> Unit
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
                icon = {
                    Icon(
                        imageVector = Icons.Rounded.Refresh,
                        contentDescription = null
                    )
                },
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
                    .padding(bottom = 88.dp)
            ) {
                ZToolSettingsList(
                    sections = settingsDetailSections(
                        state = state,
                        onRemoveBlacklistChanged = onRemoveBlacklistChanged,
                        onModuleEnabledChanged = onModuleEnabledChanged,
                        onStartFloatingWindow = onStartFloatingWindow,
                        onOpenConfigSelection = onOpenConfigSelection,
                        onOpenStrategySearch = onOpenStrategySearch,
                        onZuiForceSplit = onZuiForceSplit,
                        onZuiForceFreeform = onZuiForceFreeform,
                        onZuiForceFixed = onZuiForceFixed,
                        onFloatMandatoryChanged = onFloatMandatoryChanged,
                        onSplitScreenMandatoryChanged = onSplitScreenMandatoryChanged,
                        onImportFont = onImportFont,
                        onAllowNativePermissionControllerChanged = onAllowNativePermissionControllerChanged,
                        onAllowDisableDolbyChanged = onAllowDisableDolbyChanged,
                        onAppDetailsChanged = onAppDetailsChanged,
                        aboutDeviceInfoState = aboutDeviceInfoState,
                        onAboutDeviceInfoEnabledChanged = onAboutDeviceInfoEnabledChanged,
                        onAboutDeviceInfoModelEnabledChanged = onAboutDeviceInfoModelEnabledChanged,
                        onAboutDeviceInfoCpuEnabledChanged = onAboutDeviceInfoCpuEnabledChanged,
                        onAboutDeviceInfoRamEnabledChanged = onAboutDeviceInfoRamEnabledChanged,
                        onAboutDeviceInfoRomEnabledChanged = onAboutDeviceInfoRomEnabledChanged,
                        onAboutDeviceInfoSoftwareEnabledChanged = onAboutDeviceInfoSoftwareEnabledChanged,
                        onAboutDeviceInfoHeaderEnabledChanged = onAboutDeviceInfoHeaderEnabledChanged,
                        onAboutDeviceInfoModelChanged = onAboutDeviceInfoModelChanged,
                        onAboutDeviceInfoCpuChanged = onAboutDeviceInfoCpuChanged,
                        onAboutDeviceInfoRamChanged = onAboutDeviceInfoRamChanged,
                        onAboutDeviceInfoRomChanged = onAboutDeviceInfoRomChanged,
                        onAboutDeviceInfoSoftwareChanged = onAboutDeviceInfoSoftwareChanged,
                        onAboutDeviceInfoHeaderSelected = onAboutDeviceInfoHeaderSelected,
                    )
                )
            }
        }
    }
}

@Composable
private fun settingsDetailSections(
    state: SettingsDetailUiState,
    onRemoveBlacklistChanged: (Boolean) -> Unit,
    onModuleEnabledChanged: (Boolean) -> Unit,
    onStartFloatingWindow: () -> Unit,
    onOpenConfigSelection: () -> Unit,
    onOpenStrategySearch: () -> Unit,
    onZuiForceSplit: () -> Unit,
    onZuiForceFreeform: () -> Unit,
    onZuiForceFixed: () -> Unit,
    onFloatMandatoryChanged: (Boolean) -> Unit,
    onSplitScreenMandatoryChanged: (Boolean) -> Unit,
    onImportFont: () -> Unit,
    onAllowNativePermissionControllerChanged: (Boolean) -> Unit,
    onAllowDisableDolbyChanged: (Boolean) -> Unit,
    onAppDetailsChanged: (Boolean) -> Unit,
    aboutDeviceInfoState: com.qimian233.ztool.data.settings.CustomizeAboutDeviceInfoState,
    onAboutDeviceInfoEnabledChanged: (Boolean) -> Unit,
    onAboutDeviceInfoModelEnabledChanged: (Boolean) -> Unit,
    onAboutDeviceInfoCpuEnabledChanged: (Boolean) -> Unit,
    onAboutDeviceInfoRamEnabledChanged: (Boolean) -> Unit,
    onAboutDeviceInfoRomEnabledChanged: (Boolean) -> Unit,
    onAboutDeviceInfoSoftwareEnabledChanged: (Boolean) -> Unit,
    onAboutDeviceInfoHeaderEnabledChanged: (Boolean) -> Unit,
    onAboutDeviceInfoModelChanged: (String) -> Unit,
    onAboutDeviceInfoCpuChanged: (String) -> Unit,
    onAboutDeviceInfoRamChanged: (String) -> Unit,
    onAboutDeviceInfoRomChanged: (String) -> Unit,
    onAboutDeviceInfoSoftwareChanged: (String) -> Unit,
    onAboutDeviceInfoHeaderSelected: (Uri) -> Unit,
): List<SettingSection> {
    return buildList {
        add(
            SettingSection(
                title = stringResource(R.string.embedding_setting_title),
                items = listOf(
                    SettingItem.Switch(
                        title = stringResource(R.string.embedding_setting_removeBlacklist),
                        summary = stringResource(R.string.embedding_setting_removeBlacklist_summary),
                        checked = state.removeBlacklist,
                        onCheckedChange = onRemoveBlacklistChanged
                    ),
                    SettingItem.Switch(
                        title = stringResource(R.string.RoleModule_Title),
                        summary = stringResource(R.string.RoleModule_Summary),
                        checked = state.moduleEnabled,
                        onCheckedChange = onModuleEnabledChanged
                    ),
                    settingsDetailActionItem(
                        title = stringResource(R.string.custom_landscape_view),
                        summary = stringResource(R.string.custom_landscape_view_summary),
                        onClick = onStartFloatingWindow,
                        icon = {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.OpenInNew,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    ),
                    settingsDetailActionItem(
                        title = stringResource(R.string.custom_landscapeResult_Title),
                        summary = stringResource(R.string.custom_landscapeResult_Summary),
                        onClick = onOpenConfigSelection
                    ),
                    settingsDetailActionItem(
                        title = stringResource(R.string.YiShiJieRules),
                        summary = stringResource(R.string.YiShiJieRules_Summary),
                        onClick = onOpenStrategySearch
                    )
                )
            )
        )

        if (state.showZuiForceConfig) {
            add(
                SettingSection(
                    title = stringResource(R.string.zui_force_config_title),
                    items = listOf(
                        SettingItem.Custom(
                            content = {
                                Text(
                                    text = stringResource(R.string.zui_force_config_summary),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
                                )
                            }
                        ),
                        settingsDetailActionItem(
                            title = stringResource(R.string.zui_force_split_title),
                            summary = stringResource(R.string.zui_force_split_summary),
                            onClick = onZuiForceSplit
                        ),
                        settingsDetailActionItem(
                            title = stringResource(R.string.zui_force_freeform_title),
                            summary = stringResource(R.string.zui_force_freeform_summary),
                            onClick = onZuiForceFreeform
                        ),
                        settingsDetailActionItem(
                            title = stringResource(R.string.zui_force_fixed_title),
                            summary = stringResource(R.string.zui_force_fixed_summary),
                            onClick = onZuiForceFixed
                        )
                    )
                )
            )
        } else {
            add(
                SettingSection(
                    title = stringResource(R.string.embedding_Title),
                    items = listOf(
                        SettingItem.Switch(
                            title = stringResource(R.string.Float_app_Mandatory),
                            summary = stringResource(R.string.Float_app_Mandatory_summary),
                            checked = state.floatMandatory,
                            onCheckedChange = onFloatMandatoryChanged
                        ),
                        SettingItem.Switch(
                            title = stringResource(R.string.Split_screen_Mandatory_Title),
                            summary = stringResource(R.string.Split_screen_Mandatory_Summary),
                            checked = state.splitScreenMandatory,
                            onCheckedChange = onSplitScreenMandatoryChanged
                        )
                    )
                )
            )
        }

        add(
            SettingSection(
                title = stringResource(R.string.font_settings_title),
                items = listOf(
                    settingsDetailActionItem(
                        title = stringResource(R.string.import_font_title),
                        summary = stringResource(R.string.import_font_summary),
                        onClick = onImportFont,
                        icon = {
                            Icon(
                                imageVector = Icons.Rounded.Add,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    )
                )
            )
        )

        add(
            SettingSection(
                title = stringResource(R.string.misc),
                items = listOf(
                    SettingItem.Switch(
                        title = stringResource(R.string.NativePermissionController_enable_title),
                        summary = stringResource(R.string.NativePermissionController_enable_summary),
                        checked = state.allowNativePermissionController,
                        onCheckedChange = onAllowNativePermissionControllerChanged
                    ),
                    SettingItem.Switch(
                        title = stringResource(R.string.AllowDisableDolby),
                        summary = stringResource(R.string.AllowDisableDolby_summary),
                        checked = state.allowDisableDolby,
                        onCheckedChange = onAllowDisableDolbyChanged
                    ),
                    SettingItem.Switch(
                        title = stringResource(R.string.app_details_completion),
                        summary = stringResource(R.string.app_details_completion_summary),
                        checked = state.appDetail,
                        onCheckedChange = onAppDetailsChanged
                    ),
                )
            )
        )

        add(
            SettingSection(
                title = stringResource(R.string.about_device_info_title),
                items = buildList {
                    add(
                        SettingItem.Switch(
                            title = stringResource(R.string.about_device_info_master),
                            summary = stringResource(R.string.about_device_info_master_summary),
                            checked = aboutDeviceInfoState.enabled,
                            onCheckedChange = onAboutDeviceInfoEnabledChanged
                        )
                    )
                    if (aboutDeviceInfoState.enabled) {
                        add(
                            SettingItem.Switch(
                                title = stringResource(R.string.about_device_info_model_title),
                                checked = aboutDeviceInfoState.modelEnabled,
                                onCheckedChange = onAboutDeviceInfoModelEnabledChanged
                            )
                        )
                        if (aboutDeviceInfoState.modelEnabled) {
                            add(SettingItem.TextInput(
                                label = stringResource(R.string.about_device_info_model_label),
                                value = aboutDeviceInfoState.model,
                                onValueChange = onAboutDeviceInfoModelChanged
                            ))
                        }
                        add(
                            SettingItem.Switch(
                                title = stringResource(R.string.about_device_info_cpu_title),
                                checked = aboutDeviceInfoState.cpuEnabled,
                                onCheckedChange = onAboutDeviceInfoCpuEnabledChanged
                            )
                        )
                        if (aboutDeviceInfoState.cpuEnabled) {
                            add(SettingItem.TextInput(
                                label = stringResource(R.string.about_device_info_cpu_label),
                                value = aboutDeviceInfoState.cpu,
                                onValueChange = onAboutDeviceInfoCpuChanged
                            ))
                        }
                        add(
                            SettingItem.Switch(
                                title = stringResource(R.string.about_device_info_ram_title),
                                checked = aboutDeviceInfoState.ramEnabled,
                                onCheckedChange = onAboutDeviceInfoRamEnabledChanged
                            )
                        )
                        if (aboutDeviceInfoState.ramEnabled) {
                            add(SettingItem.TextInput(
                                label = stringResource(R.string.about_device_info_ram_label),
                                value = aboutDeviceInfoState.ram,
                                onValueChange = onAboutDeviceInfoRamChanged
                            ))
                        }
                        add(
                            SettingItem.Switch(
                                title = stringResource(R.string.about_device_info_rom_title),
                                checked = aboutDeviceInfoState.romEnabled,
                                onCheckedChange = onAboutDeviceInfoRomEnabledChanged
                            )
                        )
                        if (aboutDeviceInfoState.romEnabled) {
                            add(SettingItem.TextInput(
                                label = stringResource(R.string.about_device_info_rom_label),
                                value = aboutDeviceInfoState.rom,
                                onValueChange = onAboutDeviceInfoRomChanged
                            ))
                        }
                        add(
                            SettingItem.Switch(
                                title = stringResource(R.string.about_device_info_software_title),
                                checked = aboutDeviceInfoState.softwareEnabled,
                                onCheckedChange = onAboutDeviceInfoSoftwareEnabledChanged
                            )
                        )
                        if (aboutDeviceInfoState.softwareEnabled) {
                            add(SettingItem.TextInput(
                                label = stringResource(R.string.about_device_info_software_label),
                                value = aboutDeviceInfoState.software,
                                onValueChange = onAboutDeviceInfoSoftwareChanged
                            ))
                        }
                        add(
                            SettingItem.Switch(
                                title = stringResource(R.string.about_device_info_header_title),
                                checked = aboutDeviceInfoState.headerEnabled,
                                onCheckedChange = onAboutDeviceInfoHeaderEnabledChanged
                            )
                        )
                        if (aboutDeviceInfoState.headerEnabled) {
                            add(
                                settingsDetailActionItem(
                                    title = stringResource(R.string.about_device_info_header_action),
                                    summary = stringResource(R.string.about_device_info_header_action_summary),
                                    onClick = { onAboutDeviceInfoHeaderSelected(Uri.EMPTY) }
                                )
                            )
                        }
                    }
                }
            )
        )

    }
}

@Composable
private fun settingsDetailActionItem(
    title: String,
    summary: String,
    onClick: () -> Unit,
    icon: (@Composable () -> Unit)? = null
): SettingItem {
    return SettingItem.Action(
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
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    )
}

@Composable
private fun ConfigSelectionDialogContent(
    configs: List<EmbeddingConfigManager.ConfigFileInfo>,
    flashedConfigs: Set<String>,
    configLabel: (EmbeddingConfigManager.ConfigFileInfo) -> String,
    onAlreadyFlashedClick: () -> Unit,
    onDelete: (List<EmbeddingConfigManager.ConfigFileInfo>) -> Unit,
    onFlash: (List<EmbeddingConfigManager.ConfigFileInfo>) -> Unit,
    onRestore: () -> Unit,
    onCancel: () -> Unit
) {
    val selectedIndexes = remember { mutableStateListOf<Int>() }

    ZToolDialog(
        buttonArrangement = DIALOG_BUTTON_VERTICAL_ARRANGEMENT,
        onDismissRequest = onCancel,
        title = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = stringResource(R.string.select_config_files),
                    fontWeight = FontWeight.SemiBold
                )
                if (flashedConfigs.isNotEmpty()) {
                    Text(
                        text = stringResource(R.string.flashed_configs_count, flashedConfigs.size),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
        },
        text = {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 360.dp)
            ) {
                itemsIndexed(configs) { index, config ->
                    val configKey = config.timestamp + "_" + config.packageName
                    val flashed = configKey in flashedConfigs
                    val selected = index in selectedIndexes
                    ConfigSelectionRow(
                        label = configLabel(config),
                        flashed = flashed,
                        selected = selected,
                        onClick = {
                            if (flashed) {
                                onAlreadyFlashedClick()
                            } else if (selected) {
                                selectedIndexes -= index
                            } else {
                                selectedIndexes += index
                            }
                        }
                    )
                }
            }
        },
        confirmButton = {
            ZToolTextButton(
                onClick = {
                    val selectedConfigs = selectedIndexes.map { configs[it] }
                    if (selectedConfigs.isNotEmpty()) onFlash(selectedConfigs)
                },
                text = stringResource(R.string.flashAddedConfig),
                modifier = Modifier.fillMaxWidth()
            )
        },
        dismissButton = {
            Column (verticalArrangement = Arrangement.spacedBy(16.dp)) {
                ZToolTextButton(
                    onClick = {
                        val selectedConfigs = selectedIndexes.map { configs[it] }
                        if (selectedConfigs.isNotEmpty()) onDelete(selectedConfigs)
                    },
                    text = stringResource(R.string.delete),
                    isPrimary = false,
                    modifier = Modifier.fillMaxWidth()
                )
                if (flashedConfigs.isNotEmpty()) {
                    ZToolTextButton(
                        onClick = onRestore,
                        text = stringResource(R.string.restoreModule),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                ZToolTextButton(
                    onClick = onCancel,
                    text = stringResource(R.string.cancel),
                    isPrimary = false,
                    modifier = Modifier.fillMaxWidth())
            }
        }
    )
}

@Composable
private fun ConfigSelectionRow(
    label: String,
    flashed: Boolean,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ZToolCheckbox(
            checked = flashed || selected,
            onCheckedChange = { onClick() },
            enabled = !flashed
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = if (flashed) {
                MaterialTheme.colorScheme.onSurfaceVariant
            } else {
                MaterialTheme.colorScheme.onSurface
            },
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun FontInputDialogContent(
    originalDescription: String,
    onConfirm: (String, String) -> Unit,
    onCancel: () -> Unit
) {
    var fontName by remember { mutableStateOf("") }
    var fontDescription by remember { mutableStateOf(originalDescription) }

    ZToolDialog(
        onDismissRequest = onCancel,
        title = {
            Column {
                Text(
                    text = stringResource(R.string.input_font_info_title),
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = stringResource(R.string.doNotUseDuplicatedFontName),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                ZToolTextInputRow(
                    label = stringResource(R.string.fontName),
                    value = fontName,
                    onValueChange = { fontName = it },
                    singleLine = true
                )
                ZToolTextInputRow(
                    label = stringResource(R.string.fontDescription),
                    value = fontDescription,
                    onValueChange = { fontDescription = it },
                    singleLine = false
                )
            }
        },
        confirmButton = {
            ZToolTextButton(
                onClick = {
                    onConfirm(fontName.trim(), fontDescription.trim())
                },
                text = stringResource(R.string.confirm_button),
                modifier = Modifier.fillMaxWidth()
            )
        },
        dismissButton = {
            ZToolTextButton(
                onClick = onCancel,
                text = stringResource(R.string.cancel),
                isPrimary = false,
                modifier = Modifier.fillMaxWidth()
            )
        }
    )
}
