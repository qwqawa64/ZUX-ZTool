package com.qimian233.ztool.hook.modules.systemframework

import android.annotation.SuppressLint
import android.content.Context
import com.qimian233.ztool.data.keys.PreferenceKeys
import com.qimian233.ztool.data.keys.ScopeKeys
import com.qimian233.ztool.hook.base.SystemHookModule
import io.github.libxposed.api.XposedModuleInterface.SystemServerStartingParam

/**
 * Re-enables DisplayPowerController's color fade screen on/off animation, with a
 * customizable animation duration via [PreferenceKeys.SCREEN_ON_OFF_ANIMATION_MS].
 * See docs/research/screen-on-off-animation.md.
 */
@SuppressLint("PrivateApi")
class ForceScreenOnOffAnimation : SystemHookModule() {

    override fun getModuleName(): String = PreferenceKeys.FORCE_SCREEN_ON_OFF_ANIMATION.name

    override fun getTargetPackages(): Array<String> = arrayOf(ScopeKeys.SYSTEM_SERVER.packageName)

    override fun handleSystemServerStarting(param: SystemServerStartingParam) {
        val classLoader = param.classLoader
        try {
            logger.info("Executing hook for DisplayPowerController screen on/off animation...")
            hookDisplayPowerControllerAnimation(classLoader)
        } catch (e: Exception) {
            logger.error("Failed to hook DisplayPowerController: ", e)
        }
    }

    private fun hookDisplayPowerControllerAnimation(classLoader: ClassLoader) {
        try {
            val animateMethod = classLoader.loadClass(DISPLAY_POWER_CONTROLLER)
                .getDeclaredMethod(
                    "animateScreenStateChange",
                    Int::class.javaPrimitiveType,
                    Int::class.javaPrimitiveType,
                    Boolean::class.javaPrimitiveType,
                    Boolean::class.javaPrimitiveType,
                    Boolean::class.javaPrimitiveType
                )
            hookWithId(animateMethod, "animate_screen_state") { chain ->
                val controller = chain.thisObject
                applyAnimationDurations(controller)
                if (chain.getArg(0) as Int != DISPLAY_STATE_ON) {
                    return@hookWithId chain.proceed()
                }
                val powerState = fieldValue(controller, "mPowerState")
                val onAnimator = fieldValue(controller, "mColorFadeOnAnimator")
                val colorFadeLevel = powerState?.let { runCatching { findMethod(it.javaClass, "getColorFadeLevel").invoke(it) }.getOrNull() } as? Float ?: 1.0f
                val fadeWasLive = fieldValue(powerState, "mColorFadePrepared") as? Boolean == true
                chain.proceed()
                // The host dismisses the prepared fade on most screen-on paths and does so even
                // while its animator runs, so the fade has to be rebuilt after the fact.
                if (fadeWasLive && colorFadeLevel < 1.0f && powerState != null && onAnimator != null
                    && fieldValue(powerState, "mColorFadePrepared") as? Boolean != true
                ) {
                    rebuildScreenOnColorFade(controller, powerState, onAnimator, colorFadeLevel)
                }
                null
            }
        } catch (e: Exception) {
            logger.error("Failed to hook animateScreenStateChange", e)
        }
    }

    /**
     * Applies the configured durations to both color fade animators.
     *
     * Both animators are started only from `animateScreenStateChange`, so applying the
     * durations here makes a preference change live for screen off as well; `initialize`
     * runs only once per DisplayPowerController and cannot do that.
     */
    private fun applyAnimationDurations(controller: Any) {
        updateAnimationDurationFromPrefs()
        setAnimatorDuration(controller, "mColorFadeOnAnimator", SCREEN_ON_ANIMATION_DURATION_MS)
        setAnimatorDuration(controller, "mColorFadeOffAnimator", SCREEN_OFF_ANIMATION_DURATION_MS)
    }

