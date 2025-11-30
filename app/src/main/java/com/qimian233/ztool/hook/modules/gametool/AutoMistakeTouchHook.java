package com.qimian233.ztool.hook.modules.gametool;

import android.app.ActivityManager;
import android.app.ActivityManager.RunningAppProcessInfo;
import android.content.Context;
import android.text.TextUtils;

import com.qimian233.ztool.hook.base.BaseHookModule;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XC_MethodReplacement;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

import java.util.Arrays;
import java.util.List;

/**
 * 自动开启游戏防误触功能Hook模块
 * 为特定游戏自动开启ZUI游戏助手的防误触功能
 */
public class AutoMistakeTouchHook extends BaseHookModule {

    private static final String TARGET_PACKAGE = "com.zui.game.service";
    private static final String SETTINGS_UTIL_CLASS = "com.zui.util.SettingsValueUtilKt";
    private static final String PREVENT_MISOPERATION_KEY = "key_game_assistant_prevent_misoperation";
    private static final String PREFS_NAME = "xposed_module_config";
    private static final String MODULE_PACKAGE = "com.qimian233.ztool";

    // 白名单游戏包名列表
    private List<String> TARGET_GAME_PACKAGES;

    private boolean MistakeTouchWhiteListEnabled() {
        XSharedPreferences prefs = new XSharedPreferences(MODULE_PACKAGE, PREFS_NAME);
        prefs.reload();
        return prefs.getBoolean("MistakeTouchWhiteList", false);
    }

    @Override
    public String getModuleName() {
        return "auto_mistake_touch";
    }

    @Override
    public String[] getTargetPackages() {
        return new String[]{TARGET_PACKAGE};
    }

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        String packageName = lpparam.packageName;

