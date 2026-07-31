package com.qimian233.ztool.hook.modules.launcher.dockbar

import android.annotation.SuppressLint
import android.content.Context
import com.qimian233.ztool.hook.base.BaseHookModule
import io.github.libxposed.api.XposedModuleInterface
import java.lang.reflect.Method

@SuppressLint("PrivateApi")
class DisableRecentAppsDisplay: BaseHookModule() {
    override fun getModuleName(): String = "disable_recent_apps_display"

    override fun getTargetPackages(): Array<out String> = arrayOf("com.zui.launcher")

    override fun handleLoadPackage(param: XposedModuleInterface.PackageLoadedParam) {
        val classLoader: ClassLoader = param.defaultClassLoader
        // Hook 1: handles Android launcher3 taskbar
        val utilsClass: Class<*> = classLoader.loadClass("com.android.launcher3.taskbar.Utilities")
        val tbRecentAppEnableGetter: Method = findMethod(utilsClass, "isTbRecentUsedAppEnable",
            Context::class.java)
        hookWithId(tbRecentAppEnableGetter, "hook_utils") {
            return@hookWithId false
        }

        // Hook 2: handles static method setRecentUsedShow in com.zui.launcher.uiextend.ZuiHotseat
        val hotSeatClass: Class<*> = classLoader.loadClass("com.zui.launcher.uiextend.ZuiHotseat")
        val recentShowSetter: Method = findMethod(hotSeatClass, "setRecentUsedShow", Boolean::class.java)
        hookWithId(recentShowSetter, "hook_recent_show_setter") { chain ->
            val argList = chain.args.toMutableList()
            argList[0] = false
            return@hookWithId chain.proceed(argList.toTypedArray())
        }
    }
}