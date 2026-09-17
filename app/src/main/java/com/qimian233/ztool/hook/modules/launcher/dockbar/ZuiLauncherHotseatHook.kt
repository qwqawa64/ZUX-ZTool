package com.qimian233.ztool.hook.modules.launcher.dockbar

import android.annotation.SuppressLint
import android.content.Intent
import android.view.View
import com.qimian233.ztool.data.keys.ScopeKeys
import com.qimian233.ztool.data.keys.PreferenceKeys
import com.qimian233.ztool.dexindex.base.DexIndexConstants
import com.qimian233.ztool.hook.base.AppHookModule
import com.qimian233.ztool.hook.base.DexIndexStore
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam

/**
 * ZUI Launcher hotseat extension hook module.
 * Removes the ZUI Launcher hotseat maximum count limit, allowing more apps
 * in the bottom quick bar.
 */
@SuppressLint("PrivateApi")
class ZuiLauncherHotseatHook : AppHookModule() {
    override fun getModuleName(): String = PreferenceKeys.ZUI_LAUNCHER_HOTSEAT.name

    override fun getTargetPackages(): Array<String> = arrayOf(ScopeKeys.LAUNCHER.packageName)

    @Throws(Throwable::class)
    override fun handleLoadPackage(param: PackageLoadedParam) {
        val classLoader = param.defaultClassLoader

        // Keep avoidance logic at the hook layer so the repository stays clean
        val disableDockBar: Boolean = try {
            remotePreferences.getBoolean(PreferenceKeys.DISABLE_DOCK_BAR.name, false)
        } catch (_: Throwable) {
            false
        }
        if (disableDockBar) {
            logger.warn("Disable dock bar hook enabled, will not expand dock bar.")
            return
        }

        logger.info("Hooking ZUI Launcher hotseat limit")

        try {
            // Hook 1: bypass the hotseat max count check
            hookHotseatMaxCount(classLoader)

            // Hook 2: bypass space checks
            hookSpaceChecks(classLoader)

            // Hook 3: modify DeviceProfile configuration
            hookDeviceProfile(classLoader)

            // Hook 4: fixed add item methods
            hookAddItemMethods(classLoader)

            // Hook 5: modify the hotseat limit at the database level
            hookDatabaseHotseatLimit(classLoader)

            // Hook 6: modify LoaderCursor placement check logic
            hookLoaderCursorMethods(classLoader)

            // Hook 7: database operation hooks
            hookDatabaseOperations(classLoader)

            // Hook 9: CellLayout related methods
            hookCellLayoutMethods(classLoader)

            logger.info("ZUI Launcher hotseat hooks completed")
        } catch (t: Throwable) {
            logger.error("Error while hooking ZUI Launcher", t)
        }
    }

    /**
     * Hook 1: modify the hotseat max count limit.
     */
    private fun hookHotseatMaxCount(classLoader: ClassLoader) {
        try {
            val hotseatClass = classLoader.loadClass("com.android.launcher3.Hotseat")
            val getMaxCountMethod = hotseatClass.getDeclaredMethod("getMaxCount")
            hookWithId(getMaxCountMethod, "get_max_count") { chain ->
                chain.proceed()
                logger.debug("Changed hotseat max count to 20")
                20
            }
        } catch (t: Throwable) {
            logger.error("Failed to hook getMaxCount", t)
        }
    }

