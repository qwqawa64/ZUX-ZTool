package com.qimian233.ztool.settingactivity.setting

import android.app.AppOpsManager
import android.app.Dialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import com.qimian233.ztool.utils.ZToolComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import com.qimian233.ztool.LoadingDialog
import com.qimian233.ztool.R
import com.qimian233.ztool.data.settings.SettingsDetailRepository
import com.qimian233.ztool.settingactivity.setting.floatingwindow.FloatingWindow
import com.qimian233.ztool.settingactivity.setting.magicwindowsearch.searchPage
import com.qimian233.ztool.ui.components.SettingItem
import com.qimian233.ztool.ui.components.SettingSection
import com.qimian233.ztool.ui.components.ZToolDialog
import com.qimian233.ztool.ui.components.ZToolScaffold
import com.qimian233.ztool.ui.components.ZToolSettingsList
import com.qimian233.ztool.ui.components.ZToolTopAppBar
import com.qimian233.ztool.ui.components.showPlatformComposeDialog
import com.qimian233.ztool.ui.theme.ZToolTheme
import com.qimian233.ztool.utils.AppChooserDialog
import com.qimian233.ztool.utils.EmbeddingConfigManager
import com.qimian233.ztool.utils.OvCommonConfigManager
import com.qimian233.ztool.utils.startActivityWithZToolTransition
import com.qimian233.ztool.viewmodel.SettingsDetailConfigFlashResult
import com.qimian233.ztool.viewmodel.SettingsDetailFontInstallResult
import com.qimian233.ztool.viewmodel.SettingsDetailFontPreparationResult
import com.qimian233.ztool.viewmodel.SettingsDetailModuleResult
import com.qimian233.ztool.viewmodel.SettingsDetailOvConfigSelectionResult
import com.qimian233.ztool.viewmodel.SettingsDetailRestoreResult
import com.qimian233.ztool.viewmodel.SettingsDetailUiState
import com.qimian233.ztool.viewmodel.SettingsDetailViewModel
import androidx.core.net.toUri

