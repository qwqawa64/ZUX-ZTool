package com.qimian233.ztool.hook.modules.systemframework

import android.annotation.SuppressLint
import com.qimian233.ztool.data.keys.PreferenceKeys
import com.qimian233.ztool.data.keys.ScopeKeys
import com.qimian233.ztool.hook.base.SystemHookModule
import io.github.libxposed.api.XposedModuleInterface.SystemServerStartingParam
import java.lang.reflect.Constructor

/**
 * Forces DisplayPowerController's screen on/off Color Fade animation to be enabled,
 * with a customizable animation duration via [PreferenceKeys.SCREEN_ON_OFF_ANIMATION_MS].
 */
@SuppressLint("PrivateApi")
class ForceScreenOnOffAnimation : SystemHookModule() {

    override fun getModuleName(): String = PreferenceKeys.FORCE_SCREEN_ON_OFF_ANIMATION.name

    override fun getTargetPackages(): Array<String> = arrayOf(ScopeKeys.SYSTEM_SERVER.packageName)

    override fun handleSystemServerStarting(param: SystemServerStartingParam) {
        val classLoader = param.classLoader
        updateAnimationDurationFromPrefs()
        try {
            logger.info("Executing hook for DisplayPowerController screen on/off animation...")
            val isColorFadeEnabledMethod = classLoader.loadClass(DISPLAY_POWER_CONTROLLER_INJECTOR)
                .getDeclaredMethod("isColorFadeEnabled")
            hookWithId(isColorFadeEnabledMethod, "color_fade_enabled") { true }
            hookDisplayPowerControllerConstructor(classLoader)
            hookDisplayPowerControllerInitialize(classLoader)
            hookDisplayPowerControllerScreenOnAnimation(classLoader)
        } catch (e: Exception) {
            logger.error("Failed to hook DisplayPowerController: ", e)
        }
    }

    private fun hookDisplayPowerControllerConstructor(classLoader: ClassLoader) {
        try {
            val constructor: Constructor<*> = classLoader.loadClass(DISPLAY_POWER_CONTROLLER)
                .getDeclaredConstructor(
                    classLoader.loadClass("android.content.Context"),
                    classLoader.loadClass(DISPLAY_POWER_CONTROLLER_INJECTOR),
                    classLoader.loadClass(
                        $$"android.hardware.display.DisplayManagerInternal$DisplayPowerCallbacks"
                    ),
                    classLoader.loadClass("android.os.Handler"),
                    classLoader.loadClass("android.hardware.SensorManager"),
                    classLoader.loadClass("com.android.server.display.DisplayBlanker"),
                    classLoader.loadClass("com.android.server.display.LogicalDisplay"),
                    classLoader.loadClass("com.android.server.display.BrightnessTracker"),
                    classLoader.loadClass("com.android.server.display.BrightnessSetting"),
                    classLoader.loadClass("java.lang.Runnable"),
                    classLoader.loadClass(
                        "com.android.server.display.HighBrightnessModeMetadata"),
                    Boolean::class.javaPrimitiveType,
                    classLoader.loadClass(
                        "com.android.server.display.feature.DisplayManagerFlags")
                )
            hookWithId(constructor, "display_power_controller_init") { chain ->
                val result = chain.proceed()
                val thisObject = chain.thisObject
                findField(thisObject.javaClass, "mColorFadeEnabled").setBoolean(thisObject, true)
                findField(thisObject.javaClass, "mColorFadeFadesConfig").setBoolean(thisObject, true)
                logger.debug("Forced DisplayPowerController color fade animation enabled.")
                result
            }
        } catch (e: Exception) {
            logger.error("Failed to hook DisplayPowerController constructor", e)
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
            if (onAnimator != null) {
                findMethod(onAnimator.javaClass, "setDuration", Long::class.javaPrimitiveType)
                    .invoke(onAnimator, SCREEN_ON_ANIMATION_DURATION_MS)
            }
            if (offAnimator != null) {
                findMethod(offAnimator.javaClass, "setDuration", Long::class.javaPrimitiveType)
                    .invoke(offAnimator, SCREEN_OFF_ANIMATION_DURATION_MS)
            }
            logger.debug("Configured color fade animator durations: on="
                + getAnimatorDuration(onAnimator)
                + ", off=" + getAnimatorDuration(offAnimator))
        } catch (t: Throwable) {
            logger.error("Failed to configure color fade animator durations: ", t)
        }
    }