    /**
     * Hook 2: bypass various space check methods.
     */
    private fun hookSpaceChecks(classLoader: ClassLoader) {
        try {
            val launcherClass = classLoader.loadClass("com.android.launcher3.Launcher")

            // Hook Launcher.showOutOfSpaceMessage to suppress the out-of-space message
            val showOutOfSpaceMethod = findMethod(launcherClass, "showOutOfSpaceMessage",
                Boolean::class.javaPrimitiveType
            )
            hookWithId(showOutOfSpaceMethod, "show_out_of_space") {
                logger.debug("Suppressed out-of-space message")
                null
            }

            // Hook checkOccupiedShortcut so it always returns true (placement allowed)
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
                logger.debug("Forced space check pass")
                true
            }
        } catch (t: Throwable) {
            logger.error("Failed to hook space checks", t)
        }
    }

    /**
     * Hook 3: modify DeviceProfile configuration.
     */
    private fun hookDeviceProfile(classLoader: ClassLoader) {
        try {
            val deviceProfileClass = classLoader.loadClass("com.android.launcher3.DeviceProfile")

            // Hook DeviceProfile.getHotseatColumnSpan
            val getHotseatColumnSpanMethod =
                deviceProfileClass.getDeclaredMethod("getHotseatColumnSpan")
            hookWithId(getHotseatColumnSpanMethod, "get_hotseat_column_span") { chain ->
                chain.proceed()
                20
            }

            // Hook the recalculateHotseatWidthAndBorderSpace method
            val recalculateMethod =
                deviceProfileClass.getDeclaredMethod("recalculateHotseatWidthAndBorderSpace")
            hookWithId(recalculateMethod, "recalculate") { chain ->
                chain.proceed()
                val deviceProfile = chain.thisObject
                // Force numShownHotseatIcons to 20
                val numShownField = findField(deviceProfileClass, "numShownHotseatIcons")
                numShownField.set(deviceProfile, 20)
                logger.debug("Modified DeviceProfile hotseat configuration")
                null
            }
        } catch (t: Throwable) {
            logger.error("Failed to hook DeviceProfile", t)
        }
    }

    /**
     * Hook 4: fixed add item methods.
     */
    private fun hookAddItemMethods(classLoader: ClassLoader) {
        try {
            val launcherClass = classLoader.loadClass("com.android.launcher3.Launcher")
            val pendingRequestArgsClass =
                classLoader.loadClass("com.android.launcher3.util.PendingRequestArgs")
            val pendingAddItemInfoClass =
                classLoader.loadClass("com.android.launcher3.PendingAddItemInfo")

            // Hook completeAddShortcut to bypass add restrictions
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
                logger.debug("Preparing to add shortcut to hotseat")
                chain.proceed()
            }

            // Hook the addPendingItem method
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
                // Ensure adding items is not restricted
                val container = chain.args[1] as Int

                if (container == -101) { // -101 is the hotseat container ID
                    logger.debug("Adding item to hotseat, bypassing restriction")
                }
                chain.proceed()
            }

            // Hook addToWorkspace (a more generic method)
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
                        logger.debug("Added item to hotseat workspace")
                    }
                    chain.proceed()
                }
            } catch (t: Throwable) {
                logger.error("Failed to hook addToWorkspace", t)
            }
        } catch (t: Throwable) {
            logger.error("Failed to hook add item methods", t)
        }
    }

    /**
     * Hook 5: modify the hotseat count limit at the database level.
     */
    private fun hookDatabaseHotseatLimit(classLoader: ClassLoader) {
        try {
            val invProfileClass =
                classLoader.loadClass("com.android.launcher3.InvariantDeviceProfile")

            // Hook InvariantDeviceProfile.getNumDatabaseHotseatIcons
            val getNumMethod = invProfileClass.getDeclaredMethod("getNumDatabaseHotseatIcons")
            hookWithId(getNumMethod, "get_num") { chain ->
                chain.proceed()
                logger.debug("Changed database hotseat count to 20")
                20
            }

            // Directly modify the numDatabaseHotseatIcons field (fallback)
            try {
                val numField = findField(invProfileClass, "numDatabaseHotseatIcons")
                numField.set(null, 20)
                logger.debug("Directly set numDatabaseHotseatIcons to 20")
            } catch (t: Throwable) {
                logger.error("Failed to directly set numDatabaseHotseatIcons", t)
            }
        } catch (t: Throwable) {
            logger.error("Failed to hook database hotseat limit", t)
        }
    }

    /**
     * Hook 6: modify LoaderCursor placement check logic.
     */
    private fun hookLoaderCursorMethods(classLoader: ClassLoader) {
        try {
            val loaderCursorClass =
                classLoader.loadClass("com.android.launcher3.model.LoaderCursor")
            val itemInfoClass = classLoader.loadClass("com.android.launcher3.model.data.ItemInfo")
            val bgDataModelClass = classLoader.loadClass("com.android.launcher3.model.BgDataModel")

            // Hook checkItemPlacement to bypass the hotseat placement check
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

                // If it is the hotseat and the position is within the extended range, return true directly
                if (container == -101 && screenId >= 0 && screenId < 20) {
                    logger.debug("Forced hotseat placement check pass: $screenId")
                    return@hookWithId true
                }
                chain.proceed()
            }

            // Hook the b method (dimension check) - method name comes from the offline index
            val bMethodName = findBMethodName()
            val bMethod = loaderCursorClass.getDeclaredMethod(bMethodName, itemInfoClass)
            hookWithId(bMethod, "hook_289") { chain ->
                val result = chain.proceed()
                val itemInfo = chain.args[0]
                val containerField = findField(itemInfo.javaClass, "container")
                val container = containerField.getInt(itemInfo)

                // If it is the hotseat, force return false (do not delete)
                if (container == -101) {
                    logger.debug("Bypassed hotseat dimension check")
                    return@hookWithId false
                }
                result
            }

            // Hook the checkAndAddItem method
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
                    logger.debug("checkAndAddItem - hotseat position: $screenId")
                }
                chain.proceed()
            }
        } catch (t: Throwable) {
            logger.error("Failed to hook LoaderCursor", t)
        }
    }

    /**
     * Hook 7: database operation hooks.
     */
    private fun hookDatabaseOperations(classLoader: ClassLoader) {
        try {
            val launcherModelClass = classLoader.loadClass("com.android.launcher3.LauncherModel")
            val itemInfoClass = classLoader.loadClass("com.android.launcher3.model.data.ItemInfo")

            // Hook LauncherModel.addOrMoveItemInDatabase
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
                    logger.debug("Database operation - hotseat position: $screen")
                    // Allow the operation to continue
                }
                chain.proceed()
            }
        } catch (t: Throwable) {
            logger.error("Failed to hook database operations", t)
        }
    }

    /**
     * Hook 9: modify CellLayout related methods.
     */
    private fun hookCellLayoutMethods(classLoader: ClassLoader) {
        try {
            val cellLayoutClass = classLoader.loadClass("com.android.launcher3.CellLayout")

            // Hook CellLayout.findCellForSpan so it can always find a cell
            val findCellMethod = cellLayoutClass.getDeclaredMethod(
                "findCellForSpan",
                IntArray::class.java, Int::class.javaPrimitiveType, Int::class.javaPrimitiveType
            )
            hookWithId(findCellMethod, "find_cell") { chain ->
                val result = chain.proceed() as Boolean
                if (!result) {
                    // If no cell was found originally, force return true and set coordinates
                    val cellXY = chain.args[0] as IntArray
                    cellXY[0] = 0
                    cellXY[1] = 0
                    logger.debug("Forced cell position found")
                    return@hookWithId true
                }
                true
            }
        } catch (t: Throwable) {
            logger.error("Failed to hook CellLayout", t)
        }
    }

    /**
     * Read from the offline index the obfuscated method name in LoaderCursor with
     * signature (ItemInfo)→boolean. Falls back to the hardcoded "b" when the
     * index is missing or the lookup fails.
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
