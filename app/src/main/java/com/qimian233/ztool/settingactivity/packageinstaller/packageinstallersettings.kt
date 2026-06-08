package com.qimian233.ztool.settingactivity.packageinstaller

import android.os.Bundle
import android.widget.Toast
import com.qimian233.ztool.utils.ZToolComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import com.qimian233.ztool.data.packageinstaller.PackageInstallerSettingsRepository
import com.qimian233.ztool.ui.components.SettingItem
import com.qimian233.ztool.ui.components.SettingSection
import com.qimian233.ztool.ui.components.ZToolDialog
import com.qimian233.ztool.ui.components.ZToolScaffold
import com.qimian233.ztool.ui.components.ZToolSettingsList
import com.qimian233.ztool.ui.components.ZToolTopAppBar
import com.qimian233.ztool.ui.theme.ZToolTheme
import com.qimian233.ztool.viewmodel.PackageInstallerSettingsUiState
import com.qimian233.ztool.viewmodel.PackageInstallerSettingsViewModel

@Suppress("ClassName")
class packageinstallersettings : ZToolComponentActivity() {

    private var appPackageName: String? = null
    private lateinit var viewModel: PackageInstallerSettingsViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val appName = intent.getStringExtra("app_name").orEmpty()
        appPackageName = intent.getStringExtra("app_package")
        val repository = PackageInstallerSettingsRepository(applicationContext)
        viewModel = ViewModelProvider(
            this,
            PackageInstallerSettingsViewModelFactory(repository)
        )[PackageInstallerSettingsViewModel::class.java]
        viewModel.loadSettings()

        setContent {
            val uiState by viewModel.uiState.collectAsState()

            ZToolTheme {
                PackageInstallerSettingsScreen(
                    title = appName,
                    state = uiState,
                    onBack = ::finish,
                    onRestart = viewModel::showRestartConfirmDialog,
                    onDisableScanApkChanged = viewModel::setDisableScanApk,
                    onAlwaysAllowPermissionChanged = viewModel::setAlwaysAllowPermission,
                    onSkipWarnPageChanged = viewModel::setSkipWarnPage,
                    onDisableInstallerAdChanged = viewModel::setDisableInstallerAd,
                    onPackageInstallerStyleHookChanged = viewModel::setPackageInstallerStyleHook,
                    onDisableDeletePackageChanged = viewModel::setDisableDeletePackage
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

    private fun showRestartFailure() {
        runOnUiThread {
            Toast.makeText(this, R.string.restart_fail_simple, Toast.LENGTH_SHORT).show()
        }
    }
}

private class PackageInstallerSettingsViewModelFactory(
    private val repository: PackageInstallerSettingsRepository
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(PackageInstallerSettingsViewModel::class.java)) {
            return PackageInstallerSettingsViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}

@Composable
private fun PackageInstallerSettingsScreen(
    title: String,
    state: PackageInstallerSettingsUiState,
    onBack: () -> Unit,
    onRestart: () -> Unit,
    onDisableScanApkChanged: (Boolean) -> Unit,
    onAlwaysAllowPermissionChanged: (Boolean) -> Unit,
    onSkipWarnPageChanged: (Boolean) -> Unit,
    onDisableInstallerAdChanged: (Boolean) -> Unit,
    onPackageInstallerStyleHookChanged: (Boolean) -> Unit,
    onDisableDeletePackageChanged: (Boolean) -> Unit
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
                    sections = packageInstallerSettingsSections(
                        state = state,
                        onDisableScanApkChanged = onDisableScanApkChanged,
                        onAlwaysAllowPermissionChanged = onAlwaysAllowPermissionChanged,
                        onSkipWarnPageChanged = onSkipWarnPageChanged,
                        onDisableInstallerAdChanged = onDisableInstallerAdChanged,
                        onPackageInstallerStyleHookChanged = onPackageInstallerStyleHookChanged,
                        onDisableDeletePackageChanged = onDisableDeletePackageChanged
                    ),
                    bottomPadding = 96.dp
                )
            }
        }
    }
}

@Composable
private fun packageInstallerSettingsSections(
    state: PackageInstallerSettingsUiState,
    onDisableScanApkChanged: (Boolean) -> Unit,
    onAlwaysAllowPermissionChanged: (Boolean) -> Unit,
    onSkipWarnPageChanged: (Boolean) -> Unit,
    onDisableInstallerAdChanged: (Boolean) -> Unit,
    onPackageInstallerStyleHookChanged: (Boolean) -> Unit,
    onDisableDeletePackageChanged: (Boolean) -> Unit
): List<SettingSection> {
    return listOf(
        SettingSection(
            title = stringResource(R.string.sec_title_function),
            items = listOf(
                SettingItem.Switch(
                    title = stringResource(R.string.Disable_ScanAPK_Title),
                    summary = stringResource(R.string.Disable_ScanAPK_Summary),
                    checked = state.disableScanApk,
                    onCheckedChange = onDisableScanApkChanged
                ),
                SettingItem.Switch(
                    title = stringResource(R.string.OnlyAllow_Title),
                    summary = stringResource(R.string.OnlyAllow_Summary),
                    checked = state.alwaysAllowPermission,
                    onCheckedChange = onAlwaysAllowPermissionChanged
                ),
                SettingItem.Switch(
                    title = stringResource(R.string.skip_warn_page_title),
                    summary = stringResource(R.string.skip_warn_page_summary),
                    checked = state.skipWarnPage,
                    onCheckedChange = onSkipWarnPageChanged
                )
            )
        ),
        SettingSection(
            title = stringResource(R.string.fun_title_function),
            items = listOf(
                SettingItem.Switch(
                    title = stringResource(R.string.Disable_installerAD_Title),
                    summary = stringResource(R.string.Disable_installerAD_Summary),
                    checked = state.disableInstallerAd,
                    onCheckedChange = onDisableInstallerAdChanged
                ),
                SettingItem.Switch(
                    title = stringResource(R.string.Enable_rowStyle_title),
                    summary = stringResource(R.string.Enable_rowStyle_summary),
                    checked = state.packageInstallerStyleHook,
                    onCheckedChange = onPackageInstallerStyleHookChanged
                ),
                SettingItem.Switch(
                    title = stringResource(R.string.Disable_deletePackage_Title),
                    summary = stringResource(R.string.Disable_deletePackage_Summary),
                    checked = state.disableDeletePackage,
                    onCheckedChange = onDisableDeletePackageChanged
                )
            )
        )
    )
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