    private fun getAnimatorDuration(animator: Any?): Long {
        return try {
            findMethod(animator?.javaClass, "getDuration").invoke(animator) as? Long ?: -1L
        } catch (_: Throwable) { -1L }
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
                if (chain.getArg(0) as Int == DISPLAY_STATE_ON) {
                    startScreenOnColorFade(chain.thisObject)
                }
                chain.proceed()
            }
        } catch (e: Exception) {
            logger.error("Failed to hook animateScreenStateChange", e)
        }
    }

    /**
     * Starts the prepared color fade before the host applies the screen-on state change.
     * The host drops a prepared fade unless its ON animator is already running, so the
     * animator has to be started first; see docs/research/screen-on-off-animation.md.
     *
     * @return true when this call started the fade animator.
     */
    private fun startScreenOnColorFade(controller: Any): Boolean {
        val reportedState = intField(controller, "mReportedScreenStateToPolicy")
        // These values mean this call is about to block the panel until WindowManager has
        // drawn; the fade must not run while that wait is pending.
        if (reportedState == null
            || reportedState == REPORTED_STATE_SCREEN_OFF
            || reportedState == REPORTED_STATE_UNKNOWN
        ) {
            return false
        }
        if (fieldValue(controller, "mPendingScreenOnUnblocker") != null) {
            return false
        }

        val powerState = fieldValue(controller, "mPowerState") ?: return false
        val screenState = invokeNoArgs(powerState, "getScreenState") as? Int ?: return false
        if (screenState == DISPLAY_STATE_DOZE
            || screenState == DISPLAY_STATE_ON_SUSPEND
            || screenState == DISPLAY_STATE_DOZE_SUSPEND
        ) {
            return false
        }
        if (booleanField(powerState, "mColorFadePrepared") != true) {
            return false
        }
        val colorFadeLevel = invokeNoArgs(powerState, "getColorFadeLevel") as? Float ?: return false
        if (colorFadeLevel >= 1.0f) {
            return false
        }

        val onAnimator = fieldValue(controller, "mColorFadeOnAnimator") ?: return false
        if (invokeNoArgs(onAnimator, "isStarted") as? Boolean == true) {
            return false
        }

        return try {
            findMethod(onAnimator.javaClass, "setDuration", Long::class.javaPrimitiveType)
                .invoke(onAnimator, SCREEN_ON_ANIMATION_DURATION_MS)
            findMethod(onAnimator.javaClass, "setFloatValues", FloatArray::class.java)
                .invoke(onAnimator, floatArrayOf(colorFadeLevel, 1.0f))
            findMethod(onAnimator.javaClass, "start").invoke(onAnimator)
            logger.debug("Started the screen-on color fade at level " + colorFadeLevel)
            true
        } catch (t: Throwable) {
            logger.error("Failed to start the screen-on color fade: ", t)
            false
        }
    }

    private fun fieldValue(target: Any, name: String): Any? =
        runCatching { findField(target.javaClass, name).get(target) }.getOrNull()

    private fun booleanField(target: Any, name: String): Boolean? =
        fieldValue(target, name) as? Boolean

    private fun intField(target: Any, name: String): Int? = fieldValue(target, name) as? Int

    private fun invokeNoArgs(target: Any, name: String): Any? =
        runCatching { findMethod(target.javaClass, name).invoke(target) }.getOrNull()

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
        private const val DISPLAY_POWER_CONTROLLER_INJECTOR =
            $$"$$DISPLAY_POWER_CONTROLLER$Injector"
        private const val DISPLAY_STATE_ON = 2
        private const val DISPLAY_STATE_DOZE = 3
        private const val DISPLAY_STATE_ON_SUSPEND = 4
        private const val DISPLAY_STATE_DOZE_SUSPEND = 6
        private const val REPORTED_STATE_SCREEN_OFF = 0
        private const val REPORTED_STATE_UNKNOWN = -1
        private const val DEFAULT_ANIMATION_DURATION_MS = 400L
        private var SCREEN_ON_ANIMATION_DURATION_MS = DEFAULT_ANIMATION_DURATION_MS
        private var SCREEN_OFF_ANIMATION_DURATION_MS = DEFAULT_ANIMATION_DURATION_MS
    }
}
