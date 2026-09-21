package com.qimian233.ztool.hook.modules.launcher.grid

import android.graphics.Rect
import com.qimian233.ztool.data.keys.ScopeKeys
import com.qimian233.ztool.hook.base.AppHookModule
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam

/**
 * Test hook ("test_hook"): widens the launcher workspace grid towards the left and
 * right screen edges (bottom is reserved for the Dock and untouched).
 *
 * Measured on-device (via the dump phase of this hook): outer workspace side margins
 * are 40px and the ScalableGrid inner padding is 216px per side — together ~256px of
 * dead space per edge. This hook zeroes both after [com.android.launcher3.Workspace]
 * applies its insets, then re-runs setInsets so the new padding propagates through
 * setPadding/requestLayout and CellLayout recomputes cellWidth from the wider grid
 * (cellWidth grows automatically; icon size is independent).
 *
 * Field/method names here survive obfuscation in the target launcher build; missing
 * members are logged instead of thrown so partially different ROM builds degrade to
 * a no-op dump rather than crashing the launcher.
 *
 * Module name "test_hook" auto-enables this hook without a frontend switch.
 */
class WorkspaceGridMarginsDumpHook : AppHookModule() {

    companion object {
        // Target inner left/right padding. 0 = flush against the screen edge;
        // raise here if icons collide with curved/cornered screen edges.
        private const val TARGET_SIDE_INSET = 0
    }

    override fun getModuleName(): String = "test_hook"

    override fun getTargetPackages(): Array<String> = arrayOf(ScopeKeys.LAUNCHER.packageName)

    override fun handleLoadPackage(param: PackageLoadedParam) {
        val classLoader = param.defaultClassLoader
        val workspaceClass = try {
            classLoader.loadClass("com.android.launcher3.Workspace")
        } catch (th: Throwable) {
            logger.error("MarginsHook: Workspace class not found, aborting", th)
            return
        }
        val dpClass = try {
            classLoader.loadClass("com.android.launcher3.DeviceProfile")
        } catch (th: Throwable) {
            logger.error("MarginsHook: DeviceProfile class not found, aborting", th)
            return
        }

        val setInsets = try {
            findMethod(workspaceClass, "setInsets", Rect::class.java)
        } catch (th: Throwable) {
            logger.error("MarginsHook: Workspace#setInsets(Rect) not found, aborting", th)
            return
        }
        val activityContextClass = try {
            classLoader.loadClass("com.android.launcher3.views.ActivityContext")
        } catch (th: Throwable) {
            logger.error("MarginsHook: ActivityContext class not found, aborting", th)
            return
        }
        // ActivityContext#getDeviceProfile() is an interface method; the interface name
        // survives obfuscation while the DeviceProfile field on PagedView does not.
        val getDeviceProfile = try {
            activityContextClass.getMethod("getDeviceProfile")
        } catch (th: Throwable) {
            logger.error("MarginsHook: ActivityContext#getDeviceProfile not found, aborting", th)
            return
        }

        val marginField = try {
            findField(dpClass, "desiredWorkspaceHorizontalMarginPx")
        } catch (th: Throwable) {
            logger.error("MarginsHook: desiredWorkspaceHorizontalMarginPx missing, aborting", th)
            return
        }
        val cellPaddingField = try {
            findField(dpClass, "cellLayoutPaddingPx")
        } catch (th: Throwable) {
            logger.error("MarginsHook: cellLayoutPaddingPx missing, aborting", th)
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
                    logger.info("MarginsHook: thisObject is not a View")
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
                    logger.info("MarginsHook: context is not ActivityContext, DeviceProfile not reachable")
                    return@hookWithId null
                }
                rewriteProfile(dpClass, dp, marginField, cellPaddingField)
                // Re-run with the rewritten profile so setPadding/requestLayout picks
                // up the new values (guarded by inRewrite to avoid recursion).
                setInsets.invoke(workspace, chain.args[0])
            } catch (th: Throwable) {
                logger.error("MarginsHook: rewrite failed", th)
            } finally {
                inRewrite.set(false)
            }
            null
        }
        logger.info("WorkspaceGridMarginsDumpHook installed (hooking Workspace#setInsets)")
    }

    private fun rewriteProfile(
        dpClass: Class<*>,
        dp: Any,
        marginField: java.lang.reflect.Field,
        cellPaddingField: java.lang.reflect.Field
    ) {
        val beforeMargin = marginField.getInt(dp)
        marginField.setInt(dp, TARGET_SIDE_INSET)
        val cellPadding = cellPaddingField.get(dp) as Rect
        val before = "L${cellPadding.left} T${cellPadding.top} R${cellPadding.right} B${cellPadding.bottom}"
        cellPadding.left = TARGET_SIDE_INSET
        cellPadding.right = TARGET_SIDE_INSET
        logger.info(
            "MarginsHook: applied sideInset=$TARGET_SIDE_INSET " +
                "marginPx $beforeMargin->$TARGET_SIDE_INSET cellLayoutPadding $before->" +
                "L${cellPadding.left} T${cellPadding.top} R${cellPadding.right} B${cellPadding.bottom} " +
                "(top/bottom untouched to protect search bar and dock)"
        )
    }
}
