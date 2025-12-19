package com.qimian233.ztool.hook.base;

import android.text.TextUtils;

import de.robv.android.xposed.XSharedPreferences;

/**
 * Xposed模块配置管理工具类
 * 提供统一的XSharedPreferences操作接口
 */
public class PreferenceHelper {

    private static final String DEFAULT_PREFS_NAME = "xposed_module_config";
    private static final String DEFAULT_MODULE_PACKAGE = "com.qimian233.ztool";

    private static volatile PreferenceHelper instance;
    private final XSharedPreferences mPreferences;

    private PreferenceHelper() {
        this(DEFAULT_MODULE_PACKAGE, DEFAULT_PREFS_NAME);
    }

    private PreferenceHelper(String modulePackage, String prefsName) {
        mPreferences = new XSharedPreferences(modulePackage, prefsName);
    }

    /**
     * 获取默认实例（使用默认包名和配置名）
     */
    public static PreferenceHelper getInstance() {
        if (instance == null) {
            synchronized (PreferenceHelper.class) {
                if (instance == null) {
                    instance = new PreferenceHelper();
                }
            }
        }
        return instance;
    }

    /**
     * 重新加载配置（每次读取前调用）
     */
    public void reload() {
        mPreferences.reload();
    }

    /**
     * 读取布尔值配置
     */
    public boolean getBoolean(String key, boolean defaultValue) {
        reload();
        return mPreferences.getBoolean(key, defaultValue);
    }

    /**
     * 读取字符串配置
     */
    public String getString(String key, String defaultValue) {
        reload();
        return mPreferences.getString(key, defaultValue);
    }

    /**
     * 读取整数配置
     */
    public int getInt(String key, int defaultValue) {
        reload();
        return mPreferences.getInt(key, defaultValue);
    }

    /**
     * 读取浮点数配置
     */
    public float getFloat(String key, float defaultValue) {
        reload();
        return mPreferences.getFloat(key, defaultValue);
    }

    /**
     * 读取长整数配置
     */
    public long getLong(String key, long defaultValue) {
        reload();
        return mPreferences.getLong(key, defaultValue);
    }

    /**
     * 读取并解析逗号分隔的字符串列表
     * @param key 配置键
     * @param defaultValue 默认值
     * @return 字符串数组
     */
    public String[] getStringArray(String key, String[] defaultValue) {
        String value = getString(key, "");
        if (TextUtils.isEmpty(value)) {
            return defaultValue;
        }
        return value.split(",");
    }

    /**
     * 读取并解析逗号分隔的字符串列表
     * @param key 配置键
     * @return 字符串数组，配置不存在时返回空数组
     */
    public String[] getStringArray(String key) {
        return getStringArray(key, new String[0]);
    }

    /**
     * 检查配置是否存在
     */
    public boolean contains(String key) {
        reload();
        return mPreferences.contains(key);
    }
}
