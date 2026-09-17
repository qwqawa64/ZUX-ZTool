package com.qimian233.ztool.hook.modules.systemui.qs

import android.annotation.SuppressLint
import android.os.Handler
import android.os.Looper
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.SeekBar
import com.qimian233.ztool.data.keys.ScopeKeys
import com.qimian233.ztool.hook.base.AppHookModule
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam
import java.lang.ref.WeakReference
import java.lang.reflect.Constructor
import java.lang.reflect.Method
import java.util.WeakHashMap

/**
 * Test hook: long-press feedback for the control-center volume / brightness sliders.
 *
 * A long press (finger held still for LONG_PRESS_TIMEOUT_MS) plays a scale-down
 * "held" animation, vibrates, then opens the matching dialog: the native volume
 * panel via VolumeDialogImpl.showH(int) for the volume slider, and
 * ToggleSliderView.openBrightnessDetail() for the brightness slider. Dragging
 * beyond the touch slop cancels the timer and bounces the scale back, so
 * normal slider dragging is unaffected.
 *
 * Touch listener wrapping is installed in an after-hook of updateVolumeSlider()
 * / updateBrightnessSlider() with PRIORITY_HIGHEST: after-hook bodies unwind in
 * ascending priority order, so the highest-priority body runs last and this
 * hook is guaranteed to be the final writer of OnTouchListener (last writer
 * wins), outliving any listener work done by lower-priority hooks on the same
 * method.
 */
@SuppressLint("PrivateApi", "ClickableViewAccessibility")
class SliderLongPressTestHook : AppHookModule() {

    private val mainHandler = Handler(Looper.getMainLooper())
    private val pressStates = WeakHashMap<View, PressState>()
    private var volumeDialogRef: WeakReference<Any>? = null

    override fun getModuleName(): String = TEST_MODULE_NAME

    override fun getTargetPackages(): Array<String> = arrayOf(SYSTEM_UI_PACKAGE)

    override fun handleLoadPackage(param: PackageLoadedParam) {
        val classLoader = param.defaultClassLoader
        hookVolumeDialogCapture(classLoader)
        hookVolumeSliderLongPress(classLoader)
        hookBrightnessSliderLongPress(classLoader)
        logger.info("Slider long press test hook installed")
    }

    /**
     * Captures the VolumeDialogImpl singleton so the long-press callback can
     * summon the native volume panel with showH(reason).
     */
    private fun hookVolumeDialogCapture(classLoader: ClassLoader) {
        try {
            val dialogImplClass = classLoader.loadClass(VOLUME_DIALOG_IMPL_CLASS)
            val callbackClass = classLoader.loadClass(VOLUME_DIALOG_CALLBACK_CLASS)
            val initMethod: Method = dialogImplClass.getDeclaredMethod(
                "init",
                Int::class.javaPrimitiveType,
                callbackClass
            )
            hookWithId(initMethod, "volume_dialog_capture") { chain ->
                val result = chain.proceed()
                volumeDialogRef = WeakReference(chain.thisObject)
                result
            }
        } catch (t: Throwable) {
            logger.warn("Failed to hook VolumeDialogImpl.init: $t")
        }
    }

    private fun hookVolumeSliderLongPress(classLoader: ClassLoader) {
        try {
            val updateVolumeSliderMethod: Method = classLoader.loadClass(TOGGLE_SLIDER_VIEW_CLASS)
                .getDeclaredMethod("updateVolumeSlider")
            hookWithId(
                updateVolumeSliderMethod,
                "volume_slider_long_press",
                { chain ->
                    val result = chain.proceed()
                    attachVolumeLongPress(chain.thisObject)
                    result
                },
                XposedInterface.PRIORITY_HIGHEST
            )
        } catch (t: Throwable) {
            logger.warn("Failed to hook updateVolumeSlider for long press: $t")
        }
    }

