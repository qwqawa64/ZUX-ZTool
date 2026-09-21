package com.qimian233.ztool.hook.modules.launcher.grid

import android.content.Context
import android.content.ContextWrapper
import android.graphics.Point
import android.graphics.Rect
import android.view.View
import com.qimian233.ztool.data.keys.PreferenceKeys
import com.qimian233.ztool.data.keys.ScopeKeys
import com.qimian233.ztool.hook.base.AppHookModule
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam
import java.lang.reflect.Field
import java.lang.reflect.Method

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
 * The side inset is configured in dp and converted to px with the launcher
 * process's own density, matching the units its layout resources use.
 *
 * Square mode ([PreferenceKeys.LAUNCHER_WIDE_GRID_SQUARE]) instead computes the
 * side inset on-device so that cellWidth equals cellHeight, using the live
 * DeviceProfile. It stays correct with CustomGridSize columns (which run at
 * GridOption construction, before any setInsets pass). A negative result (columns
 * too many for square cells) clamps to 0.
 *
 * All reflection resolution happens once at install time ([handleLoadPackage]);
 * the chain callback only does field reads/writes and arithmetic, plus preference
 * lookups. Method and field names here survive obfuscation in the target launcher
 * build; missing members are logged instead of thrown so partially different ROM
 * builds degrade to a no-op rather than crashing the launcher. Requires a launcher
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
        // Square-mode fields: optional; missing any of them degrades to slider mode.
        val squareFields = resolveSquareFields(classLoader, dpClass)

        val resolver = ProfileResolver(getDeviceProfile, activityContextClass, marginField, cellPaddingField)

        // Re-entrancy guard: we invoke setInsets ourselves after rewriting the profile;
        // only the outermost invocation applies the rewrite.
        val inRewrite = ThreadLocal.withInitial { false }

        hookWithId(setInsets, "workspace_grid_margins_rewrite") { chain ->
            chain.proceed()
            if (inRewrite.get()) return@hookWithId null
            inRewrite.set(true)
            try {
                val workspace = chain.thisObject as? View
                if (workspace == null) {
                    logger.info("WideGrid: thisObject is not a View")
                    return@hookWithId null
                }
                val dp = resolver.resolve(workspace.context)
                if (dp == null) {
                    logger.info("WideGrid: DeviceProfile not reachable from context")
                    return@hookWithId null
                }
                val prefs = remotePreferences
                val squareMode = squareFields != null && prefs.getBoolean(
                    PreferenceKeys.LAUNCHER_WIDE_GRID_SQUARE.name,
                    PreferenceKeys.LAUNCHER_WIDE_GRID_SQUARE.default
                )
                val sideInsetPx = if (squareMode) {
                    computeSquareInset(dp, squareFields, resolver.margin)
                } else {
                    val insetDp = prefs.getInt(
                        PreferenceKeys.LAUNCHER_WIDE_GRID_SIDE_INSET.name,
                        PreferenceKeys.LAUNCHER_WIDE_GRID_SIDE_INSET.default
                    )
                    Math.round(insetDp * workspace.resources.displayMetrics.density)
                }
                applySideInset(dp, sideInsetPx, resolver)
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

    /**
     * Resolves every field square mode needs, once at install time. Returns null if
     * any is missing — square mode is then disabled and the slider path is used.
     */
    private fun resolveSquareFields(classLoader: ClassLoader, dpClass: Class<*>): SquareFields? {
        val cellWidth = tryField(dpClass, "cellWidthPx") ?: return null
        val cellHeight = tryField(dpClass, "cellHeightPx") ?: return null
        val borderSpace = tryField(dpClass, "cellLayoutBorderSpacePx") ?: return null
        val inv = tryField(dpClass, "inv") ?: return null
        val invClass = try {
            classLoader.loadClass("com.android.launcher3.InvariantDeviceProfile")
        } catch (th: Throwable) {
            logger.error("WideGrid: InvariantDeviceProfile not found, square mode disabled", th)
            return null
        }
        val numColumns = tryField(invClass, "numColumns") ?: return null
        return SquareFields(cellWidth, cellHeight, borderSpace, inv, numColumns)
    }

    private fun tryField(owner: Class<*>, name: String): Field? = try {
        findField(owner, name)
    } catch (th: Throwable) {
        logger.error("WideGrid: field '$name' missing, square mode disabled", th)
        null
    }

    /**
     * Solves the absolute per-side padding so cellWidth == cellHeight.
     *
     * The launcher computes cellWidth from the available width minus total side
     * padding: contentWidth = availableWidth - 2*side, and
     * cellWidth = (contentWidth - borderX*(columns-1)) / columns. Reading the
     * current side padding from the margin field lets us reconstruct
     * availableWidth = columns*cellWidth + borderX*(columns-1) + 2*side, then
     * solve for the target side with cellWidth' = cellHeight:
     * side' = (availableWidth - columns*cellHeight - borderX*(columns-1)) / 2.
     */
    private fun computeSquareInset(dp: Any, fields: SquareFields, margin: Field): Int {
        val currentSide = margin.getInt(dp)
        val cellWidth = fields.cellWidth.getInt(dp)
        val cellHeight = fields.cellHeight.getInt(dp)
        val borderSpace = fields.borderSpace.get(dp) as Point
        val columns = fields.numColumns.getInt(fields.inv.get(dp))
        if (columns <= 0 || cellWidth <= 0 || cellHeight <= 0) {
            logger.info("WideGrid: square mode skipped, bad metrics w=$cellWidth h=$cellHeight cols=$columns")
            return currentSide
        }
        val borderTotal = borderSpace.x * (columns - 1)
        val availableWidth = columns * cellWidth + borderTotal + 2 * currentSide
        val targetSide = ((availableWidth - columns * cellHeight - borderTotal) / 2).coerceAtLeast(0)
        logger.info(
            "WideGrid: square mode cellW=$cellWidth cellH=$cellHeight cols=$columns " +
                "borderX=${borderSpace.x} availW=$availableWidth side $currentSide->$targetSide" +
                (if (targetSide == 0 && availableWidth - columns * cellHeight - borderTotal < 0) " (clamped)" else "")
        )
        return targetSide
    }

    private fun applySideInset(dp: Any, sideInsetPx: Int, resolver: ProfileResolver) {
        val beforeMargin = resolver.margin.getInt(dp)
        resolver.margin.setInt(dp, sideInsetPx)
        val cellPadding = resolver.cellPadding.get(dp) as Rect
        val before = "L${cellPadding.left} T${cellPadding.top} R${cellPadding.right} B${cellPadding.bottom}"
        cellPadding.left = sideInsetPx
        cellPadding.right = sideInsetPx
        logger.info(
            "WideGrid: applied sideInsetPx=$sideInsetPx " +
                "marginPx $beforeMargin->$sideInsetPx cellLayoutPadding $before->" +
                "L${cellPadding.left} T${cellPadding.top} R${cellPadding.right} B${cellPadding.bottom}"
        )
    }

    /** Field bundle resolved at install time for square mode. */
    private class SquareFields(
        val cellWidth: Field,
        val cellHeight: Field,
        val borderSpace: Field,
        val inv: Field,
        val numColumns: Field
    )

    /** Reflection entry points resolved once at install time. */
    private class ProfileResolver(
        val getDeviceProfile: Method,
        val activityContextClass: Class<*>,
        val margin: Field,
        val cellPadding: Field
    ) {
        fun resolve(context: Context): Any? {
            if (activityContextClass.isInstance(context)) return getDeviceProfile.invoke(context)
            // DragLayer-wrapped contexts etc.: walk up to the base context
            var ctx: Context? = context
            while (ctx is ContextWrapper && !activityContextClass.isInstance(ctx)) {
                ctx = ctx.baseContext
            }
            return if (activityContextClass.isInstance(ctx)) getDeviceProfile.invoke(ctx) else null
        }
    }
}
