package com.qimian233.ztool.hook.modules.launcher.grid

import android.graphics.Point
import android.graphics.Rect
import android.view.View
import com.qimian233.ztool.hook.base.HookReflectionHelper.findField
import com.qimian233.ztool.hook.base.HookReflectionHelper.findMethod
import com.qimian233.ztool.hook.base.ModuleLog
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.util.concurrent.ConcurrentHashMap

/**
 * Workspace grid geometry shared by the launcher grid hooks.
 *
 * The live page's own cell size wins over the DeviceProfile design values; the design
 * fallback is only used before the first layout pass. See PLAN_launcher_grid_convergence.md.
 */
object LauncherGridMetrics {

    /**
     * One snapshot of the workspace grid, in pixels.
     *
     * [measured] is false while only the DeviceProfile design fallback is known.
     */
    class Metrics(
        val cols: Int,
        val rows: Int,
        val cellWidth: Int,
        val cellHeight: Int,
        val nominalCellWidth: Int,
        val borderX: Int,
        val borderY: Int,
        val padLeft: Int,
        val padTop: Int,
        val padBottom: Int,
        val contentHeightPx: Int,
        val profileContentHeightPx: Int,
        val iconSizePx: Int,
        val iconDrawablePaddingPx: Int,
        val folderIconSizePx: Int,
        val widgetPaddingLeft: Int,
        val widgetPaddingTop: Int,
        val measured: Boolean
    ) {
        /** Horizontal pitch of the page. */
        val cellPitchX: Int get() = cellWidth + borderX

        /** Vertical pitch of the page. */
        val cellPitchY: Int get() = cellHeight + borderY

        /** Free space above the icon+label block inside one cell, as the host centres it. */
        val rowInset: Int get() = maxOf(0, (cellHeight - profileContentHeightPx) / 2)

        /** Horizontal inset from the cell edge to an app icon. */
        val insetToIcon: Int get() = (cellWidth - iconSizePx) / 2

        /** Horizontal inset from the cell edge to the small-folder circle. */
        val insetToSmallFolder: Int get() = (cellWidth - folderIconSizePx) / 2
    }

    /** Last published snapshot; the static host methods without a page read this. */
    @Volatile
    var cached: Metrics? = null
        private set

    @Volatile
    private var ready = false

    @Volatile
    private var log: ModuleLog? = null

    private lateinit var dpClass: Class<*>
    private lateinit var cellLayoutClass: Class<*>
    private lateinit var dpInv: Field
    private lateinit var invNumColumns: Field
    private lateinit var invNumRows: Field
    private lateinit var dpCellLayoutPadding: Field
    private lateinit var dpBorderSpace: Field
    private lateinit var dpProfileContentHeight: Field
    private lateinit var dpIconSize: Field
    private lateinit var dpIconDrawablePadding: Field
    private lateinit var dpIconTextSize: Field
    private lateinit var dpFolderIconSize: Field
    private lateinit var dpWidgetPadding: Field
    private lateinit var dpCellYPadding: Field
    private lateinit var dpGetCellLayoutWidth: Method
    private lateinit var dpGetCellLayoutHeight: Method
    private lateinit var pageGetCellWidth: Method
    private lateinit var pageGetCellHeight: Method
    private lateinit var utilitiesCalculateTextHeight: Method

    private val textHeightCache = ConcurrentHashMap<Int, Int>()

    /**
     * Resolves the reflection handles once per process.
     *
     * @return false when a required member is missing; callers then keep their design math.
     */
    @Synchronized
    fun install(classLoader: ClassLoader, logger: ModuleLog?): Boolean {
        log = logger
        if (ready) return true
        return try {
            dpClass = classLoader.loadClass("com.android.launcher3.DeviceProfile")
            cellLayoutClass = classLoader.loadClass("com.android.launcher3.CellLayout")
            dpInv = findField(dpClass, "inv")
            invNumColumns = findField(dpInv.type, "numColumns")
            invNumRows = findField(dpInv.type, "numRows")
            dpCellLayoutPadding = findField(dpClass, "cellLayoutPaddingPx")
            dpBorderSpace = findField(dpClass, "cellLayoutBorderSpacePx")
            dpProfileContentHeight = findField(dpClass, "cellHeightPx")
            dpIconSize = findField(dpClass, "iconSizePx")
            dpIconDrawablePadding = findField(dpClass, "iconDrawablePaddingPx")
            dpIconTextSize = findField(dpClass, "iconTextSizePx")
            dpFolderIconSize = findField(dpClass, "folderIconSizePx")
            dpWidgetPadding = findField(dpClass, "widgetPadding")
            dpCellYPadding = findField(dpClass, "cellYPaddingPx")
            dpGetCellLayoutWidth = findMethod(dpClass, "getCellLayoutWidth")
            dpGetCellLayoutHeight = findMethod(dpClass, "getCellLayoutHeight")
            pageGetCellWidth = findMethod(cellLayoutClass, "getCellWidth")
            pageGetCellHeight = findMethod(cellLayoutClass, "getCellHeight")
            utilitiesCalculateTextHeight = findMethod(
                classLoader.loadClass("com.android.launcher3.Utilities"),
                "calculateTextHeight",
                Float::class.javaPrimitiveType
            )
            ready = true
            true
        } catch (t: Throwable) {
            log?.error("grid metrics resolve failed", t)
            false
        }
    }

