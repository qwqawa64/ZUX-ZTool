package com.qimian233.ztool.hook.modules.gametool

import com.qimian233.ztool.data.keys.PreferenceKeys
import com.qimian233.ztool.data.keys.ScopeKeys
import com.qimian233.ztool.hook.base.AppHookModule
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam
import java.io.File
import java.lang.reflect.Method

/**
 * CPU frequency hook module - fixes CPU clock reading in the game service.
 * Function: hooks the CPU frequency retrieval methods of
 * com.zui.game.service.util.HWDataInterface so they always read the
 * frequency data of the last CPU core.
 */
class CpuFrequencyFix : AppHookModule() {

    override fun getModuleName(): String = PreferenceKeys.FIX_CPU_CLOCK.name

    override fun getTargetPackages(): Array<String> = arrayOf(ScopeKeys.GAME_SERVICE.packageName)

    override fun handleLoadPackage(param: PackageLoadedParam) {
        val classLoader = param.defaultClassLoader
        val packageName = param.packageName
        logger.debug("CpuFrequencyFix: Targeting $packageName")

        try {
            val hwDataClass = classLoader.loadClass("com.zui.game.service.util.HWDataInterface")

            // Hook the no-arg getCpuCurFreq() method of HWDataInterface
            val getCpuCurFreqMethod: Method = hwDataClass.getDeclaredMethod("getCpuCurFreq")
            hookWithId(getCpuCurFreqMethod, "get_cpu_cur_freq") { getLastCpuCoreCurrentFreq() }

            // Hook the getCpuCurFreq(int coreIndex) method of HWDataInterface
            val getCpuCurFreqIndexMethod: Method = hwDataClass.getDeclaredMethod(
                "getCpuCurFreq", Int::class.javaPrimitiveType
            )
            hookWithId(getCpuCurFreqIndexMethod, "get_cpu_cur_freq_index") { getLastCpuCoreCurrentFreq() }

            // Hook the getCpuMaxFreq() method of HWDataInterface
            val getCpuMaxFreqMethod: Method = hwDataClass.getDeclaredMethod("getCpuMaxFreq")
            hookWithId(getCpuMaxFreqMethod, "get_cpu_max_freq") { getLastCpuCoreMaxFreq() }

            logger.info("CpuFrequencyFix: Successfully hooked CPU frequency methods")
        } catch (t: Throwable) {
            logger.error("CpuFrequencyFix: Error hooking methods", t)
        }
    }

    /**
     * Get the current frequency of the last CPU core.
     */
    private fun getLastCpuCoreCurrentFreq(): Int {
        try {
            // Get the last CPU core index
            val lastCoreIndex = getLastCpuCoreIndex()
            if (lastCoreIndex < 0) {
                logger.warn("CpuFrequencyFix: No CPU cores found, using fallback")
                return readFallbackCpuFreq()
            }

            // Read the current frequency
            val curFreqPath = "/sys/devices/system/cpu/cpu$lastCoreIndex/cpufreq/scaling_cur_freq"
            val freqStr = readSystemFile(curFreqPath)

            if (!freqStr.isNullOrEmpty()) {
                val freq = freqStr.trim().toInt()
                logger.debug("CpuFrequencyFix: Current freq from core $lastCoreIndex: $freq")
                return freq
            }

            // If reading fails, try the fallback method
            logger.warn("CpuFrequencyFix: Failed to read current freq from core $lastCoreIndex")
            return readFallbackCpuFreq()
        } catch (e: Exception) {
            logger.error("CpuFrequencyFix: Error reading CPU current freq", e)
            return DEFAULT_CURRENT_FREQ // Default 2.0GHz
        }
    }

    /**
     * Get the max frequency of the last CPU core.
     */
    private fun getLastCpuCoreMaxFreq(): Int {
        try {
            // Get the last CPU core index
            val lastCoreIndex = getLastCpuCoreIndex()
            if (lastCoreIndex < 0) {
                logger.warn("CpuFrequencyFix: No CPU cores found for max freq, using fallback")
                return readFallbackCpuMaxFreq()
            }

            // Read the max frequency
            val maxFreqPath = "/sys/devices/system/cpu/cpu$lastCoreIndex/cpufreq/scaling_max_freq"
            val freqStr = readSystemFile(maxFreqPath)

            if (!freqStr.isNullOrEmpty()) {
                val freq = freqStr.trim().toInt()
                logger.debug("CpuFrequencyFix: Max freq from core $lastCoreIndex: $freq")
                return freq
            }

            // If reading fails, try the fallback method
            logger.warn("CpuFrequencyFix: Failed to read max freq from core $lastCoreIndex")
            return readFallbackCpuMaxFreq()
        } catch (e: Exception) {
            logger.error("CpuFrequencyFix: Error reading CPU max freq", e)
            return DEFAULT_MAX_FREQ // Default 3.0GHz
        }
    }