    private fun hookBrightnessSliderLongPress(classLoader: ClassLoader) {
        try {
            val updateBrightnessMethod: Method = classLoader.loadClass(TOGGLE_SLIDER_VIEW_CLASS)
                .getDeclaredMethod("updateBrightnessSlider")
            hookWithId(
                updateBrightnessMethod,
                "brightness_slider_long_press",
                { chain ->
                    val result = chain.proceed()
                    attachBrightnessLongPress(chain.thisObject)
                    result
                },
                XposedInterface.PRIORITY_HIGHEST
            )
        } catch (t: Throwable) {
            logger.warn("Failed to hook updateBrightnessSlider for long press: $t")
        }
    }

    private fun attachVolumeLongPress(sliderView: Any) {
        try {
            val slider = sliderView.javaClass
                .getDeclaredField(VOLUME_SLIDER_FIELD).get(sliderView) as SeekBar
            // Delegate to the slider's existing touch listener (the system gate
            // listener handling CSDK / zen / accessibility checks). If it cannot
            // be read, rebuild a fresh instance of the system lambda instead.
            val delegate = readOnTouchListener(slider)
                ?: loadSystemVolumeTouchListener(sliderView.javaClass, sliderView)
            attachLongPress(slider, delegate) { openVolumeDialog() }
        } catch (t: Throwable) {
            logger.warn("Failed to attach volume long press: $t")
        }
    }

    private fun attachBrightnessLongPress(sliderView: Any) {
        try {
            val slider = sliderView.javaClass
                .getDeclaredField(BRIGHTNESS_SLIDER_FIELD).get(sliderView) as SeekBar
            val delegate = readOnTouchListener(slider)
            attachLongPress(slider, delegate) {
                runBrightnessDetail(sliderView)
            }
        } catch (t: Throwable) {
            logger.warn("Failed to attach brightness long press: $t")
        }
    }

    /**
     * Reads the view's current OnTouchListener through View.ListenerInfo so the
     * wrapper can chain to whatever the system last installed.
     */
    private fun readOnTouchListener(view: View): View.OnTouchListener? {
        return try {
            val listenerInfoField = View::class.java.getDeclaredField("mListenerInfo")
            listenerInfoField.isAccessible = true
            val listenerInfo = listenerInfoField.get(view) ?: return null
            val listenerField = listenerInfo.javaClass.getDeclaredField("mOnTouchListener")
            listenerField.isAccessible = true
            listenerField.get(listenerInfo) as? View.OnTouchListener
        } catch (t: Throwable) {
            logger.warn("Failed to read existing OnTouchListener: $t")
            null
        }
    }

    /**
     * Builds a fresh instance of the system touch listener the volume slider
     * uses, avoiding hidden ListenerInfo field reads.
     */
    private fun loadSystemVolumeTouchListener(
        sliderViewClass: Class<*>,
        sliderView: Any
    ): View.OnTouchListener? {
        return try {
            val lambdaClass = Class.forName(
                VOLUME_TOUCH_LISTENER_CLASS,
                true,
                sliderViewClass.classLoader
            )
            val ctor: Constructor<*> = lambdaClass.getDeclaredConstructor(sliderViewClass)
            ctor.isAccessible = true
            ctor.newInstance(sliderView) as View.OnTouchListener
        } catch (t: Throwable) {
            logger.warn("Failed to rebuild volume touch listener delegate: $t")
            null
        }
    }

