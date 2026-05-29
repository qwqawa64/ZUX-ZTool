package com.qimian233.ztool.settingactivity.systemui.lockscreen

import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.qimian233.ztool.R
import com.qimian233.ztool.hook.modules.SharedPreferencesTool.ModulePreferencesUtils
import com.qimian233.ztool.ui.components.ZToolDropdownField
import com.qimian233.ztool.ui.components.ZToolSwitchRow
import com.qimian233.ztool.ui.theme.ZToolTheme
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.util.regex.Pattern

class LockScreenSettingsActivity : ComponentActivity() {

    private lateinit var prefsUtils: ModulePreferencesUtils
    private lateinit var zToolPrefs: ModulePreferencesUtils
    private lateinit var yiYanPrefs: ModulePreferencesUtils

    private var yiYanEnabled by mutableStateOf(false)
    private var apiAddress by mutableStateOf("")
    private var regex by mutableStateOf("")
    private var chargeWattsOption by mutableStateOf("")
    private var realWattsIntervalOption by mutableStateOf("")
    private var realWattsRefreshInterval by mutableStateOf("")
    private var isTestingApi by mutableStateOf(false)
    private var showRootPermissionDialog by mutableStateOf(false)
    private var apiTestResult by mutableStateOf<ApiTestResult?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val appName = intent.getStringExtra("app_name").orEmpty()
        prefsUtils = ModulePreferencesUtils(this)
        zToolPrefs = ModulePreferencesUtils(this)
        yiYanPrefs = ModulePreferencesUtils(this)
        loadSettings()

