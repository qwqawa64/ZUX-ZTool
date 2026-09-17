package com.qimian233.ztool.hook.modules.systemframework

import android.annotation.SuppressLint
import com.qimian233.ztool.data.keys.PreferenceKeys
import com.qimian233.ztool.data.keys.ScopeKeys
import com.qimian233.ztool.hook.base.SystemHookModule
import io.github.libxposed.api.XposedModuleInterface.SystemServerStartingParam

/**
 * Bypasses the resources.arsc storage restriction:
 * AssetManager.containsAllocatedTable always returns false,
 * allowing installation and loading of APKs with targetSdk R+ whose resources.arsc
 * is not packaged with the required uncompressed alignment.
 */
@SuppressLint("BlockedPrivateApi")
class PackageManagerArscBypassHook : SystemHookModule() {

    override fun getModuleName(): String = PreferenceKeys.PKG_MGR_BYPASS_ARSC_RESTRICTION.name

    override fun getTargetPackages(): Array<out String> = arrayOf(ScopeKeys.SYSTEM_SERVER.packageName)

    @Throws(Throwable::class)
    override fun handleSystemServerStarting(param: SystemServerStartingParam) {
        val classLoader = param.classLoader
        try {
            val assetManagerClass = classLoader.loadClass("android.content.res.AssetManager")
            val containsAllocatedTable = assetManagerClass
                .getDeclaredMethod("containsAllocatedTable")
            hookWithId(containsAllocatedTable, "pkgmgr_arsc_contains_allocated_table") { _ ->
                false
            }
            logger.info("Hooked AssetManager.containsAllocatedTable")
        } catch (e: Throwable) {
            logger.error("Failed hooking AssetManager.containsAllocatedTable", e)
        }
    }
}
