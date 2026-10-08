package com.qimian233.ztool.hook.modules.systemframework

import android.annotation.SuppressLint
import android.content.Context
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
            hookColorFadeDismiss(classLoader)
            hookWithId(animateMethod, "animate_screen_state") { chain ->
                val controller = chain.thisObject
                if (chain.getArg(0) as Int != DISPLAY_STATE_ON) {
                    return@hookWithId chain.proceed()
                }
                val powerState = fieldValue(controller, "mPowerState")
                val onAnimator = fieldValue(controller, "mColorFadeOnAnimator")
                val colorFadeLevel = invokeNoArgs(powerState, "getColorFadeLevel") as? Float ?: 1.0f
                val fadePending = colorFadeLevel < 1.0f &&
                    booleanField(powerState, "mColorFadePrepared") == true &&
                    invokeNoArgs(onAnimator, "isStarted") != true
                logger.info("Screen-on: fadePending=" + fadePending + " "
                    + describeScreenOn(controller))
                if (fadePending && powerState != null && onAnimator != null) {
                    startScreenOnColorFade(controller, colorFadeLevel, onAnimator)
                }
                chain.proceed()
                if (powerState != null && onAnimator != null && colorFadeLevel < 1.0f
                    && booleanField(powerState, "mColorFadePrepared") != true
                ) {
                    restoreScreenOnColorFade(controller, powerState, onAnimator, colorFadeLevel)
                }
                null
            }
        } catch (e: Exception) {
            logger.error("Failed to hook animateScreenStateChange", e)
        }
    }

    private fun hookColorFadeDismiss(classLoader: ClassLoader) {
        try {
            val dismissMethod = classLoader.loadClass(DISPLAY_POWER_STATE)
                .getDeclaredMethod("dismissColorFade")
            hookWithId(dismissMethod, "color_fade_dismiss") { chain ->
                val powerState = chain.thisObject
                val colorFadeLevel = invokeNoArgs(powerState, "getColorFadeLevel") as? Float
                val wasPrepared = booleanField(powerState, "mColorFadePrepared") == true
                val result = chain.proceed()
                if (wasPrepared && (colorFadeLevel ?: 1.0f) < 1.0f) {
                    logger.info("Running color fade (level " + colorFadeLevel + ") dismissed by "
                        + callerFrame())
                }
                result
            }
        } catch (e: Exception) {
            logger.error("Failed to hook DisplayPowerState.dismissColorFade", e)
        }
    }

    /**
     * Starts the prepared color fade before the host applies the screen-on state change.
     * The host drops a prepared fade unless its ON animator is already running, so the
     * animator has to be started first; see docs/research/screen-on-off-animation.md.
     *
     * @return true when this call started the fade animator.
     */
    private fun startScreenOnColorFade(
        controller: Any,
        colorFadeLevel: Float,
        onAnimator: Any
    ): Boolean {
        val reportedState = intField(controller, "mReportedScreenStateToPolicy")
        // These values mean this call is about to block the panel until WindowManager has
        // drawn; the fade must not run while that wait is pending.
        if (reportedState == null
            || reportedState == REPORTED_STATE_SCREEN_OFF
            || reportedState == REPORTED_STATE_UNKNOWN
        ) {
            logger.info("Not starting the color fade now: reported state is $reportedState")
            return false
        }
        if (fieldValue(controller, "mPendingScreenOnUnblocker") != null) {
            logger.info("Not starting the color fade now: screen-on is still blocked")
            return false
        }
        val powerState = fieldValue(controller, "mPowerState")
        val screenState = invokeNoArgs(powerState, "getScreenState") as? Int
        if (screenState == null
            || screenState == DISPLAY_STATE_DOZE
            || screenState == DISPLAY_STATE_ON_SUSPEND
            || screenState == DISPLAY_STATE_DOZE_SUSPEND
        ) {
            logger.info("Not starting the color fade now: display state is $screenState")
            return false
        }
        if (!startColorFadeAnimator(onAnimator, colorFadeLevel)) {
            return false
        }
        logger.info("Started the screen-on color fade at level " + colorFadeLevel)
        return true
    }

    /**
     * Rebuilds the color fade that the host dismissed while the screen-on state change ran.
     * Runs in the same handler message as that change, so the panel is still black.
     *
     * @return true when the fade was rebuilt and started again.
     */
    private fun restoreScreenOnColorFade(
        controller: Any,
        powerState: Any,
        onAnimator: Any,
        colorFadeLevel: Float
    ): Boolean {
        val screenState = invokeNoArgs(powerState, "getScreenState") as? Int
        if (screenState != DISPLAY_STATE_ON) {
            logger.info("Color fade was dropped, but display state is $screenState; not rebuilding")
            return false
        }
        if (invokeNoArgs(fieldValue(controller, "mPowerRequest"), "isBrightOrDim") != true) {
            logger.info("Color fade was dropped by a dim/doze policy; not rebuilding")
            return false
        }
        val context = fieldValue(controller, "mContext")
        val mode = if (booleanField(controller, "mColorFadeFadesConfig") == true) {
            COLOR_FADE_MODE_FADE
        } else {
            COLOR_FADE_MODE_COOL_DOWN
        }
        val prepared = runCatching {
            findMethod(
                powerState.javaClass, "prepareColorFade",
                Context::class.java, Int::class.javaPrimitiveType
            ).invoke(powerState, context, mode)
        }.getOrNull() as? Boolean ?: false
        if (!prepared) {
            logger.info("Could not rebuild the screen-on color fade")
            return false
        }
        runCatching {
            findMethod(powerState.javaClass, "setColorFadeLevel", Float::class.javaPrimitiveType)
                .invoke(powerState, colorFadeLevel)
        }
        val started = startColorFadeAnimator(onAnimator, colorFadeLevel)
        logger.info("Rebuilt the dismissed screen-on color fade: started=" + started)
        return started
    }

    private fun startColorFadeAnimator(onAnimator: Any, colorFadeLevel: Float): Boolean {
        return try {
            findMethod(onAnimator.javaClass, "setDuration", Long::class.javaPrimitiveType)
                .invoke(onAnimator, SCREEN_ON_ANIMATION_DURATION_MS)
            findMethod(onAnimator.javaClass, "setFloatValues", FloatArray::class.java)
                .invoke(onAnimator, floatArrayOf(colorFadeLevel, 1.0f))
            findMethod(onAnimator.javaClass, "start").invoke(onAnimator)
            true
        } catch (t: Throwable) {
            logger.error("Failed to start the screen-on color fade: ", t)
            false
        }
    }

    private fun describeScreenOn(controller: Any): String {
        val powerState = fieldValue(controller, "mPowerState")
        val powerRequest = fieldValue(controller, "mPowerRequest")
        return "reported=" + intField(controller, "mReportedScreenStateToPolicy") +
            " unblocker=" + (fieldValue(controller, "mPendingScreenOnUnblocker") != null) +
            " prepared=" + booleanField(powerState, "mColorFadePrepared") +
            " level=" + invokeNoArgs(powerState, "getColorFadeLevel") +
            " displayState=" + invokeNoArgs(powerState, "getScreenState") +
            " onStarted=" + invokeNoArgs(fieldValue(controller, "mColorFadeOnAnimator"), "isStarted") +
            " offStarted=" + invokeNoArgs(fieldValue(controller, "mColorFadeOffAnimator"), "isStarted") +
            " fadeEnabled=" + booleanField(controller, "mColorFadeEnabled") +
            " brightOrDim=" + invokeNoArgs(powerRequest, "isBrightOrDim") +
            " r4Occluded=" + booleanField(controller, "mR4BootAnimOccluded") +
            " readyForDisplay=" + isReadyForDisplay(controller)
    }

    private fun isReadyForDisplay(controller: Any): Any? {
        val context = fieldValue(controller, "mContext") ?: return null
        val displayInfo = fieldValue(controller, "mDisplayInfo") ?: return null
        return runCatching {
            controller.javaClass.classLoader!!.loadClass(MOTO_DESKTOP_MANAGER)
                .getDeclaredMethod("isReadyForDisplay", Context::class.java, displayInfo.javaClass)
                .apply { isAccessible = true }
                .invoke(null, context, displayInfo)
        }.getOrNull()
    }

    private fun callerFrame(): String {
        return Thread.currentThread().stackTrace
            .firstOrNull { it.className.contains(DISPLAY_POWER_CONTROLLER) }
            ?.let { it.className.substringAfterLast('.') + "." + it.methodName }
            ?: "unknown"
    }

    private fun fieldValue(target: Any?, name: String): Any? =
        target?.let { runCatching { findField(it.javaClass, name).get(it) }.getOrNull() }

    private fun booleanField(target: Any?, name: String): Boolean? =
        fieldValue(target, name) as? Boolean

    private fun intField(target: Any?, name: String): Int? = fieldValue(target, name) as? Int

    private fun invokeNoArgs(target: Any?, name: String): Any? =
        target?.let { runCatching { findMethod(it.javaClass, name).invoke(it) }.getOrNull() }

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
        private const val DISPLAY_POWER_STATE = "com.android.server.display.DisplayPowerState"
        private const val MOTO_DESKTOP_MANAGER = "com.motorola.internal.app.MotoDesktopManager"
        private const val COLOR_FADE_MODE_COOL_DOWN = 0
        private const val COLOR_FADE_MODE_FADE = 2
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
