package com.qimian233.ztool.hook.modules.systemui

import android.annotation.SuppressLint
import android.view.View
import android.widget.SeekBar
import com.qimian233.ztool.hook.base.BaseHookModule
import io.github.libxposed.api.XposedModuleInterface

/**
 * 测试 Hook — 强制所有 ToggleSliderView 使用竖直样式（mFromType=3）。
 *
 * 在 onAttachedToWindow 时设置 mFromType=3 并旋转 SeekBar，
 * 方便在没有竖直 Slider 的设备上调试相关布局问题。
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
        val fromTypeField = findField(sliderViewClass, "mFromType")
        val brightnessSliderField = findField(sliderViewClass, "mBrightnessSlider")
        val mediaVolumeSliderField = findField(sliderViewClass, "mMediaVolumeSlider")

        // 在 onAttachedToWindow 设置竖直样式
        val onAttachMethod = findMethod(
            sliderViewClass,
            "onAttachedToWindow"
        )
        hookWithId(onAttachMethod, "force_vertical_slider") { chain ->
            chain.proceed()
            val view = chain.thisObject as View
            // 设置 mFromType = 3（竖直样式）
            fromTypeField.setInt(view, 3)

            // 旋转亮度 Slider
            val brightnessSlider = brightnessSliderField.get(view) as? SeekBar
            if (brightnessSlider != null) {
                brightnessSlider.layoutDirection = View.LAYOUT_DIRECTION_LTR
                brightnessSlider.rotation = 90f
            }

            // 旋转音量 Slider
            val mediaVolumeSlider = mediaVolumeSliderField.get(view) as? SeekBar
            if (mediaVolumeSlider != null) {
                mediaVolumeSlider.layoutDirection = View.LAYOUT_DIRECTION_LTR
                mediaVolumeSlider.rotation = 90f
            }
        }

        log("VerticalSliderDebugHook: hooked ToggleSliderView.onAttachedToWindow")
    }
}
