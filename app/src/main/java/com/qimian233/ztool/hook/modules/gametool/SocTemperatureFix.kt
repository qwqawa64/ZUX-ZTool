package com.qimian233.ztool.hook.modules.gametool

import com.qimian233.ztool.data.PreferenceKeys
import com.qimian233.ztool.data.ScopeKeys
import com.qimian233.ztool.hook.base.AppHookModule
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam
import java.io.BufferedReader
import java.io.File
import java.io.FileReader
import java.io.IOException

/**
 * SOC温度修复Hook模块
 * 功能：拦截游戏服务的温度读取方法，从thermal_zone9文件获取真实温度值
 */
class SocTemperatureFix : AppHookModule() {
    override fun getModuleName(): String = PreferenceKeys.FIX_SOC_TEMP.name

    override fun getTargetPackages(): Array<String> = arrayOf(ScopeKeys.GAME_SERVICE.packageName)

    override fun handleLoadPackage(param: PackageLoadedParam) {
        val classLoader = param.defaultClassLoader
        val packageName = param.packageName
        logger.debug("SocTemperatureFix: 开始处理包 $packageName")
        hookZuiGameService(classLoader)
    }

    private fun hookZuiGameService(classLoader: ClassLoader) {
        try {
            val hwDataInterfaceClass =
                classLoader.loadClass("com.zui.game.service.util.HWDataInterface")

            // Hook getTemp 方法
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

            // Hook getThermalTemp 方法
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
     * 从 thermal_zone9 文件读取温度
     * @return 温度值（毫摄氏度），读取失败返回 -1
     */
    private fun readTemperatureFromFile(): Int {
        val thermalFile = File(THERMAL_FILE_PATH)

        if (!thermalFile.exists()) {
            logger.warn("Temperature file does not exist: $THERMAL_FILE_PATH")
            // 尝试其他可能的thermal文件路径
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
     * 尝试其他可能的thermal文件路径
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
        // 忽略关闭异常
        return -1
    }

    companion object {
        private const val THERMAL_FILE_PATH = "/sys/class/thermal/thermal_zone9/temp"
    }
}
