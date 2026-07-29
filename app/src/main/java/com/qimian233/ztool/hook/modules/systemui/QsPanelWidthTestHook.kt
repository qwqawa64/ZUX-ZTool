package com.qimian233.ztool.hook.modules.systemui

import android.annotation.SuppressLint
import android.content.res.Configuration
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
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

                // 将容器贴靠屏幕左侧：抵消外部父容器施加的左侧偏移
                // container.left 是父容器分配给此控件的左侧位置（含 parent padding + child margin）
                val parentLeftOffset = container.left
                val overflow = container.measuredWidth - screenWidth
                container.translationX = -(
                    parentLeftOffset + (if (overflow > 0) overflow else 0)
                ).toFloat()

                if (DEBUG) {
                    log("QsPanelWidthTestHook: width $originalWidth -> $targetWidth " +
                        "leftOffset=$parentLeftOffset overflow=$overflow " +
                        "(screen=$screenWidth, ratio=$TARGET_WIDTH_PERCENT)")
                }
            } else {
                // 横屏：直接透传原始逻辑，重置竖屏修改
                container.translationX = 0f
                chain.proceed()
            }
        }

        log("QsPanelWidthTestHook: hooked QSContainerImpl.onMeasure")
    }
}