        setContent {
            ZToolTheme {
                LockScreenSettingsScreen(
                    title = appName + stringResource(R.string.lock_screen_settings_title_suffix),
                    yiYanEnabled = yiYanEnabled,
                    apiAddress = apiAddress,
                    regex = regex,
                    chargeWattsOption = chargeWattsOption,
                    realWattsIntervalOption = realWattsIntervalOption,
                    realWattsRefreshInterval = realWattsRefreshInterval,
                    isTestingApi = isTestingApi,
                    onBack = ::finish,
                    onYiYanChanged = ::handleYiYanChanged,
                    onApiAddressChanged = { apiAddress = it },
                    onRegexChanged = { regex = it },
                    onTestApi = ::testApiConnection,
                    onChargeWattsOptionChanged = ::handleWattOptionSelected,
                    onRealWattsIntervalOptionChanged = ::handleIntervalOptionSelected,
                    onRealWattsRefreshIntervalChanged = ::handleRealWattsRefreshIntervalChanged
                )

                if (showRootPermissionDialog) {
                    RootPermissionDialog(
                        onConfirm = { showRootPermissionDialog = false },
                        onDoNotShowAgain = {
                            showRootPermissionDialog = false
                            saveSettings("isSystemUIPermissionConfirmed", true)
                            Toast.makeText(this, R.string.no_tip_next_time, Toast.LENGTH_SHORT).show()
                        }
                    )
                }

                apiTestResult?.let { result ->
                    ApiTestResultDialog(
                        result = result,
                        onSave = {
                            saveYiYanConfiguration()
                            apiTestResult = null
                        },
                        onDismiss = { apiTestResult = null }
                    )
                }
            }
        }
    }

    private fun loadSettings() {
        apiAddress = yiYanPrefs.loadStringSetting("API_URL", "")
        regex = yiYanPrefs.loadStringSetting("Regular", "")
        yiYanEnabled = prefsUtils.loadBooleanSetting("auto_owner_info", false)

        val chargeWattsEnabled = prefsUtils.loadBooleanSetting("systemui_charge_watts", false)
        val realWattsEnabled = prefsUtils.loadBooleanSetting("systemUI_RealWatts", false)
        chargeWattsOption = when {
            chargeWattsEnabled && !realWattsEnabled -> getString(R.string.watt_option_handshake)
            !chargeWattsEnabled && realWattsEnabled -> getString(R.string.watt_option_actual)
            else -> getString(R.string.watt_option_disabled)
        }

        realWattsIntervalOption = if (
            prefsUtils.loadBooleanSetting("real_watts_customized_interval", false)
        ) {
            getString(R.string.real_watt_custom_refresh_interval_enabled)
        } else {
            getString(R.string.real_watt_custom_refresh_interval_disabled)
        }
        realWattsRefreshInterval = prefsUtils
            .loadFloatSetting("real_watts_refresh_interval", 3.0f)
            .toString()

        zToolPrefs.saveStringSetting("charge_watts_selected_option", chargeWattsOption)
    }

    private fun handleYiYanChanged(isEnabled: Boolean) {
        yiYanEnabled = isEnabled
        saveSettings("YiYan", isEnabled)
        if (!isEnabled) {
            saveSettings("auto_owner_info", false)
        } else if (yiYanPrefs.loadStringSetting("API_URL", "").isNotEmpty()) {
            saveSettings("auto_owner_info", true)
        }
    }

    private fun handleWattOptionSelected(selectedOption: String) {
        chargeWattsOption = selectedOption
        when (selectedOption) {
            getString(R.string.watt_option_disabled) -> {
                saveSettings("systemui_charge_watts", false)
                saveSettings("systemUI_RealWatts", false)
            }
            getString(R.string.watt_option_handshake) -> {
                saveSettings("systemui_charge_watts", true)
                saveSettings("systemUI_RealWatts", false)
            }
            getString(R.string.watt_option_actual) -> {
                saveSettings("systemui_charge_watts", false)
                saveSettings("systemUI_RealWatts", true)
                if (!prefsUtils.loadBooleanSetting("isSystemUIPermissionConfirmed", false)) {
                    showRootPermissionDialog = true
                }
            }
        }
        zToolPrefs.saveStringSetting("charge_watts_selected_option", selectedOption)
    }

    private fun handleIntervalOptionSelected(selectedOption: String) {
        realWattsIntervalOption = selectedOption
        prefsUtils.saveBooleanSetting(
            "real_watts_customized_interval",
            selectedOption == getString(R.string.real_watt_custom_refresh_interval_enabled)
        )
    }

    private fun handleRealWattsRefreshIntervalChanged(value: String) {
        realWattsRefreshInterval = value.filter { it.isDigit() || it == '.' }
        realWattsRefreshInterval.toFloatOrNull()?.let {
            prefsUtils.saveFloatSetting("real_watts_refresh_interval", it)
        } ?: Log.d(TAG, "Empty number string, will not save it as a valid refresh interval.")
    }

    private fun testApiConnection() {
        val apiUrl = apiAddress.trim()
        val regexValue = regex.trim()

        if (apiUrl.isEmpty()) {
            Toast.makeText(this, R.string.please_input_api_address, Toast.LENGTH_SHORT).show()
            return
        }

        isTestingApi = true
        Thread {
            try {
                val response = performHttpGet(apiUrl)
                runOnUiThread {
                    isTestingApi = false
                    handleApiResponse(response, regexValue)
                }
            } catch (e: Exception) {
                runOnUiThread {
                    isTestingApi = false
                    apiTestResult = ApiTestResult(
                        title = getString(R.string.request_failed),
                        message = getString(R.string.error_message_prefix) + e.message,
                        success = false
                    )
                }
            }
        }.start()
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

            throw Exception(getString(R.string.http_error_prefix) + responseCode)
        } finally {
            reader?.close()
            connection?.disconnect()
        }
    }

    private fun handleApiResponse(response: String, regexValue: String) {
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
                    apiTestResult = ApiTestResult(
                        title = getString(R.string.regex_match_failed),
                        message = getString(R.string.response_body_prefix) +
                            response +
                            getString(R.string.regex_no_match_message),
                        success = false
                    )
                    return
                }
            } catch (e: Exception) {
                apiTestResult = ApiTestResult(
                    title = getString(R.string.regex_error),
                    message = getString(R.string.error_message_prefix) +
                        e.message +
                        getString(R.string.response_body_prefix) +
                        response,
                    success = false
                )
                return
            }
        }

        var message = getString(R.string.api_request_success)
        if (hasRegex) {
            message += getString(R.string.regex_match_result_prefix) + extractedContent + "\n\n"
        }
        message += getString(R.string.original_response_prefix) + response

        apiTestResult = ApiTestResult(
            title = getString(R.string.test_success),
            message = message,
            success = true
        )
    }

    private fun saveYiYanConfiguration() {
        yiYanPrefs.saveStringSetting("API_URL", apiAddress.trim())
        yiYanPrefs.saveStringSetting("Regular", regex.trim())
        saveSettings("auto_owner_info", true)
        yiYanEnabled = true
        Toast.makeText(this, R.string.configuration_saved_message, Toast.LENGTH_SHORT).show()
    }

    private fun saveSettings(moduleName: String, newValue: Boolean) {
        prefsUtils.saveBooleanSetting(moduleName, newValue)
    }

    companion object {
        private const val TAG = "LockScreenSettingsActivity"
    }
}

