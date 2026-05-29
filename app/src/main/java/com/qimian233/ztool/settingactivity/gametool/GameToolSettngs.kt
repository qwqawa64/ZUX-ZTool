package com.qimian233.ztool.settingactivity.gametool

import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.qimian233.ztool.EnhancedShellExecutor
import com.qimian233.ztool.R
import com.qimian233.ztool.hook.modules.SharedPreferencesTool.ModulePreferencesUtils
import com.qimian233.ztool.ui.components.ZToolDropdownField
import com.qimian233.ztool.ui.components.ZToolSettingsDivider
import com.qimian233.ztool.ui.components.ZToolSwitchRow
import com.qimian233.ztool.ui.theme.ZToolTheme
import com.qimian233.ztool.utils.AppChooserDialog

class GameToolSettngs : ComponentActivity() {

    private var appPackageName: String? = null
    private lateinit var prefsUtils: ModulePreferencesUtils

    private var disableGameAudio by mutableStateOf(false)
    private var disguiseDevice by mutableStateOf(false)
    private var fixCpuFrequency by mutableStateOf(false)
    private var fixSocTemperature by mutableStateOf(false)
    private var mistakeTouchMode by mutableStateOf(MistakeTouchMode.Default)
    private var targetGamePackages by mutableStateOf<List<String>>(emptyList())
    private var showRestartConfirmDialog by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val appName = intent.getStringExtra("app_name").orEmpty()
        appPackageName = intent.getStringExtra("app_package")
        prefsUtils = ModulePreferencesUtils(this)
        loadSettings()

        setContent {
            ZToolTheme {
                GameToolSettingsScreen(
                    title = appName + stringResource(R.string.game_tool_settings_title_suffix),
                    disableGameAudio = disableGameAudio,
                    disguiseDevice = disguiseDevice,
                    fixCpuFrequency = fixCpuFrequency,
                    fixSocTemperature = fixSocTemperature,
                    mistakeTouchMode = mistakeTouchMode,
                    whitelistCount = targetGamePackages.size,
                    onBack = ::finish,
                    onRestart = { showRestartConfirmDialog = true },
                    onDisableGameAudioChanged = {
                        disableGameAudio = it
                        saveSettings("disable_GameAudio", it)
                    },
                    onDisguiseDeviceChanged = {
                        disguiseDevice = it
                        saveSettings("disguise_TB322FC", it)
                    },
                    onFixCpuFrequencyChanged = {
                        fixCpuFrequency = it
                        saveSettings("Fix_CpuClock", it)
                    },
                    onFixSocTemperatureChanged = {
                        fixSocTemperature = it
                        saveSettings("Fix_SocTemp", it)
                    },
                    onMistakeTouchModeChanged = ::handleMistakeTouchModeChanged,
                    onSelectWhitelist = ::selectGameApps
                )

                if (showRestartConfirmDialog) {
                    RestartConfirmDialog(
                        packageName = appPackageName.orEmpty(),
                        onConfirm = {
                            showRestartConfirmDialog = false
                            forceStopApp()
                        },
                        onDismiss = { showRestartConfirmDialog = false }
                    )
                }
            }
        }
    }

    private fun loadSettings() {
        disableGameAudio = prefsUtils.loadBooleanSetting("disable_GameAudio", false)
        disguiseDevice = prefsUtils.loadBooleanSetting("disguise_TB322FC", false)
        fixCpuFrequency = prefsUtils.loadBooleanSetting("Fix_CpuClock", false)
        fixSocTemperature = prefsUtils.loadBooleanSetting("Fix_SocTemp", false)
        targetGamePackages = loadWhitelistPackages()

        val autoMistakeTouch = prefsUtils.loadBooleanSetting("auto_mistake_touch", false)
        val mistakeTouchWhiteList = prefsUtils.loadBooleanSetting("MistakeTouchWhiteList", false)
        mistakeTouchMode = when {
            autoMistakeTouch && mistakeTouchWhiteList -> MistakeTouchMode.Whitelist
            autoMistakeTouch -> MistakeTouchMode.AllGames
            else -> MistakeTouchMode.Default
        }
    }

    private fun loadWhitelistPackages(): List<String> {
        return prefsUtils.loadStringSetting("MistakeTouchWhiteListGame", "")
            .split(",")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
    }

    private fun handleMistakeTouchModeChanged(mode: MistakeTouchMode) {
        mistakeTouchMode = mode
        when (mode) {
            MistakeTouchMode.Default -> {
                saveSettings("auto_mistake_touch", false)
                saveSettings("MistakeTouchWhiteList", false)
            }
            MistakeTouchMode.AllGames -> {
                saveSettings("auto_mistake_touch", true)
                saveSettings("MistakeTouchWhiteList", false)
            }
            MistakeTouchMode.Whitelist -> {
                saveSettings("auto_mistake_touch", true)
                saveSettings("MistakeTouchWhiteList", true)
            }
        }
    }

    private fun selectGameApps() {
        AppChooserDialog.show(
            this,
            getPackageNames(),
            targetGamePackages,
            getString(R.string.SelectGame),
            object : AppChooserDialog.AppSelectionCallback {
                override fun onSelected(selectedApps: List<AppChooserDialog.AppInfo>) {
                    val selectedPackageNames = selectedApps.map { it.packageName }
                    selectedPackageNames.forEach {
                        Log.d(TAG, "Selected game package: $it")
                    }
                    targetGamePackages = selectedPackageNames
                    saveConfig(
                        "MistakeTouchWhiteListGame",
                        selectedPackageNames.joinToString(separator = ",", postfix = ",")
                    )
                }

                override fun onCancel() = Unit
            }
        )
    }

    private fun forceStopApp() {
        val packageName = appPackageName
        if (packageName.isNullOrEmpty()) {
            return
        }

        try {
            val process = Runtime.getRuntime().exec("su -c killall $packageName")
            process.waitFor()
        } catch (_: Exception) {
            Toast.makeText(this, R.string.restart_fail_short, Toast.LENGTH_SHORT).show()
        }
    }

    private fun saveSettings(moduleName: String, newValue: Boolean) {
        prefsUtils.saveBooleanSetting(moduleName, newValue)
    }

    private fun saveConfig(configName: String, newValue: String) {
        prefsUtils.saveStringSetting(configName, newValue)
    }

    companion object {
        private const val TAG = "GameToolSettngs"

        fun getPackageNames(): List<String> {
            val result = EnhancedShellExecutor.getInstance()
                .executeRootCommand("ls /data/system_ce/0/managed_apps/")

            if (!result.isSuccess) {
                return emptyList()
            }

            return result.output
                .trim()
                .split(Regex("\\s+"))
                .filter { it.isNotEmpty() }
        }
    }
}

