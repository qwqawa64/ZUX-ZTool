package com.qimian233.ztool.hook.modules.systemui.qs

import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import com.qimian233.ztool.data.keys.PreferenceKeys
import com.qimian233.ztool.data.keys.ScopeKeys
import com.qimian233.ztool.hook.base.AppHookModule
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam
import java.lang.reflect.Method
import java.util.WeakHashMap

/**
 * Unified long-press animation for control-center components, gated by the
 * single [PreferenceKeys.CONTROL_CENTER_LONG_PRESS] switch.
 *
 * Tiles (QSTileViewImpl, both large and small): the tile's own long-click
 * listener routes to its native target — DetailAdapter dialogs for Bluetooth
 * / WiFi and longClickIntent settings pages for others — but the squish
 * animation never plays on this ROM. Hook onTouchEvent with a hand-rolled
 * press detector: DOWN plays the squish-in and schedules the trigger;
 * MOVE beyond slop or UP/CANCEL reverses it. The trigger calls
 * View.performLongClick(), so the tile's own routing (dialog vs settings)
 * is preserved unchanged.
 *
 * Sliders (ToggleSliderView brightness / volume): the same detector drives
 * the squish animation; on trigger the brightness slider opens
 * openBrightnessDetail() and the volume slider opens the media/ringer volume
 * panel (see VolumeSliderLongPressHook for the dialog construction — it stays
 * there to keep dialog state local; only the detector is shared here).
 *
 * Trigger timing follows the system configuration via
 * ViewConfiguration.scaledLongPressTimeout.
 */
class ControlCenterLongPressHook : AppHookModule() {

    private val pressStates = WeakHashMap<View, PressState>()

    // True while one of our gestures is active: blocks the native
    // QSLongPressEffect pipeline's delayed qsTile.longClick() for this gesture.
    @Volatile
    private var suppressNativeLongClick = false

    override fun getModuleName(): String = PreferenceKeys.CONTROL_CENTER_LONG_PRESS.name

    override fun getTargetPackages(): Array<String> = arrayOf(ScopeKeys.SYSTEM_UI.packageName)

    override fun handleLoadPackage(param: PackageLoadedParam) {
        val classLoader = param.defaultClassLoader
        hookTileTouchEvent(classLoader)
        hookToggleSliderTouchEvent(classLoader)
        hookNativeLongClickSuppression(classLoader)
    }

    /**
     * Tiles: QSTileViewImpl.onTouchEvent already forwards to
     * super.onTouchEvent, so an after-hook sees every event and a
     * performLongClick() from our detector routes through the tile's own
     * OnLongClickListener.
     *
     * Two tile-specific behaviors on top of the shared detector:
     *  - Large tiles (detail indicator present) open the DetailAdapter dialog
     *    by simulating a click on the indicator, because the tile's own
     *    long-press routes to a Settings page instead of the dialog.
     *  - After the long-press trigger the gesture is consumed: an
     *    UP that arrives after the trigger is blocked from the original
     *    method, so tiles without a long-press handler (flashlight) do not
     *    additionally fire the click toggle.
     */
    private fun hookTileTouchEvent(classLoader: ClassLoader) {
        try {
            val onTouchEvent: Method = classLoader
                .loadClass(QS_TILE_VIEW_CLASS)
                .getDeclaredMethod("onTouchEvent", MotionEvent::class.java)
            hookWithId(
                onTouchEvent,
                "tile_touch_long_press",
                { chain ->
                    val view = chain.thisObject as View
                    val event = chain.args[0] as MotionEvent
                    if (event.actionMasked == MotionEvent.ACTION_UP ||
                        event.actionMasked == MotionEvent.ACTION_CANCEL
                    ) {
                        val state = pressStates[view]
                        if (state?.triggered == true) {
                            // Gesture already handled by the long-press
                            // trigger; swallow the trailing UP so the tile's
                            // click toggle does not fire on top of it.
                            logger.debug("tile: swallowing trailing UP after trigger")
                            cleanupState(view)
                            suppressNativeLongClick = false
                            return@hookWithId true
                        }
                        suppressNativeLongClick = false
                        if (state?.squished == true) {
                            logger.debug("tile: UP without trigger, releasing squish")
                            releaseSquish(view, state)
                        }
                        chain.proceed()
                        null
                    }
                    val result = chain.proceed()
                    trackPress(view, event) { v ->
                        val indicator = findDetailIndicator(v)
                        val hasLongClick = hasOnLongClickListener(v)
                        logger.debug(
                            "tile: long-press triggered, indicator=" + (indicator != null) +
                                ", hasLongClickListener=" + hasLongClick
                        )
                        if (indicator != null) {
                            // Large tile: open the DetailAdapter dialog via
                            // its own indicator button.
                            indicator.performClick()
                        } else if (hasLongClick) {
                            v.performLongClick()
                        }
                        // Tiles without a long-press handler do nothing:
                        // View.performLongClick would otherwise fall back to
                        // performClick and toggle the tile.
                    }
                    result
                },
                XposedInterface.PRIORITY_LOWEST
            )
        } catch (t: Throwable) {
            logger.error("Failed to hook QSTileViewImpl.onTouchEvent", t)
        }
    }

