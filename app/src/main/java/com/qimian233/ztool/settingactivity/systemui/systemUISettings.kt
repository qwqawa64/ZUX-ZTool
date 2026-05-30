package com.qimian233.ztool.settingactivity.systemui

import android.content.Intent
import android.os.Bundle
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.ExtendedFloatingActionButton
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.qimian233.ztool.R
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.qimian233.ztool.data.systemui.SystemUiSettingsRepository
import com.qimian233.ztool.settingactivity.systemui.ControlCenter.ControlCenterSettingsActivity
import com.qimian233.ztool.settingactivity.systemui.lockscreen.LockScreenSettingsActivity
import com.qimian233.ztool.settingactivity.systemui.statusBarSetting.StatusBarSettingsActivity
import com.qimian233.ztool.ui.components.ZToolCard
import com.qimian233.ztool.ui.components.ZToolDialog
import com.qimian233.ztool.ui.components.ZToolScaffold
import com.qimian233.ztool.ui.components.ZToolSettingsDivider
import com.qimian233.ztool.ui.components.ZToolSwitchRow
import com.qimian233.ztool.ui.components.ZToolTopAppBar
import com.qimian233.ztool.ui.theme.ZToolTheme
import com.qimian233.ztool.viewmodel.SystemUiSettingsUiState
import com.qimian233.ztool.viewmodel.SystemUiSettingsViewModel

@Suppress("ClassName")
class systemUISettings : ComponentActivity() {

    private lateinit var viewModel: SystemUiSettingsViewModel

    private var appName: String = ""
    private var appPackageName: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val repository = SystemUiSettingsRepository(applicationContext)
        viewModel = ViewModelProvider(
            this,
            SystemUiSettingsViewModelFactory(repository)
        )[SystemUiSettingsViewModel::class.java]
        appName = intent.getStringExtra("app_name").orEmpty()
        appPackageName = intent.getStringExtra("app_package")

        viewModel.loadSettings()

