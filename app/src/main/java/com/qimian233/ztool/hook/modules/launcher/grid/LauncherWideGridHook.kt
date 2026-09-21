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
 * The side inset is configured in dp and converted to px with the launcher
 * process's own density, matching the units its layout resources use.
 *
 * Square mode ([PreferenceKeys.LAUNCHER_WIDE_GRID_SQUARE]) instead computes the
 * side inset on-device so that cellWidth equals cellHeight:
 *   cellWidth = (availableWidth - 2*sidePadding) / columns   (borderSpace.x is 0 on
 *   the measured build; included in the formula for correctness)
 *   => sidePadding = (availableWidth - columns*(cellHeight + borderSpace.x) + borderSpace.x) / 2
 * using the live DeviceProfile, so it stays correct with CustomGridSize columns
 * (which runs at GridOption construction, before any setInsets pass). A negative
 * result (columns too many for square cells) clamps to 0.
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
        // Used by square mode: cellWidthPx/cellHeightPx/cellLayoutBorderSpacePx plus
        // the column count via the InvariantDeviceProfile reference on DeviceProfile.
        val cellWidthField = try {
            findField(dpClass, "cellWidthPx")
        } catch (th: Throwable) {
            logger.error("WideGrid: cellWidthPx missing, square mode disabled", th)
            null
        }
        val cellHeightField = try {
            findField(dpClass, "cellHeightPx")
        } catch (th: Throwable) {
            logger.error("WideGrid: cellHeightPx missing, square mode disabled", th)
            null
        }
        val borderSpaceField = try {
            findField(dpClass, "cellLayoutBorderSpacePx")
        } catch (th: Throwable) {
            logger.error("WideGrid: cellLayoutBorderSpacePx missing, square mode disabled", th)
            null
        }
        val invField = try {
            findField(dpClass, "inv")
        } catch (th: Throwable) {
            logger.error("WideGrid: inv missing, square mode disabled", th)
            null
        }
        val invClass = try {
            classLoader.loadClass("com.android.launcher3.InvariantDeviceProfile")
        } catch (th: Throwable) {
            logger.error("WideGrid: InvariantDeviceProfile not found, square mode disabled", th)
            null
        }
        val numColumnsField = try {
            invClass?.let { findField(it, "numColumns") }
        } catch (th: Throwable) {
            logger.error("WideGrid: numColumns missing, square mode disabled", th)
            null
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
                val prefs = remotePreferences
                val squareMode = prefs.getBoolean(
                    PreferenceKeys.LAUNCHER_WIDE_GRID_SQUARE.name,
                    PreferenceKeys.LAUNCHER_WIDE_GRID_SQUARE.default
                )
                val sideInsetPx = if (squareMode && cellWidthField != null && cellHeightField != null &&
                    borderSpaceField != null && invField != null && numColumnsField != null
                ) {
                    computeSquareInset(
                        dp, marginField, cellWidthField, cellHeightField,
                        borderSpaceField, invField, numColumnsField
                    )
                } else {
                    val insetDp = prefs.getInt(
                        PreferenceKeys.LAUNCHER_WIDE_GRID_SIDE_INSET.name,
                        PreferenceKeys.LAUNCHER_WIDE_GRID_SIDE_INSET.default
                    )
                    Math.round(insetDp * context.resources.displayMetrics.density)
                }
                rewriteProfile(dp, sideInsetPx, marginField, cellPaddingField)
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
     * Solves the absolute per-side padding so cellWidth == cellHeight.
     *
     * The launcher computes cellWidth from the available width minus total side
     * padding: contentWidth = availableWidth - 2*side, and
     * cellWidth = (contentWidth - borderX*(columns-1)) / columns. Reading the
     * current side padding from the margin field lets us reconstruct
     * availableWidth = columns*cellWidth + borderX*(columns-1) + 2*side, then
     * solve for the target side with cellWidth' = cellHeight:
     * side' = (availableWidth - columns*cellHeight - borderX*(columns-1)) / 2.
     * A negative result (too many columns for square cells) clamps to 0.
     */
    private fun computeSquareInset(
        dp: Any,
        marginField: java.lang.reflect.Field,
        cellWidthField: java.lang.reflect.Field,
        cellHeightField: java.lang.reflect.Field,
        borderSpaceField: java.lang.reflect.Field,
        invField: java.lang.reflect.Field,
        numColumnsField: java.lang.reflect.Field
    ): Int {
        val currentSide = marginField.getInt(dp)
        val cellWidth = cellWidthField.getInt(dp)
        val cellHeight = cellHeightField.getInt(dp)
        val borderSpace = borderSpaceField.get(dp) as android.graphics.Point
        val columns = numColumnsField.getInt(invField.get(dp))
        if (columns <= 0 || cellWidth <= 0 || cellHeight <= 0) {
            logger.info("WideGrid: square mode skipped, bad metrics w=$cellWidth h=$cellHeight cols=$columns")
            return currentSide
        }
        val borderTotal = borderSpace.x * (columns - 1)
        val availableWidth = columns * cellWidth + borderTotal + 2 * currentSide
        val targetSide = (availableWidth - columns * cellHeight - borderTotal) / 2
        val clamped = targetSide.coerceAtLeast(0)
        logger.info(
            "WideGrid: square mode cellW=$cellWidth cellH=$cellHeight cols=$columns " +
                "borderX=${borderSpace.x} availW=$availableWidth side $currentSide->$clamped" +
                (if (targetSide < 0) " (clamped, requested $targetSide)" else "")
        )
        return clamped
    }

    private fun rewriteProfile(
        dp: Any,
        sideInsetPx: Int,
        marginField: java.lang.reflect.Field,
        cellPaddingField: java.lang.reflect.Field
    ) {
        val beforeMargin = marginField.getInt(dp)
        marginField.setInt(dp, sideInsetPx)
        val cellPadding = cellPaddingField.get(dp) as Rect
        val before = "L${cellPadding.left} T${cellPadding.top} R${cellPadding.right} B${cellPadding.bottom}"
        cellPadding.left = sideInsetPx
        cellPadding.right = sideInsetPx
        logger.info(
            "WideGrid: applied sideInsetPx=$sideInsetPx " +
                "marginPx $beforeMargin->$sideInsetPx cellLayoutPadding $before->" +
                "L${cellPadding.left} T${cellPadding.top} R${cellPadding.right} B${cellPadding.bottom}"
        )
    }
}
