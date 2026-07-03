package com.qimian233.ztool.data.settings

import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.util.Log
import android.widget.Toast
import com.qimian233.ztool.BuildConfig
import com.qimian233.ztool.EnhancedShellExecutor
import com.qimian233.ztool.R
import com.qimian233.ztool.data.theme.ThemePreferencesRepository
import com.qimian233.ztool.hook.modules.SharedPreferencesTool.ModulePreferencesUtils
import com.qimian233.ztool.service.LogServiceManager
import com.qimian233.ztool.ui.theme.FrontendStyle
import com.qimian233.ztool.ui.theme.MaterialColorSpec
import com.qimian233.ztool.ui.theme.MaterialPalette
import com.qimian233.ztool.ui.theme.ThemeMode
import com.qimian233.ztool.utils.FileManager
import com.qimian233.ztool.utils.FileUtils
import com.qimian233.ztool.viewmodel.SettingsUiState
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SettingsRepository(
    private val context: Context
) {
    private val themePreferences = ThemePreferencesRepository(context)

    fun loadState(): SettingsUiState {
        val prefs = ModulePreferencesUtils(context)
        val themeSettings = themePreferences.loadSettings()
        return SettingsUiState(
            isLogServiceEnabled = LogServiceManager.isServiceEnabled(context),
            isDetailedLoggingEnabled = prefs.loadBooleanSetting(KEY_DETAILED_LOGGING, false),
            isEntryDisplayedInSettings = prefs.loadBooleanSetting(KEY_DISPLAY_ENTRY_IN_SETTINGS, false),
            isHomepageYiyanEnabled = prefs.loadBooleanSetting(KEY_HOMEPAGE_YIYAN, true),
            versionName = getVersionName(),
            commitCount = BuildConfig.GIT_COMMIT_COUNT,
            commitHash = BuildConfig.GIT_COMMIT_HASH,
            themeSettings = themeSettings,
            manualSeedColorText = formatSeedColor(themeSettings.manualSeedColor)
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

    fun setEntryInSettingsEnabled(isEnabled: Boolean) {
        ModulePreferencesUtils(context).saveBooleanSetting(KEY_DISPLAY_ENTRY_IN_SETTINGS, isEnabled)
    }

    fun setHomepageYiyanEnabled(isEnabled: Boolean) {
        ModulePreferencesUtils(context).saveBooleanSetting(KEY_HOMEPAGE_YIYAN, isEnabled)
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

    fun exportFileName(): String {
        return "ZTool_Logs_" +
            SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date()) +
            ".zip"
    }

    fun exportLogsToUri(uri: Uri): Boolean {
        // Sync LSPosed logs before exporting
        syncLsposedLogs()

        val zipFile = zipLogDir() ?: return false
        return FileManager.exportFileWithSAF(
            context,
            uri,
            "logs_" + SimpleDateFormat("yyyy-MM-dd_HHmmss", Locale.US).format(Date()) + ".zip",
            zipFile
        )
    }

    private fun zipLogDir(): File? {
        val dir = logDir()
        if (!dir.exists() || !dir.isDirectory()) return null

        val entries = dir.listFiles()
        if (entries.isNullOrEmpty()) return null

        val outputDir = File(context.cacheDir, "temp")
        if (!outputDir.exists() && !outputDir.mkdirs()) return null

        val zipFile = File(
            outputDir,
            "logs_" + SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date()) + ".zip"
        )
        return if (FileUtils.createZipFromDirectory(dir, zipFile)) zipFile else null
    }

    /**
     * 清理应用日志：如果 Log/app/ 目录下文件总大小超过 10MB，则全部删除
     */
    fun cleanupAppLogsIfNeeded() {
        val appLogDir = File(logDir(), APP_LOG_SUBDIR)
        if (!appLogDir.exists() || !appLogDir.isDirectory()) return

        val files = appLogDir.listFiles() ?: return
        val totalSize = files.sumOf { it.length() }
        val maxSize = 10L * 1024 * 1024 // 10MB

        if (totalSize > maxSize) {
            Log.i(TAG, "应用日志总大小 ${totalSize} 超过 10MB，自动清理")
            for (file in files) {
                file.delete()
            }
        }
    }

    /**
     * 从 /data/adb/lspd/log 同步 LSPosed 日志到应用私有目录
     * 需要 Root 权限，失败时 Toast 提示用户
     */
    fun syncLsposedLogs() {
        val lsposedSrc = File("/data/adb/lspd/log")
        if (!lsposedSrc.exists()) {
            Log.d(TAG, "LSPosed 日志目录不存在，跳过同步")
            return
        }

        val destDir = File(logDir(), LSPOSED_SUBDIR)
        if (!destDir.exists() && !destDir.mkdirs()) {
            Log.w(TAG, "无法创建 LSPosed 日志目标目录")
            return
        }

        val shell = EnhancedShellExecutor.getInstance()
        val result = shell.executeRootCommand(
            "cp -rf /data/adb/lspd/log/* " + destDir.absolutePath +
            " && chmod -R 644 " + destDir.absolutePath + "/*"
        )

        if (result.isSuccess) {
            Log.i(TAG, "LSPosed 日志同步成功")
        } else {
            Log.w(TAG, "LSPosed 日志同步失败: ${result.error}")
            val handler = android.os.Handler(android.os.Looper.getMainLooper())
            handler.post {
                Toast.makeText(
                    context,
                    context.getString(R.string.lsposed_log_sync_failed),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun logDir(): File = File(context.filesDir, LOG_DIR_NAME)

    companion object {
        private const val TAG = "SettingsRepository"
        private const val KEY_DETAILED_LOGGING = "isDetailedLogging"
        private const val KEY_HOMEPAGE_YIYAN = "enable_homepage_yiyan"
        private const val KEY_DISPLAY_ENTRY_IN_SETTINGS = "ztool_settings_entry"
        private const val LOG_DIR_NAME = "Log"
        private const val APP_LOG_SUBDIR = "app"
        private const val LSPOSED_SUBDIR = "lsposed"
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

