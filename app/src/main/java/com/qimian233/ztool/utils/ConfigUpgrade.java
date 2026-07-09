package com.qimian233.ztool.utils;
import android.content.Context;

import com.qimian233.ztool.EnhancedShellExecutor;
import com.qimian233.ztool.hook.modules.SharedPreferencesTool.ModulePreferencesUtils;

import android.util.Log;

import java.io.File;
import java.util.Map;

// 用于升级配置的类，它用于移除旧配置的module_enabled_前缀，并保存新的配置。
// 为其他部分完成删除PREFIX前缀的工作后方可启用这个工具类
public class ConfigUpgrade {
    ModulePreferencesUtils mPreferencesUtils;
    String TAG = "ConfigUpgrade";
    private String mCachedXSharedPrefsDir = null;

    // 执行器方法组

    private void upgradeConfigFormat(Context context) {
        try {
            if (mPreferencesUtils == null) mPreferencesUtils = new ModulePreferencesUtils(context);
            Map<String, Object> allSettings = mPreferencesUtils.getAllSettings();
            Log.d(TAG, "Successfully fetched all settings:\n" + allSettings.toString());
            mPreferencesUtils.clearAllSettings();
            Log.d(TAG, "All config wiped, start upgrading config");
            // writeConfigToSharedPrefs方法内置了移除module_enabled_前缀的操作，此处直接调用即可。
            mPreferencesUtils.writeConfigToSharedPrefs(allSettings);
            mPreferencesUtils.saveBooleanSetting("isConfigUpgraded", true);
            Log.d(TAG, "Config format upgraded successfully");
        } catch (Exception e) {
            Log.e(TAG, "Failed to upgrade config format: ", e);
        }
    }

