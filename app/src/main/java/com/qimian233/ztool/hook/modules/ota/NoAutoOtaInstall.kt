package com.qimian233.ztool.hook.modules.ota

import com.qimian233.ztool.hook.base.BaseHookModule
import io.github.libxposed.api.XposedModuleInterface
import java.lang.reflect.Method

class NoAutoOtaInstall : BaseHookModule() {
    override fun getModuleName(): String = "no_auto_ota_install"

    override fun getTargetPackages(): Array<out String> = arrayOf("com.lenovo.ota")

    override fun handleLoadPackage(param: XposedModuleInterface.PackageLoadedParam) {
        val cl: ClassLoader = param.defaultClassLoader
        val otaPolicyClass: Class<*> = cl.loadClass("com.lenovo.row.ota.core.c.policy.OtaPolicy")
        val autoInstallGetterMethod: Method = findMethod(otaPolicyClass, "getmSettingNormalAutoInstall")
        val autoInstallSetterMethod: Method = findMethod(otaPolicyClass, "setmSettingNormalAutoInstall",
            Boolean::class.java)
        xposed.hook(autoInstallGetterMethod).intercept { _ ->
            return@intercept false
        }
        xposed.hook(autoInstallSetterMethod).intercept { chain ->
            return@intercept chain.proceed(arrayOf(false))
        }
    }
}