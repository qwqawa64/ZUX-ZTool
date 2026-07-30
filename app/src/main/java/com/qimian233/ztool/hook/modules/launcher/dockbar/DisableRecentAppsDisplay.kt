package com.qimian233.ztool.hook.modules.launcher.dockbar

import android.annotation.SuppressLint
import com.qimian233.ztool.hook.base.BaseHookModule
import io.github.libxposed.api.XposedModuleInterface
import java.lang.reflect.Method

@SuppressLint("PrivateApi")
class DisableRecentAppsDisplay: BaseHookModule() {
    override fun getModuleName(): String = "test_hook"

    override fun getTargetPackages(): Array<out String> = arrayOf("com.zui.launcher")

    override fun handleLoadPackage(param: XposedModuleInterface.PackageLoadedParam) {
        val classLoader: ClassLoader = param.defaultClassLoader
        val featureEvaluatorClass: Class<*> = classLoader.loadClass("com.android.launcher3.taskbar.customization.TaskbarFeatureEvaluator")
        val recentsEnabledGetter: Method = findMethod(featureEvaluatorClass, "isRecentsEnabled")
        hookWithId(recentsEnabledGetter, "disable_recents") { return@hookWithId false }
    }
}