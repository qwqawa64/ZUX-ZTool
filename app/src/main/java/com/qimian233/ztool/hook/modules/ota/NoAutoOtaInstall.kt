package com.qimian233.ztool.hook.modules.ota

import com.qimian233.ztool.hook.base.AppHookModule
import io.github.libxposed.api.XposedModuleInterface
import java.lang.reflect.Method

class NoAutoOtaInstall : AppHookModule() {
    override fun getModuleName(): String = "no_auto_ota_install"

    override fun getTargetPackages(): Array<out String> = arrayOf("com.lenovo.ota")

    override fun handleLoadPackage(param: XposedModuleInterface.PackageLoadedParam) {
        val cl: ClassLoader = param.defaultClassLoader
        val otaPolicyClass: Class<*> = cl.loadClass("com.lenovo.row.ota.core.c.policy.OtaPolicy")
        try {
            val autoInstallGetterMethod: Method = findMethod(otaPolicyClass, "getmSettingNormalAutoInstall")
            hookWithId(autoInstallGetterMethod, "auto_install_getter") {  _ ->
                logger.debug("Intercepted auto install getter and force it to return false.")
                false
            }
        } catch (e: NoSuchMethodException) {
            logger.error("Unable to find method: ", e)
        }
        try {
            val autoDownloadGetterMethod: Method = findMethod(otaPolicyClass, "getmSettingNormalAutoDownload")
            hookWithId(autoDownloadGetterMethod, "auto_download_getter") {  _ ->
                logger.debug("Intercepted auto download getter and force it to return false.")
                false
            }
        } catch (e: NoSuchMethodException) {
            logger.error("Unable to find method: ", e)
        }
        try {
            val autoInstallSetterMethod: Method = findMethod(otaPolicyClass, "setmSettingNormalAutoInstall",
                Boolean::class.java)
            hookWithId(autoInstallSetterMethod, "auto_install_setter") {  chain ->
                logger.debug("Intercepted auto install setter and modify its argument to always false")
                chain.proceed(arrayOf(false))
            }
        } catch (e: NoSuchMethodException) {
            logger.error("Unable to find method: ", e)
        }
        try {
            val autoDownloadSetterMethod: Method = findMethod(otaPolicyClass, "setmSettingNormalAutoDownload",
                Boolean::class.java)
            hookWithId(autoDownloadSetterMethod, "auto_download_setter") {  chain ->
                logger.debug("Intercepted auto download setter and modify its argument to always false")
                chain.proceed(arrayOf(false))
            }
        } catch (e: NoSuchMethodException) {
            logger.error("Unable to find method: ", e)
        }
    }
}