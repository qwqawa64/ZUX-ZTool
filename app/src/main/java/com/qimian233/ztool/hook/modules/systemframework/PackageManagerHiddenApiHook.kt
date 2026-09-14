package com.qimian233.ztool.hook.modules.systemframework

import android.content.pm.ApplicationInfo
import com.qimian233.ztool.data.keys.PreferenceKeys
import com.qimian233.ztool.data.keys.ScopeKeys
import com.qimian233.ztool.hook.base.SystemHookModule
import io.github.libxposed.api.XposedModuleInterface.SystemServerStartingParam

/**
 * 允许系统应用使用隐藏 API：
 * ApplicationInfo.isPackageWhitelistedForHiddenApis 对系统/
 * 更新系统应用恒真，即使其签名并非平台签名。
 */
class PackageManagerHiddenApiHook : SystemHookModule() {

    override fun getModuleName(): String =
        PreferenceKeys.PKG_MGR_ALLOW_HIDDEN_APIS_SYSTEM_APPS.name

    override fun getTargetPackages(): Array<out String> = arrayOf(ScopeKeys.SYSTEM_SERVER.packageName)

    @Throws(Throwable::class)
    override fun handleSystemServerStarting(param: SystemServerStartingParam) {
        val classLoader = param.classLoader
        try {
            val applicationInfoClass = classLoader.loadClass("android.content.pm.ApplicationInfo")
            val isWhitelisted =
                applicationInfoClass.getDeclaredMethod("isPackageWhitelistedForHiddenApis")
            hookWithId(isWhitelisted, "pkgmgr_hidden_api_whitelist") { chain ->
                val info = chain.thisObject as? ApplicationInfo
                if (info != null && (info.flags and ApplicationInfo.FLAG_SYSTEM != 0 ||
                        info.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP != 0)
                ) {
                    true
                } else {
                    chain.proceed()
                }
            }
            logger.info("Hooked ApplicationInfo.isPackageWhitelistedForHiddenApis")
        } catch (e: Throwable) {
            logger.error("Failed hooking isPackageWhitelistedForHiddenApis", e)
        }
    }
}
