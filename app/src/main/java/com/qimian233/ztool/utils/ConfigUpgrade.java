package com.qimian233.ztool.utils;
import android.content.Context;

import com.qimian233.ztool.hook.modules.SharedPreferencesTool.ModulePreferencesUtils;

import android.util.Log;

import java.util.Map;

// 用于升级配置的类，它用于移除旧配置的module_enabled_前缀，并保存新的配置。
// 为其他部分完成删除PREFIX前缀的工作后方可启用这个工具类
public class ConfigUpgrade {
    ModulePreferencesUtils mPreferencesUtils;
    String TAG = "ConfigUpgrade";
    public void clearAndUpgradeConfig(Context context) {
        mPreferencesUtils = new ModulePreferencesUtils(context);
        Map<String,Object> allSettings = mPreferencesUtils.getAllSettings();
        Log.d(TAG, "Successfully fetched all settings:\n" + allSettings.toString());
        mPreferencesUtils.clearAllSettings();
        Log.d(TAG,"All config wiped, start upgrading config");
        // writeConfigToSharedPrefs方法内置了移除module_enabled_前缀的操作，此处直接调用即可。
        mPreferencesUtils.writeConfigToSharedPrefs(allSettings);
        mPreferencesUtils.saveBooleanSetting("isConfigUpgraded", true);
        Log.d(TAG,"Config upgraded successfully");
    }

    public boolean isConfigNeedUpgrade(Context context) {
        mPreferencesUtils = new ModulePreferencesUtils(context);
        Map<String,Object> allSettings = mPreferencesUtils.getAllSettings();
        // 如果配置为空，则不需要升级（可能是用户点击了“清除配置”，或者全新安装了APP）
        // 这个时候可以顺便设置一个配置升级标记，避免重复执行升级操作。
        if (allSettings.isEmpty()){
            Log.d(TAG, "Config is empty, maybe user performed reset or this is a fresh install, skipping upgrade.");
            mPreferencesUtils.saveBooleanSetting("isConfigUpgraded", true);
            return false;
        }
        // 先尝试读取新的配置升级标记，如果没有，则需要升级配置
        if (!mPreferencesUtils.loadBooleanSetting("isConfigUpgraded", false)) {
            Log.d(TAG,"Old config format detected, need to upgrade config.");
            return true;
        }
        // 如果上面的判断没有触发return，则开始逐条遍历配置信息，如果存在module_enabled_前缀，则说明需要升级配置
        for (Map.Entry<String, Object> entry : allSettings.entrySet()) {
            if (entry.getKey().startsWith("module_enabled_")) {
                Log.d(TAG,"Old config format detected, need to upgrade config.");
                return true;
            }
        }
        Log.d(TAG,"Config is already upgraded.");
        return false;
    }

    // 供外部调用的升级配置方法
    public static boolean configUpgrader(Context context){
        ConfigUpgrade configUpgrade = new ConfigUpgrade();
        if (configUpgrade.isConfigNeedUpgrade(context)) {
            configUpgrade.clearAndUpgradeConfig(context);
            return true;
        } else {
            return false;
        }
    }
}
