package com.qimian233.ztool.settingactivity.packageinstaller

import android.os.Bundle
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
import androidx.compose.ui.unit.dp
import com.qimian233.ztool.R
import com.qimian233.ztool.hook.modules.SharedPreferencesTool.ModulePreferencesUtils
import com.qimian233.ztool.ui.components.ZToolSettingsDivider
import com.qimian233.ztool.ui.components.ZToolSwitchRow
import com.qimian233.ztool.ui.theme.ZToolTheme

class packageinstallersettings : ComponentActivity() {

    private var appPackageName: String? = null
    private lateinit var prefsUtils: ModulePreferencesUtils

    private var disableScanApk by mutableStateOf(false)
    private var alwaysAllowPermission by mutableStateOf(false)
    private var skipWarnPage by mutableStateOf(false)
    private var disableInstallerAd by mutableStateOf(false)
    private var packageInstallerStyleHook by mutableStateOf(false)
    private var disableDeletePackage by mutableStateOf(false)
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
                PackageInstallerSettingsScreen(
                    title = appName + stringResource(R.string.detailed_settings_suffix),
                    disableScanApk = disableScanApk,
                    alwaysAllowPermission = alwaysAllowPermission,
                    skipWarnPage = skipWarnPage,
                    disableInstallerAd = disableInstallerAd,
                    packageInstallerStyleHook = packageInstallerStyleHook,
                    disableDeletePackage = disableDeletePackage,
                    onBack = ::finish,
                    onRestart = { showRestartConfirmDialog = true },
                    onDisableScanApkChanged = {
                        disableScanApk = it
                        saveSettings("disable_scanAPK", it)
                    },
                    onAlwaysAllowPermissionChanged = {
                        alwaysAllowPermission = it
                        saveSettings("Always_AllowPermission", it)
                    },
                    onSkipWarnPageChanged = {
                        skipWarnPage = it
                        saveSettings("Skip_WarnPage", it)
                    },
                    onDisableInstallerAdChanged = {
                        disableInstallerAd = it
                        saveSettings("disable_installerAD", it)
                    },
                    onPackageInstallerStyleHookChanged = {
                        packageInstallerStyleHook = it
                        saveSettings("packageInstallerStyle_hook", it)
                    },
                    onDisableDeletePackageChanged = {
                        disableDeletePackage = it
                        saveSettings("package_installer_disable_delete", it)
                    }
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
        disableScanApk = prefsUtils.loadBooleanSetting("disable_scanAPK", false)
        alwaysAllowPermission = prefsUtils.loadBooleanSetting("Always_AllowPermission", false)
        skipWarnPage = prefsUtils.loadBooleanSetting("Skip_WarnPage", false)
        disableInstallerAd = prefsUtils.loadBooleanSetting("disable_installerAD", false)
        packageInstallerStyleHook = prefsUtils.loadBooleanSetting("packageInstallerStyle_hook", false)
        disableDeletePackage = prefsUtils.loadBooleanSetting("package_installer_disable_delete", false)
    }

    private fun saveSettings(moduleName: String, newValue: Boolean) {
        prefsUtils.saveBooleanSetting(moduleName, newValue)
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
            Toast.makeText(this, R.string.restart_fail_simple, Toast.LENGTH_SHORT).show()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PackageInstallerSettingsScreen(
    title: String,
    disableScanApk: Boolean,
    alwaysAllowPermission: Boolean,
    skipWarnPage: Boolean,
    disableInstallerAd: Boolean,
    packageInstallerStyleHook: Boolean,
    disableDeletePackage: Boolean,
    onBack: () -> Unit,
    onRestart: () -> Unit,
    onDisableScanApkChanged: (Boolean) -> Unit,
    onAlwaysAllowPermissionChanged: (Boolean) -> Unit,
    onSkipWarnPageChanged: (Boolean) -> Unit,
    onDisableInstallerAdChanged: (Boolean) -> Unit,
    onPackageInstallerStyleHookChanged: (Boolean) -> Unit,
    onDisableDeletePackageChanged: (Boolean) -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
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
                SettingsCard(title = stringResource(R.string.sec_title_function)) {
                    ZToolSwitchRow(
                        title = stringResource(R.string.Disable_ScanAPK_Title),
                        summary = stringResource(R.string.Disable_ScanAPK_Summary),
                        checked = disableScanApk,
                        onCheckedChange = onDisableScanApkChanged
                    )
                    ZToolSettingsDivider()
                    ZToolSwitchRow(
                        title = stringResource(R.string.OnlyAllow_Title),
                        summary = stringResource(R.string.OnlyAllow_Summary),
                        checked = alwaysAllowPermission,
                        onCheckedChange = onAlwaysAllowPermissionChanged
                    )
                    ZToolSettingsDivider()
                    ZToolSwitchRow(
                        title = stringResource(R.string.skip_warn_page_title),
                        summary = stringResource(R.string.skip_warn_page_summary),
                        checked = skipWarnPage,
                        onCheckedChange = onSkipWarnPageChanged
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                SettingsCard(title = stringResource(R.string.fun_title_function)) {
                    ZToolSwitchRow(
                        title = stringResource(R.string.Disable_installerAD_Title),
                        summary = stringResource(R.string.Disable_installerAD_Summary),
                        checked = disableInstallerAd,
                        onCheckedChange = onDisableInstallerAdChanged
                    )
                    ZToolSettingsDivider()
                    ZToolSwitchRow(
                        title = stringResource(R.string.Enable_rowStyle_title),
                        summary = stringResource(R.string.Enable_rowStyle_summary),
                        checked = packageInstallerStyleHook,
                        onCheckedChange = onPackageInstallerStyleHookChanged
                    )
                    ZToolSettingsDivider()
                    ZToolSwitchRow(
                        title = stringResource(R.string.Disable_deletePackage_Title),
                        summary = stringResource(R.string.Disable_deletePackage_Summary),
                        checked = disableDeletePackage,
                        onCheckedChange = onDisableDeletePackageChanged
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
