package com.qimian233.ztool

import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material3.Button
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.qimian233.ztool.data.home.HomeRepository
import com.qimian233.ztool.ui.components.ZToolCard
import com.qimian233.ztool.ui.components.ZToolDialog
import com.qimian233.ztool.ui.components.ZToolPageSurface
import com.qimian233.ztool.ui.components.ZToolScaffold
import com.qimian233.ztool.ui.components.ZToolTopAppBar
import com.qimian233.ztool.ui.components.ZToolButton
import com.qimian233.ztool.ui.components.ZToolTextButton
import com.qimian233.ztool.ui.components.ZToolFloatingActionButton
import com.qimian233.ztool.ui.components.ZToolExtendedFloatingActionButton
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

    LaunchedEffect(uiState.environmentReady) {
        onEnvironmentStateChanged(uiState.environmentReady)
    }

    LaunchedEffect(Unit) {
        viewModel.start()
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
        onUnsupportedSoftReboot = {
            Toast.makeText(
                context,
                R.string.soft_reboot_not_supported,
                Toast.LENGTH_LONG
            ).show()
        },
        onToggleUpdateExpanded = viewModel::toggleUpdateExpanded,
        onIgnoreUpdate = {
            viewModel.ignoreUpdate(it)
            Toast.makeText(context, R.string.update_ignore_toast, Toast.LENGTH_SHORT).show()
        },
        onOpenUpdate = { url ->
            openUpdateUrl(context, url)
        }
    )

    if (uiState.configUpgradeDialogVisible) {
        ConfigUpgradeDialog(
            onRestart = {
                viewModel.dismissConfigUpgradeDialog()
                viewModel.restartAfterConfigUpgrade()
            },
            onLater = {
                viewModel.dismissConfigUpgradeDialog()
                Toast.makeText(context, R.string.have_not_restart_warn, Toast.LENGTH_SHORT).show()
            }
        )
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
        Toast.makeText(context, R.string.open_web_link_failed, Toast.LENGTH_SHORT).show()
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
                Toast.makeText(context, R.string.reboot_success, Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(
                    context,
                    context.getString(R.string.reboot_failed, error),
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }
}

private class HomeViewModelFactory(
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
    onUnsupportedSoftReboot: () -> Unit,
    onToggleUpdateExpanded: () -> Unit,
    onIgnoreUpdate: (Int) -> Unit,
    onOpenUpdate: (String) -> Unit
) {
    var showRebootTargets by remember { mutableStateOf(false) }

    ZToolScaffold (
        topBar = {
            ZToolTopAppBar(
                title = stringResource(R.string.homeFragment_title),
                addNavIcon = false
            )
        },
        floatingActionButton = {
            if (state.isRootAvailable) {
                ZToolFloatingActionButton(onClick = { showRebootTargets = true }) {
                    Icon(
                        imageVector = Icons.Rounded.Refresh,
                        contentDescription = null
                    )
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

                if (!state.environmentReady) {
                    RequirementCard()
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

                if (state.environmentReady) {
                    ModuleStatusCard(state)
                    Spacer(modifier = Modifier.height(16.dp))
                    SystemInfoCard(state)
                    Spacer(modifier = Modifier.height(24.dp))
                }

                Text(
                    text = state.hintText.ifBlank {
                        if (state.isCheckingEnvironment) {
                            stringResource(R.string.loading)
                        } else {
                            stringResource(R.string.workConditionTip)
                        }
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp),
                    textAlign = TextAlign.Center
                )
            }
        }
    }

    if (showRebootTargets) {
        RebootTargetDialog(
            onDismiss = {
                @Suppress("AssignedValueIsNeverRead")
                showRebootTargets = false
            },
            onTargetSelected = { target ->
                if (target == RebootTarget.Userspace &&
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE
                ) {
                    onUnsupportedSoftReboot()
                    return@RebootTargetDialog
                }
                onRestartTargetSelected(target)
            }
        )
    }
}

@Composable
private fun RequirementCard() {
    ZToolCard(
        modifier = Modifier.fillMaxWidth(),
        containerColor = MaterialTheme.colorScheme.errorContainer
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Rounded.Info,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onErrorContainer
            )
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                text = stringResource(R.string.workModeRequirementDetail),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onErrorContainer
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
        containerColor = MaterialTheme.colorScheme.tertiaryContainer
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Rounded.SystemUpdate,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onTertiaryContainer
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = stringResource(R.string.update_available_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = stringResource(R.string.buildCode, update.versionName, update.versionCode),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onTertiaryContainer
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = update.changelog,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onTertiaryContainer,
                maxLines = if (update.expanded) Int.MAX_VALUE else 4,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                ZToolTextButton(onClick = onIgnore, text = stringResource(R.string.update_button_ignore), isPrimary = false)
                Spacer(modifier = Modifier.width(8.dp))
                ZToolButton(onClick = onOpenUpdate) {
                    Text(stringResource(R.string.update_button_update))
                }
            }
        }
    }
}

@Composable
private fun ModuleStatusCard(state: HomeUiState) {
    val themeSpec = com.qimian233.ztool.ui.theme.LocalZToolThemeSpec.current
    val isMiuix = themeSpec.style == com.qimian233.ztool.ui.theme.FrontendStyle.Miuix
    val isDefaultColor = !themeSpec.dynamicColorEnabled && !themeSpec.manualColorEnabled
    val isGreen = isMiuix && state.isModuleActive && isDefaultColor
    
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val containerColor = if (isGreen) {
        if (isDark) Color(0xFF1B5E20).copy(alpha = 0.4f) else Color(0xFFA5D6A7)
    } else {
        MaterialTheme.colorScheme.primaryContainer
    }
    val contentColor = if (isGreen) {
        if (isDark) Color(0xFFA5D6A7) else Color(0xFF1B5E20)
    } else {
        MaterialTheme.colorScheme.onPrimaryContainer
    }
    val iconColor = if (isGreen) {
        if (isDark) Color(0xFF66BB6A) else Color(0xFF4CAF50)
    } else {
        MaterialTheme.colorScheme.primary
    }
    
    ZToolCard(
        modifier = Modifier.fillMaxWidth(),
        containerColor = containerColor,
        defaultElevation = 1.dp
    ) {
        Column(modifier = Modifier.padding(24.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.environmentState),
                        style = MaterialTheme.typography.titleMedium,
                        color = contentColor.copy(alpha = 0.8f)
                    )
                    Text(
                        text = if (state.isModuleActive) {
                            stringResource(R.string.module_active)
                        } else {
                            stringResource(R.string.module_inactive)
                        },
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = contentColor
                    )
                }
                Icon(
                    imageVector = Icons.Rounded.CheckCircle,
                    contentDescription = null,
                    tint = iconColor,
                    modifier = Modifier.size(32.dp)
                )
            }
            HorizontalDivider(
                modifier = Modifier.padding(vertical = 16.dp),
                color = contentColor.copy(alpha = 0.2f)
            )
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                InfoBlock(
                    label = stringResource(R.string.version),
                    value = state.moduleVersion.ifBlank { stringResource(R.string.loading) },
                    colorOnContainer = contentColor
                )
                InfoBlock(
                    label = stringResource(R.string.root),
                    value = state.rootSource.ifBlank { stringResource(R.string.loading) },
                    colorOnContainer = contentColor
                )
                InfoBlock(
                    label = stringResource(R.string.framework),
                    value = state.frameworkVersion.ifBlank { stringResource(R.string.loading) },
                    colorOnContainer = contentColor
                )
            }
        }
    }
}