    /**
     * The large-tile detail indicator (right-bottom ImageButton) — its click
     * opens the tile's DetailAdapter dialog. Tiles without one return null.
     */
    private fun findDetailIndicator(view: View): View? {
        return try {
            val field = view.javaClass.getDeclaredField(DETAIL_INDICATOR_FIELD)
            field.isAccessible = true
            (field.get(view) as? View)?.takeIf { it.visibility == View.VISIBLE && it.width > 0 }
        } catch (_: Throwable) {
            null
        }
    }

    private fun cleanupState(view: View) {
        pressStates.remove(view)
    }

    /**
     * True when the view has its own OnLongClickListener. Without it,
     * View.performLongClick falls back to performClick, which would toggle
     * tiles that have no long-press behavior.
     */
    private fun hasOnLongClickListener(view: View): Boolean {
        return try {
            val listenerInfoField = View::class.java.getDeclaredField("mListenerInfo")
            listenerInfoField.isAccessible = true
            val listenerInfo = listenerInfoField.get(view) ?: return false
            val listenerField = listenerInfo.javaClass.getDeclaredField("mOnLongClickListener")
            listenerField.isAccessible = true
            listenerField.get(listenerInfo) != null
        } catch (_: Throwable) {
            false
        }
    }

    /**
     * Blocks the native QSLongPressEffect pipeline's qsTile.longClick() while
     * one of our gestures is in flight: the pipeline posts its own delayed
     * task on ACTION_DOWN that would otherwise fire the tile's long-press
     * action a second time (dialog + settings page on large tiles).
     */
    private fun hookNativeLongClickSuppression(classLoader: ClassLoader) {
        try {
            val longClick: Method = classLoader
                .loadClass(QS_TILE_IMPL_CLASS)
                .getDeclaredMethod("longClick", Class.forName(EXPANDABLE_CLASS))
            hookWithId(
                longClick,
                "tile_native_long_click_suppress",
                { chain ->
                    if (suppressNativeLongClick) {
                        null
                    } else {
                        chain.proceed()
                    }
                },
                XposedInterface.PRIORITY_HIGHEST
            )
        } catch (t: Throwable) {
            logger.error("Failed to hook QSTileImpl.longClick", t)
        }
    }

    /**
     * Sliders: SeekBarNps bypasses View.onTouchEvent when max >= 100, so
     * hook ToggleSeekBar.onTouchEvent directly (same rationale as the
     * brightness hook). Trigger invokes openBrightnessDetail on the
     * enclosing ToggleSliderView.
     */
    private fun hookToggleSliderTouchEvent(classLoader: ClassLoader) {
        try {
            val onTouchEvent: Method = classLoader
                .loadClass(TOGGLE_SEEK_BAR_CLASS)
                .getDeclaredMethod("onTouchEvent", MotionEvent::class.java)
            hookWithId(
                onTouchEvent,
                "slider_touch_long_press",
                { chain ->
                    val result = chain.proceed()
                    trackPress(chain.thisObject as View, chain.args[0] as MotionEvent) { v ->
                        val host = findToggleSliderView(v)
                        if (host != null) {
                            openBrightnessDetail(host)
                        }
                    }
                    result
                },
                XposedInterface.PRIORITY_LOWEST
            )
        } catch (t: Throwable) {
            logger.error("Failed to hook ToggleSeekBar.onTouchEvent", t)
        }
    }

