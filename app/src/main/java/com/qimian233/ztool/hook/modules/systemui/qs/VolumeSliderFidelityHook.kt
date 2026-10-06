package com.qimian233.ztool.hook.modules.systemui.qs

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.view.MotionEvent
import android.view.View
import android.view.ViewParent
import com.qimian233.ztool.data.keys.PreferenceKeys
import com.qimian233.ztool.data.keys.ScopeKeys
import com.qimian233.ztool.hook.base.AppHookModule
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam
import java.lang.reflect.Field

/**
 * Keeps the control-center media volume slider faithful to the real stream
 * level when a volume write is refused instead of applied.
 *
 * ZUI's `zui.widget.SeekBarNps` drives touch itself and, on ACTION_UP /
 * ACTION_CANCEL, starts a 150 ms settle animation *before* notifying the
 * listener:
 *
 *     a(a(x));                              // start settle ValueAnimator
 *     listener.onStopTrackingTouch(this);   // ToggleSliderView$2 -> updateMusicSlider()
 *
 * `ToggleSliderView.updateMusicSlider()` re-reads `AudioManager.getStreamVolume(3)`
 * and snaps the bar back to the real level, so the correction itself is already
 * there. But the animator was started one statement earlier and keeps calling
 * `setProgress(target)` on every frame for the next 150 ms, overwriting that
 * correction. It is built as `ValueAnimator.ofInt(getProgress(), target)` and by
 * ACTION_UP both endpoints are already the dragged value (ACTION_MOVE applied it
 * live through `setProgressExternal(..., true, false)`), so the animation is a
 * numeric no-op whose only effect is to clobber.
 *
 * The symptom shows up exactly when the write is dropped rather than applied:
 * the safe media volume gate (`SoundDoseHelper.checkSafeMediaVolume_l`) posts the
 * high-volume warning and discards the volume command, and a dropped write emits
 * no `VOLUME_CHANGED_ACTION`, so nothing re-syncs the bar until some unrelated
 * volume change arrives. Cancelling the settle animator right after
 * `onTouchEvent` returns lets the existing correction become the last write.
 * In the normal (accepted) case cancelling the no-op animation is visually
 * indistinguishable.
 *
 * Scope is limited to volume sliders: a `SeekBarNps` that either sits inside a
 * stock `ToggleSliderView` (the media volume bar beside the brightness bar) or
 * carries the ZTool volume detail panel tag. Brightness uses `ToggleSeekBar`,
 * not `SeekBarNps`, so nothing else is touched.
 */
@SuppressLint("PrivateApi")
class VolumeSliderFidelityHook : AppHookModule() {

    override fun getModuleName(): String = PreferenceKeys.VOLUME_SLIDER_FIDELITY.name

    override fun getTargetPackages(): Array<String> = arrayOf(ScopeKeys.SYSTEM_UI.packageName)

    override fun handleLoadPackage(param: PackageLoadedParam) {
        val classLoader = param.defaultClassLoader
        try {
            val seekBarClass = classLoader.loadClass(SEEK_BAR_NPS_CLASS)
            val settleAnimator = seekBarClass.getDeclaredField(SETTLE_ANIMATOR_FIELD)
            settleAnimator.isAccessible = true

            val onTouchEvent = findMethod(seekBarClass, "onTouchEvent", MotionEvent::class.java)
            hookWithId(onTouchEvent, HOOK_ID) { chain ->
                val event = chain.args[0] as? MotionEvent
                val handled = chain.proceed()
                when (event?.action) {
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        try {
                            cancelSettleAnimation(chain.thisObject, settleAnimator)
                        } catch (t: Throwable) {
                            logger.warn("volume slider fidelity: cancel failed: ${t.message}")
                        }
                    }
                }
                handled
            }
            logger.info("Volume slider fidelity hook installed")
        } catch (t: Throwable) {
            logger.error("Failed to hook $SEEK_BAR_NPS_CLASS.onTouchEvent", t)
        }
    }

    /** Drops the pending settle animation so the caller's correction survives. */
    private fun cancelSettleAnimation(bar: Any?, field: Field) {
        if (!isVolumeSlider(bar)) return
        val animator = field.get(bar) as? ValueAnimator ?: return
        if (animator.isRunning) {
            animator.cancel()
        }
    }

    private fun isVolumeSlider(bar: Any?): Boolean {
        val view = bar as? View ?: return false
        if (view.tag == ControlCenterLongPressHook.VOLUME_PANEL_SLIDER_TAG) return true
        var parent: ViewParent? = view.parent
        while (parent != null) {
            if (parent.javaClass.name == TOGGLE_SLIDER_VIEW_CLASS) return true
            parent = parent.parent
        }
        return false
    }

    private companion object {
        const val HOOK_ID = "volume_slider_fidelity_settle_anim"

        const val SEEK_BAR_NPS_CLASS = ControlCenterLongPressHook.SEEK_BAR_NPS_CLASS
        const val TOGGLE_SLIDER_VIEW_CLASS = ControlCenterLongPressHook.TOGGLE_SLIDER_VIEW_CLASS

        /**
         * `zui.widget.SeekBarNps` keeps its settle `ValueAnimator` in this
         * (obfuscated) field. If a future ZUI build renames it the hook fails to
         * install and is logged instead of breaking the slider.
         */
        const val SETTLE_ANIMATOR_FIELD = "h"
    }
}
