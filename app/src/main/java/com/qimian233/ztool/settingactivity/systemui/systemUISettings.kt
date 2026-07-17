package com.qimian233.ztool.settingactivity.systemui

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import com.qimian233.ztool.R
import com.qimian233.ztool.data.systemui.SystemUiSettingsRepository
import com.qimian233.ztool.ui.components.SettingItem
import com.qimian233.ztool.ui.components.SettingSection
import com.qimian233.ztool.ui.components.ZToolDialog
import com.qimian233.ztool.ui.components.ZToolExtendedFloatingActionButton
import com.qimian233.ztool.ui.components.ZToolScaffold
import com.qimian233.ztool.ui.components.ZToolSettingsList
import com.qimian233.ztool.ui.components.ZToolTextButton
import com.qimian233.ztool.ui.components.ZToolTopAppBar
import com.qimian233.ztool.ui.theme.LocalZToolColorScheme
import com.qimian233.ztool.viewmodel.SystemUiSettingsUiState
import com.qimian233.ztool.viewmodel.SystemUiSettingsViewModel

@Composable
fun SystemUiSettingsRoute(
    title: String,
    packageName: String,
    onBack: () -> Unit,
    onOpenStatusBar: () -> Unit,
    onOpenLockScreen: () -> Unit,
    onOpenControlCenter: () -> Unit
) {
    val context = LocalContext.current
    val owner = LocalViewModelStoreOwner.current
        ?: error("SystemUiSettingsRoute requires a ViewModelStoreOwner")
    val viewModel = remember(owner) {
        ViewModelProvider(
            owner,
            SystemUiSettingsViewModelFactory(
                SystemUiSettingsRepository(context.applicationContext)
            )
        )[SystemUiSettingsViewModel::class.java]
    }

    LaunchedEffect(viewModel) {
        viewModel.loadSettings()
    }

    val uiState by viewModel.uiState.collectAsState()

    val customChargeVideoSavedText = stringResource(R.string.custom_charge_animation_video_saved)
    val customChargeVideoSaveFailedText = stringResource(R.string.custom_charge_animation_video_save_failed)

    val portraitVideoLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            val saved = viewModel.saveCustomChargeVideo(
                context, uri, "charging_animation_portrait.mp4"
            )
            Toast.makeText(
                context,
                if (saved) customChargeVideoSavedText else customChargeVideoSaveFailedText,
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    val landVideoLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            val saved = viewModel.saveCustomChargeVideo(
                context, uri, "charging_animation_land.mp4"
            )
            Toast.makeText(
                context,
                if (saved) customChargeVideoSavedText else customChargeVideoSaveFailedText,
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    SystemUiSettingsScreen(
        title = title,
        state = uiState,
        onBack = onBack,
        onOpenStatusBar = onOpenStatusBar,
        onOpenLockScreen = onOpenLockScreen,
        onOpenControlCenter = onOpenControlCenter,
        onNativeAodChanged = { enabled ->
            viewModel.setNativeAodEnabled(
                enabled = enabled,
                onLenovoAodDisabled = {
                    Toast.makeText(context, R.string.restart_scope_required, Toast.LENGTH_SHORT).show()
                },
                onFailure = { error ->
                    Toast.makeText(context, "设置失败: $error", Toast.LENGTH_SHORT).show()
                }
            )
        },
        onLenovoAodChanged = viewModel::setLenovoAodEnabled,
        onOpenLenovoAodSettings = viewModel::openLenovoAodSettings,
        onNoChargeAnimationChanged = viewModel::setNoChargeAnimation,
        onChargeAnimationFixChanged = viewModel::setChargeAnimationFix,
        onCustomChargeAnimationChanged = viewModel::setCustomChargeAnimation,
        onGuestModeChanged = viewModel::setGuestModeController,
        onRestartScope = viewModel::showRestartDialog,
        onSelectPortraitVideo = { portraitVideoLauncher.launch(arrayOf("video/*")) },
        onSelectLandVideo = { landVideoLauncher.launch(arrayOf("video/*")) }
    )

    if (uiState.showRestartDialog) {
        val restartFailString = stringResource(R.string.restartFail)
        RestartScopeDialog(
            packageName = packageName,
            onConfirm = {
                viewModel.forceStopScope { success, error ->
                    if (success) {
                        Toast.makeText(context, R.string.restartSuccess, Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(
                            context,
                            restartFailString + error,
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            },
            onDismiss = viewModel::dismissRestartDialog
        )
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
    onCustomChargeAnimationChanged: (Boolean) -> Unit,
    onGuestModeChanged: (Boolean) -> Unit,
    onRestartScope: () -> Unit,
    onSelectPortraitVideo: () -> Unit,
    onSelectLandVideo: () -> Unit
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
            ZToolExtendedFloatingActionButton(
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
                ZToolSettingsList(
                    sections = systemUiSettingsSections(
                        state = state,
                        onOpenStatusBar = onOpenStatusBar,
                        onOpenLockScreen = onOpenLockScreen,
                        onOpenControlCenter = onOpenControlCenter,
                        onNativeAodChanged = onNativeAodChanged,
                        onLenovoAodChanged = onLenovoAodChanged,
                        onOpenLenovoAodSettings = onOpenLenovoAodSettings,
                        onNoChargeAnimationChanged = onNoChargeAnimationChanged,
                        onChargeAnimationFixChanged = onChargeAnimationFixChanged,
                        onCustomChargeAnimationChanged = onCustomChargeAnimationChanged,
                        onGuestModeChanged = onGuestModeChanged,
                        onSelectPortraitVideo = onSelectPortraitVideo,
                        onSelectLandVideo = onSelectLandVideo
                    )
                )
            }
        }
    }
}

@Composable
private fun systemUiSettingsSections(
    state: SystemUiSettingsUiState,
    onOpenStatusBar: () -> Unit,
    onOpenLockScreen: () -> Unit,
    onOpenControlCenter: () -> Unit,
    onNativeAodChanged: (Boolean) -> Unit,
    onLenovoAodChanged: (Boolean) -> Unit,
    onOpenLenovoAodSettings: () -> Unit,
    onNoChargeAnimationChanged: (Boolean) -> Unit,
    onChargeAnimationFixChanged: (Boolean) -> Unit,
    onCustomChargeAnimationChanged: (Boolean) -> Unit,
    onGuestModeChanged: (Boolean) -> Unit,
    onSelectPortraitVideo: () -> Unit,
    onSelectLandVideo: () -> Unit
): List<SettingSection> {
    val aodItems = buildList {
        add(
            SettingItem.Switch(
                title = stringResource(R.string.aod_native_enable_title),
                summary = stringResource(R.string.aod_native_enable_summary),
                checked = state.nativeAod,
                onCheckedChange = onNativeAodChanged,
                enabled = !state.isAodSwitchProcessing
            )
        )
        add(
            SettingItem.Switch(
                title = stringResource(R.string.aod_lenovo_enable_title),
                summary = stringResource(R.string.aod_lenovo_enable_summary),
                checked = state.lenovoAod,
                onCheckedChange = onLenovoAodChanged,
                enabled = !state.isAodSwitchProcessing
            )
        )
        if (state.lenovoAod) {
            add(
                SettingItem.Action(
                    title = stringResource(R.string.aod_lenovo_activity_title),
                    summary = stringResource(R.string.aod_lenovo_activity_summary),
                    onClick = onOpenLenovoAodSettings,
                    trailingContent = {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                            contentDescription = null,
                            tint = LocalZToolColorScheme.current.onSurfaceVariant
                        )
                    }
                )
            )
        }
    }

    return listOf(
        SettingSection(
            items = listOf(
                systemUiNavigationItem(
                    title = stringResource(R.string.statusBarSettingTitle),
                    summary = stringResource(R.string.statusBarSettingSummary),
                    iconRes = R.drawable.ic_status_bar,
                    onClick = onOpenStatusBar
                ),
                systemUiNavigationItem(
                    title = stringResource(R.string.LockScreenSettingTitle),
                    summary = stringResource(R.string.LockScreenSummary),
                    iconRes = R.drawable.ic_lock,
                    onClick = onOpenLockScreen
                ),
                systemUiNavigationItem(
                    title = stringResource(R.string.controlCenterTitle),
                    summary = stringResource(R.string.controlCenterSummary),
                    iconRes = R.drawable.ic_control_center,
                    onClick = onOpenControlCenter
                )
            )
        ),
        SettingSection(
            title = stringResource(R.string.aod_title),
            items = aodItems
        ),
        SettingSection(
            title = stringResource(R.string.noChargingAnimation_title),
            items = buildList {
                add(
                    SettingItem.Switch(
                        title = stringResource(R.string.noChargingAnimation_enable_title),
                        summary = stringResource(R.string.noChargingAnimation_enable_summary),
                        checked = state.noChargeAnimation,
                        onCheckedChange = onNoChargeAnimationChanged
                    )
                )
                add(
                    SettingItem.Switch(
                        title = stringResource(R.string.Charge_Animation_Fix),
                        summary = stringResource(R.string.Charge_Animation_Fix_Summary),
                        checked = state.chargeAnimationFix,
                        onCheckedChange = onChargeAnimationFixChanged
                    )
                )
                add(
                    SettingItem.Switch(
                        title = stringResource(R.string.custom_charge_animation_title),
                        summary = stringResource(R.string.custom_charge_animation_summary),
                        checked = state.customChargeAnimation,
                        onCheckedChange = onCustomChargeAnimationChanged
                    )
                )
                if (state.customChargeAnimation) {
                    add(
                        SettingItem.Action(
                            title = stringResource(R.string.custom_charge_animation_portrait_action_title),
                            onClick = onSelectPortraitVideo,
                            trailingContent = {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                                    contentDescription = null,
                                    tint = LocalZToolColorScheme.current.onSurfaceVariant
                                )
                            }
                        )
                    )
                    add(
                        SettingItem.Action(
                            title = stringResource(R.string.custom_charge_animation_land_action_title),
                            onClick = onSelectLandVideo,
                            trailingContent = {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                                    contentDescription = null,
                                    tint = LocalZToolColorScheme.current.onSurfaceVariant
                                )
                            }
                        )
                    )
                }
            }
        ),
        SettingSection(
            title = stringResource(R.string.systemUIMisc),
            items = listOf(
                SettingItem.Switch(
                    title = stringResource(R.string.disable_guest_user_enable_title),
                    summary = stringResource(R.string.disable_guest_user_enable_summary),
                    checked = state.guestModeController,
                    onCheckedChange = onGuestModeChanged
                )
            )
        )
    )
}

@Composable
private fun systemUiNavigationItem(
    title: String,
    summary: String,
    iconRes: Int,
    onClick: () -> Unit
): SettingItem {
    return SettingItem.Entry(
        title = title,
        summary = summary,
        onClick = onClick,
        leadingContent = {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = null,
                tint = LocalZToolColorScheme.current.primary
            )
        },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Spacer(modifier = Modifier.width(6.dp))
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                    contentDescription = null,
                    tint = LocalZToolColorScheme.current.primary
                )
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
            ZToolTextButton(onClick = onConfirm, text = stringResource(R.string.restart_yes))
        },
        dismissButton = {
            ZToolTextButton(onClick = onDismiss, text = stringResource(R.string.restart_no), isPrimary = false)
        }
    )
}
