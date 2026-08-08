package com.qimian233.ztool.hook.modules.setting

import com.qimian233.ztool.data.keys.ScopeKeys
import com.qimian233.ztool.hook.base.AppHookModule
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam

/**
 * 系统设置Hook模块
 * 修改系统设置应用的行为
 */
class OneVisionCompletion : AppHookModule() {
    override fun getModuleName(): String = "remove_blacklist"

    override fun getTargetPackages(): Array<String> = arrayOf(ScopeKeys.SETTINGS.packageName)

    override fun handleLoadPackage(param: PackageLoadedParam) {
        val classLoader = param.defaultClassLoader
        hookSettingsAppManager(classLoader)
    }

    private fun hookSettingsAppManager(classLoader: ClassLoader) {
        try {
            val m = classLoader
                .loadClass("com.lenovo.settings.onevision.horizontal.SettingsEmbeddingAppManager")
                .getDeclaredMethod("getZuiLandScapeShouldBeHideAppList")
            hookWithId(
                m,
                "hook_44"
            ) { arrayOfNulls<String>(0) }
            logger.info("Successfully hooked SettingsEmbeddingAppManager")
        } catch (t: Throwable) {
            logger.error("Failed to hook SettingsEmbeddingAppManager", t)
        }
    }
}
