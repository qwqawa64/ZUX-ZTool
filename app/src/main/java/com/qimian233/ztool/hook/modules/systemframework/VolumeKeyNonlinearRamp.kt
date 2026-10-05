package com.qimian233.ztool.hook.modules.systemframework

import android.annotation.SuppressLint
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import com.qimian233.ztool.data.keys.PreferenceKeys
import com.qimian233.ztool.data.keys.ScopeKeys
import com.qimian233.ztool.hook.base.SystemHookModule
import io.github.libxposed.api.XposedModuleInterface.SystemServerStartingParam
import java.lang.reflect.Method

/**
 * Non-linear volume key ramping hook module.
 *
 * Stock behavior (traced live on this ZUI ROM, 2026-10): holding a volume key
 * is NOT driven by input-pipeline key repeats — PhoneWindowManager only sees
 * the initial ACTION_DOWN and the final ACTION_UP. Instead a single key event
 * arms a ~50ms Handler loop in MediaSessionService
 * (SessionManagerImpl$3.run) that repeatedly calls
 * AudioService.adjustSuggestedStreamVolume(±1) until key release. That fixed
 * ~20 ticks/s cadence caps any pass/suppress throttle at 20 steps/s, and
 * forcing faster rates by multi-invoking the adjust per tick visibly
 * stutters the volume panel animation.
 *
 * This hook therefore replaces the loop cadence with its own driver:
 *
 *  - The 9-arg AudioService.adjustSuggestedStreamVolume stays the single
 *    choke point. On the first FLAG_FROM_KEY adjust of a press the hook
 *    captures the AudioService instance and the call arguments, lets that
 *    first step through (single short presses stay stock), and arms a
 *    self-scheduled Handler. All subsequent native loop ticks are dropped.
 *  - The driver invokes the original adjust (via reflection; a reentry flag
 *    routes it straight through the hook) at a variable interval of
 *    1/speed(t), speed(t) = min(V0 + ACCEL * t, MAX) — continuously
 *    accelerating, with no tick-rate ceiling. One adjust per driver tick
 *    keeps the panel animation perfectly uniform.
 *  - The native ticks double as a heartbeat while the key is held; the
 *    driver stops when they stop (key released) or on the ADJUST_SAME
 *    release cue.
 *  - Re-entering `adjustSuggestedStreamVolume` also re-enters the framework's
 *    long-press suppression (`VolumeController.suppressAdjustment`), which
 *    forces `direction = 0` for `mLongPressTimeout` ms after the panel is
 *    first shown and therefore swallows the whole ramp. That gate is
 *    neutralised for driver sessions — see [hookSuppressAdjustment].
 *
 * ## How the ramp is emitted, and its curve
 *
 * One `adjustSuggestedStreamVolume` moves exactly **one** user step, so a
 * 150-position sweep would need 150 calls — and the volume panel only survives
 * ~50 updates/s: each call costs ~2.9 ms of synchronous AudioService work plus
 * a binder into SystemUI, and at ~110 updates/s the panel's UI thread
 * saturates and stops repainting mid-sweep (measured 2026-10-06: 150-step
 * sweeps of 1.44-1.63 s at ~110 ticks/s while the curve intended 0.93 s).
 *
 * The driver therefore moves [STEPS_PER_UPDATE] fine steps per update via
 * `AudioService.setStreamVolume(stream, absoluteIndex, …)`, which can move any
 * number of steps in a single call. This is the same trick behind HyperOS's
 * `getMusicVolumeStep` returning `maxVolume / 15` (= 10 steps per press) —
 * see docs/research/hyperos-volume/README.md §7.1.
 *
 * The curve is consequently written in **update rates**, which is what the
 * panel actually renders. Stock reference (traced above): 15 media steps at
 * the native ~50 ms loop = 20 stock-steps/s = 200 fine steps/s, full sweep
 * 0.75 s, constant.
 *
 * | quantity                           | updates/s | fine steps/s | stock-steps/s |
 * |------------------------------------|-----------|--------------|---------------|
 * | start rate V0                      | 8         | 40           | 4             |
 * | cap MAX                            | 40        | 200          | 20 (= stock)  |
 * | acceleration (cap at 30% of range) | 85.3/s²   | 426.7/s²     | 42.7/s²       |
 *
 * With `STEPS_PER_UPDATE = 5` the range is 30 updates: the first lands 125 ms
 * after the press, the cap is reached after 0.38 s / 9 updates, and a full
 * sweep takes **~0.90 s** with a peak of 40 updates/s — below the 50/s the
 * panel was measured to survive ([MIN_TICK_MS]) and below the load of the
 * earlier one-step-per-tick version. The tail rate equals stock's.
 *
 * Driving the absolute index also makes the ramp self-correcting (no drift),
 * and any press that does not resolve to the music stream falls back to the
 * original one-step driver, unchanged.
 */
