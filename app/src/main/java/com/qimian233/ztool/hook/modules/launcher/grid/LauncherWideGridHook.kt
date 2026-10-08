package com.qimian233.ztool.hook.modules.launcher.grid

import android.annotation.SuppressLint
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Point
import android.graphics.Rect
import android.view.View
import android.view.ViewGroup
import com.qimian233.ztool.data.keys.PreferenceKeys
import com.qimian233.ztool.data.keys.ScopeKeys
import com.qimian233.ztool.hook.base.AppHookModule
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam
import java.lang.reflect.Field
import java.lang.reflect.Method
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Rewrites the workspace side padding to the configured inset (square mode solves it so
 * that cell width equals cell height) and re-runs `setInsets` so the new padding
 * propagates. Square mode reads the live page geometry. See
 * docs/research/zui_launcher_wide_grid_geometry.md. Needs a launcher restart.
 */
@SuppressLint("PrivateApi")
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
        // Square-mode members: optional; missing any of them degrades to slider mode.
        val squareFields = resolveSquareFields(classLoader, dpClass)

        val resolver = ProfileResolver(getDeviceProfile, activityContextClass, marginField, cellPaddingField)

        // Our own setInsets re-invocation must not re-enter the rewrite.
        val inRewrite = ThreadLocal.withInitial { false }

        fun reapplyInsets(workspace: View, insets: Rect?) {
            if (insets == null) return
            inRewrite.set(true)
            try {
                setInsets.invoke(workspace, insets)
            } finally {
                inRewrite.set(false)
            }
        }

        hookWithId(setInsets, "workspace_grid_margins_rewrite") { chain ->
            chain.proceed()
            if (inRewrite.get() == true) return@hookWithId null
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
                val insets = if (chain.args.size > 0) chain.args[0] as? Rect else null
                val prefs = remotePreferences
                val square = squareFields
                if (square != null && prefs.getBoolean(
                        PreferenceKeys.LAUNCHER_WIDE_GRID_SQUARE.name,
                        PreferenceKeys.LAUNCHER_WIDE_GRID_SQUARE.default
                    )
                ) {
                    val side = solveSquareSide(workspace, dp, square, resolver, liveOnly = false)
                    if (side != null && applyCellLayoutSide(dp, side, resolver)) {
                        reapplyInsets(workspace, insets)
                    }
                    // Window insets are dispatched before the first measure, so the pages
                    // may still be unmeasured here: re-check against the live geometry
                    // after the layout pass and correct the padding if it differs. A page
                    // that still has a pending layout holds geometry from the old padding.
                    val recheck = object : Runnable {
                        private var attemptsLeft = SQUARE_RECHECK_ATTEMPTS

                        override fun run() {
                            val page = findPage(workspace, square.pageClass, requireMeasured = false)
                            if (page != null && page.isLayoutRequested) {
                                if (attemptsLeft-- > 0) workspace.postOnAnimation(this)
                                return
                            }
                            val live = try {
                                solveSquareSide(workspace, dp, square, resolver, liveOnly = true)
                            } catch (th: Throwable) {
                                logger.error("WideGrid: post-layout square check failed", th)
                                null
                            }
                            if (live == null) {
                                if (attemptsLeft-- > 0) workspace.postOnAnimation(this)
                                return
                            }
                            if (applyCellLayoutSide(dp, live, resolver)) reapplyInsets(workspace, insets)
                        }
                    }
                    workspace.postOnAnimation(recheck)
                } else {
                    val insetDp = prefs.getInt(
                        PreferenceKeys.LAUNCHER_WIDE_GRID_SIDE_INSET.name,
                        PreferenceKeys.LAUNCHER_WIDE_GRID_SIDE_INSET.default
                    )
                    val sideInsetPx = (insetDp * workspace.resources.displayMetrics.density).roundToInt()
                    applySideInset(dp, sideInsetPx, resolver)
                    // Re-run with the rewritten profile so setPadding/requestLayout picks
                    // up the new values (guarded by inRewrite to avoid recursion).
                    reapplyInsets(workspace, insets)
                }
            } catch (th: Throwable) {
                logger.error("WideGrid: rewrite failed", th)
            }
            null
        }
        logger.info("LauncherWideGridHook installed (hooking Workspace#setInsets)")
    }

    /**
     * Resolves every field and method square mode needs, once at install time.
     * Returns null if any is missing — square mode is then disabled and the slider
     * path is used.
     */
    private fun resolveSquareFields(classLoader: ClassLoader, dpClass: Class<*>): SquareFields? {
        val pageClass = try {
            classLoader.loadClass("com.android.launcher3.CellLayout")
        } catch (th: Throwable) {
            logger.error("WideGrid: CellLayout not found, square mode disabled", th)
            return null
        }
        val borderSpace = tryField(dpClass, "cellLayoutBorderSpacePx") ?: return null
        val availableWidth = tryField(dpClass, "availableWidthPx") ?: return null
        val availableHeight = tryField(dpClass, "availableHeightPx") ?: return null
        val invField = tryField(dpClass, "inv") ?: return null
        val invClass = try {
            classLoader.loadClass("com.android.launcher3.InvariantDeviceProfile")
        } catch (th: Throwable) {
            logger.error("WideGrid: InvariantDeviceProfile not found, square mode disabled", th)
            return null
        }
        val numColumns = tryField(invClass, "numColumns") ?: return null
        val numRows = tryField(invClass, "numRows") ?: return null
        val getCountX = tryMethod(pageClass, "getCountX") ?: return null
        val getCountY = tryMethod(pageClass, "getCountY") ?: return null
        return SquareFields(
            pageClass, borderSpace, availableWidth, availableHeight,
            invField, numColumns, numRows, getCountX, getCountY
        )
    }

    private fun tryField(owner: Class<*>, name: String): Field? = try {
        findField(owner, name)
    } catch (th: Throwable) {
        logger.error("WideGrid: field '$name' missing, square mode disabled", th)
        null
    }

    private fun tryMethod(owner: Class<*>, name: String): Method? = try {
        findMethod(owner, name)
    } catch (th: Throwable) {
        logger.error("WideGrid: method '$name' missing, square mode disabled", th)
        null
    }

    /**
     * Solves the side padding that makes cell width equal cell height. Cell width falls by
     * 2px per 1px of side padding, so the target is `side + columns * (cellWidth -
     * cellHeight) / 2`, reading live page geometry and falling back to the window size.
     *
     * @param liveOnly skip the fallback and wait for a measured page.
     * @return target side padding, or null when no usable geometry exists yet.
     */
    private fun solveSquareSide(
        workspace: View,
        dp: Any,
        fields: SquareFields,
        resolver: ProfileResolver,
        liveOnly: Boolean
    ): Int? {
        val cellPadding = resolver.cellPadding.get(dp) as? Rect ?: return null
        val measuredPage = findPage(workspace, fields.pageClass, requireMeasured = true)
        if (measuredPage == null && liveOnly) return null
        val page = measuredPage ?: findPage(workspace, fields.pageClass, requireMeasured = false)
        val width = measuredPage?.measuredWidth ?: fields.availableWidth.getInt(dp)
        val height = measuredPage?.measuredHeight ?: fields.availableHeight.getInt(dp)
        val padLeft = measuredPage?.paddingLeft ?: cellPadding.left
        val padRight = measuredPage?.paddingRight ?: cellPadding.right
        val padTop = measuredPage?.paddingTop ?: cellPadding.top
        val padBottom = measuredPage?.paddingBottom ?: cellPadding.bottom
        val cols = liveOrProfileCount(page, fields.getCountX, dp, fields.inv, fields.numColumns)
        val rows = liveOrProfileCount(page, fields.getCountY, dp, fields.inv, fields.numRows)
        val border = fields.borderSpace.get(dp) as? Point ?: return null
        if (width <= 0 || height <= 0 || cols <= 0 || rows <= 0) {
            logger.debug("WideGrid: square mode skipped, geometry ${width}x$height cols=$cols rows=$rows")
            return null
        }
        val borderX = border.x * (cols - 1)
        val borderY = border.y * (rows - 1)
        val cellWidth = (width - padLeft - padRight - borderX) / cols
        val cellHeight = (height - padTop - padBottom - borderY) / rows
        if (cellWidth <= 0 || cellHeight <= 0) {
            logger.debug("WideGrid: square mode skipped, cells w=$cellWidth h=$cellHeight")
            return null
        }
        val side = (padLeft + padRight) / 2
        // Keep at least 1px per cell so the grid cannot collapse.
        val maxSide = ((width - borderX - cols) / 2).coerceAtLeast(0)
        val target = if (abs(cellWidth - cellHeight) <= 1) {
            side
        } else {
            (side + cols * (cellWidth - cellHeight) / 2).coerceIn(0, maxSide)
        }
        val unchanged = cellPadding.left == target && cellPadding.right == target
        if (unchanged) {
            logger.debug(
                "WideGrid: square mode stable cellW=$cellWidth cellH=$cellHeight " +
                    "cols=$cols rows=$rows side=$target"
            )
        } else {
            logger.info(
                "WideGrid: square mode cellW=$cellWidth cellH=$cellHeight cols=$cols rows=$rows " +
                    "page=${width}x$height side=$side->$target" +
                    (if (measuredPage != null) " (live)" else " (model)")
            )
        }
        return target
    }

    private fun findPage(workspace: View, pageClass: Class<*>, requireMeasured: Boolean): View? {
        if (workspace !is ViewGroup) return null
        for (i in 0 until workspace.childCount) {
            val child = workspace.getChildAt(i) ?: continue
            if (!pageClass.isInstance(child)) continue
            if (!requireMeasured || (child.measuredWidth > 0 && child.measuredHeight > 0)) return child
        }
        return null
    }

    private fun liveOrProfileCount(
        page: View?,
        getter: Method,
        dp: Any,
        invField: Field,
        countField: Field
    ): Int {
        if (page != null) {
            val live = try {
                getter.invoke(page) as? Int ?: 0
            } catch (th: Throwable) {
                0
            }
            if (live > 0) return live
        }
        return try {
            val inv = invField.get(dp) ?: return 0
            countField.getInt(inv)
        } catch (th: Throwable) {
            0
        }
    }

    /** Writes the square-mode side padding; returns whether anything changed. */
    private fun applyCellLayoutSide(dp: Any, side: Int, resolver: ProfileResolver): Boolean {
        val cellPadding = resolver.cellPadding.get(dp) as Rect
        if (cellPadding.left == side && cellPadding.right == side) return false
        cellPadding.left = side
        cellPadding.right = side
        return true
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

    /** Reflection members resolved at install time for square mode. */
    private class SquareFields(
        val pageClass: Class<*>,
        val borderSpace: Field,
        val availableWidth: Field,
        val availableHeight: Field,
        val inv: Field,
        val numColumns: Field,
        val numRows: Field,
        val getCountX: Method,
        val getCountY: Method
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

    private companion object {
        /** Frames to wait for a workspace layout before dropping the re-check. */
        const val SQUARE_RECHECK_ATTEMPTS = 8
    }
}
