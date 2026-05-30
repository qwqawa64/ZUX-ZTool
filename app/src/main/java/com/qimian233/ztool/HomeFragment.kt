package com.qimian233.ztool

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.PopupMenu
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.qimian233.ztool.data.home.HomeRepository
import com.qimian233.ztool.ui.components.ZToolCard
import com.qimian233.ztool.ui.components.ZToolDialog
import com.qimian233.ztool.ui.components.ZToolPageSurface
import com.qimian233.ztool.ui.components.ZToolScaffold
import com.qimian233.ztool.ui.theme.ZToolTheme
import com.qimian233.ztool.viewmodel.HomeUiState
import com.qimian233.ztool.viewmodel.HomeViewModel
import com.qimian233.ztool.viewmodel.RebootTarget
import com.qimian233.ztool.viewmodel.UpdateInfo

class HomeFragment : Fragment() {

    interface EnvironmentStateListener {
        fun onEnvironmentStateChanged(environmentReady: Boolean)
    }

    private var environmentStateListener: EnvironmentStateListener? = null
    private var lastEnvironmentState = false

    private lateinit var viewModel: HomeViewModel

    override fun onAttach(context: Context) {
        super.onAttach(context)
        if (context is EnvironmentStateListener) {
            environmentStateListener = context
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val repository = HomeRepository(
            context = requireContext().applicationContext,
            moduleActiveChecker = ::isModuleActive
        )
        viewModel = ViewModelProvider(
            this,
            HomeViewModelFactory(repository)
        )[HomeViewModel::class.java]
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                val uiState by viewModel.uiState.collectAsState()

                LaunchedEffect(uiState.environmentReady) {
                    notifyEnvironmentState(uiState.environmentReady)
                }

                ZToolTheme {
                    HomeScreen(
                        state = uiState,
                        onRestartClick = ::showRebootMenu,
                        onToggleUpdateExpanded = viewModel::toggleUpdateExpanded,
                        onIgnoreUpdate = {
                            viewModel.ignoreUpdate(it)
                            Toast.makeText(requireContext(), R.string.update_ignore_toast, Toast.LENGTH_SHORT).show()
                        },
                        onOpenUpdate = ::openUpdateUrl
                    )

                    if (uiState.configUpgradeDialogVisible) {
                        ConfigUpgradeDialog(
                            onRestart = {
                                viewModel.dismissConfigUpgradeDialog()
                                viewModel.restartAfterConfigUpgrade()
                            },
                            onLater = {
                                viewModel.dismissConfigUpgradeDialog()
                                Toast.makeText(
                                    requireContext(),
                                    R.string.have_not_restart_warn,
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        )
                    }

                    uiState.rebootConfirmation?.let { target ->
                        RebootConfirmDialog(
                            target = target,
                            onConfirm = {
                                viewModel.dismissRebootConfirmation()
                                executeReboot(target)
                            },
                            onDismiss = viewModel::dismissRebootConfirmation
                        )
                    }
                }
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        view.postDelayed({
            viewModel.start()
        }, 100)
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshSystemInfoIfNeeded()
    }

    override fun onDetach() {
        super.onDetach()
        environmentStateListener = null
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::viewModel.isInitialized) {
            viewModel.clearShellCache()
        }
    }

    private fun notifyEnvironmentState(environmentReady: Boolean) {
        if (environmentReady != lastEnvironmentState) {
            environmentStateListener?.onEnvironmentStateChanged(environmentReady)
            lastEnvironmentState = environmentReady
        }
    }

    private fun openUpdateUrl(url: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (e: Exception) {
            Toast.makeText(requireContext(), R.string.open_web_link_failed, Toast.LENGTH_SHORT).show()
        }
    }

    private fun showRebootMenu(anchor: View) {
        PopupMenu(requireContext(), anchor).apply {
            menuInflater.inflate(R.menu.reboot_menu, menu)
            setOnMenuItemClickListener(::handleMenuItemClick)
            show()
        }
    }

    private fun handleMenuItemClick(item: MenuItem): Boolean {
        val target = when (item.itemId) {
            R.id.menu_soft_reboot -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    Toast.makeText(
                        requireContext(),
                        R.string.soft_reboot_not_supported,
                        Toast.LENGTH_LONG
                    ).show()
                    return true
                }
                RebootTarget.Userspace
            }
            R.id.menu_bootloader -> RebootTarget.Bootloader
            R.id.menu_recovery -> RebootTarget.Recovery
            R.id.menu_edl -> RebootTarget.Edl
            R.id.menu_reboot -> RebootTarget.System
            else -> return false
        }
        viewModel.showRebootConfirmation(target)
        return true
    }

