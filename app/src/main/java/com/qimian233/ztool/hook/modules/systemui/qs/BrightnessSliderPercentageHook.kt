package com.qimian233.ztool.hook.modules.systemui.qs

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.text.TextUtils
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ProgressBar
import android.widget.SeekBar
import android.widget.TextView
import com.qimian233.ztool.data.keys.PreferenceKeys
import com.qimian233.ztool.data.keys.ScopeKeys
import com.qimian233.ztool.hook.base.AppHookModule
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam
import java.lang.reflect.Constructor
import java.lang.reflect.Method
import java.util.Locale
import java.util.WeakHashMap
import kotlin.math.roundToInt

@SuppressLint("PrivateApi")
class BrightnessSliderPercentageHook : AppHookModule() {

    private val layoutListeners = WeakHashMap<View, View.OnLayoutChangeListener>()
    private val pendingPositionUpdates = WeakHashMap<FrameLayout, Boolean>()

    override fun getModuleName(): String = PreferenceKeys.BRIGHTNESS_SLIDER_PERCENTAGE.name

    override fun getTargetPackages(): Array<String> = arrayOf(SYSTEM_UI_PACKAGE)

    override fun handleLoadPackage(param: PackageLoadedParam) {
        val classLoader = param.defaultClassLoader
        updatePrefs()
        hookToggleSliderViewLifecycle(classLoader)
        hookBrightnessControllerCallbacks(classLoader)
        hookSeekProgressChanges()
        logger.info("Brightness slider percentage hooks installed")
    }

    private fun hookBrightnessControllerCallbacks(classLoader: ClassLoader) {
        try {
            val onChangedMethod: Method = classLoader
                .loadClass("com.android.systemui.settings.brightness.BrightnessController")
                .getDeclaredMethod(
                    "onChanged",
                    Int::class.javaPrimitiveType,
                    Boolean::class.javaPrimitiveType,
                    Boolean::class.javaPrimitiveType
                )
            hookWithId(onChangedMethod, "on_changed") { chain ->
                val result = chain.proceed()
                refreshBrightnessFromController(chain.thisObject, chain.args[0] as Int)
                result
            }
        } catch (_: Throwable) {
        }

        try {
            val setValueMethod: Method = classLoader
                .loadClass("com.android.systemui.settings.brightness.BrightnessSliderController")
                .getDeclaredMethod("setValue", Int::class.javaPrimitiveType)
            hookWithId(setValueMethod, "set_value") { chain ->
                val result = chain.proceed()
                val sliderController = chain.thisObject
                val scCls = sliderController.javaClass
                try {
                    scCls.getDeclaredField("mBrightnessSliderHapticPlugin")
                } catch (_: NoSuchFieldException) {
                    logger.warn("Field mBrightnessSliderHapticPlugin not found, shouldn't hook mView of this class!")
                    return@hookWithId chain.proceed()
                }
                val view = findField(scCls, "mView").get(sliderController)
                if (view is View) {
                    refreshBrightnessFromView(view, chain.args[0] as Int)
                }
                result
            }
        } catch (_: Throwable) {
        }
    }

    private fun hookToggleSliderViewLifecycle(classLoader: ClassLoader) {
        try {
            val ctor: Constructor<*> = classLoader.loadClass(TOGGLE_SLIDER_VIEW_CLASS)
                .getDeclaredConstructor(Context::class.java, AttributeSet::class.java, Int::class.javaPrimitiveType)
            hookWithId(ctor, "ctor") { chain ->
                chain.proceed()
                attachSliderLabel(chain.thisObject)
                null
            }
        } catch (_: Throwable) {
        }

        try {
            val updateBrightnessMethod: Method = classLoader.loadClass(TOGGLE_SLIDER_VIEW_CLASS)
                .getDeclaredMethod("updateBrightnessSlider")
            hookWithId(updateBrightnessMethod, "update_brightness") { chain ->
                val result = chain.proceed()
                attachSliderLabel(chain.thisObject)
                refreshBrightnessLabel(chain.thisObject)
                result
            }
        } catch (_: Throwable) {
        }

        try {
            val refreshSeekBarMethod: Method = classLoader.loadClass(TOGGLE_SLIDER_VIEW_CLASS)
                .getDeclaredMethod("refreshSeekBar", ProgressBar::class.java)
            hookWithId(refreshSeekBarMethod, "refresh_seek_bar") { chain ->
                val result = chain.proceed()
                val sliderView = chain.thisObject
                val progressBar = chain.args[0] as ProgressBar
                if (isBrightnessSlider(sliderView, progressBar)) {
                    refreshBrightnessLabel(sliderView)
                }
                result
            }
        } catch (_: Throwable) {
        }
    }

