package com.qimian233.ztool.config;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedHelpers;

public class ModuleConfig {
    private static final String PREFS_NAME = "xposed_module_config";
    private static final String MODULE_PACKAGE = "com.qimian233.ztool";

    @SuppressWarnings("unused")
    private SharedPreferences getPreferences() {
        SharedPreferences prefs = new XSharedPreferences(MODULE_PACKAGE, PREFS_NAME);
        if (prefs != null) {
//            XposedBridge.log("ModuleConfig: √ Successfully accessed com.qimian233.ztool preferences");
            return prefs;
        }
//        XposedBridge.log("ModuleConfig: Failed to access com.qimian233.ztool preferences");

        // 方法2：尝试直接通过模块上下文获取
        try {
            Context moduleContext = this.getModuleContext();
            if (moduleContext != null) {
                SharedPreferences directPrefs = moduleContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
                // 测试直接上下文的读写能力
                boolean testValue = !directPrefs.getBoolean("test_key_direct", false);
                boolean success = directPrefs.edit().putBoolean("test_key_direct", testValue).commit();

                if (success) {
                    return directPrefs;
                }
            }
        } catch (Throwable e) {
//            Log.e("ModuleConfig", "Failed to get module preferences via direct context", e);
        }
//
//        Log.e("ModuleConfig", "All methods failed to get preferences");
        return null;
    }

    private Context moduleContext = null;

    private synchronized Context getModuleContext() {
        if (moduleContext != null) {
            return moduleContext;
        }

        try {
            ClassLoader cl = ModuleConfig.class.getClassLoader();
            if (cl != null) {
                Class<?> activityThread = Class.forName("android.app.ActivityThread", false, cl);
                Object currentActivityThread = XposedHelpers.callStaticMethod(activityThread, "currentActivityThread");
                Context appContext = (Context) XposedHelpers.callMethod(currentActivityThread, "getApplication");

                if (appContext != null) {
                    moduleContext = appContext.createPackageContext(MODULE_PACKAGE,
                            Context.CONTEXT_INCLUDE_CODE | Context.CONTEXT_IGNORE_SECURITY);
//                    XposedBridge.log("ModuleConfig: Successfully initialized module context");
                    return moduleContext;
                }
            }
        } catch (Throwable e) {
//            XposedBridge.log("ModuleConfig: Failed to initialize module context: " + e.getMessage());
        }

//        XposedBridge.log("ModuleConfig: Could not initialize module context");
        return null;
    }

    public static boolean isModuleEnabled(String moduleName) {
        XSharedPreferences prefs = new XSharedPreferences(MODULE_PACKAGE,PREFS_NAME);
        prefs.reload();
        if (prefs != null) {
            boolean result = prefs.getBoolean(moduleName, false);
            // 防止一直吐日志
            if (moduleName.equals("Custom_StatusBarClock")) {
                return result;
            }
            return result;
        } else {
            return false;
        }
    }

    @SuppressWarnings("unused")
    public static void setModuleEnabled(String moduleName, boolean enabled) {
        XSharedPreferences prefs = new XSharedPreferences(MODULE_PACKAGE,PREFS_NAME);
        if (prefs != null) {
            boolean success = prefs.edit()
                    .putBoolean(moduleName, enabled)
                    .commit();
            if (!success) {
                dumpAllPreferences();
            }
        }
    }

    // 调试方法
    public static void dumpAllPreferences() {
        XSharedPreferences prefs = new XSharedPreferences(MODULE_PACKAGE,PREFS_NAME);
        if (prefs != null) {
            Log.d("ModuleConfig", "=== All Preferences ===");
            for (String key : prefs.getAll().keySet()) {
                Log.d("ModuleConfig", key + " = " + prefs.getAll().get(key));
            }
        }
    }
}