@SuppressLint("PrivateApi", "DiscouragedPrivateApi")
class VolumeKeyNonlinearRamp : SystemHookModule() {

    override fun getModuleName(): String = PreferenceKeys.VOLUME_KEY_NONLINEAR.name

    override fun getTargetPackages(): Array<String> = arrayOf(ScopeKeys.ANDROID_SYSTEM.packageName)

    private var adjustMethod: Method? = null
    private var streamVolumeMethod: Method? = null
    private var streamMaxVolumeMethod: Method? = null
    private var setStreamVolumeMethod: Method? = null
    private val driverHandler = Handler(Looper.getMainLooper())

    // Session state. `driverActive` / `sessionStartVolume` / `driverIntervalMs`
    // / `absoluteMode` are written from the AudioService (binder) thread and
    // read from the main thread, so they are volatile; the rest is
    // main-thread only.
    private var sessionStart = 0L
    private var sessionApplied = 0
    private var sessionDirection = 0
    private var lastTickTime = 0L
    private var audioService: Any? = null
    private var argsTemplate: Array<Any?> = emptyArray()

    /** True while the self-scheduled driver owns the key cadence. Also read by
     *  the [HOOK_ID_SUPPRESS] hook, which runs on the calling thread. */
    @Volatile
    private var driverActive = false

    /** Media-stream volume at session start, for the "did the index actually
     *  move" probe and the [stopDriver] diagnostic. */
    @Volatile
    private var sessionStartVolume = -1

    /** Media-stream max index (steps) at session start, used to clamp the
     *  absolute targets. */
    private var sessionMaxVolume = -1

    /** Flags / calling package of the native call, replayed to
     *  `setStreamVolume` so the panel still sees a key-driven change. */
    private var updateFlags = 0
    private var updateCallingPackage = "android"

    /** Interval the driver last scheduled, so the ADJUST_SAME refresh grid can
     *  be skipped once real steps already arrive at least that often. */
    @Volatile
    private var driverIntervalMs = 0L

    /** True once the probe confirmed the press moves the media stream, i.e.
     *  the driver may move [STEPS_PER_UPDATE] steps per update through
     *  `setStreamVolume` instead of one step per `adjustSuggestedStreamVolume`. */
    @Volatile
    private var absoluteMode = false

    /** Absolute updates emitted in this session (main thread only). */
    private var updatesEmitted = 0

    /** Volume the absolute ramp counts from: the value read *after* the first
     *  native tick, so it already includes that step (main thread only). */
    private var absoluteBase = 0

    /** Last absolute index requested, to detect that the ramp is pinned at an
     *  end of the range (main thread only). */
    private var lastTargetVolume = -1

    /** Reentry guard for the driver's own reflection invokes. */
    private var driving = false

    /** One driver step: apply one update and reschedule at the curve speed. */
    private val driverRunnable = object : Runnable {
        override fun run() {
            if (!driverActive) return
            val now = SystemClock.elapsedRealtime()
            if (now - lastTickTime > NATIVE_TICK_TIMEOUT_MS) {
                stopDriver("native heartbeat lost")
                return
            }
            if (!absoluteMode) probeAbsoluteMode()
            val tickStart = SystemClock.elapsedRealtime()
            if (absoluteMode) {
                applyAbsoluteUpdate()
            } else {
                invokeOriginalAdjust(sessionDirection, null)
            }
            sessionApplied++
            val workMs = SystemClock.elapsedRealtime() - tickStart
            val elapsedSec = (SystemClock.elapsedRealtime() - sessionStart) / 1000.0
            val speed = (V0_STEPS_PER_SEC + ACCEL_STEPS_PER_SEC2 * elapsedSec)
                .coerceAtMost(MAX_STEPS_PER_SEC)
            val intervalMs = updateIntervalMs(speed)
            driverIntervalMs = intervalMs
            // `invokeOriginalAdjust` runs the whole AudioService chain
            // synchronously (measured ~2.9 ms on this ROM, and it grows with
            // the tick rate). postDelayed would add that to every single
            // period and stretch the curve — e.g. 5 ms intended + 2.9 ms work =
            // 7.9 ms real, which is why a full sweep took 1.4 s instead of the
            // intended 0.93 s. Subtract it so the curve means what it says.
            driverHandler.postDelayed(this, (intervalMs - workMs).coerceAtLeast(1L))
        }
    }

