package com.qimian233.ztool.data.home

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.system.Os
import android.util.Log
import com.qimian233.ztool.EnhancedShellExecutor
import com.qimian233.ztool.ModuleActivationProbe
import com.qimian233.ztool.R
import com.qimian233.ztool.utils.ModulePreferencesUtils
import com.qimian233.ztool.utils.ConfigUpgrade
import com.qimian233.ztool.viewmodel.UpdateInfo
import org.json.JSONException
import org.json.JSONObject
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import androidx.core.content.edit
import com.qimian233.ztool.XposedServiceBridge
import com.qimian233.ztool.data.keys.PreferenceKeys

class HomeRepository(
    private val context: Context,
    private val shellExecutor: EnhancedShellExecutor = EnhancedShellExecutor.getInstance(),
    private val moduleActiveChecker: () -> Boolean = ModuleActivationProbe::isModuleActive
) {
    private var cachedKernelVersion = ""
    private var cachedRootSource = ""
    private var cachedFrameworkVersion = ""
    private var cachedCurrentSlot = ""
    private var cachedRomRegion = ""
    private var cachedIsZuxOsDevice = true
    private var lastSystemInfoUpdate = 0L

    fun checkEnvironment(): EnvironmentStatus {
        val moduleActive = isModuleActive()
        val rootAvailable = shellExecutor.checkRootAccess().isSuccess
        return EnvironmentStatus(
            moduleActive = moduleActive,
            rootAvailable = rootAvailable
        )
    }

    fun updateModuleStatus(): ModuleStatus {
        val version = getModuleVersionInfo()
        if (cachedRootSource.isEmpty() || isSystemInfoCacheExpired()) {
            cachedRootSource = detectRootSource()
        }
        if (cachedFrameworkVersion.isEmpty() || isSystemInfoCacheExpired()) {
            cachedFrameworkVersion = detectFrameworkVersionAndMode()
        }
        return ModuleStatus(
            moduleVersion = version,
            rootSource = cachedRootSource,
            frameworkVersion = cachedFrameworkVersion,
            apiVersion = XposedServiceBridge.getApiVersion()
        )
    }

    fun updateSystemInfo(): SystemInfo {
        val unknown = context.getString(R.string.common_unknown)
        val deviceModel = Build.MODEL.ifBlank { unknown }
        val androidVersion = Build.VERSION.RELEASE.ifBlank { unknown }
            .let { if (it == unknown) it else context.getString(R.string.page_home_android_version_prefix, it) }
        val buildVersion = Build.DISPLAY.ifBlank { unknown }

        if (cachedKernelVersion.isEmpty() || isSystemInfoCacheExpired()) {
            cachedKernelVersion = getKernelVersion()
        }
        if (cachedCurrentSlot.isEmpty() || isSystemInfoCacheExpired()) {
            cachedCurrentSlot = getCurrentBootSlot()
        }
        if (cachedRomRegion.isEmpty() || isSystemInfoCacheExpired()) {
            cachedRomRegion = getRomRegion()
        }
        if (isSystemInfoCacheExpired()) {
            cachedIsZuxOsDevice = isZuxOsBuild(Build.DISPLAY)
        }

        lastSystemInfoUpdate = System.currentTimeMillis()

        return SystemInfo(
            deviceModel = deviceModel,
            androidVersion = androidVersion,
            buildVersion = buildVersion,
            kernelVersion = cachedKernelVersion.ifBlank { unknown },
            currentSlot = cachedCurrentSlot.ifBlank { unknown },
            romRegion = cachedRomRegion.ifBlank { unknown },
            isZuxOsDevice = cachedIsZuxOsDevice
        )
    }

    fun shouldRefreshSystemInfo(): Boolean = isSystemInfoCacheExpired()

    fun isNonZuxOsWarningDismissed(): Boolean {
        return context.getSharedPreferences(PREF_NAME_HOME_UI, Context.MODE_PRIVATE)
            .getBoolean(KEY_NON_ZUXOS_WARNING_DISMISSED, false)
    }

    fun dismissNonZuxOsWarning() {
        context.getSharedPreferences(PREF_NAME_HOME_UI, Context.MODE_PRIVATE)
            .edit { putBoolean(KEY_NON_ZUXOS_WARNING_DISMISSED, true) }
    }

    fun isAutoCheckUpdateEnabled(): Boolean {
        return ModulePreferencesUtils(context)
            .loadBooleanSetting(PreferenceKeys.AUTO_CHECK_UPDATE.name, true)
    }

    fun checkConfigUpgrade(): Boolean = ConfigUpgrade.configUpgrader(context)

    fun checkAppUpdate(): UpdateCheckResult {
        val currentVersionCode = getCurrentVersionCode()
        var lastError: Exception? = null
        for (url in UPDATE_URL) {
            Log.i(TAG, "Fetching update information via url: $url")
            try {
                val cacheBustUrl = if (url.contains("?")) "$url&_t=${System.currentTimeMillis()}" else "$url?_t=${System.currentTimeMillis()}"
                val connection = URL(cacheBustUrl).openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.useCaches = false
                connection.setRequestProperty("Cache-Control", "no-cache, no-store")
                connection.connectTimeout = 5000
                connection.readTimeout = 5000

                val responseCode = connection.responseCode
                if (responseCode == 200) {

                    val json = getJsonObject(connection)
                    val newVersionCode = json.getInt("newVersionCode")
                    val ignoredVersion = context
                        .getSharedPreferences(PREF_NAME_UPDATE, Context.MODE_PRIVATE)
                        .getInt(KEY_IGNORE_VERSION, 0)

                    if (newVersionCode <= currentVersionCode || newVersionCode == ignoredVersion) {
                        Log.w(TAG, "Current version is the latest, no need to update.")
                        Log.w(TAG, "New version code: $newVersionCode, current version code: $currentVersionCode, ignored version: $ignoredVersion")
                        return UpdateCheckResult.Success(null)
                    }

                    return UpdateCheckResult.Success(
                        UpdateInfo(
                            versionName = json.getString("newVersionName"),
                            versionCode = newVersionCode,
                            changelog = json.getString("whatNew"),
                            downloadUrl = json.getString("url")
                        )
                    )
                } else {
                    Log.e(TAG, "Update info request returned HTTP $responseCode via url: $url")
                    lastError = IOException("HTTP $responseCode")
                }
            } catch (th : Exception) {
                lastError = th
                Log.e(TAG, "Failed to fetch update info: ${th.message}")
            }
        }
        return UpdateCheckResult.Failure(describeUpdateCheckError(lastError))
    }

    private fun describeUpdateCheckError(error: Exception?): String {
        if (error == null) return context.getString(R.string.common_unknown)
        return error.message?.takeIf { it.isNotBlank() } ?: error.javaClass.simpleName
    }

    fun ignoreUpdate(versionCode: Int) {
        context
            .getSharedPreferences(PREF_NAME_UPDATE, Context.MODE_PRIVATE)
            .edit {
                putInt(KEY_IGNORE_VERSION, versionCode)
            }
    }

    fun executeReboot(command: String): RebootResult {
        val result = shellExecutor.executeRootCommand(command, 5)
        return RebootResult(
            success = result.isSuccess,
            error = result.error
        )
    }

    fun restartAfterConfigUpgrade(): RebootResult {
        val result = shellExecutor.executeRootCommand("su -c reboot", 3)
        return RebootResult(
            success = result.isSuccess,
            error = result.error
        )
    }

    fun clearShellCache() {
        shellExecutor.clearCache()
    }

    private fun getCurrentVersionCode(): Int {
        val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            packageInfo.longVersionCode.toInt()
        } else {
            @Suppress("DEPRECATION")
            packageInfo.versionCode
        }
    }

    private fun getModuleVersionInfo(): String {
        return try {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            val versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                packageInfo.longVersionCode.toInt()
            } else {
                @Suppress("DEPRECATION")
                packageInfo.versionCode
            }
            "${packageInfo.versionName} ($versionCode)"
        } catch (e: PackageManager.NameNotFoundException) {
            Log.e(TAG, "Failed to get module version: ${e.message}")
            ""
        }
    }

    private fun detectRootSource(): String {
        val detectionCommands = arrayOf("magisk -v", "su -v", "apd -v")
        for (cmd in detectionCommands) {
            try {
                val result = shellExecutor.executeRootCommand(cmd, 3)
                if (result.isSuccess && !result.output.isBlank()) {
                    val output = result.output.trim()
                    if (cmd.contains("magisk")) {
                        return context.getString(R.string.page_home_magisk_su_format, output)
                    }
                    if (cmd.contains("su -v") && output.contains("KernelSU")) {
                        val endPosition = output.indexOf("KernelSU")
                        return context.getString(R.string.page_home_kernelsu_format, output.substring(0, endPosition - 1))
                    }
                    if (cmd.contains("apd")) {
                        return context.getString(R.string.page_home_apatch_format, output)
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to detect root source: ${e.message}")
            }
        }
        return context.getString(R.string.page_home_unknown_root_available)
    }

    private fun detectFrameworkVersionAndMode(): String {
        try {
            val apiVersion: Int = XposedServiceBridge.getApiVersion()
            val frameworkName: String? = XposedServiceBridge.getFrameworkName()
            val frameworkVersion: String? = XposedServiceBridge.getFrameworkVersion()
            val frameworkVersionCode: Long = XposedServiceBridge.getFrameworkVersionCode()
            Log.i(TAG, "Successfully fetched API information: API version: ${apiVersion}, framework name: ${frameworkName}, framework version: ${frameworkVersion}, framework version code: $frameworkVersionCode")
            return context.getString(R.string.page_home_lsposed_standard_format, frameworkName, frameworkVersion, frameworkVersionCode, apiVersion)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to detect framework property: ${e.message}")
        }

        return context.getString(R.string.page_home_unknown_framework)
    }

    private fun getKernelVersion(): String {
        return Os.uname().release
    }

    private fun getCurrentBootSlot(): String {
        val result = shellExecutor.executeRootCommand("getprop ro.boot.slot_suffix", 3)
        return if (result.isSuccess && !result.output.isBlank()) {
            when (result.output.trim()) {
                "_a" -> context.getString(R.string.page_home_slot_a)
                "_b" -> context.getString(R.string.page_home_slot_b)
                else -> context.getString(R.string.common_unknown)
            }
        } else {
            context.getString(R.string.common_unknown)
        }
    }

    private fun getRomRegion(): String {
        return try {
            val commands = listOf(
                "getprop ro.boot.region",
                "getprop ro.config.zui.region",
                "getprop ro.vendor.config.zui.region"
            )
            commands.firstNotNullOfOrNull { command ->
                val result = shellExecutor.executeRootCommand(command, 3)
                result.output.trim().takeIf { it.isNotEmpty() }
            } ?: context.getString(R.string.common_unknown)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch ROM region: ${e.message}")
            context.getString(R.string.common_unknown)
        }
    }

    private fun isSystemInfoCacheExpired(): Boolean {
        return System.currentTimeMillis() - lastSystemInfoUpdate > SYSTEM_INFO_CACHE_DURATION
    }

    private fun isModuleActive(): Boolean {
        return moduleActiveChecker()
    }

    companion object {
        private const val TAG = "HomeRepository"
        private val UPDATE_URL = listOf(
            "https://raw.githubusercontent.com/qwqawa64/ZUX-ZTool/refs/heads/master/UpdateCheck.json",
            "https://gh.absinthe.life/github.com/qwqawa64/ZUX-ZTool/blob/master/UpdateCheck.json"
        )
        private const val PREF_NAME_UPDATE = "update_prefs"
        private const val KEY_IGNORE_VERSION = "ignore_version_code"
        private const val PREF_NAME_HOME_UI = "home_ui_prefs"
        private const val KEY_NON_ZUXOS_WARNING_DISMISSED = "non_zuxos_warning_dismissed"
        private const val SYSTEM_INFO_CACHE_DURATION = 60_000L

        /** Identifiers recognized (after lowercasing) as a correct ROM if contained in any build-mark group. */
        private val ROM_IDENTIFIERS = listOf("zui", "zuxos", "helloui")

        /** TB model prefix: TB + 3 digits + optional 2 letters, e.g. TB710FU / TB324ZC / TB710. */
        private val TB_MODEL_PREFIX = Regex("^tb\\d{3}([a-z]{2})?$")

        /**
         * Judge whether the build marks come from a ZUXOS/ZUI-family ROM.
         * Splits by underscore, lowercases and strips spaces per entry, then matches ROM
         * identifiers (zui/zuxos/helloui) first; if none match, the build is still treated as
         * a correct device when the first group is a Lenovo TB model code (TB710FU, TB324ZC, TB710, etc.).
         */
        fun isZuxOsBuild(buildDisplay: String): Boolean {
            val entries = buildDisplay.split("_").map { it.lowercase().replace(" ", "") }
            val foundIdentifier = entries.any { entry -> ROM_IDENTIFIERS.any { entry.contains(it) } }
            if (foundIdentifier) return true

            val firstEntry = entries.firstOrNull() ?: return false
            return TB_MODEL_PREFIX.matches(firstEntry)
        }

        @Throws(IOException::class, JSONException::class)
        private fun getJsonObject(connection: HttpURLConnection): JSONObject {
            val reader = BufferedReader(InputStreamReader(connection.inputStream))
            val response = buildString {
                var line = reader.readLine()
                while (line != null) {
                    append(line)
                    line = reader.readLine()
                }
            }
            reader.close()
            return JSONObject(response)
        }
    }
}

data class EnvironmentStatus(
    val moduleActive: Boolean,
    val rootAvailable: Boolean
)

data class ModuleStatus(
    val moduleVersion: String,
    val rootSource: String,
    val frameworkVersion: String,
    val apiVersion: Int
)

data class SystemInfo(
    val deviceModel: String,
    val androidVersion: String,
    val buildVersion: String,
    val kernelVersion: String,
    val currentSlot: String,
    val romRegion: String,
    val isZuxOsDevice: Boolean
)

data class RebootResult(
    val success: Boolean,
    val error: String
)

/** Update check result: success (possibly no update) or failure (with a reason). */
sealed class UpdateCheckResult {
    data class Success(val updateInfo: UpdateInfo?) : UpdateCheckResult()
    data class Failure(val reason: String) : UpdateCheckResult()
}
