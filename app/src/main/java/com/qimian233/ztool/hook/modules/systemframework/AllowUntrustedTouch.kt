package com.qimian233.ztool.hook.modules.systemframework

import android.annotation.SuppressLint
import com.qimian233.ztool.data.keys.PreferenceKeys
import com.qimian233.ztool.data.keys.ScopeKeys
import com.qimian233.ztool.hook.base.SystemHookModule
import io.github.libxposed.api.XposedModuleInterface.SystemServerStartingParam

@SuppressLint("PrivateApi")
class AllowUntrustedTouch : SystemHookModule() {
    override fun getModuleName(): String = PreferenceKeys.ALLOW_UNTRUSTED_TOUCH.name

    override fun getTargetPackages(): Array<String> = arrayOf(ScopeKeys.SYSTEM_SERVER.packageName)

    override fun handleSystemServerStarting(param: SystemServerStartingParam) {
        val classLoader = param.classLoader
        val method = classLoader.loadClass("com.android.server.wm.WindowState")
            .getDeclaredMethod("getTouchOcclusionMode")
        hookWithId(method, "touch_occlusion_mode") { 2 }
    }
}