        if (TARGET_PACKAGE.equals(packageName)) {
            hookGameService(lpparam);
        }
    }

    private void hookGameService(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            // 方案1: Hook GameHelperViewController 的初始化
            hookGameHelperViewController(lpparam);

            // 方案2: 直接 Hook 设置工具类的方法
            hookSettingsUtil(lpparam);

            // 方案3: Hook 应用启动时的关键方法
            hookApplicationStart(lpparam);

            // 方案4: 增强版本 - Hook前台应用检测
            hookTargetGameDetection(lpparam);

            log("AutoMistakeTouch Hook initialized successfully");

        } catch (Throwable e) {
            logError("Hook GameService failed", e);
        }
    }

    private void hookGameHelperViewController(final XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            String className = "com.zui.game.service.ui.GameHelperViewController";

            // Hook 构造函数
            XposedHelpers.findAndHookConstructor(className, lpparam.classLoader,
                    "org.kodein.di.DI",
                    "kotlinx.coroutines.CoroutineScope",
                    "android.content.Context",
                    "kotlinx.coroutines.CoroutineDispatcher",
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                            checkAndEnableMistakeTouch(param.thisObject, lpparam);
                        }
                    });

            // Hook setData 方法
            XposedHelpers.findAndHookMethod(className, lpparam.classLoader,
                    "setData",
                    "com.zui.game.service.ui.view.HelperMainViewAttachState",
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                            checkAndEnableMistakeTouch(param.thisObject, lpparam);
                        }
                    });

            // Hook setPkgName 方法（游戏启动时调用）
            XposedHelpers.findAndHookMethod(className, lpparam.classLoader,
                    "setPkgName",
                    String.class,
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                            String pkgName = (String) param.args[0];
                            if (pkgName != null && !pkgName.isEmpty()) {
                                // 直接检查传入的包名
                                if (isTargetGame(pkgName)) {
                                    enableMistakeTouch(param.thisObject, lpparam);
                                    log("Target game detected by pkgName: " + pkgName);
                                }
                            } else {
                                // 如果没有传入包名，检查当前前台应用
                                checkAndEnableMistakeTouch(param.thisObject, lpparam);
                            }
                        }
                    });

            log("Successfully hooked GameHelperViewController");

        } catch (Throwable e) {
            logError("Hook GameHelperViewController failed", e);
        }
    }

    private void hookSettingsUtil(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            // Hook getPreventMisoperation 方法，在特定游戏运行时返回开启状态
            XposedHelpers.findAndHookMethod(SETTINGS_UTIL_CLASS, lpparam.classLoader,
                    "getPreventMisoperation",
                    Context.class,
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                            Context context = (Context) param.args[0];
                            if (isTargetGameRunning(context)) {
                                // 强制返回开启状态 (1)
                                param.setResult(1);
                                log("Force enabled mistake touch for target game via SettingsUtil");
                            }
                        }
                    });

            log("Successfully hooked SettingsUtil");

        } catch (Throwable e) {
            logError("Hook SettingsUtil failed", e);
        }
    }

    private void hookApplicationStart(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            // Hook Application 的 onCreate 方法
            XposedHelpers.findAndHookMethod("android.app.Application", lpparam.classLoader,
                    "onCreate",
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                            Context context = (Context) param.thisObject;
                            if (isTargetGameRunning(context)) {
                                setMistakeTouchEnabled(context, true);
                                log("Enabled mistake touch on app start for target game");
                            }
                        }
                    });

            log("Successfully hooked Application onCreate");

        } catch (Throwable e) {
            logError("Hook Application failed", e);
        }
    }

    private void hookTargetGameDetection(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            // Hook 获取前台应用包名的方法
            String hideSystemApiClass = "com.zui.util.HideSystemApiKt";
            XposedHelpers.findAndHookMethod(hideSystemApiClass, lpparam.classLoader,
                    "getForegroundGameAppPackageName",
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                            String currentPackage = (String) param.getResult();
                            if (currentPackage != null && isTargetGame(currentPackage)) {
                                log("Target game detected as foreground: " + currentPackage);
                                // 可以在这里触发防误触设置
                            }
                        }
                    });

            log("Successfully hooked target game detection");

        } catch (Throwable e) {
            log("Hook target game detection failed: " + e.getMessage());
        }
    }

    private void checkAndEnableMistakeTouch(Object gameHelper, XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            // 获取 Context
            Object context = XposedHelpers.callMethod(gameHelper, "getContext");
            if (context == null) {
                context = XposedHelpers.callMethod(gameHelper, "getNotNullContext");
            }

            if (context instanceof Context) {
                if (isTargetGameRunning((Context) context)) {
                    enableMistakeTouch(gameHelper, lpparam);
                }
            }

        } catch (Throwable e) {
            logError("Check and enable mistake touch failed", e);
        }
    }

    private void enableMistakeTouch(Object gameHelper, XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            // 获取 Context
            Object context = XposedHelpers.callMethod(gameHelper, "getContext");
            if (context == null) {
                context = XposedHelpers.callMethod(gameHelper, "getNotNullContext");
            }

            if (context instanceof Context) {
                setMistakeTouchEnabled((Context) context, true);

                // 同时调用 changeMistouchStatus 方法确保UI状态同步
                try {
                    XposedHelpers.callMethod(gameHelper, "changeMistouchStatus", true);
                    log("Called changeMistouchStatus for target game");
                } catch (Throwable e) {
                    log("changeMistouchStatus not available: " + e.getMessage());
                }
            }

        } catch (Throwable e) {
            logError("Enable mistake touch failed", e);
        }
    }

    private void setMistakeTouchEnabled(Context context, boolean enabled) {
        try {
            // 直接设置系统设置
            android.provider.Settings.Global.putInt(context.getContentResolver(),
                    PREVENT_MISOPERATION_KEY, enabled ? 1 : 0);

            log("Mistake touch set to: " + (enabled ? "enabled" : "disabled") + " for target game");

        } catch (Throwable e) {
            logError("Set mistake touch failed", e);
        }
    }

    // 检查是否为特定目标游戏
    private boolean isTargetGame(String packageName) {
        if (MistakeTouchWhiteListEnabled()) {
            // 读取白名单
            XSharedPreferences prefs = new XSharedPreferences(MODULE_PACKAGE, PREFS_NAME);
            prefs.reload();
            String whiteList = prefs.getString("MistakeTouchWhiteListGame", "");
            TARGET_GAME_PACKAGES = Arrays.asList(whiteList.split(","));
            for (String targetPackage : TARGET_GAME_PACKAGES) {
                if (targetPackage.equals(packageName)) {
                    return true;
                }
            }
            return false;
        } else {
            return true;
        }
    }

    // 检查是否有特定目标游戏正在运行
    private boolean isTargetGameRunning(Context context) {
        try {
            ActivityManager activityManager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
            if (activityManager != null) {
                List<RunningAppProcessInfo> runningProcesses = activityManager.getRunningAppProcesses();
                if (runningProcesses != null) {
                    for (RunningAppProcessInfo processInfo : runningProcesses) {
                        if (processInfo.importance == RunningAppProcessInfo.IMPORTANCE_FOREGROUND) {
                            String[] pkgList = processInfo.pkgList;
                            if (pkgList != null) {
                                for (String pkg : pkgList) {
                                    if (isTargetGame(pkg)) {
                                        log("Target game running: " + pkg);
                                        return true;
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } catch (Throwable e) {
            logError("Check running games failed", e);
        }
        return false;
    }
}
