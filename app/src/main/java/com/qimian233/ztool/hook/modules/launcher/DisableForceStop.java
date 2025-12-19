package com.qimian233.ztool.hook.modules.launcher;

import android.content.Context;
import android.os.Build;

import com.qimian233.ztool.hook.base.BaseHookModule;
import com.qimian233.ztool.hook.base.PreferenceHelper;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XC_MethodReplacement;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * ZUI Launcher后台管理优化Hook模块
 * 防止划掉后台卡片时杀死应用的后台服务
 * 智能适配Android 16+和Android 15-版本
 * 支持白名单机制，只保护指定应用
 */
public class DisableForceStop extends BaseHookModule {

    // 白名单应用包名集合
    private String[] WHITE_LIST ;
    private final PreferenceHelper mPrefHelper = PreferenceHelper.getInstance();
    private static final String KEY_FORCE_STOP_WHITELIST_ENABLE = "ForceStopWhiteListEnable";
    private static final String KEY_FORCE_STOP_WHITELIST = "ForceStopWhiteList";

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
        WHITE_LIST = getWhiteListPackages();
        log("当前Android SDK版本: " + sdkVersion + ", 目标包名: " + packageName);
        log("白名单保护机制已启用，保护应用数量: " + (WHITE_LIST.length ));

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
                hookBaseLauncherAndroid16();
            }
            log("Android 16+ Hook策略已应用，白名单保护已启用");
        } catch (Throwable t) {
            logError("Android 16+ Hook失败", t);
        }
    }

    // 检查是否启用白名单保护
    private boolean isWhiteListEnabled() {
        return mPrefHelper.getBoolean(KEY_FORCE_STOP_WHITELIST_ENABLE, false);
    }

    // 获取白名单中的应用包名
    private String[] getWhiteListPackages() {
        return mPrefHelper.getStringArray(KEY_FORCE_STOP_WHITELIST);
    }

    // 检查指定包名是否在白名单中
    private boolean isProtectedPackage(String packageName) {
        if (!isWhiteListEnabled()) return true; // 白名单未启用，保护所有应用
        for (String pkg : WHITE_LIST) {
            if (pkg.equals(packageName)) {
                return true;
            }
        }
        return false;
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
            log("Android 15- Hook策略已应用，白名单保护已启用");
        } catch (Throwable t) {
            logError("Android 15- Hook失败", t);
        }
    }

    /**
     * Android 16+ ZUI Launcher专用Hook（带白名单机制）
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
                        protected void beforeHookedMethod(MethodHookParam param) {
                            String packageName = (String) param.args[2]; // 注意：参数索引修正
                            int uid = (int) param.args[3];

                            // 检查是否在白名单中
                            if (isProtectedPackage(packageName)) {
                                // 在白名单中，阻止杀死操作
                                log("Android 16: 阻止杀死白名单应用: " + packageName + " (UID: " + uid + ")");
                                param.setResult(null);
                                return;
                            }

                            // 不在白名单中，允许执行原方法
                            log("Android 16: 允许杀死非白名单应用: " + packageName);
                            // 不设置result，让原方法继续执行
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
                        protected void beforeHookedMethod(MethodHookParam param) {
                            String packageName = (String) param.args[1];
                            int uid = (int) param.args[2];

                            // 检查是否在白名单中
                            if (isProtectedPackage(packageName)) {
                                // 在白名单中，阻止强制杀死
                                log("Android 16: 阻止强制杀死白名单应用: " + packageName + " (UID: " + uid + ")");
                                param.setResult(null);
                                return;
                            }

                            // 不在白名单中，允许执行原方法
                            log("Android 16: 允许强制杀死非白名单应用: " + packageName);
                            // 不设置result，让原方法继续执行
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
                        protected void beforeHookedMethod(MethodHookParam param) {
                            ArrayList<?> tasks = (ArrayList<?>) param.args[1];
                            boolean force = (boolean) param.args[2];

                            if (tasks != null) {
                                int totalTasks = tasks.size();
                                int protectedCount = 0;

                                // 记录白名单应用
                                for (Object task : tasks) {
                                    try {
                                        // 尝试获取任务对应的包名
                                        String packageName = getPackageNameFromTask(task);
                                        if (packageName != null && isProtectedPackage(packageName)) {
                                            protectedCount++;
                                            log("Android 16: 批量清理中检测到白名单应用: " + packageName);
                                        }
                                    } catch (Exception e) {
                                        // 如果无法获取包名，跳过
                                    }
                                }

                                if (protectedCount > 0) {
                                    // 如果包含白名单应用，阻止整个批量清理操作
                                    log("Android 16: 批量清理包含 " + protectedCount + " 个白名单应用，阻止整个清理操作");
                                    param.setResult(null);
                                    return;
                                }

                                // 不包含白名单应用，允许执行批量清理
                                log("Android 16: 批量清理 " + totalTasks + " 个应用，未包含白名单应用，允许清理");
                            }

                            // 不设置result，让原方法继续执行
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
                            protected void beforeHookedMethod(MethodHookParam param) {
                                try {
                                    // 尝试获取任务列表
                                    Object thisObject = param.thisObject;
                                    Object tasks = XposedHelpers.getObjectField(thisObject, "tasks");

                                    if (tasks instanceof ArrayList) {
                                        ArrayList<?> taskList = (ArrayList<?>) tasks;
                                        for (Object task : taskList) {
                                            try {
                                                String packageName = getPackageNameFromTask(task);
                                                if (packageName != null && isProtectedPackage(packageName)) {
                                                    log("Android 16: 异步任务中包含白名单应用 " + packageName + "，阻止执行");
                                                    param.setResult(null);
                                                    return;
                                                }
                                            } catch (Exception e) {
                                                // 跳过无法识别的任务
                                            }
                                        }
                                    }
                                } catch (Exception e) {
                                    // 如果无法检查，默认阻止
                                    log("Android 16: 无法检查异步任务内容，阻止执行以保安全");
                                    param.setResult(null);
                                    return;
                                }

                                // 不包含白名单应用，允许执行
                                log("Android 16: 异步后台清理任务不包含白名单应用，允许执行");
                                // 不设置result，让原方法继续执行
                            }
                        }
                );
            }

            // 尝试Hook Android 16可能新增的方法
            hookAdditionalAndroid16Methods(lpparam);

            log("Android 16+ ZUI Launcher后台保护Hook已成功应用，白名单机制生效");

        } catch (Throwable t) {
            logError("Android 16+ Hook ZUI Launcher失败", t);
        }
    }

    /**
     * Android 16+ 基础Launcher Hook
     */
    private void hookBaseLauncherAndroid16() {
        try {
            // Android 16上基础Launcher可能的Hook点
            // 这里可以根据需要添加对com.android.launcher3的特定Hook

        } catch (Throwable t) {
            logError("Android 16+ Hook基础Launcher失败", t);
        }
    }

    /**
     * Android 15及以下版本的通用Hook策略（带白名单机制）
     */
    private void hookLegacyLauncher(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            log("开始Hook传统Launcher架构（带白名单机制）...");

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
                        new XC_MethodHook() {
                            @Override
                            protected void beforeHookedMethod(MethodHookParam param) {
                                ArrayList<?> tasks = (ArrayList<?>) param.args[1];

                                if (tasks != null) {
                                    int protectedCount = 0;
                                    for (Object task : tasks) {
                                        try {
                                            String packageName = getPackageNameFromTask(task);
                                            if (packageName != null && isProtectedPackage(packageName)) {
                                                protectedCount++;
                                            }
                                        } catch (Exception e) {
                                            // 跳过无法识别的任务
                                        }
                                    }

                                    if (protectedCount > 0) {
                                        log("传统架构: 批量清理包含 " + protectedCount + " 个白名单应用，阻止清理");
                                        param.setResult(null);
                                        return;
                                    }
                                }

                                // 不设置result，让原方法继续执行
                            }
                        }
                );

                XposedHelpers.findAndHookMethod(
                        amwclass,
                        "removeAppProcess",
                        Context.class,
                        int.class,
                        String.class,
                        int.class,
                        new XC_MethodHook() {
                            @Override
                            protected void beforeHookedMethod(MethodHookParam param) {
                                String packageName = (String) param.args[2];

                                if (isProtectedPackage(packageName)) {
                                    log("传统架构: 阻止杀死白名单应用: " + packageName);
                                    param.setResult(null);
                                    return;
                                }

                                // 不设置result，让原方法继续执行
                            }
                        }
                );

                log("ActivityManagerWrapper Hook完成 [OK]，白名单机制生效");
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
     * 从任务对象中提取包名
     * @param task 任务对象
     * @return 包名，如果无法提取则返回null
     */
    private String getPackageNameFromTask(Object task) {
        if (task == null) {
            return null;
        }

        try {
            // 方法1：尝试通过ComponentName获取包名
            Object componentName = XposedHelpers.getObjectField(task, "componentName");
            if (componentName != null) {
                Object packageNameObj = XposedHelpers.callMethod(componentName, "getPackageName");
                if (packageNameObj instanceof String) {
                    return (String) packageNameObj;
                }
            }

            // 方法2：尝试直接获取packageName字段
            Object packageNameField = XposedHelpers.getObjectField(task, "packageName");
            if (packageNameField instanceof String) {
                return (String) packageNameField;
            }

            // 方法3：尝试通过BaseActivityInfo获取包名
            Object baseActivityInfo = XposedHelpers.getObjectField(task, "baseActivityInfo");
            if (baseActivityInfo != null) {
                Object packageNameObj = XposedHelpers.getObjectField(baseActivityInfo, "packageName");
                if (packageNameObj instanceof String) {
                    return (String) packageNameObj;
                }
            }

            // 方法4：尝试通过taskDescription获取包名
            Object taskDescription = XposedHelpers.getObjectField(task, "taskDescription");
            if (taskDescription != null) {
                Object packageNameObj = XposedHelpers.callMethod(taskDescription, "getPackageName");
                if (packageNameObj instanceof String) {
                    return (String) packageNameObj;
                }
            }

        } catch (Exception e) {
            // 所有方法都失败，返回null
        }

        return null;
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
}
