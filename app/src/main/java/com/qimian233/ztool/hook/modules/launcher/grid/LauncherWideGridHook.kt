package com.qimian233.ztool.hook.modules.launcher.grid

import android.graphics.Rect
import com.qimian233.ztool.data.keys.PreferenceKeys
import com.qimian233.ztool.data.keys.ScopeKeys
import com.qimian233.ztool.hook.base.AppHookModule
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam

/**
 * Widens the launcher workspace grid towards the left and right screen edges
 * (bottom is reserved for the Dock and untouched, top search bar area untouched).
 *
 * Measured on-device: outer workspace side margins are 40px and the ScalableGrid
 * inner padding is 216px per side — together ~256px of dead space per edge. When
 * enabled, this hook rewrites both to the user-configured side inset after
 * [com.android.launcher3.Workspace] applies its insets, then re-runs setInsets so
 * the new padding propagates through setPadding/requestLayout and CellLayout
 * recomputes cellWidth from the wider grid (cellWidth grows automatically; icon
 * size is independent).
 *
 * Method and field names here survive obfuscation in the target launcher build;
 * missing members are logged instead of thrown so partially different ROM builds
 * degrade to a no-op rather than crashing the launcher. Requires a launcher
 * restart to take effect after changing settings.
 */
class LauncherWideGridHook : AppHookModule() {

    override fun getModuleName(): String = PreferenceKeys.LAUNCHER_WIDE_GRID.name

    override fun getTargetPackages(): Array<String> = arrayOf(ScopeKeys.LAUNCHER.packageName)

    override fun handleLoadPackage(param: PackageLoadedParam) {
        val classLoader = param.defaultClassLoader
        val workspaceClass = try {
            classLoader.loadClass("com.android.launcher3.Workspace")
        } catch (th: Throwable) {
            logger.error("WideGrid: Workspace class not found, aborting", th)
            return
        }
        val dpClass = try {
            classLoader.loadClass("com.android.launcher3.DeviceProfile")
        } catch (th: Throwable) {
            logger.error("WideGrid: DeviceProfile class not found, aborting", th)
            return
        }

        val setInsets = try {
            findMethod(workspaceClass, "setInsets", Rect::class.java)
        } catch (th: Throwable) {
            logger.error("WideGrid: Workspace#setInsets(Rect) not found, aborting", th)
            return
        }
        val activityContextClass = try {
            classLoader.loadClass("com.android.launcher3.views.ActivityContext")
        } catch (th: Throwable) {
            logger.error("WideGrid: ActivityContext class not found, aborting", th)
            return
        }
        // ActivityContext#getDeviceProfile() is an interface method; the interface name
        // survives obfuscation while the DeviceProfile field on PagedView does not.
        val getDeviceProfile = try {
            activityContextClass.getMethod("getDeviceProfile")
        } catch (th: Throwable) {
            logger.error("WideGrid: ActivityContext#getDeviceProfile not found, aborting", th)
            return
        }

        val marginField = try {
            findField(dpClass, "desiredWorkspaceHorizontalMarginPx")
        } catch (th: Throwable) {
            logger.error("WideGrid: desiredWorkspaceHorizontalMarginPx missing, aborting", th)
            return
        }
        val cellPaddingField = try {
            findField(dpClass, "cellLayoutPaddingPx")
        } catch (th: Throwable) {
            logger.error("WideGrid: cellLayoutPaddingPx missing, aborting", th)
            return
        }

        // Re-entrancy guard: we invoke setInsets ourselves after rewriting the profile;
        // only the outermost invocation applies the rewrite.
        val inRewrite = ThreadLocal.withInitial { false }

        hookWithId(setInsets, "workspace_grid_margins_rewrite") { chain ->
            chain.proceed()
            if (inRewrite.get()) return@hookWithId null
            inRewrite.set(true)
            try {
                val workspace = chain.thisObject as? android.view.View
                if (workspace == null) {
                    logger.info("WideGrid: thisObject is not a View")
                    return@hookWithId null
                }
                val context = workspace.context
                val dp = if (activityContextClass.isInstance(context)) {
                    getDeviceProfile.invoke(context)
                } else {
                    // DragLayer-wrapped contexts etc.: walk up to the base context
                    var ctx: android.content.Context? = context
                    while (ctx is android.content.ContextWrapper && !activityContextClass.isInstance(ctx)) {
                        ctx = ctx.baseContext
                    }
                    if (activityContextClass.isInstance(ctx)) getDeviceProfile.invoke(ctx) else null
                }
                if (dp == null) {
                    logger.info("WideGrid: context is not ActivityContext, DeviceProfile not reachable")
                    return@hookWithId null
                }
                val sideInset = remotePreferences.getInt(
                    PreferenceKeys.LAUNCHER_WIDE_GRID_SIDE_INSET.name,
                    PreferenceKeys.LAUNCHER_WIDE_GRID_SIDE_INSET.default
                )
                rewriteProfile(dp, sideInset, marginField, cellPaddingField)
                // Re-run with the rewritten profile so setPadding/requestLayout picks
                // up the new values (guarded by inRewrite to avoid recursion).
                setInsets.invoke(workspace, chain.args[0])
            } catch (th: Throwable) {
                logger.error("WideGrid: rewrite failed", th)
            } finally {
                inRewrite.set(false)
            }
            null
        }
        logger.info("LauncherWideGridHook installed (hooking Workspace#setInsets)")
    }

    private fun rewriteProfile(
        dp: Any,
        sideInset: Int,
        marginField: java.lang.reflect.Field,
        cellPaddingField: java.lang.reflect.Field
    ) {
        val beforeMargin = marginField.getInt(dp)
        marginField.setInt(dp, sideInset)
        val cellPadding = cellPaddingField.get(dp) as Rect
        val before = "L${cellPadding.left} T${cellPadding.top} R${cellPadding.right} B${cellPadding.bottom}"
        cellPadding.left = sideInset
        cellPadding.right = sideInset
        logger.info(
            "WideGrid: applied sideInset=$sideInset " +
                "marginPx $beforeMargin->$sideInset cellLayoutPadding $before->" +
                "L${cellPadding.left} T${cellPadding.top} R${cellPadding.right} B${cellPadding.bottom}"
        )
    }
}
