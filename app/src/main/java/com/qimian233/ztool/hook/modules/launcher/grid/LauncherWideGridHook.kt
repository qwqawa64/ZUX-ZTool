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
import java.util.concurrent.ConcurrentHashMap
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
                    val solve = solveSquareSide(workspace, dp, square, resolver, preferLive = false)
                    if (solve != null) {
                        logger.info("WideGrid: square solve ${solve.report}")
                        if (applyCellLayoutSide(workspace, dp, solve.side, square, resolver)) {
                            reapplyInsets(workspace, insets)
                        }
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
                                solveSquareSide(workspace, dp, square, resolver, preferLive = true)
                            } catch (th: Throwable) {
                                logger.error("WideGrid: post-layout square check failed", th)
                                null
                            }
                            if (live == null) {
                                if (attemptsLeft-- > 0) workspace.postOnAnimation(this)
                                return
                            }
                            logger.info("WideGrid: square recheck ${live.report}")
                            if (applyCellLayoutSide(workspace, dp, live.side, square, resolver)) {
                                reapplyInsets(workspace, insets)
                            }
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

        // The icon views themselves are laid out at the cell size the page's
        // ShortcutAndWidgetContainer holds, which is where the visible icon box comes
        // from. Keep it square too, and log what it was asked to use.
        val containerClass = squareFields?.let {
            try {
                classLoader.loadClass("com.android.launcher3.ShortcutAndWidgetContainer")
            } catch (th: Throwable) {
                logger.error("WideGrid: ShortcutAndWidgetContainer not found", th)
                null
            }
        }
        val setCellDimensions = containerClass?.let {
            try {
                findMethod(
                    it, "setCellDimensions",
                    Int::class.javaPrimitiveType, Int::class.javaPrimitiveType,
                    Int::class.javaPrimitiveType, Int::class.javaPrimitiveType,
                    Point::class.java
                )
            } catch (th: Throwable) {
                logger.error("WideGrid: setCellDimensions not found", th)
                null
            }
        }
        val square = squareFields
        val containerHook = square != null && containerClass != null && setCellDimensions != null
        logger.info(
            "WideGrid: install squareFields=${square != null} containerClass=${containerClass != null} " +
                "setCellDimensions=${setCellDimensions != null} containerHook=$containerHook"
        )
        if (containerHook) {
            val inCellRewrite = ThreadLocal.withInitial { false }
            val reported = ConcurrentHashMap<String, Boolean>()
            fun reportCellOnce(key: String, message: String) {
                if (reported.putIfAbsent(key, true) == null) logger.info("WideGrid: $message")
            }
            try {
                hookWithId(setCellDimensions!!, "workspace_grid_cell_size") { chain ->
                chain.proceed()
                if (inCellRewrite.get() == true) return@hookWithId null
                try {
                    val container = chain.thisObject as? ViewGroup
                    val args = chain.args
                    reportCellOnce(
                        "fired",
                        "cell hook fired container=${container?.javaClass?.simpleName} " +
                            "parent=${container?.parent?.javaClass?.simpleName} args=${args.size}"
                    )
                    val page = container?.parent as? View
                    if (container == null || page == null || !square.pageClass.isInstance(page)) {
                        reportCellOnce(
                            "parent",
                            "cell hook skipped: container parent is not a CellLayout " +
                                "(${container?.parent?.javaClass?.simpleName})"
                        )
                        return@hookWithId null
                    }
                    var ancestor: View? = page
                    var levels = 0
                    while (ancestor != null && !workspaceClass.isInstance(ancestor) && levels < 4) {
                        ancestor = ancestor.parent as? View
                        levels++
                    }
                    if (ancestor == null || !workspaceClass.isInstance(ancestor)) {
                        reportCellOnce(
                            "ancestor",
                            "cell hook skipped: no Workspace ancestor for ${page.javaClass.simpleName}"
                        )
                        return@hookWithId null
                    }
                    if (args.size < 5) {
                        reportCellOnce("args", "cell hook skipped: args size ${args.size}")
                        return@hookWithId null
                    }
                    val cellWidth = args[0] as? Int ?: return@hookWithId null
                    val cellHeight = args[1] as? Int ?: return@hookWithId null
                    val cols = args[2] as? Int ?: return@hookWithId null
                    val rows = args[3] as? Int ?: return@hookWithId null
                    val border = args[4] as? Point ?: return@hookWithId null
                    val pageWidth = page.measuredWidth
                    val pageHeight = page.measuredHeight
                    if (pageWidth <= 0 || pageHeight <= 0 || cols <= 0 || rows <= 0) {
                        reportCellOnce(
                            "unmeasured",
                            "cell hook skipped: page=${pageWidth}x$pageHeight cols=$cols rows=$rows"
                        )
                        return@hookWithId null
                    }
                    val byBox = (pageWidth - page.paddingLeft - page.paddingRight -
                        border.x * (cols - 1)) / cols
                    val byHeight = (pageHeight - page.paddingTop - page.paddingBottom -
                        border.y * (rows - 1)) / rows
                    if (byBox <= 0 || byHeight <= 0) {
                        reportCellOnce("cells", "cell hook skipped: derived cells ${byBox}x$byHeight")
                        return@hookWithId null
                    }
                    val side = minOf(byBox, byHeight)
                    val squareMode = remotePreferences.getBoolean(
                        PreferenceKeys.LAUNCHER_WIDE_GRID_SQUARE.name,
                        PreferenceKeys.LAUNCHER_WIDE_GRID_SQUARE.default
                    )
                    val apply = squareMode && (cellWidth != side || cellHeight != side)
                    logger.info(
                        "WideGrid: cell container ${cellWidth}x$cellHeight -> ${side}x$side " +
                            "square=$squareMode apply=$apply page=${pageWidth}x$pageHeight pad=" +
                            "${page.paddingLeft},${page.paddingTop},${page.paddingRight},${page.paddingBottom} " +
                            "cols=$cols rows=$rows border=${border.x},${border.y} ${firstChild(container)}"
                    )
                    if (apply) {
                        inCellRewrite.set(true)
                        try {
                            setCellDimensions.invoke(container, side, side, cols, rows, border)
                        } finally {
                            inCellRewrite.set(false)
                        }
                    }
                } catch (th: Throwable) {
                    logger.error("WideGrid: cell container rewrite failed", th)
                }
                null
                }
            } catch (th: Throwable) {
                logger.error("WideGrid: cell container hook install failed", th)
            }
        }
        logger.info("LauncherWideGridHook installed (hooking Workspace#setInsets)")
    }

    /** Diagnostic description of the first icon view inside a cell container. */
    private fun firstChild(container: ViewGroup): String {
        if (container.childCount == 0) return "child=none"
        val child = container.getChildAt(0) ?: return "child=none"
        val lp = child.layoutParams
        val margins = if (lp is ViewGroup.MarginLayoutParams) {
            "${lp.leftMargin},${lp.topMargin},${lp.rightMargin},${lp.bottomMargin}"
        } else {
            "n/a"
        }
        return "child=${child.javaClass.simpleName} ${child.width}x${child.height} " +
            "lp=${lp?.width}x${lp?.height} margin=$margins " +
            "pad=${child.paddingLeft},${child.paddingTop},${child.paddingRight},${child.paddingBottom}"
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
        // Optional: the cell size the page last laid out with, used to verify the math.
        val getCellWidth = optionalMethod(pageClass, "getCellWidth")
        val getCellHeight = optionalMethod(pageClass, "getCellHeight")
        return SquareFields(
            pageClass, borderSpace, availableWidth, availableHeight,
            invField, numColumns, numRows, getCountX, getCountY, getCellWidth, getCellHeight
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

    private fun optionalMethod(owner: Class<*>, name: String): Method? = try {
        findMethod(owner, name)
    } catch (th: Throwable) {
        logger.info("WideGrid: optional method '$name' missing")
        null
    }

    private fun callInt(method: Method?, target: Any): Int {
        if (method == null) return -1
        return try {
            method.invoke(target) as? Int ?: -1
        } catch (th: Throwable) {
            -1
        }
    }

    /**
     * Solves the side padding that makes cell width equal cell height, using the cell
     * width the page last laid out with when [preferLive] is set and the geometry derived
     * from the page box otherwise. Cell width falls by 2px per 1px of side padding.
     *
     * @param preferLive require a laid-out page and refuse to act when its cell size does
     *   not match the geometry derived from the page box.
     * @return target side padding plus a diagnostic snapshot, or null when unusable.
     */
    private fun solveSquareSide(
        workspace: View,
        dp: Any,
        fields: SquareFields,
        resolver: ProfileResolver,
        preferLive: Boolean
    ): SquareSolve? {
        val cellPadding = resolver.cellPadding.get(dp) as? Rect ?: return null
        val measuredPage = findPage(workspace, fields.pageClass, requireMeasured = true)
        if (preferLive && measuredPage == null) return null
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
            logger.info("WideGrid: square skipped, geometry ${width}x$height cols=$cols rows=$rows")
            return null
        }
        val borderX = border.x * (cols - 1)
        val borderY = border.y * (rows - 1)
        val calcWidth = (width - padLeft - padRight - borderX) / cols
        val calcHeight = (height - padTop - padBottom - borderY) / rows
        val liveWidth = if (measuredPage != null) callInt(fields.getCellWidth, measuredPage) else -1
        val liveHeight = if (measuredPage != null) callInt(fields.getCellHeight, measuredPage) else -1
        // The page lays its cells out from the padding: a live size that disagrees with the
        // page box means the cell size is pinned elsewhere and padding cannot move it.
        if (preferLive && (liveWidth <= 0 || liveHeight <= 0 || abs(liveWidth - calcWidth) > 1)) {
            logger.error(
                "WideGrid: square mode gave up, cell size does not follow padding " +
                    "live=${liveWidth}x$liveHeight calc=${calcWidth}x$calcHeight " +
                    "page=${width}x$height pad=$padLeft,$padTop,$padRight,$padBottom"
            )
            return null
        }
        val cellWidth = if (preferLive) liveWidth else calcWidth
        val cellHeight = if (preferLive) liveHeight else calcHeight
        if (cellWidth <= 0 || cellHeight <= 0) {
            logger.info("WideGrid: square skipped, cells w=$cellWidth h=$cellHeight")
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
        val report = "live=${liveWidth}x$liveHeight calc=${calcWidth}x$calcHeight " +
            "page=${width}x$height pad=$padLeft,$padTop,$padRight,$padBottom cols=$cols rows=$rows " +
            "border=${border.x},${border.y} profile=${cellPadding.left}/${cellPadding.right} " +
            "side=$side->$target"
        return SquareSolve(target, report)
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

    /**
     * Writes the square-mode side padding to the profile and, when the pages do not carry
     * that Rect, straight onto the pages. Returns whether anything changed.
     */
    private fun applyCellLayoutSide(
        workspace: View,
        dp: Any,
        side: Int,
        fields: SquareFields,
        resolver: ProfileResolver
    ): Boolean {
        val cellPadding = resolver.cellPadding.get(dp) as? Rect ?: return false
        val group = workspace as? ViewGroup ?: return cellPadding.left != side || cellPadding.right != side
        var reachedPages = true
        for (i in 0 until group.childCount) {
            val page = group.getChildAt(i) ?: continue
            if (!fields.pageClass.isInstance(page)) continue
            if (page.paddingLeft != cellPadding.left || page.paddingRight != cellPadding.right) {
                reachedPages = false
                break
            }
        }
        var pagesChanged = false
        if (!reachedPages) {
            logger.info("WideGrid: pages did not carry cellLayoutPaddingPx, setting page padding")
            for (i in 0 until group.childCount) {
                val page = group.getChildAt(i) ?: continue
                if (!fields.pageClass.isInstance(page)) continue
                if (page.paddingLeft != side || page.paddingRight != side) {
                    page.setPadding(side, page.paddingTop, side, page.paddingBottom)
                    pagesChanged = true
                }
            }
        }
        val rectChanged = cellPadding.left != side || cellPadding.right != side
        if (rectChanged) {
            cellPadding.left = side
            cellPadding.right = side
        }
        return rectChanged || pagesChanged
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

    /** Solved side padding plus a one-line snapshot of the geometry it was read from. */
    private class SquareSolve(val side: Int, val report: String)

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
        val getCountY: Method,
        val getCellWidth: Method?,
        val getCellHeight: Method?
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
