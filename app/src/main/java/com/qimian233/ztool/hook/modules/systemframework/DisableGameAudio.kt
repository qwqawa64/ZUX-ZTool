package com.qimian233.ztool.hook.modules.systemframework

import android.annotation.SuppressLint
import com.qimian233.ztool.data.keys.PreferenceKeys
import com.qimian233.ztool.data.keys.ScopeKeys
import com.qimian233.ztool.hook.base.SystemHookModule
import io.github.libxposed.api.XposedModuleInterface.SystemServerStartingParam
import kotlin.math.min

/**
 * 禁用游戏音频优化Hook模块
 * 拦截系统游戏音频属性设置，防止游戏模式干扰音频体验
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
     * 方法1：直接 Hook SystemProperties.set 方法
     * 拦截所有对 sys.audio.game_name 的设置
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

                    // 打印调用栈以调试
                    val stackTrace = Thread.currentThread().stackTrace
                    val stackTraceStr = StringBuilder()
                    for (i in 0..<min(stackTrace.size, 10)) {
                        stackTraceStr.append(stackTrace[i].toString()).append("\n")
                    }
                    logger.trace("Call stack:\n$stackTraceStr")

                    // 阻止设置该属性
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
     * 方法2：Hook PhoneWindowManager 中的 ZuiGameAppStateListener
     * 拦截游戏模式相关的设置
     */
    private fun hookPhoneWindowManager(classLoader: ClassLoader) {
        try {
            var targetClass: Class<*>?
            targetClass = try {
                classLoader.loadClass($$"com.android.server.policy.PhoneWindowManager$ZuiGameAppStateListener")
            } catch (_: ClassNotFoundException) {
                null
            }
            if (targetClass == null) {
                try {
                    targetClass =
                        classLoader.loadClass("com.android.server.policy.PhoneWindowManager$2")
                } catch (_: ClassNotFoundException) {
                    logger.error("Unable to find PhoneWindowManager internal class")
                }
                if (targetClass == null) {
                    logger.error("Failed to find target class for PhoneWindowManager")
                    return
                } else {
                    logger.info("Found alternative class for PhoneWindowManager")
                }
            } else {
                logger.info("Found target class for PhoneWindowManager")
            }
            // Hook ZuiGameAppStateListener 的 onGameAppStart 方法
            val onGameAppStartMethod = targetClass.getDeclaredMethod(
                "onGameAppStart",
                String::class.java,
                String::class.java
            )
            hookWithId(
                onGameAppStartMethod,
                "on_game_app_start"
            ) { chain ->
                val pkgName = chain.args[0] as String
                logger.debug("ZuiGameAppStateListener.onGameAppStart for: $pkgName")
                chain.proceed()
            }

            // Hook ZuiGameAppStateListener 的 onGameAppExit 方法
            val onGameAppExitMethod = targetClass.getDeclaredMethod(
                "onGameAppExit",
                String::class.java,
                String::class.java
            )
            hookWithId(
                onGameAppExitMethod,
                "on_game_app_exit"
            ) { chain ->
                val pkgName = chain.args[0] as String
                logger.debug("ZuiGameAppStateListener.onGameAppExit for: $pkgName")
                chain.proceed()
            }

            logger.info("Successfully hooked PhoneWindowManager")
        } catch (e: Exception) {
            logger.error("Failed to hook PhoneWindowManager due to unknown reason: ", e)
        }
    }

    /**
     * 方法3：Hook AudioManager.setParameters 方法
     * 拦截 game_voip=true 的设置
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

                    // 阻止设置游戏VOIP参数
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
    }
}
