package com.qimian233.ztool.screens.ztoolsettings.misc

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Brightness4
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.BuildCircle
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.qimian233.ztool.MainActivity
import com.qimian233.ztool.R
import com.qimian233.ztool.ui.components.HighlightAnchorRegistry
import com.qimian233.ztool.ui.components.HighlightController
import com.qimian233.ztool.ui.components.SettingItem
import com.qimian233.ztool.ui.components.SettingSection
import com.qimian233.ztool.ui.components.ZToolDialog
import com.qimian233.ztool.ui.components.ZToolPageSurface
import com.qimian233.ztool.ui.components.ZToolScaffold
import com.qimian233.ztool.ui.components.ZToolSettingsList
import com.qimian233.ztool.ui.components.ZToolTextButton
import com.qimian233.ztool.ui.components.ZToolTopAppBar
import com.qimian233.ztool.viewmodel.AdvancedSettingsViewModel
import com.qimian233.ztool.viewmodel.AdvancedSettingsUiState

/**
 * Miscellaneous settings: those that live on the Advanced route historically
 * but are neither experimental nor under active exploration.
 */
@Composable
fun SettingsMiscRoute(
    onBack: () -> Unit,
    onOpenEngineeringCodes: () -> Unit = {},
    targetId: String? = null
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val activity = context as MainActivity
    val viewModel = remember {
        ViewModelProvider(
            activity,
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return AdvancedSettingsViewModel() as T
                }
            }
        )[AdvancedSettingsViewModel::class.java]
    }
    val uiState by viewModel.uiState.collectAsState()

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refresh()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val deleteOtaPackageStartingString = stringResource(R.string.page_settings_advanced_delete_ota_package_in_progress)

    if (uiState.showDeleteOtaPackageDialog) {
        DeleteOtaPackageConfirmDialog(
            onConfirm = {
                viewModel.performDeleteOtaPackage()
                Toast.makeText(context, deleteOtaPackageStartingString, Toast.LENGTH_SHORT).show()
            },
            onDismiss = viewModel::dismissDeleteOtaPackageDialog
        )
    }

    // Delete system update package: show a result Toast when done (SUCCEEDED / NOT_EXIST / FAILED)
    LaunchedEffect(uiState.deleteOtaPackageStatus) {
        uiState.deleteOtaPackageStatus?.let { status ->
            val message = when (status) {
                "SUCCEEDED" -> context.getString(R.string.page_settings_advanced_delete_ota_package_result_success)
                "NOT_EXIST" -> context.getString(R.string.page_settings_advanced_delete_ota_package_result_not_exist)
                else -> context.getString(
                    R.string.page_settings_advanced_delete_ota_package_result_failed,
                    uiState.deleteOtaPackageMessage ?: ""
                )
            }
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
            viewModel.consumeDeleteOtaPackageResult()
        }
    }

    val scrollState = rememberScrollState()
    val highlightRegistry = remember { HighlightAnchorRegistry() }

    HighlightController(
        highlightTargetId = targetId,
        scrollState = scrollState,
        registry = highlightRegistry,
        onConsumed = { }
    ) {
        SettingsMiscScreen(
            state = uiState,
            onBack = onBack,
            onDeleteOtaPackageClick = { viewModel.showDeleteOtaPackageConfirmDialog() },
            onFixNightModeOverride = { viewModel.fixNightModeOverride(context) },
            onOpenEngineeringCodes = onOpenEngineeringCodes,
            scrollState = scrollState,
            highlightRegistry = highlightRegistry
        )
    }
}

