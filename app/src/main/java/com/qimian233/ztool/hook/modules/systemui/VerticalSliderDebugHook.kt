package com.qimian233.ztool.hook.modules.systemui

import android.annotation.SuppressLint
import android.view.View
import android.widget.SeekBar
import com.qimian233.ztool.hook.base.BaseHookModule
import io.github.libxposed.api.XposedModuleInterface

/**
 * 测试 Hook — 强制所有 ToggleSliderView 的 SeekBar 旋转 90° 模拟竖直 Slider。
 *
 * 不改 mFromType（布局 XML 在构造时已按原始值加载，改后 onMeasure
 * 走竖直分支会导致视图结构不匹配、Slider 塌缩）。仅施加 rotation +
 * layoutDirection 变换，与 QsPanelWidthHook 的 rotation != 0f 门禁配合，
 * 让 SeekBar 拉伸正确跳过，方便在没有竖直 Slider 的设备上调试。
 *
 * getModuleName() 返回 "hook_test"，始终启用，无需前端开关。
 */
@SuppressLint("PrivateApi")
class VerticalSliderDebugHook : BaseHookModule() {

    companion object {
        private const val SYSTEMUI_PACKAGE = "com.android.systemui"
    }

    override fun getModuleName(): String = "hook_test"

    override fun getTargetPackages(): Array<String> = arrayOf(SYSTEMUI_PACKAGE)

    override fun handleLoadPackage(param: XposedModuleInterface.PackageLoadedParam) {
        if (param.packageName != SYSTEMUI_PACKAGE) return
        log("VerticalSliderDebugHook: loading")

        val sliderViewClass = param.defaultClassLoader
            .loadClass("com.android.systemui.settings.ToggleSliderView")
        val brightnessSliderField = findField(sliderViewClass, "mBrightnessSlider")
        val mediaVolumeSliderField = findField(sliderViewClass, "mMediaVolumeSlider")

        hookWithId(findMethod(sliderViewClass, "onAttachedToWindow"),
            "force_vertical_slider_debug") { chain ->
            chain.proceed()
            val view = chain.thisObject as View
            // 只旋转 SeekBar，不改 mFromType（保留原始布局和 onMeasure 逻辑）
            val brightnessSlider = brightnessSliderField.get(view) as? SeekBar
            if (brightnessSlider != null) {
                brightnessSlider.layoutDirection = View.LAYOUT_DIRECTION_LTR
                brightnessSlider.rotation = 90f
            }
            val mediaVolumeSlider = mediaVolumeSliderField.get(view) as? SeekBar
            if (mediaVolumeSlider != null) {
                mediaVolumeSlider.layoutDirection = View.LAYOUT_DIRECTION_LTR
                mediaVolumeSlider.rotation = 90f
            }
        }

        log("VerticalSliderDebugHook: hooked ToggleSliderView.onAttachedToWindow")
    }
}
