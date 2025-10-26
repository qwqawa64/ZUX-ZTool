package com.qimian233.ztool.hook.modules.systemui;

import android.util.Log;

import com.qimian233.ztool.hook.base.BaseHookModule;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * 自定义状态栏时钟Hook模块
 * 修改SystemUI状态栏时钟显示格式，支持自定义时间格式
 */
public class CustomStatusBarClock extends BaseHookModule {

    private static final String PREFS_NAME = "StatusBar_Clock";
    private static final String MODULE_PACKAGE = "com.qimian233.ztool";
    private static final String SYSTEMUI_PACKAGE = "com.android.systemui";
    private static final String CLOCK_CLASS = "com.android.systemui.statusbar.policy.Clock";

    @Override
    public String getModuleName() {
        return "Custom_StatusBarClock";
    }

    @Override
    public String[] getTargetPackages() {
        return new String[]{SYSTEMUI_PACKAGE};
    }

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        String packageName = lpparam.packageName;

        if (SYSTEMUI_PACKAGE.equals(packageName)) {
            hookSystemUIClock(lpparam);
        }
    }

    private void hookSystemUIClock(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            // Hook Clock 类的 getSmallTime 方法
            XposedHelpers.findAndHookMethod(CLOCK_CLASS, lpparam.classLoader,
                    "getSmallTime", new XC_MethodHook() {

                        @Override
                        protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                            try {
                                // 检查模块是否启用
                                if (!isEnabled()) {
                                    return;
                                }

                                // 获取原始返回值
                                CharSequence originalText = (CharSequence) param.getResult();

                                // 自定义时间格式
                                String customTime = getCustomTimeFormat();

                                // 设置新的返回值
                                param.setResult(customTime);

                                log("Successfully customized status bar clock: " + customTime);

                            } catch (Exception e) {
                                logError("Failed to customize getSmallTime", e);
                            }
                        }
                    });

            // Hook updateClock 方法，确保内容描述也更新
            XposedHelpers.findAndHookMethod(CLOCK_CLASS, lpparam.classLoader,
                    "updateClock", new XC_MethodHook() {

                        @Override
                        protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                            try {
                                // 检查模块是否启用
                                if (!isEnabled()) {
                                    return;
                                }

                                Object clockInstance = param.thisObject;

                                // 获取自定义时间
                                String customTime = getCustomTimeFormat();

                                // 设置内容描述（无障碍功能使用）
                                XposedHelpers.callMethod(clockInstance, "setContentDescription", customTime);

                                log("Updated clock content description: " + customTime);

                            } catch (Exception e) {
                                logError("Failed to update clock content description", e);
                            }
                        }
                    });

            log("Successfully hooked SystemUI Clock methods");

        } catch (Throwable t) {
            logError("Failed to hook SystemUI Clock class", t);
        }
    }

    /**
     * 自定义时间格式方法
     * 在这里定义您想要的时间格式
     */
    private String getCustomTimeFormat() {
        try {
            return new SimpleDateFormat(getCustomClock("Custom_StatusBarClockFormat"), Locale.getDefault()).format(new Date());
        } catch (Exception e) {
            logError("Error in custom time formatting", e);
            // 出错时返回默认时间格式
            return new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date());
        }
    }

    public static String getCustomClock(String key) {
        XSharedPreferences prefs = new XSharedPreferences(MODULE_PACKAGE,PREFS_NAME);
        prefs.reload();
        if (prefs != null) {
            String result = prefs.getString(key, "HH:mm");
            XposedBridge.log(String.format("CustomStatusBarClock: Read %s = %s (prefs: %s)", key, result, prefs));
            Log.d("CustomStatusBarClock", String.format("Read %s: %s (prefs: %s)", key, result, prefs));
            return result;
        } else {
            XposedBridge.log("CustomStatusBarClock: Preferences is null, returning default HH:mm");
            Log.w("CustomStatusBarClock", "Preferences is null, returning default HH:mm");
            return "HH:mm";
        }
    }
}
