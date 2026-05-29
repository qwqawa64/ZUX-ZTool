package com.qimian233.ztool.data.settings

import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.util.Log
import com.qimian233.ztool.R
import com.qimian233.ztool.hook.modules.SharedPreferencesTool.ModulePreferencesUtils
import com.qimian233.ztool.service.LogServiceManager
import com.qimian233.ztool.utils.FileManager
import com.qimian233.ztool.viewmodel.SettingsUiState

class SettingsRepository(
    private val context: Context
) {
    fun loadState(): SettingsUiState {
        val prefs = ModulePreferencesUtils(context)
        return SettingsUiState(
            isLogServiceEnabled = LogServiceManager.isServiceEnabled(context),
            isDetailedLoggingEnabled = prefs.loadBooleanSetting(KEY_DETAILED_LOGGING, false),
            isHomepageYiyanEnabled = prefs.loadBooleanSetting(KEY_HOMEPAGE_YIYAN, true),
            moduleVersion = getModuleVersion()
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
        ModulePreferencesUtils(context).clearAllSettings()
    }

    fun setLogServiceEnabled(isEnabled: Boolean) {
        if (isEnabled) {
            LogServiceManager.startLogService(context)
        } else {
            LogServiceManager.stopLogService(context)
        }
    }

    fun setDetailedLoggingEnabled(isEnabled: Boolean) {
        ModulePreferencesUtils(context).saveBooleanSetting(KEY_DETAILED_LOGGING, isEnabled)
    }

    fun setHomepageYiyanEnabled(isEnabled: Boolean) {
        ModulePreferencesUtils(context).saveBooleanSetting(KEY_HOMEPAGE_YIYAN, isEnabled)
    }

    fun backupFileName(): String = FileManager.generateBackupFileName()

    private fun getModuleVersion(): String {
        return try {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            val versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                packageInfo.longVersionCode.toInt()
            } else {
                @Suppress("DEPRECATION")
                packageInfo.versionCode
            }
            "${packageInfo.versionName} ($versionCode)"
        } catch (e: PackageManager.NameNotFoundException) {
            Log.w(TAG, "Unable to get module version: ${e.message}")
            context.getString(R.string.unknown)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to update module status: ${e.message}")
            context.getString(R.string.unknown)
        }
    }

    companion object {
        private const val TAG = "SettingsRepository"
        private const val KEY_DETAILED_LOGGING = "isDetailedLogging"
        private const val KEY_HOMEPAGE_YIYAN = "enable_homepage_yiyan"
    }
}
