package com.qimian233.ztool.data.systemui

import android.content.Context
import com.qimian233.ztool.R
import com.qimian233.ztool.hook.modules.SharedPreferencesTool.ModulePreferencesUtils
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
        private const val KEY_API_URL = "API_URL"
        private const val KEY_REGULAR = "Regular"
        private const val KEY_YIYAN = "YiYan"
        private const val KEY_AUTO_OWNER_INFO = "auto_owner_info"
        private const val KEY_CHARGE_WATTS = "systemui_charge_watts"
        private const val KEY_REAL_WATTS = "systemUI_RealWatts"
        private const val KEY_SYSTEMUI_PERMISSION_CONFIRMED = "isSystemUIPermissionConfirmed"
        private const val KEY_CHARGE_WATTS_SELECTED_OPTION = "charge_watts_selected_option"
    }
}
