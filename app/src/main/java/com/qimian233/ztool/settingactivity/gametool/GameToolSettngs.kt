package com.qimian233.ztool.settingactivity.gametool

import android.os.Bundle
import android.widget.Toast
import com.qimian233.ztool.utils.ZToolComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.qimian233.ztool.R
import com.qimian233.ztool.data.gametool.GameToolSettingsRepository
import com.qimian233.ztool.ui.components.SettingItem
import com.qimian233.ztool.ui.components.SettingSection
import com.qimian233.ztool.ui.components.ZToolDialog
import com.qimian233.ztool.ui.components.ZToolPopupMenuSettingRow
import com.qimian233.ztool.ui.components.ZToolScaffold
import com.qimian233.ztool.ui.components.ZToolSettingsList
import com.qimian233.ztool.ui.components.ZToolTopAppBar
import com.qimian233.ztool.ui.theme.ZToolTheme
import com.qimian233.ztool.utils.AppChooserDialog
import com.qimian233.ztool.viewmodel.GameToolSettingsUiState
import com.qimian233.ztool.viewmodel.GameToolSettingsViewModel
import com.qimian233.ztool.viewmodel.MistakeTouchMode

class GameToolSettngs : ZToolComponentActivity() {

    private var appPackageName: String? = null
    private lateinit var viewModel: GameToolSettingsViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val appName = intent.getStringExtra("app_name").orEmpty()
        appPackageName = intent.getStringExtra("app_package")
        val repository = GameToolSettingsRepository(applicationContext)
        viewModel = ViewModelProvider(
            this,
            GameToolSettingsViewModelFactory(repository)
        )[GameToolSettingsViewModel::class.java]
        viewModel.loadSettings()

        setContent {
            val uiState by viewModel.uiState.collectAsState()

            ZToolTheme {
                GameToolSettingsScreen(
                    title = appName,
                    state = uiState,
                    onBack = ::finish,
                    onRestart = viewModel::showRestartConfirmDialog,
                    onDisableGameAudioChanged = viewModel::setDisableGameAudio,
                    onDisguiseDeviceChanged = viewModel::setDisguiseDevice,
                    onFixCpuFrequencyChanged = viewModel::setFixCpuFrequency,
                    onFixSocTemperatureChanged = viewModel::setFixSocTemperature,
                    onMistakeTouchModeChanged = viewModel::setMistakeTouchMode,
                    onSelectWhitelist = ::selectGameApps
                )

                if (uiState.showRestartConfirmDialog) {
                    RestartConfirmDialog(
                        packageName = appPackageName.orEmpty(),
                        onConfirm = {
                            viewModel.forceStopPackage(
                                packageName = appPackageName.orEmpty(),
                                onFailure = ::showRestartFailure
                            )
                        },
                        onDismiss = viewModel::dismissRestartConfirmDialog
                    )
                }
            }
        }
    }

    private fun selectGameApps() {
        val uiState = viewModel.uiState.value
        AppChooserDialog.show(
            this,
            viewModel.loadManagedGamePackages(),
            uiState.targetGamePackages,
            getString(R.string.SelectGame),
            object : AppChooserDialog.AppSelectionCallback {
                override fun onSelected(selectedApps: List<AppChooserDialog.AppInfo>) {
                    val selectedPackageNames = selectedApps.map { it.packageName }
                    viewModel.setWhitelistPackages(selectedPackageNames)
                }

                override fun onCancel() = Unit
            }
        )
    }

    private fun showRestartFailure() {
        runOnUiThread {
            Toast.makeText(this, R.string.restart_fail_short, Toast.LENGTH_SHORT).show()
        }
    }
}

private class GameToolSettingsViewModelFactory(
    private val repository: GameToolSettingsRepository
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(GameToolSettingsViewModel::class.java)) {
            return GameToolSettingsViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}

