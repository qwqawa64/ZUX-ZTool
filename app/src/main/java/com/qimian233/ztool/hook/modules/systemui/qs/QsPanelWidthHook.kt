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

/**
 * Modify the QS panel width while ensuring child controls expand and align correctly.
 *
 * Core strategy: instead of modifying QSContainerImpl, modify its parent container
 * qs_frame (FrameLayout). After narrowing qs_frame's measured width and centering it,
 * the layout bounds of QSContainerImpl and all child controls naturally match the
 * visual area, so the TouchHandler needs no extra patching.
 *
 * Only effective in portrait mode; landscape passes through the original logic unchanged.
 */
@SuppressLint("PrivateApi", "DiscouragedApi")
class QsPanelWidthHook : AppHookModule() {

    companion object {
        private const val DEFAULT_WIDTH_PERCENT = 80
        private const val DEFAULT_TILE_COLUMNS = 7
    }

    override fun getModuleName(): String = "expand_qs_panel_portrait"

    override fun getTargetPackages(): Array<String> = arrayOf(ScopeKeys.SYSTEM_UI.packageName)

    override fun handleLoadPackage(param: XposedModuleInterface.PackageLoadedParam) {
        if (param.packageName != ScopeKeys.SYSTEM_UI.packageName) return
        logger.info("QsPanelWidthTestHook: loading")

        val prefs = xposed.getRemotePreferences("xposed_module_config")
        val widthPercent = prefs.getInt(PreferenceKeys.QS_PANEL_WIDTH_PERCENT.name, DEFAULT_WIDTH_PERCENT)
            .coerceIn(0, 100)
        val tileColumns = prefs.getInt(PreferenceKeys.QS_TILE_COLUMNS.name, DEFAULT_TILE_COLUMNS)
            .coerceIn(0, 10)
        val targetWidthRatio = widthPercent / 100f

        // ── Core: hook qs_frame's onMeasure ──
        // Narrow qs_frame (QSContainerImpl's parent container) and center it,
        // so all descendant controls' layout bounds naturally match the visuals,
        // preserving native touch behavior.
        var cachedQsFrameId = -1
        val onMeasureMethod = findMethod(
            FrameLayout::class.java,
            "onMeasure",
            Int::class.javaPrimitiveType!!,
            Int::class.javaPrimitiveType!!
        )
        hookWithId(onMeasureMethod, "qs_frame_width_measure") { chain ->
            val frame = chain.thisObject as FrameLayout
            if (cachedQsFrameId == -1) {
                cachedQsFrameId = frame.resources
                    .getIdentifier("qs_frame", "id", ScopeKeys.SYSTEM_UI.packageName)
            }
            if (frame.id != cachedQsFrameId) {
                return@hookWithId chain.proceed()
            }

            val orientation = frame.context.resources.configuration.orientation
            val isPortrait = orientation == Configuration.ORIENTATION_PORTRAIT

            if (isPortrait && widthPercent != 0) {
                val screenWidth = frame.context.resources.displayMetrics.widthPixels
                val targetWidth = (screenWidth * targetWidthRatio).toInt()
                val centerOffset = (screenWidth - targetWidth) / 2

                val newArgs = chain.args.toMutableList()
                newArgs[0] = View.MeasureSpec.makeMeasureSpec(
                    targetWidth, View.MeasureSpec.EXACTLY
                )
                chain.proceed(newArgs.toTypedArray())

                for (i in 0 until frame.childCount) {
                    val child = frame.getChildAt(i) ?: continue
                    val lp = child.layoutParams
                    if (lp is FrameLayout.LayoutParams) {
                        lp.rightMargin = 0
                        lp.gravity = Gravity.START or Gravity.TOP
                    }
                }

                fun disableClip(view: View) {
                    if (view is ViewGroup) {
                        view.clipChildren = false
                        view.clipToPadding = false
                        for (i in 0 until view.childCount) {
                            disableClip(view.getChildAt(i))
                        }
                    }
                }
                disableClip(frame)

                val parentLocation = IntArray(2)
                (frame.parent as View).getLocationOnScreen(parentLocation)
                val totalLeftOffset = parentLocation[0] + frame.left
                frame.translationX = (centerOffset - totalLeftOffset).toFloat()
            } else {
                frame.translationX = 0f
                chain.proceed()
            }
            null
        }

        logger.info("QsPanelWidthTestHook: hooked FrameLayout.onMeasure for qs_frame")

        // ── Hook FrameLayout.onLayout: stretch SeekBar ──
        var cachedVolumeRowSliderFrameId = -1
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
            if (cachedVolumeRowSliderFrameId == -1) {
                cachedVolumeRowSliderFrameId = frame.resources
                    .getIdentifier("volume_row_slider_frame", "id", ScopeKeys.SYSTEM_UI.packageName)
            }
            val orientation = frame.context.resources.configuration.orientation
            val isPortrait = orientation == Configuration.ORIENTATION_PORTRAIT
            if (isPortrait && frame.id != cachedVolumeRowSliderFrameId) {
                var stretchCount = 0
                for (i in 0 until frame.childCount) {
                    val child = frame.getChildAt(i)
                    if (child is AbsSeekBar && child.visibility != View.GONE) {
                        stretchCount++
                    }
                }
                if (stretchCount > 0) {
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
            }
            null
        }

        logger.info("QsPanelWidthTestHook: hooked FrameLayout.onLayout for SeekBar stretch")

        // ── Hook PagedTileLayout.onMeasure: tile column count ──
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
        hookWithId(pagedMeasureMethod, "tile_columns_adjust") { chain ->
            if (tileColumns != 0) {
                val pagedLayout = chain.thisObject as View
                val orientation = pagedLayout.context.resources.configuration.orientation
                if (orientation == Configuration.ORIENTATION_PORTRAIT) {
                    val pages = pagesField.get(pagedLayout) as ArrayList<*>
                    if (pages.isNotEmpty()) {
                        // Only trigger redistribution when the column count actually changes,
                        // to avoid rebuilding pages on every onMeasure frame
                        val currentColumns = columnsField.getInt(pages[0])
                        if (currentColumns != tileColumns) {
                            for (page in pages) {
                                columnsField.setInt(page, tileColumns)
                            }
                            distributeField.setBoolean(pagedLayout, true)
                        }
                    }
                }
            }
            chain.proceed()
        }

        logger.info("QsPanelWidthTestHook: hooked PagedTileLayout.onMeasure for tile columns")

        // ── Hook QQSSideLabelTileLayout.onMeasure: QQS tile column count ──
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

        hookWithId(qqsMeasureMethod, "tile_columns_qqs") { chain ->
            if (tileColumns != 0) {
                val tileLayout = chain.thisObject as View
                val orientation = tileLayout.context.resources.configuration.orientation
                if (orientation == Configuration.ORIENTATION_PORTRAIT) {
                    // Only write when the column count actually changes, avoiding unnecessary field updates
                    val currentColumns = columnsField.getInt(tileLayout)
                    if (currentColumns != tileColumns) {
                        columnsField.setInt(tileLayout, tileColumns)
                        val parent = tileLayout.parent
                        if (parent != null && quickQSPanelClass.isInstance(parent)) {
                            val rows = maxAllowedRowsField.getInt(tileLayout)
                            maxTilesField.setInt(parent, tileColumns * rows)
                        }
                    }
                }
            }
            chain.proceed()
        }

        logger.info("QsPanelWidthTestHook: hooked QQSSideLabelTileLayout.onMeasure for QQS tile columns")

        // ── Hook QuickQSPanelController.onConfigurationChanged: restore mMaxTiles after theme switch ──
        // QuickQSPanelController.onConfigurationChanged() reads the default value from resources,
        // resets mMaxTiles, and immediately calls setTiles() to truncate the tile list. This hook
        // re-applies the custom value and refreshes after the original method runs.
        val controllerClass = param.defaultClassLoader
            .loadClass("com.android.systemui.qs.QuickQSPanelController")
        val controllerOnConfigMethod = findMethod(controllerClass, "onConfigurationChanged")
        val controllerSetTilesMethod = findMethod(controllerClass, "setTiles")
        // mView is defined in ViewController (QuickQSPanelController's ancestor)
        val controllerViewField = findField(controllerClass, "mView")

        hookWithId(controllerOnConfigMethod, "qqs_max_tiles_config_fix") { chain ->
            chain.proceed()
            if (tileColumns != 0) {
                val controller = chain.thisObject
                val panel = controllerViewField.get(controller) as? View ?: return@hookWithId null
                if (!quickQSPanelClass.isInstance(panel)) return@hookWithId null
                val orientation = panel.context.resources.configuration.orientation
                if (orientation == Configuration.ORIENTATION_PORTRAIT) {
                    // Read the current mMaxAllowedRows from QuickQSPanel's mTileLayout
                    val tileLayout = qsPanelTileLayoutField.get(panel) ?: return@hookWithId null
                    val rows = maxAllowedRowsField.getInt(tileLayout)
                    if (rows > 0) {
                        val targetMaxTiles = tileColumns * rows
                        val currentMaxTiles = maxTilesField.getInt(panel)
                        if (currentMaxTiles != targetMaxTiles) {
                            maxTilesField.setInt(panel, targetMaxTiles)
                            controllerSetTilesMethod.invoke(controller)
                            logger.info(
                                "QsPanelWidthTestHook: restored mMaxTiles=$targetMaxTiles after config change"
                            )
                        }
                    }
                }
            }
            null
        }

        logger.info("QsPanelWidthTestHook: hooked QuickQSPanelController.onConfigurationChanged")
    }
}
