package com.qimian233.ztool.settingactivity.setting

import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ApplicationInfo
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.OpenableColumns
import android.provider.Settings
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.AlertDialog as ComposeAlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.qimian233.ztool.EnhancedShellExecutor
import com.qimian233.ztool.LoadingDialog
import com.qimian233.ztool.R
import com.qimian233.ztool.hook.modules.SharedPreferencesTool.ModulePreferencesUtils
import com.qimian233.ztool.settingactivity.setting.floatingwindow.FloatingWindow
import com.qimian233.ztool.settingactivity.setting.magicwindowsearch.searchPage
import com.qimian233.ztool.ui.components.ZToolSettingsDivider
import com.qimian233.ztool.ui.components.ZToolSwitchRow
import com.qimian233.ztool.ui.theme.ZToolTheme
import com.qimian233.ztool.utils.AppChooserDialog
import com.qimian233.ztool.utils.EmbeddingConfigManager
import com.qimian233.ztool.utils.FontInstallerManager
import com.qimian233.ztool.utils.MagiskModuleManager
import com.qimian233.ztool.utils.OvCommonConfigManager
import java.io.File

class SettingsDetailActivity : ComponentActivity() {

    private lateinit var prefsUtils: ModulePreferencesUtils
    private lateinit var magiskManager: MagiskModuleManager
    private lateinit var configManager: EmbeddingConfigManager
    private lateinit var fontManager: FontInstallerManager
    private lateinit var ovConfigManager: OvCommonConfigManager

    private var appPackageName: String? = null
    private var floatingWindow: FloatingWindow? = null
    private var currentSelectedFontFile: File? = null
    private var allInstalledPackages: MutableList<String>? = null
    private var loadingDialog: LoadingDialog? = null
    private lateinit var overlayPermissionLauncher: ActivityResultLauncher<Intent>
    private lateinit var fontPickerLauncher: ActivityResultLauncher<Intent>

    private var uiState by mutableStateOf(SettingsDetailUiState())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        prefsUtils = ModulePreferencesUtils(this)
        magiskManager = MagiskModuleManager()
        configManager = EmbeddingConfigManager()
        fontManager = FontInstallerManager()
        ovConfigManager = OvCommonConfigManager()
        initActivityResultLaunchers()

        val appName = intent.getStringExtra("app_name").orEmpty()
        appPackageName = intent.getStringExtra("app_package")
        loadSettings()

        setContent {
            ZToolTheme {
                SettingsDetailScreen(
                    title = appName + stringResource(R.string.settings_detail_title_suffix),
                    state = uiState,
                    onBack = ::finish,
                    onRemoveBlacklistChanged = {
                        uiState = uiState.copy(removeBlacklist = it)
                        prefsUtils.saveBooleanSetting("remove_blacklist", it)
                    },
                    onModuleEnabledChanged = ::handleModuleSwitch,
                    onStartFloatingWindow = ::startFloatingWindow,
                    onOpenConfigSelection = {
                        showConfigSelectionDialog(configManager.loadAndValidateConfigFiles(this))
                    },
                    onOpenStrategySearch = {
                        startActivity(Intent(this, searchPage::class.java))
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
                    onFloatMandatoryChanged = {
                        uiState = uiState.copy(floatMandatory = it)
                        EnhancedShellExecutor.getInstance().executeCommand(
                            "su -c settings put global force_resizable_activities " + if (it) "1" else "0"
                        )
                    },
                    onSplitScreenMandatoryChanged = {
                        uiState = uiState.copy(splitScreenMandatory = it)
                        prefsUtils.saveBooleanSetting("Split_Screen_mandatory", it)
                    },
                    onImportFont = ::startFontImportProcess,
                    onAllowNativePermissionControllerChanged = {
                        uiState = uiState.copy(allowNativePermissionController = it)
                        prefsUtils.saveBooleanSetting("PermissionControllerHook", it)
                    },
                    onAllowDisableDolbyChanged = {
                        uiState = uiState.copy(allowDisableDolby = it)
                        prefsUtils.saveBooleanSetting("allow_display_dolby", it)
                    },
                    onAlwaysDisplaySuggestionsChanged = {
                        uiState = uiState.copy(alwaysDisplaySuggestions = it)
                        prefsUtils.saveBooleanSetting("AlwaysDisplaySuggestion", it)
                    },
                    onRestartScope = { uiState = uiState.copy(showRestartDialog = true) }
                )

                if (uiState.showRestartDialog) {
                    RestartScopeDialog(
                        packageName = appPackageName.orEmpty(),
                        onConfirm = {
                            uiState = uiState.copy(showRestartDialog = false)
                            forceStopApp()
                        },
                        onDismiss = { uiState = uiState.copy(showRestartDialog = false) }
                    )
                }
            }
        }
    }

