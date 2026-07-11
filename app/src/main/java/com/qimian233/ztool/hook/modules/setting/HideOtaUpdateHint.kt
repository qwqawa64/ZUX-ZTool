package com.qimian233.ztool.hook.modules.setting

import android.content.ContentResolver
import android.provider.Settings.Secure
import com.qimian233.ztool.hook.base.BaseHookModule
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam
import java.lang.reflect.Method

/**
 * Hides the red OTA update hint in Settings while keeping the OTA entry usable.
 */
class HideOtaUpdateHint : BaseHookModule() {
    override fun getModuleName(): String = "hide_ota_update_hint"

    override fun getTargetPackages(): Array<String> = arrayOf(TARGET_PACKAGE)

    @Throws(Throwable::class)
    override fun handleLoadPackage(param: PackageLoadedParam) {
        handlePreferenceRead()
        handleNotificationCreate(param)
    }

    fun handlePreferenceRead() {
        try {
            // getInt(ContentResolver, String, int)
            val getInt3 : Method = Secure::class.java.getDeclaredMethod(
                "getInt",
                ContentResolver::class.java,
                String::class.java,
                Int::class.javaPrimitiveType
            )
            this.xposed.hook(getInt3).intercept { chain ->
                if (OTA_NEW_VERSION_FOUND == chain.getArg(1)) {
                    return@intercept 0
                }
                chain.proceed()
            }

            // getInt(ContentResolver, String)
            val getInt2 = Secure::class.java.getDeclaredMethod(
                "getInt", ContentResolver::class.java, String::class.java
            )
            this.xposed.hook(getInt2).intercept { chain ->
                if (OTA_NEW_VERSION_FOUND == chain.getArg(1)) {
                    return@intercept 0
                }
                chain.proceed()
            }

            log("Hooked Settings OTA new-version flag reads")
        } catch (t: Throwable) {
            logError("Failed to hook Settings OTA new-version flag reads", t)
        }
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

    companion object {
        private const val TARGET_PACKAGE = "com.android.settings"
        private const val OTA_NEW_VERSION_FOUND = "lenovo_ota_new_version_found"
    }
}
