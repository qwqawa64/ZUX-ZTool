package com.qimian233.ztool.hook.modules.systemui.keyguard

import android.annotation.SuppressLint
import android.content.SharedPreferences
import com.qimian233.ztool.data.PreferenceKeys
import com.qimian233.ztool.hook.base.AppHookModule
import io.github.libxposed.api.XposedModuleInterface
import java.io.BufferedReader
import java.io.FileReader
import java.io.IOException
import java.text.DecimalFormat

/**
 * SystemUI 充电瓦数显示 Hook 模块。
 *
 * 在锁屏充电提示中追加实时充电信息（功率、电压、电流、温度）。
 *
 * 读取策略：优先使用 Java IO 直接读取 sysfs（SystemUI 以 system uid 运行，
 * 通常有权限），失败时自动 fallback 到 su 命令。
 *
 * 显示格式可通过前端子开关或高级自定义格式（占位符替换，零 shell 风险）配置。
 */
@SuppressLint("PrivateApi")
class SystemUIRealWatts : AppHookModule() {

    // ── sysfs 路径 ──
    private companion object {
        const val TARGET_CLASS = "com.android.systemui.statusbar.KeyguardIndicationController"
        const val CURRENT_NOW_PATH = "/sys/class/power_supply/battery/current_now"
        const val VOLTAGE_NOW_PATH = "/sys/class/power_supply/battery/voltage_now"
        const val STATUS_PATH = "/sys/class/power_supply/battery/status"
        const val TEMP_PATH = "/sys/class/power_supply/battery/temp"
        const val PREFS_NAME = "xposed_module_config"

        val POWER_FORMAT = DecimalFormat("0.00")
    }

    // ── 可变状态 ──
    private var lastUpdate: Long = 0
    private var suAvailable: Boolean? = null

    override fun getModuleName(): String = PreferenceKeys.SYSTEMUI_REAL_WATTS.name

    override fun getTargetPackages(): Array<String> = arrayOf("com.android.systemui")

    override fun handleLoadPackage(param: XposedModuleInterface.PackageLoadedParam) {
        if (param.packageName != "com.android.systemui") return
        hookKeyguardIndicationController(param.defaultClassLoader)
    }

    private fun hookKeyguardIndicationController(classLoader: ClassLoader) {
        try {
            val computeMethod = classLoader.loadClass(TARGET_CLASS)
                .getDeclaredMethod("computePowerIndication")

            hookWithId(computeMethod, "compute") { chain ->
                runCatching {
                    val originalText = chain.proceed() as? String? ?: return@runCatching null

                    val controller = chain.thisObject
                    val isPluggedIn = controller.javaClass
                        .getDeclaredField("mPowerPluggedIn")
                        .getBoolean(controller)

                    if (!isPluggedIn) return@runCatching originalText

                    if (System.currentTimeMillis() - lastUpdate < 100) {
                        logger.debug("Debounce triggered. Skipping this update.")
                        return@runCatching originalText
                    }

                    val chargingData = readChargingData()
                    if (chargingData != null && chargingData.isCharging && chargingData.power > 0) {
                        val prefs = xposed.getRemotePreferences(PREFS_NAME)
                        val displayText = buildDisplayText(chargingData, prefs)
                        if (displayText.isEmpty()) {
                            logger.warn("未能检测到充电功率")
                            return@runCatching "$originalText\n --W"
                        }
                        lastUpdate = System.currentTimeMillis()
                        logger.debug("成功添加充电显示: $displayText")
                        originalText + "\n" + displayText
                    } else {
                        logger.warn("未能检测到充电功率")
                        "$originalText\n --W"
                    }
                }.getOrElse { t ->
                    logger.error("computePowerIndication hook 回调异常", t)
                    chain.proceed()
                }
            }

            logger.info("成功 Hook KeyguardIndicationController")
        } catch (t: Throwable) {
            logger.error("Hook KeyguardIndicationController 失败", t)
        }
    }