    private fun attachLongPress(
        slider: SeekBar,
        delegate: View.OnTouchListener?,
        onLongPress: () -> Unit
    ) {
        slider.setOnTouchListener { view, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    val state = obtainState(view)
                    state.downX = event.rawX
                    state.downY = event.rawY
                    state.triggered = false
                    scheduleLongPress(view, onLongPress)
                    playPressAnimation(view)
                }

                MotionEvent.ACTION_MOVE -> {
                    val state = pressStates[view]
                    if (state != null && !state.triggered) {
                        val dx = event.rawX - state.downX
                        val dy = event.rawY - state.downY
                        if (dx * dx + dy * dy > state.touchSlopSquared) {
                            cancelLongPress(view)
                            playReleaseAnimation(view)
                        }
                    }
                }

                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    val state = pressStates[view]
                    val triggered = state?.triggered == true
                    cancelLongPress(view)
                    playReleaseAnimation(view)
                    if (triggered) {
                        return@setOnTouchListener true
                    }
                }
            }
            delegate?.onTouch(view, event) ?: false
        }
    }

    private fun obtainState(view: View): PressState {
        return pressStates[view] ?: PressState(
            ViewConfiguration.get(view.context).scaledTouchSlop.toFloat() *
                ViewConfiguration.get(view.context).scaledTouchSlop
        ).also { pressStates[view] = it }
    }

    private fun scheduleLongPress(view: View, onLongPress: () -> Unit) {
        cancelLongPress(view)
        val runnable = Runnable {
            val state = pressStates[view] ?: return@Runnable
            state.triggered = true
            view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            onLongPress()
            playReleaseAnimation(view)
        }
        pressStates[view]?.runnable = runnable
        mainHandler.postDelayed(runnable, LONG_PRESS_TIMEOUT_MS)
    }

    private fun cancelLongPress(view: View) {
        val state = pressStates[view] ?: return
        state.runnable?.let { mainHandler.removeCallbacks(it) }
        state.runnable = null
    }

    private fun playPressAnimation(view: View) {
        view.animate()
            .scaleX(PRESS_SCALE)
            .scaleY(PRESS_SCALE)
            .setDuration(PRESS_DURATION_MS)
            .setInterpolator(DecelerateInterpolator())
            .start()
    }

    private fun playReleaseAnimation(view: View) {
        view.animate()
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(RELEASE_DURATION_MS)
            .setInterpolator(OvershootInterpolator(1.2f))
            .start()
    }

    private fun openVolumeDialog() {
        val dialog = volumeDialogRef?.get()
        if (dialog == null) {
            logger.warn("VolumeDialogImpl not captured yet, cannot open volume panel")
            return
        }
        try {
            val showH: Method = dialog.javaClass.getDeclaredMethod(
                "showH",
                Int::class.javaPrimitiveType
            )
            showH.isAccessible = true
            showH.invoke(dialog, SHOW_REASON_VOLUME_CHANGED)
        } catch (t: Throwable) {
            logger.warn("Failed to open volume dialog: $t")
        }
    }

    private fun runBrightnessDetail(sliderView: Any) {
        try {
            val method: Method = sliderView.javaClass.getDeclaredMethod("openBrightnessDetail")
            method.isAccessible = true
            method.invoke(sliderView)
        } catch (t: Throwable) {
            logger.warn("Failed to open brightness detail: $t")
        }
    }

    private class PressState(val touchSlopSquared: Float) {
        var downX: Float = 0f
        var downY: Float = 0f
        var triggered: Boolean = false
        var runnable: Runnable? = null
    }

    companion object {
        private val SYSTEM_UI_PACKAGE = ScopeKeys.SYSTEM_UI.packageName
        private const val TEST_MODULE_NAME = "hook_test"
        private const val TOGGLE_SLIDER_VIEW_CLASS = "com.android.systemui.settings.ToggleSliderView"
        private const val VOLUME_TOUCH_LISTENER_CLASS =
            "com.android.systemui.settings.ToggleSliderView\$\$ExternalSyntheticLambda0"
        private const val VOLUME_DIALOG_IMPL_CLASS =
            "com.android.systemui.volume.VolumeDialogImpl"
        private const val VOLUME_DIALOG_CALLBACK_CLASS =
            "com.android.systemui.plugins.VolumeDialog\$Callback"
        private const val VOLUME_SLIDER_FIELD = "mMediaVolumeSlider"
        private const val BRIGHTNESS_SLIDER_FIELD = "mBrightnessSlider"
        private const val LONG_PRESS_TIMEOUT_MS = 500L
        private const val PRESS_SCALE = 0.96f
        private const val PRESS_DURATION_MS = 120L
        private const val RELEASE_DURATION_MS = 180L
        private const val SHOW_REASON_VOLUME_CHANGED = 1
    }
}
