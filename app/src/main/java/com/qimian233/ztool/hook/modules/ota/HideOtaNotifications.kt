package com.qimian233.ztool.hook.modules.ota

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.os.Bundle
import com.qimian233.ztool.hook.base.BaseHookModule
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam
import java.lang.reflect.Method

class HideOtaNotifications: BaseHookModule() {
    override fun getModuleName(): String = "hide_ota_notifications"

    override fun getTargetPackages(): Array<String> = arrayOf("com.lenovo.ota")

    override fun handleLoadPackage(param: PackageLoadedParam) {
        handleNotificationCreate(param)
        handleRedDotCreate(param)
        handleBadgeContentProviderCall(param)
        handleSecureSettingsWrite(param)
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

    /**
     * Hook ContentResolver.call() 作为底层兜底，拦截所有对 com.android.badge
     * ContentProvider 的调用，强制将 app_badge_count 设为 0。
     */
    fun handleBadgeContentProviderCall(param: PackageLoadedParam) {
        try {
            val targetMethod: Method = findMethod(
                ContentResolver::class.java,
                "call",
                Uri::class.java,
                String::class.java,
                String::class.java,
                Bundle::class.java
            )
            xposed.hook(targetMethod).intercept { chain ->
                val uri = chain.args[0] as? Uri
                if (uri != null && uri.toString().contains("com.android.badge")) {
                    val extras = chain.args[3] as? Bundle
                    if (extras != null && extras.containsKey("app_badge_count")) {
                        val count = extras.getInt("app_badge_count")
                        if (count != 0) {
                            extras.putInt("app_badge_count", 0)
                            log("ContentResolver.call: forced app_badge_count 0 (was $count)")
                        }
                    }
                }
                chain.proceed()
            }
        } catch (th: Throwable) {
            logError("Failed to hook ContentResolver.call for badge provider!", th)
        }
    }

    /**
     * Hook SystemSettings.putSecureSetting() 拦截 lenovo_ota_new_version_found 写入。
     * Launcher 通过读取这个 Settings.Secure 键来决定是否显示 OTA 角标，
     * 这是角标显示的真正根因。
     */
    fun handleSecureSettingsWrite(param: PackageLoadedParam) {
        try {
            val cl: ClassLoader = param.defaultClassLoader
            val targetClass: Class<*> =
                cl.loadClass("com.lenovo.ota.utils.SystemSettings")
            // putSecureSetting(Context, String, Object) — 类型擦除后 T 变 Object
            val targetMethod: Method = findMethod(
                targetClass,
                "putSecureSetting",
                Context::class.java,
                String::class.java,
                Any::class.java
            )
            xposed.hook(targetMethod).intercept { chain ->
                val key = chain.args[1] as? String
                if (key == "lenovo_ota_new_version_found") {
                    val value = chain.args[2]
                    if (value != null && (value as Int) == 1) {
                        log("Blocked putSecureSetting lenovo_ota_new_version_found=1")
                        return@intercept null
                    }
                }
                chain.proceed()
            }
        } catch (th: Throwable) {
            logError("Failed to hook SystemSettings.putSecureSetting!", th)
        }
    }
}
