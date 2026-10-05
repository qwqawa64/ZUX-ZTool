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
 * ## The curve is anchored to the stock 15-step feel
 *
 * The reference is the stock behaviour traced above: **15** media steps at a
 * constant **20 stock-steps/s** (the native ~50 ms loop), i.e. 20/15 of the
 * range per second and a full sweep in **0.75 s**. The curve is written in
 * those stock units and converted to fine steps, so it stays correct if the
 * fine step count changes:
 *
 * | stock unit                          | stock-steps | fine steps (150-scale) |
 * |-------------------------------------|-------------|------------------------|
 * | start rate V0                       | 2/s         | 20/s                   |
 * | cap MAX (= stock rate, tail parity) | 20/s        | 200/s                  |
 * | acceleration (cap at 30% of range)  | 44/s²       | 440/s²                 |
 *
 * Resulting feel versus stock: the first *stock* step (1/15 of the range)
 * arrives after 0.17 s instead of 0.05 s — about 3.4x finer control at the
 * start.
 *
 * ## Why the sweep is ~3 s and not the ~0.93 s the shape implies
 *
 * The shape above wants 200 fine steps/s at the tail, but that is **not
 * renderable** on this ROM: one tick = one AudioService adjust ≈ 2.9 ms of
 * synchronous work plus a binder into SystemUI, so ~110 ticks/s already
 * saturates the volume panel's UI thread and it stops repainting mid-sweep
 * ("the head animates, then it freezes and jumps"). Measured from the
 * `ramp: stop …` log on 2026-10-06 (150-step sweeps of 1.44-1.63 s at
 * ~110 ticks/s, while the curve intended 0.93 s).
 *
 * So [MIN_TICK_MS] is the real cap, and the effective curve is
 * **20 → 50 fine steps/s** (0.5 → 5 stock-steps/s), a full sweep in **~3.0 s**,
 * with 150 updates and every update a real volume change the panel can draw.
 *
 * Getting stock's 0.75-0.93 s *and* a shaped ramp needs fewer updates, which
 * needs more than one step per update — impossible through
 * `adjustSuggestedStreamVolume` (fixed at one user step per call), but
 * possible through `AudioService.setStreamVolume(stream, absoluteIndex, …)`.
 * That is exactly the trick behind HyperOS's `getMusicVolumeStep` returning
 * `maxVolume / 15` (10 steps per press) — see
 * docs/research/hyperos-volume/README.md §7.1.
 */
@SuppressLint("PrivateApi", "DiscouragedPrivateApi")
class VolumeKeyNonlinearRamp : SystemHookModule() {

    override fun getModuleName(): String = PreferenceKeys.VOLUME_KEY_NONLINEAR.name

    override fun getTargetPackages(): Array<String> = arrayOf(ScopeKeys.ANDROID_SYSTEM.packageName)

    private var adjustMethod: Method? = null
    private var streamVolumeMethod: Method? = null
    private val driverHandler = Handler(Looper.getMainLooper())

    // Session state. `driverActive` / `sessionStartVolume` / `driverIntervalMs`
    // are written from the AudioService (binder) thread and read from the main
    // thread, so they are volatile; the rest is main-thread only.
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
     *  move" diagnostic in [stopDriver]. */
    @Volatile
    private var sessionStartVolume = -1

    /** Interval the driver last scheduled, so the ADJUST_SAME refresh grid can
     *  be skipped once real steps already arrive at least that often. */
    @Volatile
    private var driverIntervalMs = 0L

    /** Reentry guard for the driver's own reflection invokes. */
    private var driving = false

