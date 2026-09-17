package com.qimian233.ztool.hook.modules.tbengine

import com.qimian233.ztool.data.keys.PreferenceKeys
import com.qimian233.ztool.data.keys.ScopeKeys
import com.qimian233.ztool.hook.base.AppHookModule
import io.github.libxposed.api.XposedModuleInterface

/**
 * Disables the UPS push channel of the UDS real-time connection engine
 * (com.lenovo.tbengine).
 *
 * Push initialization entry points (MyApplication onCreate / device activation
 * completion) all go through PushControls.initPushChannel() and
 * registerInitReceiver(); after interception no token is registered and no
 * device info is reported to the push server.
 */
class DisableTbEnginePush : AppHookModule() {

    override fun getModuleName(): String = PreferenceKeys.DISABLE_TB_ENGINE_PUSH.name

    override fun getTargetPackages(): Array<out String?>? = arrayOf(ScopeKeys.TB_ENGINE.packageName)

    @Throws(Throwable::class)
    override fun handleLoadPackage(param: XposedModuleInterface.PackageLoadedParam) {
        val classLoader = param.defaultClassLoader

        try {
            val pushControlsClass = classLoader.loadClass(
                "com.lenovo.tbengine.core.push.PushControls"
            )
            val initPushChannel = findMethod(pushControlsClass, "initPushChannel")
            hookWithId(initPushChannel, "tbengine_push_init_channel") { _ ->
                logger.debug("Blocked UPS push channel initialization.")
                // no-op: do not initialize the push channel
            }
            val registerInitReceiver = findMethod(pushControlsClass, "registerInitReceiver")
            hookWithId(registerInitReceiver, "tbengine_push_init_receiver") { _ ->
                logger.debug("Blocked UPS push init receiver registration.")
                // no-op: do not register the push init receiver
            }
        } catch (t: Throwable) {
            logger.error("Failed to hook PushControls", t)
        }
    }
}