    private fun hookSeekProgressChanges() {
        try {
            val setProgressMethod: Method =
                SeekBar::class.java.getDeclaredMethod("setProgress", Int::class.javaPrimitiveType)
            hookWithId(setProgressMethod, "set_progress") { chain ->
                val result = chain.proceed()
                val seekBar = chain.thisObject as SeekBar
                val sliderView = findToggleSliderView(seekBar) ?: return@hookWithId result
                if (isBrightnessSlider(sliderView, seekBar)) {
                    refreshBrightnessLabel(sliderView)
                }
                result
            }
        } catch (_: Throwable) {
        }
    }

    private fun attachSliderLabel(sliderView: Any) {
        try {
            attachLabelToRoot(sliderView)
        } catch (t: Throwable) {
            logger.error("Failed to attach brightness slider label", t)
        }
    }

    private fun attachLabelToRoot(sliderView: Any) {
        val root = getFrameLayoutField(sliderView)
        val icon = getViewField(sliderView)
        if (root == null || icon == null) {
            return
        }

        if (!percentageEnabled) {
            removePercentView(root)
            return
        }

        var percentView = findPercentView(root)
        if (percentView == null) {
            percentView = createPercentView(root.context)
            root.addView(percentView, createLayoutParams())
        }

        ensureLayoutTracking(root, icon, percentView)
        updateBrightnessPercentColor(sliderView, percentView)
        refreshBrightnessLabel(sliderView, percentView)
    }

    private fun refreshBrightnessLabel(sliderView: Any) {
        if (!percentageEnabled) {
            detachBrightnessLabel(sliderView)
            return
        }
        val root = getFrameLayoutField(sliderView) ?: return
        val percentView = findPercentView(root)
        if (percentView != null) {
            refreshBrightnessLabel(sliderView, percentView)
        }
    }

    private fun refreshBrightnessLabel(sliderView: Any, percentView: TextView) {
        if (!percentageEnabled) {
            removePercentView(getFrameLayoutField(sliderView))
            return
        }
        try {
            val root = getFrameLayoutField(sliderView)
            if (root != null && root.isInLayout) {
                schedulePositionUpdate(sliderView)
                return
            }
            val cl = sliderView.javaClass
            val brightnessSlider = cl.getDeclaredField("mBrightnessSlider").get(sliderView) as SeekBar
            setPercentTextIfChanged(
                percentView,
                formatPercent(
                    brightnessSlider.progress,
                    brightnessSlider.min,
                    brightnessSlider.max
                )
            )
            updateBrightnessPercentColor(sliderView, percentView)
            schedulePositionUpdate(sliderView)
        } catch (t: Throwable) {
            setPercentTextIfChanged(percentView, "--%")
            logger.error("Failed to refresh brightness percent", t)
        }
    }

    private fun refreshBrightnessFromController(brightnessController: Any, progress: Int) {
        if (!percentageEnabled) {
            return
        }
        try {
            val bcCls = brightnessController.javaClass
            val control = bcCls.getDeclaredField("mControl").get(brightnessController) ?: return
            try {
                bcCls.getDeclaredField("mBrightnessObserver")
            } catch (_: NoSuchFieldException) {
                logger.warn("Field mBrightnessObserver not found, shouldn't hook mView of this class!")
                return
            }
            val view = findField(control.javaClass, "mView").get(control)
            if (view !is View) {
                return
            }
            refreshBrightnessFromView(view, progress)
        } catch (t: Throwable) {
            logger.error("Failed to refresh brightness from controller", t)
        }
    }

    private fun refreshBrightnessFromView(view: View, progress: Int) {
        val sliderView = findToggleSliderView(view) ?: return
        val root = getFrameLayoutField(sliderView) ?: return
        val percentView = findPercentView(root) ?: return

        try {
            val cl = sliderView.javaClass
            val brightnessSlider = cl.getDeclaredField("mBrightnessSlider").get(sliderView) as SeekBar
            var max = brightnessSlider.max
            max = 1.coerceAtLeast(max)
            val percent = 0.coerceAtLeast(100.coerceAtMost(((progress * 100f) / max).roundToInt()))
            if (root.isInLayout) {
                schedulePositionUpdate(sliderView)
                return
            }
            setPercentTextIfChanged(percentView, String.format(Locale.US, "%d%%", percent))
            updateBrightnessPercentColor(sliderView, percentView)
            schedulePositionUpdate(sliderView)
        } catch (t: Throwable) {
            logger.error("Failed to refresh brightness from view", t)
        }
    }

