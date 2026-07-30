package com.qimian233.ztool.hook.modules.systemui

import android.content.res.TypedArray
import com.qimian233.ztool.hook.base.BaseHookModule
import io.github.libxposed.api.XposedModuleInterface

/**
 * 设置控制中心 Slider 的样式。
 * 联想在 ToggleSliderView 中展平 Slider 视图并决定使用竖直或者水平的 Slider 。具体由字段 mFromType 决定，
 * 设定为 2，使用水平 Slider，适合大屏设备；设定为 3 ，使用竖直 Slider，适合小屏设备。
 * 系统还会检查屏幕短边的 dp 数，如果短边尺寸小于 720dp，说明 systemui 在小屏幕设备上运行，会强制设定 mFromType 为 3，反之设定为 2.
 * 因此总共需要 2 个 hook，第一个在方法执行前改变字段值，第二个拦截构造方法中调用的 android.view.WindowMetrics.getBounds，返回一个显然是大屏幕/小屏幕设备的 Rect 值。
 */
class SliderStyleHook: BaseHookModule() {

    companion object {
        private const val VERTICAL_TYPE = 3
        private const val HORIZONTAL_TYPE = 2
        private const val LARGE_SCREEN_RECT_VAL = 10000
        private const val SMALL_SCREEN_RECT_VAL = 50
    }

    override fun getModuleName(): String = "customize_slider_style"

    override fun getTargetPackages(): Array<out String> = arrayOf("com.android.systemui")

    private fun isFromBrightnessController(): Boolean =
        Throwable().stackTrace.any { it.className.contains("BrightnessDetailDialogController") }

    override fun handleLoadPackage(param: XposedModuleInterface.PackageLoadedParam?) {

        val prefs = xposed.getRemotePreferences("xposed_module_config")
        val fieldValue: Int = if (prefs.getBoolean("customize_slider_style_value", false)) VERTICAL_TYPE else HORIZONTAL_TYPE
        val rectValue: Int = if (prefs.getBoolean("customize_slider_style_value", false)) SMALL_SCREEN_RECT_VAL else LARGE_SCREEN_RECT_VAL

        val toggleSliderClassName = "com.android.systemui.settings.ToggleSliderView"
        val getIntegerMethod = TypedArray::class.java.getDeclaredMethod(
            "getInteger",
            Int::class.javaPrimitiveType!!,
            Int::class.javaPrimitiveType!!
        )
        hookWithId(getIntegerMethod, "force_horizontal_slider") { chain ->
            if (isFromBrightnessController()) return@hookWithId chain.proceed()
            val index = chain.args[0] as Int
            val original = chain.proceed() as Int
            var result = original
            if (index == 0) {
                val caller = Throwable().stackTrace
                    .firstOrNull { it.className == toggleSliderClassName && it.methodName == "<init>" }
                if (caller != null) {
                    result = fieldValue
                }
            }
            result
        }

        log("QsPanelWidthHook: hooked TypedArray.getInteger for horizontal slider")

        val windowMetricsClass = Class.forName("android.view.WindowMetrics")
        val getBoundsMethod = windowMetricsClass.getDeclaredMethod("getBounds")
        hookWithId(getBoundsMethod, "force_large_screen_bounds") { chain ->
            if (isFromBrightnessController()) return@hookWithId chain.proceed()
            val original = chain.proceed() as android.graphics.Rect
            val caller = Throwable().stackTrace
                .firstOrNull { it.className == toggleSliderClassName && it.methodName == "<init>" }
            if (caller != null) {
                original.set(0, 0, rectValue, rectValue)
            }
            original
        }

        log("QsPanelWidthHook: hooked WindowMetrics.getBounds for large-screen bypass")
    }

}