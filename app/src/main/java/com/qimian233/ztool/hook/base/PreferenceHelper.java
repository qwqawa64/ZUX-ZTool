package com.qimian233.ztool.hook.base;

import android.content.SharedPreferences;
import android.text.TextUtils;

import io.github.libxposed.api.XposedModule;

/**
 * Xposed 模块配置读取工具（libxposed 版）。
 * <p>
 * 内部通过 {@link XposedModule#getRemotePreferences(String)} 获取远程配置，
 * 替代旧版 {@code XSharedPreferences}，不再需要手动 {@code reload()}。
 * </p>
 * <p>
 * 用法：
 * <pre>{@code
 *   PreferenceHelper prefs = PreferenceHelper.wrap(module);
 *   boolean val = prefs.getBoolean("my_key", false);
 * }</pre>
 * </p>
 */
public class PreferenceHelper {

    private static final String PREFS_NAME = "xposed_module_config";
    private final SharedPreferences mPreferences;

    private PreferenceHelper(SharedPreferences preferences) {
        mPreferences = preferences;
    }

    /**
     * 包装 {@link XposedModule}，内部调用 {@link XposedModule#getRemotePreferences(String)}
     * 以获取模块的远程配置。
     */
    public static PreferenceHelper wrap(XposedModule module) {
        return new PreferenceHelper(module.getRemotePreferences(PREFS_NAME));
    }

    /**
     * 包装已有的 {@link SharedPreferences} 实例（供 App 端等非 Hook 进程使用）。
     */
    public static PreferenceHelper wrap(SharedPreferences prefs) {
        return new PreferenceHelper(prefs);
    }

    // ── 读取方法（无需 reload） ──

    public boolean getBoolean(String key, boolean defaultValue) {
        return mPreferences.getBoolean(key, defaultValue);
    }

    public String getString(String key, String defaultValue) {
        return mPreferences.getString(key, defaultValue);
    }

    public int getInt(String key, int defaultValue) {
        return mPreferences.getInt(key, defaultValue);
    }

    public float getFloat(String key, float defaultValue) {
        return mPreferences.getFloat(key, defaultValue);
    }

    public long getLong(String key, long defaultValue) {
        return mPreferences.getLong(key, defaultValue);
    }

    /**
     * 读取逗号分隔的字符串列表。
     */
    public String[] getStringArray(String key, String[] defaultValue) {
        String value = getString(key, "");
        if (TextUtils.isEmpty(value)) return defaultValue;
        return value.split(",");
    }

    public String[] getStringArray(String key) {
        return getStringArray(key, new String[0]);
    }

    public boolean contains(String key) {
        return mPreferences.contains(key);
    }
}
