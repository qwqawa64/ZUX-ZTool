package com.qimian233.ztool.screens.home

import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Android
import androidx.compose.material.icons.rounded.Cancel
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.DeveloperBoard
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.qimian233.ztool.MainActivity
import com.qimian233.ztool.ModuleActivationProbe
import com.qimian233.ztool.R
import com.qimian233.ztool.data.home.HomeRepository
import com.qimian233.ztool.ui.components.DexIndexProgressDialog
import com.qimian233.ztool.ui.components.SettingItem
import com.qimian233.ztool.ui.components.SettingSection
import com.qimian233.ztool.ui.components.ZToolButton
import com.qimian233.ztool.ui.components.ZToolCard
import com.qimian233.ztool.ui.components.ZToolDialog
import com.qimian233.ztool.ui.components.ZToolFloatingActionButton
import com.qimian233.ztool.ui.components.ZToolPageSurface
import com.qimian233.ztool.ui.components.ZToolScaffold
import com.qimian233.ztool.ui.components.ZToolSettingsList
import com.qimian233.ztool.ui.components.ZToolTextButton
import com.qimian233.ztool.ui.components.ZToolTopAppBar
import com.qimian233.ztool.ui.theme.LocalZToolColorScheme
import com.qimian233.ztool.ui.theme.LocalZToolThemeSpec
import com.qimian233.ztool.viewmodel.HomeUiState
import com.qimian233.ztool.viewmodel.HomeViewModel
import com.qimian233.ztool.viewmodel.RebootTarget
import com.qimian233.ztool.viewmodel.UpdateInfo

interface EnvironmentStateListener {
    fun onEnvironmentStateChanged(environmentReady: Boolean)
}

@Composable
fun HomeMainRoute(
    onEnvironmentStateChanged: (Boolean) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val activity = context as MainActivity
    val viewModel = remember {
        val repository = HomeRepository(
            context = context.applicationContext,
            moduleActiveChecker = ModuleActivationProbe::isModuleActive
        )
        ViewModelProvider(
            activity,
            HomeViewModelFactory(repository)
        )[HomeViewModel::class.java]
    }
    val uiState by viewModel.uiState.collectAsState()
    val dexIndexState by viewModel.dexIndexState.collectAsState()

    LaunchedEffect(uiState.environmentReady) {
        onEnvironmentStateChanged(uiState.environmentReady)
    }

    LaunchedEffect(Unit) {
        viewModel.start()
        viewModel.checkDexIndexOnEntry(context.applicationContext)
    }

    // DexKit 索引结果 Toast（Firstrun 后台索引 / 过期前台刷新完成后触发一次）
    LaunchedEffect(dexIndexState.toastMessage) {
        dexIndexState.toastMessage?.let { res ->
            Toast.makeText(context, res, Toast.LENGTH_SHORT).show()
            viewModel.consumeDexIndexToast()
        }
    }

    DisposableEffect(lifecycleOwner, viewModel) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> viewModel.refreshSystemInfoIfNeeded()
                Lifecycle.Event.ON_DESTROY -> viewModel.clearShellCache()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    HomeScreen(
        state = uiState,
        onRestartTargetSelected = viewModel::showRebootConfirmation,
        onToggleUpdateExpanded = viewModel::toggleUpdateExpanded,
        onIgnoreUpdate = {
            viewModel.ignoreUpdate(it)
            Toast.makeText(context, R.string.page_home_update_ignore_toast, Toast.LENGTH_SHORT).show()
        },
        onOpenUpdate = { url ->
            openUpdateUrl(context, url)
        },
        onRefreshEnvironment = viewModel::checkEnvironment
    )

    if (uiState.configUpgradeDialogVisible) {
        ConfigUpgradeDialog(
            onRestart = {
                viewModel.dismissConfigUpgradeDialog()
                viewModel.restartAfterConfigUpgrade()
            },
            onLater = {
                viewModel.dismissConfigUpgradeDialog()
                Toast.makeText(context, R.string.page_home_have_not_restart_warn, Toast.LENGTH_SHORT).show()
            }
        )
    }

    if (dexIndexState.refreshing) {
        DexIndexProgressDialog(progress = dexIndexState.progress)
    }

    uiState.rebootConfirmation?.let { target ->
        RebootConfirmDialog(
            target = target,
            onConfirm = {
                viewModel.dismissRebootConfirmation()
                executeReboot(context, viewModel, target)
            },
            onDismiss = viewModel::dismissRebootConfirmation
        )
    }
}

