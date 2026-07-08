package com.qimian233.ztool.data.home

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.system.Os
import android.util.Log
import com.qimian233.ztool.EnhancedShellExecutor
import com.qimian233.ztool.ModuleActivationProbe
import com.qimian233.ztool.R
import com.qimian233.ztool.hook.modules.SharedPreferencesTool.ModulePreferencesUtils
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
            rootAvailable = rootAvailable,
            hintText = ""
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
            frameworkVersion = cachedFrameworkVersion
        )
    }

    fun updateSystemInfo(): SystemInfo {
        val unknown = context.getString(R.string.unknown)
        val deviceModel = Build.MODEL.ifBlank { unknown }
        val androidVersion = Build.VERSION.RELEASE.ifBlank { unknown }
            .let { if (it == unknown) it else context.getString(R.string.android_version_prefix, it) }
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
            cachedIsZuxOsDevice = Build.DISPLAY.contains("ZUXOS") || Build.DISPLAY.contains("ZUI")
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

    fun environmentReadyHint(): String = context.getString(R.string.environment_ready)

    fun isAutoCheckUpdateEnabled(): Boolean {
        return ModulePreferencesUtils(context)
            .loadBooleanSetting("auto_check_update", true)
    }

    fun loadHomepageHint(): String? {
        val enableYiyan = ModulePreferencesUtils(context)
            .loadBooleanSetting("enable_homepage_yiyan", true)
        if (!enableYiyan) return null

        val connection = URL(HOMEPAGE_HINT_URL).openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.connectTimeout = 5000
        connection.readTimeout = 5000

        if (connection.responseCode != 200) return null
        val jsonResponse = getJsonObject(connection)
        if (jsonResponse.getInt("code") != 200) return null

        val data = jsonResponse.getJSONObject("data")
        return context.getString(
            R.string.homepage_yiyan,
            data.getString("content"),
            data.getString("origin")
        )
    }

    fun checkConfigUpgrade(): Boolean = ConfigUpgrade.configUpgrader(context)

    fun checkAppUpdate(): UpdateInfo? {
        val currentVersionCode = getCurrentVersionCode()
        for (url in UPDATE_URL) {
            Log.i(TAG, "Fetching update information via url: $url")
            try {
                val connection = URL(url).openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.connectTimeout = 5000
                connection.readTimeout = 5000

                if (connection.responseCode == 200) {

                    val json = getJsonObject(connection)
                    val newVersionCode = json.getInt("newVersionCode")
                    val ignoredVersion = context
                        .getSharedPreferences(PREF_NAME_UPDATE, Context.MODE_PRIVATE)
                        .getInt(KEY_IGNORE_VERSION, 0)

                    if (newVersionCode <= currentVersionCode || newVersionCode == ignoredVersion) {
                        Log.w(TAG, "Current version is the latest, no need to update.")
                        return null
                    }

                    return UpdateInfo(
                        versionName = json.getString("newVersionName"),
                        versionCode = newVersionCode,
                        changelog = json.getString("whatNew"),
                        downloadUrl = json.getString("url")
                    )
                }
            } catch (th : Exception) {
                Log.e(TAG, "Failed to fetch update info: ${th.message}")
            }
        }
        return null
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
            context.getString(R.string.module_version_unknown)
        }
    }

    private fun detectRootSource(): String {
        val detectionCommands = arrayOf("magisk -v", "su -v", "apd -v")
        for (cmd in detectionCommands) {
            try {
                val result = shellExecutor.executeRootCommand(cmd, 3)
                if (result.isSuccess && !result.output.isNullOrBlank()) {
                    val output = result.output.trim()
                    if (cmd.contains("magisk")) {
                        return context.getString(R.string.magisk_su_format, output)
                    }
                    if (cmd.contains("su -v") && output.contains("KernelSU")) {
                        val endPosition = output.indexOf("KernelSU")
                        return context.getString(R.string.kernelsu_format, output.substring(0, endPosition - 1))
                    }
                    if (cmd.contains("apd")) {
                        return context.getString(R.string.apatch_format, output)
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to detect root source: ${e.message}")
            }
        }
        return context.getString(R.string.unknown_root_available)
    }

    private fun detectFrameworkVersionAndMode(): String {
        try {
            val apiVersion: Int = XposedServiceBridge.getApiVersion()
            val frameworkName: String? = XposedServiceBridge.getFrameworkName()
            val frameworkVersion: String? = XposedServiceBridge.getFrameworkVersion()
            val frameworkVersionCode: Long = XposedServiceBridge.getFrameworkVersionCode()
            Log.i(TAG, "Successfully fetched API information: API version: ${apiVersion}, framework name: ${frameworkName}, framework version: ${frameworkVersion}, framework version code: $frameworkVersionCode")
            return context.getString(R.string.lsposed_standard_format, frameworkName, frameworkVersion, frameworkVersionCode, apiVersion)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to detect framework property: ${e.message}")
        }

        return context.getString(R.string.unknown_framework)
    }

    private fun getKernelVersion(): String {
        return Os.uname().release
    }

    private fun getCurrentBootSlot(): String {
        val result = shellExecutor.executeRootCommand("getprop ro.boot.slot_suffix", 3)
        return if (result.isSuccess && !result.output.isNullOrBlank()) {
            when (result.output.trim()) {
                "_a" -> context.getString(R.string.slot_a)
                "_b" -> context.getString(R.string.slot_b)
                else -> context.getString(R.string.unknown)
            }
        } else {
            context.getString(R.string.unknown)
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
                result.output?.trim()?.takeIf { it.isNotEmpty() }
            } ?: context.getString(R.string.unknown)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch ROM region: ${e.message}")
            context.getString(R.string.unknown)
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
        private const val HOMEPAGE_HINT_URL = "https://api.xygeng.cn/one"
        private const val PREF_NAME_UPDATE = "update_prefs"
        private const val KEY_IGNORE_VERSION = "ignore_version_code"
        private const val SYSTEM_INFO_CACHE_DURATION = 60_000L

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
    val rootAvailable: Boolean,
    val hintText: String
)

data class ModuleStatus(
    val moduleVersion: String,
    val rootSource: String,
    val frameworkVersion: String
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
