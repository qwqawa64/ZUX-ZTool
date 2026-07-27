package com.qimian233.ztool.hook.modules.systemFramework

import android.annotation.SuppressLint
import com.qimian233.ztool.hook.base.BaseHookModule
import io.github.libxposed.api.XposedModuleInterface
import java.lang.reflect.Method

@SuppressLint("PrivateApi")
class AllowRelativeAppLaunch: BaseHookModule() {
    override fun getModuleName(): String = "allow_relative_app_launch"

    override fun getTargetPackages(): Array<out String> = arrayOf("system")

    override fun handleLoadPackage(param: XposedModuleInterface.PackageLoadedParam?) {}

    override fun handleSystemServerStarting(param: XposedModuleInterface.SystemServerStartingParam) {
        val classLoader: ClassLoader = param.classLoader
        val targetClass: Class<*> = classLoader.loadClass($$"com.android.server.ZuiSecurityService$ZuiSecurityServiceBinder")
        val targetMethod: Method = findMethod(targetClass, "getRelativeAppStatus",
            String::class.java, String::class.java)
        xposed.hook(targetMethod).intercept { _ ->
            return@intercept 1
        }
    }
}