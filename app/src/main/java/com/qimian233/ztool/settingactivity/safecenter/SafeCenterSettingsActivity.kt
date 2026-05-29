package com.qimian233.ztool.settingactivity.safecenter

import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
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
import com.qimian233.ztool.ui.components.ZToolSettingsDivider
import com.qimian233.ztool.ui.components.ZToolSwitchRow
import com.qimian233.ztool.ui.theme.ZToolTheme

class SafeCenterSettingsActivity : ComponentActivity() {

    private var appPackageName: String? = null
    private lateinit var prefsUtils: ModulePreferencesUtils
    private lateinit var shellExecutor: EnhancedShellExecutor

    private var uiState by mutableStateOf(SafeCenterSettingsUiState())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val appName = intent.getStringExtra("app_name").orEmpty()
        appPackageName = intent.getStringExtra("app_package")
        prefsUtils = ModulePreferencesUtils(this)
        shellExecutor = EnhancedShellExecutor.getInstance()
        loadSettings()

        setContent {
            ZToolTheme {
                SafeCenterSettingsScreen(
                    title = appName + stringResource(R.string.safe_center_settings_title_suffix),
                    state = uiState,
                    onBack = ::finish,
                    onRestart = {
                        if (uiState.isRestartProcessing) {
                            Log.d(TAG, "Restart is already processing, ignoring duplicate click")
                        } else {
                            uiState = uiState.copy(showRestartConfirmDialog = true)
                        }
                    },
                    onDefaultEnableAutorunChanged = {
                        uiState = uiState.copy(defaultEnableAutorun = it)
                        saveSettings("default_enable_autorun", it)
                    },
                    onBlockSafeCenterScanChanged = {
                        uiState = uiState.copy(blockSafeCenterScan = it)
                        saveSettings("block_safecenter_scan", it)
                    },
                    onDocumentsUiBypassChanged = {
                        uiState = uiState.copy(documentsUiBypass = it)
                        saveSettings("documents_ui_bypass", it)
                    }
                )

                if (uiState.showRestartConfirmDialog) {
                    RestartConfirmDialog(
                        packageName = appPackageName.orEmpty(),
                        onConfirm = {
                            uiState = uiState.copy(showRestartConfirmDialog = false)
                            forceStopApp()
                        },
                        onDismiss = { uiState = uiState.copy(showRestartConfirmDialog = false) }
                    )
                }
            }
        }
    }

    private fun loadSettings() {
        uiState = uiState.copy(
            defaultEnableAutorun = prefsUtils.loadBooleanSetting("default_enable_autorun", false),
            blockSafeCenterScan = prefsUtils.loadBooleanSetting("block_safecenter_scan", false),
            documentsUiBypass = prefsUtils.loadBooleanSetting("documents_ui_bypass", false)
        )
    }

    private fun saveSettings(moduleName: String, newValue: Boolean) {
        prefsUtils.saveBooleanSetting(moduleName, newValue)
    }

    private fun forceStopApp() {
        val packageName = appPackageName
        if (packageName.isNullOrEmpty()) {
            Toast.makeText(this, R.string.empty_package_name_message, Toast.LENGTH_SHORT).show()
            return
        }

        if (uiState.isRestartProcessing) {
            Log.d(TAG, "Restart is already processing")
            return
        }

        uiState = uiState.copy(isRestartProcessing = true)

        Thread {
            try {
                val appResult = shellExecutor.executeRootCommand("am force-stop $packageName", 5)
                val documentsResult = shellExecutor.executeRootCommand("am force-stop com.android.documentsui", 5)
                val success = appResult.isSuccess && documentsResult.isSuccess

                if (!success) {
                    Log.w(TAG, "am force-stop failed, trying killall")
                    val fallbackResult = shellExecutor.executeRootCommand("killall $packageName", 5)
                    runOnUiThread {
                        if (fallbackResult.isSuccess) {
                            Toast.makeText(
                                this,
                                R.string.app_process_restarted_message,
                                Toast.LENGTH_SHORT
                            ).show()
                        } else {
                            Toast.makeText(
                                this,
                                getString(R.string.restart_fail_prefix) + fallbackResult.error,
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                        resetRestartButton()
                    }
                } else {
                    runOnUiThread {
                        Toast.makeText(
                            this,
                            R.string.app_process_restarted_message,
                            Toast.LENGTH_SHORT
                        ).show()
                        resetRestartButton()
                    }
                }

                Log.d(TAG, "Force stop result: ${if (success) "success" else "failed"}")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to force stop app: ${e.message}")
                runOnUiThread {
                    Toast.makeText(
                        this,
                        getString(R.string.restart_fail_prefix) + e.message,
                        Toast.LENGTH_SHORT
                    ).show()
                    resetRestartButton()
                }
            }
        }.start()
    }

    private fun resetRestartButton() {
        uiState = uiState.copy(isRestartProcessing = false)
    }

    companion object {
        private const val TAG = "SafeCenterSettings"
    }
}

private data class SafeCenterSettingsUiState(
    val defaultEnableAutorun: Boolean = false,
    val blockSafeCenterScan: Boolean = false,
    val documentsUiBypass: Boolean = false,
    val showRestartConfirmDialog: Boolean = false,
    val isRestartProcessing: Boolean = false
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SafeCenterSettingsScreen(
    title: String,
    state: SafeCenterSettingsUiState,
    onBack: () -> Unit,
    onRestart: () -> Unit,
    onDefaultEnableAutorunChanged: (Boolean) -> Unit,
    onBlockSafeCenterScanChanged: (Boolean) -> Unit,
    onDocumentsUiBypassChanged: (Boolean) -> Unit
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
            FloatingActionButton(
                onClick = onRestart,
                containerColor = if (state.isRestartProcessing) {
                    MaterialTheme.colorScheme.surfaceVariant
                } else {
                    MaterialTheme.colorScheme.primaryContainer
                }
            ) {
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
                SettingsCard(title = stringResource(R.string.default_allow_autorun_title)) {
                    ZToolSwitchRow(
                        title = stringResource(R.string.default_allow_autorun_enable_title),
                        summary = stringResource(R.string.default_allow_autorun_enable_summary),
                        checked = state.defaultEnableAutorun,
                        onCheckedChange = onDefaultEnableAutorunChanged
                    )
                    ZToolSettingsDivider()
                    ZToolSwitchRow(
                        title = stringResource(R.string.DisableSafeScanTitle),
                        summary = stringResource(R.string.DisableSafeScanSummary),
                        checked = state.blockSafeCenterScan,
                        onCheckedChange = onBlockSafeCenterScanChanged
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                SettingsCard(title = stringResource(R.string.sec_title_function)) {
                    ZToolSwitchRow(
                        title = stringResource(R.string.bypassDocementsUI),
                        summary = stringResource(R.string.bypassDocementsUISummary),
                        checked = state.documentsUiBypass,
                        onCheckedChange = onDocumentsUiBypassChanged
                    )
                }

                Spacer(modifier = Modifier.height(96.dp))
            }
        }
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
                    ", com.android.documentsui" +
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
