package com.qimian233.ztool.hook.modules.systemui

import android.annotation.SuppressLint
import android.content.res.Configuration
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.AbsSeekBar
import android.widget.FrameLayout
import com.qimian233.ztool.hook.base.BaseHookModule
import io.github.libxposed.api.XposedModuleInterface

/**
 * 测试 Hook — 修改 QS 面板宽度并保证子控件正确扩展和对齐。
 *
 * 核心策略：在 onMeasure 执行前将 widthMeasureSpec 替换为目标宽度，
 * 使整个测量链（childMeasureSpec → QSPanelContainer → QSPanel → TileLayout）
 * 统一使用目标宽度。然后在 after 阶段修正 gravity 和 off-screen 偏移。
 *
 * 仅在竖屏 (Portrait) 下生效，横屏时直接透传原始逻辑。
 *
 * getModuleName() 返回 "test_hook"，始终启用，无需前端开关。
 */
@SuppressLint("PrivateApi")
class QsPanelWidthHook : BaseHookModule() {

    companion object {
        /** 默认面板宽度百分比 (0-100) */
        private const val DEFAULT_WIDTH_PERCENT = 80
        /** 默认磁贴列数 */
        private const val DEFAULT_TILE_COLUMNS = 7
    }

    override fun getModuleName(): String = "expand_qs_panel_portrait"

    override fun getTargetPackages(): Array<String> = arrayOf("com.android.systemui")

