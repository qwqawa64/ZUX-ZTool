package com.qimian233.ztool.hook.modules.systemframework

import android.annotation.SuppressLint
import android.os.SystemClock
import com.qimian233.ztool.data.keys.PreferenceKeys
import com.qimian233.ztool.data.keys.ScopeKeys
import com.qimian233.ztool.hook.base.SystemHookModule
import io.github.libxposed.api.XposedModuleInterface.SystemServerStartingParam

/**
 * Non-linear volume key ramping hook module.
 *
 * Stock behavior (traced live on this ZUI ROM, 2026-10): holding a volume key
 * is NOT driven by input-pipeline key repeats — PhoneWindowManager only sees
 * the initial ACTION_DOWN and the final ACTION_UP. Instead a single key event
 * arms a ~50ms Handler loop in MediaSessionService
 * (SessionManagerImpl$3.run) that repeatedly calls
 * AudioService.adjustSuggestedStreamVolume(±1) until key release, producing
 * the constant ramp rate.
 *
 * This hook intercepts the 9-arg AudioService.adjustSuggestedStreamVolume
 * before it runs and drops part of the key-driven adjusts, producing a
 * slow-start-then-accelerate ramp:
 *
 *   speed(t) = V0_STEPS_PER_SEC + ACCEL_STEPS_PER_SEC2 * t   (steps/s)
 *
 * The speed grows linearly the whole time the key is held — no discrete
 * tiers. A loop tick (~50ms) is passed only while the count of already-passed
 * adjusts is below the integrated budget V0*t + ACCEL*t^2/2. With
 * V0=3, ACCEL=4 the speed starts at 3 steps/s and reaches the native
 * ~20 steps/s after ~4.25s of holding; past that every tick passes.
 *
 * Key-driven adjusts are identified by FLAG_FROM_KEY (0x1000) in the flags
 * argument, so slider drags, setStreamVolume and other programmatic adjusts
 * are untouched. An adjust session resets when the direction changes or when
 * adjusts stop for more than [SESSION_GAP_MS] (key released). ADJUST_SAME
 * (direction 0, the key-release sound cue) always passes. Pairs well with
 * [FineVolumeSteps] (150-step scale makes the early slow tier finer).
 */
@SuppressLint("PrivateApi", "DiscouragedPrivateApi")
class VolumeKeyNonlinearRamp : SystemHookModule() {

    override fun getModuleName(): String = PreferenceKeys.VOLUME_KEY_NONLINEAR.name

    override fun getTargetPackages(): Array<String> = arrayOf(ScopeKeys.ANDROID_SYSTEM.packageName)

    /** Monotonic clock state for the current key-hold adjust session. */
    private var sessionFirstTime = 0L
    private var sessionLastTime = 0L
    private var sessionPassed = 0
    private var sessionDirection = 0

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
            hookWithId(adjust, HOOK_ID) { chain ->
                val direction = chain.args[0] as? Int
                val flags = chain.args[2] as? Int ?: 0
                if (direction != null &&
                    flags and FLAG_FROM_KEY != 0 &&
                    shouldSuppressAdjust(direction)
                ) {
                    // Drop this key-driven adjust: return without proceeding.
                    return@hookWithId null
                }
                chain.proceed()
            }
            logger.info("Successfully hooked AudioService.adjustSuggestedStreamVolume")
        } catch (t: Throwable) {
            logger.error("Failed to hook AudioService.adjustSuggestedStreamVolume", t)
        }
    }

    /**
     * Decides whether this key-driven adjust is swallowed by the non-linear
     * ramp. Also advances the session bookkeeping for every key-driven call.
     */
    @Synchronized
    private fun shouldSuppressAdjust(direction: Int): Boolean {
        val now = SystemClock.elapsedRealtime()

        // ADJUST_SAME: release cue (sound/vibrate), always pass.
        if (direction == 0) {
            logger.debug("adjust: direction=SAME elapsedMs=${now - sessionFirstTime} decision=pass")
            return false
        }

        val newSession = direction != sessionDirection ||
            sessionLastTime == 0L ||
            now - sessionLastTime > SESSION_GAP_MS
        if (newSession) {
            sessionFirstTime = now
            sessionPassed = 0
            sessionDirection = direction
        }
        sessionLastTime = now

        val elapsedSec = (now - sessionFirstTime) / 1000.0
        // Integrated budget of allowed adjusts since press start; the loop
        // cannot pass more ticks than it receives, so no explicit cap is needed.
        val budget = V0_STEPS_PER_SEC * elapsedSec + ACCEL_STEPS_PER_SEC2 * elapsedSec * elapsedSec / 2.0
        val suppress = budget < sessionPassed + 1
        if (!suppress) sessionPassed++
        logger.debug(
            "adjust: direction=$direction passed=$sessionPassed elapsedMs=${(elapsedSec * 1000).toInt()} " +
                "budget=${"%.2f".format(budget)} speed=${"%.1f".format(V0_STEPS_PER_SEC + ACCEL_STEPS_PER_SEC2 * elapsedSec)}/s " +
                "decision=${if (suppress) "SUPPRESS" else "pass"}"
        )
        return suppress
    }

    companion object {
        private const val HOOK_ID = "volume_key_nonlinear_adjust_suggested"

        /** android.media.AudioManager.FLAG_FROM_KEY */
        private const val FLAG_FROM_KEY = 0x1000

        /** Ramp start speed (steps/s) at the moment the key goes down. */
        private const val V0_STEPS_PER_SEC = 3.0

        /** Constant acceleration of the ramp speed (steps/s^2): speed grows
         *  linearly while held, reaching the native ~20 steps/s after ~4.25s. */
        private const val ACCEL_STEPS_PER_SEC2 = 4.0

        /** No adjust for this long = key released; next adjust starts a session. */
        private const val SESSION_GAP_MS = 600L
    }
}
