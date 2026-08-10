package com.qimian233.ztool.hook.modules.gametool

import android.annotation.SuppressLint
import android.content.Context
import android.os.Bundle
import com.qimian233.ztool.data.keys.PreferenceKeys
import com.qimian233.ztool.data.keys.ScopeKeys
import com.qimian233.ztool.hook.base.AppHookModule
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam

/**
 * 禁用游戏音频优化Hook模块（App层）
 * 在应用进程中拦截游戏音频属性设置，防止游戏模式干扰音频体验
 */
@SuppressLint("PrivateApi", "DiscouragedPrivateApi")
class DisableGameAudioApp : AppHookModule() {
    override fun getModuleName(): String = PreferenceKeys.DISABLE_GAME_AUDIO_APP.name

    override fun getTargetPackages(): Array<String> = arrayOf(ScopeKeys.GAME_SERVICE.packageName)

    override fun handleLoadPackage(param: PackageLoadedParam) {
        val classLoader = param.defaultClassLoader
        val packageName = param.packageName
        hookGameApp(classLoader, packageName)
        hookGameServicePackage(classLoader)
    }

    /**
     * 针对特定游戏的Hook
     */
    private fun hookGameApp(classLoader: ClassLoader, packageName: String?) {
        try {
            logger.info("Hooking game app: $packageName")

            // 在游戏启动时主动清除游戏音频属性
            val activityClass = classLoader.loadClass("android.app.Activity")
            val onCreateMethod = activityClass.getDeclaredMethod("onCreate", Bundle::class.java)
            hookWithId(onCreateMethod, "on_create") { chain ->
                chain.proceed()
                // 清除游戏音频属性
                clearGameAudioProperties()
                logger.debug("Cleared game audio properties in $packageName")
                null
            }
        } catch (t: Throwable) {
            logger.error("Failed to hook game app", t)
        }
    }

    /**
     * 主动清除游戏音频属性
     */
    private fun clearGameAudioProperties() {
        try {
            // 使用反射调用 SystemProperties.set 来清除属性
            @SuppressLint("PrivateApi") val systemPropertiesClass =
                Class.forName("android.os.SystemProperties")
            val setMethod =
                systemPropertiesClass.getMethod("set", String::class.java, String::class.java)
            setMethod.invoke(null, TARGET_PROPERTY, "")

            logger.info("Manually cleared $TARGET_PROPERTY")
        } catch (e: Exception) {
            logger.error("Failed to clear properties", e)
        }
    }

    private fun hookGameServicePackage(classLoader: ClassLoader) {
        try {
            logger.info("Start processing DolbyUtils.")
            val m = classLoader
                .loadClass("com.zui.game.service.util.DolbyUtils")
                .getDeclaredMethod("handleDolbyGameSound", Context::class.java, Integer.TYPE)
            hookWithId(m, "hook_89") { null }
            logger.info("Successfully hooked DolbyUtils.handleDolbyGameSound - disabled game sound processing")
        } catch (t: Throwable) {
            logger.error("Failed to hook GameService package", t)
        }
    }

    companion object {
        private const val TARGET_PROPERTY = "sys.audio.game_name"
    }
}
