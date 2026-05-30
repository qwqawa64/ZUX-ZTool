package com.qimian233.ztool.data.settings

import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.util.Base64
import android.util.Log
import com.qimian233.ztool.R
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.BufferedReader
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStreamReader
import java.util.SortedMap
import java.util.TreeMap
import java.util.regex.Pattern

class FloatingWindowRepository(
    private val context: Context
) {
    private val unknownText: String
        get() = context.getString(R.string.unknown)

    fun initialForegroundInfo(): String {
        return context.getString(R.string.current_activity, unknownText)
    }

    fun initialForegroundAppLabel(): String {
        return context.getString(R.string.app_name_label, unknownText)
    }

    fun initialAddedActivitiesText(): String {
        return context.getString(R.string.noActivityAdded)
    }

    fun loadForegroundSnapshot(): FloatingForegroundSnapshot {
        val foregroundApp = getForegroundApp()
        val foregroundPackage = getForegroundActivityByShell(onlyPackageName = true)
        val appName = getAppNameFromPackage(foregroundPackage) ?: unknownText
        return FloatingForegroundSnapshot(
            foregroundInfo = context.getString(R.string.current_activity, foregroundApp),
            foregroundAppLabel = context.getString(R.string.app_name_label, appName),
            appName = appName
        )
    }

    fun getForegroundPackageForSelection(): String? {
        return getForegroundActivityByShell(onlyPackageName = true).takeUnless { it.isNullOrUnknown() }
    }

    fun getForegroundActivityForSelection(): String? {
        return getForegroundActivityByShell(onlyPackageName = false).takeUnless { it.isNullOrUnknown() }
    }

    fun getAppNameFromPackage(packageName: String?): String? {
        if (packageName.isNullOrEmpty()) return null
        val packageManager = context.packageManager
        return try {
            val applicationInfo: ApplicationInfo = packageManager.getApplicationInfo(packageName, 0)
            packageManager.getApplicationLabel(applicationInfo).toString()
        } catch (e: PackageManager.NameNotFoundException) {
            Log.e(TAG, "获取应用名称失败", e)
            null
        }
    }

    fun addedActivitiesText(activities: Set<String>): String {
        return buildString {
            append(context.getString(R.string.added_activities_count, activities.size))
            if (activities.isNotEmpty()) {
                append("\n")
                append(activities.joinToString("\n"))
            }
        }
    }

    fun generateAndSaveConfig(request: FloatingConfigRequest): Boolean {
        return try {
            val config = JSONObject().apply {
                put("name", request.appPackage)
                put("mainPage", request.mainActivity)

                val activityPairs = JSONArray()
                request.activityFromSet.forEach { fromActivity ->
                    activityPairs.put(
                        JSONObject().apply {
                            put("from", fromActivity)
                            put("to", "*")
                        }
                    )
                }
                put("activityPairs", activityPairs)

                put("showEmbeddingDivider", request.showEmbeddingDivider.toString())
                put("skipLetterboxDisplayInfo", request.skipLetterboxDisplayInfo.toString())
                put("skipMultiWindowMode", request.skipMultiWindowMode.toString())
                put("showSurfaceViewBackground", request.showSurfaceViewBackground.toString())
                put("shouldPausePrimaryActivity", request.shouldPausePrimaryActivity.toString())
                put("forceFullscreenPages", JSONArray())
                put("transActivities", JSONArray())
                put("leftTransActivities", JSONArray())
            }

            val configJson = config.toString(2)
            Log.d("EmbeddingConfig", "生成的配置\n$configJson")
            saveBase64StringToFile(configJson, request.appPackage)
            true
        } catch (e: JSONException) {
            Log.e(TAG, "生成配置失败", e)
            false
        }
    }

    private fun getForegroundApp(): String {
        val activityInfo = getForegroundActivityByShell(onlyPackageName = false)
        return if (!activityInfo.isNullOrUnknown()) {
            activityInfo.orEmpty()
        } else {
            getForegroundPackage()
        }
    }

    private fun getForegroundActivityByShell(onlyPackageName: Boolean): String? {
        return try {
            val process = Runtime.getRuntime()
                .exec("su -c dumpsys activity activities | grep -E \"ResumedActivity|mFocusedActivity\"")
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            var line = reader.readLine()
            while (line != null) {
                if (line.contains("ResumedActivity") || line.contains("mFocusedActivity")) {
                    val pattern = Pattern.compile("u0\\s+([^/]+)/([^\\s\\},]+)")
                    val matcher = pattern.matcher(line)
                    if (matcher.find()) {
                        val packageName = matcher.group(1).orEmpty()
                        val activityName = matcher.group(2).orEmpty()
                        reader.close()
                        process.destroy()
                        return if (onlyPackageName) packageName else packageName + activityName
                    }
                }
                line = reader.readLine()
            }
            reader.close()
            process.destroy()
            getForegroundActivityByShellAlternative()
        } catch (e: Exception) {
            Log.e(TAG, "读取前台 Activity 失败", e)
            getForegroundActivityByShellAlternative()
        }
    }

    private fun getForegroundActivityByShellAlternative(): String {
        return try {
            val process = Runtime.getRuntime().exec("su -c dumpsys activity top | grep -E \"ACTIVITY\"")
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            var line = reader.readLine()
            while (line != null) {
                if (line.contains("ACTIVITY")) {
                    val parts = line.trim().split("\\s+".toRegex())
                    if (parts.size >= 2) {
                        reader.close()
                        process.destroy()
                        return parts[1]
                    }
                }
                line = reader.readLine()
            }
            reader.close()
            process.destroy()
            unknownText
        } catch (e: Exception) {
            Log.e(TAG, "读取前台 Activity 备用方法失败", e)
            unknownText
        }
    }

    private fun getForegroundPackage(): String {
        val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val now = System.currentTimeMillis()
        val stats = usageStatsManager.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, now - 5000, now)
        val sortedStats: SortedMap<Long, android.app.usage.UsageStats> = TreeMap()
        stats?.forEach { usageStats ->
            sortedStats[usageStats.lastTimeUsed] = usageStats
        }
        return sortedStats.takeIf { it.isNotEmpty() }?.get(sortedStats.lastKey())?.packageName
            ?: unknownText
    }

    private fun saveBase64StringToFile(originalString: String, packageName: String?) {
        try {
            val base64String = Base64.encodeToString(originalString.toByteArray(Charsets.UTF_8), Base64.DEFAULT)
            val dir = File(context.filesDir, "data/custom_EmbeddingConfig")
            if (!dir.exists() && !dir.mkdirs()) return

            val file = File(dir, "${System.currentTimeMillis()}_$packageName")
            FileOutputStream(file).use { outputStream ->
                outputStream.write(base64String.toByteArray(Charsets.UTF_8))
            }
        } catch (e: IOException) {
            Log.e(TAG, "保存配置失败", e)
        }
    }

    private fun String?.isNullOrUnknown(): Boolean {
        return this == null || this == unknownText
    }

    companion object {
        private const val TAG = "FloatingWindow"
    }
}

data class FloatingForegroundSnapshot(
    val foregroundInfo: String,
    val foregroundAppLabel: String,
    val appName: String
)

data class FloatingConfigRequest(
    val appPackage: String?,
    val mainActivity: String?,
    val activityFromSet: Set<String>,
    val showEmbeddingDivider: Boolean,
    val skipLetterboxDisplayInfo: Boolean,
    val skipMultiWindowMode: Boolean,
    val showSurfaceViewBackground: Boolean,
    val shouldPausePrimaryActivity: Boolean
)
