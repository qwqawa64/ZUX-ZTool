package com.qimian233.ztool.hook.modules.mobiledesktop

import com.qimian233.ztool.data.keys.ScopeKeys
import com.qimian233.ztool.dexindex.base.DexIndexConstants
import com.qimian233.ztool.hook.base.AppHookModule
import com.qimian233.ztool.hook.base.DexIndexStore
import io.github.libxposed.api.XposedModuleInterface

/**
 * 测试 Hook — 禁用超级互联附近分享的 10 分钟自动关闭倒计时。
 *
 * 机制：FileUnionSwitchManager（当前版本混淆为
 * `com.motorola.motoaccount.sdk.se.c`）的 startCountDown 在附近分享开启后
 * 发送延迟消息 (what=1, delay=600000ms=10min)，handler 收到消息后调用
 * `MotoDiscoveryManager.B(false)` 自动关闭。此 Hook 将 startCountDown
 * 替换为空操作，阻止倒计时启动。
 *
 * 目标方法经过混淆，由 DexIndexer 以日志串 "startCountDown()" 定位，
 * 并回退到当前版本硬编码的类名/方法名。
 */
class DisableNearbyShareAutoOffHook : AppHookModule() {

    companion object {
        private val TARGET_PACKAGE = ScopeKeys.MOBILE_DESKTOP.packageName
        // 回退：当前版本（FileUnionSwitchManager）的硬编码类名和方法名
        private const val FALLBACK_CLASS = "com.motorola.motoaccount.sdk.se.c"
        private const val FALLBACK_METHOD = "b"
    }

    override fun getModuleName(): String = "disable_nearby_share_countdown"

    override fun getTargetPackages(): Array<String> = arrayOf(TARGET_PACKAGE)

    override fun handleLoadPackage(param: XposedModuleInterface.PackageLoadedParam) {
        val classLoader = param.defaultClassLoader

        // ── 从离线索引读取混淆类名/方法名 ─────────────────────────────
        val module = DexIndexStore.lookup(xposed, ScopeKeys.MOBILE_DESKTOP.packageName)
            ?.getAsJsonObject(DexIndexConstants.ModuleKeys.DISABLE_NEARBY_SHARE_COUNTDOWN)
        val targetClassName = module?.get(DexIndexConstants.Keys.TARGET_CLASS)
            ?.takeIf { !it.isJsonNull }?.asString ?: FALLBACK_CLASS
        val targetMethodName = module?.get(DexIndexConstants.Keys.TARGET_METHOD)
            ?.takeIf { !it.isJsonNull }?.asString ?: FALLBACK_METHOD

        // ── 安装 Hook ─────────────────────────────────────────────
        try {
            val targetClass = classLoader.loadClass(targetClassName)

            val targetMethod = targetClass.declaredMethods.firstOrNull { method ->
                method.name == targetMethodName
                        && method.parameterTypes.isEmpty()
                        && method.returnType == Void.TYPE
            }

            if (targetMethod == null) {
                logger.error(
                    "Could not find startCountDown method ($targetMethodName) in $targetClassName",
                    null
                )
                return
            }

            hookWithId(targetMethod, "target") { 
                logger.debug("startCountDown() intercepted — auto-off timer prevented.")
                null
            }
            logger.info("Installed hook for FileUnionSwitchManager.$targetMethodName()")
        } catch (e: ClassNotFoundException) {
            logger.error("$targetClassName (FileUnionSwitchManager) not found", e)
        } catch (t: Throwable) {
            logger.error("Failed to hook FileUnionSwitchManager.startCountDown()", t)
        }
    }
}
