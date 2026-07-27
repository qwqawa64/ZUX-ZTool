package com.qimian233.ztool.hook.modules.ota

import android.content.Context
import com.qimian233.ztool.hook.base.BaseHookModule
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam
import java.lang.reflect.Method

class HideOtaNotifications: BaseHookModule() {
    override fun getModuleName(): String = "hide_ota_notifications"

    override fun getTargetPackages(): Array<String> = arrayOf("com.lenovo.ota")

    override fun handleLoadPackage(param: PackageLoadedParam) {
        handleNotificationCreate(param)
        handleRedDotCreate(param)
        handleSecureSettingsWrite(param)
    }

    fun handleNotificationCreate(param: PackageLoadedParam) {
        try {
            log("Hooking OTA notification create method")
            val cl: ClassLoader = param.defaultClassLoader
            val targetClass: Class<*> = cl.loadClass("com.lenovo.row.ota.core.d.notification.NotificationCenter")
            val targetMethod: Method = findMethod(targetClass, "showNewVersionNotification")
            hookWithId(targetMethod, "target_1") {  _ ->
                null
            }
        } catch (th: Throwable) {
            logError("Failed to hook OTA update notification creation method!", th)
        }
    }

    fun handleRedDotCreate(param: PackageLoadedParam) {
        try {
            val cl: ClassLoader = param.defaultClassLoader
            val targetClass: Class<*> =
                cl.loadClass("com.lenovo.row.ota.core.a.workflow.WorkFlowManager")
            val targetMethod: Method =
                findMethod(targetClass, "reddot", Context::class.java, Int::class.javaPrimitiveType)
            hookWithId(targetMethod, "target_2") {  chain ->
                try {
                    val argList = chain.args.toMutableList()
                    if (argList[1] != 0) {
                        argList[1] = 0
                        log("Modified red dot count to 0!")
                    }
                    chain.proceed(argList.toTypedArray())
                } catch (th: Throwable) {
                    logError("Failed to set red dot count to 0!", th)
                    chain.proceed()
                }
            }
        } catch (th: Throwable) {
            logError("Failed to block reddot creation process!", th)
        }
    }

    fun handleSecureSettingsWrite(param: PackageLoadedParam) {
        try {
            val cl: ClassLoader = param.defaultClassLoader
            val targetClass: Class<*> =
                cl.loadClass("com.lenovo.ota.utils.SystemSettings")
            val targetMethod: Method = findMethod(
                targetClass,
                "putSecureSetting",
                Context::class.java,
                String::class.java,
                Any::class.java
            )
            hookWithId(targetMethod, "target_3") {  chain ->
                val argList = chain.args.toMutableList()
                val key = argList[1] as? String
                if (key == "lenovo_ota_new_version_found") {
                    val value = argList[2]
                    if (value != null && (value as Int) == 1) {
                        log("Set putSecureSetting lenovo_ota_new_version_found from 1 to 0")
                        argList[2] = 0
                    }
                }
                chain.proceed(argList.toTypedArray())
            }
        } catch (th: Throwable) {
            logError("Failed to hook SystemSettings.putSecureSetting!", th)
        }
    }
}
