package com.qimian233.ztool.hook.modules.launcher;

import com.qimian233.ztool.hook.base.BaseHookModule;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * ZUI Launcher Hotseat扩展Hook模块
 * 解除ZUI Launcher的Hotseat最大数量限制，支持添加更多应用到底部快捷栏
 */
public class ZuiLauncherHotseatHook extends BaseHookModule {

    private static final String LAUNCHER_PACKAGE = "com.zui.launcher";

    @Override
    public String getModuleName() {
        return "zui_launcher_hotseat";
    }

    @Override
    public String[] getTargetPackages() {
        return new String[]{LAUNCHER_PACKAGE};
    }

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        if (!LAUNCHER_PACKAGE.equals(lpparam.packageName)) {
            return;
        }

        log("开始Hook ZUI Launcher Hotseat限制");

        try {
            // Hook 1: 绕过Hotseat最大数量检查
            hookHotseatMaxCount(lpparam);

            // Hook 2: 绕过空间检查
            hookSpaceChecks(lpparam);

            // Hook 3: 修改DeviceProfile配置
            hookDeviceProfile(lpparam);

            // Hook 4: 修复的添加项目方法
            hookAddItemMethods(lpparam);

            // Hook 5: 修改数据库层面的Hotseat限制
            hookDatabaseHotseatLimit(lpparam);

            // Hook 6: 修改LoaderCursor的位置检查逻辑
            hookLoaderCursorMethods(lpparam);

            // Hook 7: 数据库操作Hook
            hookDatabaseOperations(lpparam);

            // Hook 8: LauncherAppState Hook
            hookLauncherAppState(lpparam);

            // Hook 9: CellLayout相关方法
            hookCellLayoutMethods(lpparam);

            log("ZUI Launcher Hotseat Hook完成");
        } catch (Throwable t) {
            logError("ZUI Launcher Hook过程中发生错误", t);
        }
    }

    /**
     * Hook 1: 修改Hotseat的最大数量限制
     */
    private void hookHotseatMaxCount(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            XposedHelpers.findAndHookMethod(
                    "com.android.launcher3.Hotseat",
                    lpparam.classLoader,
                    "getMaxCount",
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            // 将最大数量从5改为20
                            param.setResult(20);
                            log("修改Hotseat最大数量为20");
                        }
                    }
            );
        } catch (Throwable t) {
            logError("Hook getMaxCount失败", t);
        }
    }

    /**
     * Hook 2: 绕过各种空间检查方法
     */
    private void hookSpaceChecks(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            // Hook Launcher的showOutOfSpaceMessage方法，阻止显示空间不足提示
            XposedHelpers.findAndHookMethod(
                    "com.android.launcher3.Launcher",
                    lpparam.classLoader,
                    "showOutOfSpaceMessage",
                    boolean.class,
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            // 阻止显示空间不足提示
                            param.setResult(null);
                            log("阻止显示空间不足提示");
                        }
                    }
            );

            // Hook checkOccupiedShortcut方法，使其总是返回true（可以放置）
            XposedHelpers.findAndHookMethod(
                    "com.android.launcher3.Launcher",
                    lpparam.classLoader,
                    "checkOccupiedShortcut",
                    android.view.View.class,
                    "com.android.launcher3.model.data.WorkspaceItemInfo",
                    "com.android.launcher3.Workspace",
                    boolean.class,
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            param.setResult(true);
                            log("强制通过空间检查");
                        }
                    }
            );

        } catch (Throwable t) {
            logError("Hook空间检查失败", t);
        }
    }

    /**
     * Hook 3: 修改DeviceProfile配置
     */
    private void hookDeviceProfile(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            // Hook DeviceProfile的numShownHotseatIcons字段
            XposedHelpers.findAndHookMethod(
                    "com.android.launcher3.DeviceProfile",
                    lpparam.classLoader,
                    "getHotseatColumnSpan",
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            // 修改Hotseat列跨度
                            param.setResult(20);
                        }
                    }
            );

            // Hook recalculateHotseatWidthAndBorderSpace方法
            XposedHelpers.findAndHookMethod(
                    "com.android.launcher3.DeviceProfile",
                    lpparam.classLoader,
                    "recalculateHotseatWidthAndBorderSpace",
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            Object deviceProfile = param.thisObject;
                            // 强制设置numShownHotseatIcons为20
                            XposedHelpers.setIntField(deviceProfile, "numShownHotseatIcons", 20);
                            log("修改DeviceProfile的Hotseat配置");
                        }
                    }
            );

        } catch (Throwable t) {
            logError("Hook DeviceProfile失败", t);
        }
    }

    /**
     * Hook 4: 修复的添加项目方法
     */
    private void hookAddItemMethods(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            // Hook completeAddShortcut方法，绕过添加限制
            XposedHelpers.findAndHookMethod(
                    "com.android.launcher3.Launcher",
                    lpparam.classLoader,
                    "completeAddShortcut",
                    android.content.Intent.class,
                    int.class,
                    int.class,
                    int.class,
                    int.class,
                    "com.android.launcher3.util.PendingRequestArgs",
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            log("准备添加快捷方式到Hotseat");
                        }
                    }
            );

            // Hook addPendingItem方法
            XposedHelpers.findAndHookMethod(
                    "com.android.launcher3.Launcher",
                    lpparam.classLoader,
                    "addPendingItem",
                    "com.android.launcher3.PendingAddItemInfo",
                    int.class,
                    int.class,
                    int[].class,
                    int.class,
                    int.class,
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            // 确保添加项目时不会受到限制
                            int container = (int) param.args[1];

                            if (container == -101) { // -101是Hotseat的容器ID
                                log("正在添加项目到Hotseat，绕过限制");
                            }
                        }
                    }
            );

            // Hook addToWorkspace方法（更通用的方法）
            try {
                XposedHelpers.findAndHookMethod(
                        "com.android.launcher3.Launcher",
                        lpparam.classLoader,
                        "addToWorkspace",
                        "com.android.launcher3.model.data.ItemInfo",
                        boolean.class,
                        new XC_MethodHook() {
                            @Override
                            protected void beforeHookedMethod(MethodHookParam param) {
                                Object itemInfo = param.args[0];
                                int container = XposedHelpers.getIntField(itemInfo, "container");

                                if (container == -101) {
                                    log("添加项目到Hotseat工作区");
                                }
                            }
                        }
                );
            } catch (Throwable t) {
                logError("Hook addToWorkspace失败", t);
            }

        } catch (Throwable t) {
            logError("Hook添加方法失败", t);
        }
    }

    /**
     * Hook 5: 修改数据库层面的Hotseat数量限制
     */
    private void hookDatabaseHotseatLimit(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            // Hook InvariantDeviceProfile的numDatabaseHotseatIcons字段
            XposedHelpers.findAndHookMethod(
                    "com.android.launcher3.InvariantDeviceProfile",
                    lpparam.classLoader,
                    "getNumDatabaseHotseatIcons",
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            // 将数据库Hotseat数量从5改为20
                            param.setResult(20);
                            log("修改数据库Hotseat数量为20");
                        }
                    }
            );

            // 直接修改numDatabaseHotseatIcons字段（备用方案）
            try {
                Class<?> invProfileClass = XposedHelpers.findClass(
                        "com.android.launcher3.InvariantDeviceProfile",
                        lpparam.classLoader
                );

                XposedHelpers.setStaticIntField(invProfileClass, "numDatabaseHotseatIcons", 20);
                log("直接修改numDatabaseHotseatIcons为20");
            } catch (Throwable t) {
                logError("直接修改numDatabaseHotseatIcons失败", t);
            }

        } catch (Throwable t) {
            logError("Hook数据库Hotseat限制失败", t);
        }
    }

    /**
     * Hook 6: 修改LoaderCursor的位置检查逻辑
     */
    private void hookLoaderCursorMethods(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            // Hook checkItemPlacement方法，绕过Hotseat位置检查
            XposedHelpers.findAndHookMethod(
                    "com.android.launcher3.model.LoaderCursor",
                    lpparam.classLoader,
                    "checkItemPlacement",
                    "com.android.launcher3.model.data.ItemInfo",
                    boolean.class,
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            Object itemInfo = param.args[0];
                            int container = XposedHelpers.getIntField(itemInfo, "container");
                            int screenId = XposedHelpers.getIntField(itemInfo, "screenId");

                            // 如果是Hotseat且位置在扩展范围内，直接返回true
                            if (container == -101 && screenId >= 0 && screenId < 20) {
                                param.setResult(true);
                                log("强制通过Hotseat位置检查: " + screenId);
                            }
                        }
                    }
            );

            // Hook b方法（维度检查）
            XposedHelpers.findAndHookMethod(
                    "com.android.launcher3.model.LoaderCursor",
                    lpparam.classLoader,
                    "b",
                    "com.android.launcher3.model.data.ItemInfo",
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            Object itemInfo = param.args[0];
                            int container = XposedHelpers.getIntField(itemInfo, "container");

                            // 如果是Hotseat，强制返回false（不删除）
                            if (container == -101) {
                                param.setResult(false);
                                log("绕过Hotseat维度检查");
                            }
                        }
                    }
            );

            // Hook checkAndAddItem方法
            XposedHelpers.findAndHookMethod(
                    "com.android.launcher3.model.LoaderCursor",
                    lpparam.classLoader,
                    "checkAndAddItem",
                    "com.android.launcher3.model.data.ItemInfo",
                    "com.android.launcher3.model.BgDataModel",
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            Object itemInfo = param.args[0];
                            int container = XposedHelpers.getIntField(itemInfo, "container");
                            int screenId = XposedHelpers.getIntField(itemInfo, "screenId");

                            if (container == -101) {
                                log("checkAndAddItem - Hotseat位置: " + screenId);
                            }
                        }
                    }
            );

        } catch (Throwable t) {
            logError("Hook LoaderCursor失败", t);
        }
    }

    /**
     * Hook 7: 数据库操作Hook
     */
    private void hookDatabaseOperations(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            // Hook LauncherModel的添加项目方法
            XposedHelpers.findAndHookMethod(
                    "com.android.launcher3.LauncherModel",
                    lpparam.classLoader,
                    "addOrMoveItemInDatabase",
                    "com.android.launcher3.model.data.ItemInfo",
                    int.class,
                    int.class,
                    int.class,
                    int.class,
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            int container = (int) param.args[1];
                            int screen = (int) param.args[2];

                            if (container == -101 && screen >= 5) {
                                log("数据库操作 - Hotseat位置: " + screen);
                                // 允许操作继续
                            }
                        }
                    }
            );

        } catch (Throwable t) {
            logError("Hook数据库操作失败", t);
        }
    }

    /**
     * Hook 8: LauncherAppState Hook
     */
    private void hookLauncherAppState(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            XposedHelpers.findAndHookMethod(
                    "com.android.launcher3.LauncherAppState",
                    lpparam.classLoader,
                    "getInstance",
                    android.content.Context.class,
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            Object launcherAppState = param.getResult();
                            if (launcherAppState != null) {
                                try {
                                    // 修改InvariantDeviceProfile的numDatabaseHotseatIcons
                                    Object invDeviceProfile = XposedHelpers.getObjectField(launcherAppState, "mInvariantDeviceProfile");
                                    XposedHelpers.setIntField(invDeviceProfile, "numDatabaseHotseatIcons", 20);
                                    log("修改InvariantDeviceProfile的numDatabaseHotseatIcons为20");
                                } catch (Throwable t) {
                                    logError("修改InvariantDeviceProfile失败", t);
                                }
                            }
                        }
                    }
            );
        } catch (Throwable t) {
            logError("Hook LauncherAppState失败", t);
        }
    }

    /**
     * Hook 9: 修改CellLayout相关方法
     */
    private void hookCellLayoutMethods(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            // Hook CellLayout的findCellForSpan方法，使其总是能找到位置
            XposedHelpers.findAndHookMethod(
                    "com.android.launcher3.CellLayout",
                    lpparam.classLoader,
                    "findCellForSpan",
                    int[].class,
                    int.class,
                    int.class,
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            boolean result = (boolean) param.getResult();
                            if (!result) {
                                // 如果原本找不到位置，强制返回true并设置坐标
                                int[] cellXY = (int[]) param.args[0];
                                cellXY[0] = 0;
                                cellXY[1] = 0;
                                param.setResult(true);
                                log("强制找到Cell位置");
                            }
                        }
                    }
            );

        } catch (Throwable t) {
            logError("Hook CellLayout失败", t);
        }
    }
}
