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
        try {
            val autoInstallGetterMethod: Method = findMethod(otaPolicyClass, "getmSettingNormalAutoInstall")
            xposed.hook(autoInstallGetterMethod).intercept { _ ->
                log("Intercepted auto install getter and force it to return false.")
                return@intercept false
            }
        } catch (e: NoSuchMethodException) {
            logError("Unable to find method: ", e)
        }
        try {
            val autoDownloadGetterMethod: Method = findMethod(otaPolicyClass, "getmSettingNormalAutoDownload")
            xposed.hook(autoDownloadGetterMethod).intercept { _ ->
                log("Intercepted auto download getter and force it to return false.")
                return@intercept false
            }
        } catch (e: NoSuchMethodException) {
            logError("Unable to find method: ", e)
        }
        try {
            val autoInstallSetterMethod: Method = findMethod(otaPolicyClass, "setmSettingNormalAutoInstall",
                Boolean::class.java)
            xposed.hook(autoInstallSetterMethod).intercept { chain ->
                log("Intercepted auto install setter and modify its argument to always false")
                return@intercept chain.proceed(arrayOf(false))
            }
        } catch (e: NoSuchMethodException) {
            logError("Unable to find method: ", e)
        }
        try {
            val autoDownloadSetterMethod: Method = findMethod(otaPolicyClass, "setmSettingNormalAutoDownload",
                Boolean::class.java)
            xposed.hook(autoDownloadSetterMethod).intercept { chain ->
                log("Intercepted auto download setter and modify its argument to always false")
                return@intercept chain.proceed(arrayOf(false))
            }
        } catch (e: NoSuchMethodException) {
            logError("Unable to find method: ", e)
        }
    }
}