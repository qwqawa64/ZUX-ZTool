package com.qimian233.ztool.hook.modules.systemui.qs

import android.annotation.SuppressLint
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import androidx.core.view.isVisible
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
 * / Wi-Fi and longClickIntent settings pages for others — but the squish
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
@SuppressLint("DiscouragedPrivateApi", "PrivateApi")
class ControlCenterLongPressHook : AppHookModule() {

    private val pressStates = WeakHashMap<View, PressState>()

    // True while one of our gestures is active: blocks the native
    // QSLongPressEffect pipeline's delayed qsTile.longClick() for this gesture.
    @Volatile
    private var suppressNativeLongClick = false

    // True from the moment our long-click listener fires until the trailing
    // UP/CANCEL: the window where ZUI's click() fallback must stay blocked.
    // Per-gesture flag instead of scanning pressStates — stale released
    // states from other tile instances poisoned a global scan.
    @Volatile
    private var gestureTriggered = false

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
                    view.setOnLongClickListener { v ->
                        logger.debug(
                            "tile: our long click fired, indicator=" +
                                (findDetailIndicator(v) != null) +
                                ", hasOriginal=" + (original != null) +
                                ", suppress=" + suppressNativeLongClick +
                                ", gestureEnding=" + gestureEnding()
                        )
                        playReleaseAnimation(v)
                        // Our own routing below re-enters QSTileImpl.longClick;
                        // lift the suppression for the duration so we don't
                        // swallow the very call we just triggered.
                        gestureTriggered = false
                        val indicator = findDetailIndicator(v)
                        when {
                            indicator != null -> {
                                logger.debug("tile: routing to detailIndicator.performClick()")
                                indicator.performClick()
                            }
                            original != null -> {
                                logger.debug("tile: routing to original.onLongClick()")
                                original.onLongClick(v)
                            }
                            else -> {
                                // No long-press behavior: animation only.
                                logger.debug("tile: no routing target, animation only")
                            }
                        }
                        gestureTriggered = true
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
            var clazz: Class<*>? = view.javaClass
            var field: java.lang.reflect.Field? = null
            while (clazz != null) {
                field = try {
                    clazz.getDeclaredField(LONG_PRESS_EFFECT_FIELD)
                } catch (_: NoSuchFieldException) {
                    null
                }
                if (field != null) break
                clazz = clazz.superclass
            }
            if (field != null) {
                field.isAccessible = true
                field.set(view, null)
            } else {
                logger.warn("longPressEffect field not found in hierarchy")
            }
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
                                gestureTriggered = false
                                val state = obtainState(view)
                                state.downX = event.rawX
                                state.downY = event.rawY
                                state.released = false
                                logger.debug(
                                    "tile: DOWN on " + view.javaClass.simpleName +
                                        "@" + Integer.toHexString(System.identityHashCode(view)) +
                                        ", suppress=true, gestureTriggered=false"
                                )
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
                                logger.debug(
                                    "tile: " +
                                        (if (event.actionMasked == MotionEvent.ACTION_UP) "UP" else "CANCEL") +
                                        " on " + view.javaClass.simpleName +
                                        "@" + Integer.toHexString(System.identityHashCode(view)) +
                                        ", suppress=false, stateReleased=" + (state?.released ?: "null") +
                                        ", gestureTriggered=" + gestureTriggered
                                )
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

    private fun squishIn(view: View) {
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
            (field.get(view) as? View)?.takeIf { it.isVisible && it.width > 0 }
        } catch (_: Throwable) {
            null
        }
    }

