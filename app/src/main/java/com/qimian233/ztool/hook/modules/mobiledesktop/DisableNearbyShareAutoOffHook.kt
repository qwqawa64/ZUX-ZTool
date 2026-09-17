package com.qimian233.ztool.hook.modules.mobiledesktop

import com.qimian233.ztool.data.keys.ScopeKeys
import com.qimian233.ztool.dexindex.base.DexIndexConstants
import com.qimian233.ztool.hook.base.AppHookModule
import com.qimian233.ztool.hook.base.DexIndexStore
import io.github.libxposed.api.XposedModuleInterface

/**
 * Test Hook — disables the 10-minute auto-off countdown of nearby sharing
 * in Super Interconnect (FileUnion).
 *
 * Mechanism: startCountDown of FileUnionSwitchManager sends a
 * delayed 10-minute message after nearby sharing is enabled; when the handler
 * receives it, it turns nearby sharing off. This Hook replaces startCountDown
 * with a no-op to prevent the countdown from starting.
 *
 * The target method is obfuscated; it is located by the DexIndexer via the
 * log string "startCountDown()", falling back to the class/method names
 * hardcoded for the current version.
 */
class DisableNearbyShareAutoOffHook : AppHookModule() {

    companion object {
        private val TARGET_PACKAGE = ScopeKeys.MOBILE_DESKTOP.packageName
        // Fallback: hardcoded class/method names of FileUnionSwitchManager in the current version
        private const val FALLBACK_CLASS = "com.motorola.motoaccount.sdk.se.c"
        private const val FALLBACK_METHOD = "b"
    }

    override fun getModuleName(): String = "disable_nearby_share_countdown"

    override fun getTargetPackages(): Array<String> = arrayOf(TARGET_PACKAGE)

    override fun handleLoadPackage(param: XposedModuleInterface.PackageLoadedParam) {
        val classLoader = param.defaultClassLoader

        val module = DexIndexStore.lookup(xposed, ScopeKeys.MOBILE_DESKTOP.packageName)
            ?.getAsJsonObject(DexIndexConstants.ModuleKeys.DISABLE_NEARBY_SHARE_COUNTDOWN)
        val targetClassName = module?.get(DexIndexConstants.Keys.TARGET_CLASS)
            ?.takeIf { !it.isJsonNull }?.asString ?: FALLBACK_CLASS
        val targetMethodName = module?.get(DexIndexConstants.Keys.TARGET_METHOD)
            ?.takeIf { !it.isJsonNull }?.asString ?: FALLBACK_METHOD

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
