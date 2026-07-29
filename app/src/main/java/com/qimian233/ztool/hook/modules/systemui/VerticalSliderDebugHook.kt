package com.qimian233.ztool.hook.modules.systemui

import android.annotation.SuppressLint
import android.content.res.TypedArray
import com.qimian233.ztool.hook.base.BaseHookModule
import io.github.libxposed.api.XposedModuleInterface

/**
 * 测试 Hook — 强制所有 ToggleSliderView 以竖直 Slider 模式（mFromType=3）构造。
 *
 * 在 TypedArray.getInteger 层面拦截 ToggleSliderView 构造器对 styleable 属性
 * 的读取，将控制 mFromType 的属性值改写为 3。构造器后续会自然使用竖直布局资源
 * (R.layout.status_bar_toggle_slider_detail) 并执行旋转、layoutDirection 等全套设置。
 *
 * getModuleName() 返回 "hook_test"，始终启用，无需前端开关。
 */
@SuppressLint("PrivateApi")
class VerticalSliderDebugHook : BaseHookModule() {

    companion object {
        private const val SYSTEMUI_PACKAGE = "com.android.systemui"
        private const val TOGGLE_SLIDER_CLASS =
            "com.android.systemui.settings.ToggleSliderView"
        /** ZuiToggleSliderView styleable 中控制 mFromType 的属性索引 (0) */
        private const val SLIDER_TYPE_ATTR_INDEX = 0
        /** 目标 mFromType 值：3 = 竖直双 Slider（亮度+音量） */
        private const val TARGET_SLIDER_TYPE = 3
    }

    override fun getModuleName(): String = "hook_test"

    override fun getTargetPackages(): Array<String> = arrayOf(SYSTEMUI_PACKAGE)

    override fun handleLoadPackage(param: XposedModuleInterface.PackageLoadedParam) {
        if (param.packageName != SYSTEMUI_PACKAGE) return
        log("VerticalSliderDebugHook: loading")

        // Hook TypedArray.getInteger(int, int)
        val getIntegerMethod = TypedArray::class.java.getDeclaredMethod(
            "getInteger",
            Int::class.javaPrimitiveType!!,
            Int::class.javaPrimitiveType!!
        )
        hookWithId(getIntegerMethod, "force_vertical_slider_type") { chain ->
            val index = chain.args[0] as Int
            val originalResult = chain.proceed() as Int
            // 仅在 ToggleSliderView 构造器读取 styleable[0] 时改写返回值
            var result = originalResult
            if (index == SLIDER_TYPE_ATTR_INDEX) {
                val caller = Throwable().stackTrace
                    .firstOrNull { it.className == TOGGLE_SLIDER_CLASS && it.methodName == "<init>" }
                if (caller != null) {
                    result = TARGET_SLIDER_TYPE
                    if (DEBUG) log("VerticalSliderDebugHook: forcing mFromType=$TARGET_SLIDER_TYPE " +
                        "(original=$originalResult)")
                }
            }
            result
        }

        log("VerticalSliderDebugHook: hooked TypedArray.getInteger for ToggleSliderView.<init>")
    }
}
