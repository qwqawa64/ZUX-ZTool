package com.qimian233.ztool.hook.modules.launcher.dockbar

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.view.View
import com.qimian233.ztool.data.keys.ScopeKeys
import com.qimian233.ztool.data.keys.PreferenceKeys
import com.qimian233.ztool.dexindex.base.DexIndexConstants
import com.qimian233.ztool.hook.base.AppHookModule
import com.qimian233.ztool.hook.base.DexIndexStore
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.util.Collections

/**
 * ZUI Launcher hotseat extension hook module.
 * Removes the ZUI Launcher hotseat maximum count limit, allowing more apps
 * in the bottom quick bar.
 */
@SuppressLint("PrivateApi")
class ZuiLauncherHotseatHook : AppHookModule() {
    override fun getModuleName(): String = PreferenceKeys.ZUI_LAUNCHER_HOTSEAT.name

    override fun getTargetPackages(): Array<String> = arrayOf(ScopeKeys.LAUNCHER.packageName)

    /** Targets already reported as absent, so a hot-reload replay does not repeat the warn. */
    private val warnedTargets: MutableSet<String> = Collections.synchronizedSet(mutableSetOf<String>())

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
     * Hook 1: raise the hotseat item limit.
     *
     * The count moved to HotseatProfile#getNumShownIcons in the 18.6 profile split;
     * older builds still expose Hotseat#getMaxCount.
     */
    private fun hookHotseatMaxCount(classLoader: ClassLoader) {
        val profileClass = loadClassOrNull(classLoader, "com.android.launcher3.deviceprofile.HotseatProfile")
        val getNumShownIcons = profileClass?.let { tryGetDeclared(it, "getNumShownIcons") }
        if (getNumShownIcons != null) {
            hookWithId(getNumShownIcons, "hotseat_num_shown_icons") { chain ->
                chain.proceed()
                logger.debug("Changed hotseat visible icon count to $HOTSEAT_ITEM_LIMIT")
                HOTSEAT_ITEM_LIMIT
            }
            return
        }

        val hotseatClass = loadClassOrNull(classLoader, "com.android.launcher3.Hotseat")
        val getMaxCount = hotseatClass?.let { tryGetDeclared(it, "getMaxCount") }
        if (getMaxCount != null) {
            hookWithId(getMaxCount, "get_max_count") { chain ->
                chain.proceed()
                logger.debug("Changed hotseat max count to $HOTSEAT_ITEM_LIMIT")
                HOTSEAT_ITEM_LIMIT
            }
            return
        }

        warnMissing("Hotseat.getMaxCount / HotseatProfile.getNumShownIcons", "hotseat count limit")
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
     *
     * 18.6 added the icon count parameter to the recalculation entry point and dropped the
     * DeviceProfile field that used to carry it.
     */
    private fun hookDeviceProfile(classLoader: ClassLoader) {
        val deviceProfileClass = loadClassOrNull(classLoader, "com.android.launcher3.DeviceProfile")
        if (deviceProfileClass == null) {
            logger.error("Failed to hook DeviceProfile: class missing")
            return
        }

        // Hook DeviceProfile.getHotseatColumnSpan
        val getHotseatColumnSpanMethod = tryGetDeclared(deviceProfileClass, "getHotseatColumnSpan")
        if (getHotseatColumnSpanMethod != null) {
            hookWithId(getHotseatColumnSpanMethod, "get_hotseat_column_span") { chain ->
                chain.proceed()
                HOTSEAT_ITEM_LIMIT
            }
        } else {
            warnMissing("DeviceProfile.getHotseatColumnSpan", "hotseat column span")
        }

        val recalculateWithCount = tryGetDeclared(
            deviceProfileClass, "recalculateHotseatWidthAndBorderSpace",
            Int::class.javaPrimitiveType
        )
        if (recalculateWithCount != null) {
            hookWithId(recalculateWithCount, "recalculate") { chain ->
                logger.debug("Forced hotseat recalculation for $HOTSEAT_ITEM_LIMIT icons")
                chain.proceed(arrayOf<Any>(HOTSEAT_ITEM_LIMIT))
            }
            return
        }

        val recalculateMethod = tryGetDeclared(
            deviceProfileClass, "recalculateHotseatWidthAndBorderSpace"
        )
        if (recalculateMethod == null) {
            warnMissing("DeviceProfile.recalculateHotseatWidthAndBorderSpace", "hotseat recalculation")
            return
        }
        val numShownField = tryFieldOrNull(deviceProfileClass, "numShownHotseatIcons")
        hookWithId(recalculateMethod, "recalculate") { chain ->
            chain.proceed()
            val deviceProfile = chain.thisObject
            if (numShownField != null && deviceProfile != null) {
                try {
                    numShownField.set(deviceProfile, HOTSEAT_ITEM_LIMIT)
                } catch (t: Throwable) {
                    logger.warn("Failed to raise numShownHotseatIcons: ${t.javaClass.simpleName}")
                }
            }
            logger.debug("Modified DeviceProfile hotseat configuration")
            null
        }
    }

    /**
     * Hook 4: fixed add item methods.
     *
     * 18.6 removed the generic Launcher#addToWorkspace(ItemInfo, boolean) diagnostic target;
     * the add paths are observed through addPendingItem and completeAddShortcut instead.
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

                if (container == HOTSEAT_CONTAINER) {
                    logger.debug("Adding item to hotseat, bypassing restriction")
                }
                chain.proceed()
            }

            // Hook addToWorkspace (a more generic method); removed from Launcher in 18.6
            val itemInfoClass = loadClassOrNull(classLoader, "com.android.launcher3.model.data.ItemInfo")
            val addToWorkspaceMethod = itemInfoClass?.let {
                tryGetDeclared(
                    launcherClass, "addToWorkspace",
                    it, Boolean::class.javaPrimitiveType
                )
            }
            if (addToWorkspaceMethod == null) {
                warnMissing(
                    "Launcher.addToWorkspace(ItemInfo, boolean)",
                    "informational hotseat add log"
                )
            } else {
                hookWithId(addToWorkspaceMethod, "add_to_workspace") { chain ->
                    val container = readIntField(chain.args.getOrNull(0), "container")
                    if (container == HOTSEAT_CONTAINER) {
                        logger.debug("Added item to hotseat workspace")
                    }
                    chain.proceed()
                }
            }
        } catch (t: Throwable) {
            logger.error("Failed to hook add item methods", t)
        }
    }

    /**
     * Hook 5: modify the hotseat count limit at the database level.
     *
     * 18.6 dropped the getter; the count is a public instance field on
     * InvariantDeviceProfile, so it is written on the live profile object.
     */
    private fun hookDatabaseHotseatLimit(classLoader: ClassLoader) {
        val invProfileClass = loadClassOrNull(classLoader, "com.android.launcher3.InvariantDeviceProfile")
        if (invProfileClass == null) {
            logger.error("Failed to hook database hotseat limit: InvariantDeviceProfile missing")
            return
        }

        // Hook InvariantDeviceProfile.getNumDatabaseHotseatIcons (older builds)
        val getNumMethod = tryGetDeclared(invProfileClass, "getNumDatabaseHotseatIcons")
        if (getNumMethod != null) {
            hookWithId(getNumMethod, "get_num") { chain ->
                chain.proceed()
                logger.debug("Changed database hotseat count to $HOTSEAT_ITEM_LIMIT")
                HOTSEAT_ITEM_LIMIT
            }
        }

        val numField = tryFieldOrNull(invProfileClass, "numDatabaseHotseatIcons")
        if (numField == null) {
            warnMissing("InvariantDeviceProfile.numDatabaseHotseatIcons", "database hotseat limit")
            return
        }
        if (Modifier.isStatic(numField.modifiers)) {
            try {
                numField.setInt(null, HOTSEAT_ITEM_LIMIT)
                logger.debug("Directly set numDatabaseHotseatIcons to $HOTSEAT_ITEM_LIMIT")
            } catch (t: Throwable) {
                logger.error("Failed to directly set numDatabaseHotseatIcons", t)
            }
            return
        }

        val getDeviceProfile = tryGetDeclared(
            invProfileClass, "getDeviceProfile", Context::class.java
        )
        if (getDeviceProfile == null) {
            warnMissing("InvariantDeviceProfile.getDeviceProfile", "database hotseat limit")
            return
        }
        hookWithId(getDeviceProfile, "hotseat_database_count") { chain ->
            val invariantProfile = chain.thisObject
            if (invariantProfile != null) {
                try {
                    if (numField.getInt(invariantProfile) != HOTSEAT_ITEM_LIMIT) {
                        numField.setInt(invariantProfile, HOTSEAT_ITEM_LIMIT)
                        logger.debug("Raised database hotseat count to $HOTSEAT_ITEM_LIMIT")
                    }
                } catch (t: Throwable) {
                    logger.warn("Failed to write numDatabaseHotseatIcons: ${t.javaClass.simpleName}")
                }
            }
            chain.proceed()
        }
    }

    /**
     * Hook 6: modify LoaderCursor placement check logic.
     *
     * 18.6 dropped the `boolean` parameter of checkItemPlacement and added two parameters
     * to checkAndAddItem; both are resolved with the old signature as fallback.
     */
    private fun hookLoaderCursorMethods(classLoader: ClassLoader) {
        val loaderCursorClass = loadClassOrNull(classLoader, "com.android.launcher3.model.LoaderCursor")
        val itemInfoClass = loadClassOrNull(classLoader, "com.android.launcher3.model.data.ItemInfo")
        if (loaderCursorClass == null || itemInfoClass == null) {
            logger.error("Failed to hook LoaderCursor: class missing")
            return
        }

        // Hook checkItemPlacement to bypass the hotseat placement check
        val placementMethod = tryGetDeclared(loaderCursorClass, "checkItemPlacement", itemInfoClass)
            ?: tryGetDeclared(
                loaderCursorClass, "checkItemPlacement",
                itemInfoClass, Boolean::class.javaPrimitiveType
            )
        if (placementMethod == null) {
            warnMissing("LoaderCursor.checkItemPlacement", "hotseat placement check")
        } else {
            hookWithId(placementMethod, "check_item_placement") { chain ->
                val itemInfo = chain.args.getOrNull(0)
                val container = readIntField(itemInfo, "container")
                val screenId = readIntField(itemInfo, "screenId")

                // If it is the hotseat and the position is within the extended range, return true directly
                if (container == HOTSEAT_CONTAINER && screenId != null &&
                    screenId in 0 until HOTSEAT_ITEM_LIMIT
                ) {
                    logger.debug("Forced hotseat placement check pass: $screenId")
                    true
                } else {
                    chain.proceed()
                }
            }
        }

        // Hook the dimension check - method name comes from the offline index
        val dimensionCheck = resolveDimensionCheck(loaderCursorClass, itemInfoClass, placementMethod)
        if (dimensionCheck == null) {
            warnMissing("LoaderCursor dimension check", "hotseat dimension check")
        } else {
            hookWithId(dimensionCheck, "hook_289") { chain ->
                val result = chain.proceed()
                val container = readIntField(chain.args.getOrNull(0), "container")

                // If it is the hotseat, force return false (do not delete)
                if (container == HOTSEAT_CONTAINER) {
                    logger.debug("Bypassed hotseat dimension check")
                    false
                } else {
                    result
                }
            }
        }

        // Hook the checkAndAddItem method
        val checkAndAddItemMethod = resolveCheckAndAddItem(classLoader, loaderCursorClass, itemInfoClass)
        if (checkAndAddItemMethod == null) {
            warnMissing("LoaderCursor.checkAndAddItem", "hotseat add log")
        } else {
            hookWithId(checkAndAddItemMethod, "check_and_add_item") { chain ->
                val itemInfo = chain.args.getOrNull(0)
                val container = readIntField(itemInfo, "container")
                val screenId = readIntField(itemInfo, "screenId")

                if (container == HOTSEAT_CONTAINER) {
                    logger.debug("checkAndAddItem - hotseat position: $screenId")
                }
                chain.proceed()
            }
        }
    }

    /**
     * Hook 7: database operation hooks.
     *
     * 18.6 moved the write from LauncherModel to ModelWriter#addOrMoveItemInDatabase.
     */
    private fun hookDatabaseOperations(classLoader: ClassLoader) {
        val itemInfoClass = loadClassOrNull(classLoader, "com.android.launcher3.model.data.ItemInfo")
        if (itemInfoClass == null) {
            logger.error("Failed to hook database operations: ItemInfo missing")
            return
        }

        val launcherModelClass = loadClassOrNull(classLoader, "com.android.launcher3.LauncherModel")
        val legacyMethod = launcherModelClass?.let {
            tryGetDeclared(
                it, "addOrMoveItemInDatabase", itemInfoClass,
                Int::class.javaPrimitiveType, Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType, Int::class.javaPrimitiveType
            )
        }
        val writerClass = loadClassOrNull(classLoader, "com.android.launcher3.model.ModelWriter")
        val writerMethod = writerClass?.let {
            tryGetDeclared(
                it, "addOrMoveItemInDatabase", itemInfoClass,
                Int::class.javaPrimitiveType, Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType, Int::class.javaPrimitiveType
            )
        }
        val addOrMoveMethod = legacyMethod ?: writerMethod
        if (addOrMoveMethod == null) {
            warnMissing("ModelWriter.addOrMoveItemInDatabase", "hotseat database write log")
            return
        }

        hookWithId(addOrMoveMethod, "add_or_move") { chain ->
            val container = chain.args.getOrNull(1) as? Int
            val screen = chain.args.getOrNull(2) as? Int

            if (container == HOTSEAT_CONTAINER && screen != null && screen >= 5) {
                logger.debug("Database operation - hotseat position: $screen")
                // Allow the operation to continue
            }
            chain.proceed()
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
     * Resolves the obfuscated `(ItemInfo) -> boolean` dimension check in LoaderCursor.
     *
     * The offline index also matches checkItemPlacement on 18.6, so a name that points at
     * the placement check falls back to the single remaining candidate.
     */
    private fun resolveDimensionCheck(
        loaderCursorClass: Class<*>,
        itemInfoClass: Class<*>,
        placementMethod: Method?
    ): Method? {
        val indexed = try {
            loaderCursorClass.getDeclaredMethod(findBMethodName(), itemInfoClass)
        } catch (_: Throwable) {
            null
        }
        if (indexed != null && indexed != placementMethod) {
            indexed.isAccessible = true
            return indexed
        }
        val candidates = loaderCursorClass.declaredMethods.filter {
            it.parameterCount == 1 &&
                it.parameterTypes[0] == itemInfoClass &&
                it.returnType == Boolean::class.javaPrimitiveType &&
                it != placementMethod
        }
        if (candidates.size != 1) {
            logger.debug("Dimension check candidates: ${candidates.size}")
            return null
        }
        candidates[0].isAccessible = true
        return candidates[0]
    }

    /** Resolves checkAndAddItem for both the 18.6 four-parameter and the old two-parameter form. */
    private fun resolveCheckAndAddItem(
        classLoader: ClassLoader,
        loaderCursorClass: Class<*>,
        itemInfoClass: Class<*>
    ): Method? {
        val loadedItems = loadClassOrNull(classLoader, "com.android.launcher3.util.IntSparseArrayMap")
        val memoryLogger = loadClassOrNull(classLoader, "com.android.launcher3.model.LoaderMemoryLogger")
        val dataModel = loadClassOrNull(classLoader, "com.android.launcher3.model.BgDataModel")
        if (loadedItems != null && memoryLogger != null && dataModel != null) {
            tryGetDeclared(
                loaderCursorClass, "checkAndAddItem",
                itemInfoClass, loadedItems, memoryLogger, dataModel
            )?.let { return it }
        }
        return dataModel?.let {
            tryGetDeclared(loaderCursorClass, "checkAndAddItem", itemInfoClass, it)
        }
    }

    /** Reads a public ItemInfo field, tolerating a host that renamed it. */
    private fun readIntField(target: Any?, name: String): Int? {
        if (target == null) return null
        return try {
            val field: Field = target.javaClass.getField(name)
            field.getInt(target)
        } catch (_: Throwable) {
            null
        }
    }

    private fun loadClassOrNull(classLoader: ClassLoader, name: String): Class<*>? = try {
        classLoader.loadClass(name)
    } catch (_: Throwable) {
        null
    }

    private fun tryGetDeclared(owner: Class<*>, name: String, vararg params: Class<*>?): Method? = try {
        owner.getDeclaredMethod(name, *params).also { it.isAccessible = true }
    } catch (_: Throwable) {
        null
    }

    private fun tryFieldOrNull(owner: Class<*>, name: String): Field? = try {
        findField(owner, name)
    } catch (_: Throwable) {
        null
    }

    /** Warns once per process about a reflection target this launcher build no longer has. */
    private fun warnMissing(target: String, feature: String) {
        if (warnedTargets.add(target)) {
            logger.warn("$feature: $target is absent in this launcher build, skipped")
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

    private companion object {
        /** Dock slots the module extends to, whatever the stock grid option allows. */
        const val HOTSEAT_ITEM_LIMIT = 20

        /** LauncherSettings.Favorites.CONTAINER_HOTSEAT. */
        const val HOTSEAT_CONTAINER = -101
    }
}
