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
import kotlin.math.ceil
import kotlin.math.max

/**
 * Replaces the native volume-key repeat cadence with a self-scheduled,
 * accelerating ramp: the native loop's ticks are swallowed and the driver
 * applies its own updates on a curve, moving several steps per update through
 * `setStreamVolume` so the update rate stays inside what the panel can render.
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

    // Session state. The volatile fields cross threads (AudioService binder
    // thread -> main thread); the rest is main-thread only.
    private var sessionStart = 0L
    private var sessionApplied = 0
    private var sessionDirection = 0
    private var lastTickTime = 0L
    private var audioService: Any? = null
    private var argsTemplate: Array<Any?> = emptyArray()

    @Volatile
    private var driverActive = false

    @Volatile
    private var absoluteMode = false

    @Volatile
    private var sessionStartVolume = -1

    @Volatile
    private var driverIntervalMs = 0L

    @Volatile
    private var curve = Curve(DEFAULT_STEPS)

    private var sessionMaxVolume = -1
    private var updateFlags = 0
    private var updateCallingPackage = "android"
    private var updatesEmitted = 0
    private var absoluteBase = 0
    private var lastTargetVolume = -1

    /** Reentry guard for the driver's own reflection invokes. */
    private var driving = false

    /**
     * Ramp shape for one session, derived from the affected stream's real step
     * count so the same formulas serve a stock 15-step scale and a fine one:
     * updating ~[TARGET_UPDATES_PER_SWEEP] times per sweep keeps the peak update
     * rate constant, and [vMax] keeps the tail at the native range rate.
     */
    private class Curve(steps: Int) {
        /** Steps moved per update. */
        val stepsPerUpdate: Int = max(1, ceil(steps / TARGET_UPDATES_PER_SWEEP).toInt())

        /** Start rate (steps/s) — the first update lands 1/[V0_UPDATES_PER_SEC] s in. */
        val v0: Double = V0_UPDATES_PER_SEC * stepsPerUpdate

        /** Tail rate (steps/s) — never below the native cadence. */
        val vMax: Double = STOCK_STEP_RATE * max(1.0, steps / STOCK_STEPS)

        /** Acceleration placing [ACCEL_RANGE_FRACTION] of the range before [vMax]. */
        val accel: Double = (vMax * vMax - v0 * v0) / (2.0 * ACCEL_RANGE_FRACTION * steps)

        fun intervalMs(speed: Double): Long =
            (1000.0 * stepsPerUpdate / speed).toLong().coerceIn(MIN_TICK_MS, 1000L)
    }

    /** One update: apply one step of the curve, then reschedule. */
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
            if (absoluteMode) applyAbsoluteUpdate() else invokeOriginalAdjust(sessionDirection, null)
            sessionApplied++
            // The invoke above runs the whole AudioService chain synchronously;
            // postDelayed would add that to every period, stretching the curve.
            val workMs = SystemClock.elapsedRealtime() - tickStart
            val elapsedSec = (SystemClock.elapsedRealtime() - sessionStart) / 1000.0
            val speed = (curve.v0 + curve.accel * elapsedSec).coerceAtMost(curve.vMax)
            driverIntervalMs = curve.intervalMs(speed)
            driverHandler.postDelayed(this, (driverIntervalMs - workMs).coerceAtLeast(1L))
        }
    }

    /**
     * Keeps the panel alive between real updates: ADJUST_SAME with
     * FLAG_SHOW_UI changes nothing but still reaches `sendVolumeUpdate` ->
     * `postVolumeChanged`, which repaints the panel with the current index.
     */
    private val refreshRunnable = object : Runnable {
        override fun run() {
            if (!driverActive) return
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
                    TICK_DROP -> { /* the driver owns the cadence from here */ }
                }
                return@hookWithId null
            }
            logger.info("Successfully hooked AudioService.adjustSuggestedStreamVolume")

            streamVolumeMethod = runCatching {
                findMethod(audioServiceClass, "getStreamVolume", intT).also { it.isAccessible = true }
            }.getOrNull()
            streamMaxVolumeMethod = runCatching {
                findMethod(audioServiceClass, "getStreamMaxVolume", intT).also { it.isAccessible = true }
            }.getOrNull()
            setStreamVolumeMethod = runCatching {
                findMethod(audioServiceClass, "setStreamVolume", intT, intT, intT, String::class.java)
                    .also { it.isAccessible = true }
            }.getOrNull()
            if (setStreamVolumeMethod == null) {
                logger.warn("setStreamVolume(int,int,int,String) not found; " +
                    "the ramp stays on one step per update and sweeps slowly")
            }

            hookSuppressAdjustment(audioServiceClass, param.classLoader)
        } catch (t: Throwable) {
            logger.error("Failed to hook AudioService.adjustSuggestedStreamVolume", t)
        }
    }

    /**
     * `VolumeController.suppressAdjustment` forces `direction = 0` for
     * `mLongPressTimeout` ms after the panel is first shown, and its enable flag
     * defaults to true and is never cleared by SystemUI — so every adjust inside
     * that window is dropped, which swallows the whole ramp. Return false while
     * the driver owns the cadence; presses outside a session stay stock.
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
            logger.warn("Ramp: cannot bypass long-press suppression (${t.message})")
        }
    }

    /**
     * Handles one native FLAG_FROM_KEY adjust tick.
     *
     * @return [TICK_PROCEED] to run the original call, [TICK_DROP] to swallow it.
     */
    @Synchronized
    private fun handleNativeKeyTick(
        direction: Int,
        service: Any?,
        args: List<Any?>
    ): Int {
        val now = SystemClock.elapsedRealtime()

        if (direction == 0) {
            // ADJUST_SAME: the release cue.
            if (driverActive) stopDriver("release cue")
            return TICK_PROCEED
        }

        lastTickTime = now
        // A tick starts a session unless the driver is running in the same
        // direction, in which case it belongs to the hold the driver already
        // owns. driverActive is cleared by the release cue, so a press after a
        // release always starts fresh — deciding this from a time gap instead
        // swallows every second quick tap.
        val newSession = !driverActive || direction != sessionDirection
        if (newSession) {
            sessionStart = now
            sessionApplied = 1
            sessionDirection = direction
            audioService = service
            argsTemplate = args.toTypedArray().copyOf()
            sessionStartVolume = readStreamVolume(service)
            sessionMaxVolume = readStreamMaxVolume(service)
            curve = Curve(if (sessionMaxVolume > 0) sessionMaxVolume else DEFAULT_STEPS)
            updateFlags = args.getOrNull(2) as? Int ?: 0
            updateCallingPackage = args.getOrNull(3) as? String ?: "android"
            absoluteMode = false
            updatesEmitted = 0
            absoluteBase = 0
            lastTargetVolume = -1
            driverActive = true
            // This tick is the first step of the press; let it through, then the
            // driver takes over the cadence.
            val intervalMs = curve.intervalMs(curve.v0)
            driverIntervalMs = intervalMs
            driverHandler.postDelayed(driverRunnable, intervalMs)
            driverHandler.postDelayed(refreshRunnable, REFRESH_INTERVAL_MS)
            logger.debug("ramp: session start direction=$direction steps=${sessionMaxVolume} " +
                "perUpdate=${curve.stepsPerUpdate} first update in ${intervalMs}ms")
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
            "ramp: stop ($reason), updates=$sessionApplied " +
                "mode=${if (updatesEmitted > 0) "absolute x${curve.stepsPerUpdate}" else "one-step"} in " +
                "${SystemClock.elapsedRealtime() - sessionStart}ms, " +
                "music $sessionStartVolume -> $endVolume"
        )
    }

    /**
     * Applies absolute multi-step updates once the press is known to act on the
     * media stream.
     *
     * The first tick has already run, so a press that moved the stream reads
     * differently than [sessionStartVolume]. The test is "changed in the press
     * direction by any amount", not "by one": a muted stream reports 0 while its
     * real index is preserved, so unmuting can move a long way in one step.
     */
    private fun probeAbsoluteMode() {
        if (setStreamVolumeMethod == null || curve.stepsPerUpdate <= 1) return
        val current = readStreamVolume(audioService)
        if (current < 0 || sessionStartVolume < 0) return
        if ((current - sessionStartVolume) * sessionDirection <= 0) return
        absoluteMode = true
        absoluteBase = current
        updatesEmitted = 0
        lastTargetVolume = -1
        logger.debug("ramp: absolute mode x${curve.stepsPerUpdate}, base=$current")
    }

    /** Moves the media stream to the curve's absolute index, clamped to range. */
    private fun applyAbsoluteUpdate() {
        updatesEmitted++
        val maxIndex = if (sessionMaxVolume > 0) sessionMaxVolume else Int.MAX_VALUE
        val target = (absoluteBase + sessionDirection * (updatesEmitted * curve.stepsPerUpdate))
            .coerceIn(0, maxIndex)
        if (target == lastTargetVolume) {
            // Pinned at an end: a repeated setStreamVolume with an unchanged
            // index may early-return before sendVolumeUpdate, so refresh instead.
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

    /** Media stream volume, used by [probeAbsoluteMode] and the stop log. */
    private fun readStreamVolume(service: Any?): Int {
        val method = streamVolumeMethod ?: return -1
        val target = service ?: return -1
        return try {
            (method.invoke(target, STREAM_MUSIC) as? Int) ?: -1
        } catch (t: Throwable) {
            -1
        }
    }

    /** Media stream max index (steps), the ramp curve's scale. */
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
     * Applies one adjust through the original method, bypassing this hook.
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

        /** android.media.AudioManager.STREAM_MUSIC / FLAG_FROM_KEY */
        private const val STREAM_MUSIC = 3
        private const val FLAG_FROM_KEY = 0x1000

        private const val TICK_PROCEED = 0
        private const val TICK_DROP = 1

        /** Stock reference: 15 media steps driven by the native ~50 ms loop. */
        private const val STOCK_STEPS = 15.0
        private const val STOCK_STEP_RATE = 20.0

        /** Curve shape, independent of the scale. */
        private const val TARGET_UPDATES_PER_SWEEP = 30.0
        private const val V0_UPDATES_PER_SEC = 8.0
        private const val ACCEL_RANGE_FRACTION = 0.30

        /** Scale assumed when getStreamMaxVolume is unavailable. */
        private const val DEFAULT_STEPS = 150

        /** Update-rate ceiling: the panel stops repainting somewhere above this. */
        private const val MIN_TICK_MS = 20L

        /** Silent refresh grid; only used while no real update is due. */
        private const val REFRESH_INTERVAL_MS = 50L
        private const val REFRESH_FLAGS = FLAG_FROM_KEY or 0x1

        /** No native tick for this long while holding = key released. */
        private const val NATIVE_TICK_TIMEOUT_MS = 300L
    }
}
