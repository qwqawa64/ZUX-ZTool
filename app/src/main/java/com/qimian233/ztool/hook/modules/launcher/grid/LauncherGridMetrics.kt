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
 * fallback is only used before the first layout pass. Geometry is read through the host's
 * WorkspaceProfile when present, else through the pre-18.6 DeviceProfile fields.
 * See docs/research/zui_launcher_wide_grid_geometry.md.
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

    /** DeviceProfile geometry access, as a field set (pre-18.6) or a profile object (18.6+). */
    private interface Geometry {
        fun cellLayoutPadding(dp: Any): Rect?
        fun cellLayoutBorderSpacePx(dp: Any): Point?
        fun cellHeightPx(dp: Any): Int?
        fun iconSizePx(dp: Any): Int?
        fun iconTextSizePx(dp: Any): Int?
        fun iconDrawablePaddingPx(dp: Any): Int?
        fun folderIconSizePx(dp: Any): Int?
        fun widgetPadding(dp: Any): Rect?
        fun cellYPaddingPx(dp: Any): Int?
        fun writeCellYPaddingPx(dp: Any, value: Int): Boolean
        fun horizontalMarginPx(dp: Any): Int?
        fun writeHorizontalMarginPx(dp: Any, value: Int): Boolean
    }

    /** Pre-18.6 layout: every geometry value is a mutable field on DeviceProfile. */
    private class LegacyGeometry(
        private val cellLayoutPadding: Field,
        private val borderSpace: Field,
        private val cellHeight: Field,
        private val iconSize: Field,
        private val iconTextSize: Field,
        private val iconDrawablePadding: Field,
        private val folderIconSize: Field,
        private val widgetPadding: Field,
        private val cellYPadding: Field,
        private val horizontalMargin: Field
    ) : Geometry {
        override fun cellLayoutPadding(dp: Any): Rect? = cellLayoutPadding.get(dp) as? Rect

        override fun cellLayoutBorderSpacePx(dp: Any): Point? = borderSpace.get(dp) as? Point

        override fun cellHeightPx(dp: Any): Int? = cellHeight.getInt(dp)

        override fun iconSizePx(dp: Any): Int? = iconSize.getInt(dp)

        override fun iconTextSizePx(dp: Any): Int? = iconTextSize.getInt(dp)

        override fun iconDrawablePaddingPx(dp: Any): Int? = iconDrawablePadding.getInt(dp)

        override fun folderIconSizePx(dp: Any): Int? = folderIconSize.getInt(dp)

        override fun widgetPadding(dp: Any): Rect? = widgetPadding.get(dp) as? Rect

        override fun cellYPaddingPx(dp: Any): Int? = cellYPadding.getInt(dp)

        override fun writeCellYPaddingPx(dp: Any, value: Int): Boolean =
            try {
                cellYPadding.setInt(dp, value)
                true
            } catch (t: Throwable) {
                false
            }

        override fun horizontalMarginPx(dp: Any): Int? = horizontalMargin.getInt(dp)

        override fun writeHorizontalMarginPx(dp: Any, value: Int): Boolean =
            try {
                horizontalMargin.setInt(dp, value)
                true
            } catch (t: Throwable) {
                false
            }
    }

    /**
     * 18.6+ layout: geometry lives in an immutable WorkspaceProfile reached from DeviceProfile.
     * Scalar writes rebuild the profile through its data-class `copy` and install it back.
     */
    private class ProfileGeometry(
        private val getWorkspaceProfile: Method,
        private val setWorkspaceProfile: Method,
        private val getFolderProfile: Method?,
        private val folderIconSize: Method?,
        private val cellLayoutPadding: Method,
        private val borderSpace: Method,
        private val cellHeight: Method,
        private val iconSize: Method,
        private val iconTextSize: Method,
        private val iconDrawablePadding: Method,
        private val widgetPadding: Method,
        private val cellYPadding: Method,
        private val horizontalMargin: Method,
        private val copy: Method,
        private val copyGetters: Array<Method>,
        private val marginIndex: Int,
        private val cellYPaddingIndex: Int,
        private val logger: ModuleLog?
    ) : Geometry {

        private fun profile(dp: Any): Any? = try {
            getWorkspaceProfile.invoke(dp)
        } catch (t: Throwable) {
            null
        }

        override fun cellLayoutPadding(dp: Any): Rect? = read(dp, cellLayoutPadding) as? Rect

        override fun cellLayoutBorderSpacePx(dp: Any): Point? = read(dp, borderSpace) as? Point

        override fun cellHeightPx(dp: Any): Int? = read(dp, cellHeight) as? Int

        override fun iconSizePx(dp: Any): Int? = read(dp, iconSize) as? Int

        override fun iconTextSizePx(dp: Any): Int? = read(dp, iconTextSize) as? Int

        override fun iconDrawablePaddingPx(dp: Any): Int? = read(dp, iconDrawablePadding) as? Int

        override fun folderIconSizePx(dp: Any): Int? = try {
            val getFolder = getFolderProfile
            val getSize = folderIconSize
            if (getFolder == null || getSize == null) {
                null
            } else {
                val folder = getFolder.invoke(dp)
                if (folder == null) null else getSize.invoke(folder) as? Int
            }
        } catch (t: Throwable) {
            null
        }

        override fun widgetPadding(dp: Any): Rect? = read(dp, widgetPadding) as? Rect

        override fun cellYPaddingPx(dp: Any): Int? = read(dp, cellYPadding) as? Int

        override fun writeCellYPaddingPx(dp: Any, value: Int): Boolean =
            writeInt(dp, cellYPaddingIndex, value)

        override fun horizontalMarginPx(dp: Any): Int? = read(dp, horizontalMargin) as? Int

        override fun writeHorizontalMarginPx(dp: Any, value: Int): Boolean =
            writeInt(dp, marginIndex, value)

        private fun read(dp: Any, getter: Method): Any? = try {
            val target = profile(dp)
            if (target == null) null else getter.invoke(target)
        } catch (t: Throwable) {
            null
        }

        /** Rebuilds the profile with one int replaced; the copy keeps the same Rect/Point objects. */
        private fun writeInt(dp: Any, index: Int, value: Int): Boolean = try {
            val current = profile(dp)
            if (current == null) {
                false
            } else if (copyGetters[index].invoke(current) as? Int == value) {
                true
            } else {
                val args = Array<Any?>(copyGetters.size) { i ->
                    if (i == index) value else copyGetters[i].invoke(current)
                }
                val updated = copy.invoke(current, *args)
                if (updated == null) {
                    false
                } else {
                    setWorkspaceProfile.invoke(dp, updated)
                    true
                }
            }
        } catch (t: Throwable) {
            logger?.error("grid metrics: profile write failed", t)
            false
        }
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
    private var geometry: Geometry? = null
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
            dpGetCellLayoutWidth = findMethod(dpClass, "getCellLayoutWidth")
            dpGetCellLayoutHeight = findMethod(dpClass, "getCellLayoutHeight")
            pageGetCellWidth = findMethod(cellLayoutClass, "getCellWidth")
            pageGetCellHeight = findMethod(cellLayoutClass, "getCellHeight")
            utilitiesCalculateTextHeight = findMethod(
                classLoader.loadClass("com.android.launcher3.Utilities"),
                "calculateTextHeight",
                Float::class.javaPrimitiveType
            )
            geometry = resolveProfileGeometry(classLoader) ?: resolveLegacyGeometry()
            ready = geometry != null
            ready
        } catch (t: Throwable) {
            log?.error("grid metrics resolve failed", t)
            false
        }
    }

    /** True once [install] succeeded. */
    val installed: Boolean get() = ready

    /** 18.6+ geometry: DeviceProfile#getWorkspaceProfile plus the WorkspaceProfile getters. */
    private fun resolveProfileGeometry(classLoader: ClassLoader): Geometry? {
        return try {
            val profileClass = classLoader.loadClass("com.android.launcher3.deviceprofile.WorkspaceProfile")
            // Optional: a missing FolderProfile only removes the folder icon size, instead
            // of disabling the whole geometry layer.
            val folderClass = try {
                classLoader.loadClass("com.android.launcher3.deviceprofile.FolderProfile")
            } catch (t: Throwable) {
                log?.warn("grid metrics: FolderProfile unavailable, folder icon size disabled")
                null
            }
            val copy = profileClass.declaredMethods.firstOrNull { it.name == "copy" }
            if (copy == null) {
                log?.error("grid metrics: WorkspaceProfile#copy missing")
                return null
            }
            val getters = Array<Method?>(COPY_GETTERS.size) { i ->
                resolveGetter(profileClass, COPY_GETTERS[i])
            }
            val missing = COPY_GETTERS.indices.filter { getters[it] == null }
            if (missing.isNotEmpty()) {
                log?.warn(
                    "grid metrics: WorkspaceProfile getters missing: " +
                        missing.joinToString { COPY_GETTERS[it] }
                )
                return null
            }
            if (copy.parameterCount != getters.size ||
                getters.indices.any { copy.parameterTypes[it] != getters[it]?.returnType }
            ) {
                log?.error("grid metrics: WorkspaceProfile#copy signature changed")
                return null
            }
            val getFolderProfile = try {
                findMethod(dpClass, "getFolderProfile")
            } catch (t: Throwable) {
                null
            }
            val folderIconSize = folderClass?.let {
                try {
                    findMethod(it, "getFolderIconSizePx")
                } catch (t: Throwable) {
                    null
                }
            }
            ProfileGeometry(
                findMethod(dpClass, "getWorkspaceProfile"),
                findMethod(dpClass, "setWorkspaceProfile", profileClass),
                getFolderProfile,
                folderIconSize,
                findMethod(profileClass, "getCellLayoutPaddingPx"),
                findMethod(profileClass, "getCellLayoutBorderSpacePx"),
                findMethod(profileClass, "getCellHeightPx"),
                findMethod(profileClass, "getIconSizePx"),
                findMethod(profileClass, "getIconTextSizePx"),
                findMethod(profileClass, "getIconDrawablePaddingPx"),
                findMethod(profileClass, "getWidgetPadding"),
                findMethod(profileClass, "getCellYPaddingPx"),
                findMethod(profileClass, "getDesiredWorkspaceHorizontalMarginPx"),
                copy,
                getters.requireNoNulls(),
                COPY_GETTERS.indexOf("getDesiredWorkspaceHorizontalMarginPx"),
                COPY_GETTERS.indexOf("getCellYPaddingPx"),
                log
            )
        } catch (t: Throwable) {
            log?.warn("grid metrics: WorkspaceProfile geometry unavailable, trying legacy fields")
            null
        }
    }

    /** Resolves a Kotlin property getter, tolerating both the `isX` and `getIsX` forms. */
    private fun resolveGetter(profileClass: Class<*>, name: String): Method? {
        try {
            return findMethod(profileClass, name)
        } catch (_: Throwable) {
            if (!name.startsWith("is")) return null
        }
        return try {
            findMethod(profileClass, "get" + name.replaceFirstChar { it.uppercaseChar() })
        } catch (_: Throwable) {
            null
        }
    }

    /** Pre-18.6 geometry: the same values as DeviceProfile fields. */
    private fun resolveLegacyGeometry(): Geometry? = try {
        LegacyGeometry(
            findField(dpClass, "cellLayoutPaddingPx"),
            findField(dpClass, "cellLayoutBorderSpacePx"),
            findField(dpClass, "cellHeightPx"),
            findField(dpClass, "iconSizePx"),
            findField(dpClass, "iconTextSizePx"),
            findField(dpClass, "iconDrawablePaddingPx"),
            findField(dpClass, "folderIconSizePx"),
            findField(dpClass, "widgetPadding"),
            findField(dpClass, "cellYPaddingPx"),
            findField(dpClass, "desiredWorkspaceHorizontalMarginPx")
        )
    } catch (t: Throwable) {
        log?.error("grid metrics: no DeviceProfile geometry available", t)
        null
    }

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

    /** Workspace page insets, the value Workspace#setInsets re-applies to its pages. */
    fun cellLayoutPaddingPx(dp: Any?): Rect? {
        val g = geometry ?: return null
        if (dp == null) return null
        return try {
            g.cellLayoutPadding(dp)
        } catch (t: Throwable) {
            null
        }
    }

    /** Workspace cell gap, read by the square solve. */
    fun cellLayoutBorderSpacePx(dp: Any?): Point? {
        val g = geometry ?: return null
        if (dp == null) return null
        return try {
            g.cellLayoutBorderSpacePx(dp)
        } catch (t: Throwable) {
            null
        }
    }

    /** Current workspace side margin; the host derives its workspace padding from it. */
    fun desiredWorkspaceHorizontalMarginPx(dp: Any?): Int? {
        val g = geometry ?: return null
        if (dp == null) return null
        return try {
            g.horizontalMarginPx(dp)
        } catch (t: Throwable) {
            null
        }
    }

    /** Writes the workspace side margin; false when the host geometry is unavailable. */
    fun writeDesiredWorkspaceHorizontalMarginPx(dp: Any?, value: Int): Boolean {
        val g = geometry ?: return false
        if (dp == null) return false
        return try {
            g.writeHorizontalMarginPx(dp, value)
        } catch (t: Throwable) {
            false
        }
    }

    /** Reads the grid from [page] when its measured cell is available, else from [dp]. */
    fun fromPage(page: View?, dp: Any?): Metrics? {
        val g = geometry ?: return null
        if (!ready || dp == null) return null
        return try {
            val inv = dpInv.get(dp) ?: return null
            val cols = invNumColumns.getInt(inv)
            val rows = invNumRows.getInt(inv)
            if (cols <= 0 || rows <= 0) return null
            val padding = g.cellLayoutPadding(dp) ?: return null
            val border = g.cellLayoutBorderSpacePx(dp) ?: return null
            val widgetPadding = g.widgetPadding(dp) ?: return null
            val iconSizePx = g.iconSizePx(dp) ?: return null
            val iconDrawablePaddingPx = g.iconDrawablePaddingPx(dp) ?: return null
            val profileContentHeightPx = g.cellHeightPx(dp) ?: return null
            val contentHeightPx = iconSizePx + iconDrawablePaddingPx +
                (textHeight(g.iconTextSizePx(dp) ?: 0) ?: 0)

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
                folderIconSizePx = g.folderIconSizePx(dp) ?: 0,
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
        val g = geometry ?: return null
        if (!ready || dp == null) return null
        return try {
            (g.iconSizePx(dp) ?: return null) + (g.iconDrawablePaddingPx(dp) ?: return null) +
                (textHeight(g.iconTextSizePx(dp) ?: 0) ?: 0)
        } catch (t: Throwable) {
            null
        }
    }

    /** Current workspace content centring inset (DeviceProfile cellYPaddingPx). */
    fun cellYPaddingPx(dp: Any?): Int? {
        val g = geometry ?: return null
        if (!ready || dp == null) return null
        return try {
            g.cellYPaddingPx(dp)
        } catch (t: Throwable) {
            null
        }
    }

    /** Writes the content centring inset; false when the geometry is unavailable. */
    fun writeCellYPaddingPx(dp: Any?, value: Int): Boolean {
        val g = geometry ?: return false
        if (!ready || dp == null) return false
        return try {
            g.writeCellYPaddingPx(dp, value)
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

    /**
     * WorkspaceProfile#copy parameters in declaration order; the getters are used to rebuild
     * a profile with one changed value. See WorkspaceProfile's constructor in the host.
     */
    private val COPY_GETTERS = arrayOf(
        "getIconScale",
        "getIconSizePx",
        "getIconTextSizePx",
        "getIconDrawablePaddingPx",
        "getCellScaleToFit",
        "getCellWidthPx",
        "getCellHeightPx",
        "getCellLayoutBorderSpacePx",
        "getDesiredWorkspaceHorizontalMarginPx",
        "getCellYPaddingPx",
        "getMaxIconTextLineCount",
        "getIconCenterVertically",
        "getDesiredWorkspaceHorizontalMarginOriginalPx",
        "getWorkspaceContentScale",
        "getWorkspaceSpringLoadedMinNextPageVisiblePx",
        "getMaxEmptySpace",
        "getWorkspaceTopPadding",
        "getWorkspaceBottomPadding",
        "getWorkspaceCellPaddingXPx",
        "getEdgeMarginPx",
        "getCellLayoutPaddingPx",
        "getWorkspacePadding",
        "getPanelCount",
        "getGridVisualizationPaddingX",
        "getGridVisualizationPaddingY",
        "getWorkspacePageIndicatorHeight",
        "getWorkspacePageIndicatorOverlapWorkspace",
        "isItemsLabelHidden",
        "getIconDrawablePaddingOriginalPx",
        "getCellLayoutHeightSpecification",
        "getCellLayoutWidthSpecification",
        "getCellSize",
        "getScale",
        "getExtraSpace",
        "getFlingToDeleteThresholdVelocity",
        "getWidgetPadding",
        "getEdgeMarginTopPx",
        "getRealAvailableHeight",
        "getHotseatAreaHeight",
        "getBadgeCircleHeight",
        "getEditBadgeCircleHeight",
        "getBadgeTextSize"
    )
}
