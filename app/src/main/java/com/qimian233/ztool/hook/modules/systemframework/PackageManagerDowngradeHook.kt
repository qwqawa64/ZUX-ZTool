package com.qimian233.ztool.hook.modules.systemframework

import com.qimian233.ztool.data.keys.PreferenceKeys
import com.qimian233.ztool.data.keys.ScopeKeys
import com.qimian233.ztool.hook.base.SystemHookModule
import io.github.libxposed.api.XposedModuleInterface.SystemServerStartingParam

/**
 * 允许降级安装：短路 PackageManagerServiceUtils.checkDowngrade。
 *
 * Android 16 上该方法存在三个以 PackageInfoLite 结尾的 void 重载，
 * 全部拦截后 versionCode 比较不再抛出 INSTALL_FAILED_VERSION_DOWNGRADE。
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
                    // 直接跳过 versionCode 比较逻辑
                    null
                }
            }
            logger.info("Hooked PackageManagerServiceUtils.checkDowngrade x${overloads.size}")
        } catch (e: Throwable) {
            logger.error("Failed hooking checkDowngrade", e)
        }
    }
}
