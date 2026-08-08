package com.qimian233.ztool.hook.modules.systemframework

import android.annotation.SuppressLint
import com.qimian233.ztool.data.keys.PreferenceKeys
import com.qimian233.ztool.data.keys.ScopeKeys
import com.qimian233.ztool.hook.base.SystemHookModule
import io.github.libxposed.api.XposedModuleInterface.SystemServerStartingParam

/**
 * 禁用FLAG_SECURE标志Hook模块
 * 作用：移除安全窗口标志，允许对"安全内容"进行截图
 */
@SuppressLint("PrivateApi")
class DisableFlagSecure : SystemHookModule() {
    override fun getModuleName(): String = PreferenceKeys.DISABLE_FLAG_SECURE.name

    override fun getTargetPackages(): Array<String> = arrayOf(ScopeKeys.SYSTEM_SERVER.packageName)

    override fun handleSystemServerStarting(param: SystemServerStartingParam) {
        val classLoader = param.classLoader
        try {
            logger.info("开始Hook FLAG_SECURE...")
            val windowStateClass = classLoader.loadClass(
                "com.android.server.wm.WindowState"
            )
            val method = windowStateClass.getDeclaredMethod("isSecureLocked")
            hookWithId(method, "is_secure_locked") { false }
            logger.info("成功Hook WindowState.isSecureLocked()")
        } catch (t: Throwable) {
            logger.error("Hook FLAG_SECURE失败", t)
        }
    }
}
