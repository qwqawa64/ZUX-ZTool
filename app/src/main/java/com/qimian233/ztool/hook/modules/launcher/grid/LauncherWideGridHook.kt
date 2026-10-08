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
import kotlin.math.roundToInt

/**
 * Rewrites the workspace side padding to the configured inset, or — in square mode — makes
 * every icon's cell box square by solving the cell size in the page's own measure pass.
 * Square mode reads the cell the page handed to its container and only applies it to a
 * clearly wide rectangle; a mild one keeps the host's per-axis (ColorOS-like) cells.
 * See docs/research/zui_launcher_wide_grid_geometry.md. Needs a launcher restart.
 */
@SuppressLint("PrivateApi")
class LauncherWideGridHook : AppHookModule() {

    /**
     * Cell width/height ratio above which square mode overrides the host cell. Measured
     * 10 columns x 6 rows gives ~1.16 (kept per-axis, as ColorOS does) and 6 x 4 gives ~1.29
     * (squared).
     */
    private val squareAspectThreshold = 1.2f

    /** Last square-mode geometry applied, so a measure pass never re-triggers itself. */
    @Volatile
    private var lastSquarePad = -1

    @Volatile
    private var lastSquareSide = -1

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

        // Shared geometry object; the big-folder hook reads the same measured cell.
        LauncherGridMetrics.install(classLoader, logger)

        val resolver = ProfileResolver(getDeviceProfile, activityContextClass, marginField, cellPaddingField)

