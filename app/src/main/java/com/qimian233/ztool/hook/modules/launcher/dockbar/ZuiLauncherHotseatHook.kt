package com.qimian233.ztool.hook.modules.launcher.dockbar;

import android.annotation.SuppressLint;

import com.qimian233.ztool.data.PreferenceKeys;
import com.qimian233.ztool.hook.base.AppHookModule;
import com.qimian233.ztool.hook.base.DexKitHelper;

import io.github.libxposed.api.XposedModuleInterface;

import org.luckypray.dexkit.DexKitBridge;
import org.luckypray.dexkit.query.FindMethod;
import org.luckypray.dexkit.query.matchers.MethodMatcher;
import org.luckypray.dexkit.result.MethodData;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

/**
 * ZUI Launcher Hotseat扩展Hook模块
 * 解除ZUI Launcher的Hotseat最大数量限制，支持添加更多应用到底部快捷栏
 */
@SuppressLint("PrivateApi")
public class ZuiLauncherHotseatHook extends AppHookModule {

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
            disableDockBar = getRemotePreferences().getBoolean(PreferenceKeys.DISABLE_DOCK_BAR.name, false);
        } catch (Throwable t) {
            disableDockBar = false;
        }
        if (disableDockBar) {
            logger.warn("Disable dock bar hook enabled, will not expand dock bar.");
            return;
        }

        logger.info("开始Hook ZUI Launcher Hotseat限制");

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

            // Hook 9: CellLayout相关方法
            hookCellLayoutMethods(classLoader);

            logger.info("ZUI Launcher Hotseat Hook完成");
        } catch (Throwable t) {
            logger.error("ZUI Launcher Hook过程中发生错误", t);
        }
    }

    /**
     * Hook 1: 修改Hotseat的最大数量限制
     */
    private void hookHotseatMaxCount(ClassLoader classLoader) {
        try {
            Class<?> hotseatClass = classLoader.loadClass("com.android.launcher3.Hotseat");
            Method getMaxCountMethod = hotseatClass.getDeclaredMethod("getMaxCount");
            hookWithId(getMaxCountMethod, "get_max_count", chain -> {
                chain.proceed();
                // 将最大数量从5改为20
                logger.debug("修改Hotseat最大数量为20");
                return 20;
            });
        } catch (Throwable t) {
            logger.error("Hook getMaxCount失败", t);
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
            hookWithId(showOutOfSpaceMethod, "show_out_of_space", chain -> {
                // 阻止显示空间不足提示
                logger.debug("阻止显示空间不足提示");
                return null;
            });

            // Hook checkOccupiedShortcut方法，使其总是返回true（可以放置）
            Class<?> workspaceItemInfoClass = classLoader.loadClass("com.android.launcher3.model.data.WorkspaceItemInfo");
            Class<?> workspaceClass = classLoader.loadClass("com.android.launcher3.Workspace");
            Method checkOccupiedMethod = launcherClass.getDeclaredMethod("checkOccupiedShortcut",
                    android.view.View.class, workspaceItemInfoClass, workspaceClass, boolean.class);
            hookWithId(checkOccupiedMethod, "check_occupied", chain -> {
                chain.proceed();
                logger.debug("强制通过空间检查");
                return true;
            });

        } catch (Throwable t) {
            logger.error("Hook空间检查失败", t);
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
            hookWithId(getHotseatColumnSpanMethod, "get_hotseat_column_span", chain -> {
                chain.proceed();
                return 20;
            });

            // Hook recalculateHotseatWidthAndBorderSpace方法
            Method recalculateMethod = deviceProfileClass.getDeclaredMethod("recalculateHotseatWidthAndBorderSpace");
            hookWithId(recalculateMethod, "recalculate", chain -> {
                chain.proceed();
                Object deviceProfile = chain.getThisObject();
                // 强制设置numShownHotseatIcons为20
                Field numShownField = deviceProfileClass.getDeclaredField("numShownHotseatIcons");
                numShownField.setAccessible(true);
                numShownField.set(deviceProfile, 20);
                logger.debug("修改DeviceProfile的Hotseat配置");
                return null;
            });

        } catch (Throwable t) {
            logger.error("Hook DeviceProfile失败", t);
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
            hookWithId(completeAddMethod, "complete_add", chain -> {
                logger.debug("准备添加快捷方式到Hotseat");
                return chain.proceed();
            });

            // Hook addPendingItem方法
            Method addPendingItemMethod = launcherClass.getDeclaredMethod("addPendingItem",
                    pendingAddItemInfoClass, int.class, int.class, int[].class, int.class, int.class);
            hookWithId(addPendingItemMethod, "add_pending_item", chain -> {
                // 确保添加项目时不会受到限制
                int container = (int) chain.getArg(1);

                if (container == -101) { // -101是Hotseat的容器ID
                    logger.debug("正在添加项目到Hotseat，绕过限制");
                }
                return chain.proceed();
            });

            // Hook addToWorkspace方法（更通用的方法）
            try {
                Class<?> itemInfoClass = classLoader.loadClass("com.android.launcher3.model.data.ItemInfo");
                Method addToWorkspaceMethod = launcherClass.getDeclaredMethod("addToWorkspace",
                        itemInfoClass, boolean.class);
                hookWithId(addToWorkspaceMethod, "add_to_workspace", chain -> {
                    Object itemInfo = chain.getArg(0);
                    Field containerField = findField(itemInfo.getClass(), "container");
                    int container = containerField.getInt(itemInfo);

                    if (container == -101) {
                        logger.debug("添加项目到Hotseat工作区");
                    }
                    return chain.proceed();
                });
            } catch (Throwable t) {
                logger.error("Hook addToWorkspace失败", t);
            }

        } catch (Throwable t) {
            logger.error("Hook添加方法失败", t);
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
            hookWithId(getNumMethod, "get_num", chain -> {
                chain.proceed();
                // 将数据库Hotseat数量从5改为20
                logger.debug("修改数据库Hotseat数量为20");
                return 20;
            });

            // 直接修改numDatabaseHotseatIcons字段（备用方案）
            try {
                Field numField = invProfileClass.getDeclaredField("numDatabaseHotseatIcons");
                numField.setAccessible(true);
                numField.set(null, 20);
                logger.debug("直接修改numDatabaseHotseatIcons为20");
            } catch (Throwable t) {
                logger.error("直接修改numDatabaseHotseatIcons失败", t);
            }

        } catch (Throwable t) {
            logger.error("Hook数据库Hotseat限制失败", t);
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
            hookWithId(checkItemPlacementMethod, "check_item_placement", chain -> {
                Object itemInfo = chain.getArg(0);
                Field containerField = findField(itemInfo.getClass(), "container");
                int container = containerField.getInt(itemInfo);
                Field screenIdField = findField(itemInfo.getClass(), "screenId");
                int screenId = screenIdField.getInt(itemInfo);

                // 如果是Hotseat且位置在扩展范围内，直接返回true
                if (container == -101 && screenId >= 0 && screenId < 20) {
                    logger.debug("强制通过Hotseat位置检查: " + screenId);
                    return true;
                }
                return chain.proceed();
            });

            // Hook b方法（维度检查）— 通过 DEXKit 按签名动态查找
            String bMethodName = findBMethodName(classLoader, itemInfoClass);
            Method bMethod = loaderCursorClass.getDeclaredMethod(bMethodName, itemInfoClass);
            hookWithId(bMethod, "hook_289", chain -> {
                Object result = chain.proceed();
                Object itemInfo = chain.getArg(0);
                Field containerField = findField(itemInfo.getClass(), "container");
                int container = containerField.getInt(itemInfo);

                // 如果是Hotseat，强制返回false（不删除）
                if (container == -101) {
                    logger.debug("绕过Hotseat维度检查");
                    return false;
                }
                return result;
            });

            // Hook checkAndAddItem方法
            Method checkAndAddItemMethod = loaderCursorClass.getDeclaredMethod("checkAndAddItem",
                    itemInfoClass, bgDataModelClass);
            hookWithId(checkAndAddItemMethod, "check_and_add_item", chain -> {
                Object itemInfo = chain.getArg(0);
                Field containerField = findField(itemInfo.getClass(), "container");
                int container = containerField.getInt(itemInfo);
                Field screenIdField = findField(itemInfo.getClass(), "screenId");
                int screenId = screenIdField.getInt(itemInfo);

                if (container == -101) {
                    logger.debug("checkAndAddItem - Hotseat位置: " + screenId);
                }
                return chain.proceed();
            });

        } catch (Throwable t) {
            logger.error("Hook LoaderCursor失败", t);
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
            hookWithId(addOrMoveMethod, "add_or_move", chain -> {
                int container = (int) chain.getArg(1);
                int screen = (int) chain.getArg(2);

                if (container == -101 && screen >= 5) {
                    logger.debug("数据库操作 - Hotseat位置: " + screen);
                    // 允许操作继续
                }
                return chain.proceed();
            });

        } catch (Throwable t) {
            logger.error("Hook数据库操作失败", t);
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
            hookWithId(findCellMethod, "find_cell", chain -> {
                boolean result = (boolean) chain.proceed();
                if (!result) {
                    // 如果原本找不到位置，强制返回true并设置坐标
                    int[] cellXY = (int[]) chain.getArg(0);
                    cellXY[0] = 0;
                    cellXY[1] = 0;
                    logger.debug("强制找到Cell位置");
                    return true;
                }
                return true;
            });

        } catch (Throwable t) {
            logger.error("Hook CellLayout失败", t);
        }
    }

    /**
     * 通过 DEXKit 在 LoaderCursor 中查找签名 (ItemInfo)→boolean 的混淆方法。
     */
    private String findBMethodName(ClassLoader classLoader, Class<?> itemInfoClass) {
        DexKitBridge bridge = DexKitHelper.INSTANCE.getBridgeForClass(
                classLoader, "com.android.launcher3.model.LoaderCursor");
        if (bridge != null) {
            try {
                List<MethodData> methods = bridge.findMethod(FindMethod.create()
                        .searchPackages("com.android.launcher3")
                        .matcher(MethodMatcher.create()
                                .paramTypes(itemInfoClass.getName())
                                .returnType("boolean")
                                .declaredClass("com.android.launcher3.model.LoaderCursor")
                        )
                );
                for (MethodData md : methods) {
                    logger.info("DEXKit found dimension-check method: " + md.getName());
                    return md.getName();
                }
            } catch (Throwable ignored) {}
        }
        return "b"; // 回退硬编码
    }
}
