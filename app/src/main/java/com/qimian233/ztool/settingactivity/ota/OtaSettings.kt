package com.qimian233.ztool.settingactivity.ota

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
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
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.qimian233.ztool.EnhancedShellExecutor
import com.qimian233.ztool.R
import com.qimian233.ztool.hook.modules.SharedPreferencesTool.ModulePreferencesUtils
import com.qimian233.ztool.ui.components.ZToolSwitchRow
import com.qimian233.ztool.ui.theme.ZToolTheme
import com.qimian233.ztool.utils.GetPCFlashFirmware
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

class OtaSettings : ComponentActivity() {

    private lateinit var prefsUtils: ModulePreferencesUtils
    private var appPackageName: String? = null

    private var disableOtaCheck by mutableStateOf(false)
    private var customVersion by mutableStateOf("")
    private var customDeviceId by mutableStateOf("")
    private var currentVersion by mutableStateOf("")
    private var currentSn by mutableStateOf("")
    private var firmwareSnInput by mutableStateOf("")
    private var isFetchingOtaInfo by mutableStateOf(false)
    private var isFetchingFirmware by mutableStateOf(false)
    private var otaInfoResult by mutableStateOf<OtaInfoResult?>(null)
    private var firmwareResult by mutableStateOf<FirmwareResult?>(null)
    private var errorDialogMessage by mutableStateOf<String?>(null)
    private var showRestartDialog by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val appName = intent.getStringExtra("app_name").orEmpty()
        appPackageName = intent.getStringExtra("app_package")
        prefsUtils = ModulePreferencesUtils(this)
        prefsUtils.saveBooleanSetting("custom_ota_parameters", true)

        loadSettings()
        loadCurrentDeviceInfo()

