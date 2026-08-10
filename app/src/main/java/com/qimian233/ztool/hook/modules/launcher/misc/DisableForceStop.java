package com.qimian233.ztool.hook.modules.launcher.misc;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Build;

import com.qimian233.ztool.data.keys.ScopeKeys;
import com.qimian233.ztool.data.keys.PreferenceKeys;
import com.qimian233.ztool.dexindex.base.DexIndexConstants;
import com.qimian233.ztool.hook.base.AppHookModule;
import com.qimian233.ztool.hook.base.DexIndexStore;

import io.github.libxposed.api.XposedModuleInterface;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;

/**
 * ZUI Launcher后台管理优化Hook模块
 * 防止划掉后台卡片时杀死应用的后台服务
 * 智能适配Android 16+和Android 15-版本
 * 支持白名单机制，只保护指定应用
 */
public class DisableForceStop extends AppHookModule {

    // 白名单应用包名集合
    private String[] WHITE_LIST;
    public DisableForceStop() {}

    @Override
    public String getModuleName() {
        return "disable_force_stop";
    }

    @Override
    public String[] getTargetPackages() {
        return new String[]{
                ScopeKeys.LAUNCHER.packageName
        };
    }

    @Override
    public void handleLoadPackage(XposedModuleInterface.PackageLoadedParam param) throws Throwable {
        ClassLoader classLoader = param.getDefaultClassLoader();
        String packageName = param.getPackageName();
        // 获取当前Android SDK版本
        int sdkVersion = getSDKVersion();
        WHITE_LIST = getWhiteListPackages();
        logger.trace("Current Android SDK: "
                + sdkVersion
                + ", target package name: "
                + packageName);
        logger.trace("White list enabled, app in whitelist: " + (WHITE_LIST.length));

        // 根据Android版本选择Hook策略
        if (sdkVersion >= 36) { // 包括Android 16
            hookForAndroid16Plus(classLoader, packageName);
        } else {
            hookForAndroid15Minus(classLoader, packageName);
        }
    }

    /**
     * Android 16+版本的Hook策略
     * 针对ZUI Launcher桌面大改后的新架构
     */
    private void hookForAndroid16Plus(ClassLoader classLoader, String packageName) {
        try {
            if (ScopeKeys.LAUNCHER.packageName.equals(packageName)) {
                hookZuiLauncherAndroid16(classLoader);
            } else if ("com.android.launcher3".equals(packageName)) {
                hookBaseLauncherAndroid16();
            }
            logger.info("Android 16+ Hook applied, whitelist protection enabled");
        } catch (Throwable t) {
            logger.error("Android 16+ Hook failed!", t);
        }
    }

    // 检查是否启用白名单保护
    private boolean isWhiteListEnabled() {
        try {
            return getRemotePreferences().getBoolean(PreferenceKeys.FORCE_STOP_WHITE_LIST_ENABLE.name, false);
        } catch (Throwable t) {
            return false;
        }
    }

