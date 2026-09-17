package com.qimian233.ztool.hook.modules.gametool

import com.qimian233.ztool.data.keys.PreferenceKeys
import com.qimian233.ztool.data.keys.ScopeKeys
import com.qimian233.ztool.hook.base.AppHookModule
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam
import java.io.BufferedReader
import java.io.File
import java.io.FileReader
import java.io.IOException

/**
 * SOC temperature fix hook module.
 * Function: intercepts the temperature reading methods of the game service
 * and obtains the real temperature value from the thermal_zone9 file.
 */
class SocTemperatureFix : AppHookModule() {
    override fun getModuleName(): String = PreferenceKeys.FIX_SOC_TEMP.name

    override fun getTargetPackages(): Array<String> = arrayOf(ScopeKeys.GAME_SERVICE.packageName)

    override fun handleLoadPackage(param: PackageLoadedParam) {
        val classLoader = param.defaultClassLoader
        val packageName = param.packageName
        logger.debug("SocTemperatureFix: start processing package $packageName")
        hookZuiGameService(classLoader)
    }

    private fun hookZuiGameService(classLoader: ClassLoader) {
        try {
            val hwDataInterfaceClass =
                classLoader.loadClass("com.zui.game.service.util.HWDataInterface")

            // Hook the getTemp method
            val getTempMethod = hwDataInterfaceClass.getDeclaredMethod("getTemp")
            hookWithId(getTempMethod, "get_temp") { chain ->
                logger.info("Block call to getTemp()")
                val originalResult = chain.proceed() as Int
                val newTemperature = readTemperatureFromFile()
                if (newTemperature > 0) {
                    logger.trace("getTemp - Original temperature: $originalResult, new temperature: $newTemperature")
                    return@hookWithId newTemperature
                } else {
                    logger.warn("Failed to read temperature file, use original value: $originalResult")
                    return@hookWithId originalResult
                }
            }

            // Hook the getThermalTemp method
            val getThermalTempMethod = hwDataInterfaceClass.getDeclaredMethod(
                "getThermalTemp",
                Int::class.javaPrimitiveType
            )
            hookWithId(
                getThermalTempMethod,
                "get_thermal_temp"
            ) { chain ->
                val type = chain.getArg(0) as Int
                logger.debug("Blocked getThermalTemp(), type: $type")
                val originalResult = chain.proceed() as Int
                val newTemperature = readTemperatureFromFile()

                if (newTemperature > 0) {
                    logger.trace("getThermalTemp - Original: $originalResult, new: $newTemperature")
                    return@hookWithId newTemperature
                }
                originalResult
            }

            logger.info("Hook executed successfully.")
        } catch (t: Throwable) {
            logger.error("Failed to hook ZUI game service!", t)
        }
    }

    /**
     * Read the temperature from the thermal_zone9 file.
     * @return temperature in milli-Celsius, or -1 when reading fails
     */
    private fun readTemperatureFromFile(): Int {
        val thermalFile = File(THERMAL_FILE_PATH)

        if (!thermalFile.exists()) {
            logger.warn("Temperature file does not exist: $THERMAL_FILE_PATH")
            // Try other possible thermal file paths
            return tryAlternativeThermalFiles()
        }

        if (!thermalFile.canRead()) {
            logger.warn("Failed to read file: permission denied $THERMAL_FILE_PATH")
            return -1
        }

        try {
            BufferedReader(FileReader(thermalFile)).use { reader ->
                val line = reader.readLine()
                if (line != null && !line.trim { it <= ' ' }.isEmpty()) {
                    val temperature = line.trim { it <= ' ' }.toInt()
                    logger.debug("Read temperature data from file: $temperature")
                    return temperature
                }
            }
        } catch (e: IOException) {
            logger.error("IO exception happened when reading temperature file", e)
        } catch (e: NumberFormatException) {
            logger.error("Invalid temperature file format", e)
        }

        return -1
    }

    /**
     * Try other possible thermal file paths.
     */
    private fun tryAlternativeThermalFiles(): Int {
        val alternativePaths = arrayOf(
            "/sys/class/thermal/thermal_zone0/temp",
            "/sys/class/thermal/thermal_zone1/temp",
            "/sys/class/thermal/thermal_zone2/temp",
            "/sys/devices/virtual/thermal/thermal_zone9/temp",
            "/sys/class/hwmon/hwmon0/temp1_input"
        )

        for (path in alternativePaths) {
            val thermalFile = File(path)
            if (thermalFile.exists() && thermalFile.canRead()) {
                logger.debug("Alternate temperature file found: $path")
                return readFromSpecificFile(path)
            }
        }

        logger.warn("Unable to find a valid temperature file.")
        return -1
    }

    private fun readFromSpecificFile(filePath: String): Int {
        val thermalFile = File(filePath)
        try {
            BufferedReader(FileReader(thermalFile)).use { reader ->
                val line = reader.readLine()
                if (line != null && !line.trim { it <= ' ' }.isEmpty()) {
                    return line.trim { it <= ' ' }.toInt()
                }
            }
        } catch (e: Exception) {
            logger.error("Failed to read temperature file: $filePath", e)
        }
        // Ignore close exceptions
        return -1
    }

    companion object {
        private const val THERMAL_FILE_PATH = "/sys/class/thermal/thermal_zone9/temp"
    }
}