    /**
     * Get the index of the last CPU core.
     */
    private fun getLastCpuCoreIndex(): Int {
        try {
            val cpuDir = File("/sys/devices/system/cpu/")
            val cpuFiles = cpuDir.listFiles { _, name -> name.matches(Regex("cpu[0-9]+")) }

            if (cpuFiles == null || cpuFiles.isEmpty()) {
                logger.error("CpuFrequencyFix: No CPU cores found in /sys/devices/system/cpu/")
                return -1
            }

            // Sort by core number in descending order and take the largest (last core)
            cpuFiles.sortWith { f1, f2 ->
                try {
                    val num1 = f1.name.substring(3).toInt()
                    val num2 = f2.name.substring(3).toInt()
                    num2.compareTo(num1) // Descending
                } catch (_: NumberFormatException) {
                    0
                }
            }

            // Get the last core index
            val lastName = cpuFiles[0].name
            val lastIndex = lastName.substring(3).toInt()
            logger.error("CpuFrequencyFix: Last CPU core index: $lastIndex")
            return lastIndex
        } catch (e: Exception) {
            logger.error("CpuFrequencyFix: Error getting last CPU core index", e)
            return -1
        }
    }

    /**
     * Fallback: read the current CPU frequency.
     */
    private fun readFallbackCpuFreq(): Int {
        try {
            // Try reading cpu0's current frequency
            val curFreqStr = readSystemFile("/sys/devices/system/cpu/cpu0/cpufreq/scaling_cur_freq")
            if (!curFreqStr.isNullOrEmpty()) {
                val freq = curFreqStr.trim().toInt()
                logger.info("CpuFrequencyFix: Fallback current freq: $freq")
                return freq
            }

            // Try reading cpuinfo_cur_freq
            val infoCurFreqStr = readSystemFile("/sys/devices/system/cpu/cpu0/cpufreq/cpuinfo_cur_freq")
            if (!infoCurFreqStr.isNullOrEmpty()) {
                val freq = infoCurFreqStr.trim().toInt()
                logger.info("CpuFrequencyFix: Fallback cpuinfo current freq: $freq")
                return freq
            }
        } catch (e: Exception) {
            logger.error("CpuFrequencyFix: Error in fallback current freq reading", e)
        }

        logger.warn("CpuFrequencyFix: Using default current freq: 2000000")
        return 0 // Default 0GHz
    }

    /**
     * Fallback: read the CPU max frequency.
     */
    private fun readFallbackCpuMaxFreq(): Int {
        try {
            // Try reading cpu0's max frequency
            val maxFreqStr = readSystemFile("/sys/devices/system/cpu/cpu0/cpufreq/scaling_max_freq")
            if (!maxFreqStr.isNullOrEmpty()) {
                val freq = maxFreqStr.trim().toInt()
                logger.debug("CpuFrequencyFix: Fallback max freq: $freq")
                return freq
            }

            // Try reading cpuinfo_max_freq
            val infoMaxFreqStr = readSystemFile("/sys/devices/system/cpu/cpu0/cpufreq/cpuinfo_max_freq")
            if (!infoMaxFreqStr.isNullOrEmpty()) {
                val freq = infoMaxFreqStr.trim().toInt()
                logger.debug("CpuFrequencyFix: Fallback cpuinfo max freq: $freq")
                return freq
            }
        } catch (e: Exception) {
            logger.error("CpuFrequencyFix: Error in fallback max freq reading", e)
        }

        logger.warn("CpuFrequencyFix: Using default max freq: 3000000")
        return 0 // Default 0GHz
    }

    /**
     * Read the content of a system file.
     */
    private fun readSystemFile(filePath: String): String? {
        val file = File(filePath)
        if (!file.exists()) {
            logger.error("CpuFrequencyFix: File does not exist: $filePath")
            return null
        }
        return try {
            file.bufferedReader().use { it.readLine() }
        } catch (e: Exception) {
            logger.error("CpuFrequencyFix: Error reading file $filePath", e)
            null
        }
    }

    companion object {
        private const val DEFAULT_CURRENT_FREQ = 2000000 // Default 2.0GHz
        private const val DEFAULT_MAX_FREQ = 3000000 // Default 3.0GHz
    }
}
