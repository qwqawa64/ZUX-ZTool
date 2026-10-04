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
 *
 * Works against any fine-volume step count; with [FineVolumeSteps] at 150
 * steps and V0=8/ACCEL=16/MAX=80, the ramp reaches full speed after ~4.5s.
 */
@SuppressLint("PrivateApi", "DiscouragedPrivateApi")
class VolumeKeyNonlinearRamp : SystemHookModule() {

    override fun getModuleName(): String = PreferenceKeys.VOLUME_KEY_NONLINEAR.name

    override fun getTargetPackages(): Array<String> = arrayOf(ScopeKeys.ANDROID_SYSTEM.packageName)

    private var adjustMethod: Method? = null
    private val driverHandler = Handler(Looper.getMainLooper())

    // Session state; all touched on the system server main thread only.
    private var sessionStart = 0L
    private var sessionApplied = 0
    private var sessionDirection = 0
    private var lastTickTime = 0L
    private var audioService: Any? = null
    private var argsTemplate: Array<Any?> = emptyArray()
    private var driverActive = false

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
            invokeOriginalAdjust(sessionDirection, null)
            sessionApplied++
            val elapsedSec = (SystemClock.elapsedRealtime() - sessionStart) / 1000.0
            val speed = (V0_STEPS_PER_SEC + ACCEL_STEPS_PER_SEC2 * elapsedSec)
                .coerceAtMost(MAX_STEPS_PER_SEC)
            val intervalMs = (1000.0 / speed).toLong().coerceIn(MIN_TICK_MS, 1000L)
            driverHandler.postDelayed(this, intervalMs)
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
            invokeOriginalAdjust(0, REFRESH_FLAGS)
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
        } catch (t: Throwable) {
            logger.error("Failed to hook AudioService.adjustSuggestedStreamVolume", t)
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
            driverActive = true
            // The first step of the press is this very tick; the hook lets it
            // run natively, then the driver takes over the cadence.
            val intervalMs = (1000.0 / V0_STEPS_PER_SEC).toLong().coerceIn(MIN_TICK_MS, 1000L)
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
        logger.debug(
            "ramp: stop ($reason), applied=$sessionApplied steps in " +
                "${SystemClock.elapsedRealtime() - sessionStart}ms"
        )
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

        /** Tick verdicts for [handleNativeKeyTick]. */
        const val TICK_PROCEED = 0
        const val TICK_DROP = 1

        /** android.media.AudioManager.FLAG_FROM_KEY */
        private const val FLAG_FROM_KEY = 0x1000

        /** Ramp start speed (steps/s) at the moment the key goes down. */
        private const val V0_STEPS_PER_SEC = 8.0

        /** Constant acceleration of the ramp speed (steps/s^2). */
        private const val ACCEL_STEPS_PER_SEC2 = 200.0

        /** Full-speed cap (steps/s); no longer bounded by the native loop. */
        private const val MAX_STEPS_PER_SEC = 150.0

        /** Lower bound of the driver tick interval (ms). NOTE: this clamps
         *  the achievable speed at 1000/MIN_TICK_MS steps/s (100/s at 10ms);
         *  each step also runs the full AudioService chain synchronously on
         *  the main thread, so the real ceiling may be lower — check the
         *  "ramp: stop ... applied=N in Xms" debug log to measure it. */
        private const val MIN_TICK_MS = 5L

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
