package com.qimian233.ztool.hook.modules.systemframework

import android.annotation.SuppressLint
import com.qimian233.ztool.data.keys.ScopeKeys
import com.qimian233.ztool.hook.base.SystemHookModule
import io.github.libxposed.api.XposedModuleInterface
import java.lang.reflect.Method

/**
 * Skips the ZUI relative app launch permission check, always allowing relative app launch.
 *
 * Hooks com.android.server.ZuiSecurityService.ZuiSecurityServiceBinder.getRelativeAppStatus
 * so it always returns 1 (allowed).
 */
@SuppressLint("PrivateApi")
class AllowRelativeAppLaunch: SystemHookModule() {
    override fun getModuleName(): String = "allow_relative_app_launch"

    override fun getTargetPackages(): Array<out String> = arrayOf(ScopeKeys.SYSTEM_SERVER.packageName)

    override fun handleSystemServerStarting(param: XposedModuleInterface.SystemServerStartingParam) {
        val classLoader: ClassLoader = param.classLoader

        val securityBinderClass: Class<*> = classLoader.loadClass(
            $$"com.android.server.ZuiSecurityService$ZuiSecurityServiceBinder")
        val getStatusMethod: Method = findMethod(securityBinderClass, "getRelativeAppStatus",
            String::class.java, String::class.java)
        hookWithId(getStatusMethod, "relative_app_status") { _ ->
            1
        }
    }
}
