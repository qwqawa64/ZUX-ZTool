package com.qimian233.ztool.hook.modules.systemui.qs

import android.annotation.SuppressLint
import android.content.res.Configuration
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.AbsSeekBar
import android.widget.FrameLayout
import com.qimian233.ztool.data.keys.ScopeKeys
import com.qimian233.ztool.data.keys.PreferenceKeys
import com.qimian233.ztool.hook.base.AppHookModule
import io.github.libxposed.api.XposedModuleInterface
import java.util.WeakHashMap

/**
 * Modify the QS panel width while ensuring child controls expand and align correctly.
 *
 * Core strategy: instead of modifying QSContainerImpl, modify its parent container
 * qs_frame (FrameLayout). After narrowing qs_frame's measured width and centering it,
 * the layout bounds of QSContainerImpl and all child controls naturally match the
 * visual area, so the TouchHandler needs no extra patching.
 *
 * Only effective when the host window is taller than wide (portrait); the landscape
 * pass-through restores every mutated state (translation, clip flags, child margins,
 * tile columns) so portrait <-> landscape round trips stay consistent.
 */
@SuppressLint("PrivateApi", "DiscouragedApi")
class QsPanelWidthHook : AppHookModule() {

    companion object {
        private const val DEFAULT_WIDTH_PERCENT = 80
        private const val DEFAULT_TILE_COLUMNS = 7
    }

    override fun getModuleName(): String = PreferenceKeys.EXPAND_QS_PANEL_PORTRAIT.name

    override fun getTargetPackages(): Array<String> = arrayOf(ScopeKeys.SYSTEM_UI.packageName)

    /**
     * Orientation gate based on the actual host window size instead of
     * [Configuration].orientation: in split-screen/freeform the configuration can
     * disagree with the window the shade renders in. Falls back to the configuration
     * only before the root view has been laid out.
     */
    private fun isWindowPortrait(view: View): Boolean {
        val root = view.rootView
        if (root.width > 0 && root.height > 0) return root.width <= root.height
        return view.resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT
    }

