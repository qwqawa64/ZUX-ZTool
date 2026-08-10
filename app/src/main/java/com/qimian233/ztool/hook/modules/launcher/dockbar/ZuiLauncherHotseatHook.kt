package com.qimian233.ztool.hook.modules.launcher.dockbar

import android.annotation.SuppressLint
import android.content.Intent
import android.view.View
import com.qimian233.ztool.data.keys.ScopeKeys
import com.qimian233.ztool.data.keys.PreferenceKeys
import com.qimian233.ztool.dexindex.DexIndexConstants
import com.qimian233.ztool.hook.base.AppHookModule
import com.qimian233.ztool.hook.base.DexIndexStore
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam

/**
 * ZUI Launcher Hotseat扩展Hook模块
 * 解除ZUI Launcher的Hotseat最大数量限制，支持添加更多应用到底部快捷栏
 */
@SuppressLint("PrivateApi")
class ZuiLauncherHotseatHook : AppHookModule() {
    override fun getModuleName(): String = PreferenceKeys.ZUI_LAUNCHER_HOTSEAT.name

    override fun getTargetPackages(): Array<String> = arrayOf(ScopeKeys.LAUNCHER.packageName)

    @Throws(Throwable::class)
    override fun handleLoadPackage(param: PackageLoadedParam) {
        val classLoader = param.defaultClassLoader

        // 避让逻辑做到 Hook 层，repository 保持干净
        val disableDockBar: Boolean = try {
            remotePreferences.getBoolean(PreferenceKeys.DISABLE_DOCK_BAR.name, false)
        } catch (_: Throwable) {
            false
        }
        if (disableDockBar) {
            logger.warn("Disable dock bar hook enabled, will not expand dock bar.")
            return
        }

        logger.info("开始Hook ZUI Launcher Hotseat限制")

        try {
            // Hook 1: 绕过Hotseat最大数量检查
            hookHotseatMaxCount(classLoader)

            // Hook 2: 绕过空间检查
            hookSpaceChecks(classLoader)

            // Hook 3: 修改DeviceProfile配置
            hookDeviceProfile(classLoader)

            // Hook 4: 修复的添加项目方法
            hookAddItemMethods(classLoader)

            // Hook 5: 修改数据库层面的Hotseat限制
            hookDatabaseHotseatLimit(classLoader)

            // Hook 6: 修改LoaderCursor的位置检查逻辑
            hookLoaderCursorMethods(classLoader)

            // Hook 7: 数据库操作Hook
            hookDatabaseOperations(classLoader)

            // Hook 9: CellLayout相关方法
            hookCellLayoutMethods(classLoader)

            logger.info("ZUI Launcher Hotseat Hook完成")
        } catch (t: Throwable) {
            logger.error("ZUI Launcher Hook过程中发生错误", t)
        }
    }

    /**
     * Hook 1: 修改Hotseat的最大数量限制
     */
    private fun hookHotseatMaxCount(classLoader: ClassLoader) {
        try {
            val hotseatClass = classLoader.loadClass("com.android.launcher3.Hotseat")
            val getMaxCountMethod = hotseatClass.getDeclaredMethod("getMaxCount")
            hookWithId(getMaxCountMethod, "get_max_count") { chain ->
                chain.proceed()
                logger.debug("修改Hotseat最大数量为20")
                20
            }
        } catch (t: Throwable) {
            logger.error("Hook getMaxCount失败", t)
        }
    }

    /**
     * Hook 2: 绕过各种空间检查方法
     */
    private fun hookSpaceChecks(classLoader: ClassLoader) {
        try {
            val launcherClass = classLoader.loadClass("com.android.launcher3.Launcher")

            // Hook Launcher的showOutOfSpaceMessage方法，阻止显示空间不足提示
            val showOutOfSpaceMethod = findMethod(launcherClass, "showOutOfSpaceMessage",
                Boolean::class.javaPrimitiveType
            )
            hookWithId(showOutOfSpaceMethod, "show_out_of_space") {
                logger.debug("阻止显示空间不足提示")
                null
            }

            // Hook checkOccupiedShortcut方法，使其总是返回true（可以放置）
            val workspaceItemInfoClass =
                classLoader.loadClass("com.android.launcher3.model.data.WorkspaceItemInfo")
            val workspaceClass = classLoader.loadClass("com.android.launcher3.Workspace")
            val checkOccupiedMethod = findMethod(launcherClass, "checkOccupiedShortcut",
                View::class.java,
                workspaceItemInfoClass,
                workspaceClass,
                Boolean::class.javaPrimitiveType
            )
            hookWithId(checkOccupiedMethod, "check_occupied") { chain ->
                chain.proceed()
                logger.debug("强制通过空间检查")
                true
            }
        } catch (t: Throwable) {
            logger.error("Hook空间检查失败", t)
        }
    }

