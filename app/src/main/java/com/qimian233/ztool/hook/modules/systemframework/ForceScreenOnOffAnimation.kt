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
            hookDisplayPowerControllerInitialize(classLoader)
            hookDisplayPowerControllerScreenOnAnimation(classLoader)
        } catch (e: Exception) {
            logger.error("Failed to hook DisplayPowerController: ", e)
        }
    }

    private fun hookDisplayPowerControllerInitialize(classLoader: ClassLoader) {
        try {
            val initializeMethod = classLoader.loadClass(DISPLAY_POWER_CONTROLLER)
                .getDeclaredMethod("initialize", Int::class.javaPrimitiveType)
            hookWithId(initializeMethod, "display_power_init") { chain ->
                val result = chain.proceed()
                configureColorFadeAnimators(chain.thisObject)
                result
            }
        } catch (e: Exception) {
            logger.error("Failed to hook DisplayPowerController.initialize", e)
        }
    }

    private fun configureColorFadeAnimators(controller: Any) {
        val onAnimator = findField(controller.javaClass, "mColorFadeOnAnimator").get(controller)
        val offAnimator = findField(controller.javaClass, "mColorFadeOffAnimator").get(controller)
        try {
            updateAnimationDurationFromPrefs()
            if (onAnimator != null) {
                findMethod(onAnimator.javaClass, "setDuration", Long::class.javaPrimitiveType)
                    .invoke(onAnimator, SCREEN_ON_ANIMATION_DURATION_MS)
            }
            if (offAnimator != null) {
                findMethod(offAnimator.javaClass, "setDuration", Long::class.javaPrimitiveType)
                    .invoke(offAnimator, SCREEN_OFF_ANIMATION_DURATION_MS)
            }
            logger.debug("Configured color fade animator durations: on="
                + SCREEN_ON_ANIMATION_DURATION_MS
                + ", off=" + SCREEN_OFF_ANIMATION_DURATION_MS)
        } catch (t: Throwable) {
            logger.error("Failed to configure color fade animator durations: ", t)
        }
    }

    private fun hookDisplayPowerControllerScreenOnAnimation(classLoader: ClassLoader) {
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
            updateAnimationDurationFromPrefs()
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
