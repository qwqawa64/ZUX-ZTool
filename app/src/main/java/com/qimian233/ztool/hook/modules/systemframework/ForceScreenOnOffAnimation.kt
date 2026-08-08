package com.qimian233.ztool.hook.modules.systemframework

import android.annotation.SuppressLint
import com.qimian233.ztool.data.keys.PreferenceKeys
import com.qimian233.ztool.data.keys.ScopeKeys
import com.qimian233.ztool.hook.base.SystemHookModule
import io.github.libxposed.api.XposedModuleInterface.SystemServerStartingParam
import java.lang.reflect.Constructor

/**
 * 强制启用 DisplayPowerController 的屏幕开/关 Color Fade 动画，
 * 并通过偏好设置 [PreferenceKeys.SCREEN_ON_OFF_ANIMATION_MS] 自定义动画时长。
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
                if (tryStartPreparedScreenOnAnimation(chain.thisObject, chain.getArg(0) as Int)) {
                    return@hookWithId null
                }
                chain.proceed()
            }
        } catch (e: Exception) {
            logger.error("Failed to hook animateScreenStateChange", e)
        }
    }

    private fun tryStartPreparedScreenOnAnimation(controller: Any, targetState: Int): Boolean {
        if (targetState != 2) {
            return false
        }
        val powerState = runCatching {
            findField(controller.javaClass, "mPowerState").get(controller)
        }.getOrNull()
        val currentColorFadeLevel = powerState?.let { state ->
            runCatching {
                findMethod(state.javaClass, "getColorFadeLevel").invoke(state) as? Float
            }.getOrNull()
        }
        if (powerState == null
            || !findField(powerState.javaClass, "mColorFadePrepared").getBoolean(powerState)
            || (currentColorFadeLevel ?: 1.0f) >= 1.0f
        ) {
            return false
        }
        val onAnimator = runCatching {
            findField(controller.javaClass, "mColorFadeOnAnimator").get(controller)
        }.getOrNull()
        if (onAnimator == null) {
            return false
        }

        return try {
            findMethod(onAnimator.javaClass, "cancel").invoke(onAnimator)
            findMethod(onAnimator.javaClass, "setDuration", Long::class.javaPrimitiveType)
                .invoke(onAnimator, SCREEN_ON_ANIMATION_DURATION_MS)
            findMethod(onAnimator.javaClass, "setFloatValues", FloatArray::class.java)
                .invoke(
                    onAnimator,
                    floatArrayOf(currentColorFadeLevel ?: 0.0f, 1.0f)
                )
            findMethod(onAnimator.javaClass, "start").invoke(onAnimator)
            logger.debug("Started prepared screen-on color fade animation.")
            true
        } catch (t: Throwable) {
            logger.error("Failed to start prepared screen-on color fade animation: ", t)
            false
        }
    }

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
        private const val DEFAULT_ANIMATION_DURATION_MS = 400L
        private var SCREEN_ON_ANIMATION_DURATION_MS = DEFAULT_ANIMATION_DURATION_MS
        private var SCREEN_OFF_ANIMATION_DURATION_MS = DEFAULT_ANIMATION_DURATION_MS
    }
}