    private fun trackPress(view: View, event: MotionEvent, onTrigger: (View) -> Unit) {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                // Our gesture owns this touch from now on: any native
                // qsTile.longClick() firing during it is suppressed.
                suppressNativeLongClick = true
                logger.debug("press: ACTION_DOWN on " + view.javaClass.simpleName)
                val state = obtainState(view)
                state.downX = event.rawX
                state.downY = event.rawY
                state.triggered = false
                state.squished = true
                view.animate()
                    .scaleX(SQUISH_SCALE_X)
                    .scaleY(SQUISH_SCALE_Y)
                    .setDuration(SQUISH_DURATION_MS)
                    .setInterpolator(DecelerateInterpolator())
                    .start()
                view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                cancelTrigger(view)
                val runnable = Runnable {
                    state.triggered = true
                    view.animate()
                        .scaleX(1f)
                        .scaleY(1f)
                        .setDuration(SQUISH_RELEASE_DURATION_MS)
                        .setInterpolator(OvershootInterpolator(SQUISH_OVERSHOOT))
                        .start()
                    onTrigger(view)
                }
                state.runnable = runnable
                view.postDelayed(runnable, longPressTimeout)
            }

            MotionEvent.ACTION_MOVE -> {
                val state = pressStates[view] ?: return
                if (state.triggered) return
                val dx = event.rawX - state.downX
                val dy = event.rawY - state.downY
                if (dx * dx + dy * dy > state.touchSlopSquared) {
                    logger.debug("press: MOVE beyond slop, cancelling long press")
                    suppressNativeLongClick = false
                    releaseSquish(view, state)
                }
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                suppressNativeLongClick = false
                val state = pressStates[view] ?: return
                if (!state.triggered) {
                    releaseSquish(view, state)
                }
            }
        }
    }

    private fun releaseSquish(view: View, state: PressState) {
        cancelTrigger(view)
        view.animate()
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(SQUISH_RELEASE_DURATION_MS)
            .setInterpolator(OvershootInterpolator(SQUISH_OVERSHOOT))
            .start()
    }

    private fun cancelTrigger(view: View) {
        val state = pressStates[view] ?: return
        state.runnable?.let { view.removeCallbacks(it) }
        state.runnable = null
    }

    private fun obtainState(view: View): PressState {
        val config = ViewConfiguration.get(view.context)
        return pressStates[view] ?: PressState(config.scaledTouchSlop.toFloat()).also { pressStates[view] = it }
    }

    private val longPressTimeout: Long
        get() = try {
            ViewConfiguration.getLongPressTimeout().toLong()
        } catch (_: Throwable) {
            500L
        }

    private fun findToggleSliderView(view: View): Any? {
        var current: View = view
        for (i in 0 until 6) {
            current = current.parent as? View ?: return null
            if (current.javaClass.name == TOGGLE_SLIDER_VIEW_CLASS) return current
        }
        return null
    }

    private fun openBrightnessDetail(sliderView: Any) {
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
        var squished: Boolean = false
        var runnable: Runnable? = null
    }

    private companion object {
        const val QS_TILE_VIEW_CLASS = "com.android.systemui.qs.tileimpl.QSTileViewImpl"
        const val QS_TILE_IMPL_CLASS = "com.android.systemui.qs.tileimpl.QSTileImpl"
        const val EXPANDABLE_CLASS = "com.android.systemui.animation.Expandable"
        const val TOGGLE_SLIDER_VIEW_CLASS = "com.android.systemui.settings.ToggleSliderView"
        const val TOGGLE_SEEK_BAR_CLASS =
            "com.android.systemui.settings.brightness.ToggleSeekBar"
        const val DETAIL_INDICATOR_FIELD = "detailIndicatorView"
        const val SQUISH_SCALE_X = 0.94f
        const val SQUISH_SCALE_Y = 0.90f
        const val SQUISH_DURATION_MS = 120L
        const val SQUISH_RELEASE_DURATION_MS = 180L
        const val SQUISH_OVERSHOOT = 1.2f
    }
}
