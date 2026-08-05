package com.qimian233.ztool.hook.modules.setting

import com.qimian233.ztool.hook.base.AppHookModule
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam

/**
 * 测试 Hook：拦截 LenovoUtils 区域判断方法。
 *
 * 强制 [com.lenovo.common.utils.LenovoUtils.isRowVersion] 返回 true，
 * [com.lenovo.common.utils.LenovoUtils.isPrcVersion] 返回 false，
 * 从而影响 LocaleListEditor 等组件的区域行为。
 *
 * 模块名使用 "test_hook" 自动启用，无需前端开关。
 */
class LocaleListEditorHook : AppHookModule() {

    override fun getModuleName(): String = "test_hook"

    override fun getTargetPackages(): Array<String> = arrayOf("com.android.settings")

    override fun handleLoadPackage(param: PackageLoadedParam) {
        val cl = param.defaultClassLoader
        try {
            val lenovoUtils = cl.loadClass("com.lenovo.common.utils.LenovoUtils")

            // Hook isRowVersion() → 强制返回 true
            val isRowMethod = lenovoUtils.getDeclaredMethod("isRowVersion")
            hookWithId(isRowMethod, "locale_row_version") {
                true
            }

            // Hook isPrcVersion() → 强制返回 false
            val isPrcMethod = lenovoUtils.getDeclaredMethod("isPrcVersion")
            hookWithId(isPrcMethod, "locale_prc_version") {
                false
            }

            logger.info("LocaleListEditorHook: isRowVersion→true, isPrcVersion→false")
        } catch (t: Throwable) {
            logger.error("LocaleListEditorHook failed", t)
        }
    }
}
