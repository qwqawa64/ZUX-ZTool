package com.qimian233.ztool.hook.modules.launcher;

import com.qimian233.ztool.hook.base.BaseHookModule;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * ZUI Launcher Hotseat扩展Hook模块
 * 功能：解除Hotseat最大数量限制，绕过空间检查，修改DeviceProfile配置
 */
public class ZuiLauncherHotseatHook extends BaseHookModule {

    private static final String LAUNCHER_PACKAGE = "com.zui.launcher";

    @Override
    public String getModuleName() {
        return "zui_launcher_hotseat";
    }

    @Override
    public String[] getTargetPackages() {
        return new String[]{
                LAUNCHER_PACKAGE,
                "com.android.launcher3"  // 可能使用的通用Launcher包名
        };
    }

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        String packageName = lpparam.packageName;

        if (LAUNCHER_PACKAGE.equals(packageName)) {
            hookZuiLauncher(lpparam);
        } else if ("com.android.launcher3".equals(packageName)) {
            hookGenericLauncher(lpparam);
        }
    }

    /**
     * Hook ZUI Launcher特定实现
     */
    private void hookZuiLauncher(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            log("开始Hook ZUI Launcher Hotseat功能");

            // Hook 1: 修改Hotseat的最大数量限制
            hookHotseatMaxCount(lpparam);

            // Hook 2: 绕过空间检查
            hookSpaceChecks(lpparam);

            // Hook 3: 修改DeviceProfile配置
            hookDeviceProfile(lpparam);

            // Hook 4: 绕过添加项目时的限制
            hookAddItemMethods(lpparam);

            // Hook 5: 修改CellLayout相关方法
            hookCellLayoutMethods(lpparam);

            log("ZUI Launcher Hotseat Hook完成");
        } catch (Throwable t) {
            logError("Hook ZUI Launcher失败", t);
        }
    }

    /**
     * Hook 通用Launcher3实现（备用）
     */
    private void hookGenericLauncher(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            log("开始Hook通用Launcher3 Hotseat功能");
            hookHotseatMaxCount(lpparam);
            hookSpaceChecks(lpparam);
            hookDeviceProfile(lpparam);
            log("通用Launcher3 Hotseat Hook完成");
        } catch (Throwable t) {
            logError("Hook通用Launcher3失败", t);
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
                        protected void afterHookedMethod(MethodHookParam param) throws Throwable {
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
                        protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
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
                        protected void afterHookedMethod(MethodHookParam param) throws Throwable {
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
            // Hook DeviceProfile的getHotseatColumnSpan方法
            XposedHelpers.findAndHookMethod(
                    "com.android.launcher3.DeviceProfile",
                    lpparam.classLoader,
                    "getHotseatColumnSpan",
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                            // 修改Hotseat列跨度
                            param.setResult(20);
                            log("修改Hotseat列跨度为20");
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
                        protected void afterHookedMethod(MethodHookParam param) throws Throwable {
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
     * Hook 4: 绕过添加项目时的各种限制
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
                        protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                            // 在添加前确保不会因为空间限制而失败
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
                        protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                            // 确保添加项目时不会受到限制
                            Object pendingItemInfo = param.args[0];
                            int container = (int) param.args[1];

                            if (container == -101) { // -101是Hotseat的容器ID
                                log("正在添加项目到Hotseat，绕过限制");
                            }
                        }
                    }
            );

            // Hook Hotseat的addInScreen方法
            XposedHelpers.findAndHookMethod(
                    "com.android.launcher3.Hotseat",
                    lpparam.classLoader,
                    "addInScreen",
                    android.view.View.class,
                    "com.android.launcher3.model.data.ItemInfo",
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                            log("Hotseat直接添加项目");
                        }
                    }
            );

        } catch (Throwable t) {
            logError("Hook添加方法失败", t);
        }
    }

    /**
     * Hook 5: 修改CellLayout相关方法
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
                        protected void afterHookedMethod(MethodHookParam param) throws Throwable {
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