        setContent {
            ZToolTheme {
                OtaSettingsScreen(
                    title = appName + stringResource(R.string.ota_settings_title_suffix),
                    disableOtaCheck = disableOtaCheck,
                    customVersion = customVersion,
                    customDeviceId = customDeviceId,
                    currentVersion = currentVersion,
                    currentSn = currentSn,
                    firmwareSnInput = firmwareSnInput,
                    isFetchingOtaInfo = isFetchingOtaInfo,
                    isFetchingFirmware = isFetchingFirmware,
                    otaInfoResult = otaInfoResult,
                    firmwareResult = firmwareResult,
                    onBack = ::finish,
                    onDisableOtaCheckChanged = {
                        disableOtaCheck = it
                        prefsUtils.saveBooleanSetting("disable_OtaCheck", it)
                    },
                    onFetchOtaInfo = ::fetchOtaInfo,
                    onFirmwareSnChanged = { firmwareSnInput = it },
                    onFetchFirmware = ::fetchFirmware,
                    onCustomVersionChanged = {
                        customVersion = it
                        prefsUtils.saveStringSetting("Custom_ota_target_versionName", it)
                    },
                    onCustomDeviceIdChanged = {
                        customDeviceId = it
                        prefsUtils.saveStringSetting("Custom_ota_target_deviceID", it)
                    },
                    onCopyDownloadLink = {
                        copyToClipboard(it)
                        Toast.makeText(this, R.string.download_link_copied, Toast.LENGTH_SHORT).show()
                    },
                    onCopyChangelog = {
                        copyToClipboard(it)
                        Toast.makeText(this, R.string.changelog_copied, Toast.LENGTH_SHORT).show()
                    },
                    onCopyPassword = {
                        copyToClipboard(it)
                        Toast.makeText(this, R.string.password_copied, Toast.LENGTH_SHORT).show()
                    },
                    onRestartScope = { showRestartDialog = true }
                )

                errorDialogMessage?.let { message ->
                    ErrorDialog(
                        message = message,
                        onDismiss = { errorDialogMessage = null }
                    )
                }

                if (showRestartDialog) {
                    RestartScopeDialog(
                        packageName = appPackageName.orEmpty(),
                        onConfirm = {
                            showRestartDialog = false
                            forceStopApp()
                        },
                        onDismiss = { showRestartDialog = false }
                    )
                }
            }
        }
    }

    private fun loadSettings() {
        disableOtaCheck = prefsUtils.loadBooleanSetting("disable_OtaCheck", false)
        customVersion = prefsUtils.loadStringSetting("Custom_ota_target_versionName", "")
        customDeviceId = prefsUtils.loadStringSetting("Custom_ota_target_deviceID", "")
        currentVersion = getString(R.string.loading_ellipsis)
        currentSn = getString(R.string.loading_ellipsis)
    }

    private fun loadCurrentDeviceInfo() {
        Thread {
            val executor = EnhancedShellExecutor.getInstance()
            val versionResult = executor.executeCommand("getprop ro.build.display.id")
            val version = if (versionResult.isSuccess && versionResult.output.isNotEmpty()) {
                versionResult.output.trim()
            } else {
                getString(R.string.unknown)
            }

            val sn = getMachineSnByProps()?.takeIf { it.isNotEmpty() } ?: getString(R.string.unknown)
            runOnUiThread {
                currentVersion = version
                currentSn = sn
                if (firmwareSnInput.isEmpty() && sn != getString(R.string.unknown)) {
                    firmwareSnInput = sn
                }
            }
        }.start()
    }

    private fun fetchOtaInfo() {
        isFetchingOtaInfo = true
        Thread {
            try {
                val filePath = "/data_mirror/data_ce/null/0/com.lenovo.tbengine/shared_prefs/lenovo_row_ota_package_info.xml"
                val xmlContent = readFileWithRoot(filePath)
                val otaInfo = parseOtaInfoXml(xmlContent)
                val result = otaInfo.toOtaInfoResult()
                runOnUiThread {
                    otaInfoResult = result
                    isFetchingOtaInfo = false
                }
            } catch (e: Exception) {
                Log.e(TAG, "读取OTA信息失败", e)
                runOnUiThread {
                    isFetchingOtaInfo = false
                    errorDialogMessage = getString(R.string.ota_info_fetch_failed) + e.message
                }
            }
        }.start()
    }

    private fun fetchFirmware() {
        val sn = firmwareSnInput.trim().ifEmpty { getMachineSnByProps().orEmpty() }
        if (sn.isEmpty()) {
            errorDialogMessage = getString(R.string.SN_default_hint)
            return
        }

        isFetchingFirmware = true
        GetPCFlashFirmware().queryFirmwareAsync(sn) { firmwareInfo ->
            runOnUiThread {
                isFetchingFirmware = false
                if (firmwareInfo != null && firmwareInfo.size >= 6) {
                    firmwareResult = FirmwareResult(
                        downloadUrl = firmwareInfo[0].orEmpty(),
                        password = firmwareInfo[1].orEmpty(),
                        platform = firmwareInfo[2].orEmpty(),
                        method = firmwareInfo[3].orEmpty(),
                        firstUploadTime = formatTimestamp(firmwareInfo[4].toLongOrNull() ?: 0L),
                        lastUpdateTime = formatTimestamp(firmwareInfo[5].toLongOrNull() ?: 0L)
                    )
                } else {
                    errorDialogMessage = getString(R.string.PCFlashFirmwareFetch_failed_message)
                }
            }
        }
    }

    private fun getMachineSnByProps(): String? {
        val shellExecutor = EnhancedShellExecutor.getInstance()
        val keys = listOf("ro.odm.lenovo.gsn", "ro.serialno", "ro.boot.serialno")
        for (key in keys) {
            val result = shellExecutor.executeRootCommand("getprop $key", 3)
            if (result.isSuccess && result.output.trim().isNotEmpty()) {
                return result.output.trim()
            }
        }
        return null
    }

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
        val fromVersion = getOrDefault("mUpdateFromVersion", getString(R.string.unknown))
        val toVersion = getOrDefault("updateToVersion", getString(R.string.unknown))
        val downloadUrl = getOrDefault("downloadUrl", getString(R.string.no_download_link))
        val size = getOrDefault("size", "0").toLongOrNull() ?: 0L
        val md5 = getOrDefault("md5", getString(R.string.unknown))
        val changelog = getChangelogByLocale(this)
        val formattedSize = formatFileSize(size)

        return OtaInfoResult(
            fromVersion = fromVersion,
            toVersion = toVersion,
            downloadUrl = downloadUrl,
            formattedSize = formattedSize,
            md5 = md5,
            changelog = changelog,
            changelogCopyText = getString(
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
        val locale = resources.configuration.locales[0] ?: Locale.getDefault()
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

        return otaInfo.getOrDefault("HashMap.en", getString(R.string.no_changelog_available))
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

    private fun forceStopApp() {
        val packageName = appPackageName
        if (packageName.isNullOrEmpty()) return

        try {
            val process = Runtime.getRuntime().exec("su -c killall $packageName")
            val process2 = Runtime.getRuntime().exec("su -c killall com.lenovo.tbengine")
            process.waitFor()
            process2.waitFor()
        } catch (_: Exception) {
            Toast.makeText(this, R.string.restart_failed, Toast.LENGTH_SHORT).show()
        }
    }

    private fun copyToClipboard(text: String) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText(getString(R.string.ota_info_clipboard_label), text)
        clipboard.setPrimaryClip(clip)
    }

    companion object {
        private const val TAG = "OtaSettings"
    }
}

