package com.qimian233.ztool.hook.modules.systemui.qs

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.drawable.ClipDrawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import com.qimian233.ztool.data.keys.ScopeKeys
import com.qimian233.ztool.hook.base.AppHookModule
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam
import java.lang.reflect.Constructor
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import java.util.WeakHashMap

/**
 * Test hook: long-press feedback for the control-center volume / brightness sliders.
 *
 * A long press (finger held still for LONG_PRESS_TIMEOUT_MS) plays a scale-down
 * "held" animation, vibrates, then opens the matching dialog: a ZUI QS detail
 * dialog carrying a custom DetailAdapter (media + ringer volume rows) for the
 * volume slider, and ToggleSliderView.openBrightnessDetail() for the brightness
 * slider. Dragging beyond the touch slop cancels the timer and bounces the scale
 * back, so normal slider dragging is unaffected.
 *
 * The QSDetailDialogController singleton is captured once via a constructor
 * after-hook at SystemUI startup; the DetailAdapter is implemented through a
 * java.lang.reflect.Proxy because SystemUI classes are not available at compile
 * time. Delegating the dialog presentation to QSDetailDialogController keeps the
 * ZUI styling, window layering, and show/hide transition identical to the
 * brightness detail dialog.
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
    private var qsDetailControllerRef: java.lang.ref.WeakReference<Any>? = null
    private var detailAdapterProxy: Any? = null

    override fun getModuleName(): String = TEST_MODULE_NAME

    override fun getTargetPackages(): Array<String> = arrayOf(SYSTEM_UI_PACKAGE)

    override fun handleLoadPackage(param: PackageLoadedParam) {
        val classLoader = param.defaultClassLoader
        hookQsDetailDialogControllerCapture(classLoader)
        hookVolumeSliderLongPress(classLoader)
        hookBrightnessSliderLongPress(classLoader)
        logger.info("Slider long press test hook installed")
    }

    /**
     * Captures the QSDetailDialogController singleton at Dagger construction so
     * the volume long press can reuse the ZUI QS detail dialog presentation.
     */
    private fun hookQsDetailDialogControllerCapture(classLoader: ClassLoader) {
        try {
            val controllerClass = classLoader.loadClass(QS_DETAIL_DIALOG_CONTROLLER_CLASS)
            for (ctor: Constructor<*> in controllerClass.declaredConstructors) {
                hookWithId(ctor, "qs_detail_dialog_capture") { chain ->
                    val result = chain.proceed()
                    qsDetailControllerRef = java.lang.ref.WeakReference(chain.thisObject)
                    result
                }
            }
        } catch (t: Throwable) {
            logger.warn("Failed to hook QSDetailDialogController constructor: $t")
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
            attachLongPress(slider, delegate) { showVolumeDetailDialog(slider) }
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
                runOnUiThread(slider) { runBrightnessDetail(sliderView) }
            }
        } catch (t: Throwable) {
            logger.warn("Failed to attach brightness long press: $t")
        }
    }

    private fun showVolumeDetailDialog(anchor: View) {
        val controller = qsDetailControllerRef?.get()
        if (controller == null) {
            logger.warn("QSDetailDialogController not captured yet")
            return
        }
        try {
            val adapterClass = Class.forName(DETAIL_ADAPTER_CLASS, true, anchor.context.classLoader)
            val adapter = obtainDetailAdapter(anchor.context)
            logger.debug("volume detail: controller=${controller.javaClass.name}, " +
                "adapterClass=$adapterClass, adapterInterfaces=${adapter.javaClass.interfaces.contentToString()}")
            val showOrHide: Method = controller.javaClass.getMethod(
                "showOrHideDialog",
                View::class.java,
                adapterClass
            )
            logger.debug("volume detail: resolved $showOrHide, invoking on $controller")
            showOrHide.invoke(controller, anchor, adapter)
            logger.debug("volume detail: showOrHideDialog returned normally")
        } catch (t: Throwable) {
            // InvocationTargetException wraps the real failure thrown inside
            // QSDetailDialogController; unwrap so the log shows the root cause.
            val cause = (t as? java.lang.reflect.InvocationTargetException)?.targetException ?: t
            logger.error("Failed to show volume detail dialog: $cause", cause)
        }
    }

    /**
     * Builds (once) a dynamic proxy implementing the SystemUI DetailAdapter
     * interface. The detail view carries media and ringer volume rows.
     */
    private fun obtainDetailAdapter(context: Context): Any {
        detailAdapterProxy?.let { return it }
        val adapterInterface = Class.forName(DETAIL_ADAPTER_CLASS, true, context.classLoader)
        val handler = InvocationHandler { proxy, method, args ->
            when (method.name) {
                "createDetailView" -> {
                    @Suppress("UNCHECKED_CAST")
                    createVolumeDetailView(
                        args[0] as Context,
                        args[2] as ViewGroup
                    )
                }

                "getMetricsCategory" -> VOLUME_DETAIL_METRICS_CATEGORY
                "getSettingsIntent" -> null
                "getTitle" -> VOLUME_DIALOG_TITLE
                "getToggleState" -> java.lang.Boolean.FALSE
                "setToggleState" -> Unit
                "getToggleEnabled" -> java.lang.Boolean.FALSE
                "setDetailListening" -> Unit
                else -> defaultProxyReturn(method)
            }
        }
        val proxy = Proxy.newProxyInstance(
            adapterInterface.classLoader,
            arrayOf(adapterInterface),
            handler
        )
        detailAdapterProxy = proxy
        return proxy
    }

    private fun defaultProxyReturn(method: Method): Any? {
        return when (method.returnType) {
            Boolean::class.javaPrimitiveType -> java.lang.Boolean.FALSE
            Int::class.javaPrimitiveType -> 0
            else -> null
        }
    }

    /**
     * Builds the dialog content: a title plus one slider row per stream. The
     * SeekBars reuse the control-center slider drawable
     * (brightness_progress_selector) with the same corner rounding
     * ToggleSliderView.refreshSeekBar applies, so they render in ZUI style.
     */
    private fun createVolumeDetailView(context: Context, parent: ViewGroup): View {
        val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(context, 8), dp(context, 4), dp(context, 8), dp(context, 12))
        }

        val title = TextView(context).apply {
            text = VOLUME_DIALOG_TITLE
            textSize = 16f
            isSingleLine = true
        }
        container.addView(
            title,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(context, 12) }
        )

        container.addView(buildStreamRow(context, audio, AudioManager.STREAM_MUSIC, MEDIA_LABEL))
        container.addView(buildStreamRow(context, audio, AudioManager.STREAM_RING, RINGER_LABEL))
        return container
    }

    private fun buildStreamRow(
        context: Context,
        audio: AudioManager,
        stream: Int,
        label: String
    ): View {
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            setPadding(0, dp(context, 8), 0, dp(context, 8))
        }
        val labelView = TextView(context).apply {
            text = label
            textSize = 14f
            isSingleLine = true
        }
        row.addView(
            labelView,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { rightMargin = dp(context, 16) }
        )
        val seekBar = SeekBar(context).apply {
            max = audio.getStreamMaxVolume(stream)
            progress = audio.getStreamVolume(stream)
            applyZuiSliderStyle(context)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(
                    bar: SeekBar,
                    progress: Int,
                    fromUser: Boolean
                ) {
                    if (fromUser) {
                        try {
                            audio.setStreamVolume(stream, progress, 0)
                        } catch (t: Throwable) {
                            logger.warn("Failed to set stream volume: $t")
                        }
                    }
                }

                override fun onStartTrackingTouch(bar: SeekBar) = Unit

                override fun onStopTrackingTouch(bar: SeekBar) = Unit
            })
        }
        row.addView(
            seekBar,
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        )
        return row
    }

    /**
     * Applies the control-center slider appearance: the same progress selector
     * drawable used by ToggleSliderView plus the corner radius refreshSeekBar
     * stamps onto its background/progress layers. Falls back silently to the
     * platform style when the SystemUI resources are not resolvable.
     */
    private fun SeekBar.applyZuiSliderStyle(context: Context) {
        try {
            val res = context.resources
            val pkg = context.packageName
            val drawableId = res.getIdentifier(SLIDER_DRAWABLE, "drawable", pkg)
            if (drawableId == 0) return
            val drawable = res.getDrawable(drawableId, context.theme)
            setProgressDrawable(drawable)
            val cornerRadius = res.getDimension(
                res.getIdentifier(SLIDER_CORNER_DIMEN, "dimen", pkg)
            )
            val layerDrawable = progressDrawable as? LayerDrawable ?: return
            layerDrawable.findDrawableByLayerId(android.R.id.background)?.let {
                (it as? GradientDrawable)?.cornerRadius = cornerRadius
            }
            val progressLayer = layerDrawable.findDrawableByLayerId(android.R.id.progress)
            if (progressLayer is android.graphics.drawable.StateListDrawable) {
                for (i in 0 until progressLayer.stateCount) {
                    val clip = progressLayer.getStateDrawable(i) as? ClipDrawable ?: continue
                    (clip.drawable as? GradientDrawable)?.cornerRadius = cornerRadius
                }
            } else {
                (progressLayer as? ClipDrawable)?.drawable?.let {
                    (it as? GradientDrawable)?.cornerRadius = cornerRadius
                }
            }
            val barHeight = res.getDimensionPixelSize(
                res.getIdentifier(SLIDER_HEIGHT_DIMEN, "dimen", pkg)
            )
            if (barHeight > 0) {
                minHeight = barHeight
                maxHeight = barHeight
            }
            splitTrack = false
            thumbOffset = 0
        } catch (t: Throwable) {
            logger.warn("Failed to apply ZUI slider style: $t")
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
     * Rebuilds the system volume touch gate listener when the ListenerInfo read
     * is unavailable, avoiding per-press reflection scans.
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
            val ctor = lambdaClass.getDeclaredConstructor(sliderViewClass)
            ctor.isAccessible = true
            ctor.newInstance(sliderView) as View.OnTouchListener
        } catch (t: Throwable) {
            logger.warn("Failed to rebuild volume touch listener delegate: $t")
            null
        }
    }

    private fun runOnUiThread(view: View, action: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            action()
        } else {
            view.post { action() }
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
                    val triggered = pressStates[view]?.triggered == true
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

    private fun dp(context: Context, value: Int): Int {
        return (value * context.resources.displayMetrics.density).toInt()
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
        private const val QS_DETAIL_DIALOG_CONTROLLER_CLASS =
            "com.android.systemui.qs.tiles.dialog.QSDetailDialogController"
        private const val DETAIL_ADAPTER_CLASS = "com.android.systemui.qs.DetailAdapter"
        private const val VOLUME_SLIDER_FIELD = "mMediaVolumeSlider"
        private const val BRIGHTNESS_SLIDER_FIELD = "mBrightnessSlider"
        private const val SLIDER_DRAWABLE = "brightness_progress_selector"
        private const val SLIDER_CORNER_DIMEN = "qs_corner_radius"
        private const val SLIDER_HEIGHT_DIMEN = "brightness_bar_height"
        private const val VOLUME_DIALOG_TITLE = "音量"
        private const val MEDIA_LABEL = "媒体音量"
        private const val RINGER_LABEL = "铃声音量"
        private const val VOLUME_DETAIL_METRICS_CATEGORY = 9128
        private const val LONG_PRESS_TIMEOUT_MS = 500L
        private const val PRESS_SCALE = 0.96f
        private const val PRESS_DURATION_MS = 120L
        private const val RELEASE_DURATION_MS = 180L
    }
}
