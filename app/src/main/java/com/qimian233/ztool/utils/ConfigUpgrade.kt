package com.qimian233.ztool.utils

import android.content.Context
import android.util.Log
import com.qimian233.ztool.EnhancedShellExecutor
import java.io.File

// 用于升级配置的类，它用于移除旧配置的module_enabled_前缀，并保存新的配置。
// 为其他部分完成删除PREFIX前缀的工作后方可启用这个工具类
object ConfigUpgrade {
    private const val TAG = "ConfigUpgrade"
    private var mPreferencesUtils: ModulePreferencesUtils? = null
    private var mCachedXSharedPrefsDir: String? = null

    private fun getPreferencesUtils(context: Context): ModulePreferencesUtils {
        return mPreferencesUtils ?: ModulePreferencesUtils(context).also { mPreferencesUtils = it }
    }

    // 执行器方法组
    private fun upgradeConfigFormat(context: Context) {
        try {
            val prefs = getPreferencesUtils(context)
            val allSettings = prefs.getAllSettings()
            Log.d(TAG, "Successfully fetched all settings:\n$allSettings")
            prefs.clearAllSettings()
            Log.d(TAG, "All config wiped, start upgrading config")
            // writeConfigToSharedPrefs方法内置了移除module_enabled_前缀的操作，此处直接调用即可。
            prefs.writeConfigToSharedPrefs(allSettings)
            prefs.saveBooleanSetting("isConfigUpgraded", true)
            Log.d(TAG, "Config format upgraded successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to upgrade config format: ", e)
        }
    }

    private fun upgradeRemotePrefs(context: Context) {
        try {
            var oldDir = getXSharedPreferenceDirectory()
            if (oldDir == null || oldDir.trim().isEmpty()) {
                Log.i(TAG, "No old XSharedPreferences directory found, skipping remote prefs upgrade.")
                return
            }
            oldDir = oldDir.trim()
            if (oldDir.contains("\n")) {
                oldDir = oldDir.substring(0, oldDir.indexOf("\n")).trim()
            }
            Log.d(TAG, "Old XSharedPreferences directory: $oldDir")

            val sharedPrefsDir = File(context.filesDir.parentFile, "shared_prefs")
            val destPath = sharedPrefsDir.absolutePath
            val appUid = android.os.Process.myUid()
            val executor = EnhancedShellExecutor.getInstance()

            val copyResult = executor.executeRootCommand(
                "cp " + oldDir + "/* " + destPath + "/ 2>/dev/null; " +
                        "chown -R " + appUid + " " + destPath + "/ 2>/dev/null; " +
                        "chmod -R 660 " + destPath + "/* 2>/dev/null; " +
                        "echo DONE",
                10
            )
            if (!copyResult.isSuccess || !copyResult.output.contains("DONE")) {
                Log.e(TAG, "Failed to copy old prefs files: " + copyResult.output)
                return
            }
            Log.d(TAG, "Copied old config files to shared_prefs")

            val prefs = getPreferencesUtils(context)
            val oldSettings = prefs.getAllSettingsFromLocal()
            if (oldSettings.isEmpty()) {
                Log.i(TAG, "No settings found in old config, skipping remote sync.")
                return
            }
            Log.d(TAG, "Read " + oldSettings.size + " settings from old config, syncing to RemotePreferences...")
            prefs.writeConfigToSharedPrefs(oldSettings)

            val newSettings = prefs.getAllSettings()
            if (newSettings.isNotEmpty()) {
                Log.d(TAG, "Sync verified: " + newSettings.size + " settings in RemotePreferences.")
                prefs.deleteLocalModulePreferences()
                executor.executeRootCommand("rm -rf " + oldDir, 5)
                Log.d(TAG, "Remote prefs upgrade completed successfully.")
            } else {
                Log.e(TAG, "Remote prefs sync verification failed, keeping old files.")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to upgrade remote prefs: ", e)
        }
    }

    // 单独检测向量对应的方法
    private fun isConfigEmpty(prefs: ModulePreferencesUtils): Boolean {
        return prefs.getAllSettings().isEmpty()
    }

    private fun isUpgradedFlagExists(prefs: ModulePreferencesUtils): Boolean {
        return prefs.loadBooleanSetting("isConfigUpgraded", false)
    }

    private fun isConfigItemStartsWithOldPrefix(prefs: ModulePreferencesUtils): Boolean {
        for (key in prefs.getAllSettings().keys) {
            if (key.startsWith("module_enabled_")) {
                Log.d(TAG, "Old config format detected, need to upgrade config.")
                return true
            }
        }
        return false
    }

    private fun getXSharedPreferenceDirectory(): String? {
        if (mCachedXSharedPrefsDir != null) {
            return mCachedXSharedPrefsDir
        }
        val executor = EnhancedShellExecutor.getInstance()
        val result = executor.executeRootCommand(
            "find /data/misc -type d -name 'com.qimian233.ztool' 2>/dev/null | grep -E '[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}/prefs/com.qimian233.ztool$'", 5
        )
        if (!result.isSuccess) {
            Log.i(TAG, "Unable to find New XSharedPreferences directory, command failed!")
            return null
        }
        mCachedXSharedPrefsDir = result.output
        return mCachedXSharedPrefsDir
    }

    // 两个配置升级检测点对应的综合检测门禁
    fun isConfigFormatUpgradeRequired(context: Context): Boolean {
        val prefs = getPreferencesUtils(context)
        // 如果配置为空，则不需要升级（可能是用户点击了“清除配置”，或者全新安装了APP）
        // 这个时候可以顺便设置一个配置升级标记，避免重复执行升级操作。
        if (isConfigEmpty(prefs)) {
            Log.d(TAG, "Config is empty, maybe user performed reset or this is a fresh install, skipping upgrade.")
            prefs.saveBooleanSetting("isConfigUpgraded", true)
            return false
        }
        // 先尝试读取新的配置升级标记，如果没有，则需要升级配置
        if (!isUpgradedFlagExists(prefs)) {
            Log.d(TAG, "Upgraded flag not detected, try alternative method to detect config version.")
            return isConfigItemStartsWithOldPrefix(prefs)
        }
        Log.d(TAG, "Config is already upgraded.")
        return false
    }

    private fun isRemotePrefsUpgradeRequired(context: Context): Boolean {
        val prefs = ModulePreferencesUtils(context)
        val isUpgradeNeeded = isConfigEmpty(prefs) && getXSharedPreferenceDirectory() != null
        if (isUpgradeNeeded) Log.w(TAG, "Please upgrade to RemotePreferences!") else Log.i(TAG, "No need to upgrade from XSharedPreferences.")
        return isUpgradeNeeded
    }

    // 供外部调用的升级配置方法
    // 依次检查 RemotePrefs 和 Prefs 格式的升级必要性，如果有必要，就升级配置
    // 返回值用于决定前端是否展示配置升级弹窗
    // New 前缀也是老配置了吗...有点搞
    fun configUpgrader(context: Context): Boolean {
        // Java 版每次调用都会创建新实例，这里重置实例状态以保持行为一致
        mPreferencesUtils = null
        mCachedXSharedPrefsDir = null

        if (isRemotePrefsUpgradeRequired(context)) { // 升级到 RemotePrefs 不需要弹窗
            upgradeRemotePrefs(context)
        }

        return if (isConfigFormatUpgradeRequired(context)) {
            upgradeConfigFormat(context)
            true
        } else {
            false
        }
    }
}
