package com.qimian233.ztool.hook.modules.systemframework

import com.qimian233.ztool.data.keys.PreferenceKeys
import com.qimian233.ztool.data.keys.ScopeKeys
import com.qimian233.ztool.hook.base.SystemHookModule
import io.github.libxposed.api.XposedModuleInterface.SystemServerStartingParam

/**
 * 绕过 resources.arsc 存储限制：
 * AssetManager.containsAllocatedTable 恒返 false，
 * 允许 targetSdk R+ 且 resources.arsc 未按未压缩对齐要求打包的 APK 安装与加载。
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