    /** True once [install] succeeded. */
    val installed: Boolean get() = ready

    /** Walks up from an icon view to the CellLayout page it is placed on. */
    fun pageOf(view: View?): View? {
        if (!ready || view == null) return null
        var ancestor: View? = view
        var levels = 0
        while (ancestor != null && levels < 4) {
            if (cellLayoutClass.isInstance(ancestor)) return ancestor
            ancestor = ancestor.parent as? View
            levels++
        }
        return null
    }

    /** Reads the grid from [page] when its measured cell is available, else from [dp]. */
    fun fromPage(page: View?, dp: Any?): Metrics? {
        if (!ready || dp == null) return null
        return try {
            val inv = dpInv.get(dp) ?: return null
            val cols = invNumColumns.getInt(inv)
            val rows = invNumRows.getInt(inv)
            if (cols <= 0 || rows <= 0) return null
            val padding = dpCellLayoutPadding.get(dp) as Rect
            val border = dpBorderSpace.get(dp) as Point
            val widgetPadding = dpWidgetPadding.get(dp) as Rect
            val iconSizePx = dpIconSize.getInt(dp)
            val iconDrawablePaddingPx = dpIconDrawablePadding.getInt(dp)
            val profileContentHeightPx = dpProfileContentHeight.getInt(dp)
            val contentHeightPx = iconSizePx + iconDrawablePaddingPx +
                (textHeight(dpIconTextSize.getInt(dp)) ?: 0)

            val designWidth = (dpGetCellLayoutWidth.invoke(dp) as Int) - 2 * padding.left
            val designHeight = (dpGetCellLayoutHeight.invoke(dp) as Int) - padding.top - padding.bottom
            val live = page != null && cellLayoutClass.isInstance(page)
            val liveWidth = if (live) ((pageGetCellWidth.invoke(page) as? Int) ?: -1) else -1
            val liveHeight = if (live) ((pageGetCellHeight.invoke(page) as? Int) ?: -1) else -1

            Metrics(
                cols = cols,
                rows = rows,
                cellWidth = if (liveWidth > 0) liveWidth
                else (designWidth - (cols - 1) * border.x) / cols,
                cellHeight = if (liveHeight > 0) liveHeight
                else (designHeight - (rows - 1) * border.y) / rows,
                nominalCellWidth = designWidth / cols,
                borderX = border.x,
                borderY = border.y,
                padLeft = padding.left,
                padTop = padding.top,
                padBottom = padding.bottom,
                contentHeightPx = contentHeightPx,
                profileContentHeightPx = profileContentHeightPx,
                iconSizePx = iconSizePx,
                iconDrawablePaddingPx = iconDrawablePaddingPx,
                folderIconSizePx = dpFolderIconSize.getInt(dp),
                widgetPaddingLeft = widgetPadding.left,
                widgetPaddingTop = widgetPadding.top,
                measured = liveWidth > 0 && liveHeight > 0
            )
        } catch (t: Throwable) {
            log?.error("grid metrics read failed", t)
            null
        }
    }

    /** Publishes a snapshot for the host methods that have no page reference. */
    fun publish(metrics: Metrics?) {
        cached = metrics
    }

    /** Icon height + label + drawable padding, i.e. what the host centres inside a cell. */
    fun contentHeightPx(dp: Any?): Int? {
        if (!ready || dp == null) return null
        return try {
            dpIconSize.getInt(dp) + dpIconDrawablePadding.getInt(dp) +
                (textHeight(dpIconTextSize.getInt(dp)) ?: 0)
        } catch (t: Throwable) {
            null
        }
    }

    /** Current DeviceProfile.cellYPaddingPx, the workspace content centring inset. */
    fun cellYPaddingPx(dp: Any?): Int? {
        if (!ready || dp == null) return null
        return try {
            dpCellYPadding.getInt(dp)
        } catch (t: Throwable) {
            null
        }
    }

    /** Writes DeviceProfile.cellYPaddingPx; false when the field is unavailable. */
    fun writeCellYPaddingPx(dp: Any?, value: Int): Boolean {
        if (!ready || dp == null) return false
        return try {
            dpCellYPadding.setInt(dp, value)
            true
        } catch (t: Throwable) {
            false
        }
    }

    /** Memoized Utilities.calculateTextHeight; the host measures with a fresh Paint per call. */
    private fun textHeight(textSizePx: Int): Int? {
        textHeightCache[textSizePx]?.let { return it }
        return try {
            val height = utilitiesCalculateTextHeight.invoke(null, textSizePx.toFloat()) as Int
            textHeightCache[textSizePx] = height
            height
        } catch (t: Throwable) {
            log?.error("calculateTextHeight failed", t)
            null
        }
    }
}
