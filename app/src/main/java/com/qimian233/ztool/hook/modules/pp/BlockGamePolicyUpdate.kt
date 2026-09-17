package com.qimian233.ztool.hook.modules.pp

import com.qimian233.ztool.data.keys.PreferenceKeys
import com.qimian233.ztool.data.keys.ScopeKeys
import com.qimian233.ztool.hook.base.AppHookModule
import io.github.libxposed.api.XposedModuleInterface

/**
 * Blocks cloud-side OTA application of game/performance policies in the ZUI
 * performance service (com.zui.pp).
 *
 * WhatsNewBroadcastReceiver receives the
 * android.action.targetcomponent.performance.dataupdate broadcast pushed by
 * com.lenovo.tbengine (UDS engine), pulls gamepolicy.zip from a
 * ContentProvider, unpacks it, and overwrites PerformanceConfig /
 * GamePolicyConfig by version number. Swallowing onReceive prevents
 * cloud-delivered policy packages from being written to disk, keeping the
 * built-in policies under system/etc.
 */
class BlockGamePolicyUpdate : AppHookModule() {

    override fun getModuleName(): String = PreferenceKeys.PP_BLOCK_GAME_POLICY_UPDATE.name

    override fun getTargetPackages(): Array<out String?>? = arrayOf(ScopeKeys.ZUI_PERFORMANCE.packageName)

    @Throws(Throwable::class)
    override fun handleLoadPackage(param: XposedModuleInterface.PackageLoadedParam) {
        val classLoader = param.defaultClassLoader

        try {
            val receiverClass = classLoader.loadClass(
                "com.zui.performance.utils.WhatsNewBroadcastReceiver"
            )
            val onReceive = findMethod(
                receiverClass,
                "onReceive",
                android.content.Context::class.java,
                android.content.Intent::class.java
            )
            hookWithId(onReceive, "pp_game_policy_broadcast") { _ ->
                logger.debug("Blocked WhatsNewBroadcastReceiver.onReceive (game policy OTA).")
                // no-op: do not receive the cloud policy package
            }
            logger.info("Hooked WhatsNewBroadcastReceiver.onReceive")
        } catch (t: Throwable) {
            logger.error("Failed to hook WhatsNewBroadcastReceiver.onReceive", t)
        }
    }
}