    private fun updateBrightnessPercentColor(sliderView: Any, percentView: TextView) {
        percentView.setTextColor(resolveBrightnessPercentColor(sliderView))
    }

    private fun resolveBrightnessPercentColor(sliderView: Any): Int {
        val brightnessSlider: SeekBar?
        try {
            brightnessSlider = sliderView as? SeekBar
                ?: sliderView.javaClass.getDeclaredField("mBrightnessSlider").get(sliderView) as SeekBar
        } catch (_: Throwable) {
            return Color.argb(0xff, 0xd8, 0xd8, 0xd8)
        }
        val progress = ((brightnessSlider.progress - brightnessSlider.min) * 1.0f) /
                1.coerceAtLeast(brightnessSlider.max - brightnessSlider.min)
        if (progress < 0.2f) {
            return Color.argb(0xff, 0xd8, 0xd8, 0xd8)
        }
        var gray = ((1.0f - ((progress - 0.2f) / 0.2f).coerceAtMost(1.0f)) * 216.0f).toInt()
        gray = gray.coerceAtLeast(0x80)
        val alpha = (kotlin.math.floor(progress * 85.0f).toInt() + 170).coerceAtMost(255)
        return Color.argb(alpha, gray, gray, gray)
    }

    private fun formatPercent(progress: Int, min: Int, max: Int): String {
        val range = 1.coerceAtLeast(max - min)
        var value = (((progress - min) * 100f) / range).roundToInt()
        value = 0.coerceAtLeast(100.coerceAtMost(value))
        return String.format(Locale.US, "%d%%", value)
    }

    private fun createPercentView(context: Context): TextView {
        val textView = TextView(context)
        textView.tag = SLIDER_PERCENT_TAG
        textView.setTextColor(Color.argb(0xff, 0xd8, 0xd8, 0xd8))
        textView.setTypeface(Typeface.DEFAULT_BOLD)
        textView.textSize = 13f
        textView.setShadowLayer(2f, 0f, 0f, Color.BLACK)
        textView.isSingleLine = true
        textView.includeFontPadding = false
        textView.gravity = Gravity.CENTER
        textView.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        textView.isClickable = false
        textView.isFocusable = false
        textView.setPadding(dp(context, 6), dp(context, 2), dp(context, 6), dp(context, 2))
        textView.elevation = dp(context, 2).toFloat()
        return textView
    }

    private fun createLayoutParams(): FrameLayout.LayoutParams {
        val params = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        params.gravity = Gravity.TOP or Gravity.START
        return params
    }

    private fun findPercentView(root: FrameLayout): TextView? {
        val view = root.findViewWithTag<View>(SLIDER_PERCENT_TAG)
        return view as? TextView
    }

    private fun getFrameLayoutField(sliderView: Any): FrameLayout? {
        return try {
            val field = sliderView.javaClass.getDeclaredField(BRIGHTNESS_ROOT_FIELD).get(sliderView)
            field as? FrameLayout
        } catch (_: Throwable) {
            null
        }
    }

    private fun getViewField(sliderView: Any): View? {
        return try {
            val field = sliderView.javaClass.getDeclaredField(BRIGHTNESS_ICON_FIELD).get(sliderView)
            field as? View
        } catch (_: Throwable) {
            null
        }
    }

    private fun isBrightnessSlider(sliderView: Any, view: Any): Boolean {
        return try {
            val slider = sliderView.javaClass.getDeclaredField("mBrightnessSlider").get(sliderView)
            slider == view
        } catch (_: Throwable) {
            false
        }
    }

    private fun findToggleSliderView(seekBar: View): Any? {
        var current: View = seekBar
        for (i in 0 until 5) {
            val parent = current.parent
            if (parent !is View) {
                break
            }
            current = parent
            if (TOGGLE_SLIDER_VIEW_CLASS == current.javaClass.name) {
                return current
            }
        }
        return null
    }