    private fun executeReboot(target: RebootTarget) {
        viewModel.executeReboot(target) { success, error ->
            activity?.runOnUiThread {
                if (success) {
                    Toast.makeText(requireContext(), R.string.reboot_success, Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(
                        requireContext(),
                        getString(R.string.reboot_failed, error),
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    private fun isModuleActive(): Boolean {
        return false
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
    onRestartClick: (View) -> Unit,
    onToggleUpdateExpanded: () -> Unit,
    onIgnoreUpdate: (Int) -> Unit,
    onOpenUpdate: (String) -> Unit
) {
    ZToolScaffold { innerPadding ->
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
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.homeFragment_title),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )
                    if (state.isRootAvailable) {
                        androidx.compose.ui.viewinterop.AndroidView(
                            modifier = Modifier.size(48.dp),
                            factory = { context ->
                                android.widget.FrameLayout(context).apply {
                                    val button = android.widget.ImageButton(context).apply {
                                        setImageResource(R.drawable.ic_restart_menu)
                                        background = null
                                        contentDescription = context.getString(R.string.reboot_menu_description)
                                        setOnClickListener { onRestartClick(this) }
                                    }
                                    addView(
                                        button,
                                        android.widget.FrameLayout.LayoutParams(
                                            android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                                            android.widget.FrameLayout.LayoutParams.MATCH_PARENT
                                        )
                                    )
                                }
                            }
                        )
                    }
                }

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
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        }
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
                TextButton(onClick = onIgnore) {
                    Text(stringResource(R.string.update_button_ignore))
                }
                Spacer(modifier = Modifier.width(8.dp))
                Button(onClick = onOpenUpdate) {
                    Text(stringResource(R.string.update_button_update))
                }
            }
        }
    }
}

@Composable
private fun ModuleStatusCard(state: HomeUiState) {
    ZToolCard(
        modifier = Modifier.fillMaxWidth(),
        containerColor = MaterialTheme.colorScheme.primaryContainer,
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
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                    )
                    Text(
                        text = if (state.isModuleActive) {
                            stringResource(R.string.module_active)
                        } else {
                            stringResource(R.string.module_inactive)
                        },
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
                Icon(
                    imageVector = Icons.Rounded.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(32.dp)
                )
            }
            HorizontalDivider(
                modifier = Modifier.padding(vertical = 16.dp),
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.2f)
            )
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                InfoBlock(
                    label = stringResource(R.string.version),
                    value = state.moduleVersion.ifBlank { stringResource(R.string.loading) },
                    colorOnContainer = MaterialTheme.colorScheme.onPrimaryContainer
                )
                InfoBlock(
                    label = stringResource(R.string.root),
                    value = state.rootSource.ifBlank { stringResource(R.string.loading) },
                    colorOnContainer = MaterialTheme.colorScheme.onPrimaryContainer
                )
                InfoBlock(
                    label = stringResource(R.string.framework),
                    value = state.frameworkVersion.ifBlank { stringResource(R.string.loading) },
                    colorOnContainer = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
    }
}

@Composable
private fun InfoBlock(
    label: String,
    value: String,
    colorOnContainer: androidx.compose.ui.graphics.Color
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
    ZToolCard(
        modifier = Modifier.fillMaxWidth(),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
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
            TextButton(onClick = onRestart) {
                Text(stringResource(R.string.restart_system_button))
            }
        },
        dismissButton = {
            TextButton(onClick = onLater) {
                Text(stringResource(R.string.do_not_restart_system_button))
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
