package com.qimian233.ztool.hook.modules.systemui.qs

import android.media.AudioManager
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.SeekBar
import com.qimian233.ztool.data.keys.PreferenceKeys
import com.qimian233.ztool.data.keys.ScopeKeys
import com.qimian233.ztool.hook.base.AppHookModule
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam
import java.lang.ref.WeakReference
import java.lang.reflect.Method
import java.util.WeakHashMap

/**
 * Opens the ZUI brightness detail panel when the control-center brightness
 * slider is long pressed.
 *
 * SeekBarNps — ToggleSeekBar's superclass — short-circuits every branch of
 * onTouchEvent with an early return when max >= 100 (the brightness slider
 * always exceeds that), so View.onTouchEvent and with it the framework
 * long-click machinery is never reached. Hooking ToggleSeekBar.onTouchEvent
 * itself is the only reliable interception point: every touch event flows
 * through it, and a PRIORITY_LOWEST after-hook runs immediately after the
 * method body with nothing able to overwrite it.
 *
 * The press detector uses the same semantics as the volume slider: a finger
 * held still for [LONG_PRESS_TIMEOUT_MS] triggers the panel; moving beyond
 * the touch slop cancels it, so normal brightness dragging is unaffected.
 */
class BrightnessSliderLongPressHook : AppHookModule() {

    private val pressStates = WeakHashMap<View, PressState>()

    override fun getModuleName(): String = PreferenceKeys.BRIGHTNESS_SLIDER_LONG_PRESS.name

    override fun getTargetPackages(): Array<String> = arrayOf(ScopeKeys.SYSTEM_UI.packageName)

    override fun handleLoadPackage(param: PackageLoadedParam) {
        val classLoader = param.defaultClassLoader
        try {
            val toggleSeekBarClass = classLoader.loadClass(TOGGLE_SEEK_BAR_CLASS)
            val onTouchEvent: Method = toggleSeekBarClass.getDeclaredMethod(
                "onTouchEvent",
                MotionEvent::class.java
            )
            hookWithId(
                onTouchEvent,
                "brightness_touch_long_press",
                { chain ->
                    val result = chain.proceed()
                    try {
                        val seekBar = chain.thisObject as View
                        val event = chain.args[0] as MotionEvent
                        when (event.actionMasked) {
                            MotionEvent.ACTION_DOWN -> {
                                val state = obtainState(seekBar)
                                state.downX = event.rawX
                                state.downY = event.rawY
                                state.triggered = false
                                scheduleLongPress(seekBar) {
                                    runBrightnessDetail(findToggleSliderView(seekBar) ?: seekBar)
                                }
                            }

                            MotionEvent.ACTION_MOVE -> {
                                val state = pressStates[seekBar]
                                if (state != null && !state.triggered) {
                                    val dx = event.rawX - state.downX
                                    val dy = event.rawY - state.downY
                                    if (dx * dx + dy * dy > state.touchSlopSquared) {
                                        cancelLongPress(seekBar)
                                    }
                                }
                            }

                            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                                cancelLongPress(seekBar)
                            }
                        }
                    } catch (t: Throwable) {
                        logger.error("Brightness long-press tracking failed", t)
                    }
                    result
                },
                XposedInterface.PRIORITY_LOWEST
            )
        } catch (t: Throwable) {
            logger.error("Failed to hook ToggleSeekBar.onTouchEvent", t)
        }
    }

    /**
     * Walks up from the touched SeekBar to its ToggleSliderView host so
     * openBrightnessDetail is invoked on the right instance.
     */
    private fun findToggleSliderView(view: View): Any? {
        var current: View = view
        for (i in 0 until 6) {
            current = current.parent as? View ?: return null
            if (current.javaClass.name == TOGGLE_SLIDER_VIEW_CLASS) return current
        }
        return null
    }

    private fun obtainState(view: View): PressState {
        val config = ViewConfiguration.get(view.context)
        return pressStates[view] ?: PressState(config.scaledTouchSlop.toFloat()).also { pressStates[view] = it }
    }

    private fun scheduleLongPress(view: View, action: () -> Unit) {
        cancelLongPress(view)
        val state = pressStates[view] ?: return
        state.runnable = Runnable {
            state.triggered = true
            view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            action()
        }
        view.postDelayed(state.runnable, LONG_PRESS_TIMEOUT_MS)
    }

    private fun cancelLongPress(view: View) {
        val state = pressStates[view] ?: return
        state.runnable?.let { view.removeCallbacks(it) }
        state.runnable = null
    }

    private fun runBrightnessDetail(sliderView: Any) {
        try {
            val method: Method = sliderView.javaClass.getDeclaredMethod("openBrightnessDetail")
            method.isAccessible = true
            method.invoke(sliderView)
        } catch (t: Throwable) {
            logger.error("Failed to open brightness detail", t)
        }
    }

    private class PressState(slop: Float) {
        val touchSlopSquared: Float = slop * slop
        var downX: Float = 0f
        var downY: Float = 0f
        var triggered: Boolean = false
        var runnable: Runnable? = null
    }

    private companion object {
        const val TOGGLE_SLIDER_VIEW_CLASS = "com.android.systemui.settings.ToggleSliderView"
        const val TOGGLE_SEEK_BAR_CLASS =
            "com.android.systemui.settings.brightness.ToggleSeekBar"
        const val LONG_PRESS_TIMEOUT_MS = 500L
    }
}
