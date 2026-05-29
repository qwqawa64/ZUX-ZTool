package com.qimian233.ztool.settingactivity.systemui

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
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
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.qimian233.ztool.EnhancedShellExecutor
import com.qimian233.ztool.R
import com.qimian233.ztool.hook.modules.SharedPreferencesTool.ModulePreferencesUtils
import com.qimian233.ztool.settingactivity.systemui.ControlCenter.ControlCenterSettingsActivity
import com.qimian233.ztool.settingactivity.systemui.lockscreen.LockScreenSettingsActivity
import com.qimian233.ztool.settingactivity.systemui.statusBarSetting.StatusBarSettingsActivity
import com.qimian233.ztool.ui.components.ZToolSettingsDivider
import com.qimian233.ztool.ui.components.ZToolSwitchRow
import com.qimian233.ztool.ui.theme.ZToolTheme

@Suppress("ClassName")
class systemUISettings : ComponentActivity() {

    private lateinit var prefsUtils: ModulePreferencesUtils
    private lateinit var shellExecutor: EnhancedShellExecutor
    private val handler = Handler(Looper.getMainLooper())

    private var appName: String = ""
    private var appPackageName: String? = null

    private var uiState by mutableStateOf(SystemUiSettingsUiState())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        shellExecutor = EnhancedShellExecutor.getInstance()
        prefsUtils = ModulePreferencesUtils(this)
        appName = intent.getStringExtra("app_name").orEmpty()
        appPackageName = intent.getStringExtra("app_package")

        loadSettingsAsync()

        setContent {
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
                    onLenovoAodChanged = ::handleLenovoAodChanged,
                    onOpenLenovoAodSettings = ::openLenovoAodSettings,
                    onNoChargeAnimationChanged = {
                        uiState = uiState.copy(noChargeAnimation = it)
                        prefsUtils.saveBooleanSetting("No_ChargeAnimation", it)
                    },
                    onChargeAnimationFixChanged = {
                        uiState = uiState.copy(chargeAnimationFix = it)
                        prefsUtils.saveBooleanSetting("charge_animation_fix", it)
                    },
                    onGuestModeChanged = {
                        uiState = uiState.copy(guestModeController = it)
                        prefsUtils.saveBooleanSetting("guest_mode_controller", it)
                    },
                    onRestartScope = {
                        if (!uiState.isRestartProcessing) {
                            uiState = uiState.copy(showRestartDialog = true)
                        }
                    }
                )