    /**
     * Decides, once per session, whether the press acts on the media stream —
     * and therefore whether the driver may use absolute multi-step updates.
     *
     * The first native tick has already run (`chain.proceed()`), so if this
     * press moved the media stream it now reads a different value than
     * [sessionStartVolume]. The test is "changed in the press direction, by any
     * amount" rather than "by exactly one": a muted stream reports 0 while its
     * real index is preserved, so a press that unmutes it can move a long way
     * in one step. Anything that leaves the media stream untouched (ring /
     * notification press, already at a range end) keeps the legacy one-step
     * path, so the ramp never drives a stream it did not verify.
     */
    private fun probeAbsoluteMode() {
        if (setStreamVolumeMethod == null) return
        val current = readStreamVolume(audioService)
        if (current < 0 || sessionStartVolume < 0) return
        if ((current - sessionStartVolume) * sessionDirection <= 0) return
        absoluteMode = true
        absoluteBase = current
        updatesEmitted = 0
        lastTargetVolume = -1
        logger.debug("ramp: absolute mode (x$STEPS_PER_UPDATE) confirmed, music=$current")
    }

    /**
     * Moves the media stream to an absolute index on the curve, avoiding the
     * one-step ceiling of `adjustSuggestedStreamVolume`. Requests beyond the
     * range are clamped; once pinned at an end the driver switches to silent
     * ADJUST_SAME refreshes so the panel stays alive while the key is held.
     */
    private fun applyAbsoluteUpdate() {
        updatesEmitted++
        val max = if (sessionMaxVolume > 0) sessionMaxVolume else Int.MAX_VALUE
        val target = (absoluteBase + sessionDirection * (updatesEmitted * STEPS_PER_UPDATE))
            .coerceIn(0, max)
        if (target == lastTargetVolume) {
            // Pinned at 0 or max: keep the panel refreshed (a repeated
            // setStreamVolume with an unchanged index may early-return before
            // sendVolumeUpdate), but do not spam real changes.
            invokeOriginalAdjust(0, REFRESH_FLAGS)
            return
        }
        val method = setStreamVolumeMethod ?: return
        val service = audioService ?: return
        lastTargetVolume = target
        try {
            method.invoke(service, STREAM_MUSIC, target, updateFlags, updateCallingPackage)
        } catch (t: Throwable) {
            logger.warn("Volume ramp absolute update failed: $t")
        }
    }

    /** Update interval (ms) for a fine-step rate; one update moves
     *  [STEPS_PER_UPDATE] steps. */
    private fun updateIntervalMs(fineStepsPerSec: Double): Long =
        (1000.0 * STEPS_PER_UPDATE / fineStepsPerSec).toLong().coerceIn(MIN_TICK_MS, 1000L)

    /**
     * Uniform panel refresh between real steps: while the driver waits for
     * its next variable-interval step, emits ADJUST_SAME adjusts (index
     * unchanged, no broadcast/persist per VolumeStreamState.setIndex, no
     * sound with PLAY_SOUND stripped) at a fixed grid so the volume panel
     * receives a refresh event on every frame and the animation never
     * stalls between steps. postVolumeChanged is unconditional in
     * AudioService.sendVolumeUpdate — the official mechanism behind
     * ADJUST_SAME + FLAG_SHOW_UI "show panel without changing volume".
     */
    private val refreshRunnable = object : Runnable {
        override fun run() {
            if (!driverActive) return
            // Only fill the gaps: once real steps already arrive at least as
            // often as the grid, they are the refresh.
            if (driverIntervalMs > REFRESH_INTERVAL_MS) invokeOriginalAdjust(0, REFRESH_FLAGS)
            driverHandler.postDelayed(this, REFRESH_INTERVAL_MS)
        }
    }

