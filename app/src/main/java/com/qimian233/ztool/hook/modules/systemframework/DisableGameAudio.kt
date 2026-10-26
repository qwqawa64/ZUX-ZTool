package com.qimian233.ztool.hook.modules.systemframework

import android.annotation.SuppressLint
import com.qimian233.ztool.data.keys.PreferenceKeys
import com.qimian233.ztool.data.keys.ScopeKeys
import com.qimian233.ztool.hook.base.SystemHookModule
import io.github.libxposed.api.XposedModuleInterface.SystemServerStartingParam
import kotlin.math.min

/**
 * Disable game audio optimization hook module
 * Intercepts system game audio property settings to prevent game mode from interfering with audio
 */
@SuppressLint("PrivateApi", "DiscouragedPrivateApi")
class DisableGameAudio : SystemHookModule() {
    override fun getModuleName(): String = PreferenceKeys.DISABLE_GAME_AUDIO.name

    override fun getTargetPackages(): Array<String> = arrayOf(ScopeKeys.ANDROID_SYSTEM.packageName)

    override fun handleSystemServerStarting(param: SystemServerStartingParam) {
        val classLoader = param.classLoader
        hookSystemProperties(classLoader)
        hookPhoneWindowManager(classLoader)
        hookAudioManager(classLoader)
    }

    /**
     * Method 1: hook SystemProperties.set directly
     * Intercepts all writes to sys.audio.game_name
     */
    private fun hookSystemProperties(classLoader: ClassLoader) {
        try {
            logger.info("Attempting to hook SystemProperties.set")

            val sysPropsClass = classLoader.loadClass("android.os.SystemProperties")
            val setMethod =
                sysPropsClass.getDeclaredMethod("set", String::class.java, String::class.java)
            hookWithId(setMethod, "set") { chain ->
                val key = chain.args[0] as String
                val value = chain.args[1] as String

                if (TARGET_PROPERTY == key) {
                    logger.debug("Blocked SystemProperties.set for $key = $value")

                    // Print the call stack for debugging
                    val stackTrace = Thread.currentThread().stackTrace
                    val stackTraceStr = StringBuilder()
                    for (i in 0..<min(stackTrace.size, 10)) {
                        stackTraceStr.append(stackTrace[i].toString()).append("\n")
                    }
                    logger.trace("Call stack:\n$stackTraceStr")

                    // Block setting this property
                    return@hookWithId null
                }
                chain.proceed()
            }

            logger.info("Successfully hooked SystemProperties.set")
        } catch (t: Throwable) {
            logger.error("Failed to hook SystemProperties.set", t)
        }
    }

    /**
     * Method 2: observes the Zui game app state listener held by PhoneWindowManager.
     * The listener is resolved from the `mZuiGameAppStateListener` field, because the
     * anonymous implementation class name differs between ZUI versions.
     */
    private fun hookPhoneWindowManager(classLoader: ClassLoader) {
        val pwmClass = runCatching {
            classLoader.loadClass("com.android.server.policy.PhoneWindowManager")
        }.getOrNull()
        if (pwmClass != null) {
            val listenerField = runCatching { findField(pwmClass, "mZuiGameAppStateListener") }.getOrNull()
            val initGameMode = runCatching { findMethod(pwmClass, "initGameMode") }.getOrNull()
            if (listenerField != null && initGameMode != null) {
                try {
                    hookWithId(initGameMode, "pwm_init_game_mode") { chain ->
                        val listener = listenerField.get(chain.thisObject)
                        if (listener != null) {
                            if (hookGameAppStateListener(listener.javaClass)) {
                                logger.info("Hooked ${listener.javaClass.name} game app listener")
                            } else {
                                logger.warn("Game app listener ${listener.javaClass.name} has no hookable callbacks")
                            }
                        }
                        chain.proceed()
                    }
                    logger.info("Hooked PhoneWindowManager.initGameMode for Zui game app listener")
                    return
                } catch (t: Throwable) {
                    logger.error("Failed to hook PhoneWindowManager.initGameMode", t)
                }
            }
        }

        for (className in LEGACY_LISTENER_CLASSES) {
            val listenerClass = runCatching { classLoader.loadClass(className) }.getOrNull() ?: continue
            if (hookGameAppStateListener(listenerClass)) {
                logger.info("Hooked legacy listener $className")
                return
            }
        }
        logger.warn("Zui game app state listener not found; game audio listener hook skipped")
    }

    /**
     * Hooks `onGameAppStart` and `onGameAppExit` if the class provides both.
     *
     * @return true when both methods were hooked
     */
    private fun hookGameAppStateListener(listenerClass: Class<*>): Boolean {
        val onGameAppStart = runCatching {
            findMethod(listenerClass, "onGameAppStart", String::class.java, String::class.java)
        }.getOrNull() ?: return false
        val onGameAppExit = runCatching {
            findMethod(listenerClass, "onGameAppExit", String::class.java, String::class.java)
        }.getOrNull() ?: return false
        return try {
            hookWithId(onGameAppStart, "on_game_app_start") { chain ->
                logger.debug("ZuiGameAppStateListener.onGameAppStart for: ${chain.args[0]}")
                chain.proceed()
            }
            hookWithId(onGameAppExit, "on_game_app_exit") { chain ->
                logger.debug("ZuiGameAppStateListener.onGameAppExit for: ${chain.args[0]}")
                chain.proceed()
            }
            true
        } catch (t: Throwable) {
            logger.error("Failed to hook ${listenerClass.name} game app listener", t)
            false
        }
    }

    /**
     * Method 3: hook AudioManager.setParameters
     * Intercepts game_voip=true settings
     */
    private fun hookAudioManager(classLoader: ClassLoader) {
        try {
            logger.info("Attempting to hook AudioManager.setParameters")

            val audioManagerClass = classLoader.loadClass("android.media.AudioManager")
            val setParametersMethod =
                audioManagerClass.getDeclaredMethod("setParameters", String::class.java)
            hookWithId(
                setParametersMethod,
                "set_parameters"
            ) { chain ->
                val keyValuePairs = chain.args[0] as String
                if (keyValuePairs.contains("game_voip=true")) {
                    logger.debug("Blocked AudioManager.setParameters: $keyValuePairs")

                    // Block setting the game VOIP parameter
                    return@hookWithId null
                }
                chain.proceed()
            }

            logger.info("Successfully hooked AudioManager.setParameters")
        } catch (t: Throwable) {
            logger.error("Failed to hook AudioManager.setParameters", t)
        }
    }

    companion object {
        private const val TARGET_PROPERTY = "sys.audio.game_name"

        // Candidate names used by ZUI builds that declared the listener as a named inner class
        private val LEGACY_LISTENER_CLASSES = listOf(
            $$"com.android.server.policy.PhoneWindowManager$ZuiGameAppStateListener",
            $$"com.android.server.policy.PhoneWindowManager$2"
        )
    }
}
