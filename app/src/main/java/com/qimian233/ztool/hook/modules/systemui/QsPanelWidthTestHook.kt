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
class QsPanelWidthTestHook : BaseHookModule() {

    companion object {
        /** 目标宽度占屏幕宽度的比例 (0.0~1.0) */
        private const val TARGET_WIDTH_PERCENT = 0.8f
        /** 每行 QS 磁贴目标列数 */
        private const val TARGET_TILE_COLUMNS = 7
    }

    override fun getModuleName(): String = "test_hook"

    override fun getTargetPackages(): Array<String> = arrayOf("com.android.systemui")

    override fun handleLoadPackage(param: XposedModuleInterface.PackageLoadedParam) {
        if (param.packageName != "com.android.systemui") return
        log("QsPanelWidthTestHook: loading")

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

            if (isPortrait) {
                // 竖屏：替换 widthMeasureSpec + 修正 gravity / off-screen
                val screenWidth = container.context.resources.displayMetrics.widthPixels
                val originalWidth = View.MeasureSpec.getSize(chain.args[0] as Int)
                val targetWidth = (screenWidth * TARGET_WIDTH_PERCENT).toInt()

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
            // 跳过音量条布局，其余包含 SeekBar 的 FrameLayout 均拉伸
            if (frame.id != cachedVolumeRowSliderFrameId) {
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
            val pagedLayout = chain.thisObject as View
            // 仅在竖屏下修改列数
            val orientation = pagedLayout.context.resources.configuration.orientation
            val isPortrait = orientation == Configuration.ORIENTATION_PORTRAIT
            if (isPortrait) {
                val pages = pagesField.get(pagedLayout) as ArrayList<*>
                if (pages.isNotEmpty()) {
                    for (page in pages) {
                        columnsField.setInt(page, TARGET_TILE_COLUMNS)
                    }
                }
                // 强制触发磁贴重分布
                distributeField.setBoolean(pagedLayout, true)
            }
            chain.proceed()
        }

        log("QsPanelWidthTestHook: hooked PagedTileLayout.onMeasure for tile columns")
    }
}