    private void upgradeRemotePrefs(Context context) {
        try {
            String oldDir = getXSharedPreferenceDirectory();
            if (oldDir == null || oldDir.trim().isEmpty()) {
                Log.i(TAG, "No old XSharedPreferences directory found, skipping remote prefs upgrade.");
                return;
            }
            oldDir = oldDir.trim();
            if (oldDir.contains("\n")) {
                oldDir = oldDir.substring(0, oldDir.indexOf("\n")).trim();
            }
            Log.d(TAG, "Old XSharedPreferences directory: " + oldDir);

            File sharedPrefsDir = new File(context.getFilesDir().getParentFile(), "shared_prefs");
            String destPath = sharedPrefsDir.getAbsolutePath();
            int appUid = android.os.Process.myUid();
            EnhancedShellExecutor executor = EnhancedShellExecutor.getInstance();

            EnhancedShellExecutor.ShellResult copyResult = executor.executeRootCommand(
                "cp " + oldDir + "/* " + destPath + "/ 2>/dev/null; " +
                "chown -R " + appUid + " " + destPath + "/ 2>/dev/null; " +
                "chmod -R 660 " + destPath + "/* 2>/dev/null; " +
                "echo DONE",
                10
            );
            if (!copyResult.isSuccess() || !copyResult.output.contains("DONE")) {
                Log.e(TAG, "Failed to copy old prefs files: " + copyResult.output);
                return;
            }
            Log.d(TAG, "Copied old config files to shared_prefs");

            if (mPreferencesUtils == null) mPreferencesUtils = new ModulePreferencesUtils(context);
            Map<String, Object> oldSettings = mPreferencesUtils.getAllSettingsFromLocal();
            if (oldSettings == null || oldSettings.isEmpty()) {
                Log.i(TAG, "No settings found in old config, skipping remote sync.");
                return;
            }
            Log.d(TAG, "Read " + oldSettings.size() + " settings from old config, syncing to RemotePreferences...");
            mPreferencesUtils.writeConfigToSharedPrefs(oldSettings);

            Map<String, Object> newSettings = mPreferencesUtils.getAllSettings();
            if (newSettings != null && !newSettings.isEmpty()) {
                Log.d(TAG, "Sync verified: " + newSettings.size() + " settings in RemotePreferences.");
                mPreferencesUtils.deleteLocalModulePreferences();
                executor.executeRootCommand("rm -rf " + oldDir, 5);
                Log.d(TAG, "Remote prefs upgrade completed successfully.");
            } else {
                Log.e(TAG, "Remote prefs sync verification failed, keeping old files.");
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to upgrade remote prefs: ", e);
        }
    }

    // 单独检测向量对应的方法
    private boolean isConfigEmpty(ModulePreferencesUtils prefs) {
        return prefs.getAllSettings().isEmpty();
    }

    private boolean isUpgradedFlagExists(ModulePreferencesUtils prefs) {
        return prefs.loadBooleanSetting("isConfigUpgraded", false);
    }

    private boolean isConfigItemStartsWithOldPrefix(ModulePreferencesUtils prefs) {
        Map<String, Object> allSettings = prefs.getAllSettings();
        if (allSettings == null) return false;
        for (Map.Entry<String, Object> entry : allSettings.entrySet()) {
            if (entry.getKey().startsWith("module_enabled_")) {
                Log.d(TAG,"Old config format detected, need to upgrade config.");
                return true;
            }
        }
        return false;
    }

    private String getXSharedPreferenceDirectory() {
        if (mCachedXSharedPrefsDir != null) {
            return mCachedXSharedPrefsDir;
        }
        EnhancedShellExecutor executor = EnhancedShellExecutor.getInstance();
        EnhancedShellExecutor.ShellResult result = executor.executeRootCommand(
        "find /data/misc -type d -name 'com.qimian233.ztool' 2>/dev/null | grep -E '[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}/prefs/com.qimian233.ztool$'", 5);
        if (!result.isSuccess()) {
            Log.i(TAG,"Unable to find New XSharedPreferences directory, command failed!");
            return null;
        }
        mCachedXSharedPrefsDir = result.output;
        return mCachedXSharedPrefsDir;
    }

    // 两个配置升级检测点对应的综合检测门禁

    public boolean isConfigFormatUpgradeRequired(Context context) {
        if (mPreferencesUtils == null) mPreferencesUtils = new ModulePreferencesUtils(context);
        // 如果配置为空，则不需要升级（可能是用户点击了“清除配置”，或者全新安装了APP）
        // 这个时候可以顺便设置一个配置升级标记，避免重复执行升级操作。
        if (isConfigEmpty(mPreferencesUtils)) {
            Log.d(TAG, "Config is empty, maybe user performed reset or this is a fresh install, skipping upgrade.");
            mPreferencesUtils.saveBooleanSetting("isConfigUpgraded", true);
            return false;
        }
        // 先尝试读取新的配置升级标记，如果没有，则需要升级配置
        if (!isUpgradedFlagExists(mPreferencesUtils)) {
            Log.d(TAG,"Upgraded flag not detected, try alternative method to detect config version.");
            return isConfigItemStartsWithOldPrefix(mPreferencesUtils);
        }
        Log.d(TAG,"Config is already upgraded.");
        return false;
    }

    private boolean isRemotePrefsUpgradeRequired(Context context) {
        ModulePreferencesUtils prefs = new ModulePreferencesUtils(context);
        boolean isUpgradeNeeded = isConfigEmpty(prefs) && getXSharedPreferenceDirectory() != null;
        if (isUpgradeNeeded) Log.w(TAG, "Please upgrade to RemotePreferences!"); else Log.i(TAG, "No need to upgrade from XSharedPreferences.");
        return isUpgradeNeeded;
    }

    // 供外部调用的升级配置方法
    // 依次检查 RemotePrefs 和 Prefs 格式的升级必要性，如果有必要，就升级配置
    // 返回值用于决定前端是否展示配置升级弹窗
    // New 前缀也是老配置了吗...有点搞
    public static boolean configUpgrader(Context context){
        ConfigUpgrade configUpgrade = new ConfigUpgrade();

        if (configUpgrade.isRemotePrefsUpgradeRequired(context)) { // 升级到 RemotePrefs 不需要弹窗
            configUpgrade.upgradeRemotePrefs(context);
        }

        if (configUpgrade.isConfigFormatUpgradeRequired(context)) {
            configUpgrade.upgradeConfigFormat(context);
            return true;
        } else {
            return false;
        }
    }
}