    /** 读取充电数据：Java IO 优先，失败时 fallback 到 su。 */
    private fun readChargingData(): ChargingData? {
        readChargingDataViaFileIO()?.let { return it }

        logger.warn("Java IO 读取 sysfs 失败，尝试 fallback 到 su 模式")
        if (!isSuAvailable()) {
            logger.warn("su 不可用，无法 fallback")
            return null
        }
        return readChargingDataViaSu()
    }

    private fun readChargingDataViaFileIO(): ChargingData? {
        return try {
            val status = readSysfs(STATUS_PATH)
            val currentStr = readSysfs(CURRENT_NOW_PATH)
            val voltageStr = readSysfs(VOLTAGE_NOW_PATH)
            val tempStr = readSysfs(TEMP_PATH)

            if (currentStr.isNullOrEmpty() || voltageStr.isNullOrEmpty()) {
                logger.warn("Java IO 读取 sysfs 无有效数据 - 电流: $currentStr, 电压: $voltageStr")
                return null
            }
            buildChargingData(status, currentStr, voltageStr, tempStr, "Java IO")
        } catch (e: Exception) {
            logger.error("Java IO 读取充电数据异常", e)
            null
        }
    }

    private fun readChargingDataViaSu(): ChargingData? {
        return try {
            val status = executeRootCommand("cat $STATUS_PATH")
            val currentStr = executeRootCommand("cat $CURRENT_NOW_PATH")
            val voltageStr = executeRootCommand("cat $VOLTAGE_NOW_PATH")
            val tempStr = executeRootCommand("cat $TEMP_PATH")

            if (currentStr.isNullOrEmpty() || voltageStr.isNullOrEmpty()) {
                logger.warn("su 读取失败 - 电流: $currentStr, 电压: $voltageStr")
                return null
            }
            buildChargingData(status, currentStr, voltageStr, tempStr, "su")
        } catch (e: Exception) {
            logger.error("su 读取充电数据异常", e)
            null
        }
    }

    private fun buildChargingData(
        status: String?,
        currentStr: String,
        voltageStr: String,
        tempStr: String?,
        source: String
    ): ChargingData {
        val isCharging = status.equals("Charging", ignoreCase = true) ||
            status.equals("Full", ignoreCase = true)

        val currentMicroA = currentStr.trim().toLong()
        val voltageMicroV = voltageStr.trim().toLong()
        val currentA = currentMicroA / 1_000_000.0
        val voltageV = voltageMicroV / 1_000_000.0
        val power = kotlin.math.abs(currentA * voltageV)

        // 温度：sysfs 单位为 0.1°C
        var temperature = -273.0
        if (!tempStr.isNullOrEmpty()) {
            try {
                temperature = tempStr.trim().toLong() / 10.0
            } catch (_: NumberFormatException) {
                logger.warn("温度值解析失败: $tempStr")
            }
        }

        val data = ChargingData(
            isCharging = isCharging,
            current = (currentA * 1000).toInt(),
            voltage = voltageV.toFloat(),
            power = power,
            temperature = temperature
        )

        logger.debug(
            "$source 读取实时充电数据 - 状态: $status, " +
                "电流: $currentA" + "A ($currentMicroA" + "μA), " +
                "电压: $voltageV" + "V ($voltageMicroV" + "μV), " +
                "温度: ${if (temperature > -200) "${temperature.toInt()}°C" else "N/A"}, " +
                "功率: ${POWER_FORMAT.format(power)}W"
        )

        return data
    }

    private fun readSysfs(path: String): String? {
        return try {
            BufferedReader(FileReader(path)).use { it.readLine() }
        } catch (e: IOException) {
            logger.warn("Java IO 读取 $path 失败: ${e.message}")
            null
        }
    }

