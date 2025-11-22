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
        mPreferencesUtils.writeConfigToSharedPrefs(allSettings);
        mPreferencesUtils.saveBooleanSetting("isConfigUpgraded", true);
    }

    public boolean isConfigNeedUpgrade(Context context) {
        mPreferencesUtils = new ModulePreferencesUtils(context);
        Map<String,Object> allSettings = mPreferencesUtils.getAllSettings();
        // 先尝试读取新的配置升级标记，如果没有，则需要升级配置
        if (!mPreferencesUtils.loadBooleanSetting("isConfigUpgraded", false)) return true;
        // 如果上面的判断没有触发return，则开始逐条遍历配置信息，如果存在module_enabled_前缀，则说明需要升级配置
        for (Map.Entry<String, Object> entry : allSettings.entrySet()) {
            if (entry.getKey().startsWith("module_enabled_")) return true;
        }
        return false;
    }

    // 供外部调用的升级配置方法
    public static void configUpgrader(Context context){
        ConfigUpgrade configUpgrade = new ConfigUpgrade();
        if (configUpgrade.isConfigNeedUpgrade(context)) configUpgrade.clearAndUpgradeConfig(context);
    }
}
