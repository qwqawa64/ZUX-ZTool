package com.qimian233.ztool.hook.modules.gametool

import android.annotation.SuppressLint
import android.content.Context
import android.os.Bundle
import com.qimian233.ztool.data.keys.PreferenceKeys
import com.qimian233.ztool.data.keys.ScopeKeys
import com.qimian233.ztool.hook.base.AppHookModule
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam

/**
 * Game audio optimization disable hook module (app side).
 * Intercepts game audio property setting in the app process to prevent
 * game mode from interfering with the audio experience.
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
     * Hook for specific game apps.
     */
    private fun hookGameApp(classLoader: ClassLoader, packageName: String?) {
        try {
            logger.info("Hooking game app: $packageName")

            // Proactively clear game audio properties when a game starts
            val activityClass = classLoader.loadClass("android.app.Activity")
            val onCreateMethod = activityClass.getDeclaredMethod("onCreate", Bundle::class.java)
            hookWithId(onCreateMethod, "on_create") { chain ->
                chain.proceed()
                // Clear game audio properties
                clearGameAudioProperties()
                logger.debug("Cleared game audio properties in $packageName")
                null
            }
        } catch (t: Throwable) {
            logger.error("Failed to hook game app", t)
        }
    }

    /**
     * Proactively clear game audio properties.
     */
    private fun clearGameAudioProperties() {
        try {
            // Use reflection to call SystemProperties.set to clear the property
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
