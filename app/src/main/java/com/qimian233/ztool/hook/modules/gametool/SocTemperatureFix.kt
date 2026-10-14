package com.qimian233.ztool.hook.modules.gametool

import com.qimian233.ztool.data.keys.PreferenceKeys
import com.qimian233.ztool.data.keys.ScopeKeys
import com.qimian233.ztool.hook.base.AppHookModule
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam

/**
 * SoC temperature hook module.
 * Replaces the game service thermal readings with a sysfs value, so the floating
 * window and island show a real temperature where the vendor HAL returns 0.
 * See docs/research/gametool_hardware_telemetry.md
 */
class SocTemperatureFix : AppHookModule() {

    override fun getModuleName(): String = PreferenceKeys.FIX_SOC_TEMP.name

    override fun getTargetPackages(): Array<String> = arrayOf(ScopeKeys.GAME_SERVICE.packageName)

    override fun handleLoadPackage(param: PackageLoadedParam) {
        logger.debug("SocTemperatureFix: start processing package ${param.packageName}")
        hookZuiGameService(param.defaultClassLoader)
    }

    private fun hookZuiGameService(classLoader: ClassLoader) {
        try {
            val hwDataInterfaceClass = classLoader.loadClass(HW_DATA_INTERFACE_CLASS)
            val thermalSource = ThermalZoneSource(logger)

            val getTempMethod = hwDataInterfaceClass.getDeclaredMethod("getTemp")
            hookWithId(getTempMethod, "get_temp") { chain ->
                val originalResult = chain.proceed() as Int
                thermalSource.read() ?: originalResult
            }

            val getThermalTempMethod = hwDataInterfaceClass.getDeclaredMethod(
                "getThermalTemp",
                Int::class.javaPrimitiveType
            )
            hookWithId(getThermalTempMethod, "get_thermal_temp") { chain ->
                val originalResult = chain.proceed() as Int
                thermalSource.read() ?: originalResult
            }

            logger.info("Hook executed successfully.")
        } catch (t: Throwable) {
            logger.error("Failed to hook ZUI game service!", t)
        }
    }

    companion object {
        private const val HW_DATA_INTERFACE_CLASS = "com.zui.game.service.util.HWDataInterface"
    }
}