    private fun loadSettings() {
        uiState = uiState.copy(
            removeBlacklist = prefsUtils.loadBooleanSetting("remove_blacklist", false),
            splitScreenMandatory = prefsUtils.loadBooleanSetting("Split_Screen_mandatory", false),
            allowDisableDolby = prefsUtils.loadBooleanSetting("allow_display_dolby", false),
            allowNativePermissionController = prefsUtils.loadBooleanSetting("PermissionControllerHook", false),
            alwaysDisplaySuggestions = prefsUtils.loadBooleanSetting("AlwaysDisplaySuggestion", false)
        )

        Thread {
            val isModuleEnabled = magiskManager.isModuleEnabled
            val isForceResize = isForceResizableActivitiesEnabled()
            runOnUiThread {
                uiState = uiState.copy(
                    moduleEnabled = isModuleEnabled,
                    floatMandatory = isForceResize
                )
            }
        }.start()
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

        Thread {
            if (allInstalledPackages == null) {
                val packages = mutableListOf<String>()
                val pm = packageManager
                val apps = pm.getInstalledApplications(0)
                for (app in apps) {
                    if (pm.getLaunchIntentForPackage(app.packageName) != null) {
                        packages.add(app.packageName)
                    }
                }
                allInstalledPackages = packages
            }

            val currentConfig = ovConfigManager.loadConfig(this)
            val selectedPackages = ovConfigManager.getPackagesForMode(currentConfig, mode)

            runOnUiThread {
                loadingDialog?.dismiss()
                AppChooserDialog.show(
                    this,
                    allInstalledPackages.orEmpty(),
                    selectedPackages,
                    title,
                    object : AppChooserDialog.AppSelectionCallback {
                        override fun onSelected(selectedApps: List<AppChooserDialog.AppInfo>) {
                            saveOvConfig(currentConfig, selectedApps, mode)
                        }

                        override fun onCancel() = Unit
                    }
                )
            }
        }.start()
    }

    private fun saveOvConfig(
        configMap: MutableMap<String, OvCommonConfigManager.AppConfig>,
        selectedApps: List<AppChooserDialog.AppInfo>,
        mode: Int
    ) {
        loadingDialog = LoadingDialog(this).also {
            it.show(getString(R.string.saving_config))
        }

        Thread {
            val newSelectedPackages = selectedApps.map { it.packageName }
            ovConfigManager.updateConfigForMode(configMap, newSelectedPackages, mode)
            val result = ovConfigManager.saveConfig(this, configMap)

            runOnUiThread {
                loadingDialog?.dismiss()
                if (result == "success") {
                    MaterialAlertDialogBuilder(this)
                        .setTitle(R.string.success_title)
                        .setMessage(R.string.save_success_message)
                        .setPositiveButton(R.string.got_it_button, null)
                        .show()
                } else {
                    MaterialAlertDialogBuilder(this)
                        .setTitle(R.string.error_title)
                        .setMessage(getString(R.string.error_prefix) + result)
                        .setPositiveButton(R.string.got_it_button, null)
                        .show()
                }
            }
        }.start()
    }

