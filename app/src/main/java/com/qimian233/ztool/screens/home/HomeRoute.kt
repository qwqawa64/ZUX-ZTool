package com.qimian233.ztool.screens.home

import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
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
import com.qimian233.ztool.data.home.HomeTipRepository
import com.qimian233.ztool.ui.components.DexIndexProgressDialog
import com.qimian233.ztool.ui.components.ZToolDialog
import com.qimian233.ztool.ui.components.ZToolFloatingActionButton
import com.qimian233.ztool.ui.components.ZToolPageSurface
import com.qimian233.ztool.ui.components.ZToolScaffold
import com.qimian233.ztool.ui.components.ZToolTextButton
import com.qimian233.ztool.ui.components.ZToolTopAppBar
import com.qimian233.ztool.viewmodel.HomeUiState
import com.qimian233.ztool.viewmodel.HomeViewModel
import com.qimian233.ztool.viewmodel.RebootTarget

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
            HomeViewModelFactory(
                repository = repository,
                tipRepository = HomeTipRepository(context.applicationContext)
            )
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

    // DexKit index result toast (fired once after Firstrun background indexing /
    // foreground refresh finishes)
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
        onDismissNonZuxOsWarning = viewModel::dismissNonZuxOsWarning,
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
    private val repository: HomeRepository,
    private val tipRepository: HomeTipRepository
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(HomeViewModel::class.java)) {
            return HomeViewModel(repository, tipRepository) as T
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
    onDismissNonZuxOsWarning: () -> Unit,
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
            HomeCardStack(
                state = state,
                onDismissNonZuxOsWarning = onDismissNonZuxOsWarning,
                onToggleUpdateExpanded = onToggleUpdateExpanded,
                onIgnoreUpdate = onIgnoreUpdate,
                onOpenUpdate = onOpenUpdate,
                onRefreshEnvironment = onRefreshEnvironment
            )
        }
    }
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
