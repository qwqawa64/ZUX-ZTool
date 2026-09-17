package com.qimian233.ztool.hook.modules.systemframework

import android.annotation.SuppressLint
import com.qimian233.ztool.data.keys.PreferenceKeys
import com.qimian233.ztool.data.keys.ScopeKeys
import com.qimian233.ztool.hook.base.SystemHookModule
import io.github.libxposed.api.XposedModuleInterface.SystemServerStartingParam

/**
 * Disables the ZUI high-temperature brightness reduction (HBM high brightness mode thermal protection).
 *
 * ZuiDisplayService (services.jar) turns off HBM when the hbm temperature sensor
 * (type==SKIN, name=="hbm") reaches the quitTemperature threshold, limiting screen
 * brightness below the normal cap (temperature check inside setHbmBrightness / setHbmLux).
 *
 * This hook pins ZuiDisplayService.pullTemperatureLocked() to return 0 (treated as
 * low temperature) so the temperature check never triggers, allowing HBM even at
 * high temperatures.
 *
 * Takes effect after a system reboot (system_server process).
 */
class DisableHbmThermalLimit : SystemHookModule() {

    override fun getModuleName(): String = PreferenceKeys.DISABLE_HBM_THERMAL_LIMIT.name

    override fun getTargetPackages(): Array<String> = arrayOf(ScopeKeys.SYSTEM_SERVER.packageName)

    @SuppressLint("PrivateApi")
    override fun handleSystemServerStarting(param: SystemServerStartingParam) {
        val classLoader = param.classLoader
        try {
            logger.info("Hooking ZuiDisplayService.pullTemperatureLocked")
            val method = findMethod(
                classLoader.loadClass("com.android.server.display.ZuiDisplayService"),
                "pullTemperatureLocked"
            )
            // Pin the return value to 0 (0.0°C) to bypass the quitTemperature check
            hookWithId(method, "disable_hbm_thermal_limit") { 0 }
            logger.info("Hooked ZuiDisplayService.pullTemperatureLocked [OK]")
        } catch (t: Throwable) {
            logger.error("Failed hooking ZuiDisplayService.pullTemperatureLocked", t)
        }
    }
}