@Composable
private fun SettingsMiscScreen(
    state: AdvancedSettingsUiState,
    onBack: () -> Unit,
    onDeleteOtaPackageClick: () -> Unit,
    onFixNightModeOverride: () -> Unit,
    onOpenEngineeringCodes: () -> Unit,
    scrollState: ScrollState,
    highlightRegistry: HighlightAnchorRegistry
) {
    ZToolScaffold(
        topBar = {
            ZToolTopAppBar(
                title = stringResource(R.string.page_settings_misc_title),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = null
                        )
                    }
                }
            )
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
                    .fillMaxSize()
                    .widthIn(max = 960.dp)
                    .verticalScroll(scrollState)
                    .padding(horizontal = 24.dp, vertical = 24.dp)
            ) {
                ZToolSettingsList(
                    highlightRegistry = highlightRegistry,
                    sections = miscSettingsSections(
                        state = state,
                        onDeleteOtaPackageClick = onDeleteOtaPackageClick,
                        onFixNightModeOverride = onFixNightModeOverride,
                        onOpenEngineeringCodes = onOpenEngineeringCodes
                    ),
                    bottomPadding = 32.dp
                )
            }
        }
    }
}

@Composable
private fun miscSettingsSections(
    state: AdvancedSettingsUiState,
    onDeleteOtaPackageClick: () -> Unit,
    onFixNightModeOverride: () -> Unit,
    onOpenEngineeringCodes: () -> Unit
): List<SettingSection> {
    val deleteOtaPackageInProgressString = stringResource(R.string.page_settings_advanced_delete_ota_package_in_progress)
    val deleteOtaPackageDefaultSummary = stringResource(R.string.page_settings_advanced_delete_ota_package_summary)
    return listOf(
        SettingSection(
            items = listOf(
                SettingItem.Action(
                    key = "misc_delete_ota_package",
                    title = stringResource(R.string.page_settings_advanced_delete_ota_package_title),
                    summary = if (state.deleteOtaPackageInProgress) {
                        deleteOtaPackageInProgressString
                    } else {
                        deleteOtaPackageDefaultSummary
                    },
                    onClick = onDeleteOtaPackageClick,
                    enabled = !state.deleteOtaPackageInProgress,
                    icon = if (state.deleteOtaPackageInProgress) null else Icons.Rounded.DeleteForever,
                    trailingContent = if (state.deleteOtaPackageInProgress) {
                        {
                            CircularProgressIndicator(
                                modifier = Modifier
                                    .height(20.dp)
                                    .padding(0.dp),
                                strokeWidth = 2.dp
                            )
                        }
                    } else null
                ),
                SettingItem.Action(
                    key = "misc_fix_night_mode_override",
                    title = stringResource(R.string.system_framework_fix_night_mode_title),
                    summary = stringResource(R.string.system_framework_fix_night_mode_summary),
                    onClick = onFixNightModeOverride,
                    enabled = !state.fixNightModeOverrideInProgress,
                    icon = if (state.fixNightModeOverrideInProgress) null else Icons.Rounded.Brightness4,
                    trailingContent = if (state.fixNightModeOverrideInProgress) {
                        {
                            CircularProgressIndicator(
                                modifier = Modifier
                                    .height(20.dp)
                                    .padding(0.dp),
                                strokeWidth = 2.dp
                            )
                        }
                    } else null
                ),
                SettingItem.Action(
                    key = "misc_engineering_codes",
                    title = stringResource(R.string.engineering_codes_title),
                    summary = stringResource(R.string.engineering_codes_summary),
                    onClick = onOpenEngineeringCodes,
                    icon = Icons.Rounded.BuildCircle
                )
            )
        ),
        SettingSection(
            title = stringResource(R.string.page_settings_advanced_info_title),
            items = listOf(
                SettingItem.Action(
                    key = "misc_api_version",
                    title = stringResource(R.string.page_settings_advanced_api_version),
                    summary = "${state.apiVersion}",
                    onClick = {},
                    enabled = false,
                    icon = Icons.Rounded.Build
                )
            )
        )
    )
}

@Composable
private fun DeleteOtaPackageConfirmDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    ZToolDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.page_settings_advanced_delete_ota_package_confirm_title)) },
        text = {
            Text(stringResource(R.string.page_settings_advanced_delete_ota_package_confirm_message))
        },
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