    override fun handleSystemServerStarting(param: SystemServerStartingParam) {
        try {
            val audioServiceClass = param.classLoader.loadClass("com.android.server.audio.AudioService")
            val intT = Int::class.javaPrimitiveType
            val adjust = findMethod(
                audioServiceClass,
                "adjustSuggestedStreamVolume",
                intT, intT, intT,
                String::class.java, String::class.java,
                intT, intT,
                java.lang.Boolean.TYPE,
                intT
            )
            adjust.isAccessible = true
            adjustMethod = adjust
            hookWithId(adjust, HOOK_ID) { chain ->
                val direction = chain.args[0] as? Int
                val flags = chain.args[2] as? Int ?: 0
                if (direction == null || flags and FLAG_FROM_KEY == 0 || driving) {
                    chain.proceed()
                    return@hookWithId null
                }
                when (handleNativeKeyTick(direction, chain.thisObject, chain.args)) {
                    TICK_PROCEED -> chain.proceed()
                    TICK_DROP -> { /* driver owns the cadence from here */ }
                }
                return@hookWithId null
            }
            logger.info("Successfully hooked AudioService.adjustSuggestedStreamVolume")

            // Diagnostic: lets the stop log distinguish "the index moved but the
            // panel did not repaint" from "the ticks were swallowed".
            streamVolumeMethod = runCatching {
                findMethod(audioServiceClass, "getStreamVolume", intT).also { it.isAccessible = true }
            }.getOrNull()
            if (streamVolumeMethod == null) logger.warn("getStreamVolume not found; ramp log has no index delta")

            // Absolute multi-step updates: one setStreamVolume call can move any
            // number of steps, which is what keeps the update rate (and thus the
            // panel) inside its budget.
            streamMaxVolumeMethod = runCatching {
                findMethod(audioServiceClass, "getStreamMaxVolume", intT).also { it.isAccessible = true }
            }.getOrNull()
            setStreamVolumeMethod = runCatching {
                findMethod(audioServiceClass, "setStreamVolume", intT, intT, intT, String::class.java)
                    .also { it.isAccessible = true }
            }.getOrNull()
            if (setStreamVolumeMethod == null) {
                logger.warn("setStreamVolume(int,int,int,String) not found; " +
                    "ramp stays on one step per update (slow sweep)")
            }

            hookSuppressAdjustment(audioServiceClass, param.classLoader)
        } catch (t: Throwable) {
            logger.error("Failed to hook AudioService.adjustSuggestedStreamVolume", t)
        }
    }

    /**
     * Neutralises the framework's long-press suppression while the driver owns
     * the cadence.
     *
     * `AudioService$VolumeController.suppressAdjustment` forces `direction = 0`
     * (an ADJUST_SAME) for `mLongPressTimeout` ms after the panel is first
     * shown, and [`mVolumeControllerLongPressEnabled`] defaults to **true**, so
     * unless the volume controller opts out (HyperOS's SystemUI never does, and
     * neither does ZUI's) every adjustment inside that window is dropped. That
     * window is exactly the reported "panel appears -> frozen -> jumps": the
     * whole ramp fits inside it, so only the tail after it expires is ever
     * visible. Returning false for our own session removes the throttle and
     * leaves the driver as the only owner of the cadence; presses outside a
     * driver session keep stock behaviour.
     */
    private fun hookSuppressAdjustment(serviceClass: Class<*>, classLoader: ClassLoader) {
        try {
            val intT = Int::class.javaPrimitiveType
            val controllerClass = serviceClass.declaredClasses
                .firstOrNull { it.simpleName == "VolumeController" }
                ?: classLoader.loadClass("com.android.server.audio.AudioService\$VolumeController")
            val suppress = findMethod(
                controllerClass,
                "suppressAdjustment",
                intT, intT, java.lang.Boolean.TYPE
            )
            suppress.isAccessible = true
            hookWithId(suppress, HOOK_ID_SUPPRESS) { chain ->
                if (driverActive) false else chain.proceed()
            }
            logger.info("Ramp: long-press suppression bypass installed")
        } catch (t: Throwable) {
            logger.warn("Ramp: cannot bypass long-press suppression (${t.message}); " +
                "a held key may stay frozen until the suppression window expires")
        }
    }

