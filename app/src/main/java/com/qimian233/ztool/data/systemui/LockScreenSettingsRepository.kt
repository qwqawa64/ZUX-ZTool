package com.qimian233.ztool.data.systemui

import android.content.Context
import com.qimian233.ztool.R
import com.qimian233.ztool.utils.ModulePreferencesUtils
import com.qimian233.ztool.data.PreferenceKeys
import com.qimian233.ztool.utils.ScopeUtils
import com.qimian233.ztool.viewmodel.ApiTestResult
import com.qimian233.ztool.viewmodel.LockScreenSettingsUiState
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.util.regex.Pattern

class LockScreenSettingsRepository(
    private val context: Context
) {
    private val prefsUtils = ModulePreferencesUtils(context)
    private val zToolPrefs = ModulePreferencesUtils(context)
    private val yiYanPrefs = ModulePreferencesUtils(context)

    fun loadState(): LockScreenSettingsUiState {
        val chargeWattsEnabled = prefsUtils.loadBooleanSetting(KEY_CHARGE_WATTS, false)
        val realWattsEnabled = prefsUtils.loadBooleanSetting(KEY_REAL_WATTS, false)
        val chargeWattsOption = when {
            chargeWattsEnabled && !realWattsEnabled -> context.getString(R.string.watt_option_handshake)
            !chargeWattsEnabled && realWattsEnabled -> context.getString(R.string.watt_option_actual)
            else -> context.getString(R.string.watt_option_disabled)
        }

        zToolPrefs.saveStringSetting(KEY_CHARGE_WATTS_SELECTED_OPTION, chargeWattsOption)

        return LockScreenSettingsUiState(
            yiYanEnabled = prefsUtils.loadBooleanSetting(KEY_AUTO_OWNER_INFO, false),
            apiAddress = yiYanPrefs.loadStringSetting(KEY_API_URL, ""),
            regex = yiYanPrefs.loadStringSetting(KEY_REGULAR, ""),
            chargeWattsOption = chargeWattsOption,
            showVoltage = prefsUtils.loadBooleanSetting(KEY_RW_SHOW_VOLTAGE, false),
            showCurrent = prefsUtils.loadBooleanSetting(KEY_RW_SHOW_CURRENT, false),
            showPower = prefsUtils.loadBooleanSetting(KEY_RW_SHOW_POWER, true),
            showTemperature = prefsUtils.loadBooleanSetting(KEY_RW_SHOW_TEMPERATURE, false),
            showIndicator = prefsUtils.loadBooleanSetting(KEY_RW_SHOW_INDICATOR, true),
            customFormatEnabled = prefsUtils.loadBooleanSetting(KEY_RW_CUSTOM_FORMAT_ENABLED, false),
            customFormat = prefsUtils.loadStringSetting(KEY_RW_CUSTOM_FORMAT, ""),
        )
    }

    fun saveYiYanEnabled(enabled: Boolean) {
        prefsUtils.saveBooleanSetting(KEY_YIYAN, enabled)
        if (!enabled) {
            prefsUtils.saveBooleanSetting(KEY_AUTO_OWNER_INFO, false)
        } else if (yiYanPrefs.loadStringSetting(KEY_API_URL, "").isNotEmpty()) {
            prefsUtils.saveBooleanSetting(KEY_AUTO_OWNER_INFO, true)
        }
    }

    fun saveChargeWattsOption(selectedOption: String): Boolean {
        when (selectedOption) {
            context.getString(R.string.watt_option_disabled) -> {
                prefsUtils.saveBooleanSetting(KEY_CHARGE_WATTS, false)
                prefsUtils.saveBooleanSetting(KEY_REAL_WATTS, false)
            }
            context.getString(R.string.watt_option_handshake) -> {
                prefsUtils.saveBooleanSetting(KEY_CHARGE_WATTS, true)
                prefsUtils.saveBooleanSetting(KEY_REAL_WATTS, false)
            }
            context.getString(R.string.watt_option_actual) -> {
                prefsUtils.saveBooleanSetting(KEY_CHARGE_WATTS, false)
                prefsUtils.saveBooleanSetting(KEY_REAL_WATTS, true)
            }
        }
        zToolPrefs.saveStringSetting(KEY_CHARGE_WATTS_SELECTED_OPTION, selectedOption)
        return selectedOption == context.getString(R.string.watt_option_actual) &&
            !prefsUtils.loadBooleanSetting(KEY_SYSTEMUI_PERMISSION_CONFIRMED, false)
    }

    fun saveSystemUiPermissionConfirmed() {
        prefsUtils.saveBooleanSetting(KEY_SYSTEMUI_PERMISSION_CONFIRMED, true)
    }

    fun saveShowVoltage(enabled: Boolean) = prefsUtils.saveBooleanSetting(KEY_RW_SHOW_VOLTAGE, enabled)
    fun saveShowCurrent(enabled: Boolean) = prefsUtils.saveBooleanSetting(KEY_RW_SHOW_CURRENT, enabled)
    fun saveShowPower(enabled: Boolean) = prefsUtils.saveBooleanSetting(KEY_RW_SHOW_POWER, enabled)
    fun saveShowTemperature(enabled: Boolean) = prefsUtils.saveBooleanSetting(KEY_RW_SHOW_TEMPERATURE, enabled)
    fun saveShowIndicator(enabled: Boolean) = prefsUtils.saveBooleanSetting(KEY_RW_SHOW_INDICATOR, enabled)
    fun saveCustomFormatEnabled(enabled: Boolean) = prefsUtils.saveBooleanSetting(KEY_RW_CUSTOM_FORMAT_ENABLED, enabled)
    fun saveCustomFormat(value: String) = prefsUtils.saveStringSetting(KEY_RW_CUSTOM_FORMAT, value.trim())

    fun forceStopScope(): ShellActionResult {
        val packages = listOf("com.android.systemui", "com.zui.wallpapersetting")
        return when (val result = ScopeUtils.restartScope(packages)) {
            is ScopeUtils.RestartResult.Success -> ShellActionResult(success = true, error = "", exitCode = 0)
            is ScopeUtils.RestartResult.PartialSuccess -> ShellActionResult(
                success = false,
                error = "Partial failure: ${result.failed.joinToString()}",
                exitCode = -1
            )
            is ScopeUtils.RestartResult.Failure -> ShellActionResult(
                success = false,
                error = result.message,
                exitCode = -1
            )
        }
    }

    fun testApi(apiUrl: String, regexValue: String): ApiTestResult {
        return try {
            val response = performHttpGet(apiUrl)
            buildApiResponseResult(response, regexValue)
        } catch (e: Exception) {
            ApiTestResult(
                title = context.getString(R.string.request_failed),
                message = context.getString(R.string.error_message_prefix) + e.message,
                success = false
            )
        }
    }

    fun saveYiYanConfiguration(apiAddress: String, regex: String) {
        yiYanPrefs.saveStringSetting(KEY_API_URL, apiAddress.trim())
        yiYanPrefs.saveStringSetting(KEY_REGULAR, regex.trim())
        prefsUtils.saveBooleanSetting(KEY_AUTO_OWNER_INFO, true)
    }

    private fun performHttpGet(urlString: String): String {
        var connection: HttpURLConnection? = null
        var reader: BufferedReader? = null

        try {
            val url = URL(urlString)
            connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 10000
            connection.readTimeout = 10000
            connection.setRequestProperty("User-Agent", "ZTool/1.0")

            val responseCode = connection.responseCode
            if (responseCode == HttpURLConnection.HTTP_OK) {
                reader = BufferedReader(InputStreamReader(connection.inputStream, Charsets.UTF_8))
                return reader.readText()
            }

            throw Exception(context.getString(R.string.http_error_prefix) + responseCode)
        } finally {
            reader?.close()
            connection?.disconnect()
        }
    }

    private fun buildApiResponseResult(response: String, regexValue: String): ApiTestResult {
        var extractedContent = response
        val hasRegex = regexValue.isNotEmpty()

        if (hasRegex) {
            try {
                val matcher = Pattern.compile(regexValue).matcher(response)
                if (matcher.find()) {
                    extractedContent = matcher.group(1)
                        ?.replace("\\\"", "\"")
                        ?.replace("\\\\", "\\")
                        ?.replace("\\/", "/")
                        ?.replace("\\b", "\b")
                        ?.replace("\\f", "\u000C")
                        ?.replace("\\n", "\n")
                        ?.replace("\\r", "\r")
                        ?.replace("\\t", "\t")
                        .orEmpty()
                } else {
                    return ApiTestResult(
                        title = context.getString(R.string.regex_match_failed),
                        message = context.getString(R.string.response_body_prefix) +
                            response +
                            context.getString(R.string.regex_no_match_message),
                        success = false
                    )
                }
            } catch (e: Exception) {
                return ApiTestResult(
                    title = context.getString(R.string.regex_error),
                    message = context.getString(R.string.error_message_prefix) +
                        e.message +
                        context.getString(R.string.response_body_prefix) +
                        response,
                    success = false
                )
            }
        }

        var message = context.getString(R.string.api_request_success)
        if (hasRegex) {
            message += context.getString(R.string.regex_match_result_prefix) + extractedContent + "\n\n"
        }
        message += context.getString(R.string.original_response_prefix) + response

        return ApiTestResult(
            title = context.getString(R.string.test_success),
            message = message,
            success = true
        )
    }

    companion object {
        private val KEY_API_URL = PreferenceKeys.API_URL.name
        private val KEY_REGULAR = PreferenceKeys.REGULAR.name
        private val KEY_YIYAN = PreferenceKeys.YIYAN.name
        private val KEY_AUTO_OWNER_INFO = PreferenceKeys.AUTO_OWNER_INFO.name
        private val KEY_CHARGE_WATTS = PreferenceKeys.SYSTEMUI_CHARGE_WATTS.name
        private val KEY_REAL_WATTS = PreferenceKeys.SYSTEMUI_REAL_WATTS.name
        private val KEY_SYSTEMUI_PERMISSION_CONFIRMED = PreferenceKeys.IS_SYSTEMUI_PERMISSION_CONFIRMED.name
        private val KEY_CHARGE_WATTS_SELECTED_OPTION = PreferenceKeys.CHARGE_WATTS_SELECTED_OPTION.name
        private val KEY_RW_SHOW_VOLTAGE = PreferenceKeys.SYSTEMUI_REALWATTS_SHOW_VOLTAGE.name
        private val KEY_RW_SHOW_CURRENT = PreferenceKeys.SYSTEMUI_REALWATTS_SHOW_CURRENT.name
        private val KEY_RW_SHOW_POWER = PreferenceKeys.SYSTEMUI_REALWATTS_SHOW_POWER.name
        private val KEY_RW_SHOW_TEMPERATURE = PreferenceKeys.SYSTEMUI_REALWATTS_SHOW_TEMPERATURE.name
        private val KEY_RW_SHOW_INDICATOR = PreferenceKeys.SYSTEMUI_REALWATTS_SHOW_INDICATOR.name
        private val KEY_RW_CUSTOM_FORMAT_ENABLED = PreferenceKeys.SYSTEMUI_REALWATTS_CUSTOM_FORMAT_ENABLED.name
        private val KEY_RW_CUSTOM_FORMAT = PreferenceKeys.SYSTEMUI_REALWATTS_CUSTOM_FORMAT.name
    }
}
