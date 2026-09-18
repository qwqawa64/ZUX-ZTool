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
        hookTileListenerReplacement(classLoader)
        hookTileTouchEvent(classLoader)
        hookToggleSliderTouchEvent(classLoader)
        hookNativeLongClickSuppression(classLoader)
    }

    /**
     * Tiles: replace the system's OnLongClickListener with ours after
     * QSTileViewImpl.init wires it. Ours plays the release squish and then:
     *  - large tiles (detail indicator present): indicator.performClick(),
     *    which opens the tile's DetailAdapter dialog;
     *  - small tiles: the captured original listener, preserving the native
     *    long-press routing (Settings page etc.).
     * Tiles without an original listener do nothing on long press.
     *
     * The framework long click (View.onTouchEvent -> checkForLongClick)
     * drives the timing, since QSTileViewImpl.onTouchEvent calls through to
     * super normally. The squish-in is driven from the touch hook below.
     */
    private fun hookTileListenerReplacement(classLoader: ClassLoader) {
        // CustomQSTileViewImpl (small tiles) overrides init, so the
        // superclass hook alone never fires for them. Hook both inits.
        val initClasses = listOf(QS_TILE_VIEW_CLASS, CUSTOM_QS_TILE_VIEW_CLASS)
        for (className in initClasses) {
            try {
                val initMethod: Method = classLoader.loadClass(className)
                    .getDeclaredMethod(
                        "init",
                        View.OnClickListener::class.java,
                        View.OnLongClickListener::class.java,
                        View.OnClickListener::class.java
                    )
                hookWithId(initMethod, "tile_listener_replace_$className") { chain ->
                    val result = chain.proceed()
                    val view = chain.thisObject as View
                    val original = chain.args[1] as? View.OnLongClickListener
                    originalTileListeners[view] = original
                    view.setOnLongClickListener { v ->
                        logger.debug(
                            "tile: our long click fired, indicator=" +
                                (findDetailIndicator(v) != null) +
                                ", hasOriginal=" + (original != null)
                        )
                        playReleaseAnimation(v)
                        val indicator = findDetailIndicator(v)
                        when {
                            indicator != null -> indicator.performClick()
                            original != null -> original.onLongClick(v)
                            else -> {
                                // No long-press behavior: animation only.
                            }
                        }
                        true
                    }
                    // Null out the native QSLongPressEffect: its own delayed
                    // animator overwrites our squish (and its end action
                    // routes long click to the Settings page) the moment
                    // isLongClickable becomes true.
                    disableNativeLongPressEffect(view)
                    result
                }
            } catch (t: Throwable) {
                logger.error("Failed to hook $className.init", t)
            }
        }
    }

    /**
     * Nulls the native QSLongPressEffect so its animator stops fighting our
     * squish and its completion no longer routes long click to Settings.
     */
    private fun disableNativeLongPressEffect(view: View) {
        try {
            val field = view.javaClass.getDeclaredField(LONG_PRESS_EFFECT_FIELD)
            field.isAccessible = true
            field.set(view, null)
        } catch (t: Throwable) {
            logger.error("Failed to null longPressEffect", t)
        }
    }

    /**
     * Visual-only touch tracking for tiles: squish-in on DOWN, release on
     * MOVE-beyond-slop / UP / CANCEL. No triggering logic — the framework
     * long click on the replaced listener handles that.
     */
    private fun hookTileTouchEvent(classLoader: ClassLoader) {
        try {
            val onTouchEvent: Method = classLoader
                .loadClass(QS_TILE_VIEW_CLASS)
                .getDeclaredMethod("onTouchEvent", MotionEvent::class.java)
            hookWithId(
                onTouchEvent,
                "tile_touch_squish",
                { chain ->
                    val result = chain.proceed()
                    try {
                        val view = chain.thisObject as View
                        val event = chain.args[0] as MotionEvent
                        when (event.actionMasked) {
                            MotionEvent.ACTION_DOWN -> {
                                suppressNativeLongClick = true
                                squishIn(view)
                            }

                            MotionEvent.ACTION_MOVE -> {
                                val state = pressStates[view]
                                if (state != null && !state.released) {
                                    val dx = event.rawX - state.downX
                                    val dy = event.rawY - state.downY
                                    if (dx * dx + dy * dy > state.touchSlopSquared) {
                                        state.released = true
                                        playReleaseAnimation(view)
                                    }
                                }
                            }

                            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                                suppressNativeLongClick = false
                                val state = pressStates[view]
                                if (state != null && !state.released) {
                                    // Long click (if any) fires around this
                                    // time; release the squish so the action
                                    // starts from a settled view.
                                    state.released = true
                                    playReleaseAnimation(view)
                                }
                            }
                        }
                    } catch (t: Throwable) {
                        logger.error("Tile squish tracking failed", t)
                    }
                    result
                },
                XposedInterface.PRIORITY_LOWEST
            )
        } catch (t: Throwable) {
            logger.error("Failed to hook QSTileViewImpl.onTouchEvent", t)
        }
    }

    private val originalTileListeners =
        WeakHashMap<View, View.OnLongClickListener?>()

    private fun squishIn(view: View) {
        val state = obtainState(view)
        state.downX = 0f
        state.downY = 0f
        state.released = false
        view.animate()
            .scaleX(SQUISH_SCALE_X)
            .scaleY(SQUISH_SCALE_Y)
            .setDuration(SQUISH_DURATION_MS)
            .setInterpolator(DecelerateInterpolator())
            .start()
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
                state.released = false
                view.animate()
                    .scaleX(SQUISH_SCALE_X)
                    .scaleY(SQUISH_SCALE_Y)
                    .setDuration(SQUISH_DURATION_MS)
                    .setInterpolator(DecelerateInterpolator())
                    .start()
                view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                cancelTrigger(view)
                val runnable = Runnable {
                    state.released = true
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
                if (state.released) return
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
                if (!state.released) {
                    releaseSquish(view, state)
                }
            }
        }
    }

    private fun playReleaseAnimation(view: View) {
        view.animate()
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(SQUISH_RELEASE_DURATION_MS)
            .setInterpolator(OvershootInterpolator(SQUISH_OVERSHOOT))
            .start()
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
        var released: Boolean = false
        var runnable: Runnable? = null
    }

    private companion object {
        const val QS_TILE_VIEW_CLASS = "com.android.systemui.qs.tileimpl.QSTileViewImpl"
        const val CUSTOM_QS_TILE_VIEW_CLASS =
            "com.android.systemui.qs.tileimpl.CustomQSTileViewImpl"
        const val LONG_PRESS_EFFECT_FIELD = "longPressEffect"
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