    override fun handleLoadPackage(param: XposedModuleInterface.PackageLoadedParam) {
        if (param.packageName != "com.android.systemui") return
        log("QsPanelWidthTestHook: loading")

        // 从 SharedPreferences 读取配置
        val prefs = xposed.getRemotePreferences("xposed_module_config")
        val widthPercent = prefs.getInt("qs_panel_width_percent", DEFAULT_WIDTH_PERCENT)
            .coerceIn(0, 100)
        val tileColumns = prefs.getInt("qs_tile_columns", DEFAULT_TILE_COLUMNS)
            .coerceIn(0, 10)
        val targetWidthRatio = widthPercent / 100f

        val qsContainerClass = param.defaultClassLoader
            .loadClass("com.android.systemui.qs.QSContainerImpl")

        val onMeasureMethod = findMethod(
            qsContainerClass,
            "onMeasure",
            Int::class.javaPrimitiveType!!,
            Int::class.javaPrimitiveType!!
        )

        hookWithId(onMeasureMethod, "qs_panel_width_measure") { chain ->
            val container = chain.thisObject as ViewGroup

            // 仅在竖屏 (Portrait) 下修改宽度逻辑
            val orientation = container.context.resources.configuration.orientation
            val isPortrait = orientation == Configuration.ORIENTATION_PORTRAIT

            if (isPortrait && widthPercent != 0) {
                // 竖屏：替换 widthMeasureSpec + 修正 gravity / off-screen
                val screenWidth = container.context.resources.displayMetrics.widthPixels
                val originalWidth = View.MeasureSpec.getSize(chain.args[0] as Int)
                val targetWidth = (screenWidth * targetWidthRatio).toInt()

                val newArgs = chain.args.toMutableList()
                newArgs[0] = View.MeasureSpec.makeMeasureSpec(
                    targetWidth, View.MeasureSpec.EXACTLY
                )
                chain.proceed(newArgs.toTypedArray())

                // 子控件贴靠 START，zero rightMargin
                for (i in 0 until container.childCount) {
                    val child = container.getChildAt(i) ?: continue
                    val lp = child.layoutParams
                    if (lp is FrameLayout.LayoutParams) {
                        lp.rightMargin = 0
                        lp.gravity = Gravity.START or Gravity.TOP
                    }
                }

                // 将容器居中于屏幕：先抵消祖先链偏移贴靠左侧，再加居中偏移
                // 用 parent.screenLocation + container.left 而非 container.screenLocation，
                // 因为后者会受 translationX 自身影响形成反馈振荡
                val parentLocation = IntArray(2)
                (container.parent as View).getLocationOnScreen(parentLocation)
                val totalLeftOffset = parentLocation[0] + container.left
                val centerOffset = (screenWidth - targetWidth) / 2
                val overflow = container.measuredWidth - screenWidth
                container.translationX = -(
                    totalLeftOffset + (if (overflow > 0) overflow else 0) - centerOffset
                ).toFloat()

                if (DEBUG) {
                    log("QsPanelWidthTestHook: width $originalWidth -> $targetWidth " +
                        "screenLeft=$totalLeftOffset centerOffset=$centerOffset " +
                        "overflow=$overflow (screen=$screenWidth)")
                }
            } else {
                // 横屏：直接透传原始逻辑，重置竖屏修改
                container.translationX = 0f
                chain.proceed()
            }
        }

        log("QsPanelWidthTestHook: hooked QSContainerImpl.onMeasure")

        // Hook FrameLayout.onLayout：检测包含 SeekBar 子控件的 FrameLayout，
        // 将 SeekBar 拉伸至 FrameLayout 宽度，修复 SeekBarNps 等不跟随拉伸的问题
        // 排除 volume_row_slider_frame（音量调节弹窗中的独立滑块）
        // 资源 ID 延迟缓存，热路径仅做 int 比较，避免每次 getResourceEntryName 的 JNI 开销
        var cachedVolumeRowSliderFrameId = -1
        val frameLayoutClass = FrameLayout::class.java
        val onLayoutMethod = findMethod(
            frameLayoutClass,
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
            // 延迟解析并缓存 volume_row_slider_frame 的资源 ID
            if (cachedVolumeRowSliderFrameId == -1) {
                cachedVolumeRowSliderFrameId = frame.resources
                    .getIdentifier("volume_row_slider_frame", "id", "com.android.systemui")
            }
            // 仅在竖屏下执行拉伸，且跳过音量弹窗和旋转 90° 的纵向 Slider
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
                        if (child is AbsSeekBar && child.visibility != View.GONE) {
                            // 跳过旋转过的纵向 Slider（mFromType==3，rotation=90°）
                            if (child.rotation != 0f) continue
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

        log("QsPanelWidthTestHook: hooked FrameLayout.onLayout for SeekBar stretch")

        // Hook PagedTileLayout.onMeasure：增加每行磁贴列数以匹配面板宽度
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
                // 仅在竖屏下修改列数
                val orientation = pagedLayout.context.resources.configuration.orientation
                val isPortrait = orientation == Configuration.ORIENTATION_PORTRAIT
                if (isPortrait) {
                    val pages = pagesField.get(pagedLayout) as ArrayList<*>
                    if (pages.isNotEmpty()) {
                        for (page in pages) {
                            columnsField.setInt(page, tileColumns)
                        }
                    }
                    // 强制触发磁贴重分布
                    distributeField.setBoolean(pagedLayout, true)
                }
            }
            chain.proceed()
        }

        log("QsPanelWidthTestHook: hooked PagedTileLayout.onMeasure for tile columns")

        // Hook QQSSideLabelTileLayout.onMeasure：收起状态（QQS）的磁贴列数
        // 必须在 TileLayout.onMeasure 之前触发，因为 QQSSideLabelTileLayout.onMeasure
        // 在 super.onMeasure() 之前就调用了 updateMaxRows()
        val qqsTileLayoutClass = param.defaultClassLoader
            .loadClass("com.android.systemui.qs.QuickQSPanel\$QQSSideLabelTileLayout")
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

        hookWithId(qqsMeasureMethod, "tile_columns_qqs") { chain ->
            if (tileColumns != 0) {
                val tileLayout = chain.thisObject as View
                val orientation = tileLayout.context.resources.configuration.orientation
                if (orientation == Configuration.ORIENTATION_PORTRAIT) {
                    columnsField.setInt(tileLayout, tileColumns)
                    // 同步更新 QQS 总数上限：mMaxTiles = mMaxAllowedRows * mColumns
                    val parent = tileLayout.parent
                    if (parent != null && quickQSPanelClass.isInstance(parent)) {
                        val rows = maxAllowedRowsField.getInt(tileLayout)
                        maxTilesField.setInt(parent, tileColumns * rows)
                    }
                }
            }
            chain.proceed()
        }

        log("QsPanelWidthTestHook: hooked QQSSideLabelTileLayout.onMeasure for QQS tile columns")
    }
}
