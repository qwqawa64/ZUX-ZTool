package com.qimian233.ztool.hook.modules.launcher;

import android.content.Context;

import com.qimian233.ztool.hook.base.BaseHookModule;

import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModuleInterface;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * ZUI Launcher Hotseat扩展Hook模块
 * 解除ZUI Launcher的Hotseat最大数量限制，支持添加更多应用到底部快捷栏
 */
public class ZuiLauncherHotseatHook extends BaseHookModule {

    private static final String LAUNCHER_PACKAGE = "com.zui.launcher";

    public ZuiLauncherHotseatHook() {}

    @Override
    public String getModuleName() {
        return "zui_launcher_hotseat";
    }

    @Override
    public String[] getTargetPackages() {
        return new String[]{LAUNCHER_PACKAGE};
    }

    @Override
    public void handleLoadPackage(XposedModuleInterface.PackageLoadedParam param) throws Throwable {
        ClassLoader classLoader = param.getDefaultClassLoader();
        String packageName = param.getPackageName();
        if (!LAUNCHER_PACKAGE.equals(packageName)) {
            return;
        }

        // 避让逻辑做到 Hook 层，repository 保持干净
        boolean disableDockBar;
        try {
            disableDockBar = this.xposed.getRemotePreferences("xposed_module_config").getBoolean("disable_dock_bar", false);
        } catch (Throwable t) {
            disableDockBar = false;
        }
        if (disableDockBar) {
            log("Disable dock bar hook enabled, will not expand dock bar.");
            return;
        }

        log("开始Hook ZUI Launcher Hotseat限制");

        try {
            // Hook 1: 绕过Hotseat最大数量检查
            hookHotseatMaxCount(classLoader);

            // Hook 2: 绕过空间检查
            hookSpaceChecks(classLoader);

            // Hook 3: 修改DeviceProfile配置
            hookDeviceProfile(classLoader);

            // Hook 4: 修复的添加项目方法
            hookAddItemMethods(classLoader);

            // Hook 5: 修改数据库层面的Hotseat限制
            hookDatabaseHotseatLimit(classLoader);

            // Hook 6: 修改LoaderCursor的位置检查逻辑
            hookLoaderCursorMethods(classLoader);

            // Hook 7: 数据库操作Hook
            hookDatabaseOperations(classLoader);

            // Hook 8: LauncherAppState Hook
            hookLauncherAppState(classLoader);

            // Hook 9: CellLayout相关方法
            hookCellLayoutMethods(classLoader);

            log("ZUI Launcher Hotseat Hook完成");
        } catch (Throwable t) {
            logError("ZUI Launcher Hook过程中发生错误", t);
        }
    }

    /**
     * Hook 1: 修改Hotseat的最大数量限制
     */
    private void hookHotseatMaxCount(ClassLoader classLoader) {
        try {
            Class<?> hotseatClass = classLoader.loadClass("com.android.launcher3.Hotseat");
            Method getMaxCountMethod = hotseatClass.getDeclaredMethod("getMaxCount");
            this.xposed.hook(getMaxCountMethod).intercept(chain -> {
                chain.proceed();
                // 将最大数量从5改为20
                log("修改Hotseat最大数量为20");
                return 20;
            });
        } catch (Throwable t) {
            logError("Hook getMaxCount失败", t);
        }
    }