private fun openUpdateUrl(context: Context, url: String) {
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
    } catch (_: Exception) {
        Toast.makeText(context, R.string.common_open_web_link_failed, Toast.LENGTH_SHORT).show()
    }
}

private fun executeReboot(
    context: Context,
    viewModel: HomeViewModel,
    target: RebootTarget
) {
    viewModel.executeReboot(target) { success, error ->
        (context as? MainActivity)?.runOnUiThread {
            if (success) {
                Toast.makeText(context, R.string.page_home_reboot_success, Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(
                    context,
                    context.getString(R.string.page_home_reboot_failed, error),
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }
}

internal class HomeViewModelFactory(
    private val repository: HomeRepository
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(HomeViewModel::class.java)) {
            return HomeViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}

@Composable
private fun HomeScreen(
    state: HomeUiState,
    onRestartTargetSelected: (RebootTarget) -> Unit,
    onToggleUpdateExpanded: () -> Unit,
    onIgnoreUpdate: (Int) -> Unit,
    onOpenUpdate: (String) -> Unit,
    onRefreshEnvironment: () -> Unit
) {
    var showRebootMenu by remember { mutableStateOf(false) }

    ZToolScaffold (
        topBar = {
            ZToolTopAppBar(
                title = stringResource(R.string.page_home_title),
                addNavIcon = false
            )
        },
        floatingActionButton = {
            if (state.isRootAvailable) {
                Box {
                    ZToolFloatingActionButton(onClick = { showRebootMenu = true }) {
                        Icon(
                            imageVector = Icons.Rounded.Refresh,
                            contentDescription = null
                        )
                    }
                    DropdownMenu(
                        expanded = showRebootMenu,
                        onDismissRequest = { showRebootMenu = false },
                        modifier = Modifier
                            .heightIn(max = 360.dp)
                            .widthIn(max = 160.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        RebootTarget.entries
                            .filter { target ->
                                target != RebootTarget.Userspace ||
                                    Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE
                            }
                            .forEach { target ->
                                DropdownMenuItem(
                                    text = { Text(stringResource(target.displayNameRes)) },
                                    onClick = {
                                        showRebootMenu = false
                                        onRestartTargetSelected(target)
                                    }
                                )
                            }
                    }
                }
            }
        }
    ) { innerPadding ->
        ZToolPageSurface(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth()
                    .widthIn(max = 1120.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 32.dp, vertical = 32.dp)
            ) {

                Spacer(modifier = Modifier.height(24.dp))

                if (!state.isZuxOsDevice) {
                    NonZuxOsCard()
                    Spacer(modifier = Modifier.height(16.dp))
                }

                AnimatedVisibility(visible = state.environmentReady && state.updateInfo != null) {
                    state.updateInfo?.let { update ->
                        Column {
                            UpdateCard(
                                update = update,
                                onToggleExpanded = onToggleUpdateExpanded,
                                onIgnore = { onIgnoreUpdate(update.versionCode) },
                                onOpenUpdate = { onOpenUpdate(update.downloadUrl) }
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                        }
                    }
                }

                ModuleStatusCard(state, onRefreshEnvironment)

                if (state.environmentReady) {
                    Spacer(modifier = Modifier.height(16.dp))
                    SystemInfoCard(state)
                }

                Spacer(modifier = Modifier.padding(48.dp))
            }
        }
    }
}

@Composable
private fun NonZuxOsCard() {
    ZToolCard(
        modifier = Modifier.fillMaxWidth(),
        containerColor = LocalZToolColorScheme.current.errorContainer
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Rounded.Warning,
                contentDescription = null,
                tint = LocalZToolColorScheme.current.onErrorContainer
            )
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                text = stringResource(R.string.page_home_non_zuxos_warn),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = LocalZToolColorScheme.current.onErrorContainer
            )
        }
    }
}

@Composable
private fun UpdateCard(
    update: UpdateInfo,
    onToggleExpanded: () -> Unit,
    onIgnore: () -> Unit,
    onOpenUpdate: () -> Unit
) {
    ZToolCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggleExpanded),
        containerColor = LocalZToolColorScheme.current.tertiaryContainer
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Rounded.SystemUpdate,
                    contentDescription = null,
                    tint = LocalZToolColorScheme.current.onTertiaryContainer
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = stringResource(R.string.page_home_update_available_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = LocalZToolColorScheme.current.onTertiaryContainer,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = stringResource(R.string.page_home_build_code, update.versionName, update.versionCode),
                    style = MaterialTheme.typography.labelMedium,
                    color = LocalZToolColorScheme.current.onTertiaryContainer
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = update.changelog,
                style = MaterialTheme.typography.bodyMedium,
                color = LocalZToolColorScheme.current.onTertiaryContainer,
                maxLines = if (update.expanded) Int.MAX_VALUE else 4,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                ZToolTextButton(onClick = onIgnore, text = stringResource(R.string.page_home_update_button_ignore), isPrimary = false)
                Spacer(modifier = Modifier.width(8.dp))
                ZToolButton(onClick = onOpenUpdate) {
                    Text(stringResource(R.string.page_home_update_button_update))
                }
            }
        }
    }
}

