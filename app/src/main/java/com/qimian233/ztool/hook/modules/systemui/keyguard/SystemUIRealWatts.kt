package com.qimian233.ztool.hook.modules.systemui.keyguard

import android.annotation.SuppressLint
import android.content.SharedPreferences
import com.qimian233.ztool.data.keys.ScopeKeys
import com.qimian233.ztool.data.keys.PreferenceKeys
import com.qimian233.ztool.hook.base.AppHookModule
import io.github.libxposed.api.XposedModuleInterface
import java.io.BufferedReader
import java.io.FileReader
import java.io.IOException
import java.text.DecimalFormat

/**
 * SystemUI charging wattage display hook module.
 *
 * Appends real-time charging info (power, voltage, current, temperature) to the
 * lock screen charging indication.
 *
 * Read strategy: prefer direct Java IO reads of sysfs (SystemUI runs as system uid
 * and usually has permission); automatically falls back to the su command on failure.
 *
 * The display format is configurable via frontend sub-switches or an advanced custom
 * format (placeholder replacement, zero shell risk).
 */
@SuppressLint("PrivateApi")
class SystemUIRealWatts : AppHookModule() {

    private companion object {
        const val TARGET_CLASS = "com.android.systemui.statusbar.KeyguardIndicationController"
        const val CURRENT_NOW_PATH = "/sys/class/power_supply/battery/current_now"
        const val VOLTAGE_NOW_PATH = "/sys/class/power_supply/battery/voltage_now"
        const val STATUS_PATH = "/sys/class/power_supply/battery/status"
        const val TEMP_PATH = "/sys/class/power_supply/battery/temp"

        val POWER_FORMAT = DecimalFormat("0.00")
    }

    private var lastUpdate: Long = 0
    private var suAvailable: Boolean? = null

    override fun getModuleName(): String = PreferenceKeys.SYSTEMUI_REAL_WATTS.name

    override fun getTargetPackages(): Array<String> = arrayOf(ScopeKeys.SYSTEM_UI.packageName)

    override fun handleLoadPackage(param: XposedModuleInterface.PackageLoadedParam) {
        if (param.packageName != ScopeKeys.SYSTEM_UI.packageName) return
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
                            logger.warn("No charging power detected")
                            return@runCatching "$originalText\n --W"
                        }
                        lastUpdate = System.currentTimeMillis()
                        logger.debug("Charging display added: $displayText")
                        originalText + "\n" + displayText
                    } else {
                        logger.warn("No charging power detected")
                        "$originalText\n --W"
                    }
                }.getOrElse { t ->
                    logger.error("computePowerIndication hook callback error", t)
                    chain.proceed()
                }
            }

            logger.info("Successfully hooked KeyguardIndicationController")
        } catch (t: Throwable) {
            logger.error("Failed to hook KeyguardIndicationController", t)
        }
    }

    /** Read charging data: Java IO first, fallback to su on failure. */
    private fun readChargingData(): ChargingData? {
        readChargingDataViaFileIO()?.let { return it }

        logger.warn("Java IO sysfs read failed, falling back to su mode")
        if (!isSuAvailable()) {
            logger.warn("su unavailable, cannot fall back")
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
                logger.warn("Java IO sysfs read returned no valid data - current: $currentStr, voltage: $voltageStr")
                return null
            }
            buildChargingData(status, currentStr, voltageStr, tempStr, "Java IO")
        } catch (e: Exception) {
            logger.error("Java IO charging data read error", e)
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
                logger.warn("su read failed - current: $currentStr, voltage: $voltageStr")
                return null
            }
            buildChargingData(status, currentStr, voltageStr, tempStr, "su")
        } catch (e: Exception) {
            logger.error("su charging data read error", e)
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

        // Temperature: sysfs unit is 0.1°C
        var temperature = -273.0
        if (!tempStr.isNullOrEmpty()) {
            try {
                temperature = tempStr.trim().toLong() / 10.0
            } catch (_: NumberFormatException) {
                logger.warn("Failed to parse temperature value: $tempStr")
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
            "$source read real-time charging data - status: $status, " +
                "current: $currentA" + "A ($currentMicroA" + "μA), " +
                "voltage: $voltageV" + "V ($voltageMicroV" + "μV), " +
                "temperature: ${if (temperature > -200) "${temperature.toInt()}°C" else "N/A"}, " +
                "power: ${POWER_FORMAT.format(power)}W"
        )

        return data
    }

    private fun readSysfs(path: String): String? {
        return try {
            BufferedReader(FileReader(path)).use { it.readLine() }
        } catch (e: IOException) {
            logger.warn("Java IO read of $path failed: ${e.message}")
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
            logger.debug("su availability check: $available")
            available
        } catch (e: Exception) {
            logger.warn("su availability check error: ${e.message}")
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
                logger.warn("su command failed, exit code: $exitCode, command: $command")
                return null
            }
            logger.debug("su command succeeded: $command -> $output")
            output
        } catch (e: Exception) {
            logger.error("Failed to execute su command: $command", e)
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

    /** Advanced custom format: Java-side String.replace, zero shell risk. */
    private fun buildCustomFormat(data: ChargingData, prefs: SharedPreferences): String {
        val format = prefs.getString(PreferenceKeys.SYSTEMUI_REALWATTS_CUSTOM_FORMAT.name, "")
        if (format.isNullOrBlank()) {
            logger.warn("Custom format is empty, falling back to default format")
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
        val current: Int,       // milliamps
        val voltage: Float,     // volts
        val power: Double,      // watts
        val temperature: Double // celsius, -273 means invalid
    )
}
