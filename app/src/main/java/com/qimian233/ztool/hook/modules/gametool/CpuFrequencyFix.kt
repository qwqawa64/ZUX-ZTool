package com.qimian233.ztool.hook.modules.gametool

import com.qimian233.ztool.data.keys.PreferenceKeys
import com.qimian233.ztool.data.keys.ScopeKeys
import com.qimian233.ztool.hook.base.AppHookModule
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam
import java.io.File
import java.lang.reflect.Method

/**
 * CPU频率Hook模块 - 修复游戏服务中的CPU时钟读取
 * 功能：Hook com.zui.game.service.util.HWDataInterface 的CPU频率获取方法
 * 使其始终读取最后一个CPU核心的频率数据
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

            // Hook HWDataInterface 的 getCpuCurFreq() 方法（无参数）
            val getCpuCurFreqMethod: Method = hwDataClass.getDeclaredMethod("getCpuCurFreq")
            hookWithId(getCpuCurFreqMethod, "get_cpu_cur_freq") { getLastCpuCoreCurrentFreq() }

            // Hook HWDataInterface 的 getCpuCurFreq(int coreIndex) 方法
            val getCpuCurFreqIndexMethod: Method = hwDataClass.getDeclaredMethod(
                "getCpuCurFreq", Int::class.javaPrimitiveType
            )
            hookWithId(getCpuCurFreqIndexMethod, "get_cpu_cur_freq_index") { getLastCpuCoreCurrentFreq() }

            // Hook HWDataInterface 的 getCpuMaxFreq() 方法
            val getCpuMaxFreqMethod: Method = hwDataClass.getDeclaredMethod("getCpuMaxFreq")
            hookWithId(getCpuMaxFreqMethod, "get_cpu_max_freq") { getLastCpuCoreMaxFreq() }

            logger.info("CpuFrequencyFix: Successfully hooked CPU frequency methods")
        } catch (t: Throwable) {
            logger.error("CpuFrequencyFix: Error hooking methods", t)
        }
    }

    /**
     * 获取最后一个CPU核心的当前频率
     */
    private fun getLastCpuCoreCurrentFreq(): Int {
        try {
            // 获取最后一个CPU核心的索引
            val lastCoreIndex = getLastCpuCoreIndex()
            if (lastCoreIndex < 0) {
                logger.warn("CpuFrequencyFix: No CPU cores found, using fallback")
                return readFallbackCpuFreq()
            }

            // 读取当前频率
            val curFreqPath = "/sys/devices/system/cpu/cpu$lastCoreIndex/cpufreq/scaling_cur_freq"
            val freqStr = readSystemFile(curFreqPath)

            if (freqStr != null && freqStr.isNotEmpty()) {
                val freq = freqStr.trim().toInt()
                logger.debug("CpuFrequencyFix: Current freq from core $lastCoreIndex: $freq")
                return freq
            }

            // 如果读取失败，尝试备用方法
            logger.warn("CpuFrequencyFix: Failed to read current freq from core $lastCoreIndex")
            return readFallbackCpuFreq()
        } catch (e: Exception) {
            logger.error("CpuFrequencyFix: Error reading CPU current freq", e)
            return DEFAULT_CURRENT_FREQ // 默认值 2.0GHz
        }
    }

    /**
     * 获取最后一个CPU核心的最大频率
     */
    private fun getLastCpuCoreMaxFreq(): Int {
        try {
            // 获取最后一个CPU核心的索引
            val lastCoreIndex = getLastCpuCoreIndex()
            if (lastCoreIndex < 0) {
                logger.warn("CpuFrequencyFix: No CPU cores found for max freq, using fallback")
                return readFallbackCpuMaxFreq()
            }

            // 读取最大频率
            val maxFreqPath = "/sys/devices/system/cpu/cpu$lastCoreIndex/cpufreq/scaling_max_freq"
            val freqStr = readSystemFile(maxFreqPath)

            if (freqStr != null && freqStr.isNotEmpty()) {
                val freq = freqStr.trim().toInt()
                logger.debug("CpuFrequencyFix: Max freq from core $lastCoreIndex: $freq")
                return freq
            }

            // 如果读取失败，尝试备用方法
            logger.warn("CpuFrequencyFix: Failed to read max freq from core $lastCoreIndex")
            return readFallbackCpuMaxFreq()
        } catch (e: Exception) {
            logger.error("CpuFrequencyFix: Error reading CPU max freq", e)
            return DEFAULT_MAX_FREQ // 默认值 3.0GHz
        }
    }

    /**
     * 获取最后一个CPU核心的索引
     */
    private fun getLastCpuCoreIndex(): Int {
        try {
            val cpuDir = File("/sys/devices/system/cpu/")
            val cpuFiles = cpuDir.listFiles { _, name -> name.matches(Regex("cpu[0-9]+")) }

            if (cpuFiles == null || cpuFiles.isEmpty()) {
                logger.error("CpuFrequencyFix: No CPU cores found in /sys/devices/system/cpu/")
                return -1
            }

            // 按核心编号降序排序，取最大的（最后一个核心）
            cpuFiles.sortWith { f1, f2 ->
                try {
                    val num1 = f1.name.substring(3).toInt()
                    val num2 = f2.name.substring(3).toInt()
                    Integer.compare(num2, num1) // 降序
                } catch (e: NumberFormatException) {
                    0
                }
            }

            // 获取最后一个核心的索引
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
     * 备用方法：读取CPU当前频率
     */
    private fun readFallbackCpuFreq(): Int {
        try {
            // 尝试读取cpu0的当前频率
            val curFreqStr = readSystemFile("/sys/devices/system/cpu/cpu0/cpufreq/scaling_cur_freq")
            if (curFreqStr != null && curFreqStr.isNotEmpty()) {
                val freq = curFreqStr.trim().toInt()
                logger.info("CpuFrequencyFix: Fallback current freq: $freq")
                return freq
            }

            // 尝试读取cpuinfo_cur_freq
            val infoCurFreqStr = readSystemFile("/sys/devices/system/cpu/cpu0/cpufreq/cpuinfo_cur_freq")
            if (infoCurFreqStr != null && infoCurFreqStr.isNotEmpty()) {
                val freq = infoCurFreqStr.trim().toInt()
                logger.info("CpuFrequencyFix: Fallback cpuinfo current freq: $freq")
                return freq
            }
        } catch (e: Exception) {
            logger.error("CpuFrequencyFix: Error in fallback current freq reading", e)
        }

        logger.warn("CpuFrequencyFix: Using default current freq: 2000000")
        return 0 // 默认0GHz
    }

    /**
     * 备用方法：读取CPU最大频率
     */
    private fun readFallbackCpuMaxFreq(): Int {
        try {
            // 尝试读取cpu0的最大频率
            val maxFreqStr = readSystemFile("/sys/devices/system/cpu/cpu0/cpufreq/scaling_max_freq")
            if (maxFreqStr != null && maxFreqStr.isNotEmpty()) {
                val freq = maxFreqStr.trim().toInt()
                logger.debug("CpuFrequencyFix: Fallback max freq: $freq")
                return freq
            }

            // 尝试读取cpuinfo_max_freq
            val infoMaxFreqStr = readSystemFile("/sys/devices/system/cpu/cpu0/cpufreq/cpuinfo_max_freq")
            if (infoMaxFreqStr != null && infoMaxFreqStr.isNotEmpty()) {
                val freq = infoMaxFreqStr.trim().toInt()
                logger.debug("CpuFrequencyFix: Fallback cpuinfo max freq: $freq")
                return freq
            }
        } catch (e: Exception) {
            logger.error("CpuFrequencyFix: Error in fallback max freq reading", e)
        }

        logger.warn("CpuFrequencyFix: Using default max freq: 3000000")
        return 0 // 默认0GHz
    }

    /**
     * 读取系统文件内容
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
        private const val DEFAULT_CURRENT_FREQ = 2000000 // 默认值 2.0GHz
        private const val DEFAULT_MAX_FREQ = 3000000 // 默认值 3.0GHz
    }
}