        // Read once: the page measure hook runs on every layout pass, and changing these
        // requires a launcher restart anyway.
        val preferences = remotePreferences
        val squareMode = preferences.getBoolean(
            PreferenceKeys.LAUNCHER_WIDE_GRID_SQUARE.name,
            PreferenceKeys.LAUNCHER_WIDE_GRID_SQUARE.default
        )
        val insetDp = preferences.getInt(
            PreferenceKeys.LAUNCHER_WIDE_GRID_SIDE_INSET.name,
            PreferenceKeys.LAUNCHER_WIDE_GRID_SIDE_INSET.default
        )

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
                // Square mode is solved in the page's measure pass, where the page box is
                // known; writing padding here too would fight that solve.
                if (squareFields != null && squareMode) return@hookWithId null
                val dp = resolver.resolve(workspace.context)
                if (dp == null) {
                    logger.info("WideGrid: DeviceProfile not reachable from context")
                    return@hookWithId null
                }
                val sideInsetPx = (insetDp * workspace.resources.displayMetrics.density).roundToInt()
                applySideInset(dp, sideInsetPx, resolver)
                // Re-run with the rewritten profile so setPadding/requestLayout picks up the
                // new values (guarded by inRewrite to avoid recursion).
                reapplyInsets(workspace, if (chain.args.size > 0) chain.args[0] as? Rect else null)
            } catch (th: Throwable) {
                logger.error("WideGrid: rewrite failed", th)
            }
            null
        }

        val square = squareFields
        val pageMeasure = square?.let {
            try {
                findMethod(
                    it.pageClass, "onMeasure",
                    Int::class.javaPrimitiveType, Int::class.javaPrimitiveType
                )
            } catch (th: Throwable) {
                logger.error("WideGrid: CellLayout#onMeasure not found, square mode disabled", th)
                null
            }
        }
        logger.info(
            "WideGrid: install squareFields=${square != null} squareMode=$squareMode " +
                "insetDp=$insetDp setCellDimensions=${square?.setCellDimensions != null} " +
                "pageMeasure=${pageMeasure != null}"
        )
        if (square != null && pageMeasure != null) {
            val counts = ConcurrentHashMap<String, Int>()
            fun reportLimited(key: String, limit: Int, message: String) {
                val seen = (counts[key] ?: 0) + 1
                counts[key] = seen
                if (seen <= limit) logger.info("WideGrid: $message")
            }
            try {
                hookWithId(pageMeasure, "workspace_grid_page_measure") { chain ->
                    chain.proceed()
                    try {
                        val page = chain.thisObject as? ViewGroup ?: return@hookWithId null
                        if (!isWorkspacePage(page, workspaceClass)) return@hookWithId null
                        if (!squareMode) return@hookWithId null
                        val args = chain.args
                        if (args.size < 2) {
                            reportLimited("args", 3, "page measure skipped: args=${args.size}")
                            return@hookWithId null
                        }
                        val widthSpec = args[0] as? Int ?: return@hookWithId null
                        val heightSpec = args[1] as? Int ?: return@hookWithId null
                        val boxWidth = View.MeasureSpec.getSize(widthSpec)
                        val boxHeight = View.MeasureSpec.getSize(heightSpec)
                        val cols = callInt(square.getCountX, page)
                        val rows = callInt(square.getCountY, page)
                        val dp = resolver.resolve(page.context)
                        val border = dp?.let { square.borderSpace.get(it) as? Point }
                        // The icon box is the cell the page handed to its container, so the
                        // decision reads that cell instead of re-deriving it from the raw box.
                        val cellWidth = callInt(square.getCellWidth, page)
                        val cellHeight = callInt(square.getCellHeight, page)
                        if (boxWidth <= 0 || boxHeight <= 0 || cols <= 0 || rows <= 0 ||
                            border == null || cellWidth <= 0 || cellHeight <= 0
                        ) {
                            reportLimited(
                                "box", 3,
                                "page measure skipped: box=${boxWidth}x$boxHeight cols=$cols " +
                                    "rows=$rows cell=${cellWidth}x$cellHeight"
                            )
                            return@hookWithId null
                        }
                        // The host's own cells are width-driven in X and height-driven in Y,
                        // which is what ColorOS/OPlus produce, and squaring them costs
                        // (cellWidth - cellHeight) per column. Only a clearly wide cell is
                        // squared; a mild rectangle stays as the vendor lays it out.
                        val aspect = cellWidth.toFloat() / cellHeight
                        if (aspect <= squareAspectThreshold) {
                            val verdict =
                                if (aspect >= 0.98f) "already square"
                                else "kept per-axis (ColorOS) cells"
                            reportLimited(
                                "perAxis", 3,
                                "square skipped: cell=${cellWidth}x$cellHeight aspect=$aspect " +
                                    "cols=$cols rows=$rows -> $verdict"
                            )
                            return@hookWithId null
                        }
                        val byHeight = (boxHeight - page.paddingTop - page.paddingBottom -
                            border.y * (rows - 1)) / rows
                        // The height budget is padding-independent, so the square side and the
                        // centring padding converge in one pass instead of drifting.
                        val side = if (byHeight > 0) byHeight else cellHeight
                        val padding = ((boxWidth - (cols * side + border.x * (cols - 1))) / 2)
                            .coerceAtLeast(0)
                        val container = square.getContainer.invoke(page) as? ViewGroup
                        reportLimited(
                            "page", 12,
                            "page measure box=${boxWidth}x$boxHeight pad=${page.paddingLeft}->$padding " +
                                "cell=${cellWidth}x$cellHeight side=$side cols=$cols rows=$rows " +
                                "border=${border.x},${border.y} " +
                                (if (container != null) containerChildren(container) else "children=none")
                        )
                        var relayout = false
                        // The profile padding survives page relayouts but not a profile
                        // rebuild, so it is synced on every pass, outside the solve guard.
                        val profilePadding = resolver.cellPadding.get(dp) as? Rect
                        if (profilePadding != null &&
                            (profilePadding.left != padding || profilePadding.right != padding)
                        ) {
                            profilePadding.left = padding
                            profilePadding.right = padding
                        }
                        // The host freezes the workspace content centring in cellYPaddingPx
                        // while building the profile; a square cell of a different height must
                        // recompute it or the icon keeps the old offset (plan item B3).
                        val contentHeight = LauncherGridMetrics.contentHeightPx(dp)
                        if (contentHeight != null) {
                            val desired = maxOf(0, (side - contentHeight) / 2)
                            val current = LauncherGridMetrics.cellYPaddingPx(dp)
                            if (current != null && current != desired &&
                                LauncherGridMetrics.writeCellYPaddingPx(dp, desired)
                            ) {
                                relayout = true
                                reportLimited(
                                    "ypad", 4,
                                    "cellYPaddingPx $current -> $desired (side=$side content=$contentHeight)"
                                )
                            }
                        }
                        val padStale = page.paddingLeft != padding || page.paddingRight != padding
                        val newTarget = padding != lastSquarePad || side != lastSquareSide
                        if (padStale || newTarget) {
                            lastSquarePad = padding
                            lastSquareSide = side
                            if (padStale) {
                                page.setPadding(padding, page.paddingTop, padding, page.paddingBottom)
                            }
                            if (container != null) {
                                square.setCellDimensions.invoke(
                                    container, side, side, cols, rows, border
                                )
                            }
                            relayout = true
                        }
                        if (relayout) {
                            page.requestLayout()
                            container?.requestLayout()
                            LauncherGridMetrics.publish(LauncherGridMetrics.fromPage(page, dp))
                        }
                    } catch (th: Throwable) {
                        logger.error("WideGrid: page measure rewrite failed", th)
                    }
                    null
                }
            } catch (th: Throwable) {
                logger.error("WideGrid: page measure hook install failed", th)
            }
        }
        logger.info("LauncherWideGridHook installed (hooking Workspace#setInsets)")
    }

    /**
     * Resolves every member square mode needs, once at install time. Returns null if any
     * required one is missing — square mode is then disabled and the slider path is used.
     */
    private fun resolveSquareFields(classLoader: ClassLoader, dpClass: Class<*>): SquareFields? {
        val pageClass = try {
            classLoader.loadClass("com.android.launcher3.CellLayout")
        } catch (th: Throwable) {
            logger.error("WideGrid: CellLayout not found, square mode disabled", th)
            return null
        }
        val containerClass = try {
            classLoader.loadClass("com.android.launcher3.ShortcutAndWidgetContainer")
        } catch (th: Throwable) {
            logger.error("WideGrid: ShortcutAndWidgetContainer not found, square mode disabled", th)
            return null
        }
        val borderSpace = tryField(dpClass, "cellLayoutBorderSpacePx") ?: return null
        val getCountX = tryMethod(pageClass, "getCountX") ?: return null
        val getCountY = tryMethod(pageClass, "getCountY") ?: return null
        // The icon box is the cell the page handed to its container; these are that cell.
        val getCellWidth = tryMethod(pageClass, "getCellWidth") ?: return null
        val getCellHeight = tryMethod(pageClass, "getCellHeight") ?: return null
        val getContainer = tryMethod(pageClass, "getShortcutsAndWidgets") ?: return null
        val setCellDimensions = try {
            findMethod(
                containerClass, "setCellDimensions",
                Int::class.javaPrimitiveType, Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType, Int::class.javaPrimitiveType,
                Point::class.java
            )
        } catch (th: Throwable) {
            logger.error("WideGrid: container setCellDimensions missing, square mode disabled", th)
            return null
        }
        return SquareFields(
            pageClass, borderSpace, getCountX, getCountY, getCellWidth, getCellHeight,
            getContainer, setCellDimensions
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

    private fun callInt(method: Method?, target: Any): Int {
        if (method == null) return -1
        return try {
            method.invoke(target) as? Int ?: -1
        } catch (th: Throwable) {
            -1
        }
    }

    /** True when the view is a page of the workspace (itself a bound ancestor). */
    private fun isWorkspacePage(page: View, workspaceClass: Class<*>): Boolean {
        if (workspaceClass.isInstance(page)) return false
        var ancestor: View? = page
        var levels = 0
        while (ancestor != null && !workspaceClass.isInstance(ancestor) && levels < 4) {
            ancestor = ancestor.parent as? View
            levels++
        }
        return ancestor != null && workspaceClass.isInstance(ancestor)
    }

    /** Diagnostic description of the first icon views inside a cell container. */
    private fun containerChildren(container: ViewGroup): String {
        if (container.childCount == 0) return "children=none"
        val sb = StringBuilder("children=")
        for (i in 0 until minOf(2, container.childCount)) {
            val child = container.getChildAt(i)
            val lp = child.layoutParams
            val locked = try {
                lp?.javaClass?.getField("isLockedToGrid")?.getBoolean(lp)
            } catch (th: Throwable) {
                null
            }
            val margins = if (lp is ViewGroup.MarginLayoutParams) {
                "${lp.leftMargin},${lp.topMargin},${lp.rightMargin},${lp.bottomMargin}"
            } else {
                "n/a"
            }
            sb.append('[').append(child.javaClass.simpleName)
                .append(' ').append(child.measuredWidth).append('x').append(child.measuredHeight)
                .append(" lp=").append(lp?.width).append('x').append(lp?.height)
                .append(" locked=").append(locked)
                .append(" margin=").append(margins)
                .append(" pad=").append(child.paddingLeft).append(',').append(child.paddingTop)
                .append(',').append(child.paddingRight).append(',').append(child.paddingBottom)
                .append("] ")
        }
        return sb.toString()
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
        val getCountX: Method,
        val getCountY: Method,
        val getCellWidth: Method,
        val getCellHeight: Method,
        val getContainer: Method,
        val setCellDimensions: Method
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
