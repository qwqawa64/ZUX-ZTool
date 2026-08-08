package com.qimian233.ztool.hook.modules.systemframework

import com.qimian233.ztool.data.keys.PreferenceKeys
import com.qimian233.ztool.data.keys.ScopeKeys
import com.qimian233.ztool.hook.base.SystemHookModule
import io.github.libxposed.api.XposedModuleInterface.SystemServerStartingParam
import java.lang.Boolean
import kotlin.Array
import kotlin.String
import kotlin.Throwable
import kotlin.arrayOf

class KeepRotation : SystemHookModule() {
    override fun getModuleName(): String = PreferenceKeys.KEEP_ROTATION.name

    override fun getTargetPackages(): Array<String> = arrayOf(ScopeKeys.SYSTEM_SERVER.packageName)

    override fun handleSystemServerStarting(param: SystemServerStartingParam) {
        val classLoader = param.classLoader
        logger.info("Hooking DisplayRotation.isRotationCts")
        try {
            val method = classLoader.loadClass("com.zui.server.wm.ZuiDisplayRotation")
                .getDeclaredMethod("isRotationCts")
            hookWithId(method, "is_rotation_cts") { Boolean.TRUE }
            logger.info("Hooked DisplayRotation.isRotationCts [OK]")
        } catch (th: Throwable) {
            logger.error("Error hooking DisplayRotation.isRotationCts", th)
        }
    }
}