    private fun handleModuleSwitch(isChecked: Boolean) {
        if (isChecked && magiskManager.isModuleEnabled) {
            uiState = uiState.copy(moduleEnabled = true)
            return
        }

        uiState = uiState.copy(moduleEnabled = isChecked)
        loadingDialog = LoadingDialog(this).also {
            it.show(getString(if (isChecked) R.string.installing_module else R.string.removing_module))
        }

        Thread {
            val result = if (isChecked) {
                magiskManager.installModule(this)
            } else {
                magiskManager.removeModule(this)
            }

            runOnUiThread {
                loadingDialog?.dismiss()
                if (result == "success") {
                    uiState = uiState.copy(moduleEnabled = isChecked)
                    MaterialAlertDialogBuilder(this)
                        .setTitle(R.string.tip_title)
                        .setMessage(if (isChecked) R.string.install_success_message else R.string.remove_success_message)
                        .setNegativeButton(R.string.got_it_button, null)
                        .show()
                } else {
                    uiState = uiState.copy(moduleEnabled = !isChecked)
                    MaterialAlertDialogBuilder(this)
                        .setTitle(R.string.error_title)
                        .setMessage(
                            getString(
                                if (isChecked) R.string.install_failed_message else R.string.remove_failed_message,
                                result
                            )
                        )
                        .setNegativeButton(R.string.got_it_button, null)
                        .show()
                }
            }
        }.start()
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
            Uri.parse("package:$packageName")
        )
        overlayPermissionLauncher.launch(intent)
    }

    private fun hasUsageStatsPermission(): Boolean {
        val appOps = getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
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

        val flashedConfigs = loadStringSetSetting("flashed_configs", hashSetOf())
        lateinit var dialog: AlertDialog
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
        dialog: AlertDialog
    ) {
        var count = 0
        for (config in toDelete) {
            if (flashed.contains(config.timestamp + "_" + config.packageName)) continue
            if (config.file.delete()) count++
        }
        Toast.makeText(this, getString(R.string.delete_success, count), Toast.LENGTH_SHORT).show()
        dialog.dismiss()
        showConfigSelectionDialog(configManager.loadAndValidateConfigFiles(this))
    }

    private fun flashSelectedConfigs(selectedConfigs: List<EmbeddingConfigManager.ConfigFileInfo>) {
        if (!magiskManager.isModuleEnabled) {
            MaterialAlertDialogBuilder(this)
                .setTitle(R.string.tip_title)
                .setMessage(R.string.install_module_first)
                .show()
            return
        }

        loadingDialog = LoadingDialog(this).also {
            it.show(getString(R.string.flashing_config))
        }

        Thread {
            try {
                configManager.flashConfigs(this, selectedConfigs)
                val flashed = loadStringSetSetting("flashed_configs", hashSetOf())
                for (config in selectedConfigs) {
                    flashed.add(config.timestamp + "_" + config.packageName)
                }
                saveStringSetSetting("flashed_configs", flashed)

                runOnUiThread {
                    loadingDialog?.dismiss()
                    MaterialAlertDialogBuilder(this)
                        .setTitle(R.string.success_title)
                        .setMessage(R.string.flash_success_message)
                        .show()
                }
            } catch (e: Exception) {
                runOnUiThread {
                    loadingDialog?.dismiss()
                    MaterialAlertDialogBuilder(this)
                        .setTitle(R.string.error_title)
                        .setMessage(getString(R.string.flash_failed_message, e.message))
                        .show()
                }
            }
        }.start()
    }

    private fun restoreOriginalModule() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.confirm_restore_title)
            .setMessage(R.string.confirm_restore_message)
            .setPositiveButton(R.string.confirm_button) { _, _ ->
                loadingDialog = LoadingDialog(this).also {
                    it.show(getString(R.string.restoring_module))
                }
                Thread {
                    magiskManager.removeModule(this)
                    val result = magiskManager.installModule(this)
                    saveStringSetSetting("flashed_configs", hashSetOf())
                    runOnUiThread {
                        loadingDialog?.dismiss()
                        if (result == "success") {
                            MaterialAlertDialogBuilder(this)
                                .setTitle(R.string.success_title)
                                .setMessage(R.string.restore_success_message)
                                .show()
                        } else {
                            MaterialAlertDialogBuilder(this)
                                .setTitle(R.string.error_title)
                                .setMessage(result)
                                .show()
                        }
                    }
                }.start()
            }
            .setNegativeButton(R.string.restart_no, null)
            .show()
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
        } catch (e: Exception) {
            Toast.makeText(this, R.string.no_file_manager_found, Toast.LENGTH_SHORT).show()
        }
    }

    private fun handleFontSelection(uri: Uri?) {
        if (uri == null) return
        loadingDialog = LoadingDialog(this).also {
            it.show(getString(R.string.preparing_font_file))
        }

        Thread {
            try {
                currentSelectedFontFile = fontManager.copyFontToTemp(this, uri)
                val fileName = getFileName(uri)
                runOnUiThread {
                    loadingDialog?.dismiss()
                    showFontInputDialog(fileName ?: getString(R.string.unknown_font_filename))
                }
            } catch (e: Exception) {
                runOnUiThread {
                    loadingDialog?.dismiss()
                    Toast.makeText(this, "File Error: " + e.message, Toast.LENGTH_SHORT).show()
                }
            }
        }.start()
    }

    private fun showFontInputDialog(originalFileName: String) {
        lateinit var dialog: AlertDialog
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

    private fun showComposeDialog(content: @Composable () -> Unit): AlertDialog {
        val composeView = ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@SettingsDetailActivity)
            setViewTreeViewModelStoreOwner(this@SettingsDetailActivity)
            setViewTreeSavedStateRegistryOwner(this@SettingsDetailActivity)
            setContent {
                ZToolTheme {
                    content()
                }
            }
        }

        return MaterialAlertDialogBuilder(this)
            .setView(composeView)
            .create()
            .also { dialog ->
                dialog.show()
                dialog.window?.setLayout(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            }
    }

    private fun startFontImport(fontName: String, fontDescription: String) {
        loadingDialog = LoadingDialog(this).also {
            it.show(getString(R.string.importing_font))
        }

        Thread {
            try {
                fontManager.installFont(this, currentSelectedFontFile, fontName, fontDescription)
                runOnUiThread {
                    loadingDialog?.dismiss()
                    MaterialAlertDialogBuilder(this)
                        .setTitle(R.string.import_success_title)
                        .setMessage(R.string.import_success_message)
                        .setPositiveButton(R.string.restart_yes, null)
                        .show()
                }
            } catch (e: Exception) {
                runOnUiThread {
                    loadingDialog?.dismiss()
                    MaterialAlertDialogBuilder(this)
                        .setTitle(R.string.import_failed_title)
                        .setMessage(e.message)
                        .show()
                }
            }
        }.start()
    }

    private fun forceStopApp() {
        val packageName = appPackageName ?: return
        Thread {
            val executor = EnhancedShellExecutor.getInstance()
            executor.executeRootCommand("am force-stop $packageName")
            executor.executeRootCommand("am force-stop com.android.permissioncontroller")
            executor.executeRootCommand("am force-stop com.zui.safecenter")
        }.start()
    }

    private fun isForceResizableActivitiesEnabled(): Boolean {
        val result = EnhancedShellExecutor.getInstance()
            .executeRootCommand("settings get global force_resizable_activities", 2)
        return result.isSuccess && result.output == "1"
    }

    private fun loadStringSetSetting(key: String, defaultSet: HashSet<String>): HashSet<String> {
        val sp: SharedPreferences = getSharedPreferences("module_settings", Context.MODE_PRIVATE)
        val result = sp.getStringSet(key, null)
        return if (result == null) defaultSet else HashSet(result)
    }

    private fun saveStringSetSetting(key: String, set: Set<String>) {
        val sp: SharedPreferences = getSharedPreferences("module_settings", Context.MODE_PRIVATE)
        sp.edit().putStringSet(key, HashSet(set)).apply()
    }

    private fun getFileName(uri: Uri): String? {
        var result: String? = null
        if (uri.scheme == "content") {
            try {
                val cursor: Cursor? = contentResolver.query(uri, null, null, null, null)
                cursor.use {
                    if (it != null && it.moveToFirst()) {
                        val index = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (index >= 0) result = it.getString(index)
                    }
                }
            } catch (_: Exception) {
            }
        }
        if (result == null) {
            result = uri.path
            val value = result
            if (value != null) {
                val cut = value.lastIndexOf('/')
                if (cut != -1) result = value.substring(cut + 1)
            }
        }
        return result
    }

    override fun onDestroy() {
        super.onDestroy()
        hideFloatingWindow()
    }

    companion object {
    }
}

