package com.qimian233.ztool.hook.modules.systemui.misc

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioManager
import android.util.AttributeSet
import android.widget.SeekBar
import com.qimian233.ztool.data.keys.PreferenceKeys
import com.qimian233.ztool.data.keys.ScopeKeys
import com.qimian233.ztool.hook.base.AppHookModule
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam

/**
 * SystemUI companion of FineVolumeSteps: raises the control-center media
 * volume slider's raw max so the slider keeps mapping 1:1 onto the stream's
 * (now 150) steps.
 *
 * The stock ToggleSliderView hardcodes `mMediaVolumeSlider.setMax(150000)`
 * (15 steps x 10000 raw units per step) in its constructor, while every
 * conversion around it is step-count-agnostic:
 * - setVolumeProgress(v)            -> slider progress = v * 10000
 * - getVolumeProgressReal$1()       -> level = ceil(progress / 10000)
 * - drag (ToggleSliderView$2)       -> setStreamVolume(3, ceil(progress/10000))
 *
 * With the stream raised to 150 steps the hardcoded max makes the slider top
 * out at volume 15/150. Re-asserting max = getStreamMaxVolume(3) * 10000 (and
 * re-applying the current volume) realigns the whole chain; nothing else in
 * SystemUI needs changing.
 *
 * Shares the FINE_VOLUME_STEPS preference with the system-server hook
 * (FineVolumeSteps), which must be active too for the stream max to rise.
 */
@SuppressLint("PrivateApi", "DiscouragedPrivateApi")
class FineVolumeStepsSliderHook : AppHookModule() {

    override fun getModuleName(): String = PreferenceKeys.FINE_VOLUME_STEPS.name

    override fun getTargetPackages(): Array<String> = arrayOf(ScopeKeys.SYSTEM_UI.packageName)

    override fun handleLoadPackage(param: PackageLoadedParam) {
        val classLoader = param.defaultClassLoader
        try {
            val sliderClass = classLoader.loadClass(TOGGLE_SLIDER_VIEW_CLASS)
            val ctor = sliderClass.getDeclaredConstructor(
                Context::class.java, AttributeSet::class.java, Int::class.javaPrimitiveType
            )
            hookWithId(ctor, HOOK_ID) { chain ->
                chain.proceed()
                try {
                    realignSliderMax(chain.thisObject)
                } catch (t: Throwable) {
                    logger.error("Failed to realign volume slider max", t)
                }
                null
            }
            logger.info("Fine volume steps slider hook installed")
        } catch (t: Throwable) {
            logger.error("Failed to hook ToggleSliderView constructor", t)
        }
    }

    /**
     * Sets max = streamMax * 10000 and re-applies the current volume, so the
     * slider spans the full fine-grained range instead of topping out at the
     * stock 15-step maximum.
     */
    private fun realignSliderMax(sliderView: Any) {
        val sliderField = findField(sliderView.javaClass, MEDIA_VOLUME_SLIDER_FIELD)
        sliderField.isAccessible = true
        val slider = sliderField.get(sliderView) as? SeekBar ?: return
        val context = (sliderView as? android.view.View)?.context ?: return
        val am = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return

        val streamMax = try {
            am.getStreamMaxVolume(STREAM_MUSIC)
        } catch (_: Throwable) {
            -1
        }
        // A binder failure must not leave the slider at 0 max; skip instead.
        if (streamMax <= 0) {
            logger.warn("fine volume steps: stream max unavailable, slider left stock")
            return
        }
        val rawMax = streamMax * RAW_UNITS_PER_STEP
        if (slider.max == rawMax) return
        slider.max = rawMax
        slider.progress = am.getStreamVolume(STREAM_MUSIC) * RAW_UNITS_PER_STEP
        logger.info(
            "fine volume steps: slider max ${slider.max} -> $rawMax (streamMax=$streamMax)"
        )
    }

    companion object {
        private const val HOOK_ID = "fine_volume_steps_slider_ctor"
        private const val TOGGLE_SLIDER_VIEW_CLASS =
            "com.android.systemui.settings.ToggleSliderView"
        private const val MEDIA_VOLUME_SLIDER_FIELD = "mMediaVolumeSlider"

        /** android.media.AudioManager.STREAM_MUSIC */
        private const val STREAM_MUSIC = 3

        /** Stock ToggleSliderView raw units per volume step (progress = level * 10000). */
        private const val RAW_UNITS_PER_STEP = 10_000
    }
}
