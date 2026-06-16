package com.qimian233.ztool.config;

import de.robv.android.xposed.XSharedPreferences;

public final class ModuleConfig {
    private static final String PREFS_NAME = "xposed_module_config";
    private static final String MODULE_PACKAGE = "com.qimian233.ztool";
    private static final Object PREFS_LOCK = new Object();
    private static volatile XSharedPreferences modulePreferences;

    private ModuleConfig() {
    }

    private static XSharedPreferences getPreferences() {
        XSharedPreferences prefs = modulePreferences;
        if (prefs != null) {
            return prefs;
        }

        synchronized (PREFS_LOCK) {
            if (modulePreferences == null) {
                modulePreferences = new XSharedPreferences(MODULE_PACKAGE, PREFS_NAME);
            }
            return modulePreferences;
        }
    }

    private static boolean reloadPreferences(XSharedPreferences prefs) {
        try {
            prefs.reload();
            return true;
        } catch (Throwable ignored) {
            synchronized (PREFS_LOCK) {
                if (modulePreferences == prefs) {
                    modulePreferences = null;
                }
            }
            return false;
        }
    }

    public static boolean isModuleEnabled(String moduleName) {
        if ("hook_test".equals(moduleName) || "test_hook".equals(moduleName)) return true;
        if (moduleName == null) return false;

        return getBoolean(moduleName, false);
    }

    public static boolean isDetailedLoggingEnabled() {
        return getBoolean("isDetailedLogging", false);
    }

    private static boolean getBoolean(String key, boolean defaultValue) {
        XSharedPreferences prefs = getPreferences();
        if (!reloadPreferences(prefs)) {
            prefs = getPreferences();
            if (!reloadPreferences(prefs)) {
                return defaultValue;
            }
        }

        try {
            return prefs.getBoolean(key, defaultValue);
        } catch (Throwable ignored) {
            return defaultValue;
        }
    }
}
