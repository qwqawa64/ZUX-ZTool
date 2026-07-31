package com.qimian233.ztool.hook.modules.mobiledesktop

import com.qimian233.ztool.hook.base.AppHookModule
import com.qimian233.ztool.hook.base.DexKitHelper
import io.github.libxposed.api.XposedModuleInterface
import java.lang.reflect.Modifier

/**
 * 测试 Hook — 禁用超级互联附近分享的 10 分钟自动关闭倒计时。
 *
 * 机制：[FileUnionSwitchManager.startCountDown] (混淆后为 `ra.c.q()`)
 * 在附近分享开启后发送延迟消息 (what=1, delay=600000ms=10min)，
 * handler 收到消息后调用 `c0.setNearbyShareStatus(false)` 自动关闭。
 * 此 Hook 将 startCountDown 替换为空操作，阻止倒计时启动。
 *
 * 目标方法经过混淆，因此使用 DEXKit 按签名动态匹配，
 * 并回退到硬编码的类名 `ra.c` 和方法名 `q`。
 */
class DisableNearbyShareAutoOffHook : AppHookModule() {

    companion object {
        private const val TARGET_PACKAGE = "com.motorola.mobiledesktop"
        // DEXKit 搜索的混淆包名
        private const val SEARCH_PACKAGE = "ra"
        // 回退：硬编码的类名和方法名
        private const val FALLBACK_CLASS = "ra.c"
        private const val FALLBACK_METHOD = "q"
    }

    override fun getModuleName(): String = "disable_nearby_share_countdown"

    override fun getTargetPackages(): Array<String> = arrayOf(TARGET_PACKAGE)

    override fun handleLoadPackage(param: XposedModuleInterface.PackageLoadedParam) {
        val classLoader = param.defaultClassLoader

        // ── DEXKit：通过 ApplicationInfo.sourceDir 获取桥 ─────────────
        val bridge = DexKitHelper.getBridgeForApp(param.applicationInfo)

        var targetClassName: String? = null
        var targetMethodName: String? = null

        if (bridge != null) {
            try {
                val classData = bridge.findClass {
                    searchPackages(SEARCH_PACKAGE)
                    matcher {
                        methods {
                            // startCountDown: () → void
                            add {
                                paramTypes()
                                returnType = "void"
                            }
                            // 单例工厂: static (Context) → ra.c
                            add {
                                modifiers = Modifier.STATIC or Modifier.PUBLIC
                                paramTypes("android.content.Context")
                            }
                        }
                    }
                }.singleOrNull()

                if (classData != null) {
                    targetClassName = classData.name
                    for (md in classData.methods) {
                        val params = md.paramTypeNames
                        if (params.isEmpty() && md.returnTypeName == "void") {
                            targetMethodName = md.name
                            break
                        }
                    }
                }
            } catch (_: Throwable) {
                logger.warn("DEXKit discovery failed, falling back to hardcoded names")
            }
        }

        if (targetClassName == null) targetClassName = FALLBACK_CLASS
        if (targetMethodName == null) targetMethodName = FALLBACK_METHOD

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