    // 获取白名单中的应用包名
    private String[] getWhiteListPackages() {
        String value;
        try {
            value = getRemotePreferences().getString(PreferenceKeys.FORCE_STOP_WHITE_LIST.name, "");
        } catch (Throwable t) {
            value = "";
        }
        if (value.isEmpty()) return new String[0];
        return value.split(",");
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
    private void hookForAndroid15Minus(ClassLoader classLoader, String packageName) {
        try {
            if (ScopeKeys.LAUNCHER.packageName.equals(packageName) || "com.android.launcher3".equals(packageName)) {
                hookLegacyLauncher(classLoader);
            }
            logger.info("Android 15- Hook applied, whitelist protection enabled");
        } catch (Throwable t) {
            logger.error("Android 15- Hook failed!", t);
        }
    }

    /**
     * Android 16+ ZUI Launcher专用Hook（带白名单机制）
     */
    private void hookZuiLauncherAndroid16(ClassLoader classLoader) {
        try {
            Class<?> overviewUtilitiesClass = classLoader.loadClass("com.zui.launcher.util.OverviewUtilities");

            // Hook removeAppProcess方法 - 主要的进程杀死入口
            Method removeAppProcessMethod = overviewUtilitiesClass.getDeclaredMethod(
                    "removeAppProcess", Context.class, int.class, String.class, int.class);
            hookWithId(removeAppProcessMethod, "remove_app_process_1", chain -> {
                String pkgName = (String) chain.getArg(2); // 注意：参数索引修正
                int uid = (int) chain.getArg(3);

                // 检查是否在白名单中
                if (isProtectedPackage(pkgName)) {
                    // 在白名单中，阻止杀死操作
                    logger.trace("Android 16: Avoid killing app in whitelist: "
                            + pkgName
                            + " (UID: " + uid + ")");
                    return null;
                }

                // 不在白名单中，允许执行原方法
                logger.trace("Android 16: Allow killing app: " + pkgName);
                return chain.proceed();
            });

            // Hook c方法 - 强制杀死进程的辅助方法（DEXKit 动态查找）
            String cMethodName = findCMethodName();
            Method cMethod = overviewUtilitiesClass.getDeclaredMethod(
                    cMethodName, Context.class, String.class, int.class);
            hookWithId(cMethod, "hook_165", chain -> {
                String pkgName = (String) chain.getArg(1);
                int uid = (int) chain.getArg(2);

                // 检查是否在白名单中
                if (isProtectedPackage(pkgName)) {
                    // 在白名单中，阻止强制杀死
                    logger.trace("Android 16: Blocked forced killing app in whitelist: "
                            + pkgName
                            + " (UID: " + uid + ")");
                    return null;
                }

                // 不在白名单中，允许执行原方法
                logger.trace("Android 16: Allow forced killing app: " + pkgName);
                return chain.proceed();
            });

            // Hook removeAllRunningAppProcesses方法 - 批量清理入口
            Method removeAllMethod = overviewUtilitiesClass.getDeclaredMethod(
                    "removeAllRunningAppProcesses", Context.class, ArrayList.class, boolean.class);
            hookWithId(removeAllMethod, "remove_all_1", chain -> {
                ArrayList<?> tasks = (ArrayList<?>) chain.getArg(1);

                if (tasks != null) {
                    int totalTasks = tasks.size();
                    int protectedCount = 0;

                    // 记录白名单应用
                    for (Object task : tasks) {
                        try {
                            // 尝试获取任务对应的包名
                            String pkgName = getPackageNameFromTask(task);
                            if (pkgName != null && isProtectedPackage(pkgName)) {
                                protectedCount++;
                                logger.trace("Android 16: Whitelist APP detected when performing batch kill: "
                                        + pkgName);
                            }
                        } catch (Exception e) {
                            // 如果无法获取包名，跳过
                        }
                    }

                    if (protectedCount > 0) {
                        // 如果包含白名单应用，阻止整个批量清理操作
                        logger.trace("Android 16: " + protectedCount
                                + "included in batch kill list, blocking kill operation");
                        return null;
                    }

                    // 不包含白名单应用，允许执行批量清理
                    logger.trace("Android 16: " + totalTasks + " APP(s) are allowed to be killed.");
                }

                return chain.proceed();
            });

            // Hook AsyncTask子类的doInBackground方法 - 异步清理逻辑
            Class<?> asyncTaskClass = findInnerClass(classLoader);

            if (asyncTaskClass != null) {
                Method doInBackgroundMethod = asyncTaskClass.getDeclaredMethod("doInBackground", Void[].class);
                hookWithId(doInBackgroundMethod, "do_in_background", chain -> {
                    try {
                        // 尝试获取任务列表
                        Object thisObject = chain.getThisObject();
                        Field tasksField = thisObject.getClass().getDeclaredField("tasks");
                        tasksField.setAccessible(true);
                        Object tasks = tasksField.get(thisObject);

                        if (tasks instanceof ArrayList) {
                            ArrayList<?> taskList = (ArrayList<?>) tasks;
                            for (Object task : taskList) {
                                try {
                                    String pkgName = getPackageNameFromTask(task);
                                    if (pkgName != null && isProtectedPackage(pkgName)) {
                                        logger.trace("Android 16: Whitelist app detected in async task, count: "
                                                + pkgName + ", blocking async task");
                                        return null;
                                    }
                                } catch (Exception e) {
                                    // 跳过无法识别的任务
                                }
                            }
                        }
                    } catch (Exception e) {
                        // 如果无法检查，默认阻止
                        logger.warn("Android 16: Unable to check async task, blocking it by default");
                        return null;
                    }

                    // 不包含白名单应用，允许执行
                    logger.trace("Android 16: Allowed to perform async kill");
                    return chain.proceed();
                });
            }

            // 尝试Hook Android 16可能新增的方法
            hookAdditionalAndroid16Methods(classLoader);

            logger.info("Hook for Android 16+ ZUI Launcher successfully applied.");

        } catch (Throwable t) {
            logger.error("Android 16+: Failed to hook ZUI Launcher", t);
        }
    }

    /**
     * Android 16+ 基础Launcher Hook
     */
    private void hookBaseLauncherAndroid16() {
        try {
            // Android 16上基础Launcher可能的Hook点
            // 这里可以根据需要添加对com.android.launcher3的特定Hook
            logger.warn("Android 16 logic not implemented yet!");
        } catch (Throwable t) {
            logger.error("Android 16+: failed to hook basic Launcher", t);
        }
    }

    /**
     * Android 15及以下版本的通用Hook策略（带白名单机制）
     */
    @SuppressLint("PrivateApi")
    private void hookLegacyLauncher(ClassLoader classLoader) {
        try {
            logger.info("Start hooking legacy Launcher with whitelist enabled.");

            // Hook ActivityManagerWrapper类的方法
            Class<?> amwclass;
            try {
                amwclass = classLoader.loadClass("com.android.systemui.shared.system.ActivityManagerWrapper");
            } catch (ClassNotFoundException e) {
                amwclass = null;
            }

            if (amwclass != null) {
                logger.info("找到ActivityManagerWrapper类，开始Hook...");

                Method removeAllMethod = amwclass.getDeclaredMethod(
                        "removeAllRunningAppProcesses", Context.class, ArrayList.class);
                hookWithId(removeAllMethod, "remove_all_2", chain -> {
                    ArrayList<?> tasks = (ArrayList<?>) chain.getArg(1);

                    if (tasks != null) {
                        int protectedCount = 0;
                        for (Object task : tasks) {
                            try {
                                String pkgName = getPackageNameFromTask(task);
                                if (pkgName != null && isProtectedPackage(pkgName)) {
                                    protectedCount++;
                                }
                            } catch (Exception e) {
                                // 跳过无法识别的任务
                            }
                        }

                        if (protectedCount > 0) {
                            logger.trace("传统架构: 批量清理包含 " + protectedCount + " 个白名单应用，阻止清理");
                            return null;
                        }
                    }

                    return chain.proceed();
                });

                Method removeAppProcessMethod = amwclass.getDeclaredMethod(
                        "removeAppProcess", Context.class, int.class, String.class, int.class);
                hookWithId(removeAppProcessMethod, "remove_app_process_2", chain -> {
                    String pkgName = (String) chain.getArg(2);

                    if (isProtectedPackage(pkgName)) {
                        logger.trace("传统架构: 阻止杀死白名单应用: " + pkgName);
                        return null;
                    }

                    return chain.proceed();
                });

                logger.info("ActivityManagerWrapper Hook完成 [OK]，白名单机制生效");
            } else {
                logger.warn("未找到ActivityManagerWrapper类，尝试其他Hook点...");
                // 可以添加备用的Hook点
            }

        } catch (Exception e) {
            logger.error("Failed to hook legacy launcher", e);
        }
    }

    /**
     * Android 16可能新增的Hook点
     */
    private void hookAdditionalAndroid16Methods(ClassLoader classLoader) {
        try {
            // 尝试Hook Android 16可能新增的任务管理相关方法
            String[] potentialClasses = {
                    "com.zui.launcher.taskbar.TaskbarManager",
                    "com.zui.launcher.recents.RecentsModel",
                    "com.zui.launcher.recents.TaskStackListener"
            };

            for (String className : potentialClasses) {
                Class<?> targetClass;
                try {
                    targetClass = classLoader.loadClass(className);
                } catch (ClassNotFoundException e) {
                    targetClass = null;
                }
                if (targetClass != null) {
                    logger.debug("Android 16 new class: " + className);
                    // 可以根据需要添加具体的Hook逻辑
                }
            }

        } catch (Throwable t) {
            // 忽略错误，这些是可选的Hook点
            logger.info("Android 16 extra hook points detection completed.");
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
            Field componentNameField = task.getClass().getDeclaredField("componentName");
            componentNameField.setAccessible(true);
            Object componentName = componentNameField.get(task);
            if (componentName != null) {
                Method getPackageNameMethod = componentName.getClass().getMethod("getPackageName");
                Object packageNameObj = getPackageNameMethod.invoke(componentName);
                if (packageNameObj instanceof String) {
                    return (String) packageNameObj;
                }
            }

            // 方法2：尝试直接获取packageName字段
            try {
                Field packageNameField = task.getClass().getDeclaredField("packageName");
                packageNameField.setAccessible(true);
                Object packageNameFieldVal = packageNameField.get(task);
                if (packageNameFieldVal instanceof String) {
                    return (String) packageNameFieldVal;
                }
            } catch (NoSuchFieldException e) {
                // 字段可能不存在，继续尝试其他方法
            }

            // 方法3：尝试通过BaseActivityInfo获取包名
            try {
                Field baseActivityInfoField = task.getClass().getDeclaredField("baseActivityInfo");
                baseActivityInfoField.setAccessible(true);
                Object baseActivityInfo = baseActivityInfoField.get(task);
                if (baseActivityInfo != null) {
                    Field packageNameField = baseActivityInfo.getClass().getDeclaredField("packageName");
                    packageNameField.setAccessible(true);
                    Object packageNameObj = packageNameField.get(baseActivityInfo);
                    if (packageNameObj instanceof String) {
                        return (String) packageNameObj;
                    }
                }
            } catch (NoSuchFieldException e) {
                // 字段可能不存在
            }

            // 方法4：尝试通过taskDescription获取包名
            try {
                Field taskDescriptionField = task.getClass().getDeclaredField("taskDescription");
                taskDescriptionField.setAccessible(true);
                Object taskDescription = taskDescriptionField.get(task);
                if (taskDescription != null) {
                    Method getPackageNameMethod = taskDescription.getClass().getMethod("getPackageName");
                    Object packageNameObj = getPackageNameMethod.invoke(taskDescription);
                    if (packageNameObj instanceof String) {
                        return (String) packageNameObj;
                    }
                }
            } catch (NoSuchFieldException e) {
                // 字段可能不存在
            }

        } catch (Exception e) {
            // 所有方法都失败，返回null
        }

        return null;
    }

    /**
     * 从离线索引读取 OverviewUtilities 中签名 (Context, String, int)→void 的混淆方法名。
     * 索引缺失/失败时回退硬编码 "c"。
     */
    private String findCMethodName() {
        String name = DexIndexStore.INSTANCE.string(
                xposed,
                ScopeKeys.LAUNCHER.packageName,
                DexIndexConstants.ModuleKeys.DISABLE_FORCE_STOP,
                DexIndexConstants.Keys.FORCE_STOP_METHOD);
        if (name != null) {
            logger.info("Loaded force-stop method from dex index: " + name);
            return name;
        }
        return "c"; // 回退硬编码
    }

    /**
     * 通过反射查找内部类（处理混淆后的内部类名）。
     * 遍历可能的内部类名（$1-$5, $a-$e）直到找到有 doInBackground 方法的类。
     */
    private Class<?> findInnerClass(ClassLoader classLoader) {
        // 先尝试常见混淆模式: $a, $b, $c, $d, $e
        for (char suffix = 'a'; suffix <= 'e'; suffix++) {
            try {
                Class<?> cls = classLoader.loadClass("com.zui.launcher.util.OverviewUtilities" + "$" + suffix);
                // 验证：该内部类应有 doInBackground 方法
                try {
                    cls.getDeclaredMethod("doInBackground", Void[].class);
                    logger.info("Found inner class: " + cls.getName());
                    return cls;
                } catch (NoSuchMethodException ignored) {}
            } catch (ClassNotFoundException ignored) {}
        }
        // 再尝试数字后缀: $1, $2, $3, $4, $5
        for (int i = 1; i <= 5; i++) {
            try {
                Class<?> cls = classLoader.loadClass("com.zui.launcher.util.OverviewUtilities" + "$" + i);
                try {
                    cls.getDeclaredMethod("doInBackground", Void[].class);
                    logger.info("Found inner class: " + cls.getName());
                    return cls;
                } catch (NoSuchMethodException ignored) {}
            } catch (ClassNotFoundException ignored) {}
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
            logger.error("Failed to fetch SDK level, use default.", t);
            return Build.VERSION_CODES.BASE; // 返回最低版本
        }
    }
}