    /**
     * Blocks the native QSLongPressEffect pipeline's qsTile.longClick() and
     * ZUI's click() fallback while one of our gestures is in flight. The
     * Expandable parameter type moved packages on this ROM, so hook targets
     * are discovered from the declared method signatures instead of by name.
     */
    private fun hookNativeLongClickSuppression(classLoader: ClassLoader) {
        val implClass = classLoader.loadClass(QS_TILE_IMPL_CLASS)
        val singleParamByName = implClass.declaredMethods
            .filter { it.parameterTypes.size == 1 }
            .groupBy { it.name }
        for ((name, suppressAlways) in listOf("longClick" to true, "click" to false)) {
            val candidates = singleParamByName[name]
            if (candidates.isNullOrEmpty()) {
                logger.warn("QSTileImpl.$name not found for suppression hook")
                continue
            }
            for (method in candidates) {
                val methodName = method.name
                val paramTypeName = method.parameterTypes[0].name
                hookWithId(
                    method,
                    "tile_${name}_suppress_$paramTypeName",
                    { chain ->
                        if (suppressNativeLongClick &&
                            (!suppressAlways || gestureEnding())
                        ) {
                            logger.debug(
                                "tile: suppressed QSTileImpl.$methodName($paramTypeName)" +
                                    ", suppress=" + suppressNativeLongClick +
                                    ", gestureEnding=" + gestureEnding()
                            )
                            null
                        } else {
                            logger.debug(
                                "tile: PASSTHROUGH QSTileImpl.$methodName($paramTypeName)" +
                                    ", suppress=" + suppressNativeLongClick +
                                    ", gestureEnding=" + gestureEnding()
                            )
                            chain.proceed()
                        }
                    },
                    XposedInterface.PRIORITY_HIGHEST
                )
            }
        }
    }

    /**
     * True from when our listener triggered until the trailing UP/CANCEL:
     * the window where ZUI's click() fallback must stay blocked. Managed
     * explicitly by the touch hook (set on trigger-clearing UP, cleared on
     * the next DOWN) — no cross-gesture state.
     */
    private fun gestureEnding(): Boolean = gestureTriggered

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
        // The QS volume slider is a zui.widget.SeekBarNps, not a
        // ToggleSeekBar — hook it too, but animation-only: this ROM has no
        // native volume panel behind a slider long press.
        try {
            val onTouchEvent: Method = classLoader
                .loadClass(SEEK_BAR_NPS_CLASS)
                .getDeclaredMethod("onTouchEvent", MotionEvent::class.java)
            hookWithId(
                onTouchEvent,
                "slider_touch_long_press_nps",
                { chain ->
                    val result = chain.proceed()
                    val view = chain.thisObject as View
                    if (isVolumeSliderView(view)) {
                        trackPress(view, chain.args[0] as MotionEvent) {
                            logger.debug("slider: volume long press, animation only")
                        }
                    }
                    result
                },
                XposedInterface.PRIORITY_LOWEST
            )
        } catch (t: Throwable) {
            logger.error("Failed to hook SeekBarNps.onTouchEvent", t)
        }
    }

    /**
     * True when the touched view is the media-volume SeekBar of an enclosing
     * ToggleSliderView. Guards the SeekBarNps hook from animating unrelated
     * sliders elsewhere in SystemUI.
     */
    private fun isVolumeSliderView(view: View): Boolean {
        val host = findToggleSliderView(view) ?: return false
        return try {
            val field = host.javaClass.getDeclaredField(VOLUME_SLIDER_FIELD)
            field.isAccessible = true
            field.get(host) == view
        } catch (_: Throwable) {
            false
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
                    releaseSquish(view)
                }
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                suppressNativeLongClick = false
                val state = pressStates[view] ?: return
                if (!state.released) {
                    releaseSquish(view)
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

    private fun releaseSquish(view: View) {
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
        repeat(6) {
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
        const val TOGGLE_SLIDER_VIEW_CLASS = "com.android.systemui.settings.ToggleSliderView"
        const val TOGGLE_SEEK_BAR_CLASS =
            "com.android.systemui.settings.brightness.ToggleSeekBar"
        const val SEEK_BAR_NPS_CLASS = "zui.widget.SeekBarNps"
        const val VOLUME_SLIDER_FIELD = "mMediaVolumeSlider"
        const val DETAIL_INDICATOR_FIELD = "detailIndicatorView"
        const val SQUISH_SCALE_X = 0.94f
        const val SQUISH_SCALE_Y = 0.90f
        const val SQUISH_DURATION_MS = 120L
        const val SQUISH_RELEASE_DURATION_MS = 180L
        const val SQUISH_OVERSHOOT = 1.2f
    }
}