    private fun setAnimatorDuration(controller: Any, name: String, durationMs: Long) {
        val animator = fieldValue(controller, name) ?: return
        // A running animator derives its progress from mDuration on every frame, so re-timing
        // it mid-flight would make the fade jump.
        val started = animator.let {
            runCatching { findMethod(it.javaClass, "isStarted").invoke(it) }.getOrNull()
        } as? Boolean ?: false
        if (started) return
        runCatching {
            findMethod(animator.javaClass, "setDuration", Long::class.javaPrimitiveType)
                .invoke(animator, durationMs)
        }.onFailure { logger.error("Failed to set the duration of " + name, it) }
    }

    /**
     * Rebuilds the color fade the host dismissed during a screen-on state change.
     * Runs in the same handler message as that change, so the panel stays black.
     *
     * @return true when the fade was rebuilt and started again.
     */
    private fun rebuildScreenOnColorFade(
        controller: Any,
        powerState: Any,
        onAnimator: Any,
        colorFadeLevel: Float
    ): Boolean {
        if (powerState.let { runCatching { findMethod(it.javaClass, "getScreenState").invoke(it) }.getOrNull() } as? Int != DISPLAY_STATE_ON) {
            logger.debug("Screen-on color fade was dropped without the display turning on")
            return false
        }
        val prepared = runCatching {
            findMethod(
                powerState.javaClass, "prepareColorFade",
                Context::class.java, Int::class.javaPrimitiveType
            ).invoke(powerState, fieldValue(controller, "mContext"), COLOR_FADE_MODE_FADE)
        }.getOrNull() as? Boolean ?: false
        if (!prepared) {
            logger.warn("Could not rebuild the screen-on color fade")
            return false
        }
        runCatching {
            findMethod(powerState.javaClass, "setColorFadeLevel", Float::class.javaPrimitiveType)
                .invoke(powerState, colorFadeLevel)
        }
        return try {
            // The animator may still be running here, in which case the durations applied at
            // hook entry were skipped; start() restarts the timeline, so re-time it now.
            findMethod(onAnimator.javaClass, "setDuration", Long::class.javaPrimitiveType)
                .invoke(onAnimator, SCREEN_ON_ANIMATION_DURATION_MS)
            findMethod(onAnimator.javaClass, "setFloatValues", FloatArray::class.java)
                .invoke(onAnimator, floatArrayOf(colorFadeLevel, 1.0f))
            findMethod(onAnimator.javaClass, "start").invoke(onAnimator)
            logger.debug("Rebuilt the screen-on color fade at level $colorFadeLevel")
            true
        } catch (t: Throwable) {
            logger.error("Failed to start the screen-on color fade: ", t)
            false
        }
    }

    private fun fieldValue(target: Any?, name: String): Any? =
        target?.let { runCatching { findField(it.javaClass, name).get(it) }.getOrNull() }

    private fun updateAnimationDurationFromPrefs() {
        try {
            val duration = remotePreferences.getInt(
                PreferenceKeys.SCREEN_ON_OFF_ANIMATION_MS.name,
                PreferenceKeys.SCREEN_ON_OFF_ANIMATION_MS.default
            )
            SCREEN_ON_ANIMATION_DURATION_MS = duration.toLong()
            SCREEN_OFF_ANIMATION_DURATION_MS = duration.toLong()
        } catch (_: Throwable) {
            SCREEN_ON_ANIMATION_DURATION_MS = DEFAULT_ANIMATION_DURATION_MS
            SCREEN_OFF_ANIMATION_DURATION_MS = DEFAULT_ANIMATION_DURATION_MS
        }
    }

    companion object {

        private const val DISPLAY_POWER_CONTROLLER =
            "com.android.server.display.DisplayPowerController"
        private const val COLOR_FADE_MODE_FADE = 2
        private const val DISPLAY_STATE_ON = 2
        private const val DEFAULT_ANIMATION_DURATION_MS = 400L
        private var SCREEN_ON_ANIMATION_DURATION_MS = DEFAULT_ANIMATION_DURATION_MS
        private var SCREEN_OFF_ANIMATION_DURATION_MS = DEFAULT_ANIMATION_DURATION_MS
    }
}
