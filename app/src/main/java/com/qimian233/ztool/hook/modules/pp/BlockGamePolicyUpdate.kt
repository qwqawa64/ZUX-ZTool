package com.qimian233.ztool.hook.modules.pp

import com.qimian233.ztool.data.keys.PreferenceKeys
import com.qimian233.ztool.data.keys.ScopeKeys
import com.qimian233.ztool.hook.base.AppHookModule
import io.github.libxposed.api.XposedModuleInterface

/**
 * 屏蔽 ZUI 性能服务 (com.zui.pp) 游戏/性能策略的云端 OTA 应用。
 *
 * WhatsNewBroadcastReceiver 接收 com.lenovo.tbengine (UDS 引擎) 推送的
 * android.action.targetcomponent.performance.dataupdate 广播，从 ContentProvider
 * 拉取 gamepolicy.zip，解压后按版本号覆盖 PerformanceConfig / GamePolicyConfig。
 * 吞掉 onReceive 后云端下发的策略包不再落盘，保留 system/etc 下的系统内置策略。
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
                // no-op：不接收云端策略包
            }
            logger.info("Hooked WhatsNewBroadcastReceiver.onReceive")
        } catch (t: Throwable) {
            logger.error("Failed to hook WhatsNewBroadcastReceiver.onReceive", t)
        }
    }
}
