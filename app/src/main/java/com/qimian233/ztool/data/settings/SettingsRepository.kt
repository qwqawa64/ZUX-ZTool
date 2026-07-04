package com.qimian233.ztool.data.settings

import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.util.Log
import com.qimian233.ztool.BuildConfig
import com.qimian233.ztool.R
import com.qimian233.ztool.data.theme.ThemePreferencesRepository
import com.qimian233.ztool.hook.modules.SharedPreferencesTool.ModulePreferencesUtils
import com.qimian233.ztool.service.LogServiceManager
import com.qimian233.ztool.ui.theme.FrontendStyle
import com.qimian233.ztool.ui.theme.MaterialColorSpec
import com.qimian233.ztool.ui.theme.MaterialPalette
import com.qimian233.ztool.ui.theme.ThemeMode
import com.qimian233.ztool.utils.FileManager
import com.qimian233.ztool.utils.LogUtils
import com.qimian233.ztool.viewmodel.SettingsUiState

class SettingsRepository(
    private val context: Context
) {
    private val themePreferences = ThemePreferencesRepository(context)
    private val prefsUtils = ModulePreferencesUtils(context)

    fun loadState(): SettingsUiState {
        val themeSettings = themePreferences.loadSettings()
        return SettingsUiState(
            isLogServiceEnabled = LogServiceManager.isServiceEnabled(context),
            isDetailedLoggingEnabled = prefsUtils.loadBooleanSetting(KEY_DETAILED_LOGGING, false),
            isEntryDisplayedInSettings = prefsUtils.loadBooleanSetting(KEY_DISPLAY_ENTRY_IN_SETTINGS, false),
            isHomepageYiyanEnabled = prefsUtils.loadBooleanSetting(KEY_HOMEPAGE_YIYAN, true),
            versionName = getVersionName(),
            commitCount = BuildConfig.GIT_COMMIT_COUNT,
            commitHash = BuildConfig.GIT_COMMIT_HASH,
            themeSettings = themeSettings,
            manualSeedColorText = formatSeedColor(themeSettings.manualSeedColor),
            lsposedServiceProtector = prefsUtils.loadBooleanSetting(KEY_LSPOSED_SERVICE_PROTECTOR, false),
        )
    }

    fun backupConfig(uri: Uri): Boolean {
        return FileManager.saveConfigWithSAF(
            context,
            uri,
            FileManager.generateBackupFileName(),
            ModulePreferencesUtils.getAllSettingsAsJSON(context)
        )
    }

    fun restoreConfig(uri: Uri): Boolean {
        val content = FileManager.readConfigWithSAF(context, uri) ?: return false
        Log.d(TAG, "Read config content: $content")
        ModulePreferencesUtils.restoreConfig(context, content)
        return true
    }

    fun restoreDefaultConfig() {
        prefsUtils.clearAllSettings()
    }

    fun setLogServiceEnabled(isEnabled: Boolean) {
        if (isEnabled) {
            LogServiceManager.startLogService(context)
        } else {
            LogServiceManager.stopLogService(context)
        }
    }

    fun setDetailedLoggingEnabled(isEnabled: Boolean) {
        prefsUtils.saveBooleanSetting(KEY_DETAILED_LOGGING, isEnabled)
    }

    fun setEntryInSettingsEnabled(isEnabled: Boolean) {
        prefsUtils.saveBooleanSetting(KEY_DISPLAY_ENTRY_IN_SETTINGS, isEnabled)
    }

    fun setHomepageYiyanEnabled(isEnabled: Boolean) {
        prefsUtils.saveBooleanSetting(KEY_HOMEPAGE_YIYAN, isEnabled)
    }

    fun saveLsposedServiceProtector(enabled: Boolean) {
        prefsUtils.saveBooleanSetting(KEY_LSPOSED_SERVICE_PROTECTOR, enabled)
    }


    fun setFrontendStyle(style: FrontendStyle) {
        themePreferences.saveFrontendStyle(style)
    }

    fun setThemeMode(mode: ThemeMode) {
        themePreferences.saveThemeMode(mode)
    }

    fun setMaterialColorSpec(spec: MaterialColorSpec) {
        themePreferences.saveMaterialColorSpec(spec)
    }

    fun setMaterialPalette(palette: MaterialPalette) {
        themePreferences.saveMaterialPalette(palette)
    }

    fun setDynamicColorEnabled(enabled: Boolean) {
        themePreferences.saveDynamicColorEnabled(enabled)
    }

    fun setAmoledBlackEnabled(enabled: Boolean) {
        themePreferences.saveAmoledBlackEnabled(enabled)
    }

    fun setPredictiveBackGestureEnabled(enabled: Boolean) {
        themePreferences.savePredictiveBackGestureEnabled(enabled)
    }

    fun setEnableFloatingBottomBar(enabled: Boolean) {
        themePreferences.saveEnableFloatingBottomBar(enabled)
    }

    fun setEnableFloatingBottomBarBlur(enabled: Boolean) {
        themePreferences.saveEnableFloatingBottomBarBlur(enabled)
    }

    fun setManualColorEnabled(enabled: Boolean) {
        themePreferences.saveManualColorEnabled(enabled)
    }

    fun setManualSeedColor(color: Long) {
        themePreferences.saveManualSeedColor(color)
    }
    fun backupFileName(): String = FileManager.generateBackupFileName()

    fun formatSeedColor(color: Long): String {
        return "#%08X".format(color)
    }

    fun parseSeedColor(input: String): Long? {
        val trimmed = input.trim()
        val normalized = when {
            trimmed.startsWith("#") -> trimmed.drop(1)
            trimmed.startsWith("0x", ignoreCase = true) -> trimmed.drop(2)
            else -> trimmed
        }
        if (normalized.length != 6 && normalized.length != 8) return null
        if (!normalized.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }) return null
        val argb = if (normalized.length == 6) {
            "FF$normalized"
        } else {
            normalized
        }
        return argb.toLong(16)
    }

    fun exportFileName(): String = LogUtils.exportFileName()

    fun exportLogsToUri(uri: Uri): Boolean = LogUtils.exportLogsToUri(context, uri)

    fun cleanupAppLogsIfNeeded() = LogUtils.cleanupAppLogsIfNeeded(context)

    fun syncLsposedLogs() = LogUtils.syncLsposedLogs(context)

    fun deleteAllLogs() = LogUtils.deleteAllLogs(context)

    companion object {
        private const val TAG = "SettingsRepository"
        private const val KEY_DETAILED_LOGGING = "isDetailedLogging"
        private const val KEY_HOMEPAGE_YIYAN = "enable_homepage_yiyan"
        private const val KEY_DISPLAY_ENTRY_IN_SETTINGS = "ztool_settings_entry"
        private const val KEY_LSPOSED_SERVICE_PROTECTOR = "lsposed_service_protector"
    }

    private fun getVersionName(): String {
        return try {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            packageInfo.versionName.orEmpty()
        } catch (e: PackageManager.NameNotFoundException) {
            Log.w(TAG, "Unable to get module version: ${e.message}")
            context.getString(R.string.unknown)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to update module status: ${e.message}")
            context.getString(R.string.unknown)
        }
    }
}