    /**
     * Handles one native FLAG_FROM_KEY adjust tick.
     *
     * @return [TICK_PROCEED] to run the original call (first step of a press,
     *         release cue), [TICK_DROP] to swallow it (the driver owns the
     *         cadence from the second native tick onward).
     */
    @Synchronized
    private fun handleNativeKeyTick(
        direction: Int,
        service: Any?,
        args: List<Any?>
    ): Int {
        val now = SystemClock.elapsedRealtime()

        if (direction == 0) {
            // ADJUST_SAME: release cue (sound/vibrate), always passes.
            if (driverActive) stopDriver("release cue")
            return TICK_PROCEED
        }

        lastTickTime = now
        val newSession = !driverActive && (
            direction != sessionDirection ||
                sessionStart == 0L ||
                now - sessionStart > SESSION_GAP_MS
            )
        if (newSession) {
            sessionStart = now
            sessionApplied = 1
            sessionDirection = direction
            audioService = service
            argsTemplate = args.toTypedArray().copyOf()
            sessionStartVolume = readStreamVolume(service)
            sessionMaxVolume = readStreamMaxVolume(service)
            updateFlags = args.getOrNull(2) as? Int ?: 0
            updateCallingPackage = args.getOrNull(3) as? String ?: "android"
            // The absolute path is only entered once the probe confirms this
            // press moves the media stream — never assume it.
            absoluteMode = false
            updatesEmitted = 0
            absoluteBase = 0
            lastTargetVolume = -1
            driverActive = true
            // The first step of the press is this very tick; the hook lets it
            // run natively, then the driver takes over the cadence.
            val intervalMs = updateIntervalMs(V0_STEPS_PER_SEC)
            driverIntervalMs = intervalMs
            driverHandler.postDelayed(driverRunnable, intervalMs)
            driverHandler.postDelayed(refreshRunnable, REFRESH_INTERVAL_MS)
            logger.debug("ramp: session start direction=$direction first update in ${intervalMs}ms")
            return TICK_PROCEED
        }
        return TICK_DROP
    }

    private fun stopDriver(reason: String) {
        driverActive = false
        absoluteMode = false
        driverHandler.removeCallbacks(driverRunnable)
        driverHandler.removeCallbacks(refreshRunnable)
        val endVolume = readStreamVolume(audioService)
        logger.debug(
            "ramp: stop ($reason), ticks=$sessionApplied " +
                "mode=${if (updatesEmitted > 0) "absolute x$STEPS_PER_UPDATE" else "one-step"} in " +
                "${SystemClock.elapsedRealtime() - sessionStart}ms, " +
                "music $sessionStartVolume -> $endVolume"
        )
    }

    /**
     * Reads the media stream volume through the captured AudioService. Used by
     * [probeAbsoluteMode] (did the press actually move the stream?) and by the
     * [stopDriver] diagnostic, where `ticks` counts driver updates while the
     * volume delta says how many of them reached the index — which separates a
     * swallowed update from a panel that did not repaint.
     */
    private fun readStreamVolume(service: Any?): Int {
        val method = streamVolumeMethod ?: return -1
        val target = service ?: return -1
        return try {
            (method.invoke(target, STREAM_MUSIC) as? Int) ?: -1
        } catch (t: Throwable) {
            -1
        }
    }

    /** Reads the media stream's max index (steps), to clamp absolute targets. */
    private fun readStreamMaxVolume(service: Any?): Int {
        val method = streamMaxVolumeMethod ?: return -1
        val target = service ?: return -1
        return try {
            (method.invoke(target, STREAM_MUSIC) as? Int) ?: -1
        } catch (t: Throwable) {
            -1
        }
    }

    /**
     * Applies one adjust via the original method, bypassing this hook.
     * @param direction adjust direction (0 = ADJUST_SAME refresh)
     * @param flagsOverride when non-null, replaces the flags argument
     */
    private fun invokeOriginalAdjust(direction: Int, flagsOverride: Int?) {
        val method = adjustMethod ?: return
        val service = audioService ?: return
        val args = argsTemplate.copyOf()
        args[0] = direction
        if (flagsOverride != null) args[2] = flagsOverride
        driving = true
        try {
            method.invoke(service, *args)
        } catch (t: Throwable) {
            logger.warn("Volume ramp driver invoke failed: $t")
        } finally {
            driving = false
        }
    }

