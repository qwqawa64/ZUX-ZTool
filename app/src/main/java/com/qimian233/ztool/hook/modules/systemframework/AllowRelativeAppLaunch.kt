package com.qimian233.ztool.hook.modules.systemframework

import android.annotation.SuppressLint
import com.qimian233.ztool.data.keys.ScopeKeys
import com.qimian233.ztool.hook.base.SystemHookModule
import io.github.libxposed.api.XposedModuleInterface
import java.lang.reflect.Method

/**
 * 跳过 ZUI 关联启动许可检查，始终允许关联启动。
 *
 * Hook [com.android.server.ZuiSecurityService.ZuiSecurityServiceBinder.getRelativeAppStatus]
 * 使其始终返回 1（已允许）。
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