@Composable
fun SettingsDetailRoute(
    title: String,
    packageName: String,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val activity = context as? android.app.Activity
    val owner = LocalViewModelStoreOwner.current
        ?: error("SettingsDetailRoute requires a ViewModelStoreOwner")
    val viewModel = remember(owner) {
        ViewModelProvider(
            owner,
            SettingsDetailViewModelFactory(SettingsDetailRepository(context.applicationContext))
        )[SettingsDetailViewModel::class.java]
    }
    var floatingWindow by remember { mutableStateOf<FloatingWindow?>(null) }
    var loadingDialog by remember { mutableStateOf<LoadingDialog?>(null) }

    fun showMessageDialog(
        dialogTitle: String,
        message: String,
        buttonText: String = context.getString(R.string.got_it_button)
    ) {
        if (activity == null) return
        showPlatformComposeDialog(activity) { dialog ->
            SimpleSettingsDetailDialogContent(
                title = dialogTitle,
                message = message,
                confirmText = buttonText,
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
        showPlatformComposeDialog(activity) { dialog ->
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
            showPlatformComposeDialog(it) {
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
        if (activity == null) return
        if (floatingWindow != null) {
            hideFloatingWindow()
            return
        }
        floatingWindow = FloatingWindow(activity)
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
            it.show(context.getString(R.string.saving_config))
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
                        dialogTitle = context.getString(R.string.success_title),
                        message = context.getString(R.string.save_success_message)
                    )
                } else {
                    showMessageDialog(
                        dialogTitle = context.getString(R.string.error_title),
                        message = context.getString(R.string.error_prefix) + result
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
                    config.timestamp + " " + config.appName + context.getString(R.string.config_suffix)
                },
                onAlreadyFlashedClick = {
                    Toast.makeText(context, R.string.config_already_flashed, Toast.LENGTH_SHORT).show()
                },
                onDelete = { selectedConfigs ->
                    val count = viewModel.deleteEmbeddingConfigs(selectedConfigs, flashedConfigs)
                    Toast.makeText(
                        context,
                        context.getString(R.string.delete_success, count),
                        Toast.LENGTH_SHORT
                    ).show()
                    dialog.dismiss()
                    showConfigSelectionDialog(viewModel.loadEmbeddingConfigFiles())
                },
                onFlash = { selectedConfigs ->
                    dialog.dismiss()
                    if (!viewModel.uiState.value.moduleEnabled) {
                        showMessageDialog(
                            dialogTitle = context.getString(R.string.tip_title),
                            message = context.getString(R.string.install_module_first)
                        )
                        return@ConfigSelectionDialogContent
                    }
                    loadingDialog = LoadingDialog(context).also {
                        it.show(context.getString(R.string.flashing_config))
                    }
                    viewModel.flashEmbeddingConfigs(selectedConfigs) { result ->
                        activity.runOnUiThread {
                            loadingDialog?.dismiss()
                            when (result) {
                                SettingsDetailConfigFlashResult.Success -> {
                                    showMessageDialog(
                                        dialogTitle = context.getString(R.string.success_title),
                                        message = context.getString(R.string.flash_success_message)
                                    )
                                }
                                is SettingsDetailConfigFlashResult.Failure -> {
                                    showMessageDialog(
                                        dialogTitle = context.getString(R.string.error_title),
                                        message = context.getString(
                                            R.string.flash_failed_message,
                                            result.message
                                        )
                                    )
                                }
                            }
                        }
                    }
                },
                onRestore = {
                    dialog.dismiss()
                    showConfirmDialog(
                        dialogTitle = context.getString(R.string.confirm_restore_title),
                        message = context.getString(R.string.confirm_restore_message),
                        confirmText = context.getString(R.string.confirm_button),
                        dismissText = context.getString(R.string.restart_no)
                    ) {
                        loadingDialog = LoadingDialog(context).also {
                            it.show(context.getString(R.string.restoring_module))
                        }
                        viewModel.restoreOriginalModule { result ->
                            activity.runOnUiThread {
                                loadingDialog?.dismiss()
                                when (result) {
                                    SettingsDetailRestoreResult.Success -> {
                                        showMessageDialog(
                                            dialogTitle = context.getString(R.string.success_title),
                                            message = context.getString(R.string.restore_success_message)
                                        )
                                    }
                                    is SettingsDetailRestoreResult.Failure -> {
                                        showMessageDialog(
                                            dialogTitle = context.getString(R.string.error_title),
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
        loadingDialog = LoadingDialog(context).also {
            it.show(context.getString(R.string.loading_config))
        }
        viewModel.loadOvConfigSelection(mode) { result ->
            activity.runOnUiThread {
                loadingDialog?.dismiss()
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
                            dialogTitle = context.getString(R.string.error_title),
                            message = context.getString(R.string.error_prefix) + result.message
                        )
                    }
                }
            }
        }
    }

    fun handleModuleSwitch(isChecked: Boolean) {
        if (activity == null) return
        loadingDialog = LoadingDialog(context).also {
            it.show(
                context.getString(
                    if (isChecked) R.string.installing_module else R.string.removing_module
                )
            )
        }
        viewModel.setModuleEnabled(isChecked) { result ->
            activity.runOnUiThread {
                loadingDialog?.dismiss()
                when (result) {
                    SettingsDetailModuleResult.AlreadyEnabled -> Unit
                    is SettingsDetailModuleResult.Success -> {
                        showMessageDialog(
                            dialogTitle = context.getString(R.string.tip_title),
                            message = context.getString(
                                if (result.enabled) {
                                    R.string.install_success_message
                                } else {
                                    R.string.remove_success_message
                                }
                            )
                        )
                    }
                    is SettingsDetailModuleResult.Failure -> {
                        showMessageDialog(
                            dialogTitle = context.getString(R.string.error_title),
                            message = context.getString(
                                if (result.requestedEnabled) {
                                    R.string.install_failed_message
                                } else {
                                    R.string.remove_failed_message
                                },
                                result.message
                            )
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
            it.show(context.getString(R.string.importing_font))
        }
        viewModel.installFont(fontName, fontDescription) { result ->
            activity.runOnUiThread {
                loadingDialog?.dismiss()
                when (result) {
                    SettingsDetailFontInstallResult.Success -> {
                        showMessageDialog(
                            dialogTitle = context.getString(R.string.import_success_title),
                            message = context.getString(R.string.import_success_message),
                            buttonText = context.getString(R.string.restart_yes)
                        )
                    }
                    is SettingsDetailFontInstallResult.Failure -> {
                        showMessageDialog(
                            dialogTitle = context.getString(R.string.import_failed_title),
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
                originalDescription = context.getString(R.string.default_font_description, originalFileName),
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
                it.show(context.getString(R.string.preparing_font_file))
            }
            viewModel.prepareFontImport(uri) { result ->
                activity.runOnUiThread {
                    loadingDialog?.dismiss()
                    when (result) {
                        is SettingsDetailFontPreparationResult.Success -> {
                            showFontInputDialog(
                                result.originalFileName ?: context.getString(R.string.unknown_font_filename)
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
                Intent.createChooser(intent, context.getString(R.string.select_ttf_file))
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
        onOpenStrategySearch = {
            val intent = Intent(context, searchPage::class.java)
            if (activity != null) {
                activity.startActivityWithZToolTransition(intent)
            } else {
                context.startActivity(intent)
            }
        },
        onZuiForceSplit = {
            openOvConfigDialog(
                OvCommonConfigManager.MODE_SPLIT_SCREEN,
                context.getString(R.string.zui_force_split_title)
            )
        },
        onZuiForceFreeform = {
            openOvConfigDialog(
                OvCommonConfigManager.MODE_FREEFORM_FREE,
                context.getString(R.string.zui_force_freeform_title)
            )
        },
        onZuiForceFixed = {
            openOvConfigDialog(
                OvCommonConfigManager.MODE_FREEFORM_FIXED,
                context.getString(R.string.zui_force_fixed_title)
            )
        },
        onFloatMandatoryChanged = viewModel::setFloatMandatory,
        onSplitScreenMandatoryChanged = viewModel::setSplitScreenMandatory,
        onImportFont = ::startFontImportProcess,
        onAllowNativePermissionControllerChanged = viewModel::setAllowNativePermissionController,
        onAllowDisableDolbyChanged = viewModel::setAllowDisableDolby,
        onAlwaysDisplaySuggestionsChanged = viewModel::setAlwaysDisplaySuggestions,
        onRestartScope = viewModel::showRestartDialog
    )

    if (uiState.showRestartDialog) {
        RestartScopeDialog(
            packageName = packageName,
            onConfirm = {
                viewModel.restartScope(packageName)
            },
            onDismiss = viewModel::dismissRestartDialog
        )
    }
}

class SettingsDetailActivity : ZToolComponentActivity() {

    private var appPackageName: String? = null
    private var floatingWindow: FloatingWindow? = null
    private var loadingDialog: LoadingDialog? = null
    private lateinit var overlayPermissionLauncher: ActivityResultLauncher<Intent>
    private lateinit var fontPickerLauncher: ActivityResultLauncher<Intent>

    private lateinit var viewModel: SettingsDetailViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        viewModel = ViewModelProvider(
            this,
            SettingsDetailViewModelFactory(SettingsDetailRepository(applicationContext))
        )[SettingsDetailViewModel::class.java]
        initActivityResultLaunchers()

        val appName = intent.getStringExtra("app_name").orEmpty()
        appPackageName = intent.getStringExtra("app_package")
        viewModel.loadSettings()

        setContent {
            val uiState by viewModel.uiState.collectAsState()

            ZToolTheme {
                SettingsDetailScreen(
                    title = appName,
                    state = uiState,
                    onBack = ::finish,
                    onRemoveBlacklistChanged = viewModel::setRemoveBlacklist,
                    onModuleEnabledChanged = ::handleModuleSwitch,
                    onStartFloatingWindow = ::startFloatingWindow,
                    onOpenConfigSelection = {
                        showConfigSelectionDialog(viewModel.loadEmbeddingConfigFiles())
                    },
                    onOpenStrategySearch = {
                        startActivityWithZToolTransition(Intent(this, searchPage::class.java))
                    },
                    onZuiForceSplit = {
                        openOvConfigDialog(
                            OvCommonConfigManager.MODE_SPLIT_SCREEN,
                            getString(R.string.zui_force_split_title)
                        )
                    },
                    onZuiForceFreeform = {
                        openOvConfigDialog(
                            OvCommonConfigManager.MODE_FREEFORM_FREE,
                            getString(R.string.zui_force_freeform_title)
                        )
                    },
                    onZuiForceFixed = {
                        openOvConfigDialog(
                            OvCommonConfigManager.MODE_FREEFORM_FIXED,
                            getString(R.string.zui_force_fixed_title)
                        )
                    },
                    onFloatMandatoryChanged = viewModel::setFloatMandatory,
                    onSplitScreenMandatoryChanged = viewModel::setSplitScreenMandatory,
                    onImportFont = ::startFontImportProcess,
                    onAllowNativePermissionControllerChanged = viewModel::setAllowNativePermissionController,
                    onAllowDisableDolbyChanged = viewModel::setAllowDisableDolby,
                    onAlwaysDisplaySuggestionsChanged = viewModel::setAlwaysDisplaySuggestions,
                    onRestartScope = viewModel::showRestartDialog
                )

                if (uiState.showRestartDialog) {
                    RestartScopeDialog(
                        packageName = appPackageName.orEmpty(),
                        onConfirm = {
                            viewModel.restartScope(appPackageName.orEmpty())
                        },
                        onDismiss = viewModel::dismissRestartDialog
                    )
                }
            }
        }
    }

    private fun initActivityResultLaunchers() {
        overlayPermissionLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) {
            if (Settings.canDrawOverlays(this)) {
                startFloatingWindow()
            } else {
                Toast.makeText(this, R.string.overlay_permission_denied, Toast.LENGTH_SHORT).show()
            }
        }

        fontPickerLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            val data = result.data
            if (result.resultCode == RESULT_OK && data != null) {
                handleFontSelection(data.data)
            }
        }
    }

    private fun openOvConfigDialog(mode: Int, title: String) {
        loadingDialog = LoadingDialog(this).also {
            it.show(getString(R.string.loading_config))
        }

        viewModel.loadOvConfigSelection(mode) { result ->
            runOnUiThread {
                loadingDialog?.dismiss()
                when (result) {
                    is SettingsDetailOvConfigSelectionResult.Success -> {
                        AppChooserDialog.show(
                            this,
                            result.selection.allPackages,
                            result.selection.selectedPackages,
                            title,
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
                            title = getString(R.string.error_title),
                            message = getString(R.string.error_prefix) + result.message
                        )
                    }
                }
            }
        }
    }

    private fun saveOvConfig(
        configMap: MutableMap<String, OvCommonConfigManager.AppConfig>,
        selectedApps: List<AppChooserDialog.AppInfo>,
        mode: Int
    ) {
        loadingDialog = LoadingDialog(this).also {
            it.show(getString(R.string.saving_config))
        }

        viewModel.saveOvConfig(
            configMap = configMap,
            selectedPackages = selectedApps.map { it.packageName },
            mode = mode
        ) { result ->
            runOnUiThread {
                loadingDialog?.dismiss()
                if (result == "success") {
                    showMessageDialog(
                        title = getString(R.string.success_title),
                        message = getString(R.string.save_success_message)
                    )
                } else {
                    showMessageDialog(
                        title = getString(R.string.error_title),
                        message = getString(R.string.error_prefix) + result
                    )
                }
            }
        }
    }

    private fun handleModuleSwitch(isChecked: Boolean) {
        loadingDialog = LoadingDialog(this).also {
            it.show(getString(if (isChecked) R.string.installing_module else R.string.removing_module))
        }

        viewModel.setModuleEnabled(isChecked) { result ->
            runOnUiThread {
                loadingDialog?.dismiss()
                when (result) {
                    SettingsDetailModuleResult.AlreadyEnabled -> Unit
                    is SettingsDetailModuleResult.Success -> {
                        showMessageDialog(
                            title = getString(R.string.tip_title),
                            message = getString(
                                if (result.enabled) {
                                    R.string.install_success_message
                                } else {
                                    R.string.remove_success_message
                                }
                            )
                        )
                    }
                    is SettingsDetailModuleResult.Failure -> {
                        showMessageDialog(
                            title = getString(R.string.error_title),
                            message = getString(
                                if (result.requestedEnabled) {
                                    R.string.install_failed_message
                                } else {
                                    R.string.remove_failed_message
                                },
                                result.message
                            )
                        )
                    }
                }
            }
        }
    }

    private fun startFloatingWindow() {
        if (!Settings.canDrawOverlays(this)) {
            requestOverlayPermission()
            return
        }
        if (!hasUsageStatsPermission()) {
            Toast.makeText(this, R.string.request_usage_stats_permission, Toast.LENGTH_LONG).show()
            startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
            return
        }
        showFloatingWindow()
    }

    private fun requestOverlayPermission() {
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            "package:$packageName".toUri()
        )
        overlayPermissionLauncher.launch(intent)
    }

    private fun hasUsageStatsPermission(): Boolean {
        val appOps = getSystemService(APP_OPS_SERVICE) as AppOpsManager
        val mode = appOps.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            android.os.Process.myUid(),
            packageName
        )
        return mode == AppOpsManager.MODE_ALLOWED
    }

    private fun showFloatingWindow() {
        if (floatingWindow != null) {
            hideFloatingWindow()
            return
        }
        floatingWindow = FloatingWindow(this)
        Toast.makeText(this, R.string.floating_window_started, Toast.LENGTH_SHORT).show()
    }

    private fun hideFloatingWindow() {
        floatingWindow?.hide()
        if (floatingWindow != null) {
            floatingWindow = null
            Toast.makeText(this, R.string.floating_window_closed, Toast.LENGTH_SHORT).show()
        }
    }

    private fun showConfigSelectionDialog(configs: List<EmbeddingConfigManager.ConfigFileInfo>) {
        if (configs.isEmpty()) {
            Toast.makeText(this, R.string.no_config_files_prompt, Toast.LENGTH_SHORT).show()
            return
        }

        val flashedConfigs = viewModel.loadFlashedConfigs()
        lateinit var dialog: Dialog
        dialog = showComposeDialog {
            ConfigSelectionDialogContent(
                configs = configs,
                flashedConfigs = flashedConfigs,
                configLabel = { config ->
                    config.timestamp + " " + config.appName + getString(R.string.config_suffix)
                },
                onAlreadyFlashedClick = {
                    Toast.makeText(this, R.string.config_already_flashed, Toast.LENGTH_SHORT).show()
                },
                onDelete = { selectedConfigs ->
                    performConfigDelete(selectedConfigs, flashedConfigs, dialog)
                },
                onFlash = { selectedConfigs ->
                    dialog.dismiss()
                    flashSelectedConfigs(selectedConfigs)
                },
                onRestore = {
                    dialog.dismiss()
                    restoreOriginalModule()
                },
                onCancel = { dialog.dismiss() }
            )
        }
    }

    private fun performConfigDelete(
        toDelete: List<EmbeddingConfigManager.ConfigFileInfo>,
        flashed: Set<String>,
        dialog: Dialog
    ) {
        val count = viewModel.deleteEmbeddingConfigs(toDelete, flashed)
        Toast.makeText(this, getString(R.string.delete_success, count), Toast.LENGTH_SHORT).show()
        dialog.dismiss()
        showConfigSelectionDialog(viewModel.loadEmbeddingConfigFiles())
    }

    private fun flashSelectedConfigs(selectedConfigs: List<EmbeddingConfigManager.ConfigFileInfo>) {
        if (!viewModel.uiState.value.moduleEnabled) {
            showMessageDialog(
                title = getString(R.string.tip_title),
                message = getString(R.string.install_module_first)
            )
            return
        }

        loadingDialog = LoadingDialog(this).also {
            it.show(getString(R.string.flashing_config))
        }

        viewModel.flashEmbeddingConfigs(selectedConfigs) { result ->
            runOnUiThread {
                loadingDialog?.dismiss()
                when (result) {
                    SettingsDetailConfigFlashResult.Success -> {
                        showMessageDialog(
                            title = getString(R.string.success_title),
                            message = getString(R.string.flash_success_message)
                        )
                    }
                    is SettingsDetailConfigFlashResult.Failure -> {
                        showMessageDialog(
                            title = getString(R.string.error_title),
                            message = getString(R.string.flash_failed_message, result.message)
                        )
                    }
                }
            }
        }
    }

    private fun restoreOriginalModule() {
        showConfirmDialog(
            title = getString(R.string.confirm_restore_title),
            message = getString(R.string.confirm_restore_message),
            confirmText = getString(R.string.confirm_button),
            dismissText = getString(R.string.restart_no)
        ) {
                loadingDialog = LoadingDialog(this).also {
                    it.show(getString(R.string.restoring_module))
                }
                viewModel.restoreOriginalModule { result ->
                    runOnUiThread {
                        loadingDialog?.dismiss()
                        when (result) {
                            SettingsDetailRestoreResult.Success -> {
                                showMessageDialog(
                                    title = getString(R.string.success_title),
                                    message = getString(R.string.restore_success_message)
                                )
                            }
                            is SettingsDetailRestoreResult.Failure -> {
                                showMessageDialog(
                                    title = getString(R.string.error_title),
                                    message = result.message
                                )
                            }
                        }
                    }
                }
            }
    }

    private fun startFontImportProcess() {
        val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
            type = "*/*"
            addCategory(Intent.CATEGORY_OPENABLE)
            putExtra(
                Intent.EXTRA_MIME_TYPES,
                arrayOf("font/ttf", "application/x-font-ttf", "application/octet-stream")
            )
        }
        try {
            fontPickerLauncher.launch(Intent.createChooser(intent, getString(R.string.select_ttf_file)))
        } catch (_: Exception) {
            Toast.makeText(this, R.string.no_file_manager_found, Toast.LENGTH_SHORT).show()
        }
    }

    private fun handleFontSelection(uri: Uri?) {
        if (uri == null) return
        loadingDialog = LoadingDialog(this).also {
            it.show(getString(R.string.preparing_font_file))
        }

        viewModel.prepareFontImport(uri) { result ->
            runOnUiThread {
                loadingDialog?.dismiss()
                when (result) {
                    is SettingsDetailFontPreparationResult.Success -> {
                        showFontInputDialog(
                            result.originalFileName ?: getString(R.string.unknown_font_filename)
                        )
                    }
                    is SettingsDetailFontPreparationResult.Failure -> {
                        Toast.makeText(this, "File Error: " + result.message, Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    private fun showFontInputDialog(originalFileName: String) {
        lateinit var dialog: Dialog
        dialog = showComposeDialog {
            FontInputDialogContent(
                originalDescription = getString(R.string.default_font_description, originalFileName),
                onConfirm = { name, description ->
                    if (name.isNotEmpty() && description.isNotEmpty()) {
                        dialog.dismiss()
                        startFontImport(name, description)
                    }
                },
                onCancel = { dialog.dismiss() }
            )
        }
    }

    private fun showComposeDialog(content: @Composable () -> Unit): Dialog {
        return showPlatformComposeDialog(this) {
            content()
        }
    }

    private fun startFontImport(fontName: String, fontDescription: String) {
        loadingDialog = LoadingDialog(this).also {
            it.show(getString(R.string.importing_font))
        }

        viewModel.installFont(fontName, fontDescription) { result ->
            runOnUiThread {
                loadingDialog?.dismiss()
                when (result) {
                    SettingsDetailFontInstallResult.Success -> {
                        showMessageDialog(
                            title = getString(R.string.import_success_title),
                            message = getString(R.string.import_success_message),
                            buttonText = getString(R.string.restart_yes)
                        )
                    }
                    is SettingsDetailFontInstallResult.Failure -> {
                        showMessageDialog(
                            title = getString(R.string.import_failed_title),
                            message = result.message
                        )
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        hideFloatingWindow()
    }

    companion object;

    private fun showMessageDialog(
        title: String,
        message: String,
        buttonText: String = getString(R.string.got_it_button)
    ) {
        showPlatformComposeDialog(this) { dialog ->
            SimpleSettingsDetailDialogContent(
                title = title,
                message = message,
                confirmText = buttonText,
                onConfirm = { dialog.dismiss() }
            )
        }
    }

    private fun showConfirmDialog(
        title: String,
        message: String,
        confirmText: String,
        dismissText: String,
        onConfirm: () -> Unit
    ) {
        showPlatformComposeDialog(this) { dialog ->
            SimpleSettingsDetailDialogContent(
                title = title,
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
    Surface(color = MaterialTheme.colorScheme.surface) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 20.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 12.dp)
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Spacer(modifier = Modifier.weight(1f))
                if (dismissText != null && onDismiss != null) {
                    TextButton(onClick = onDismiss) {
                        Text(dismissText)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                }
                TextButton(onClick = onConfirm) {
                    Text(confirmText)
                }
            }
        }
    }
}

private class SettingsDetailViewModelFactory(
    private val repository: SettingsDetailRepository
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(SettingsDetailViewModel::class.java)) {
            return SettingsDetailViewModel(repository) as T
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
    onAlwaysDisplaySuggestionsChanged: (Boolean) -> Unit,
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
            ExtendedFloatingActionButton(
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
                        onAlwaysDisplaySuggestionsChanged = onAlwaysDisplaySuggestionsChanged
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
    onAlwaysDisplaySuggestionsChanged: (Boolean) -> Unit
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
                        title = stringResource(R.string.AlwaysDisplaySuggestionsTitle),
                        summary = stringResource(R.string.AlwaysDisplaySuggestionsSummary),
                        checked = state.alwaysDisplaySuggestions,
                        onCheckedChange = onAlwaysDisplaySuggestionsChanged
                    )
                )
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

    Surface(color = MaterialTheme.colorScheme.surface) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp)
        ) {
            Text(
                text = stringResource(R.string.select_config_files),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
            if (flashedConfigs.isNotEmpty()) {
                Text(
                    text = stringResource(R.string.flashed_configs_count, flashedConfigs.size),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .padding(top = 8.dp)
                )
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp)
                    .padding(top = 12.dp)
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

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    onClick = {
                        val selectedConfigs = selectedIndexes.map { configs[it] }
                        if (selectedConfigs.isNotEmpty()) onDelete(selectedConfigs)
                    }
                ) {
                    Text(stringResource(R.string.delete))
                }
                TextButton(
                    onClick = {
                        val selectedConfigs = selectedIndexes.map { configs[it] }
                        if (selectedConfigs.isNotEmpty()) onFlash(selectedConfigs)
                    }
                ) {
                    Text(stringResource(R.string.flashAddedConfig))
                }
                if (flashedConfigs.isNotEmpty()) {
                    TextButton(onClick = onRestore) {
                        Text(stringResource(R.string.restoreModule))
                    }
                }
                Spacer(modifier = Modifier.weight(1f))
                TextButton(onClick = onCancel) {
                    Text(stringResource(R.string.cancel))
                }
            }
        }
    }
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
        Checkbox(
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

    Surface(color = MaterialTheme.colorScheme.surface) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp)
        ) {
            Text(
                text = stringResource(R.string.input_font_info_title),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = stringResource(R.string.doNotUseDuplicatedFontName),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp)
            )
            OutlinedTextField(
                value = fontName,
                onValueChange = { fontName = it },
                label = { Text(stringResource(R.string.fontName)) },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp)
            )
            OutlinedTextField(
                value = fontDescription,
                onValueChange = { fontDescription = it },
                label = { Text(stringResource(R.string.fontDescription)) },
                minLines = 3,
                maxLines = 5,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp)
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Spacer(modifier = Modifier.weight(1f))
                TextButton(onClick = onCancel) {
                    Text(stringResource(R.string.cancel))
                }
                TextButton(
                    onClick = {
                        onConfirm(fontName.trim(), fontDescription.trim())
                    }
                ) {
                    Text(stringResource(R.string.confirm_button))
                }
            }
        }
    }
}

@Composable
private fun RestartScopeDialog(
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
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.restart_yes))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.restart_no))
            }
        }
    )
}
