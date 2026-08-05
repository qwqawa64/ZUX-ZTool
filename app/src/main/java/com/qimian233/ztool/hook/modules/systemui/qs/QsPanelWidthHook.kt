package com.qimian233.ztool.hook.modules.systemui.qs

import android.annotation.SuppressLint
import android.content.res.Configuration
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.AbsSeekBar
import android.widget.FrameLayout
import com.qimian233.ztool.data.PreferenceKeys
import com.qimian233.ztool.hook.base.AppHookModule
import io.github.libxposed.api.XposedModuleInterface

/**
 * 修改 QS 面板宽度并保证子控件正确扩展和对齐。
 *
 * 核心策略：不改 QSContainerImpl，而是改它的父容器 qs_frame (FrameLayout)。
 * 缩窄 qs_frame 的测量宽度并居中后，QSContainerImpl 及所有子控件
 * 的 layout bounds 自然匹配视觉范围，TouchHandler 无需额外修补。
 *
 * 仅在竖屏 (Portrait) 下生效，横屏时直接透传原始逻辑。
 */
@SuppressLint("PrivateApi", "DiscouragedApi")
class QsPanelWidthHook : AppHookModule() {

    companion object {
        private const val DEFAULT_WIDTH_PERCENT = 80
        private const val DEFAULT_TILE_COLUMNS = 7
    }

    override fun getModuleName(): String = "expand_qs_panel_portrait"

    override fun getTargetPackages(): Array<String> = arrayOf("com.android.systemui")

    override fun handleLoadPackage(param: XposedModuleInterface.PackageLoadedParam) {
        if (param.packageName != "com.android.systemui") return
        logger.info("QsPanelWidthTestHook: loading")

        val prefs = xposed.getRemotePreferences("xposed_module_config")
        val widthPercent = prefs.getInt(PreferenceKeys.QS_PANEL_WIDTH_PERCENT.name, DEFAULT_WIDTH_PERCENT)
            .coerceIn(0, 100)
        val tileColumns = prefs.getInt(PreferenceKeys.QS_TILE_COLUMNS.name, DEFAULT_TILE_COLUMNS)
            .coerceIn(0, 10)
        val targetWidthRatio = widthPercent / 100f

        // ── 核心：Hook qs_frame 的 onMeasure ──
        // 缩窄 qs_frame（QSContainerImpl 的父容器）并居中，
        // 所有后代控件的 layout bounds 自然匹配视觉，保留原生触控行为。
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
                    .getIdentifier("qs_frame", "id", "com.android.systemui")
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

        // ── Hook FrameLayout.onLayout：拉伸 SeekBar ──
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
                    .getIdentifier("volume_row_slider_frame", "id", "com.android.systemui")
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

        // ── Hook PagedTileLayout.onMeasure：磁贴列数 ──
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
                        // 仅在列数真正变化时才触发重新分配，避免每帧 onMeasure 都重建页面
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

        // ── Hook QQSSideLabelTileLayout.onMeasure：QQS 磁贴列数 ──
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
        // mTileLayout 定义在 QSPanel（QuickQSPanel 的父类），用于读取当前的 mMaxAllowedRows
        val qsPanelTileLayoutField = findField(quickQSPanelClass, "mTileLayout")

        hookWithId(qqsMeasureMethod, "tile_columns_qqs") { chain ->
            if (tileColumns != 0) {
                val tileLayout = chain.thisObject as View
                val orientation = tileLayout.context.resources.configuration.orientation
                if (orientation == Configuration.ORIENTATION_PORTRAIT) {
                    // 仅在列数真正变化时才写入，避免不必要的字段更新
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

        // ── Hook QuickQSPanelController.onConfigurationChanged：主题切换后恢复 mMaxTiles ──
        // QuickQSPanelController.onConfigurationChanged() 会从资源读取默认值重置 mMaxTiles
        // 并立即调用 setTiles() 截断磁贴列表。本 Hook 在原方法执行后重新应用自定义值并刷新。
        val controllerClass = param.defaultClassLoader
            .loadClass("com.android.systemui.qs.QuickQSPanelController")
        val controllerOnConfigMethod = findMethod(controllerClass, "onConfigurationChanged")
        val controllerSetTilesMethod = findMethod(controllerClass, "setTiles")
        // mView 定义在 ViewController（QuickQSPanelController 的祖先）
        val controllerViewField = findField(controllerClass, "mView")

        hookWithId(controllerOnConfigMethod, "qqs_max_tiles_config_fix") { chain ->
            chain.proceed()
            if (tileColumns != 0) {
                val controller = chain.thisObject
                val panel = controllerViewField.get(controller) as? View ?: return@hookWithId null
                if (!quickQSPanelClass.isInstance(panel)) return@hookWithId null
                val orientation = panel.context.resources.configuration.orientation
                if (orientation == Configuration.ORIENTATION_PORTRAIT) {
                    // 从 QuickQSPanel 的 mTileLayout 读取当前 mMaxAllowedRows
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
