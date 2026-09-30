package com.qimian233.ztool.hook.modules.lsfdevice

import com.qimian233.ztool.data.keys.PreferenceKeys
import com.qimian233.ztool.data.keys.ScopeKeys
import com.qimian233.ztool.hook.base.AppHookModule
import io.github.libxposed.api.XposedModuleInterface

/**
 * Disables the push keep-alive channel of the Lenovo device service
 * (com.lenovo.lsf.device, the LSF push/UPS component).
 *
 * Both keep-alive entry points are Manifest-anchored, non-obfuscated
 * BroadcastReceiver classes:
 * - PushReceiver: BOOT_COMPLETED / CONNECTIVITY_CHANGE / power plug events and
 *   package change events all funnel into onReceive, which starts the
 *   ONLINE/OFFLINE push services and registers the SDAC alarm;
 * - UpgradeReceiver: sole receiver of the hourly "com.lenovo.lsf.sdac.ALARM"
 *   exact alarm that drives the registration/message-check loop.
 *
 * Swallowing onReceive on both breaks the self-waking alarm cycle and the
 * network-triggered re-registration without touching any obfuscated class.
 */
class DisableLsfDevicePush : AppHookModule() {

    override fun getModuleName(): String = PreferenceKeys.DISABLE_LSF_DEVICE_PUSH.name

    override fun getTargetPackages(): Array<out String?>? =
        arrayOf(ScopeKeys.LENOVO_DEVICE_SERVICE.packageName)

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
            hookWithId(onReceive, "lsf_device_push_receiver") { chain ->
                val action = (chain.args[1] as? android.content.Intent)?.action
                logger.debug("Blocked PushReceiver.onReceive, action=$action")
                // no-op: swallow all keep-alive triggers
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
            hookWithId(onReceive, "lsf_device_upgrade_receiver") { chain ->
                val action = (chain.args[1] as? android.content.Intent)?.action
                logger.debug("Blocked UpgradeReceiver.onReceive, action=$action")
                // no-op: swallow the SDAC hourly alarm
            }
        } catch (t: Throwable) {
            logger.error("Failed to hook UpgradeReceiver", t)
        }
    }
}
