package com.qimian233.ztool.hook.modules.launcher.grid

import android.graphics.Rect
import com.qimian233.ztool.data.keys.ScopeKeys
import com.qimian233.ztool.hook.base.AppHookModule
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam

/**
 * Test hook ("test_hook"): dumps Workspace grid margin fields from the launcher's
 * DeviceProfile so the real on-device values can be measured before writing the
 * actual workspace-margin hook.
 *
 * Dumps on every [com.android.launcher3.Workspace.setInsets] call, which runs at
 * Launcher startup and on every rotation/configuration change:
 * - DeviceProfile.desiredWorkspaceHorizontalMarginPx / ...OriginalPx
 * - DeviceProfile.workspacePadding (left/top/right/bottom)
 * - DeviceProfile.cellLayoutPaddingPx
 * - DeviceProfile.edgeMarginPx, cellWidthPx, availableWidthPx
 *
 * Method and field names here survive obfuscation in the target launcher build;
 * a missing member is logged instead of thrown, so partially different ROM builds
 * still dump whatever they can.
 *
 * Module name "test_hook" auto-enables this hook without a frontend switch.
 */
class WorkspaceGridMarginsDumpHook : AppHookModule() {

    override fun getModuleName(): String = "test_hook"

    override fun getTargetPackages(): Array<String> = arrayOf(ScopeKeys.LAUNCHER.packageName)

    override fun handleLoadPackage(param: PackageLoadedParam) {
        val classLoader = param.defaultClassLoader
        val workspaceClass = try {
            classLoader.loadClass("com.android.launcher3.Workspace")
        } catch (th: Throwable) {
            logger.error("DumpHook: Workspace class not found, aborting", th)
            return
        }
        val dpClass = try {
            classLoader.loadClass("com.android.launcher3.DeviceProfile")
        } catch (th: Throwable) {
            logger.error("DumpHook: DeviceProfile class not found, aborting", th)
            return
        }

        val setInsets = try {
            findMethod(workspaceClass, "setInsets", Rect::class.java)
        } catch (th: Throwable) {
            logger.error("DumpHook: Workspace#setInsets(Rect) not found, aborting", th)
            return
        }
        val dpField = try {
            findField(workspaceClass, "mDeviceProfile")
        } catch (th: Throwable) {
            null // PagedView's profile field may be renamed; fall back to the arg chain below
        }

        hookWithId(setInsets, "workspace_grid_margins_dump") { chain ->
            chain.proceed()
            try {
                // DeviceProfile: prefer the field, otherwise resolve from thisObject's
                // hierarchy at dump time.
                val dp: Any? = try {
                    dpField?.get(chain.thisObject)
                } catch (_: Throwable) {
                    null
                } ?: findDeviceProfile(chain.thisObject)
                if (dp == null) {
                    logger.info("DumpHook: DeviceProfile instance not reachable on this call")
                    return@hookWithId null
                }
                dumpDeviceProfile(dpClass, dp)
            } catch (th: Throwable) {
                logger.error("DumpHook: dump failed", th)
            }
            null
        }
        logger.info("WorkspaceGridMarginsDumpHook installed (hooking Workspace#setInsets)")
    }

    private fun findDeviceProfile(workspace: Any?): Any? {
        var clazz: Class<*>? = workspace?.javaClass
        while (clazz != null) {
            for (field in clazz.declaredFields) {
                if (field.type.name == "com.android.launcher3.DeviceProfile") {
                    field.isAccessible = true
                    return field.get(workspace)
                }
            }
            clazz = clazz.superclass
        }
        return null
    }

    private fun dumpDeviceProfile(dpClass: Class<*>, dp: Any) {
        fun intField(name: String): Int? = try {
            @Suppress("UNCHECKED_CAST")
            findField(dpClass, name).getInt(dp)
        } catch (th: Throwable) {
            logger.error("DumpHook: field '$name' missing", th)
            null
        }

        fun rectField(name: String): String? = try {
            val rect = findField(dpClass, name).get(dp) as? Rect
            rect?.let { "L${it.left} T${it.top} R${it.right} B${it.bottom}" }
        } catch (th: Throwable) {
            logger.error("DumpHook: rect field '$name' missing", th)
            null
        }

        logger.info(
            "DumpHook: desiredWorkspaceHorizontalMarginPx=${intField("desiredWorkspaceHorizontalMarginPx")} " +
                "desiredWorkspaceHorizontalMarginOriginalPx=${intField("desiredWorkspaceHorizontalMarginOriginalPx")} " +
                "edgeMarginPx=${intField("edgeMarginPx")}"
        )
        logger.info(
            "DumpHook: workspacePadding=${rectField("workspacePadding")} " +
                "cellLayoutPaddingPx=${rectField("cellLayoutPaddingPx")}"
        )
        logger.info(
            "DumpHook: cellWidthPx=${intField("cellWidthPx")} cellHeightPx=${intField("cellHeightPx")} " +
                "cellLayoutBorderSpacePx=${try {
                    findField(dpClass, "cellLayoutBorderSpacePx").get(dp).toString()
                } catch (_: Throwable) { "?" }} " +
                "availableWidthPx=${intField("availableWidthPx")}"
        )
    }
}
