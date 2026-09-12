package com.qimian233.ztool.hook.modules.tbengine

import com.qimian233.ztool.data.keys.PreferenceKeys
import com.qimian233.ztool.data.keys.ScopeKeys
import com.qimian233.ztool.hook.base.AppHookModule
import io.github.libxposed.api.XposedModuleInterface

/**
 * 禁用 UDS 实时连接引擎 (com.lenovo.tbengine) 的 UPS 推送通道。
 *
 * 推送初始化入口（MyApplication onCreate / 设备开通完成）均经由
 * PushControls.initPushChannel() 与 registerInitReceiver()，
 * 拦截后不再注册 token，也不再向 push 服务器上报设备信息。
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
                // no-op：不初始化推送通道
            }
            val registerInitReceiver = findMethod(pushControlsClass, "registerInitReceiver")
            hookWithId(registerInitReceiver, "tbengine_push_init_receiver") { _ ->
                logger.debug("Blocked UPS push init receiver registration.")
                // no-op：不注册推送初始化接收器
            }
        } catch (t: Throwable) {
            logger.error("Failed to hook PushControls", t)
        }
    }
}
