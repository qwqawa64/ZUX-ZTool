package com.qimian233.ztool.hook.modules.SharedPreferencesTool;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

/**
 * SharedPreferences工具类，封装Xposed模块配置的读写操作
 */
public class ModulePreferencesUtils {

    private static final String PREFS_NAME = "xposed_module_config";
    private static final String PREFIX_ENABLED = "module_enabled_";

    private Context mContext;
    private String mModulePackageName;

    public ModulePreferencesUtils(Context context) {
        this(context, "com.qimian233.ztool");
    }

    public ModulePreferencesUtils(Context context, String modulePackageName) {
        this.mContext = context;
        this.mModulePackageName = modulePackageName;
    }

    /**
     * 获取模块的SharedPreferences实例
     */
    public SharedPreferences getModulePreferences() {
        try {
            Context moduleContext = mContext.createPackageContext(mModulePackageName, Context.CONTEXT_IGNORE_SECURITY);
            return moduleContext.getSharedPreferences(PREFS_NAME, Context.MODE_WORLD_READABLE);
        } catch (Exception e) {
            Log.e("ModulePreferences", "Failed to get module preferences, using fallback", e);
            // 降级方案：使用当前Context
            return mContext.getSharedPreferences(PREFS_NAME, Context.MODE_WORLD_READABLE);
        }
    }

    /**
     * 加载布尔型设置
     * @param featureName 功能名称（如："remove_blacklist"）
     * @param defaultValue 默认值
     * @return 设置值
     */
    public boolean loadBooleanSetting(String featureName, boolean defaultValue) {
        SharedPreferences prefs = getModulePreferences();
        boolean value = prefs.getBoolean(PREFIX_ENABLED + featureName, defaultValue);
        Log.d("ModulePreferences", "Loading " + featureName + ": " + value);
        return value;
    }

    /**
     * 保存布尔型设置
     * @param featureName 功能名称（如："remove_blacklist"）
     * @param value 要保存的值
     * @return 是否保存成功
     */
    public boolean saveBooleanSetting(String featureName, boolean value) {
        SharedPreferences prefs = getModulePreferences();
        boolean success = prefs.edit()
                .putBoolean(PREFIX_ENABLED + featureName, value)
                .commit();
        Log.d("ModulePreferences", "Saved " + featureName + ": " + value + ", success: " + success);
        return success;
    }

    /**
     * 加载字符串设置
     * @param featureName 功能名称
     * @param defaultValue 默认值
     * @return 设置值
     */
    public String loadStringSetting(String featureName, String defaultValue) {
        SharedPreferences prefs = getModulePreferences();
        return prefs.getString(PREFIX_ENABLED + featureName, defaultValue);
    }

    /**
     * 保存字符串设置
     * @param featureName 功能名称
     * @param value 要保存的值
     * @return 是否保存成功
     */
    public boolean saveStringSetting(String featureName, String value) {
        SharedPreferences prefs = getModulePreferences();
        return prefs.edit()
                .putString(PREFIX_ENABLED + featureName, value)
                .commit();
    }

    /**
     * 删除指定设置
     * @param featureName 功能名称
     * @return 是否删除成功
     */
    public boolean removeSetting(String featureName) {
        SharedPreferences prefs = getModulePreferences();
        return prefs.edit()
                .remove(PREFIX_ENABLED + featureName)
                .commit();
    }

    /**
     * 清除所有设置
     * @return 是否清除成功
     */
    public boolean clearAllSettings() {
        SharedPreferences prefs = getModulePreferences();
        return prefs.edit()
                .clear()
                .commit();
    }
}
