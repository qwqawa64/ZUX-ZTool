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
 *   0..TIER_1_MS after press start   -> pass every 4th  (slow, ~5 steps/s)
 *   TIER_1..TIER_2_MS (~3s)          -> pass every 2nd  (~10 steps/s)
 *   beyond TIER_2_MS                 -> pass all        (~20 steps/s, stock)
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
    private var sessionOrdinal = 0
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
            sessionOrdinal = 0
            sessionDirection = direction
        }
        sessionLastTime = now
        sessionOrdinal++

        val elapsed = now - sessionFirstTime
        val modulo = when {
            elapsed < TIER_1_MS -> TIER_1_MODULO
            elapsed < TIER_2_MS -> TIER_2_MODULO
            else -> 1
        }
        val suppress = sessionOrdinal % modulo != 0
        logger.debug(
            "adjust: direction=$direction ordinal=$sessionOrdinal elapsedMs=$elapsed " +
                "modulo=$modulo decision=${if (suppress) "SUPPRESS" else "pass"}"
        )
        return suppress
    }

    companion object {
        private const val HOOK_ID = "volume_key_nonlinear_adjust_suggested"

        /** android.media.AudioManager.FLAG_FROM_KEY */
        private const val FLAG_FROM_KEY = 0x1000

        // The native loop ticks ~50ms; keep the tier boundaries in wall time
        // so the curve does not depend on the tick rate.
        private const val TIER_1_MS = 1000L
        private const val TIER_2_MS = 3000L
        private const val TIER_1_MODULO = 4
        private const val TIER_2_MODULO = 2

        /** No adjust for this long = key released; next adjust starts a session. */
        private const val SESSION_GAP_MS = 600L
    }
}