                if (uiState.showRestartDialog) {
                    RestartScopeDialog(
                        packageName = appPackageName.orEmpty(),
                        onConfirm = {
                            uiState = uiState.copy(showRestartDialog = false)
                            forceStopApp()
                        },
                        onDismiss = { uiState = uiState.copy(showRestartDialog = false) }
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

    private fun loadSettingsAsync() {
        Thread {
            try {
                val native = prefsUtils.loadBooleanSetting("ForceNativeAOD", false)
                val lenovo = prefsUtils.loadBooleanSetting("ForceLenovoAOD", false)
                val noCharge = prefsUtils.loadBooleanSetting("No_ChargeAnimation", false)
                val chargeFix = prefsUtils.loadBooleanSetting("charge_animation_fix", false)
                val guest = prefsUtils.loadBooleanSetting("guest_mode_controller", false)

                runOnUiThread {
                    uiState = uiState.copy(
                        nativeAod = native,
                        lenovoAod = lenovo,
                        noChargeAnimation = noCharge,
                        chargeAnimationFix = chargeFix,
                        guestModeController = guest
                    )
                }
                Log.d(TAG, "设置加载完成")
            } catch (e: Exception) {
                Log.e(TAG, "加载设置失败: ${e.message}")
            }
        }.start()
    }

    private fun handleNativeAodChanged(enabled: Boolean) {
        if (uiState.isAodSwitchProcessing) {
            Log.d(TAG, "AOD开关正在处理中，忽略重复操作")
            return
        }
        uiState = uiState.copy(
            nativeAod = enabled,
            isAodSwitchProcessing = true
        )

        handler.post {
            setNativeAodEnabled(enabled)
            prefsUtils.saveBooleanSetting("ForceNativeAOD", enabled)

            if (prefsUtils.loadBooleanSetting("ForceLenovoAOD", false)) {
                prefsUtils.saveBooleanSetting("ForceLenovoAOD", false)
                uiState = uiState.copy(lenovoAod = false)
                Toast.makeText(this, R.string.restart_scope_required, Toast.LENGTH_SHORT).show()
            }
            uiState = uiState.copy(isAodSwitchProcessing = false)
        }
    }

    private fun handleLenovoAodChanged(enabled: Boolean) {
        if (uiState.isAodSwitchProcessing) {
            Log.d(TAG, "AOD开关正在处理中，忽略重复操作")
            return
        }
        uiState = uiState.copy(
            lenovoAod = enabled,
            isAodSwitchProcessing = true
        )
        prefsUtils.saveBooleanSetting("ForceLenovoAOD", enabled)

        handler.post {
            if (isAodEnabled()) {
                setNativeAodEnabled(false)
                uiState = uiState.copy(nativeAod = false)
            }
            uiState = uiState.copy(isAodSwitchProcessing = false)
        }
    }

    private fun openLenovoAodSettings() {
        val result = shellExecutor.executeRootCommand(
            "am start -n com.android.systemui/com.android.systemui.aod.setting.AoDSettingActivity",
            5
        )
        Log.d("LenovoAODPicker", result.toString())
    }

    private fun setNativeAodEnabled(enabled: Boolean) {
        Thread {
            try {
                val command = "settings put secure doze_always_on " + if (enabled) "1" else "0"
                val result = shellExecutor.executeRootCommand(command, 5)
                val success = result.isSuccess
                Log.d("AODSwitch", "AOD设置命令执行结果: ${if (success) "成功" else "失败"}, 退出码: ${result.exitCode}")

                handler.post {
                    if (!success) {
                        uiState = uiState.copy(nativeAod = !enabled)
                        Toast.makeText(this, "设置失败: ${result.error}", Toast.LENGTH_SHORT).show()
                    }
                    uiState = uiState.copy(isAodSwitchProcessing = false)
                }
            } catch (e: Exception) {
                Log.e("AODSwitch", "设置AOD时出错: ${e.message}")
                handler.post {
                    uiState = uiState.copy(nativeAod = !enabled)
                    Toast.makeText(this, "执行错误: ${e.message}", Toast.LENGTH_SHORT).show()
                    uiState = uiState.copy(isAodSwitchProcessing = false)
                }
            }
        }.start()
    }

    private fun isAodEnabled(): Boolean {
        return try {
            val result = shellExecutor.executeRootCommand("settings get secure doze_always_on", 5)
            if (result.isSuccess && result.output != null) {
                val enabled = result.output.trim() == "1"
                Log.d("AODCheck", "原生AOD状态: ${if (enabled) "启用" else "禁用"}")
                enabled
            } else {
                false
            }
        } catch (e: Exception) {
            Log.e("AODCheck", "检查原生AOD状态失败: ${e.message}")
            false
        }
    }

    private fun forceStopApp() {
        val packageName = appPackageName
        if (packageName.isNullOrEmpty() || uiState.isRestartProcessing) return

        uiState = uiState.copy(isRestartProcessing = true)

        Thread {
            try {
                val result = shellExecutor.executeRootCommand("killall $packageName", 5)
                val success = result.isSuccess

                handler.post {
                    if (success) {
                        Toast.makeText(this, R.string.restartSuccess, Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(this, getString(R.string.restartFail) + result.error, Toast.LENGTH_SHORT).show()
                    }
                    resetRestartButton()
                }
                Log.d("ForceStopApp", "强制停止应用结果: ${if (success) "成功" else "失败"}")
            } catch (e: Exception) {
                Log.e("ForceStopApp", "强制停止应用时出错: ${e.message}")
                handler.post {
                    Toast.makeText(this, getString(R.string.restartFail) + e.message, Toast.LENGTH_SHORT).show()
                    resetRestartButton()
                }
            }
        }.start()

        Thread {
            try {
                val result = shellExecutor.executeRootCommand("killall com.zui.wallpapersetting", 5)
                Log.d("ForceStopApp", "强制停止壁纸设置结果: ${if (result.isSuccess) "成功" else "失败"}")
            } catch (e: Exception) {
                Log.e("ForceStopApp", "强制停止壁纸设置时出错: ${e.message}")
            }
        }.start()
    }

    private fun resetRestartButton() {
        handler.post {
            uiState = uiState.copy(isRestartProcessing = false)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "Activity销毁")
    }

    override fun onPause() {
        super.onPause()
        Log.d(TAG, "Activity暂停")
    }

    override fun onResume() {
        super.onResume()
        Log.d(TAG, "Activity恢复")
    }

    companion object {
        private const val TAG = "SystemUISettings"
    }
}

private data class SystemUiSettingsUiState(
    val nativeAod: Boolean = false,
    val lenovoAod: Boolean = false,
    val noChargeAnimation: Boolean = false,
    val chargeAnimationFix: Boolean = false,
    val guestModeController: Boolean = false,
    val isAodSwitchProcessing: Boolean = false,
    val isRestartProcessing: Boolean = false,
    val showRestartDialog: Boolean = false
)

@OptIn(ExperimentalMaterial3Api::class)
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
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
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
    AlertDialog(
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
