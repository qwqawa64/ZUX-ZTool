package com.qimian233.ztool.hook.modules.systemframework

import android.annotation.SuppressLint
import com.qimian233.ztool.data.keys.PreferenceKeys
import com.qimian233.ztool.data.keys.ScopeKeys
import com.qimian233.ztool.hook.base.SystemHookModule
import io.github.libxposed.api.XposedModuleInterface.SystemServerStartingParam

@SuppressLint("PrivateApi")
class NoMorePasswordPer24H : SystemHookModule() {
    override fun getModuleName(): String = PreferenceKeys.NO_MORE_PASSWORD_PER_24H.name

    override fun getTargetPackages(): Array<String> = arrayOf(
        ScopeKeys.SYSTEM_SERVER.packageName
    )

    override fun handleSystemServerStarting(param: SystemServerStartingParam) {
        val classLoader = param.classLoader

        val lockSettingsClass = classLoader.loadClass(
            "com.android.server.locksettings.LockSettingsStrongAuth"
        )

        val rescheduleMethod = lockSettingsClass.getDeclaredMethod(
            "rescheduleStrongAuthTimeoutAlarm",
            Long::class.javaPrimitiveType,
            Int::class.javaPrimitiveType
        )
        hookWithId(
            rescheduleMethod,
            "reschedule_strong_auth"
        ) { null }

        val handleIdleMethod = lockSettingsClass.getDeclaredMethod(
            "handleScheduleNonStrongBiometricIdleTimeout", Int::class.javaPrimitiveType
        )
        hookWithId(
            handleIdleMethod,
            "handle_idle_timeout"
        ) { null }

        val handleTimeoutMethod = lockSettingsClass.getDeclaredMethod(
            "handleScheduleNonStrongBiometricTimeout", Int::class.javaPrimitiveType
        )
        hookWithId(
            handleTimeoutMethod,
            "handle_timeout"
        ) { null }
    }

}