@Composable
private fun ModuleStatusCard(
    state: HomeUiState,
    onRefreshEnvironment: () -> Unit
) {
    val themeSpec = LocalZToolThemeSpec.current
    val isDefaultColor = !themeSpec.dynamicColorEnabled && !themeSpec.manualColorEnabled
    val bothActive = state.isModuleActive && state.isRootAvailable
    val anyActive = state.isModuleActive || state.isRootAvailable

    val isDark = LocalZToolColorScheme.current.surface.luminance() < 0.5f
    val containerColor = when {
        bothActive && isDefaultColor -> if (isDark) Color(0xFF1B5E20).copy(alpha = 0.4f) else Color(0xFFA5D6A7)
        !bothActive && anyActive -> if (isDark) Color(0xFFE65100).copy(alpha = 0.35f) else Color(0xFFFFE082)
        !anyActive -> if (isDark) Color(0xFFB71C1C).copy(alpha = 0.4f) else Color(0xFFEF9A9A)
        else -> LocalZToolColorScheme.current.primaryContainer
    }
    val contentColor = when {
        bothActive && isDefaultColor -> if (isDark) Color(0xFFA5D6A7) else Color(0xFF1B5E20)
        !bothActive && anyActive -> if (isDark) Color(0xFFFFE082) else Color(0xFFE65100)
        !anyActive -> if (isDark) Color(0xFFEF9A9A) else Color(0xFFB71C1C)
        else -> LocalZToolColorScheme.current.onPrimaryContainer
    }
    val iconColor = when {
        bothActive && isDefaultColor -> if (isDark) Color(0xFF66BB6A) else Color(0xFF4CAF50)
        !bothActive && anyActive -> if (isDark) Color(0xFFFFB300) else Color(0xFFFF8F00)
        !anyActive -> if (isDark) Color(0xFFEF5350) else Color(0xFFE53935)
        else -> LocalZToolColorScheme.current.primary
    }

    val statusText = when {
        bothActive -> stringResource(R.string.page_home_module_active)
        state.isModuleActive -> stringResource(R.string.page_home_no_root_permission)
        state.isRootAvailable -> stringResource(R.string.page_home_module_inactive)
        else -> stringResource(R.string.page_home_no_root_and_module_inactive)
    }

    val summaryText = if (bothActive) {
        state.moduleVersion.ifBlank { stringResource(R.string.common_loading) }
    } else {
        null
    }

    val icon = when {
        bothActive -> Icons.Rounded.CheckCircle
        anyActive -> Icons.Rounded.Warning
        else -> Icons.Rounded.Cancel
    }

    ZToolCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp)
            .clip(RoundedCornerShape(16.dp))
            .then(
                if (!bothActive) Modifier.clickable { onRefreshEnvironment() }
                else Modifier.clickable(onClick = {})
            ),
        containerColor = containerColor,
        defaultElevation = 1.dp
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = statusText,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = contentColor
                        )
                        if (state.apiVersion > 0) {
                            Spacer(modifier = Modifier.width(8.dp))
                            ApiVersionBadge(
                                apiVersion = state.apiVersion,
                                colorOnContainer = contentColor
                            )
                        }
                    }
                    if (summaryText != null) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = summaryText,
                            style = MaterialTheme.typography.bodyMedium,
                            color = contentColor.copy(alpha = 0.8f)
                        )
                    }
                    if (!bothActive) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Rounded.Refresh,
                                contentDescription = null,
                                tint = contentColor.copy(alpha = 0.6f),
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = stringResource(R.string.page_home_environment_tap_to_refresh),
                                style = MaterialTheme.typography.labelMedium,
                                color = contentColor.copy(alpha = 0.6f)
                            )
                        }
                    }
                }
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconColor,
                    modifier = Modifier.size(32.dp)
                )
            }
        }
    }
}