private data class ApiTestResult(
    val title: String,
    val message: String,
    val success: Boolean
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LockScreenSettingsScreen(
    title: String,
    yiYanEnabled: Boolean,
    apiAddress: String,
    regex: String,
    chargeWattsOption: String,
    realWattsIntervalOption: String,
    realWattsRefreshInterval: String,
    isTestingApi: Boolean,
    onBack: () -> Unit,
    onYiYanChanged: (Boolean) -> Unit,
    onApiAddressChanged: (String) -> Unit,
    onRegexChanged: (String) -> Unit,
    onTestApi: () -> Unit,
    onChargeWattsOptionChanged: (String) -> Unit,
    onRealWattsIntervalOptionChanged: (String) -> Unit,
    onRealWattsRefreshIntervalChanged: (String) -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = title,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = null
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer
                )
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 960.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 24.dp)
            ) {
                SettingsCard(title = stringResource(R.string.YiYanTile)) {
                    ZToolSwitchRow(
                        title = stringResource(R.string.YiYanSwitchTitle),
                        summary = stringResource(R.string.YiYanSummary),
                        checked = yiYanEnabled,
                        onCheckedChange = onYiYanChanged
                    )
                    if (yiYanEnabled) {
                        YiYanConfigFields(
                            apiAddress = apiAddress,
                            regex = regex,
                            isTestingApi = isTestingApi,
                            onApiAddressChanged = onApiAddressChanged,
                            onRegexChanged = onRegexChanged,
                            onTestApi = onTestApi
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                SettingsCard(title = stringResource(R.string.ChargeWattsTitle)) {
                    DropdownSettingRow(
                        title = stringResource(R.string.ChargeWattsEnableTitle),
                        summary = stringResource(R.string.ChargeWattsSummary),
                        options = stringArrayResource(R.array.watt_options).toList(),
                        selectedOption = chargeWattsOption,
                        onOptionSelected = onChargeWattsOptionChanged
                    )

                    if (chargeWattsOption == stringResource(R.string.watt_option_actual)) {
                        DropdownSettingRow(
                            title = stringResource(R.string.RealWattsRefreshInterval),
                            summary = stringResource(R.string.RealWattsRefreshIntervalSummary),
                            options = stringArrayResource(R.array.real_watt_interval).toList(),
                            selectedOption = realWattsIntervalOption,
                            onOptionSelected = onRealWattsIntervalOptionChanged
                        )

                        if (realWattsIntervalOption == stringResource(R.string.real_watt_custom_refresh_interval_enabled)) {
                            OutlinedTextField(
                                value = realWattsRefreshInterval,
                                onValueChange = onRealWattsRefreshIntervalChanged,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 24.dp, vertical = 8.dp),
                                label = {
                                    Text(
                                        stringResource(
                                            R.string.RealWattsRefreshIntervalInputTip,
                                            realWattsRefreshInterval.toFloatOrNull() ?: 3.0f
                                        )
                                    )
                                },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun YiYanConfigFields(
    apiAddress: String,
    regex: String,
    isTestingApi: Boolean,
    onApiAddressChanged: (String) -> Unit,
    onRegexChanged: (String) -> Unit,
    onTestApi: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = apiAddress,
                onValueChange = onApiAddressChanged,
                modifier = Modifier.weight(1f),
                label = { Text(stringResource(R.string.api_address_hint)) },
                singleLine = true
            )
            Spacer(modifier = Modifier.width(12.dp))
            Button(
                onClick = onTestApi,
                enabled = !isTestingApi
            ) {
                Text(
                    if (isTestingApi) {
                        stringResource(R.string.testing_api_connection)
                    } else {
                        stringResource(R.string.test)
                    }
                )
            }
        }
        OutlinedTextField(
            value = regex,
            onValueChange = onRegexChanged,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            label = { Text(stringResource(R.string.regex_label)) },
            singleLine = true
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DropdownSettingRow(
    title: String,
    summary: String,
    options: List<String>,
    selectedOption: String,
    onOptionSelected: (String) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = summary,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
        Spacer(modifier = Modifier.width(16.dp))
        ZToolDropdownField(
            label = "",
            value = selectedOption,
            options = options,
            optionLabel = { it },
            onOptionSelected = onOptionSelected,
            modifier = Modifier.widthIn(min = 132.dp, max = 180.dp)
        )
    }
}

@Composable
private fun SettingsCard(
    title: String,
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(PaddingValues(horizontal = 24.dp, vertical = 8.dp))
            )
            content()
        }
    }
}

@Composable
private fun RootPermissionDialog(
    onConfirm: () -> Unit,
    onDoNotShowAgain: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onConfirm,
        title = { Text(stringResource(R.string.tooltip_content_description)) },
        text = { Text(stringResource(R.string.systemui_root_permission_required_message)) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDoNotShowAgain) {
                Text(stringResource(R.string.do_not_show_again))
            }
        }
    )
}

@Composable
private fun ApiTestResultDialog(
    result: ApiTestResult,
    onSave: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(result.title) },
        text = { Text(result.message) },
        confirmButton = {
            if (result.success) {
                TextButton(onClick = onSave) {
                    Text(stringResource(R.string.save_configuration_button))
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.restart_no))
            }
        }
    )
}
