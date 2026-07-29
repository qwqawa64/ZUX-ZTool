package com.qimian233.ztool.hook.modules.systemui

import android.annotation.SuppressLint
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
 * getModuleName() 返回 "test_hook"，始终启用，无需前端开关。
 */
@SuppressLint("PrivateApi")
class QsPanelWidthTestHook : BaseHookModule() {

    companion object {
        /** 目标宽度占屏幕宽度的比例 (0.0~1.0) */
        private const val TARGET_WIDTH_PERCENT = 0.75f
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
            val screenWidth = container.context.resources.displayMetrics.widthPixels
            val originalWidth = View.MeasureSpec.getSize(chain.args[0] as Int)
            val targetWidth = (screenWidth * TARGET_WIDTH_PERCENT).toInt()

            // —— before: 替换 widthMeasureSpec，使整个子控件链用目标宽度测量 ——
            val newArgs = chain.args.toMutableList()
            newArgs[0] = View.MeasureSpec.makeMeasureSpec(
                targetWidth, View.MeasureSpec.EXACTLY
            )
            chain.proceed(newArgs.toTypedArray())

            // —— after: 修正子控件 gravity 和 off-screen 偏移 ——

            // 1. 子控件贴靠 START（左侧），zero rightMargin 释放右侧空间
            for (i in 0 until container.childCount) {
                val child = container.getChildAt(i) ?: continue
                val lp = child.layoutParams
                if (lp is FrameLayout.LayoutParams) {
                    lp.rightMargin = 0
                    lp.gravity = Gravity.START or Gravity.TOP
                }
            }

            // 2. 将容器向左平移，防止右侧超出屏幕
            val overflow = container.measuredWidth - screenWidth
            container.translationX = if (overflow > 0) -overflow.toFloat() else 0f

            if (DEBUG) {
                log("QsPanelWidthTestHook: width $originalWidth -> $targetWidth " +
                    "overflow=$overflow (screen=$screenWidth, ratio=$TARGET_WIDTH_PERCENT)")
            }
        }

        log("QsPanelWidthTestHook: hooked QSContainerImpl.onMeasure")
    }
}
