package com.qimian233.ztool.data.ota

import android.content.Context
import android.util.Log
import com.qimian233.ztool.EnhancedShellExecutor
import com.qimian233.ztool.R
import com.qimian233.ztool.hook.modules.SharedPreferencesTool.ModulePreferencesUtils
import com.qimian233.ztool.utils.GetPCFlashFirmware
import com.qimian233.ztool.viewmodel.FirmwareResult
import com.qimian233.ztool.viewmodel.OtaInfoResult
import com.qimian233.ztool.viewmodel.OtaSettingsUiState
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.BufferedReader
import java.io.DataOutputStream
import java.io.IOException
import java.io.InputStreamReader
import java.io.StringReader
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.log10
import kotlin.math.pow

class OtaSettingsRepository(
    private val context: Context,
    private val shellExecutor: EnhancedShellExecutor = EnhancedShellExecutor.getInstance()
) {
    private val prefsUtils = ModulePreferencesUtils(context)

    fun ensureCustomOtaParametersEnabled() {
        prefsUtils.saveBooleanSetting(KEY_CUSTOM_OTA_PARAMETERS, true)
    }

    fun loadState(): OtaSettingsUiState {
        return OtaSettingsUiState(
            disableOtaCheck = prefsUtils.loadBooleanSetting(KEY_DISABLE_OTA_CHECK, false),
            customVersion = prefsUtils.loadStringSetting(KEY_CUSTOM_OTA_TARGET_VERSION, ""),
            customDeviceId = prefsUtils.loadStringSetting(KEY_CUSTOM_OTA_TARGET_DEVICE_ID, ""),
            currentVersion = context.getString(R.string.loading_ellipsis),
            currentSn = context.getString(R.string.loading_ellipsis)
        )
    }

    fun saveDisableOtaCheck(enabled: Boolean) {
        prefsUtils.saveBooleanSetting(KEY_DISABLE_OTA_CHECK, enabled)
    }

    fun saveCustomVersion(value: String) {
        prefsUtils.saveStringSetting(KEY_CUSTOM_OTA_TARGET_VERSION, value)
    }

    fun saveCustomDeviceId(value: String) {
        prefsUtils.saveStringSetting(KEY_CUSTOM_OTA_TARGET_DEVICE_ID, value)
    }

    fun loadCurrentDeviceInfo(): CurrentDeviceInfo {
        val versionResult = shellExecutor.executeCommand("getprop ro.build.display.id")
        val version = if (versionResult.isSuccess && versionResult.output.isNotEmpty()) {
            versionResult.output.trim()
        } else {
            context.getString(R.string.unknown)
        }

        val sn = getMachineSnByProps()?.takeIf { it.isNotEmpty() } ?: context.getString(R.string.unknown)
        return CurrentDeviceInfo(version = version, sn = sn)
    }

    fun fetchOtaInfo(): OtaInfoResult {
        val xmlContent = readFileWithRoot(OTA_INFO_FILE_PATH)
        val otaInfo = parseOtaInfoXml(xmlContent)
        return otaInfo.toOtaInfoResult()
    }

    fun fetchFirmware(sn: String, callback: (FirmwareFetchResult) -> Unit) {
        GetPCFlashFirmware().queryFirmwareAsync(sn) { firmwareInfo ->
            if (firmwareInfo != null && firmwareInfo.size >= 6) {
                callback(
                    FirmwareFetchResult.Success(
                        FirmwareResult(
                            downloadUrl = firmwareInfo[0].orEmpty(),
                            password = firmwareInfo[1].orEmpty(),
                            platform = firmwareInfo[2].orEmpty(),
                            method = firmwareInfo[3].orEmpty(),
                            firstUploadTime = formatTimestamp(firmwareInfo[4].toLongOrNull() ?: 0L),
                            lastUpdateTime = formatTimestamp(firmwareInfo[5].toLongOrNull() ?: 0L)
                        )
                    )
                )
            } else {
                callback(
                    FirmwareFetchResult.Failure(
                        context.getString(R.string.PCFlashFirmwareFetch_failed_message)
                    )
                )
            }
        }
    }

    fun getMachineSn(): String? = getMachineSnByProps()

    fun restartScope(packageName: String): OtaRestartResult {
        if (packageName.isEmpty()) return OtaRestartResult.Success

        return try {
            val process = Runtime.getRuntime().exec("su -c killall $packageName")
            val process2 = Runtime.getRuntime().exec("su -c killall com.lenovo.tbengine")
            process.waitFor()
            process2.waitFor()
            OtaRestartResult.Success
        } catch (e: Exception) {
            OtaRestartResult.Failure(e.message.orEmpty())
        }
    }

    private fun getMachineSnByProps(): String? {
        val keys = listOf("ro.odm.lenovo.gsn", "ro.serialno", "ro.boot.serialno")
        for (key in keys) {
            val result = shellExecutor.executeRootCommand("getprop $key", 3)
            if (result.isSuccess && result.output.trim().isNotEmpty()) {
                return result.output.trim()
            }
        }
        return null
    }

    @Suppress("SameParameterValue")
    private fun readFileWithRoot(filePath: String): String {
        val process = Runtime.getRuntime().exec("su")
        val os = DataOutputStream(process.outputStream)
        val reader = BufferedReader(InputStreamReader(process.inputStream))
        val errorReader = BufferedReader(InputStreamReader(process.errorStream))

        os.writeBytes("cat $filePath\n")
        os.writeBytes("exit\n")
        os.flush()

        val content = StringBuilder()
        val errorContent = StringBuilder()

        reader.useLines { lines ->
            lines.forEach { content.append(it).append("\n") }
        }
        errorReader.useLines { lines ->
            lines.forEach { errorContent.append(it).append("\n") }
        }
        os.close()

        val exitCode = process.waitFor()
        Log.i(TAG, "readFileWithRoot exit code: $exitCode")
        Log.i(TAG, "readFileWithRoot content length: ${content.length}")
        Log.i(TAG, "readFileWithRoot error: $errorContent")

        if (exitCode != 0 || content.isEmpty()) {
            throw IOException("Root command failed. Exit code: $exitCode, Error: $errorContent")
        }

        return content.toString()
    }

    private fun parseOtaInfoXml(xmlContent: String): Map<String, String> {
        val otaInfo = mutableMapOf<String, String>()
        val factory = XmlPullParserFactory.newInstance()
        val parser = factory.newPullParser()
        parser.setInput(StringReader(xmlContent))

        var eventType = parser.eventType
        while (eventType != XmlPullParser.END_DOCUMENT) {
            if (eventType == XmlPullParser.START_TAG) {
                val tagName = parser.name
                if (tagName == "string" || tagName == "int" || tagName == "long" || tagName == "boolean") {
                    val currentKey = parser.getAttributeValue(null, "name")
                    if (tagName == "string") {
                        eventType = parser.next()
                        if (eventType == XmlPullParser.TEXT) {
                            otaInfo[currentKey] = parser.text
                        }
                    } else {
                        otaInfo[currentKey] = parser.getAttributeValue(null, "value")
                    }
                }
            }
            eventType = parser.next()
        }
        return otaInfo
    }

    private fun Map<String, String>.toOtaInfoResult(): OtaInfoResult {
        val fromVersion = getOrDefault("mUpdateFromVersion", context.getString(R.string.unknown))
        val toVersion = getOrDefault("updateToVersion", context.getString(R.string.unknown))
        val downloadUrl = getOrDefault("downloadUrl", context.getString(R.string.no_download_link))
        val size = getOrDefault("size", "0").toLongOrNull() ?: 0L
        val md5 = getOrDefault("md5", context.getString(R.string.unknown))
        val changelog = getChangelogByLocale(this)
        val formattedSize = formatFileSize(size)

        return OtaInfoResult(
            fromVersion = fromVersion,
            toVersion = toVersion,
            downloadUrl = downloadUrl,
            formattedSize = formattedSize,
            md5 = md5,
            changelog = changelog,
            changelogCopyText = context.getString(
                R.string.changelog_full_format,
                fromVersion,
                toVersion,
                changelog,
                formattedSize,
                md5
            )
        )
    }

    private fun getChangelogByLocale(otaInfo: Map<String, String>): String {
        val locale = context.resources.configuration.locales[0] ?: Locale.getDefault()
        val language = locale.language
        val country = locale.country

        val preciseKey = "HashMap.${language}_$country"
        if (otaInfo.containsKey(preciseKey)) {
            return otaInfo[preciseKey].orEmpty()
        }

        val languageKey = "HashMap.$language"
        if (otaInfo.containsKey(languageKey)) {
            return otaInfo[languageKey].orEmpty()
        }

        return otaInfo.getOrDefault("HashMap.en", context.getString(R.string.no_changelog_available))
    }

    private fun formatFileSize(size: Long): String {
        if (size <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        val digitGroups = (log10(size.toDouble()) / log10(1024.0)).toInt()
        return DecimalFormat("#,##0.#").format(size / 1024.0.pow(digitGroups.toDouble())) +
            " " +
            units[digitGroups]
    }

    private fun formatTimestamp(timestamp: Long): String {
        if (timestamp <= 0L) return timestamp.toString()
        return try {
            SimpleDateFormat("yyyy.MM.dd-HH:mm:ss", Locale.getDefault())
                .format(Date(timestamp * 1000L))
        } catch (_: Exception) {
            timestamp.toString()
        }
    }

    companion object {
        private const val TAG = "OtaSettings"
        private const val OTA_INFO_FILE_PATH =
            "/data_mirror/data_ce/null/0/com.lenovo.tbengine/shared_prefs/lenovo_row_ota_package_info.xml"

        private const val KEY_CUSTOM_OTA_PARAMETERS = "custom_ota_parameters"
        private const val KEY_DISABLE_OTA_CHECK = "disable_OtaCheck"
        private const val KEY_CUSTOM_OTA_TARGET_VERSION = "Custom_ota_target_versionName"
        private const val KEY_CUSTOM_OTA_TARGET_DEVICE_ID = "Custom_ota_target_deviceID"
    }
}

data class CurrentDeviceInfo(
    val version: String,
    val sn: String
)

sealed interface FirmwareFetchResult {
    data class Success(val firmware: FirmwareResult) : FirmwareFetchResult
    data class Failure(val message: String) : FirmwareFetchResult
}

sealed interface OtaRestartResult {
    data object Success : OtaRestartResult
    data class Failure(val error: String) : OtaRestartResult
}