    private fun isSuAvailable(): Boolean {
        suAvailable?.let { return it }
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("sh", "-c", "command -v su"))
            val result = process.inputStream.bufferedReader().use { it.readLine() }
            process.waitFor()
            val available = !result.isNullOrEmpty()
            suAvailable = available
            logger.debug("su 可用性检测: $available")
            available
        } catch (e: Exception) {
            logger.warn("su 可用性检测异常: ${e.message}")
            suAvailable = false
            false
        }
    }

    private fun executeRootCommand(command: String): String? {
        return try {
            val process = Runtime.getRuntime().exec("su -c $command")
            val output = process.inputStream.bufferedReader().use { it.readText().trim() }
            val exitCode = process.waitFor()
            if (exitCode != 0) {
                logger.warn("su 命令执行失败，退出码: $exitCode, 命令: $command")
                return null
            }
            logger.debug("su 命令执行成功: $command -> $output")
            output
        } catch (e: Exception) {
            logger.error("执行 su 命令失败: $command", e)
            null
        }
    }

    private fun buildDisplayText(data: ChargingData, prefs: SharedPreferences): String {
        if (data.power <= 0) return ""

        return if (prefs.getBoolean(PreferenceKeys.SYSTEMUI_REALWATTS_CUSTOM_FORMAT_ENABLED.name, false)) {
            buildCustomFormat(data, prefs)
        } else {
            buildDefaultFormat(data, prefs)
        }
    }

    private fun buildDefaultFormat(data: ChargingData, prefs: SharedPreferences): String {
        val showPower = prefs.getBoolean(PreferenceKeys.SYSTEMUI_REALWATTS_SHOW_POWER.name, true)
        val showVoltage = prefs.getBoolean(PreferenceKeys.SYSTEMUI_REALWATTS_SHOW_VOLTAGE.name, false)
        val showCurrent = prefs.getBoolean(PreferenceKeys.SYSTEMUI_REALWATTS_SHOW_CURRENT.name, false)
        val showTemp = prefs.getBoolean(PreferenceKeys.SYSTEMUI_REALWATTS_SHOW_TEMPERATURE.name, false)
        val showIndicator = prefs.getBoolean(PreferenceKeys.SYSTEMUI_REALWATTS_SHOW_INDICATOR.name, true)

        val parts = mutableListOf<String>()

        if (showVoltage) parts += POWER_FORMAT.format(data.voltage.toDouble()) + "V"
        if (showCurrent) parts += POWER_FORMAT.format(data.current / 1000.0) + "A"
        if (showPower)  parts += POWER_FORMAT.format(data.power) + "W"
        if (showTemp && data.temperature > -200) parts += "${data.temperature.toInt()}°C"

        if (parts.isEmpty()) {
            parts += POWER_FORMAT.format(data.power) + "W"
        }

        return buildString {
            append(parts.joinToString(" / "))
            if (showIndicator) append(indicator(data.power))
        }
    }

    /** 高级自定义格式：Java 侧 String.replace，零 shell 风险。 */
    private fun buildCustomFormat(data: ChargingData, prefs: SharedPreferences): String {
        val format = prefs.getString(PreferenceKeys.SYSTEMUI_REALWATTS_CUSTOM_FORMAT.name, "")
        if (format.isNullOrBlank()) {
            logger.warn("自定义格式为空，回退到默认格式")
            return buildDefaultFormat(data, prefs)
        }

        return format
            .replace($$"${voltage}", POWER_FORMAT.format(data.voltage.toDouble()))
            .replace($$"${current}", POWER_FORMAT.format(data.current / 1000.0))
            .replace($$"${power}", POWER_FORMAT.format(data.power))
            .replace(
                $$"${temperature}",
                if (data.temperature > -200) data.temperature.toInt().toString() else "N/A"
            )
            .replace($$"${ind}", indicator(data.power))
    }

    private fun indicator(watts: Double): String = when {
        watts < 30  -> ""
        watts < 65  -> " ⚡"
        else        -> " ⚡⚡"
    }

    private data class ChargingData(
        val isCharging: Boolean,
        val current: Int,       // 毫安
        val voltage: Float,     // 伏特
        val power: Double,      // 瓦特
        val temperature: Double // 摄氏度，-273 表示无效
    )
}