    /**
     * Hook 3: 修改DeviceProfile配置
     */
    private fun hookDeviceProfile(classLoader: ClassLoader) {
        try {
            val deviceProfileClass = classLoader.loadClass("com.android.launcher3.DeviceProfile")

            // Hook DeviceProfile的getHotseatColumnSpan
            val getHotseatColumnSpanMethod =
                deviceProfileClass.getDeclaredMethod("getHotseatColumnSpan")
            hookWithId(getHotseatColumnSpanMethod, "get_hotseat_column_span") { chain ->
                chain.proceed()
                20
            }

            // Hook recalculateHotseatWidthAndBorderSpace方法
            val recalculateMethod =
                deviceProfileClass.getDeclaredMethod("recalculateHotseatWidthAndBorderSpace")
            hookWithId(recalculateMethod, "recalculate") { chain ->
                chain.proceed()
                val deviceProfile = chain.thisObject
                // 强制设置numShownHotseatIcons为20
                val numShownField = findField(deviceProfileClass, "numShownHotseatIcons")
                numShownField.set(deviceProfile, 20)
                logger.debug("修改DeviceProfile的Hotseat配置")
                null
            }
        } catch (t: Throwable) {
            logger.error("Hook DeviceProfile失败", t)
        }
    }

    /**
     * Hook 4: 修复的添加项目方法
     */
    private fun hookAddItemMethods(classLoader: ClassLoader) {
        try {
            val launcherClass = classLoader.loadClass("com.android.launcher3.Launcher")
            val pendingRequestArgsClass =
                classLoader.loadClass("com.android.launcher3.util.PendingRequestArgs")
            val pendingAddItemInfoClass =
                classLoader.loadClass("com.android.launcher3.PendingAddItemInfo")

            // Hook completeAddShortcut方法，绕过添加限制
            val completeAddMethod = launcherClass.getDeclaredMethod(
                "completeAddShortcut",
                Intent::class.java,
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType,
                pendingRequestArgsClass
            )
            hookWithId(completeAddMethod, "complete_add") { chain ->
                logger.debug("准备添加快捷方式到Hotseat")
                chain.proceed()
            }

            // Hook addPendingItem方法
            val addPendingItemMethod = launcherClass.getDeclaredMethod(
                "addPendingItem",
                pendingAddItemInfoClass,
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType,
                IntArray::class.java,
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType
            )
            hookWithId(
                addPendingItemMethod,
                "add_pending_item"
            ) { chain ->
                // 确保添加项目时不会受到限制
                val container = chain.args[1] as Int

                if (container == -101) { // -101是Hotseat的容器ID
                    logger.debug("正在添加项目到Hotseat，绕过限制")
                }
                chain.proceed()
            }

            // Hook addToWorkspace方法（更通用的方法）
            try {
                val itemInfoClass =
                    classLoader.loadClass("com.android.launcher3.model.data.ItemInfo")
                val addToWorkspaceMethod = launcherClass.getDeclaredMethod(
                    "addToWorkspace",
                    itemInfoClass, Boolean::class.javaPrimitiveType
                )
                hookWithId(
                    addToWorkspaceMethod,
                    "add_to_workspace"
                ) { chain ->
                    val itemInfo = chain.args[0]
                    val containerField = findField(itemInfo.javaClass, "container")
                    val container = containerField.getInt(itemInfo)

                    if (container == -101) {
                        logger.debug("添加项目到Hotseat工作区")
                    }
                    chain.proceed()
                }
            } catch (t: Throwable) {
                logger.error("Hook addToWorkspace失败", t)
            }
        } catch (t: Throwable) {
            logger.error("Hook添加方法失败", t)
        }
    }

    /**
     * Hook 5: 修改数据库层面的Hotseat数量限制
     */
    private fun hookDatabaseHotseatLimit(classLoader: ClassLoader) {
        try {
            val invProfileClass =
                classLoader.loadClass("com.android.launcher3.InvariantDeviceProfile")

            // Hook InvariantDeviceProfile的getNumDatabaseHotseatIcons
            val getNumMethod = invProfileClass.getDeclaredMethod("getNumDatabaseHotseatIcons")
            hookWithId(getNumMethod, "get_num") { chain ->
                chain.proceed()
                logger.debug("修改数据库Hotseat数量为20")
                20
            }

            // 直接修改numDatabaseHotseatIcons字段（备用方案）
            try {
                val numField = findField(invProfileClass, "numDatabaseHotseatIcons")
                numField.set(null, 20)
                logger.debug("直接修改numDatabaseHotseatIcons为20")
            } catch (t: Throwable) {
                logger.error("直接修改numDatabaseHotseatIcons失败", t)
            }
        } catch (t: Throwable) {
            logger.error("Hook数据库Hotseat限制失败", t)
        }
    }

