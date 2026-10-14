package com.qimian233.ztool.hook.modules.gametool

import com.qimian233.ztool.hook.base.ModuleLog
import java.io.File

/**
 * Resolves the thermal zone reporting device/SoC temperature by zone *name*.
 * Zone indices are not portable: index 9 is `aoss-0` on Qualcomm ZUI and `soc_max`
 * on MediaTek ZUI. See docs/research/gametool_hardware_telemetry.md
 */
internal class ThermalZoneSource(private val logger: ModuleLog) {

    @Volatile
    private var cachedPath: String? = null

    @Volatile
    private var lastFailureAt = 0L

    /** Temperature in milli-Celsius, or null when no zone yields a plausible value. */
    fun read(): Int? {
        cachedPath?.let { path ->
            readMilliCelsius(File(path))?.let { return it }
            cachedPath = null
        }
        val now = System.currentTimeMillis()
        if (now - lastFailureAt < RESOLVE_COOLDOWN_MS) return null

        val zone = resolve()
        val value = zone?.value
        if (zone == null || value == null) {
            lastFailureAt = now
            return null
        }
        cachedPath = zone.path
        logger.info("Thermal zone: ${zone.name} -> $value mC")
        return value
    }

    private fun resolve(): Candidate? {
        val candidates = scan()
        for (candidate in candidates) {
            logger.debug("Thermal candidate ${candidate.name} = ${candidate.raw ?: "unreadable"}")
        }
        val usable = candidates.filter { it.value != null }

        for (preferred in PREFERRED_NAMES) {
            usable.firstOrNull { it.name.equals(preferred, ignoreCase = true) }?.let { return it }
        }
        for (path in LEGACY_PATHS) {
            readMilliCelsius(File(path))?.let {
                logger.debug("Thermal zone from legacy path $path")
                return Candidate(File(path).parentFile?.name ?: path, path, it.toString(), it)
            }
        }
        usable.firstOrNull { candidate -> NAME_HINTS.any { candidate.name.contains(it, true) } }
            ?.let { return it }

        logger.warn("No usable thermal zone under $THERMAL_ROOT")
        return null
    }

    private fun scan(): List<Candidate> {
        val zones = File(THERMAL_ROOT).listFiles { file -> file.name.startsWith(ZONE_PREFIX) }
        return zones.orEmpty().map { zoneDir ->
            val name = readText(File(zoneDir, TYPE_FILE)) ?: zoneDir.name
            val path = File(zoneDir, TEMP_FILE).path
            val raw = readText(File(path))
            Candidate(name, path, raw, raw?.toIntOrNull()?.takeIf { it in PLAUSIBLE_RANGE })
        }
    }

    private fun readMilliCelsius(file: File): Int? =
        readText(file)?.toIntOrNull()?.takeIf { it in PLAUSIBLE_RANGE }

    private fun readText(file: File): String? = try {
        if (!file.canRead()) null else file.bufferedReader().use { it.readLine() }?.trim()
    } catch (_: Exception) {
        null
    }

    private data class Candidate(val name: String, val path: String, val raw: String?, val value: Int?)

    companion object {
        private const val THERMAL_ROOT = "/sys/class/thermal"
        private const val ZONE_PREFIX = "thermal_zone"
        private const val TYPE_FILE = "type"
        private const val TEMP_FILE = "temp"
        private const val RESOLVE_COOLDOWN_MS = 30_000L

        /** 5–150 °C; rejects sentinels (-274000, -40000), 0 and non-temperature zones such as `vbat`. */
        private val PLAUSIBLE_RANGE = 5_000..150_000

        /** Preferred zone names, most meaningful first. `user_temp` is the name the app's own RomFeatures prefers. */
        private val PREFERRED_NAMES = arrayOf(
            "soc_max", "user_temp", "skin-msm-therm-usr", "skin-msm-therm",
            "back_temp", "user_back_temp", "front_temp", "user_front_temp",
            "soc_therm", "mtktscpu", "ap_therm"
        )

        /** Paths used before zone names were resolved; kept as a fallback for unknown layouts. */
        private val LEGACY_PATHS = arrayOf(
            "/sys/class/thermal/thermal_zone9/temp",
            "/sys/class/thermal/thermal_zone0/temp",
            "/sys/class/thermal/thermal_zone1/temp",
            "/sys/class/thermal/thermal_zone2/temp",
            "/sys/devices/virtual/thermal/thermal_zone9/temp",
            "/sys/class/hwmon/hwmon0/temp1_input"
        )

        /** Last-resort name hints, used only when neither the preferred names nor the legacy paths match. */
        private val NAME_HINTS = arrayOf("soc", "cpu", "skin", "ap_")
    }
}
