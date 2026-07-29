package com.qimian233.ztool.hook.modules.systemui

import android.annotation.SuppressLint
import android.view.View
import com.qimian233.ztool.hook.base.BaseHookModule
import io.github.libxposed.api.XposedModuleInterface

/**
 * 测试 Hook — 修改 QS 面板宽度。
 *
 * 通过拦截 QSContainerImpl.onMeasure 在测量完成后覆写宽度，
 * 将 QS 面板宽度缩小到屏幕宽度的指定比例。
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

        hookWithId(onMeasureMethod, "qs_panel_width_test_measure") { chain ->
            // 先执行原始测量逻辑
            chain.proceed()

            val container = chain.thisObject as View
            val originalWidth = container.measuredWidth
            val screenWidth = container.context.resources.displayMetrics.widthPixels
            val targetWidth = (screenWidth * TARGET_WIDTH_PERCENT).toInt()

            if (originalWidth != targetWidth) {
                // setMeasuredDimension 是 protected，通过反射调用
                val setMeasuredDimension = View::class.java
                    .getDeclaredMethod(
                        "setMeasuredDimension",
                        Int::class.javaPrimitiveType,
                        Int::class.javaPrimitiveType
                    )
                setMeasuredDimension.isAccessible = true
                setMeasuredDimension.invoke(container, targetWidth, container.measuredHeight)
                if (DEBUG) {
                    log("QsPanelWidthTestHook: width $originalWidth -> $targetWidth " +
                        "(screen=$screenWidth, ratio=$TARGET_WIDTH_PERCENT)")
                }
            }
        }

        log("QsPanelWidthTestHook: hooked QSContainerImpl.onMeasure")
    }
}
