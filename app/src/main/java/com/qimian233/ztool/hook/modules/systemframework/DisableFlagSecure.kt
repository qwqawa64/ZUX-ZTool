package com.qimian233.ztool.hook.modules.systemframework

import android.annotation.SuppressLint
import com.qimian233.ztool.data.keys.PreferenceKeys
import com.qimian233.ztool.data.keys.ScopeKeys
import com.qimian233.ztool.hook.base.SystemHookModule
import io.github.libxposed.api.XposedModuleInterface.SystemServerStartingParam

/**
 * Disable FLAG_SECURE flag hook module
 * Removes the secure window flag to allow screenshots of "secure" content
 */
@SuppressLint("PrivateApi")
class DisableFlagSecure : SystemHookModule() {
    override fun getModuleName(): String = PreferenceKeys.DISABLE_FLAG_SECURE.name

    override fun getTargetPackages(): Array<String> = arrayOf(ScopeKeys.SYSTEM_SERVER.packageName)

    override fun handleSystemServerStarting(param: SystemServerStartingParam) {
        val classLoader = param.classLoader
        try {
            logger.info("Hooking FLAG_SECURE...")
            val windowStateClass = classLoader.loadClass(
                "com.android.server.wm.WindowState"
            )
            val method = windowStateClass.getDeclaredMethod("isSecureLocked")
            hookWithId(method, "is_secure_locked") { false }
            logger.info("Successfully hooked WindowState.isSecureLocked()")
        } catch (t: Throwable) {
            logger.error("Failed to hook FLAG_SECURE", t)
        }
    }
}