    /**
     * Hook 2: 绕过各种空间检查方法
     */
    private void hookSpaceChecks(ClassLoader classLoader) {
        try {
            Class<?> launcherClass = classLoader.loadClass("com.android.launcher3.Launcher");

            // Hook Launcher的showOutOfSpaceMessage方法，阻止显示空间不足提示
            Method showOutOfSpaceMethod = launcherClass.getDeclaredMethod("showOutOfSpaceMessage", boolean.class);
            this.xposed.hook(showOutOfSpaceMethod).intercept(chain -> {
                // 阻止显示空间不足提示
                log("阻止显示空间不足提示");
                return null;
            });

            // Hook checkOccupiedShortcut方法，使其总是返回true（可以放置）
            Class<?> workspaceItemInfoClass = classLoader.loadClass("com.android.launcher3.model.data.WorkspaceItemInfo");
            Class<?> workspaceClass = classLoader.loadClass("com.android.launcher3.Workspace");
            Method checkOccupiedMethod = launcherClass.getDeclaredMethod("checkOccupiedShortcut",
                    android.view.View.class, workspaceItemInfoClass, workspaceClass, boolean.class);
            this.xposed.hook(checkOccupiedMethod).intercept(chain -> {
                chain.proceed();
                log("强制通过空间检查");
                return true;
            });

        } catch (Throwable t) {
            logError("Hook空间检查失败", t);
        }
    }

    /**
     * Hook 3: 修改DeviceProfile配置
     */
    private void hookDeviceProfile(ClassLoader classLoader) {
        try {
            Class<?> deviceProfileClass = classLoader.loadClass("com.android.launcher3.DeviceProfile");

            // Hook DeviceProfile的getHotseatColumnSpan
            Method getHotseatColumnSpanMethod = deviceProfileClass.getDeclaredMethod("getHotseatColumnSpan");
            this.xposed.hook(getHotseatColumnSpanMethod).intercept(chain -> {
                chain.proceed();
                return 20;
            });

            // Hook recalculateHotseatWidthAndBorderSpace方法
            Method recalculateMethod = deviceProfileClass.getDeclaredMethod("recalculateHotseatWidthAndBorderSpace");
            this.xposed.hook(recalculateMethod).intercept(chain -> {
                chain.proceed();
                Object deviceProfile = chain.getThisObject();
                // 强制设置numShownHotseatIcons为20
                Field numShownField = deviceProfileClass.getDeclaredField("numShownHotseatIcons");
                numShownField.setAccessible(true);
                numShownField.set(deviceProfile, 20);
                if (DEBUG) log("修改DeviceProfile的Hotseat配置");
                return null;
            });

        } catch (Throwable t) {
            logError("Hook DeviceProfile失败", t);
        }
    }