private data class SystemInfoRow(
    val key: String,
    val title: String,
    val value: String,
    val icon: ImageVector
)

@Composable
private fun ApiVersionBadge(
    apiVersion: Int,
    colorOnContainer: Color
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(colorOnContainer.copy(alpha = 0.15f))
            .padding(horizontal = 8.dp, vertical = 2.dp)
    ) {
        Text(
            text = stringResource(R.string.page_home_api_badge_format, apiVersion),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = colorOnContainer
        )
    }
}

@Composable
private fun SystemInfoCard(state: HomeUiState) {
    val unknownText = stringResource(R.string.page_home_place_holder_unknown)

    val infoRows = listOf(
        SystemInfoRow(
            key = "home_device_code_name",
            title = stringResource(R.string.page_home_device_code_name),
            value = state.deviceModel,
            icon = Icons.Rounded.PhoneAndroid
        ),
        SystemInfoRow(
            key = "home_android_version",
            title = stringResource(R.string.page_home_android_version),
            value = state.androidVersion,
            icon = Icons.Rounded.Android
        ),
        SystemInfoRow(
            key = "home_build_version",
            title = stringResource(R.string.page_home_build_version),
            value = state.buildVersion,
            icon = Icons.Rounded.Memory
        ),
        SystemInfoRow(
            key = "home_kernel_version",
            title = stringResource(R.string.page_home_kernel_version),
            value = state.kernelVersion,
            icon = Icons.Rounded.DeveloperBoard
        ),
        SystemInfoRow(
            key = "home_current_slot",
            title = stringResource(R.string.page_home_current_slot),
            value = state.currentSlot,
            icon = Icons.Rounded.SwapHoriz
        ),
        SystemInfoRow(
            key = "home_rom_region",
            title = stringResource(R.string.page_home_rom_region),
            value = state.romRegion,
            icon = Icons.Rounded.Public
        ),
        SystemInfoRow(
            key = "home_root_source",
            title = stringResource(R.string.page_home_root),
            value = state.rootSource,
            icon = Icons.Rounded.Security
        ),
        SystemInfoRow(
            key = "home_framework_info",
            title = stringResource(R.string.page_home_framework),
            value = state.frameworkVersion,
            icon = Icons.Rounded.Extension
        )
    )

    ZToolSettingsList(
        sections = listOf(
            SettingSection(
                items = infoRows.map { row ->
                    SettingItem.Action(
                        key = row.key,
                        title = row.title,
                        summary = row.value.ifBlank { unknownText },
                        icon = row.icon,
                        onClick = {}
                    )
                }
            )
        )
    )
}


@Composable
private fun ConfigUpgradeDialog(
    onRestart: () -> Unit,
    onLater: () -> Unit
) {
    ZToolDialog(
        onDismissRequest = onLater,
        title = { Text(stringResource(R.string.page_home_config_upgraded_tip_title)) },
        text = { Text(stringResource(R.string.page_home_config_upgraded_tip_message)) },
        confirmButton = {
            ZToolTextButton(onClick = onRestart, text = stringResource(R.string.page_home_restart_system_button))
        },
        dismissButton = {
            ZToolTextButton(onClick = onLater, text = stringResource(R.string.page_home_do_not_restart_system_button), isPrimary = false)
        }
    )
}

@Composable
private fun RebootConfirmDialog(
    target: RebootTarget,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    ZToolDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.page_home_reboot_confirm_title)) },
        text = { Text(stringResource(target.messageRes)) },
        confirmButton = {
            ZToolTextButton(
                onClick = onConfirm,
                text = stringResource(R.string.common_confirm)
            )
        },
        dismissButton = {
            ZToolTextButton(
                onClick = onDismiss,
                text = stringResource(R.string.common_cancel),
                isPrimary = false
            )
        }
    )
}