    override fun handleLoadPackage(param: XposedModuleInterface.PackageLoadedParam) {
        if (param.packageName != ScopeKeys.SYSTEM_UI.packageName) return
        logger.info("QsPanelWidthTestHook: loading")

        val prefs = xposed.getRemotePreferences("xposed_module_config")
        val widthPercent = prefs.getInt(PreferenceKeys.QS_PANEL_WIDTH_PERCENT.name, DEFAULT_WIDTH_PERCENT)
            .coerceIn(0, 100)
        val tileColumns = prefs.getInt(PreferenceKeys.QS_TILE_COLUMNS.name, DEFAULT_TILE_COLUMNS)
            .coerceIn(0, 10)
        val targetWidthRatio = widthPercent / 100f

        // Narrow qs_frame (QSContainerImpl's parent container) and center it,
        // so all descendant controls' layout bounds naturally match the visuals,
        // preserving native touch behavior.
        //
        // Id caches use 0 as the "unresolved" sentinel: getIdentifier() returns 0 on
        // failure, and caching that would make every NO_ID (-1) frame a false match.
        // An unresolved id is retried on the next callback instead of being cached.
        var cachedQsFrameId = 0
        // Only pay the clip-override subtree walk once per portrait period, and keep
        // the original values so the landscape pass-through can restore them.
        var clipDisabled = false
        val clipOverrides = WeakHashMap<View, Pair<Boolean, Boolean>>()
        // rightMargin + gravity originals of frame children mutated while narrowed.
        val childGravityOverrides = WeakHashMap<View, Pair<Int, Int>>()

        val onMeasureMethod = findMethod(
            FrameLayout::class.java,
            "onMeasure",
            Int::class.javaPrimitiveType!!,
            Int::class.javaPrimitiveType!!
        )
        hookWithId(onMeasureMethod, "qs_frame_width_measure") { chain ->
            val frame = chain.thisObject as FrameLayout
            if (cachedQsFrameId == 0) {
                val resolved = frame.resources
                    .getIdentifier("qs_frame", "id", ScopeKeys.SYSTEM_UI.packageName)
                if (resolved != 0) cachedQsFrameId = resolved
            }
            if (cachedQsFrameId == 0 || frame.id != cachedQsFrameId) {
                return@hookWithId chain.proceed()
            }

            val dbgParent = frame.parent as? ViewGroup
            logger.trace(
                "QSW measure: frame=${System.identityHashCode(frame)} avail=" +
                    "${View.MeasureSpec.getSize(chain.args[0] as Int)} left=${frame.left} " +
                    "parentW=${dbgParent?.width} " +
                    "root=${frame.rootView.width}x${frame.rootView.height} " +
                    "tx=${frame.translationX}"
            )
            if (isWindowPortrait(frame) && widthPercent != 0 && frame.rootView.width > 0) {
                // Base both the target width and the centering on the frame's host
                // window (root view), matching the intended "N% of the window,
                // centered" semantics. The parent's measure spec is only a native
                // layout slot (on ZUI tablets it is already a centered ~63% column),
                // so using it would both undersize the frame and mis-place it.
                val root = frame.rootView
                val windowWidth = root.width
                val targetWidth = (windowWidth * targetWidthRatio).toInt()
                val centerOffset = (windowWidth - targetWidth) / 2

                val newArgs = chain.args.toMutableList()
                newArgs[0] = View.MeasureSpec.makeMeasureSpec(
                    targetWidth, View.MeasureSpec.EXACTLY
                )
                chain.proceed(newArgs.toTypedArray())

                for (i in 0 until frame.childCount) {
                    val child = frame.getChildAt(i) ?: continue
                    val lp = child.layoutParams
                    if (lp is FrameLayout.LayoutParams) {
                        if (lp.rightMargin != 0 || lp.gravity != (Gravity.START or Gravity.TOP)) {
                            childGravityOverrides.putIfAbsent(child, lp.rightMargin to lp.gravity)
                        }
                        lp.rightMargin = 0
                        lp.gravity = Gravity.START or Gravity.TOP
                    }
                }

                if (!clipDisabled) {
                    clipDisabled = true
                    disableClip(frame, clipOverrides)
                }

                // Incremental correction: getLocationOnScreen includes the current
                // translation, so nudging by the remaining delta converges even when
                // left/layout state is stale mid-measure.
                val frameLoc = IntArray(2)
                frame.getLocationOnScreen(frameLoc)
                val rootLoc = IntArray(2)
                root.getLocationOnScreen(rootLoc)
                frame.translationX += (rootLoc[0] + centerOffset - frameLoc[0])
            } else {
                restoreFrameState(clipOverrides, childGravityOverrides)
                clipDisabled = false
                frame.translationX = 0f
                chain.proceed()
            }
            null
        }

        logger.info("QsPanelWidthTestHook: hooked FrameLayout.onMeasure for qs_frame")

        var cachedVolumeRowSliderFrameId = 0
        val onLayoutMethod = findMethod(
            FrameLayout::class.java,
            "onLayout",
            Boolean::class.javaPrimitiveType!!,
            Int::class.javaPrimitiveType!!,
            Int::class.javaPrimitiveType!!,
            Int::class.javaPrimitiveType!!,
            Int::class.javaPrimitiveType!!
        )
        hookWithId(onLayoutMethod, "stretch_seekbar_in_frame") { chain ->
            chain.proceed()
            val frame = chain.thisObject as FrameLayout
            if (cachedVolumeRowSliderFrameId == 0) {
                val resolved = frame.resources
                    .getIdentifier("volume_row_slider_frame", "id", ScopeKeys.SYSTEM_UI.packageName)
                if (resolved != 0) cachedVolumeRowSliderFrameId = resolved
            }
            // The QS-panel sliders (media_volume_slider, brightness) live in arbitrary
            // FrameLayouts; volume_row_slider_frame is the volume dialog's own window,
            // which must NOT be touched. Stretch every other portrait frame's seekbars.
            if (frame.id == cachedVolumeRowSliderFrameId) {
                return@hookWithId null
            }
            if (isWindowPortrait(frame)) {
                val contentLeft = frame.paddingLeft
                val contentRight = frame.width - frame.paddingRight
                for (i in 0 until frame.childCount) {
                    val child = frame.getChildAt(i)
                    if (child is AbsSeekBar && child.visibility != View.GONE && child.rotation == 0f) {
                        val lp = child.layoutParams as? FrameLayout.LayoutParams
                        val left = contentLeft + (lp?.leftMargin ?: 0)
                        val right = contentRight - (lp?.rightMargin ?: 0)
                        child.layout(left, child.top, right, child.bottom)
                    }
                }
            }
            null
        }

        logger.info("QsPanelWidthTestHook: hooked FrameLayout.onLayout for SeekBar stretch")

        val pagedTileLayoutClass = param.defaultClassLoader
            .loadClass("com.android.systemui.qs.PagedTileLayout")
        val tileLayoutClass = param.defaultClassLoader
            .loadClass("com.android.systemui.qs.TileLayout")
        val columnsField = findField(tileLayoutClass, "mColumns")
        val pagesField = findField(pagedTileLayoutClass, "mPages")
        val distributeField = findField(pagedTileLayoutClass, "mDistributeTiles")
        val pagedMeasureMethod = findMethod(
            pagedTileLayoutClass,
            "onMeasure",
            Int::class.javaPrimitiveType!!,
            Int::class.javaPrimitiveType!!
        )
        // Native column count observed before the first custom write, used to restore
        // when the window leaves portrait (PagedTileLayout has no config-change hook).
        var pagedDefaultColumns = -1

        hookWithId(pagedMeasureMethod, "tile_columns_adjust") { chain ->
            if (tileColumns != 0) {
                val pagedLayout = chain.thisObject as View
                if (isWindowPortrait(pagedLayout)) {
                    val pages = pagesField.get(pagedLayout) as ArrayList<*>
                    if (pages.isNotEmpty()) {
                        // Only trigger redistribution when the column count actually changes,
                        // to avoid rebuilding pages on every onMeasure frame
                        val currentColumns = columnsField.getInt(pages[0])
                        if (currentColumns != tileColumns) {
                            if (pagedDefaultColumns == -1) pagedDefaultColumns = currentColumns
                            for (page in pages) {
                                columnsField.setInt(page, tileColumns)
                            }
                            distributeField.setBoolean(pagedLayout, true)
                        }
                    }
                } else if (pagedDefaultColumns > 0) {
                    val pages = pagesField.get(pagedLayout) as ArrayList<*>
                    if (pages.isNotEmpty() && columnsField.getInt(pages[0]) == tileColumns) {
                        for (page in pages) {
                            columnsField.setInt(page, pagedDefaultColumns)
                        }
                        distributeField.setBoolean(pagedLayout, true)
                    }
                }
            }
            chain.proceed()
        }

        logger.info("QsPanelWidthTestHook: hooked PagedTileLayout.onMeasure for tile columns")

        val qqsTileLayoutClass = param.defaultClassLoader
            .loadClass($$"com.android.systemui.qs.QuickQSPanel$QQSSideLabelTileLayout")
        val qqsMeasureMethod = findMethod(
            qqsTileLayoutClass,
            "onMeasure",
            Int::class.javaPrimitiveType!!,
            Int::class.javaPrimitiveType!!
        )
        val maxAllowedRowsField = findField(tileLayoutClass, "mMaxAllowedRows")
        val quickQSPanelClass = param.defaultClassLoader
            .loadClass("com.android.systemui.qs.QuickQSPanel")
        val maxTilesField = findField(quickQSPanelClass, "mMaxTiles")
        // mTileLayout is defined in QSPanel (QuickQSPanel's parent), used to read the current mMaxAllowedRows
        val qsPanelTileLayoutField = findField(quickQSPanelClass, "mTileLayout")
        var qqsDefaultColumns = -1
        // Native mMaxTiles cap read from the quick_qs_panel_max_tiles resource, used to
        // restore when the window leaves portrait (id cache: 0 = unresolved, retried).
        var cachedMaxTilesResId = 0
        fun nativeMaxTiles(panel: View): Int {
            if (cachedMaxTilesResId == 0) {
                val resolved = panel.resources
                    .getIdentifier("quick_qs_panel_max_tiles", "integer", ScopeKeys.SYSTEM_UI.packageName)
                if (resolved != 0) cachedMaxTilesResId = resolved
            }
            return if (cachedMaxTilesResId != 0) panel.resources.getInteger(cachedMaxTilesResId) else 0
        }

        hookWithId(qqsMeasureMethod, "tile_columns_qqs") { chain ->
            if (tileColumns != 0) {
                val tileLayout = chain.thisObject as View
                val parent = tileLayout.parent
                if (parent != null && quickQSPanelClass.isInstance(parent)) {
                    if (isWindowPortrait(tileLayout)) {
                        // Only write when the column count actually changes, avoiding unnecessary field updates
                        val currentColumns = columnsField.getInt(tileLayout)
                        if (currentColumns != tileColumns) {
                            if (qqsDefaultColumns == -1) qqsDefaultColumns = currentColumns
                            columnsField.setInt(tileLayout, tileColumns)
                        }
                        // mMaxTiles must be synced independently of the columns gate: when the
                        // native column count equals the custom value the gate above never
                        // fires and the native mMaxTiles cap (from resources) would survive.
                        val rows = maxAllowedRowsField.getInt(tileLayout)
                        if (rows > 0 && maxTilesField.getInt(parent) != tileColumns * rows) {
                            maxTilesField.setInt(parent, tileColumns * rows)
                            logger.trace("QSW qqs: mMaxTiles -> ${tileColumns * rows}")
                        }
                    } else {
                        if (qqsDefaultColumns > 0 && columnsField.getInt(tileLayout) == tileColumns) {
                            columnsField.setInt(tileLayout, qqsDefaultColumns)
                        }
                        // Restore the native cap when leaving portrait. This also heals the
                        // rotation race where setTiles() runs while the root view still has
                        // stale portrait dimensions and the portrait branch re-applied 14.
                        val nativeMax = nativeMaxTiles(tileLayout)
                        if (nativeMax > 0 && maxTilesField.getInt(parent) != nativeMax) {
                            maxTilesField.setInt(parent, nativeMax)
                            logger.trace("QSW qqs: mMaxTiles restored to native $nativeMax")
                        }
                    }
                }
            }
            chain.proceed()
        }

        logger.info("QsPanelWidthTestHook: hooked QQSSideLabelTileLayout.onMeasure for QQS tile columns")

        // QuickQSPanelController.setTiles() truncates the tile list at QuickQSPanel.mMaxTiles,
        // which is (re)initialized from resources on construction and on every config change.
        // Re-apply the custom cap and refresh the list after every native setTiles() so all
        // paths (boot init, tile list changes, configuration changes) are covered. The
        // re-applying guard prevents recursion from invoking setTiles inside its own hook.
        val controllerClass = param.defaultClassLoader
            .loadClass("com.android.systemui.qs.QuickQSPanelController")
        val controllerSetTilesMethod = findMethod(controllerClass, "setTiles")
        // mView is defined in ViewController (QuickQSPanelController's ancestor)
        val controllerViewField = findField(controllerClass, "mView")
        var applyingSetTiles = false

        hookWithId(controllerSetTilesMethod, "qqs_max_tiles_settiles") { chain ->
            chain.proceed()
            if (tileColumns != 0 && !applyingSetTiles) {
                val controller = chain.thisObject
                val panel = controllerViewField.get(controller) as? View ?: return@hookWithId null
                if (!quickQSPanelClass.isInstance(panel)) return@hookWithId null
                if (isWindowPortrait(panel)) {
                    // Read the current mMaxAllowedRows from QuickQSPanel's mTileLayout
                    val tileLayout = qsPanelTileLayoutField.get(panel) ?: return@hookWithId null
                    val rows = maxAllowedRowsField.getInt(tileLayout)
                    if (rows > 0) {
                        val targetMaxTiles = tileColumns * rows
                        if (maxTilesField.getInt(panel) != targetMaxTiles) {
                            maxTilesField.setInt(panel, targetMaxTiles)
                            applyingSetTiles = true
                            try {
                                controllerSetTilesMethod.invoke(controller)
                                logger.trace(
                                    "QSW qqs: setTiles re-applied with mMaxTiles=$targetMaxTiles"
                                )
                            } finally {
                                applyingSetTiles = false
                            }
                        }
                    }
                } else {
                    // Heal the rotation race: setTiles() can run while the root view
                    // still has stale portrait dimensions, leaving the custom cap
                    // applied in landscape. Restore the native cap and refresh.
                    val nativeMax = nativeMaxTiles(panel)
                    if (nativeMax > 0 && maxTilesField.getInt(panel) != nativeMax) {
                        maxTilesField.setInt(panel, nativeMax)
                        applyingSetTiles = true
                        try {
                            controllerSetTilesMethod.invoke(controller)
                            logger.trace(
                                "QSW qqs: setTiles re-applied with native mMaxTiles=$nativeMax"
                            )
                        } finally {
                            applyingSetTiles = false
                        }
                    }
                }
            }
            null
        }

        logger.info("QsPanelWidthTestHook: hooked QuickQSPanelController.setTiles for QQS max tiles")
    }

    private fun disableClip(view: View, overrides: WeakHashMap<View, Pair<Boolean, Boolean>>) {
        if (view is ViewGroup) {
            if (view.clipChildren || view.clipToPadding) {
                overrides.putIfAbsent(view, view.clipChildren to view.clipToPadding)
            }
            view.clipChildren = false
            view.clipToPadding = false
            for (i in 0 until view.childCount) {
                disableClip(view.getChildAt(i), overrides)
            }
        }
    }

    private fun restoreFrameState(
        clipOverrides: WeakHashMap<View, Pair<Boolean, Boolean>>,
        childGravityOverrides: WeakHashMap<View, Pair<Int, Int>>
    ) {
        for ((view, original) in clipOverrides) {
            (view as? ViewGroup)?.let {
                it.clipChildren = original.first
                it.clipToPadding = original.second
            }
        }
        clipOverrides.clear()
        for ((view, original) in childGravityOverrides) {
            val lp = view.layoutParams
            if (lp is FrameLayout.LayoutParams) {
                lp.rightMargin = original.first
                lp.gravity = original.second
            }
        }
        childGravityOverrides.clear()
    }
}