@Composable
private fun GameToolSettingsScreen(
    title: String,
    state: GameToolSettingsUiState,
    onBack: () -> Unit,
    onRestart: () -> Unit,
    onDisableGameAudioChanged: (Boolean) -> Unit,
    onDisguiseDeviceChanged: (Boolean) -> Unit,
    onFixCpuFrequencyChanged: (Boolean) -> Unit,
    onFixSocTemperatureChanged: (Boolean) -> Unit,
    onMistakeTouchModeChanged: (MistakeTouchMode) -> Unit,
    onSelectWhitelist: () -> Unit
) {
    ZToolScaffold(
        topBar = {
            ZToolTopAppBar(
                title = title,
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = null
                        )
                    }
                }
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
                ZToolSettingsList(
                    sections = gameToolSettingsSections(
                        state = state,
                        onDisableGameAudioChanged = onDisableGameAudioChanged,
                        onDisguiseDeviceChanged = onDisguiseDeviceChanged,
                        onFixCpuFrequencyChanged = onFixCpuFrequencyChanged,
                        onFixSocTemperatureChanged = onFixSocTemperatureChanged,
                        onMistakeTouchModeChanged = onMistakeTouchModeChanged,
                        onSelectWhitelist = onSelectWhitelist
                    ),
                    bottomPadding = 96.dp
                )
            }
        }
    }
}

@Composable
private fun gameToolSettingsSections(
    state: GameToolSettingsUiState,
    onDisableGameAudioChanged: (Boolean) -> Unit,
    onDisguiseDeviceChanged: (Boolean) -> Unit,
    onFixCpuFrequencyChanged: (Boolean) -> Unit,
    onFixSocTemperatureChanged: (Boolean) -> Unit,
    onMistakeTouchModeChanged: (MistakeTouchMode) -> Unit,
    onSelectWhitelist: () -> Unit
): List<SettingSection> {
    val functionItems = buildList {
        add(
            SettingItem.Switch(
                title = stringResource(R.string.Device_Model_Disguise),
                summary = stringResource(R.string.Device_Model_Disguise_summary),
                checked = state.disguiseDevice,
                onCheckedChange = onDisguiseDeviceChanged
            )
        )
        add(
            SettingItem.Switch(
                title = stringResource(R.string.FIx_CPU_Frequency),
                summary = stringResource(R.string.FIx_CPU_Frequency_summary),
                checked = state.fixCpuFrequency,
                onCheckedChange = onFixCpuFrequencyChanged
            )
        )
        add(
            SettingItem.Switch(
                title = stringResource(R.string.Fix_SocTemp),
                summary = stringResource(R.string.Fix_SocTemp_summary),
                checked = state.fixSocTemperature,
                onCheckedChange = onFixSocTemperatureChanged
            )
        )
        add(
            SettingItem.Custom(
                content = {
                    MistakeTouchModeRow(
                        selectedMode = state.mistakeTouchMode,
                        onModeChanged = onMistakeTouchModeChanged
                    )
                }
            )
        )
        if (state.mistakeTouchMode == MistakeTouchMode.Whitelist) {
            add(
                SettingItem.Custom(
                    content = {
                        WhitelistRow(
                            whitelistCount = state.whitelistCount,
                            onClick = onSelectWhitelist
                        )
                    }
                )
            )
        }
    }

    return listOf(
        SettingSection(
            title = stringResource(R.string.Game_Audio_Setting_Title),
            items = listOf(
                SettingItem.Switch(
                    title = stringResource(R.string.Game_Audio_title),
                    summary = stringResource(R.string.Game_Audio_summary),
                    checked = state.disableGameAudio,
                    onCheckedChange = onDisableGameAudioChanged
                )
            )
        ),
        SettingSection(
            title = stringResource(R.string.function_title),
            items = functionItems
        )
    )
}

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

    ZToolPopupMenuSettingRow(
        title = stringResource(R.string.auto_open_prevent_touch_title),
        summary = stringResource(R.string.auto_open_prevent_touch_summary),
        value = selectedLabel,
        options = options,
        optionLabel = { it.second },
        onOptionSelected = { (mode, _) -> onModeChanged(mode) }
    )
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
private fun RestartConfirmDialog(
    packageName: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    ZToolDialog(
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
