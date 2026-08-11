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
import android.view.ViewParent
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

@SuppressLint("PrivateApi")
class VolumeSliderPercentageHook : AppHookModule() {

    private val layoutListeners = WeakHashMap<View, View.OnLayoutChangeListener>()
    private val pendingPositionUpdates = WeakHashMap<FrameLayout, Boolean>()

    override fun getModuleName(): String = PreferenceKeys.VOLUME_SLIDER_PERCENTAGE.name

    override fun getTargetPackages(): Array<String> = arrayOf(SYSTEM_UI_PACKAGE)

    override fun handleLoadPackage(param: PackageLoadedParam) {
        val classLoader = param.defaultClassLoader
        updatePrefs()
        hookToggleSliderViewLifecycle(classLoader)
        hookVolumeControllerCallbacks(classLoader)
        hookSeekProgressChanges(classLoader)
        logger.info("Volume slider percentage hooks installed")
    }

    private fun hookVolumeControllerCallbacks(classLoader: ClassLoader) {
        try {
            val updateMusicSliderMethod: Method = classLoader.loadClass(TOGGLE_SLIDER_VIEW_CLASS)
                .getDeclaredMethod("updateMusicSlider")
            hookWithId(updateMusicSliderMethod, "update_music_slider") { chain ->
                val result = chain.proceed()
                refreshVolumeFromToggleSlider(chain.thisObject)
                result
            }
        } catch (_: Throwable) {
        }

        try {
            val registerVolumeObserverMethod: Method = classLoader.loadClass(TOGGLE_SLIDER_VIEW_CLASS)
                .getDeclaredMethod("registerVolumeObserver")
            hookWithId(registerVolumeObserverMethod, "register_volume_observer") { chain ->
                val result = chain.proceed()
                refreshVolumeFromToggleSlider(chain.thisObject)
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
            val updateVolumeSliderMethod: Method = classLoader.loadClass(TOGGLE_SLIDER_VIEW_CLASS)
                .getDeclaredMethod("updateVolumeSlider")
            hookWithId(updateVolumeSliderMethod, "update_volume_slider") { chain ->
                val result = chain.proceed()
                attachSliderLabel(chain.thisObject)
                refreshVolumeLabel(chain.thisObject)
                result
            }
        } catch (_: Throwable) {
        }

        try {
            val setVolumeProgressMethod: Method = classLoader.loadClass(TOGGLE_SLIDER_VIEW_CLASS)
                .getDeclaredMethod("setVolumeProgress", Int::class.javaPrimitiveType)
            hookWithId(setVolumeProgressMethod, "set_volume_progress") { chain ->
                val result = chain.proceed()
                refreshVolumeLabel(chain.thisObject)
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
                if (isVolumeSlider(sliderView, progressBar)) {
                    refreshVolumeLabel(sliderView)
                }
                result
            }
        } catch (_: Throwable) {
        }
    }

    private fun hookSeekProgressChanges(classLoader: ClassLoader) {
        try {
            val onProgressChangedMethod: Method = classLoader
                .loadClass("com.android.systemui.settings.ToggleSliderView\$2")
                .getDeclaredMethod(
                    "onProgressChanged",
                    SeekBar::class.java,
                    Int::class.javaPrimitiveType,
                    Boolean::class.javaPrimitiveType
                )
            hookWithId(onProgressChangedMethod, "on_progress_changed") { chain ->
                val result = chain.proceed()
                val seekBar = chain.args[0] as SeekBar
                val sliderView = findToggleSliderView(seekBar)
                if (sliderView == null || !isVolumeSlider(sliderView, seekBar)) {
                    return@hookWithId result
                }
                if (java.lang.Boolean.TRUE == chain.args[2]) {
                    refreshVolumeLabel(sliderView, chain.args[1] as Int)
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
            logger.error("Failed to attach volume slider label", t)
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
        val rawProgress = getVolumeRawProgress(sliderView, null)
        if (rawProgress != null) {
            updateVolumePercentColor(percentView, rawProgress)
        }
        refreshVolumeLabel(sliderView, percentView, null)
    }

    private fun refreshVolumeLabel(sliderView: Any) {
        refreshVolumeLabel(sliderView, null)
    }

    private fun refreshVolumeLabel(sliderView: Any, rawProgress: Int?) {
        if (!percentageEnabled) {
            detachVolumeLabel(sliderView)
            return
        }
        val root = getFrameLayoutField(sliderView)
        if (root == null) {
            return
        }
        val percentView = findPercentView(root)
        if (percentView != null) {
            refreshVolumeLabel(sliderView, percentView, rawProgress)
        }
    }

    private fun refreshVolumeLabel(sliderView: Any, percentView: TextView, rawProgress: Int?) {
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
            val rawProgress2 = getVolumeRawProgress(sliderView, rawProgress)
            val volumeProgress = getVolumeProgress(sliderView, rawProgress)
            if (rawProgress2 == null || volumeProgress == null) {
                setPercentTextIfChanged(percentView, "--%")
                return
            }
            updateVolumePercentColor(percentView, rawProgress2)
            setPercentTextIfChanged(percentView, formatPercent(volumeProgress))
            schedulePositionUpdate(sliderView)
        } catch (t: Throwable) {
            setPercentTextIfChanged(percentView, "--%")
            logger.error("Failed to refresh volume percent", t)
        }
    }

    private fun refreshVolumeFromToggleSlider(sliderView: Any) {
        if (!percentageEnabled || sliderView == null) {
            return
        }
        try {
            val rawProgress2 = getVolumeRawProgress(sliderView, null)
            val volumeProgress = getVolumeProgress(sliderView, null)
            if (rawProgress2 == null || volumeProgress == null) {
                return
            }
            val root = getFrameLayoutField(sliderView)
            if (root == null) {
                return
            }
            val percentView = findPercentView(root)
            if (percentView == null) {
                return
            }
            updateVolumePercentColor(percentView, rawProgress2)
            if (root.isInLayout) {
                schedulePositionUpdate(sliderView)
                return
            }
            setPercentTextIfChanged(percentView, formatPercent(volumeProgress))
            schedulePositionUpdate(sliderView)
        } catch (t: Throwable) {
            logger.error("Failed to refresh volume from toggle slider", t)
        }
    }

    private fun getVolumeRawProgress(sliderView: Any, rawProgress: Int?): Int? {
        return try {
            val volumeSlider = sliderView.javaClass
                .getDeclaredField("mMediaVolumeSlider").get(sliderView) as SeekBar
            if (volumeSlider == null) {
                null
            } else {
                rawProgress ?: volumeSlider.progress
            }
        } catch (_: Throwable) {
            null
        }
    }

    private fun getVolumeProgress(sliderView: Any, rawProgress: Int?): Int? {
        return try {
            val volumeSlider = sliderView.javaClass
                .getDeclaredField("mMediaVolumeSlider").get(sliderView) as SeekBar
            if (volumeSlider == null) {
                return null
            }
            val progress = rawProgress ?: volumeSlider.progress
            val min = volumeSlider.min
            val max = volumeSlider.max
            val range = Math.max(1, max - min)
            var percent = Math.round(((progress - min) * 100f) / range)
            percent = Math.max(0, Math.min(100, percent))
            percent
        } catch (t: Throwable) {
            logger.error("Failed to resolve volume progress", t)
            null
        }
    }

    private fun formatPercent(progress: Int): String {
        val range = Math.max(1, 100)
        var value = Math.round((progress * 100f) / range)
        value = Math.max(0, Math.min(100, value))
        return String.format(Locale.US, "%d%%", value)
    }

    private fun createPercentView(context: Context): TextView {
        val textView = TextView(context)
        textView.setTag(SLIDER_PERCENT_TAG)
        textView.setTextColor(Color.argb(0xff, 0xd8, 0xd8, 0xd8))
        textView.setTypeface(Typeface.DEFAULT_BOLD)
        textView.textSize = 13f
        textView.setShadowLayer(2f, 0f, 0f, Color.BLACK)
        textView.setSingleLine(true)
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
            val field = sliderView.javaClass.getDeclaredField(VOLUME_ROOT_FIELD).get(sliderView)
            field as? FrameLayout
        } catch (_: Throwable) {
            null
        }
    }

    private fun getViewField(sliderView: Any): View? {
        return try {
            val field = sliderView.javaClass.getDeclaredField(VOLUME_ICON_FIELD).get(sliderView)
            field as? View
        } catch (_: Throwable) {
            null
        }
    }

    private fun isVolumeSlider(sliderView: Any, view: Any): Boolean {
        return try {
            val slider = sliderView.javaClass.getDeclaredField("mMediaVolumeSlider").get(sliderView)
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

    private fun detachVolumeLabel(sliderView: Any) {
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
        val percentView = findPercentView(root)
        if (percentView == null) {
            return
        }
        schedulePositionUpdate(root, icon, percentView)
    }

    private fun schedulePositionUpdate(root: FrameLayout, icon: View, percentView: TextView) {
        if (root == null || icon == null || percentView == null) {
            return
        }
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
        if (root == null || icon == null || percentView == null) {
            return
        }

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

        val labelWidth = Math.max(1, percentView.measuredWidth)
        val labelHeight = Math.max(1, percentView.measuredHeight)
        val iconCenterX = icon.left + (icon.width / 2)
        var targetLeft = iconCenterX - (labelWidth / 2)
        var targetTop = icon.bottom + dp(root.context, LABEL_GAP_DP)

        targetLeft = Math.max(0, Math.min(targetLeft, Math.max(0, rootWidth - labelWidth)))
        targetTop = Math.max(0, Math.min(targetTop, Math.max(0, rootHeight - labelHeight)))

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
        if (percentView == null || TextUtils.equals(percentView.text, text)) {
            return
        }
        percentView.text = text
    }

    private fun dp(context: Context, value: Int): Int {
        return Math.round(value * context.resources.displayMetrics.density)
    }

    private fun updateVolumePercentColor(percentView: TextView, seekBarProgress: Int) {
        percentView.setTextColor(resolveVolumePercentColor(seekBarProgress))
    }

    private fun resolveVolumePercentColor(seekBarProgress: Int): Int {
        val progress = (seekBarProgress * 1.0f) / 15000.0f
        if (progress < 0.2f) {
            return Color.argb(0xff, 0xd8, 0xd8, 0xd8)
        }
        var gray = ((1.0f - Math.min((progress - 0.2f) / 0.2f, 1.0f)) * 216.0f).toInt()
        gray = Math.max(gray, 0x80)
        val alpha = Math.min(kotlin.math.floor(progress * 85.0f).toInt() + 170, 255)
        return Color.argb(alpha, gray, gray, gray)
    }

    private fun updatePrefs() {
        percentageEnabled = try {
            remotePreferences.getBoolean(PreferenceKeys.VOLUME_SLIDER_PERCENTAGE.name, false)
        } catch (_: Throwable) {
            false
        }
    }

    private var percentageEnabled = false

    companion object {
        private val SYSTEM_UI_PACKAGE = ScopeKeys.SYSTEM_UI.packageName
        private const val TOGGLE_SLIDER_VIEW_CLASS = "com.android.systemui.settings.ToggleSliderView"
        private const val SLIDER_PERCENT_TAG = "ztool_control_center_slider_percent"
        private const val VOLUME_ROOT_FIELD = "mVolumeSliderRoot"
        private const val VOLUME_ICON_FIELD = "mMediaVolumeIconMark"
        private const val LABEL_GAP_DP = 2
    }
}
