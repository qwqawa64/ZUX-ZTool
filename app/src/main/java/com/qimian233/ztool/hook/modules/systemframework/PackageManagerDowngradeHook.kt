package com.qimian233.ztool.hook.modules.systemframework

import com.qimian233.ztool.data.keys.PreferenceKeys
import com.qimian233.ztool.data.keys.ScopeKeys
import com.qimian233.ztool.hook.base.SystemHookModule
import io.github.libxposed.api.XposedModuleInterface.SystemServerStartingParam

/**
 * Allows downgrade installation: short-circuits PackageManagerServiceUtils.checkDowngrade.
 *
 * On Android 16 this method has three void overloads ending with PackageInfoLite;
 * intercepting all of them stops versionCode comparisons from throwing
 * INSTALL_FAILED_VERSION_DOWNGRADE.
 */
class PackageManagerDowngradeHook : SystemHookModule() {

    override fun getModuleName(): String = PreferenceKeys.PKG_MGR_ALLOW_DOWNGRADE.name

    override fun getTargetPackages(): Array<out String> = arrayOf(ScopeKeys.SYSTEM_SERVER.packageName)

    @Throws(Throwable::class)
    override fun handleSystemServerStarting(param: SystemServerStartingParam) {
        val classLoader = param.classLoader
        try {
            val utilsClass = classLoader.loadClass("com.android.server.pm.PackageManagerServiceUtils")
            val overloads = utilsClass.declaredMethods.filter { method ->
                method.name == "checkDowngrade" &&
                    method.returnType == Void.TYPE &&
                    method.parameterTypes.lastOrNull()?.name == "android.content.pm.PackageInfoLite"
            }
            if (overloads.isEmpty()) {
                logger.warn("checkDowngrade overloads not found, skip downgrade bypass")
                return
            }
            overloads.forEachIndexed { index, method ->
                hookWithId(method, "pkgmgr_check_downgrade_$index") { _ ->
                    // Skip the versionCode comparison logic directly
                    null
                }
            }
            logger.info("Hooked PackageManagerServiceUtils.checkDowngrade x${overloads.size}")
        } catch (e: Throwable) {
            logger.error("Failed hooking checkDowngrade", e)
        }
    }
}
