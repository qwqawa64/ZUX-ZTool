package com.qimian233.ztool.hook.modules.systemui.qs

import android.annotation.SuppressLint
import android.app.Dialog
import android.content.Context
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import com.qimian233.ztool.data.keys.ScopeKeys
import com.qimian233.ztool.hook.base.AppHookModule
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam
import java.lang.ref.WeakReference
import java.lang.reflect.Method
import java.util.WeakHashMap

/**
 * Test hook: long-press feedback for the control-center volume / brightness sliders.
 *
 * A long press (finger held still for LONG_PRESS_TIMEOUT_MS) plays a scale-down
 * "held" animation, vibrates, then opens the matching dialog: a volume dialog
 * built exactly like BrightnessDetailDialogController.BrightnessDetailDialog —
 * same theme (Theme_SystemUI_Dialog_GlobalActionsLite), same
 * brightness_detail_dialog layout root, same window parameters — with the
 * brightness content swapped for media + ringer volume rows carried by the ZUI
 * BrightnessSliderView widget. Dragging beyond the touch slop cancels the timer
 * and bounces the scale back, so normal slider dragging is unaffected.
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
    private var volumeDialogRef: WeakReference<Dialog>? = null

    override fun getModuleName(): String = TEST_MODULE_NAME

    override fun getTargetPackages(): Array<String> = arrayOf(SYSTEM_UI_PACKAGE)

    override fun handleLoadPackage(param: PackageLoadedParam) {
        val classLoader = param.defaultClassLoader
        hookVolumeSliderLongPress(classLoader)
        hookBrightnessSliderLongPress(classLoader)
        logger.info("Slider long press test hook installed")
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
            attachLongPress(slider, delegate) {
                runOnUiThread(slider) { showVolumeDetailDialog(slider) }
            }
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

    /**
     * Toggles the volume dialog. Repeated long presses dismiss it, mirroring
     * BrightnessDetailDialogController.showOrHideDialog's toggle semantics.
     */
    private fun showVolumeDetailDialog(anchor: View) {
        val current = volumeDialogRef?.get()
        if (current != null && current.isShowing) {
            current.dismiss()
            volumeDialogRef = null
            return
        }
        try {
            val dialog = buildBrightnessStyleVolumeDialog(anchor.context)
            volumeDialogRef = WeakReference(dialog)
            dialog.show()
        } catch (t: Throwable) {
            logger.error("Failed to show volume detail dialog: $t", t)
        }
    }

    /**
     * Reproduces the BrightnessDetailDialog geometry measured from the live
     * reference dump: a fullscreen transparent window whose visible part is a
     * vertical panel docked to the right edge (80% of screen height, width
     * ~31% of screen width per the reference bounds [1898,200][2903,1800] on a
     * 3200x2000 screen). Panel color follows the theme's floating background.
     * Volume bars reuse the ZUI rotation trick: the BrightnessSliderView bar
     * is rotated 90 degrees into a vertical slider, matching the reference.
     */
    private fun buildBrightnessStyleVolumeDialog(context: Context): Dialog {
        val res = context.resources
        val pkg = context.packageName
        val themeId = res.getIdentifier(BRIGHTNESS_DIALOG_THEME, "style", pkg)
        val dialog = if (themeId != 0) {
            Dialog(context, themeId)
        } else {
            logger.warn("Brightness dialog theme not found, using default theme")
            Dialog(context)
        }

        // Reference proportions on a 3200x2000 landscape screen: panel
        // 1005x1600 with 200px top/bottom insets and ~102px end margin.
        val metrics = res.displayMetrics
        val panelWidth = res.getDimensionPixelSize(
            res.getIdentifier("brightness_bar_width_detail", "dimen", pkg)
        ).takeIf { it >= metrics.widthPixels / 4 }
            ?: (metrics.widthPixels * 1005 / 3200)
        val panelHeight = (metrics.heightPixels * 0.8f).toInt()

        val panel = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            setPadding(dp(context, 24), dp(context, 24), dp(context, 24), dp(context, 24))
            background = buildPanelBackground(context)
        }
        panel.addView(
            createVolumeDetailView(context, panel),
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )

        val root = android.widget.FrameLayout(context)
        root.addView(
            panel,
            android.widget.FrameLayout.LayoutParams(
                panelWidth,
                panelHeight,
                android.view.Gravity.END or android.view.Gravity.CENTER_VERTICAL
            ).apply { marginEnd = dp(context, 32) }
        )
        dialog.setContentView(
            root,
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )

        dialog.setCanceledOnTouchOutside(true)
        dialog.window?.let { window ->
            try {
                val attrs = window.attributes
                attrs.type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                attrs.layoutInDisplayCutoutMode = 3
                attrs.title = VOLUME_WINDOW_TITLE
                window.attributes = attrs
                window.addFlags(WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN)
                window.setDimAmount(0f)
            } catch (t: Throwable) {
                logger.warn("Failed to apply dialog window params: $t")
            }
        }
        return dialog
    }

    /**
     * Panel surface color from the theme's floating background so dark mode is
     * honored; falls back to translucent white when the attr is unavailable.
     */
    private fun buildPanelBackground(context: Context): android.graphics.drawable.Drawable {
        val radius = try {
            context.resources.getDimension(
                context.resources.getIdentifier(SLIDER_CORNER_DIMEN, "dimen", context.packageName)
            )
        } catch (_: Throwable) {
            dp(context, 16).toFloat()
        }
        val color = try {
            val typed = context.theme.obtainStyledAttributes(
                intArrayOf(android.R.attr.colorBackgroundFloating)
            )
            try {
                typed.getColor(0, 0xE6FFFFFF.toInt())
            } finally {
                typed.recycle()
            }
        } catch (_: Throwable) {
            0xE6FFFFFF.toInt()
        }
        return android.graphics.drawable.GradientDrawable().apply {
            shape = android.graphics.drawable.GradientDrawable.RECTANGLE
            cornerRadius = radius
            setColor(color)
        }
    }

    /**
     * Builds the dialog content: a title plus one slider row per stream. Each
     * slider reuses the ZUI BrightnessSliderView control (the same slider
     * widget the brightness detail dialog shows, inflated from
     * quick_settings_brightness_dialog_zui). The rotation the brightness
     * dialog applies to make it vertical is deliberately omitted so the
     * volume bars stay horizontal. Falls back to a platform SeekBar styled
     * with the control-center slider drawable when the ZUI layout is
     * unavailable.
     */
    private fun createVolumeDetailView(context: Context, parent: ViewGroup): View {
        val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = android.view.Gravity.CENTER_VERTICAL
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
        // Vertical layout like the reference: label on top, vertical slider below.
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = android.view.Gravity.CENTER_HORIZONTAL
            setPadding(0, dp(context, 12), 0, dp(context, 12))
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
            ).apply { bottomMargin = dp(context, 12) }
        )
        row.addView(
            buildStreamSlider(context, audio, stream),
            LinearLayout.LayoutParams(
                dp(context, VERTICAL_SLIDER_WIDTH_DP),
                dp(context, VERTICAL_SLIDER_HEIGHT_DP)
            ).apply { gravity = android.view.Gravity.CENTER_HORIZONTAL }
        )
        return row
    }

    /**
     * Builds one stream slider. Prefers inflating the ZUI BrightnessSliderView
     * layout (identical widget to the brightness detail dialog slider) and
     * driving its inner R.id.slider SeekBar; the bar is rotated 90 degrees the
     * same way the brightness detail dialog turns it into a vertical slider.
     * Falls back to a styled platform SeekBar when the ZUI resources cannot be
     * resolved.
     */
    private fun buildStreamSlider(
        context: Context,
        audio: AudioManager,
        stream: Int
    ): View {
        val zuiSlider = inflateZuiBrightnessSlider(context)
        if (zuiSlider != null) {
            val (seekBar, root) = zuiSlider
            configureVolumeSeekBar(seekBar, audio, stream)
            // Wrap the bar in a fixed-size frame holding the post-rotation
            // (vertical) footprint; the bar itself lays out with swapped
            // dimensions and rotates 90° around its center, exactly how the
            // brightness detail dialog turns the horizontal bar vertical.
            val frame = android.widget.FrameLayout(context)
            frame.addView(
                root,
                android.widget.FrameLayout.LayoutParams(
                    dp(context, VERTICAL_SLIDER_HEIGHT_DP),
                    dp(context, VERTICAL_SLIDER_WIDTH_DP),
                    android.view.Gravity.CENTER
                )
            )
            root.rotation = 90f
            return frame
        }
        return SeekBar(context).apply {
            applyZuiSliderStyle(context)
            configureVolumeSeekBar(this, audio, stream)
        }
    }

    /**
     * Inflates quick_settings_brightness_dialog_zui (the brightness detail
     * slider widget) and returns its inner R.id.slider SeekBar together with
     * the inflated root, or null when the resources are unavailable.
     */
    private fun inflateZuiBrightnessSlider(context: Context): Pair<SeekBar, View>? {
        return try {
            val res = context.resources
            val pkg = context.packageName
            val layoutId = res.getIdentifier(ZUI_BRIGHTNESS_SLIDER_LAYOUT, "layout", pkg)
            if (layoutId == 0) {
                logger.warn("ZUI brightness slider layout not found, using fallback slider")
                return null
            }
            val root = LayoutInflater.from(context).inflate(layoutId, null)
            val sliderId = res.getIdentifier("slider", "id", pkg)
            val seekBar = if (sliderId != 0) {
                root.findViewById(sliderId) as? SeekBar
            } else {
                findFirstSeekBar(root)
            }
            if (seekBar == null) {
                logger.warn("ZUI brightness slider layout has no SeekBar, using fallback")
                return null
            }
            seekBar to root
        } catch (t: Throwable) {
            logger.warn("Failed to inflate ZUI brightness slider layout: $t")
            null
        }
    }

    private fun findFirstSeekBar(root: View): SeekBar? {
        if (root is SeekBar) return root
        if (root is ViewGroup) {
            for (i in 0 until root.childCount) {
                findFirstSeekBar(root.getChildAt(i))?.let { return it }
            }
        }
        return null
    }

    private fun configureVolumeSeekBar(
        seekBar: SeekBar,
        audio: AudioManager,
        stream: Int
    ) {
        seekBar.max = audio.getStreamMaxVolume(stream)
        seekBar.progress = audio.getStreamVolume(stream)
        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
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

    /**
     * Applies the control-center slider appearance to the fallback SeekBar: the
     * same progress selector drawable used by ToggleSliderView plus the corner
     * radius refreshSeekBar stamps onto its background/progress layers.
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
            val layerDrawable = progressDrawable as? android.graphics.drawable.LayerDrawable
                ?: return
            layerDrawable.findDrawableByLayerId(android.R.id.background)?.let {
                (it as? android.graphics.drawable.GradientDrawable)?.cornerRadius = cornerRadius
            }
            val progressLayer = layerDrawable.findDrawableByLayerId(android.R.id.progress)
            if (progressLayer is android.graphics.drawable.StateListDrawable) {
                for (i in 0 until progressLayer.stateCount) {
                    val clip = progressLayer.getStateDrawable(i)
                        as? android.graphics.drawable.ClipDrawable ?: continue
                    (clip.drawable as? android.graphics.drawable.GradientDrawable)
                        ?.cornerRadius = cornerRadius
                }
            } else {
                (progressLayer as? android.graphics.drawable.ClipDrawable)?.drawable?.let {
                    (it as? android.graphics.drawable.GradientDrawable)?.cornerRadius =
                        cornerRadius
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
            // The ZUI keyboard slider variant ships with thumb=@null; match it
            // so the bar renders as a plain rounded track.
            thumb = null
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
        private const val VOLUME_SLIDER_FIELD = "mMediaVolumeSlider"
        private const val BRIGHTNESS_SLIDER_FIELD = "mBrightnessSlider"
        // Style resource names are dotted in the resource table; R.style fields
        // merely replace the dots with underscores.
        private const val BRIGHTNESS_DIALOG_THEME = "Theme.SystemUI.Dialog.GlobalActionsLite"
        private const val ZUI_BRIGHTNESS_SLIDER_LAYOUT = "quick_settings_brightness_dialog_zui"
        // Vertical slider footprint measured from the reference dump
        // (203x650 px on a 3200x2000 screen at ~420dpi ≈ 96x154 dp).
        private const val VERTICAL_SLIDER_WIDTH_DP = 96
        private const val VERTICAL_SLIDER_HEIGHT_DP = 154
        private const val SLIDER_DRAWABLE = "brightness_progress_selector_keyboard"
        private const val SLIDER_CORNER_DIMEN = "qs_corner_radius"
        private const val SLIDER_HEIGHT_DIMEN = "brightness_bar_height"
        private const val VOLUME_DIALOG_TITLE = "音量"
        private const val VOLUME_WINDOW_TITLE = "ZToolVolumeDetailDialog"
        private const val MEDIA_LABEL = "媒体音量"
        private const val RINGER_LABEL = "铃声音量"
        private const val LONG_PRESS_TIMEOUT_MS = 500L
        private const val PRESS_SCALE = 0.96f
        private const val PRESS_DURATION_MS = 120L
        private const val RELEASE_DURATION_MS = 180L
    }
}