    companion object {
        private const val HOOK_ID = "volume_key_nonlinear_adjust_suggested"
        private const val HOOK_ID_SUPPRESS = "volume_ramp_suppress_adjustment"

        /** android.media.AudioManager.STREAM_MUSIC */
        private const val STREAM_MUSIC = 3

        /** Tick verdicts for [handleNativeKeyTick]. */
        const val TICK_PROCEED = 0
        const val TICK_DROP = 1

        /** android.media.AudioManager.FLAG_FROM_KEY */
        private const val FLAG_FROM_KEY = 0x1000

        // ---- 15-step ("stock feel") anchoring --------------------------------
        // The curve is defined in *stock* units and converted to fine steps, so
        // it keeps its feel if the fine step count changes. The stock reference
        // is the native ~50 ms MediaSessionService loop on a 15-step phone.

        /** Fine steps moved by one driver update. One `setStreamVolume` call
         *  can move any number of steps, and every update costs ~2.9 ms of
         *  synchronous AudioService work plus a binder + a panel repaint, so
         *  this is the knob that trades key-press granularity for sweep time:
         *  total time = FINE_STEPS / (STEPS_PER_UPDATE * updates/s).
         *  5 = 1/30 of the range per update (2x finer than stock's 1/15). */
        private const val STEPS_PER_UPDATE = 5

        /** Media steps installed by FineVolumeSteps; keep the two in sync. */
        private const val FINE_STEPS = 150.0

        /** Stock reference: media steps before fine volume was installed. */
        private const val STOCK_STEPS = 15.0

        /** Stock reference rate: the native ~50 ms loop = 20 ticks/s. */
        private const val STOCK_STEP_RATE = 20.0

        /** Fine steps per stock step (= 10 on the 150-step scale). */
        private const val FINE_PER_STOCK = FINE_STEPS / STOCK_STEPS

        /** Start rate in *updates* per second: 8/s = the first update lands
         *  125 ms after the press. */
        private const val V0_UPDATES_PER_SEC = 8.0

        /** Fraction of the range covered while accelerating; the rest runs at max. */
        private const val ACCEL_RANGE_FRACTION = 0.30

        /** Start rate (fine steps/s); 40 on the 150-step scale. */
        private const val V0_STEPS_PER_SEC = V0_UPDATES_PER_SEC * STEPS_PER_UPDATE

        /** Full-speed cap (fine steps/s): exactly the stock rate, so the tail
         *  never drags. 200 here = 20 stock-steps/s = 40 updates/s, which stays
         *  under the 1000/[MIN_TICK_MS] = 50 updates/s floor by construction. */
        private const val MAX_STEPS_PER_SEC = STOCK_STEP_RATE * FINE_PER_STOCK

        /**
         * Constant acceleration (fine steps/s²); 426.7 on the 150-step scale.
         *
         * Solved from "the cap is reached after [ACCEL_RANGE_FRACTION] of the
         * range": with t1 = 2·f·N/(v0+v1) and a = (v1−v0)/t1, this reduces to
         * a = (v1² − v0²) / (2·f·N). It puts the cap at 0.375 s / 9 updates and
         * the full sweep at ~0.90 s.
         */
        private const val ACCEL_STEPS_PER_SEC2 =
            ((MAX_STEPS_PER_SEC * MAX_STEPS_PER_SEC - V0_STEPS_PER_SEC * V0_STEPS_PER_SEC)
                / (2.0 * ACCEL_RANGE_FRACTION * FINE_STEPS))

        /** Lower bound of the driver update interval (ms) = 50 updates/s.
         *
         *  This is the panel's budget, not the CPU's: measured on this ROM,
         *  ~110 updates/s already saturates SystemUI's UI thread and it stops
         *  repainting mid-sweep, while 50/s renders cleanly. [MAX_STEPS_PER_SEC]
         *  is deliberately below this so the curve, not the floor, binds. */
        private const val MIN_TICK_MS = 20L

        /** Fixed grid for ADJUST_SAME panel refreshes between real steps. */
        private const val REFRESH_INTERVAL_MS = 50L

        /** Flags for ADJUST_SAME refreshes: FROM_KEY | SHOW_UI only — sound,
         *  vibrate and vendor bits stripped so the refresh is silent. */
        private const val REFRESH_FLAGS = FLAG_FROM_KEY or 0x1

        /** No native tick for this long while holding = key released. */
        private const val NATIVE_TICK_TIMEOUT_MS = 300L

        /** Session gap (ms) separating two presses. */
        private const val SESSION_GAP_MS = 600L
    }
}