private data class SettingsDetailUiState(
    val removeBlacklist: Boolean = false,
    val moduleEnabled: Boolean = false,
    val floatMandatory: Boolean = false,
    val splitScreenMandatory: Boolean = false,
    val allowDisableDolby: Boolean = false,
    val allowNativePermissionController: Boolean = false,
    val alwaysDisplaySuggestions: Boolean = false,
    val showZuiForceConfig: Boolean = Build.VERSION.SDK_INT >= 36,
    val showRestartDialog: Boolean = false
)

@OptIn(ExperimentalMaterial3Api::class)
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
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = title,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = null
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer
                )
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
                SettingsCard(title = stringResource(R.string.embedding_setting_title)) {
                    ZToolSwitchRow(
                        title = stringResource(R.string.embedding_setting_removeBlacklist),
                        summary = stringResource(R.string.embedding_setting_removeBlacklist_summary),
                        checked = state.removeBlacklist,
                        onCheckedChange = onRemoveBlacklistChanged
                    )
                    ZToolSettingsDivider()
                    ZToolSwitchRow(
                        title = stringResource(R.string.RoleModule_Title),
                        summary = stringResource(R.string.RoleModule_Summary),
                        checked = state.moduleEnabled,
                        onCheckedChange = onModuleEnabledChanged
                    )
                    ZToolSettingsDivider()
                    ActionSettingRow(
                        title = stringResource(R.string.custom_landscape_view),
                        summary = stringResource(R.string.custom_landscape_view_summary),
                        icon = { Icon(Icons.AutoMirrored.Rounded.OpenInNew, contentDescription = null) },
                        onClick = onStartFloatingWindow
                    )
                    ZToolSettingsDivider()
                    ActionSettingRow(
                        title = stringResource(R.string.custom_landscapeResult_Title),
                        summary = stringResource(R.string.custom_landscapeResult_Summary),
                        onClick = onOpenConfigSelection
                    )
                    ZToolSettingsDivider()
                    ActionSettingRow(
                        title = stringResource(R.string.YiShiJieRules),
                        summary = stringResource(R.string.YiShiJieRules_Summary),
                        icon = { Icon(Icons.AutoMirrored.Rounded.ArrowForward, contentDescription = null) },
                        onClick = onOpenStrategySearch
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                if (state.showZuiForceConfig) {
                    SettingsCard(title = stringResource(R.string.zui_force_config_title)) {
                        Text(
                            text = stringResource(R.string.zui_force_config_summary),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
                        )
                        ActionSettingRow(
                            title = stringResource(R.string.zui_force_split_title),
                            summary = stringResource(R.string.zui_force_split_summary),
                            onClick = onZuiForceSplit
                        )
                        ZToolSettingsDivider()
                        ActionSettingRow(
                            title = stringResource(R.string.zui_force_freeform_title),
                            summary = stringResource(R.string.zui_force_freeform_summary),
                            onClick = onZuiForceFreeform
                        )
                        ZToolSettingsDivider()
                        ActionSettingRow(
                            title = stringResource(R.string.zui_force_fixed_title),
                            summary = stringResource(R.string.zui_force_fixed_summary),
                            onClick = onZuiForceFixed
                        )
                    }
                } else {
                    SettingsCard(title = stringResource(R.string.embedding_Title)) {
                        ZToolSwitchRow(
                            title = stringResource(R.string.Float_app_Mandatory),
                            summary = stringResource(R.string.Float_app_Mandatory_summary),
                            checked = state.floatMandatory,
                            onCheckedChange = onFloatMandatoryChanged
                        )
                        ZToolSettingsDivider()
                        ZToolSwitchRow(
                            title = stringResource(R.string.Split_screen_Mandatory_Title),
                            summary = stringResource(R.string.Split_screen_Mandatory_Summary),
                            checked = state.splitScreenMandatory,
                            onCheckedChange = onSplitScreenMandatoryChanged
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                SettingsCard(title = stringResource(R.string.font_settings_title)) {
                    ActionSettingRow(
                        title = stringResource(R.string.import_font_title),
                        summary = stringResource(R.string.import_font_summary),
                        icon = { Icon(Icons.Rounded.Add, contentDescription = null) },
                        onClick = onImportFont
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                SettingsCard(title = stringResource(R.string.misc)) {
                    ZToolSwitchRow(
                        title = stringResource(R.string.NativePermissionController_enable_title),
                        summary = stringResource(R.string.NativePermissionController_enable_summary),
                        checked = state.allowNativePermissionController,
                        onCheckedChange = onAllowNativePermissionControllerChanged
                    )
                    ZToolSettingsDivider()
                    ZToolSwitchRow(
                        title = stringResource(R.string.AllowDisableDolby),
                        summary = stringResource(R.string.AllowDisableDolby_summary),
                        checked = state.allowDisableDolby,
                        onCheckedChange = onAllowDisableDolbyChanged
                    )
                    ZToolSettingsDivider()
                    ZToolSwitchRow(
                        title = stringResource(R.string.AlwaysDisplaySuggestionsTitle),
                        summary = stringResource(R.string.AlwaysDisplaySuggestionsSummary),
                        checked = state.alwaysDisplaySuggestions,
                        onCheckedChange = onAlwaysDisplaySuggestionsChanged
                    )
                }
            }
        }
    }
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
private fun SettingsCard(
    title: String,
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(PaddingValues(horizontal = 24.dp, vertical = 8.dp))
            )
            content()
        }
    }
}

@Composable
private fun ActionSettingRow(
    title: String,
    summary: String,
    onClick: () -> Unit,
    icon: @Composable (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .widthIn(max = 720.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = summary,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
        Spacer(modifier = Modifier.width(16.dp))
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
}

@Composable
private fun RestartScopeDialog(
    packageName: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    ComposeAlertDialog(
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
