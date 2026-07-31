package com.qimian233.ztool.data.settings

import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import com.qimian233.ztool.EnhancedShellExecutor
import com.qimian233.ztool.FeatureDestination
import com.qimian233.ztool.utils.ModulePreferencesUtils
import com.qimian233.ztool.utils.EmbeddingConfigManager
import com.qimian233.ztool.utils.FontInstallerManager
import com.qimian233.ztool.utils.MagiskModuleManager
import com.qimian233.ztool.utils.OvCommonConfigManager
import com.qimian233.ztool.utils.ScopeUtils
import com.qimian233.ztool.viewmodel.SettingsDetailUiState
import java.io.File
import androidx.core.content.edit

class SettingsDetailRepository(
    val context: Context,
    private val shellExecutor: EnhancedShellExecutor = EnhancedShellExecutor.getInstance(),
    private val magiskManager: MagiskModuleManager = MagiskModuleManager(),
    private val embeddingConfigManager: EmbeddingConfigManager = EmbeddingConfigManager(),
    private val fontInstallerManager: FontInstallerManager = FontInstallerManager(),
    private val ovConfigManager: OvCommonConfigManager = OvCommonConfigManager()
) {
    private val prefsUtils = ModulePreferencesUtils(context)
    private var cachedLaunchablePackages: List<String>? = null

    fun loadState(): SettingsDetailUiState {
        return SettingsDetailUiState(
            removeBlacklist = prefsUtils.loadBooleanSetting(KEY_REMOVE_BLACKLIST, false),
            moduleEnabled = magiskManager.isModuleEnabled,
            floatMandatory = isForceResizableActivitiesEnabled(),
            splitScreenMandatory = prefsUtils.loadBooleanSetting(KEY_SPLIT_SCREEN_MANDATORY, false),
            allowDisableDolby = prefsUtils.loadBooleanSetting(KEY_ALLOW_DISPLAY_DOLBY, false),
            allowNativePermissionController = prefsUtils.loadBooleanSetting(KEY_PERMISSION_CONTROLLER_HOOK, false),
            appDetail = prefsUtils.loadBooleanSetting(KEY_APP_DETAILS, false),
            showZuiForceConfig = Build.VERSION.SDK_INT >= 36
        )
    }

    fun saveRemoveBlacklist(enabled: Boolean) {
        prefsUtils.saveBooleanSetting(KEY_REMOVE_BLACKLIST, enabled)
    }

    fun saveForceResizableActivities(enabled: Boolean) {
        shellExecutor.executeCommand(
            "su -c settings put global force_resizable_activities " + if (enabled) "1" else "0"
        )
    }

    fun saveSplitScreenMandatory(enabled: Boolean) {
        prefsUtils.saveBooleanSetting(KEY_SPLIT_SCREEN_MANDATORY, enabled)
    }

    fun saveAllowNativePermissionController(enabled: Boolean) {
        prefsUtils.saveBooleanSetting(KEY_PERMISSION_CONTROLLER_HOOK, enabled)
    }

    fun saveAllowDisableDolby(enabled: Boolean) {
        prefsUtils.saveBooleanSetting(KEY_ALLOW_DISPLAY_DOLBY, enabled)
    }

    fun saveAppDetails(enabled: Boolean) {
        prefsUtils.saveBooleanSetting(KEY_APP_DETAILS, enabled)
    }

    fun forceStopScope() {
        val packages = ScopeUtils.getScopePackages(FeatureDestination.SettingsDetail)
        ScopeUtils.restartScope(packages, shellExecutor)
    }

    fun isModuleEnabled(): Boolean = magiskManager.isModuleEnabled

    fun setModuleEnabled(enabled: Boolean): String {
        return if (enabled) {
            magiskManager.installModule(context)
        } else {
            magiskManager.removeModule(context)
        }
    }

    fun restoreOriginalModule(): String {
        magiskManager.removeModule(context)
        val result = magiskManager.installModule(context)
        if (result == RESULT_SUCCESS) {
            clearFlashedConfigs()
        }
        return result
    }

    fun loadFlashedConfigs(): HashSet<String> {
        val result = moduleSettings.getStringSet(KEY_FLASHED_CONFIGS, null)
        return if (result == null) hashSetOf() else HashSet(result)
    }

    fun addFlashedConfigKeys(keys: List<String>) {
        val flashed = loadFlashedConfigs()
        flashed.addAll(keys)
        saveFlashedConfigs(flashed)
    }

    fun clearFlashedConfigs() {
        saveFlashedConfigs(emptySet())
    }

    fun loadEmbeddingConfigFiles(): List<EmbeddingConfigManager.ConfigFileInfo> {
        return embeddingConfigManager.loadAndValidateConfigFiles(context)
    }

    fun deleteEmbeddingConfigs(
        configs: List<EmbeddingConfigManager.ConfigFileInfo>,
        flashed: Set<String>
    ): Int {
        var count = 0
        for (config in configs) {
            if (flashed.contains(config.toFlashedKey())) continue
            if (config.file.delete()) count++
        }
        return count
    }

    fun flashEmbeddingConfigs(configs: List<EmbeddingConfigManager.ConfigFileInfo>) {
        embeddingConfigManager.flashConfigs(context, configs)
        addFlashedConfigKeys(configs.map { config -> config.toFlashedKey() })
    }

    fun prepareFontImport(uri: Uri): FontImportPreparation {
        val fontFile = fontInstallerManager.copyFontToTemp(context, uri)
        return FontImportPreparation(
            file = fontFile,
            originalFileName = getFileName(uri)
        )
    }

    fun installFont(fontFile: File?, fontName: String, fontDescription: String) {
        fontInstallerManager.installFont(context, fontFile, fontName, fontDescription)
    }

    fun loadOvConfigSelection(mode: Int): OvConfigSelection {
        val config = ovConfigManager.loadConfig(context)
        return OvConfigSelection(
            allPackages = loadLaunchablePackageNames(),
            configMap = config,
            selectedPackages = ovConfigManager.getPackagesForMode(config, mode)
        )
    }

    fun saveOvConfig(
        configMap: MutableMap<String, OvCommonConfigManager.AppConfig>,
        selectedPackages: List<String>,
        mode: Int
    ): String {
        ovConfigManager.updateConfigForMode(configMap, selectedPackages, mode)
        return ovConfigManager.saveConfig(context, configMap)
    }

    private fun saveFlashedConfigs(set: Set<String>) {
        moduleSettings.edit { putStringSet(KEY_FLASHED_CONFIGS, HashSet(set)) }
    }

    private fun isForceResizableActivitiesEnabled(): Boolean {
        val result = shellExecutor.executeRootCommand("settings get global force_resizable_activities", 2)
        return result.isSuccess && result.output == "1"
    }

    private val moduleSettings by lazy {
        context.getSharedPreferences(PREF_MODULE_SETTINGS, Context.MODE_PRIVATE)
    }

    private fun EmbeddingConfigManager.ConfigFileInfo.toFlashedKey(): String {
        return timestamp + "_" + packageName
    }

    private fun getFileName(uri: Uri): String? {
        var result: String? = null
        if (uri.scheme == "content") {
            try {
                val cursor: Cursor? = context.contentResolver.query(uri, null, null, null, null)
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

    private fun loadLaunchablePackageNames(): List<String> {
        cachedLaunchablePackages?.let { return it }

        val packages = mutableListOf<String>()
        val packageManager = context.packageManager
        val apps = packageManager.getInstalledApplications(0)
        for (app in apps) {
            if (packageManager.getLaunchIntentForPackage(app.packageName) != null) {
                packages.add(app.packageName)
            }
        }
        cachedLaunchablePackages = packages
        return packages
    }

    companion object {
        const val RESULT_SUCCESS = "success"

        private const val PREF_MODULE_SETTINGS = "module_settings"
        private const val KEY_REMOVE_BLACKLIST = "remove_blacklist"
        private const val KEY_FLASHED_CONFIGS = "flashed_configs"
        private const val KEY_SPLIT_SCREEN_MANDATORY = "Split_Screen_mandatory"
        private const val KEY_ALLOW_DISPLAY_DOLBY = "allow_display_dolby"
        private const val KEY_PERMISSION_CONTROLLER_HOOK = "PermissionControllerHook"
        private const val KEY_APP_DETAILS = "app_details"
    }
}

data class FontImportPreparation(
    val file: File,
    val originalFileName: String?
)

data class OvConfigSelection(
    val allPackages: List<String>,
    val configMap: MutableMap<String, OvCommonConfigManager.AppConfig>,
    val selectedPackages: List<String>
)