        setContent {
            val uiState by viewModel.uiState.collectAsState()

            ZToolTheme {
                SystemUiSettingsScreen(
                    title = appName + stringResource(R.string.SystemUIActionBar),
                    state = uiState,
                    onBack = ::finish,
                    onOpenStatusBar = {
                        openSubSettings(StatusBarSettingsActivity::class.java)
                    },
                    onOpenLockScreen = {
                        openSubSettings(LockScreenSettingsActivity::class.java)
                    },
                    onOpenControlCenter = {
                        openSubSettings(ControlCenterSettingsActivity::class.java)
                    },
                    onNativeAodChanged = ::handleNativeAodChanged,
                    onLenovoAodChanged = viewModel::setLenovoAodEnabled,
                    onOpenLenovoAodSettings = viewModel::openLenovoAodSettings,
                    onNoChargeAnimationChanged = viewModel::setNoChargeAnimation,
                    onChargeAnimationFixChanged = viewModel::setChargeAnimationFix,
                    onGuestModeChanged = viewModel::setGuestModeController,
                    onRestartScope = viewModel::showRestartDialog
                )

                if (uiState.showRestartDialog) {
                    RestartScopeDialog(
                        packageName = appPackageName.orEmpty(),
                        onConfirm = {
                            forceStopScope()
                        },
                        onDismiss = viewModel::dismissRestartDialog
                    )
                }
            }
        }
    }

    private fun <T> openSubSettings(target: Class<T>) {
        val intent = Intent(this, target).apply {
            putExtra("app_name", appName)
            putExtra("app_package", appPackageName)
        }
        startActivity(intent)
    }


    private fun handleNativeAodChanged(enabled: Boolean) {
        viewModel.setNativeAodEnabled(
            enabled = enabled,
            onLenovoAodDisabled = {
                runOnUiThread {
                    Toast.makeText(this, R.string.restart_scope_required, Toast.LENGTH_SHORT).show()
                }
            },
            onFailure = { error ->
                runOnUiThread {
                    Toast.makeText(this, "设置失败: $error", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }

    private fun forceStopScope() {
        viewModel.forceStopScope(appPackageName.orEmpty()) { success, error ->
            runOnUiThread {
                if (success) {
                    Toast.makeText(this, R.string.restartSuccess, Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, getString(R.string.restartFail) + error, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

}

private class SystemUiSettingsViewModelFactory(
    private val repository: SystemUiSettingsRepository
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(SystemUiSettingsViewModel::class.java)) {
            return SystemUiSettingsViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}

@Composable
private fun SystemUiSettingsScreen(
    title: String,
    state: SystemUiSettingsUiState,
    onBack: () -> Unit,
    onOpenStatusBar: () -> Unit,
    onOpenLockScreen: () -> Unit,
    onOpenControlCenter: () -> Unit,
    onNativeAodChanged: (Boolean) -> Unit,
    onLenovoAodChanged: (Boolean) -> Unit,
    onOpenLenovoAodSettings: () -> Unit,
    onNoChargeAnimationChanged: (Boolean) -> Unit,
    onChargeAnimationFixChanged: (Boolean) -> Unit,
    onGuestModeChanged: (Boolean) -> Unit,
    onRestartScope: () -> Unit
) {
    ZToolScaffold(
        topBar = {
            ZToolTopAppBar(
                title = title,
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = null)
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onRestartScope,
                expanded = !state.isRestartProcessing,
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
                NavigationCard(
                    title = stringResource(R.string.statusBarSettingTitle),
                    summary = stringResource(R.string.statusBarSettingSummary),
                    iconRes = R.drawable.ic_status_bar,
                    onClick = onOpenStatusBar
                )
                Spacer(modifier = Modifier.height(16.dp))
                NavigationCard(
                    title = stringResource(R.string.LockScreenSettingTitle),
                    summary = stringResource(R.string.LockScreenSummary),
                    iconRes = R.drawable.ic_lock,
                    onClick = onOpenLockScreen
                )
                Spacer(modifier = Modifier.height(16.dp))
                NavigationCard(
                    title = stringResource(R.string.controlCenterTitle),
                    summary = stringResource(R.string.controlCenterSummary),
                    iconRes = R.drawable.ic_control_center,
                    onClick = onOpenControlCenter
                )

                Spacer(modifier = Modifier.height(16.dp))

                SettingsCard(title = stringResource(R.string.aod_title)) {
                    ZToolSwitchRow(
                        title = stringResource(R.string.aod_native_enable_title),
                        summary = stringResource(R.string.aod_native_enable_summary),
                        checked = state.nativeAod,
                        onCheckedChange = onNativeAodChanged
                    )
                    ZToolSettingsDivider()
                    ZToolSwitchRow(
                        title = stringResource(R.string.aod_lenovo_enable_title),
                        summary = stringResource(R.string.aod_lenovo_enable_summary),
                        checked = state.lenovoAod,
                        onCheckedChange = onLenovoAodChanged
                    )
                    if (state.lenovoAod) {
                        ZToolSettingsDivider()
                        ActionRow(
                            title = stringResource(R.string.aod_lenovo_activity_title),
                            summary = stringResource(R.string.aod_lenovo_activity_summary),
                            onClick = onOpenLenovoAodSettings
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                SettingsCard(title = stringResource(R.string.noChargingAnimation_title)) {
                    ZToolSwitchRow(
                        title = stringResource(R.string.noChargingAnimation_enable_title),
                        summary = stringResource(R.string.noChargingAnimation_enable_summary),
                        checked = state.noChargeAnimation,
                        onCheckedChange = onNoChargeAnimationChanged
                    )
                    ZToolSettingsDivider()
                    ZToolSwitchRow(
                        title = stringResource(R.string.Charge_Animation_Fix),
                        summary = stringResource(R.string.Charge_Animation_Fix_Summary),
                        checked = state.chargeAnimationFix,
                        onCheckedChange = onChargeAnimationFixChanged
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                SettingsCard(title = stringResource(R.string.systemUIMisc)) {
                    ZToolSwitchRow(
                        title = stringResource(R.string.disable_guest_user_enable_title),
                        summary = stringResource(R.string.disable_guest_user_enable_summary),
                        checked = state.guestModeController,
                        onCheckedChange = onGuestModeChanged
                    )
                }
            }
        }
    }
}

@Composable
private fun NavigationCard(
    title: String,
    summary: String,
    iconRes: Int,
    onClick: () -> Unit
) {
    ZToolCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    painter = painterResource(iconRes),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
            }
            Text(
                text = summary,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 12.dp)
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.seeDetail),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(6.dp))
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
private fun SettingsCard(
    title: String,
    content: @Composable () -> Unit
) {
    ZToolCard(modifier = Modifier.fillMaxWidth()) {
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
private fun ActionRow(
    title: String,
    summary: String,
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
        Icon(
            imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun RestartScopeDialog(
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
                    "，com.zui.wallpapersetting" +
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