    /**
     * Hook 6: 修改LoaderCursor的位置检查逻辑
     */
    private fun hookLoaderCursorMethods(classLoader: ClassLoader) {
        try {
            val loaderCursorClass =
                classLoader.loadClass("com.android.launcher3.model.LoaderCursor")
            val itemInfoClass = classLoader.loadClass("com.android.launcher3.model.data.ItemInfo")
            val bgDataModelClass = classLoader.loadClass("com.android.launcher3.model.BgDataModel")

            // Hook checkItemPlacement方法，绕过Hotseat位置检查
            val checkItemPlacementMethod = loaderCursorClass.getDeclaredMethod(
                "checkItemPlacement",
                itemInfoClass, Boolean::class.javaPrimitiveType
            )
            hookWithId(
                checkItemPlacementMethod,
                "check_item_placement"
            ) { chain ->
                val itemInfo = chain.args[0]
                val containerField = findField(itemInfo.javaClass, "container")
                val container = containerField.getInt(itemInfo)
                val screenIdField = findField(itemInfo.javaClass, "screenId")
                val screenId = screenIdField.getInt(itemInfo)

                // 如果是Hotseat且位置在扩展范围内，直接返回true
                if (container == -101 && screenId >= 0 && screenId < 20) {
                    logger.debug("强制通过Hotseat位置检查: $screenId")
                    return@hookWithId true
                }
                chain.proceed()
            }

            // Hook b方法（维度检查）— 方法名来自离线索引
            val bMethodName = findBMethodName()
            val bMethod = loaderCursorClass.getDeclaredMethod(bMethodName, itemInfoClass)
            hookWithId(bMethod, "hook_289") { chain ->
                val result = chain.proceed()
                val itemInfo = chain.args[0]
                val containerField = findField(itemInfo.javaClass, "container")
                val container = containerField.getInt(itemInfo)

                // 如果是Hotseat，强制返回false（不删除）
                if (container == -101) {
                    logger.debug("绕过Hotseat维度检查")
                    return@hookWithId false
                }
                result
            }

            // Hook checkAndAddItem方法
            val checkAndAddItemMethod = loaderCursorClass.getDeclaredMethod(
                "checkAndAddItem",
                itemInfoClass, bgDataModelClass
            )
            hookWithId(
                checkAndAddItemMethod,
                "check_and_add_item"
            ) { chain ->
                val itemInfo = chain.args[0]
                val containerField = findField(itemInfo.javaClass, "container")
                val container = containerField.getInt(itemInfo)
                val screenIdField = findField(itemInfo.javaClass, "screenId")
                val screenId = screenIdField.getInt(itemInfo)

                if (container == -101) {
                    logger.debug("checkAndAddItem - Hotseat位置: $screenId")
                }
                chain.proceed()
            }
        } catch (t: Throwable) {
            logger.error("Hook LoaderCursor失败", t)
        }
    }

    /**
     * Hook 7: 数据库操作Hook
     */
    private fun hookDatabaseOperations(classLoader: ClassLoader) {
        try {
            val launcherModelClass = classLoader.loadClass("com.android.launcher3.LauncherModel")
            val itemInfoClass = classLoader.loadClass("com.android.launcher3.model.data.ItemInfo")

            // Hook LauncherModel的addOrMoveItemInDatabase方法
            val addOrMoveMethod = launcherModelClass.getDeclaredMethod(
                "addOrMoveItemInDatabase",
                itemInfoClass,
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType
            )
            hookWithId(addOrMoveMethod, "add_or_move") { chain ->
                val container = chain.args[1] as Int
                val screen = chain.args[2] as Int

                if (container == -101 && screen >= 5) {
                    logger.debug("数据库操作 - Hotseat位置: $screen")
                    // 允许操作继续
                }
                chain.proceed()
            }
        } catch (t: Throwable) {
            logger.error("Hook数据库操作失败", t)
        }
    }

    /**
     * Hook 9: 修改CellLayout相关方法
     */
    private fun hookCellLayoutMethods(classLoader: ClassLoader) {
        try {
            val cellLayoutClass = classLoader.loadClass("com.android.launcher3.CellLayout")

            // Hook CellLayout的findCellForSpan方法，使其总是能找到位置
            val findCellMethod = cellLayoutClass.getDeclaredMethod(
                "findCellForSpan",
                IntArray::class.java, Int::class.javaPrimitiveType, Int::class.javaPrimitiveType
            )
            hookWithId(findCellMethod, "find_cell") { chain ->
                val result = chain.proceed() as Boolean
                if (!result) {
                    // 如果原本找不到位置，强制返回true并设置坐标
                    val cellXY = chain.args[0] as IntArray
                    cellXY[0] = 0
                    cellXY[1] = 0
                    logger.debug("强制找到Cell位置")
                    return@hookWithId true
                }
                true
            }
        } catch (t: Throwable) {
            logger.error("Hook CellLayout失败", t)
        }
    }

    /**
     * 从离线索引读取 LoaderCursor 中签名 (ItemInfo)→boolean 的混淆方法名。
     * 索引缺失/失败时回退硬编码 "b"。
     */
    private fun findBMethodName(): String {
        return DexIndexStore.string(
            xposed,
            ScopeKeys.LAUNCHER.packageName,
            DexIndexConstants.ModuleKeys.ZUI_LAUNCHER_HOTSEAT,
            DexIndexConstants.Keys.LOADER_CURSOR_B_METHOD
        ) ?: "b"
    }
}