    private fun ensureLayoutTracking(root: FrameLayout, icon: View, percentView: TextView) {
        if (layoutListeners.containsKey(root)) {
            schedulePositionUpdate(root, icon, percentView)
            return
        }

        val listener = View.OnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            schedulePositionUpdate(root, icon, percentView)
        }
        layoutListeners[root] = listener
        root.addOnLayoutChangeListener(listener)
        schedulePositionUpdate(root, icon, percentView)
    }

    private fun detachBrightnessLabel(sliderView: Any) {
        removePercentView(getFrameLayoutField(sliderView))
    }

    private fun removePercentView(root: FrameLayout?) {
        if (root == null) {
            return
        }
        val view = root.findViewWithTag<View>(SLIDER_PERCENT_TAG)
        if (view != null) {
            root.removeView(view)
        }
        val listener = layoutListeners.remove(root)
        if (listener != null) {
            root.removeOnLayoutChangeListener(listener)
        }
    }

    private fun schedulePositionUpdate(sliderView: Any) {
        val root = getFrameLayoutField(sliderView)
        val icon = getViewField(sliderView)
        if (root == null || icon == null) {
            return
        }
        val percentView = findPercentView(root) ?: return
        schedulePositionUpdate(root, icon, percentView)
    }

    private fun schedulePositionUpdate(root: FrameLayout, icon: View, percentView: TextView) {
        if (java.lang.Boolean.TRUE == pendingPositionUpdates[root]) {
            return
        }
        pendingPositionUpdates[root] = java.lang.Boolean.TRUE
        root.post {
            try {
                positionLabel(root, icon, percentView)
            } finally {
                pendingPositionUpdates.remove(root)
            }
        }
    }

    private fun positionLabel(root: FrameLayout, icon: View, percentView: TextView) {

        val rootWidth = root.width
        val rootHeight = root.height
        if (rootWidth <= 0 || rootHeight <= 0) {
            return
        }

        if (percentView.measuredWidth == 0 || percentView.measuredHeight == 0) {
            percentView.measure(
                View.MeasureSpec.makeMeasureSpec(rootWidth, View.MeasureSpec.AT_MOST),
                View.MeasureSpec.makeMeasureSpec(rootHeight, View.MeasureSpec.AT_MOST)
            )
        }

        val labelWidth = 1.coerceAtLeast(percentView.measuredWidth)
        val labelHeight = 1.coerceAtLeast(percentView.measuredHeight)
        val iconCenterX = icon.left + (icon.width / 2)
        var targetLeft = iconCenterX - (labelWidth / 2)
        var targetTop = icon.bottom + dp(root.context, LABEL_GAP_DP)

        targetLeft = 0.coerceAtLeast(targetLeft.coerceAtMost(0.coerceAtLeast(rootWidth - labelWidth)))
        targetTop = 0.coerceAtLeast(targetTop.coerceAtMost(0.coerceAtLeast(rootHeight - labelHeight)))

        var params = percentView.layoutParams as? FrameLayout.LayoutParams
        if (params == null) {
            params = createLayoutParams()
        }
        if (params.leftMargin == targetLeft && params.topMargin == targetTop
            && params.width == ViewGroup.LayoutParams.WRAP_CONTENT
            && params.height == ViewGroup.LayoutParams.WRAP_CONTENT
            && params.gravity == (Gravity.TOP or Gravity.START)
        ) {
            return
        }
        params.width = ViewGroup.LayoutParams.WRAP_CONTENT
        params.height = ViewGroup.LayoutParams.WRAP_CONTENT
        params.gravity = Gravity.TOP or Gravity.START
        params.leftMargin = targetLeft
        params.topMargin = targetTop
        percentView.layoutParams = params
    }

    private fun setPercentTextIfChanged(percentView: TextView, text: String) {
        if (TextUtils.equals(percentView.text, text)) {
            return
        }
        percentView.text = text
    }

    private fun dp(context: Context, value: Int): Int {
        return (value * context.resources.displayMetrics.density).roundToInt()
    }

    private fun updatePrefs() {
        percentageEnabled = try {
            remotePreferences.getBoolean(PreferenceKeys.BRIGHTNESS_SLIDER_PERCENTAGE.name, false)
        } catch (_: Throwable) {
            false
        }
    }

    private var percentageEnabled = false

    companion object {
        private val SYSTEM_UI_PACKAGE = ScopeKeys.SYSTEM_UI.packageName
        private const val TOGGLE_SLIDER_VIEW_CLASS = "com.android.systemui.settings.ToggleSliderView"
        private const val SLIDER_PERCENT_TAG = "ztool_control_center_slider_percent"
        private const val BRIGHTNESS_ROOT_FIELD = "mBrightnessSliderRoot"
        private const val BRIGHTNESS_ICON_FIELD = "mBrightnessIconMark"
        private const val LABEL_GAP_DP = 2
    }
}