@Composable
private fun InfoBlock(
    label: String,
    value: String,
    colorOnContainer: Color
) {
    Column(modifier = Modifier.widthIn(min = 180.dp, max = 320.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = colorOnContainer.copy(alpha = 0.7f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Bold,
            color = colorOnContainer,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun SystemInfoCard(state: HomeUiState) {
    val themeSpec = com.qimian233.ztool.ui.theme.LocalZToolThemeSpec.current
    val isMiuix = themeSpec.style == com.qimian233.ztool.ui.theme.FrontendStyle.Miuix
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val containerColor = if (isMiuix) {
        if (isDark) MaterialTheme.colorScheme.surfaceContainer else Color.White
    } else {
        MaterialTheme.colorScheme.surfaceContainerHigh
    }

    ZToolCard(
        modifier = Modifier.fillMaxWidth(),
        containerColor = containerColor
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = stringResource(R.string.deviceInfo),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(16.dp))
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                DeviceInfoItem(stringResource(R.string.deviceCodeName), state.deviceModel)
                DeviceInfoItem(stringResource(R.string.AndroidVersion), state.androidVersion)
                DeviceInfoItem(stringResource(R.string.buildVersion), state.buildVersion)
                DeviceInfoItem(stringResource(R.string.kernelVersion), state.kernelVersion)
            }
            Spacer(modifier = Modifier.height(18.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SuggestionChip(
                    onClick = {},
                    label = {
                        Text(stringResource(R.string.currentSlot) + state.currentSlot.ifBlank { stringResource(R.string.unknown) })
                    }
                )
                SuggestionChip(
                    onClick = {},
                    label = {
                        Text(stringResource(R.string.romRegion) + state.romRegion.ifBlank { stringResource(R.string.unknown) })
                    }
                )
            }
        }
    }
}

@Composable
private fun DeviceInfoItem(
    label: String,
    value: String
) {
    val themeSpec = com.qimian233.ztool.ui.theme.LocalZToolThemeSpec.current
    val isMiuix = themeSpec.style == com.qimian233.ztool.ui.theme.FrontendStyle.Miuix
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val valueColor = if (isMiuix && isDark) Color.White else Color.Unspecified
    
    Column(modifier = Modifier.widthIn(min = 220.dp, max = 420.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value.ifBlank { stringResource(R.string.placeHolderUnknown) },
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Bold,
            color = valueColor,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}


@Composable
private fun ConfigUpgradeDialog(
    onRestart: () -> Unit,
    onLater: () -> Unit
) {
    ZToolDialog(
        onDismissRequest = onLater,
        title = { Text(stringResource(R.string.config_upgraded_tip_title)) },
        text = { Text(stringResource(R.string.config_upgraded_tip_message)) },
        confirmButton = {
            ZToolTextButton(onClick = onRestart, text = stringResource(R.string.restart_system_button))
        },
        dismissButton = {
            ZToolTextButton(onClick = onLater, text = stringResource(R.string.do_not_restart_system_button), isPrimary = false)
        }
    )
}

@Composable
private fun RebootTargetDialog(
    onDismiss: () -> Unit,
    onTargetSelected: (RebootTarget) -> Unit
) {
    val options = remember {
        listOf(
            RebootTarget.Userspace to R.string.soft_reboot,
            RebootTarget.System to R.string.reboot,
            RebootTarget.Bootloader to R.string.bootloader,
            RebootTarget.Recovery to R.string.recovery,
            RebootTarget.Edl to R.string.edl
        )
    }

    ZToolDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.reboot_menu_description)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                options.forEach { (target, titleRes) ->
                    TextButton(
                        onClick = { onTargetSelected(target) },
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)
                    ) {
                        Text(
                            text = stringResource(titleRes),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
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
        title = { Text(stringResource(R.string.reboot_confirm_title)) },
        text = { Text(stringResource(target.messageRes)) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}