    /** One driver step: apply one adjust and reschedule at the curve speed. */
    private val driverRunnable = object : Runnable {
        override fun run() {
            if (!driverActive) return
            val now = SystemClock.elapsedRealtime()
            if (now - lastTickTime > NATIVE_TICK_TIMEOUT_MS) {
                stopDriver("native heartbeat lost")
                return
            }
            val tickStart = SystemClock.elapsedRealtime()
            invokeOriginalAdjust(sessionDirection, null)
            sessionApplied++
            val workMs = SystemClock.elapsedRealtime() - tickStart
            val elapsedSec = (SystemClock.elapsedRealtime() - sessionStart) / 1000.0
            val speed = (V0_STEPS_PER_SEC + ACCEL_STEPS_PER_SEC2 * elapsedSec)
                .coerceAtMost(MAX_STEPS_PER_SEC)
            val intervalMs = (1000.0 / speed).toLong().coerceIn(MIN_TICK_MS, 1000L)
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
            driverActive = true
            // The first step of the press is this very tick; the hook lets it
            // run natively, then the driver takes over the cadence.
            val intervalMs = (1000.0 / V0_STEPS_PER_SEC).toLong().coerceIn(MIN_TICK_MS, 1000L)
            driverIntervalMs = intervalMs
            driverHandler.postDelayed(driverRunnable, intervalMs)
            driverHandler.postDelayed(refreshRunnable, REFRESH_INTERVAL_MS)
            logger.debug("ramp: session start direction=$direction speed0=$V0_STEPS_PER_SEC/s")
            return TICK_PROCEED
        }
        return TICK_DROP
    }

    private fun stopDriver(reason: String) {
        driverActive = false
        driverHandler.removeCallbacks(driverRunnable)
        driverHandler.removeCallbacks(refreshRunnable)
        val endVolume = readStreamVolume(audioService)
        logger.debug(
            "ramp: stop ($reason), applied=$sessionApplied steps in " +
                "${SystemClock.elapsedRealtime() - sessionStart}ms, " +
                "music $sessionStartVolume -> $endVolume"
        )
    }

    /**
     * Reads the media stream volume through the captured AudioService, purely
     * for the [stopDriver] diagnostic. `applied` counts driver ticks that ran;
     * the volume delta says how many of them actually reached the index, which
     * is what separates a swallowed tick from a panel that did not repaint.
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

        /** Media steps installed by FineVolumeSteps; keep the two in sync. */
        private const val FINE_STEPS = 150.0

        /** Stock reference: media steps before fine volume was installed. */
        private const val STOCK_STEPS = 15.0

        /** Stock reference rate: the native ~50 ms loop = 20 ticks/s. */
        private const val STOCK_STEP_RATE = 20.0

        /** Fine steps per stock step (= 10 on the 150-step scale). */
        private const val FINE_PER_STOCK = FINE_STEPS / STOCK_STEPS

        /** Start rate: stock/10, i.e. ten times finer at the moment of press. */
        private const val V0_STOCK_RATE = STOCK_STEP_RATE / 10.0

        /** Top rate: exactly stock, so the tail never feels slower than stock. */
        private const val MAX_STOCK_RATE = STOCK_STEP_RATE

        /** Fraction of the range covered while accelerating; the rest runs at max. */
        private const val ACCEL_RANGE_FRACTION = 0.30

        /** Ramp start speed (fine steps/s); 20 on the 150-step scale. */
        private const val V0_STEPS_PER_SEC = V0_STOCK_RATE * FINE_PER_STOCK

        /** Full-speed cap (fine steps/s); 200 on the 150-step scale. */
        private const val MAX_STEPS_PER_SEC = MAX_STOCK_RATE * FINE_PER_STOCK

        /**
         * Constant acceleration (fine steps/s²); 440 on the 150-step scale.
         *
         * Solved from "the cap is reached after [ACCEL_RANGE_FRACTION] of the
         * range": with t1 = 2·f·N/(v0+v1) and a = (v1−v0)/t1, this reduces to
         * a = (v1² − v0²) / (2·f·N).
         */
        private const val ACCEL_STEPS_PER_SEC2 =
            ((MAX_STOCK_RATE * MAX_STOCK_RATE - V0_STOCK_RATE * V0_STOCK_RATE)
                / (2.0 * ACCEL_RANGE_FRACTION * STOCK_STEPS)) * FINE_PER_STOCK

        /** Lower bound of the driver tick interval (ms).
         *
         *  This is the binding constraint, not [MAX_STEPS_PER_SEC]: the volume
         *  panel can only render ~60 updates/s, and each tick costs ~2.9 ms of
         *  synchronous AudioService work plus a binder into SystemUI, so a
         *  driver that emits faster than this saturates the panel's UI thread
         *  and it stops repainting mid-sweep ("frozen, then it jumps"). 20 ms
         *  caps the driver at 50 updates/s — below the frame rate, above the
         *  native 20/s loop. Raise it towards 30-33 ms if the panel still
         *  stutters; the trade-off is a proportionally longer sweep. */
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
