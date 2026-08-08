package com.qimian233.ztool.hook.modules.launcher.dockbar

import android.view.View
import com.qimian233.ztool.data.keys.ScopeKeys
import com.qimian233.ztool.data.keys.PreferenceKeys
import com.qimian233.ztool.hook.base.AppHookModule
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam

class DisableDockBar : AppHookModule() {
    override fun getModuleName(): String = PreferenceKeys.DISABLE_DOCK_BAR.name

    override fun getTargetPackages(): Array<String> = arrayOf(ScopeKeys.LAUNCHER.packageName)

    @Throws(Throwable::class)
    override fun handleLoadPackage(param: PackageLoadedParam) {
        val classLoader = param.defaultClassLoader

        logger.info("开始Hook ZUI Launcher Dock栏")

        try {
            val zuiHotSeatClass = classLoader.loadClass("com.zui.launcher.uiextend.ZuiHotseat")
            val setVisibilityMethod =
                findMethod(zuiHotSeatClass, "setVisibility",
                    Int::class.javaPrimitiveType)
            hookWithId(
                setVisibilityMethod,
                "set_visibility"
            ) { chain ->
                val visibility = chain.getArg(0) as Int
                if (visibility == View.VISIBLE) {
                    // Block setting visibility to VISIBLE, effectively hiding the dock
                    return@hookWithId null
                }
                chain.proceed()
            }
            logger.info("ZuiHotseat.setVisibility Hook完成")
        } catch (t: Throwable) {
            logger.error("Hook ZuiHotseat.setVisibility失败", t)
        }
    }

}