private enum class MistakeTouchMode {
    Default,
    AllGames,
    Whitelist
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GameToolSettingsScreen(
    title: String,
    disableGameAudio: Boolean,
    disguiseDevice: Boolean,
    fixCpuFrequency: Boolean,
    fixSocTemperature: Boolean,
    mistakeTouchMode: MistakeTouchMode,
    whitelistCount: Int,
    onBack: () -> Unit,
    onRestart: () -> Unit,
    onDisableGameAudioChanged: (Boolean) -> Unit,
    onDisguiseDeviceChanged: (Boolean) -> Unit,
    onFixCpuFrequencyChanged: (Boolean) -> Unit,
    onFixSocTemperatureChanged: (Boolean) -> Unit,
    onMistakeTouchModeChanged: (MistakeTouchMode) -> Unit,
    onSelectWhitelist: () -> Unit
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
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onRestart) {
                Icon(
                    imageVector = Icons.Rounded.Refresh,
                    contentDescription = null
                )
            }
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
                SettingsCard(title = stringResource(R.string.Game_Audio_Setting_Title)) {
                    ZToolSwitchRow(
                        title = stringResource(R.string.Game_Audio_title),
                        summary = stringResource(R.string.Game_Audio_summary),
                        checked = disableGameAudio,
                        onCheckedChange = onDisableGameAudioChanged
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                SettingsCard(title = stringResource(R.string.function_title)) {
                    ZToolSwitchRow(
                        title = stringResource(R.string.Device_Model_Disguise),
                        summary = stringResource(R.string.Device_Model_Disguise_summary),
                        checked = disguiseDevice,
                        onCheckedChange = onDisguiseDeviceChanged
                    )
                    ZToolSettingsDivider()
                    ZToolSwitchRow(
                        title = stringResource(R.string.FIx_CPU_Frequency),
                        summary = stringResource(R.string.FIx_CPU_Frequency_summary),
                        checked = fixCpuFrequency,
                        onCheckedChange = onFixCpuFrequencyChanged
                    )
                    ZToolSettingsDivider()
                    ZToolSwitchRow(
                        title = stringResource(R.string.Fix_SocTemp),
                        summary = stringResource(R.string.Fix_SocTemp_summary),
                        checked = fixSocTemperature,
                        onCheckedChange = onFixSocTemperatureChanged
                    )
                    ZToolSettingsDivider()
                    MistakeTouchModeRow(
                        selectedMode = mistakeTouchMode,
                        onModeChanged = onMistakeTouchModeChanged
                    )
                    if (mistakeTouchMode == MistakeTouchMode.Whitelist) {
                        ZToolSettingsDivider()
                        WhitelistRow(
                            whitelistCount = whitelistCount,
                            onClick = onSelectWhitelist
                        )
                    }
                }

                Spacer(modifier = Modifier.height(96.dp))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MistakeTouchModeRow(
    selectedMode: MistakeTouchMode,
    onModeChanged: (MistakeTouchMode) -> Unit
) {
    val options = listOf(
        MistakeTouchMode.Default to stringResource(R.string.SelectDefault),
        MistakeTouchMode.AllGames to stringResource(R.string.SelectAllGames),
        MistakeTouchMode.Whitelist to stringResource(R.string.SelectWhiteList)
    )
    val selectedLabel = options.first { it.first == selectedMode }.second

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.auto_open_prevent_touch_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = stringResource(R.string.auto_open_prevent_touch_summary),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
        Spacer(modifier = Modifier.width(16.dp))
        ZToolDropdownField(
            label = "",
            value = selectedLabel,
            options = options,
            optionLabel = { it.second },
            onOptionSelected = { (mode, _) -> onModeChanged(mode) },
            modifier = Modifier.widthIn(min = 132.dp, max = 180.dp)
        )
    }
}

@Composable
private fun WhitelistRow(
    whitelistCount: Int,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.whitelist_config_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = stringResource(R.string.whitelist_count, whitelistCount),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
        Spacer(modifier = Modifier.width(16.dp))
        Icon(
            imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
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
private fun RestartConfirmDialog(
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
