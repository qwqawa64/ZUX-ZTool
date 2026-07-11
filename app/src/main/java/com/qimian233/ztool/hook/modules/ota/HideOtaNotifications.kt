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
    }

    fun handleNotificationCreate(param: PackageLoadedParam) {
        try {
            log("Hooking OTA notification create method")
            val cl: ClassLoader = param.defaultClassLoader
            val targetClass: Class<*> = cl.loadClass("com.lenovo.row.ota.core.d.notification.NotificationCenter")
            val targetMethod: Method = findMethod(targetClass, "showNewVersionNotification")
            xposed.hook(targetMethod).intercept { _ ->
                return@intercept null
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
            xposed.hook(targetMethod).intercept { chain ->
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
}