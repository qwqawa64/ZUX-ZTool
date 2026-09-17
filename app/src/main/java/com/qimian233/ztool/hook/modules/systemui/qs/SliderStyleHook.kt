package com.qimian233.ztool.hook.modules.systemui.qs

import android.content.res.TypedArray
import android.graphics.Rect
import com.qimian233.ztool.data.keys.ScopeKeys
import com.qimian233.ztool.data.keys.PreferenceKeys
import com.qimian233.ztool.hook.base.AppHookModule
import io.github.libxposed.api.XposedModuleInterface

/**
 * Set the style of the control center Slider.
 * Lenovo's ToggleSliderView flattens the Slider view and decides whether to use a
 * vertical or horizontal Slider, controlled by the field mFromType: set to 2 to use
 * a horizontal Slider (suitable for large-screen devices); set to 3 to use a vertical
 * Slider (suitable for small-screen devices).
 * The system also checks the dp size of the screen's short edge: if it is smaller than
 * 720dp, SystemUI is running on a small-screen device and mFromType is forced to 3,
 * otherwise it is set to 2.
 * Therefore two hooks are needed: the first changes the field value before the method
 * runs; the second intercepts android.view.WindowMetrics.getBounds called in the
 * constructor and returns a Rect that clearly corresponds to a large/small-screen device.
 */
class SliderStyleHook: AppHookModule() {

    companion object {
        private const val VERTICAL_TYPE = 3
        private const val HORIZONTAL_TYPE = 2
        private const val LARGE_SCREEN_RECT_VAL = 10000
        private const val SMALL_SCREEN_RECT_VAL = 50
    }

    override fun getModuleName(): String = "customize_slider_style"

    override fun getTargetPackages(): Array<out String> = arrayOf(ScopeKeys.SYSTEM_UI.packageName)

    private fun isFromBrightnessController(): Boolean =
        Throwable().stackTrace.any { it.className.contains("BrightnessDetailDialogController") }

    override fun handleLoadPackage(param: XposedModuleInterface.PackageLoadedParam) {

        val prefs = xposed.getRemotePreferences("xposed_module_config")
        val fieldValue: Int = if (prefs.getBoolean(PreferenceKeys.CUSTOMIZE_SLIDER_STYLE_VALUE.name, false)) VERTICAL_TYPE else HORIZONTAL_TYPE
        val rectValue: Int = if (prefs.getBoolean(PreferenceKeys.CUSTOMIZE_SLIDER_STYLE_VALUE.name, false)) SMALL_SCREEN_RECT_VAL else LARGE_SCREEN_RECT_VAL

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

        logger.info("QsPanelWidthHook: hooked TypedArray.getInteger for horizontal slider")

        val windowMetricsClass = Class.forName("android.view.WindowMetrics")
        val getBoundsMethod = windowMetricsClass.getDeclaredMethod("getBounds")
        hookWithId(getBoundsMethod, "force_large_screen_bounds") { chain ->
            if (isFromBrightnessController()) return@hookWithId chain.proceed()
            val original = chain.proceed() as Rect
            val caller = Throwable().stackTrace
                .firstOrNull { it.className == toggleSliderClassName && it.methodName == "<init>" }
            if (caller != null) {
                original.set(0, 0, rectValue, rectValue)
            }
            original
        }

        logger.info("QsPanelWidthHook: hooked WindowMetrics.getBounds for large-screen bypass")
    }

}