private data class OtaInfoResult(
    val fromVersion: String,
    val toVersion: String,
    val downloadUrl: String,
    val formattedSize: String,
    val md5: String,
    val changelog: String,
    val changelogCopyText: String
)

private data class FirmwareResult(
    val downloadUrl: String,
    val password: String,
    val platform: String,
    val method: String,
    val firstUploadTime: String,
    val lastUpdateTime: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OtaSettingsScreen(
    title: String,
    disableOtaCheck: Boolean,
    customVersion: String,
    customDeviceId: String,
    currentVersion: String,
    currentSn: String,
    firmwareSnInput: String,
    isFetchingOtaInfo: Boolean,
    isFetchingFirmware: Boolean,
    otaInfoResult: OtaInfoResult?,
    firmwareResult: FirmwareResult?,
    onBack: () -> Unit,
    onDisableOtaCheckChanged: (Boolean) -> Unit,
    onFetchOtaInfo: () -> Unit,
    onFirmwareSnChanged: (String) -> Unit,
    onFetchFirmware: () -> Unit,
    onCustomVersionChanged: (String) -> Unit,
    onCustomDeviceIdChanged: (String) -> Unit,
    onCopyDownloadLink: (String) -> Unit,
    onCopyChangelog: (String) -> Unit,
    onCopyPassword: (String) -> Unit,
    onRestartScope: () -> Unit
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
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = null)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer
                )
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onRestartScope,
                icon = { Icon(Icons.Rounded.Refresh, contentDescription = null) },
                text = { Text(stringResource(R.string.restart_yes)) }
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
                    .padding(bottom = 88.dp)
            ) {
                SettingsCard(title = stringResource(R.string.Ota_Setting_Title)) {
                    ZToolSwitchRow(
                        title = stringResource(R.string.OtaDisable_title),
                        summary = stringResource(R.string.OtaDisable_summary),
                        checked = disableOtaCheck,
                        onCheckedChange = onDisableOtaCheckChanged
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                OtaInfoCard(
                    isFetching = isFetchingOtaInfo,
                    result = otaInfoResult,
                    onFetch = onFetchOtaInfo,
                    onCopyDownloadLink = onCopyDownloadLink,
                    onCopyChangelog = onCopyChangelog
                )

                Spacer(modifier = Modifier.height(16.dp))

                FirmwareCard(
                    sn = firmwareSnInput,
                    currentSn = currentSn,
                    isFetching = isFetchingFirmware,
                    result = firmwareResult,
                    onSnChanged = onFirmwareSnChanged,
                    onFetch = onFetchFirmware,
                    onCopyDownloadLink = onCopyDownloadLink,
                    onCopyPassword = onCopyPassword
                )

                Spacer(modifier = Modifier.height(16.dp))

                SettingsCard(title = stringResource(R.string.Ota_Custom_Params_Title)) {
                    Column(modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)) {
                        Text(
                            text = stringResource(R.string.Ota_Custom_Params_Desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = stringResource(R.string.current_version_fmt, currentVersion),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        OutlinedTextField(
                            value = customVersion,
                            onValueChange = onCustomVersionChanged,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp),
                            label = { Text(stringResource(R.string.Ota_Custom_Version_Hint)) },
                            singleLine = true
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = stringResource(R.string.current_sn_fmt, currentSn),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        OutlinedTextField(
                            value = customDeviceId,
                            onValueChange = onCustomDeviceIdChanged,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp),
                            label = { Text(stringResource(R.string.Ota_Custom_DeviceID_Hint)) },
                            singleLine = true
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun OtaInfoCard(
    isFetching: Boolean,
    result: OtaInfoResult?,
    onFetch: () -> Unit,
    onCopyDownloadLink: (String) -> Unit,
    onCopyChangelog: (String) -> Unit
) {
    SettingsCard(title = stringResource(R.string.OtaInfoFetch_title)) {
        Column(modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)) {
            Text(
                text = stringResource(R.string.OtaInfoFetch_summary),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(12.dp))
            Button(
                onClick = onFetch,
                enabled = !isFetching
            ) {
                Text(
                    if (isFetching) {
                        stringResource(R.string.loading_ellipsis)
                    } else {
                        stringResource(R.string.OtaInfoFetch_title)
                    }
                )
            }

            if (result != null) {
                Spacer(modifier = Modifier.height(16.dp))
                ResultText(
                    title = stringResource(R.string.ota_update_info_title),
                    body = stringResource(
                        R.string.version_info_format,
                        result.fromVersion,
                        result.toVersion
                    )
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = stringResource(R.string.updateLog),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = result.changelog,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
                Spacer(modifier = Modifier.height(12.dp))
                ResultText(
                    title = stringResource(R.string.copy_download_link),
                    body = stringResource(
                        R.string.download_info_format,
                        result.downloadUrl,
                        result.formattedSize,
                        result.md5
                    )
                )
                Spacer(modifier = Modifier.height(12.dp))
                Row {
                    Button(onClick = { onCopyDownloadLink(result.downloadUrl) }) {
                        Icon(Icons.Rounded.ContentCopy, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(stringResource(R.string.copy_download_link))
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    TextButton(onClick = { onCopyChangelog(result.changelogCopyText) }) {
                        Text(stringResource(R.string.copy_changelog))
                    }
                }
            }
        }
    }
}

@Composable
private fun FirmwareCard(
    sn: String,
    currentSn: String,
    isFetching: Boolean,
    result: FirmwareResult?,
    onSnChanged: (String) -> Unit,
    onFetch: () -> Unit,
    onCopyDownloadLink: (String) -> Unit,
    onCopyPassword: (String) -> Unit
) {
    SettingsCard(title = stringResource(R.string.PCFlashFirmwareFetch_title)) {
        Column(modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)) {
            Text(
                text = stringResource(R.string.PCFlashFirmwareFetch_summary),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedTextField(
                value = sn,
                onValueChange = onSnChanged,
                modifier = Modifier.fillMaxWidth(),
                label = {
                    Text(
                        if (currentSn.isNotEmpty() && currentSn != stringResource(R.string.loading_ellipsis)) {
                            stringResource(R.string.SN_current_machine_hint, currentSn)
                        } else {
                            stringResource(R.string.SN_default_hint)
                        }
                    )
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii)
            )
            Spacer(modifier = Modifier.height(12.dp))
            Button(
                onClick = onFetch,
                enabled = !isFetching
            ) {
                Text(
                    if (isFetching) {
                        stringResource(R.string.fetching_firmware_info)
                    } else {
                        stringResource(R.string.confirm)
                    }
                )
            }

            if (result != null) {
                Spacer(modifier = Modifier.height(16.dp))
                ResultText(
                    title = stringResource(R.string.PCFlashFirmwareFetch_result),
                    body = buildString {
                        append(stringResource(R.string.firmware_download_link)).append(result.downloadUrl).append("\n")
                        append(stringResource(R.string.firmware_extract_password)).append(result.password).append("\n")
                        append(stringResource(R.string.firmware_platform_and_method))
                            .append(result.platform)
                            .append(stringResource(R.string.firmware_platform_suffix))
                            .append(result.method)
                            .append("\n")
                        append(stringResource(R.string.firmware_first_upload_time)).append(result.firstUploadTime).append("\n")
                        append(stringResource(R.string.firmware_last_update_time)).append(result.lastUpdateTime)
                    }
                )
                Spacer(modifier = Modifier.height(12.dp))
                Row {
                    Button(onClick = { onCopyDownloadLink(result.downloadUrl) }) {
                        Icon(Icons.Rounded.ContentCopy, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(stringResource(R.string.copy_download_link))
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    TextButton(onClick = { onCopyPassword(result.password) }) {
                        Text(stringResource(R.string.copy_password))
                    }
                }
            }
        }
    }
}

@Composable
private fun ResultText(
    title: String,
    body: String
) {
    Column {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp)
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
private fun ErrorDialog(
    message: String,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.error_title)) },
        text = { Text(message) },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.confirm))
            }
        }
    )
}

@Composable
private fun RestartScopeDialog(
    packageName: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.restart_xp_title)) },
        text = {
            Text(
                stringResource(R.string.restart_xp_message_header) +
                    packageName +
                    ",com.lenovo.tbengine" +
                    stringResource(R.string.restart_xp_message)
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.restart_yes))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.restart_no))
            }
        }
    )
}
