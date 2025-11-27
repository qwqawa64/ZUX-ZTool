package com.qimian233.ztool.hook.modules.launcher;

import android.content.Context;
import android.os.Build;

import com.qimian233.ztool.hook.base.BaseHookModule;

import java.util.ArrayList;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XC_MethodReplacement;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * ZUI Launcher后台管理优化Hook模块
 * 防止划掉后台卡片时杀死应用的后台服务
 * 智能适配Android 16+和Android 15-版本
 */
public class DisableForceStop extends BaseHookModule {

    @Override
    public String getModuleName() {
        return "disable_force_stop";
    }

    @Override
    public String[] getTargetPackages() {
        return new String[]{
                "com.zui.launcher"
        };
    }

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        String packageName = lpparam.packageName;

        // 获取当前Android SDK版本
        int sdkVersion = getSDKVersion();
        log("当前Android SDK版本: " + sdkVersion + ", 目标包名: " + packageName);

        // 根据Android版本选择Hook策略
        if (sdkVersion >= 36) { // 包括Android 16
            hookForAndroid16Plus(lpparam, packageName);
        } else {
            hookForAndroid15Minus(lpparam, packageName);
        }
    }

    /**
     * Android 16+版本的Hook策略
     * 针对ZUI Launcher桌面大改后的新架构
     */
    private void hookForAndroid16Plus(XC_LoadPackage.LoadPackageParam lpparam, String packageName) {
        try {
            if ("com.zui.launcher".equals(packageName)) {
                hookZuiLauncherAndroid16(lpparam);
            } else if ("com.android.launcher3".equals(packageName)) {
                hookBaseLauncherAndroid16(lpparam);
            }
            log("Android 16+ Hook策略已应用");
        } catch (Throwable t) {
            logError("Android 16+ Hook失败", t);
        }
    }

    /**
     * Android 15及以下版本的Hook策略
     * 针对传统Launcher架构
     */
    private void hookForAndroid15Minus(XC_LoadPackage.LoadPackageParam lpparam, String packageName) {
        try {
            if ("com.zui.launcher".equals(packageName) || "com.android.launcher3".equals(packageName)) {
                hookLegacyLauncher(lpparam);
            }
            log("Android 15- Hook策略已应用");
        } catch (Throwable t) {
            logError("Android 15- Hook失败", t);
        }
    }

    /**
     * Android 16+ ZUI Launcher专用Hook
     */
    private void hookZuiLauncherAndroid16(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            // Hook removeAppProcess方法 - 主要的进程杀死入口
            XposedHelpers.findAndHookMethod(
                    "com.zui.launcher.util.OverviewUtilities",
                    lpparam.classLoader,
                    "removeAppProcess",
                    Context.class, int.class, String.class, int.class,
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                            String packageName = (String) param.args[2]; // 注意：参数索引修正

                            // 记录原本要杀死的应用，但不执行杀死操作
                            log("Android 16: 阻止杀死应用: " + packageName + " (UID: " + param.args[3] + ")");

                            // 直接返回，不执行任何杀死操作
                            param.setResult(null);
                        }
                    }
            );

            // Hook c方法 - 强制杀死进程的辅助方法
            XposedHelpers.findAndHookMethod(
                    "com.zui.launcher.util.OverviewUtilities",
                    lpparam.classLoader,
                    "c",  // 这是混淆后的方法名，对应强制杀死逻辑
                    Context.class, String.class, int.class,
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                            String packageName = (String) param.args[1];
                            log("Android 16: 阻止强制杀死应用: " + packageName);
                            param.setResult(null);
                        }
                    }
            );

            // Hook removeAllRunningAppProcesses方法 - 批量清理入口
            XposedHelpers.findAndHookMethod(
                    "com.zui.launcher.util.OverviewUtilities",
                    lpparam.classLoader,
                    "removeAllRunningAppProcesses",
                    Context.class, ArrayList.class, boolean.class,
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                            ArrayList tasks = (ArrayList) param.args[1];
                            boolean forceKill = (Boolean) param.args[2];

                            // 记录批量清理操作但不执行
                            if (tasks != null) {
                                log("Android 16: 阻止批量清理 " + tasks.size() + " 个应用的后台进程");
                            }

                            param.setResult(null);
                        }
                    }
            );

            // Hook AsyncTask子类的doInBackground方法 - 异步清理逻辑
            Class<?> asyncTaskClass = XposedHelpers.findClassIfExists(
                    "com.zui.launcher.util.OverviewUtilities$a",
                    lpparam.classLoader
            );

            if (asyncTaskClass != null) {
                XposedHelpers.findAndHookMethod(
                        asyncTaskClass,
                        "doInBackground",
                        Void[].class,
                        new XC_MethodHook() {
                            @Override
                            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                                log("Android 16: 阻止异步后台清理任务执行");
                                param.setResult(null);
                            }
                        }
                );
            }

            // 尝试Hook Android 16可能新增的方法
            hookAdditionalAndroid16Methods(lpparam);

            log("Android 16+ ZUI Launcher后台保护Hook已成功应用");

        } catch (Throwable t) {
            logError("Android 16+ Hook ZUI Launcher失败", t);
        }
    }

    /**
     * Android 16+ 基础Launcher Hook
     */
    private void hookBaseLauncherAndroid16(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            // Android 16上基础Launcher可能的Hook点
            // 这里可以根据需要添加对com.android.launcher3的特定Hook

            log("Android 16+ 基础Launcher Hook已加载");

        } catch (Throwable t) {
            logError("Android 16+ Hook基础Launcher失败", t);
        }
    }

    /**
     * Android 15及以下版本的通用Hook策略
     */
    private void hookLegacyLauncher(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            log("开始Hook传统Launcher架构...");

            // Hook ActivityManagerWrapper类的方法
            var amwclass = XposedHelpers.findClassIfExists(
                    "com.android.systemui.shared.system.ActivityManagerWrapper",
                    lpparam.classLoader
            );

            if (amwclass != null) {
                log("找到ActivityManagerWrapper类，开始Hook...");

                XposedHelpers.findAndHookMethod(
                        amwclass,
                        "removeAllRunningAppProcesses",
                        Context.class,
                        ArrayList.class,
                        XC_MethodReplacement.returnConstant(null)
                );

                XposedHelpers.findAndHookMethod(
                        amwclass,
                        "removeAppProcess",
                        Context.class,
                        int.class,
                        String.class,
                        int.class,
                        XC_MethodReplacement.returnConstant(null)
                );

                log("ActivityManagerWrapper Hook完成 [OK]");
            } else {
                log("未找到ActivityManagerWrapper类，尝试其他Hook点...");
                // 可以添加备用的Hook点
            }

        } catch (Exception e) {
            logError("传统Launcher Hook失败", e);
        }
    }

    /**
     * Android 16可能新增的Hook点
     */
    private void hookAdditionalAndroid16Methods(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            // 尝试Hook Android 16可能新增的任务管理相关方法
            String[] potentialClasses = {
                    "com.zui.launcher.taskbar.TaskbarManager",
                    "com.zui.launcher.recents.RecentsModel",
                    "com.zui.launcher.recents.TaskStackListener"
            };

            for (String className : potentialClasses) {
                Class<?> targetClass = XposedHelpers.findClassIfExists(className, lpparam.classLoader);
                if (targetClass != null) {
                    log("找到Android 16新类: " + className);
                    // 可以根据需要添加具体的Hook逻辑
                }
            }

        } catch (Throwable t) {
            // 忽略错误，这些是可选的Hook点
            log("Android 16附加Hook点探测完成");
        }
    }

    /**
     * 获取当前Android SDK版本
     */
    private int getSDKVersion() {
        try {
            return Build.VERSION.SDK_INT;
        } catch (Throwable t) {
            logError("获取SDK版本失败，使用默认值", t);
            return Build.VERSION_CODES.BASE; // 返回最低版本
        }
    }

    /**
     * 可选：添加配置管理，允许用户选择哪些应用需要保护
     */
    private boolean shouldProtectApp(String packageName) {
        // 默认保护所有应用
        // 可以扩展为读取配置，让用户选择白名单
        return true;
    }
}
