package com.qimian233.ztool.hook.modules.setting

import com.qimian233.ztool.data.ScopeKeys
import com.qimian233.ztool.data.PreferenceKeys
import com.qimian233.ztool.hook.base.AppHookModule
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam

/**
 * 测试 Hook：拦截 LenovoUtils 区域判断方法，仅在 LocaleListEditor 调用场景生效。
 *
 * 当调用来自 com.android.settings.localepicker.LocaleListEditor 时：
 * - com.lenovo.common.utils.LenovoUtils.isRowVersion 返回 true
 * - com.lenovo.common.utils.LenovoUtils.isPrcVersion 返回 false
 *
 * 其他调用场景走原始逻辑，避免对 Settings 其他页面产生副作用。
 * 通过调用栈检查精确命中目标，语言页面使用频率极低，开销可忽略。
 *
 * 模块名使用 "test_hook" 自动启用，无需前端开关。
 */
class LocaleListEditorHook : AppHookModule() {

    companion object {
        private const val TARGET_CLASS = "com.android.settings.localepicker.LocaleListEditor"
    }

    override fun getModuleName(): String = PreferenceKeys.ALLOW_ADD_LANGUAGE.name

    override fun getTargetPackages(): Array<String> = arrayOf(ScopeKeys.SETTINGS.packageName)

    private fun isFromLocaleListEditor(): Boolean =
        Throwable().stackTrace.any { it.className == TARGET_CLASS }

    override fun handleLoadPackage(param: PackageLoadedParam) {
        val cl = param.defaultClassLoader
        try {
            val lenovoUtils = cl.loadClass("com.lenovo.common.utils.LenovoUtils")

            val isRowMethod = lenovoUtils.getDeclaredMethod("isRowVersion")
            hookWithId(isRowMethod, "locale_row_version") { chain ->
                if (isFromLocaleListEditor()) true else chain.proceed()
            }

            val isPrcMethod = lenovoUtils.getDeclaredMethod("isPrcVersion")
            hookWithId(isPrcMethod, "locale_prc_version") { chain ->
                if (isFromLocaleListEditor()) false else chain.proceed()
            }

            logger.info("LocaleListEditorHook installed: isRowVersion→true, isPrcVersion→false (LocaleListEditor only)")
        } catch (t: Throwable) {
            logger.error("LocaleListEditorHook failed", t)
        }
    }
}
