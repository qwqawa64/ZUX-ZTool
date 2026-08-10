package com.qimian233.ztool.hook.modules.mobiledesktop

import com.qimian233.ztool.data.keys.ScopeKeys
import com.qimian233.ztool.dexindex.base.DexIndexConstants
import com.qimian233.ztool.hook.base.AppHookModule
import com.qimian233.ztool.hook.base.DexIndexStore
import io.github.libxposed.api.XposedModuleInterface

/**
 * 测试 Hook — 禁用超级互联附近分享的 10 分钟自动关闭倒计时。
 *
 * 机制：FileUnionSwitchManager.startCountDown (混淆后为 `ra.c.q()`)
 * 在附近分享开启后发送延迟消息 (what=1, delay=600000ms=10min)，
 * handler 收到消息后调用 `c0.setNearbyShareStatus(false)` 自动关闭。
 * 此 Hook 将 startCountDown 替换为空操作，阻止倒计时启动。
 *
 * 目标方法经过混淆，因此使用 DEXKit 按签名动态匹配，
 * 并回退到硬编码的类名 `ra.c` 和方法名 `q`。
 */
class DisableNearbyShareAutoOffHook : AppHookModule() {

    companion object {
        private val TARGET_PACKAGE = ScopeKeys.MOBILE_DESKTOP.packageName
        // 回退：硬编码的类名和方法名
        private const val FALLBACK_CLASS = "ra.c"
        private const val FALLBACK_METHOD = "q"
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

        val finalClassName = targetClassName
        val finalMethodName = targetMethodName

        // ── 安装 Hook ─────────────────────────────────────────────
        try {
            val targetClass = classLoader.loadClass(finalClassName)

            val targetMethod = targetClass.declaredMethods.firstOrNull { method ->
                method.name == finalMethodName
                        && method.parameterTypes.isEmpty()
                        && method.returnType == Void.TYPE
            }

            if (targetMethod == null) {
                logger.error(
                    "Could not find startCountDown method ($finalMethodName) in $finalClassName",
                    null
                )
                return
            }

            hookWithId(targetMethod, "target") { 
                logger.debug("startCountDown() intercepted — auto-off timer prevented.")
                null
            }
            logger.info("Installed hook for FileUnionSwitchManager.$finalMethodName()")
        } catch (e: ClassNotFoundException) {
            logger.error("$finalClassName (FileUnionSwitchManager) not found", e)
        } catch (t: Throwable) {
            logger.error("Failed to hook FileUnionSwitchManager.startCountDown()", t)
        }
    }
}
