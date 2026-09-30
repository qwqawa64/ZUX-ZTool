package com.qimian233.ztool.hook.modules.lsfdevice

import com.qimian233.ztool.data.keys.PreferenceKeys
import com.qimian233.ztool.data.keys.ScopeKeys
import com.qimian233.ztool.hook.base.AppHookModule
import io.github.libxposed.api.XposedModuleInterface

/**
 * Disables the SDAC activation registration of the Lenovo device service
 * companion app (com.tblenovo.tabpushout, "pushout" channel).
 *
 * The app's sole function is registering the device with
 * https://sdac.lenovo.com/DSS/api/Activation/Regist (device identifiers,
 * Wi-Fi access points, coarse region), which also lifts the 3-day push
 * deferral (lsf_start_time) inside com.lenovo.lsf.device. Its keep-alive is
 * an exact-alarm retry loop: UpgradeReceiver re-arms a
 * "com.lenovo.lsf.sdac.ALARM" alarm every 15 minutes until registration
 * succeeds.
 *
 * Both entry points are Manifest-anchored, non-obfuscated BroadcastReceivers;
 * swallowing onReceive on both stops the alarm cycle and the registration.
 */
class DisableTabPushoutSdacRegister : AppHookModule() {

    override fun getModuleName(): String = PreferenceKeys.DISABLE_TAB_PUSHOUT_SDAC.name

    override fun getTargetPackages(): Array<out String?>? =
        arrayOf(ScopeKeys.TAB_PUSHOUT.packageName)

    @Throws(Throwable::class)
    override fun handleLoadPackage(param: XposedModuleInterface.PackageLoadedParam) {
        val classLoader = param.defaultClassLoader

        try {
            val pushReceiverClass = classLoader.loadClass(
                "com.lenovo.lsf.push.receiver.PushReceiver"
            )
            val onReceive = findMethod(
                pushReceiverClass, "onReceive",
                android.content.Context::class.java, android.content.Intent::class.java
            )
            hookWithId(onReceive, "tab_pushout_push_receiver") { chain ->
                val action = (chain.args[1] as? android.content.Intent)?.action
                logger.debug("Blocked PushReceiver.onReceive, action=$action")
                // no-op: swallow keep-alive triggers
            }
        } catch (t: Throwable) {
            logger.error("Failed to hook PushReceiver", t)
        }

        try {
            val upgradeReceiverClass = classLoader.loadClass(
                "com.lenovo.lsf.upgrade.UpgradeReceiver"
            )
            val onReceive = findMethod(
                upgradeReceiverClass, "onReceive",
                android.content.Context::class.java, android.content.Intent::class.java
            )
            hookWithId(onReceive, "tab_pushout_upgrade_receiver") { chain ->
                val action = (chain.args[1] as? android.content.Intent)?.action
                logger.debug("Blocked UpgradeReceiver.onReceive, action=$action")
                // no-op: swallow the 15-minute SDAC alarm
            }
        } catch (t: Throwable) {
            logger.error("Failed to hook UpgradeReceiver", t)
        }
    }
}