    /**
     * Hook 4: 修复的添加项目方法
     */
    private void hookAddItemMethods(ClassLoader classLoader) {
        try {
            Class<?> launcherClass = classLoader.loadClass("com.android.launcher3.Launcher");
            Class<?> pendingRequestArgsClass = classLoader.loadClass("com.android.launcher3.util.PendingRequestArgs");
            Class<?> pendingAddItemInfoClass = classLoader.loadClass("com.android.launcher3.PendingAddItemInfo");

            // Hook completeAddShortcut方法，绕过添加限制
            Method completeAddMethod = launcherClass.getDeclaredMethod("completeAddShortcut",
                    android.content.Intent.class, int.class, int.class, int.class, int.class, pendingRequestArgsClass);
            this.xposed.hook(completeAddMethod).intercept(chain -> {
                log("准备添加快捷方式到Hotseat");
                return chain.proceed();
            });

            // Hook addPendingItem方法
            Method addPendingItemMethod = launcherClass.getDeclaredMethod("addPendingItem",
                    pendingAddItemInfoClass, int.class, int.class, int[].class, int.class, int.class);
            this.xposed.hook(addPendingItemMethod).intercept(chain -> {
                // 确保添加项目时不会受到限制
                int container = (int) chain.getArg(1);

                if (container == -101) { // -101是Hotseat的容器ID
                    log("正在添加项目到Hotseat，绕过限制");
                }
                return chain.proceed();
            });

            // Hook addToWorkspace方法（更通用的方法）
            try {
                Class<?> itemInfoClass = classLoader.loadClass("com.android.launcher3.model.data.ItemInfo");
                Method addToWorkspaceMethod = launcherClass.getDeclaredMethod("addToWorkspace",
                        itemInfoClass, boolean.class);
                this.xposed.hook(addToWorkspaceMethod).intercept(chain -> {
                    Object itemInfo = chain.getArg(0);
                    Field containerField = itemInfo.getClass().getDeclaredField("container");
                    containerField.setAccessible(true);
                    int container = containerField.getInt(itemInfo);

                    if (container == -101) {
                        log("添加项目到Hotseat工作区");
                    }
                    return chain.proceed();
                });
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
    private void hookDatabaseHotseatLimit(ClassLoader classLoader) {
        try {
            Class<?> invProfileClass = classLoader.loadClass("com.android.launcher3.InvariantDeviceProfile");

            // Hook InvariantDeviceProfile的getNumDatabaseHotseatIcons
            Method getNumMethod = invProfileClass.getDeclaredMethod("getNumDatabaseHotseatIcons");
            this.xposed.hook(getNumMethod).intercept(chain -> {
                chain.proceed();
                // 将数据库Hotseat数量从5改为20
                log("修改数据库Hotseat数量为20");
                return 20;
            });

            // 直接修改numDatabaseHotseatIcons字段（备用方案）
            try {
                Field numField = invProfileClass.getDeclaredField("numDatabaseHotseatIcons");
                numField.setAccessible(true);
                numField.set(null, 20);
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
    private void hookLoaderCursorMethods(ClassLoader classLoader) {
        try {
            Class<?> loaderCursorClass = classLoader.loadClass("com.android.launcher3.model.LoaderCursor");
            Class<?> itemInfoClass = classLoader.loadClass("com.android.launcher3.model.data.ItemInfo");
            Class<?> bgDataModelClass = classLoader.loadClass("com.android.launcher3.model.BgDataModel");

            // Hook checkItemPlacement方法，绕过Hotseat位置检查
            Method checkItemPlacementMethod = loaderCursorClass.getDeclaredMethod("checkItemPlacement",
                    itemInfoClass, boolean.class);
            this.xposed.hook(checkItemPlacementMethod).intercept(chain -> {
                Object itemInfo = chain.getArg(0);
                Field containerField = itemInfo.getClass().getDeclaredField("container");
                containerField.setAccessible(true);
                int container = containerField.getInt(itemInfo);
                Field screenIdField = itemInfo.getClass().getDeclaredField("screenId");
                screenIdField.setAccessible(true);
                int screenId = screenIdField.getInt(itemInfo);

                // 如果是Hotseat且位置在扩展范围内，直接返回true
                if (container == -101 && screenId >= 0 && screenId < 20) {
                    if (DEBUG) log("强制通过Hotseat位置检查: " + screenId);
                    return true;
                }
                return chain.proceed();
            });

            // Hook b方法（维度检查）
            Method bMethod = loaderCursorClass.getDeclaredMethod("b", itemInfoClass);
            this.xposed.hook(bMethod).intercept(chain -> {
                Object result = chain.proceed();
                Object itemInfo = chain.getArg(0);
                Field containerField = itemInfo.getClass().getDeclaredField("container");
                containerField.setAccessible(true);
                int container = containerField.getInt(itemInfo);

                // 如果是Hotseat，强制返回false（不删除）
                if (container == -101) {
                    log("绕过Hotseat维度检查");
                    return false;
                }
                return result;
            });

            // Hook checkAndAddItem方法
            Method checkAndAddItemMethod = loaderCursorClass.getDeclaredMethod("checkAndAddItem",
                    itemInfoClass, bgDataModelClass);
            this.xposed.hook(checkAndAddItemMethod).intercept(chain -> {
                Object itemInfo = chain.getArg(0);
                Field containerField = itemInfo.getClass().getDeclaredField("container");
                containerField.setAccessible(true);
                int container = containerField.getInt(itemInfo);
                Field screenIdField = itemInfo.getClass().getDeclaredField("screenId");
                screenIdField.setAccessible(true);
                int screenId = screenIdField.getInt(itemInfo);

                if (container == -101) {
                    log("checkAndAddItem - Hotseat位置: " + screenId);
                }
                return chain.proceed();
            });

        } catch (Throwable t) {
            logError("Hook LoaderCursor失败", t);
        }
    }

    /**
     * Hook 7: 数据库操作Hook
     */
    private void hookDatabaseOperations(ClassLoader classLoader) {
        try {
            Class<?> launcherModelClass = classLoader.loadClass("com.android.launcher3.LauncherModel");
            Class<?> itemInfoClass = classLoader.loadClass("com.android.launcher3.model.data.ItemInfo");

            // Hook LauncherModel的addOrMoveItemInDatabase方法
            Method addOrMoveMethod = launcherModelClass.getDeclaredMethod("addOrMoveItemInDatabase",
                    itemInfoClass, int.class, int.class, int.class, int.class);
            this.xposed.hook(addOrMoveMethod).intercept(chain -> {
                int container = (int) chain.getArg(1);
                int screen = (int) chain.getArg(2);

                if (container == -101 && screen >= 5) {
                    if (DEBUG) log("数据库操作 - Hotseat位置: " + screen);
                    // 允许操作继续
                }
                return chain.proceed();
            });

        } catch (Throwable t) {
            logError("Hook数据库操作失败", t);
        }
    }

    /**
     * Hook 8: LauncherAppState Hook
     */
    private void hookLauncherAppState(ClassLoader classLoader) {
        try {
            Class<?> launcherAppStateClass = classLoader.loadClass("com.android.launcher3.LauncherAppState");
            Method getInstanceMethod = launcherAppStateClass.getDeclaredMethod("getInstance", Context.class);
            this.xposed.hook(getInstanceMethod).intercept(chain -> {
                Object launcherAppState = chain.proceed();
                if (launcherAppState != null) {
                    try {
                        // 修改InvariantDeviceProfile的numDatabaseHotseatIcons
                        Field invField = launcherAppStateClass.getDeclaredField("mInvariantDeviceProfile");
                        invField.setAccessible(true);
                        Object invDeviceProfile = invField.get(launcherAppState);
                        Field numField = invDeviceProfile.getClass().getDeclaredField("numDatabaseHotseatIcons");
                        numField.setAccessible(true);
                        numField.set(invDeviceProfile, 20);
                        log("修改InvariantDeviceProfile的numDatabaseHotseatIcons为20");
                    } catch (NoSuchFieldError ignored) {
                        log("修改InvariantDeviceProfile失败, 无法找到对应字段");
                    } catch (Exception e) {
                        logError("Unhandle exception happened when attempting to modify mInvariantDeviceProfile: ", e);
                    }
                }
                return launcherAppState;
            });
        } catch (Throwable t) {
            logError("Hook LauncherAppState失败", t);
        }
    }

    /**
     * Hook 9: 修改CellLayout相关方法
     */
    private void hookCellLayoutMethods(ClassLoader classLoader) {
        try {
            Class<?> cellLayoutClass = classLoader.loadClass("com.android.launcher3.CellLayout");

            // Hook CellLayout的findCellForSpan方法，使其总是能找到位置
            Method findCellMethod = cellLayoutClass.getDeclaredMethod("findCellForSpan",
                    int[].class, int.class, int.class);
            this.xposed.hook(findCellMethod).intercept(chain -> {
                boolean result = (boolean) chain.proceed();
                if (!result) {
                    // 如果原本找不到位置，强制返回true并设置坐标
                    int[] cellXY = (int[]) chain.getArg(0);
                    cellXY[0] = 0;
                    cellXY[1] = 0;
                    log("强制找到Cell位置");
                    return true;
                }
                return result;
            });

        } catch (Throwable t) {
            logError("Hook CellLayout失败", t);
        }
    }
}
