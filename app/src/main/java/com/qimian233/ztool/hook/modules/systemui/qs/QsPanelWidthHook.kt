package com.qimian233.ztool.hook.modules.systemui.qs

import android.annotation.SuppressLint
import android.content.res.AssetManager
import android.content.res.Configuration
import android.content.res.Resources
import android.util.DisplayMetrics
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
        logger.warn("root.width = ${root.width}, root.height = ${root.height}")
        return view.resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT
    }

    @SuppressLint("DiscouragedPrivateApi")
    override fun handleLoadPackage(param: XposedModuleInterface.PackageLoadedParam) {
        logger.info("QsPanelWidthTestHook: loading")

        val widthPercent = remotePreferences.getInt(PreferenceKeys.QS_PANEL_WIDTH_PERCENT.name, DEFAULT_WIDTH_PERCENT)
            .coerceIn(0, 100)
        val tileColumns = remotePreferences.getInt(PreferenceKeys.QS_TILE_COLUMNS.name, DEFAULT_TILE_COLUMNS)
            .coerceIn(0, 10)
        val targetWidthRatio = widthPercent / 100f

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
                if (resolved != 0) cachedQsFrameId = resolved else logger.warn("Failed to resolve QS frame id!")
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

        // Native portrait default of the PagedTileLayout pages: SideLabelTileLayout.
        // updateResources() derives mColumns as min(mResourceColumns, mMaxColumns) with
        // mResourceColumns read from R.integer.quick_settings_num_columns. Resolve that
        // resource eagerly from the SystemUI APK here — capturing it lazily inside the
        // onMeasure hook is unreliable because the first measure can arrive with empty
        // pages, or after the field was already mutated.
        var pagedDefaultColumns = -1
        runCatching {
            val assets = AssetManager::class.java.getDeclaredConstructor()
                .apply { isAccessible = true }
                .newInstance() as AssetManager
            AssetManager::class.java.getDeclaredMethod("addAssetPath", String::class.java)
                .invoke(assets, param.applicationInfo.sourceDir)
            val res = Resources(assets, DisplayMetrics(), Configuration())
            val id = res.getIdentifier(
                "quick_settings_num_columns", "integer", ScopeKeys.SYSTEM_UI.packageName
            )
            if (id != 0) pagedDefaultColumns = res.getInteger(id)
        }.onFailure {
            logger.error("QsPanelWidth: failed to resolve native qs tile columns", it)
        }
        logger.info("QsPanelWidthHook: pagedDefaultColumns = $pagedDefaultColumns")

        hookWithId(pagedMeasureMethod, "tile_columns_adjust", 100) { chain ->
            logger.info("tileColumns = $tileColumns")
            if (tileColumns != 0) {
                val pagedLayout = chain.thisObject as View
                val isPortrait = isWindowPortrait(pagedLayout)
                logger.debug("isWindowPortrait = $isPortrait")
                if (isPortrait) {
                    val pages = pagesField.get(pagedLayout) as ArrayList<*>
                    if (pages.isNotEmpty()) {
                        // Only trigger redistribution when the column count actually changes,
                        // to avoid rebuilding pages on every onMeasure frame
                        val currentColumns = columnsField.getInt(pages[0])
                        logger.debug("current = $currentColumns, target = $tileColumns")
                        if (currentColumns != tileColumns) {
                            if (pagedDefaultColumns == -1) {
                                pagedDefaultColumns = currentColumns
                            }
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
        val quickQSPanelClass = param.defaultClassLoader
            .loadClass("com.android.systemui.qs.QuickQSPanel")
        val maxTilesField = findField(quickQSPanelClass, "mMaxTiles")
        var qqsDefaultColumns = -1
        var maxTilesResId = 0
        fun nativeMaxTiles(panel: View): Int {
            if (maxTilesResId == 0) {
                maxTilesResId = panel.resources
                    .getIdentifier("quick_qs_panel_max_tiles", "integer", ScopeKeys.SYSTEM_UI.packageName)
            }
            return if (maxTilesResId != 0) panel.resources.getInteger(maxTilesResId) else 0
        }
        var maxRowsResId = 0
        fun nativeMaxRows(panel: View): Int {
            if (maxRowsResId == 0) {
                maxRowsResId = panel.resources
                    .getIdentifier("quick_qs_panel_max_rows", "integer", ScopeKeys.SYSTEM_UI.packageName)
            }
            return if (maxRowsResId != 0) panel.resources.getInteger(maxRowsResId) else 0
        }

        hookWithId(qqsMeasureMethod, "tile_columns_qqs") { chain ->
            if (tileColumns != 0) {
                val tileLayout = chain.thisObject as View
                val parent = tileLayout.parent
                if (parent != null && quickQSPanelClass.isInstance(parent)) {
                    val isPortrait = isWindowPortrait(tileLayout)
                    if (isPortrait) {
                        val currentColumns = columnsField.getInt(tileLayout)
                        if (currentColumns != tileColumns) {
                            if (qqsDefaultColumns == -1) qqsDefaultColumns = currentColumns
                            columnsField.setInt(tileLayout, tileColumns)
                        }
                    } else if (qqsDefaultColumns > 0 && columnsField.getInt(tileLayout) == tileColumns) {
                        columnsField.setInt(tileLayout, qqsDefaultColumns)
                    }
                }
            }
            chain.proceed()
        }

        logger.info("QsPanelWidthTestHook: hooked QQSSideLabelTileLayout.onMeasure for QQS tile columns")

        val controllerClass = param.defaultClassLoader
            .loadClass("com.android.systemui.qs.QuickQSPanelController")
        val controllerSetTilesMethod = findMethod(controllerClass, "setTiles")
        val controllerViewField = findField(controllerClass, "mView")

        hookWithId(controllerSetTilesMethod, "qqs_max_tiles_settiles") { chain ->
            if (tileColumns != 0) {
                val controller = chain.thisObject
                val panel = controllerViewField.get(controller) as? View
                if (panel != null && quickQSPanelClass.isInstance(panel)) {
                    val isPortrait = panel.resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT
                    val target = if (isPortrait) {
                        val rows = nativeMaxRows(panel)
                        if (rows > 0) tileColumns * rows else 0
                    } else {
                        nativeMaxTiles(panel)
                    }
                    if (target > 0 && maxTilesField.getInt(panel) != target) {
                        maxTilesField.setInt(panel, target)
                        logger.trace("QSW qqs: mMaxTiles -> $target (${if (isPortrait) "portrait" else "native"})")
                    }
                }
            }
            chain.proceed()
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
