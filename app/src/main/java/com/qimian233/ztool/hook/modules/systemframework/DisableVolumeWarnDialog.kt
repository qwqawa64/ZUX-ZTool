package com.qimian233.ztool.hook.modules.systemframework

import android.annotation.SuppressLint
import com.qimian233.ztool.data.keys.PreferenceKeys
import com.qimian233.ztool.data.keys.ScopeKeys
import com.qimian233.ztool.hook.base.SystemHookModule
import io.github.libxposed.api.XposedModuleInterface
import java.lang.reflect.Method

@SuppressLint("PrivateApi")
class DisableVolumeWarnDialog: SystemHookModule() {
    override fun handleSystemServerStarting(param: XposedModuleInterface.SystemServerStartingParam) {
        val cl: ClassLoader = param.classLoader
        val cls: Class<*> = cl.loadClass("com.android.server.audio.SoundDoseHelper")
        val methodLgsi: Method = findMethod(cls, "checkSafeMediaVolume_l",
            Int::class.javaPrimitiveType,
            Int::class.javaPrimitiveType,
            Int::class.javaPrimitiveType)
        hookWithId(methodLgsi, "force_safe_volume") { false }
    }

    override fun getModuleName(): String = PreferenceKeys.DISABLE_SAFE_VOLUME_WARNING.name

    override fun getTargetPackages(): Array<out String> = arrayOf(ScopeKeys.SYSTEM_SERVER.packageName)

}