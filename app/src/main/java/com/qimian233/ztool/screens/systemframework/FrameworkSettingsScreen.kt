package com.qimian233.ztool.screens.systemframework

import android.widget.Toast
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import com.qimian233.ztool.R
import com.qimian233.ztool.data.systemframework.FrameworkSettingsRepository
import com.qimian233.ztool.data.pen.PenHapticRedirectRule
import com.qimian233.ztool.data.pen.PenHapticWaveformCatalog
import com.qimian233.ztool.ui.components.QuickHelpExample
import com.qimian233.ztool.ui.components.QuickHelpItem
import com.qimian233.ztool.ui.components.HighlightAnchorRegistry
import com.qimian233.ztool.ui.components.HighlightController
import com.qimian233.ztool.ui.components.SettingItem
import com.qimian233.ztool.ui.components.SettingSection
import com.qimian233.ztool.ui.components.ZToolExtendedFloatingActionButton
import com.qimian233.ztool.ui.components.ZToolOutlinedTextField
import com.qimian233.ztool.ui.components.ZToolPopupDialogField
import com.qimian233.ztool.ui.components.ZToolQuickHelpDialog
import com.qimian233.ztool.ui.components.ZToolScaffold
import com.qimian233.ztool.ui.components.ZToolSettingsDivider
import com.qimian233.ztool.ui.components.ZToolSettingsList
import com.qimian233.ztool.ui.components.ZToolSliderRow
import com.qimian233.ztool.ui.components.ZToolSwitchRow
import com.qimian233.ztool.ui.components.ZToolRestartSystemDialog
import com.qimian233.ztool.ui.components.ZToolTopAppBar
import com.qimian233.ztool.ui.theme.LocalZToolColorScheme
import com.qimian233.ztool.utils.AppChooserDialog
import com.qimian233.ztool.viewmodel.FrameworkSettingsUiState
import com.qimian233.ztool.viewmodel.FrameworkSettingsViewModel
import kotlin.math.roundToInt

@Composable
fun FrameworkSettingsRoute(
    title: String,
    onBack: () -> Unit,
    targetId: String? = null
) {
    val context = LocalContext.current
    val owner = LocalViewModelStoreOwner.current
        ?: error("FrameworkSettingsRoute requires a ViewModelStoreOwner")
    val viewModel = remember(owner) {
        ViewModelProvider(
            owner,
            FrameworkSettingsViewModelFactory(
                FrameworkSettingsRepository(context.applicationContext)
            )
        )[FrameworkSettingsViewModel::class.java]
    }

    LaunchedEffect(viewModel) {
        viewModel.loadSettings()
    }

    val uiState by viewModel.uiState.collectAsState()

    val scrollState = rememberScrollState()
    val highlightRegistry = remember { HighlightAnchorRegistry() }

    HighlightController(
        highlightTargetId = targetId,
        scrollState = scrollState,
        registry = highlightRegistry,
        onConsumed = { }
    ) {
        FrameworkSettingsScreen(
            title = title,
            state = uiState,
            onBack = onBack,
            onRestart = viewModel::showRestartConfirmDialog,
            onKeepRotationChanged = viewModel::setKeepRotation,
            onAllowGetPackagesChanged = viewModel::setAllowGetPackages,
            onDisableFlagSecureChanged = viewModel::setDisableFlagSecure,
            onForceOnOffAnimationChanged = viewModel::setForceOnOffAnimation,
            onForceOnOffAnimationDurationChanged = viewModel::setOnOffScreenAnimationDuration,
            onAiInputExpandChanged = viewModel::setAiInputExpand,
            onAiInputSignsChanged = viewModel::setAiInputSigns,
            onShowAiInputInfo = viewModel::showAiInputInfoDialog,
            onAllowUntrustedTouch = viewModel::setAllowUntrustedTouch,
            onAllowAllRestrictedSettingsChanged = viewModel::setAllowAllRestrictedSettings,
            onAllowRelativeAppLaunchChanged = viewModel::setAllowRelativeAppLaunch,
            onDisableHbmThermalLimitChanged = viewModel::setDisableHbmThermalLimit,
            onDisableVolumeWarnChanged = viewModel::setDisableVolumeWarn,
            onClipboardToastSuppressChanged = viewModel::setClipboardAccessToastSuppress,
            onSelectClipboardToastPackages = {
                AppChooserDialog.show(
                    context,
                    viewModel.loadUserInstalledPackageNames(),
                    uiState.clipboardAccessToastPackages,
                    context.getString(R.string.system_framework_clipboard_toast_apps_picker_title),
                    object : AppChooserDialog.AppSelectionCallback {
                        override fun onSelected(selectedApps: List<AppChooserDialog.AppInfo>) {
                            viewModel.setClipboardAccessToastPackages(
                                selectedApps.map { it.packageName }
                            )
                        }

                        override fun onCancel() = Unit
                    }
                )
            },
            onFineVolumeStepsChanged = viewModel::setFineVolumeSteps,
            onVolumeKeyNonlinearChanged = viewModel::setVolumeKeyNonlinear,
            onPenHapticRedirectChanged = viewModel::setPenHapticRedirectEnabled,
            onPenHapticRuleAdded = viewModel::addPenHapticRedirectRule,
            onPenHapticRuleTargetChanged = viewModel::setPenHapticRedirectTarget,
            onPenHapticRuleRemoved = viewModel::removePenHapticRedirectRule,
            onPkgMgrAllowDowngradeChanged = viewModel::setPkgMgrAllowDowngrade,
            onPkgMgrBypassVerificationChanged = viewModel::setPkgMgrBypassVerification,
            onPkgMgrDisableVerificationAgentChanged = viewModel::setPkgMgrDisableVerificationAgent,
            onPkgMgrBypassDigestChanged = viewModel::setPkgMgrBypassDigest,
            onPkgMgrUsePreviousSignaturesChanged = viewModel::setPkgMgrUsePreviousSignatures,
            onPkgMgrBypassExactSigMatchChanged = viewModel::setPkgMgrBypassExactSigMatch,
            onPkgMgrBypassSharedUserChanged = viewModel::setPkgMgrBypassSharedUser,
            onPkgMgrAllowHiddenApisSystemAppsChanged = viewModel::setPkgMgrAllowHiddenApisSystemApps,
            onPkgMgrBypassArscRestrictionChanged = viewModel::setPkgMgrBypassArscRestriction,
            scrollState = scrollState,
            highlightRegistry = highlightRegistry
        )
    }

    if (uiState.showAiInputInfoDialog) {
        AiInputInfoDialog(
            onDismiss = viewModel::dismissAiInputInfoDialog
        )
    }
    val restartFailPrefix = stringResource(R.string.common_restart_fail_prefix)
    if (uiState.showRestartConfirmDialog) {
        ZToolRestartSystemDialog(
            title = stringResource(R.string.system_framework_restart_system_title),
            message = stringResource(R.string.system_framework_restart_system_message),
            onConfirm = {
                viewModel.restartSystem { error ->
                    Toast.makeText(
                        context,
                        restartFailPrefix + error,
                        Toast.LENGTH_SHORT
                    ).show()
                }
            },
            onDismiss = viewModel::dismissRestartConfirmDialog
        )
    }
}

private class FrameworkSettingsViewModelFactory(
    private val repository: FrameworkSettingsRepository
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(FrameworkSettingsViewModel::class.java)) {
            return FrameworkSettingsViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}

@Composable
private fun FrameworkSettingsScreen(
    title: String,
    state: FrameworkSettingsUiState,
    onBack: () -> Unit,
    onRestart: () -> Unit,
    onKeepRotationChanged: (Boolean) -> Unit,
    onAllowGetPackagesChanged: (Boolean) -> Unit,
    onDisableFlagSecureChanged: (Boolean) -> Unit,
    onForceOnOffAnimationChanged: (Boolean) -> Unit,
    onAllowUntrustedTouch: (Boolean) -> Unit,
    onAllowAllRestrictedSettingsChanged: (Boolean) -> Unit,
    onForceOnOffAnimationDurationChanged: (Int) -> Unit,
    onAiInputExpandChanged: (Boolean) -> Unit,
    onAllowRelativeAppLaunchChanged: (Boolean) -> Unit,
    onDisableHbmThermalLimitChanged: (Boolean) -> Unit,
    onDisableVolumeWarnChanged: (Boolean) -> Unit,
    onClipboardToastSuppressChanged: (Boolean) -> Unit,
    onSelectClipboardToastPackages: () -> Unit,
    onFineVolumeStepsChanged: (Boolean) -> Unit,
    onVolumeKeyNonlinearChanged: (Boolean) -> Unit,
    onPenHapticRedirectChanged: (Boolean) -> Unit,
    onPenHapticRuleAdded: (Int) -> Unit,
    onPenHapticRuleTargetChanged: (Int, Int) -> Unit,
    onPenHapticRuleRemoved: (Int) -> Unit,
    onPkgMgrAllowDowngradeChanged: (Boolean) -> Unit,
    onPkgMgrBypassVerificationChanged: (Boolean) -> Unit,
    onPkgMgrDisableVerificationAgentChanged: (Boolean) -> Unit,
    onPkgMgrBypassDigestChanged: (Boolean) -> Unit,
    onPkgMgrUsePreviousSignaturesChanged: (Boolean) -> Unit,
    onPkgMgrBypassExactSigMatchChanged: (Boolean) -> Unit,
    onPkgMgrBypassSharedUserChanged: (Boolean) -> Unit,
    onPkgMgrAllowHiddenApisSystemAppsChanged: (Boolean) -> Unit,
    onPkgMgrBypassArscRestrictionChanged: (Boolean) -> Unit,
    onAiInputSignsChanged: (String) -> Unit,
    onShowAiInputInfo: () -> Unit,
    scrollState: ScrollState,
    highlightRegistry: HighlightAnchorRegistry
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
            ZToolExtendedFloatingActionButton(
                onClick = onRestart,
                icon = {Icon(imageVector = Icons.Rounded.Refresh, contentDescription = null)},
                text = {Text(stringResource(R.string.common_restart_yes))})
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
                    .verticalScroll(scrollState)
                    .padding(horizontal = 24.dp, vertical = 24.dp)
            ) {
                ZToolSettingsList(
                    sections = frameworkSettingsSections(
                        state = state,
                        onKeepRotationChanged = onKeepRotationChanged,
                        onAllowGetPackagesChanged = onAllowGetPackagesChanged,
                        onDisableFlagSecureChanged = onDisableFlagSecureChanged,
                        onForceOnOffAnimationChanged = onForceOnOffAnimationChanged,
                        onForceOnOffAnimationDurationChanged = onForceOnOffAnimationDurationChanged,
                        onAiInputExpandChanged = onAiInputExpandChanged,
                        onAiInputSignsChanged = onAiInputSignsChanged,
                        onShowAiInputInfo = onShowAiInputInfo,
                        onAllowUntrustedTouch = onAllowUntrustedTouch,
                        onAllowAllRestrictedSettingsChanged = onAllowAllRestrictedSettingsChanged,
                        onAllowRelativeAppLaunchChanged = onAllowRelativeAppLaunchChanged,
                        onDisableHbmThermalLimitChanged = onDisableHbmThermalLimitChanged,
                        onDisableVolumeWarnChanged = onDisableVolumeWarnChanged,
                        onClipboardToastSuppressChanged = onClipboardToastSuppressChanged,
                        onSelectClipboardToastPackages = onSelectClipboardToastPackages,
                        onFineVolumeStepsChanged = onFineVolumeStepsChanged,
                        onVolumeKeyNonlinearChanged = onVolumeKeyNonlinearChanged,
                        onPenHapticRedirectChanged = onPenHapticRedirectChanged,
                        onPenHapticRuleAdded = onPenHapticRuleAdded,
                        onPenHapticRuleTargetChanged = onPenHapticRuleTargetChanged,
                        onPenHapticRuleRemoved = onPenHapticRuleRemoved,
                        onPkgMgrAllowDowngradeChanged = onPkgMgrAllowDowngradeChanged,
                        onPkgMgrBypassVerificationChanged = onPkgMgrBypassVerificationChanged,
                        onPkgMgrDisableVerificationAgentChanged = onPkgMgrDisableVerificationAgentChanged,
                        onPkgMgrBypassDigestChanged = onPkgMgrBypassDigestChanged,
                        onPkgMgrUsePreviousSignaturesChanged = onPkgMgrUsePreviousSignaturesChanged,
                        onPkgMgrBypassExactSigMatchChanged = onPkgMgrBypassExactSigMatchChanged,
                        onPkgMgrBypassSharedUserChanged = onPkgMgrBypassSharedUserChanged,
                        onPkgMgrAllowHiddenApisSystemAppsChanged = onPkgMgrAllowHiddenApisSystemAppsChanged,
                        onPkgMgrBypassArscRestrictionChanged = onPkgMgrBypassArscRestrictionChanged,
                    ),
                    bottomPadding = 96.dp,
                    highlightRegistry = highlightRegistry
                )
            }
        }
    }
}

@Composable
private fun frameworkSettingsSections(
    state: FrameworkSettingsUiState,
    onKeepRotationChanged: (Boolean) -> Unit,
    onAllowGetPackagesChanged: (Boolean) -> Unit,
    onDisableFlagSecureChanged: (Boolean) -> Unit,
    onForceOnOffAnimationChanged: (Boolean) -> Unit,
    onAllowUntrustedTouch: (Boolean) -> Unit,
    onAllowAllRestrictedSettingsChanged: (Boolean) -> Unit,
    onForceOnOffAnimationDurationChanged: (Int) -> Unit,
    onAiInputExpandChanged: (Boolean) -> Unit,
    onAllowRelativeAppLaunchChanged: (Boolean) -> Unit,
    onDisableHbmThermalLimitChanged: (Boolean) -> Unit,
    onDisableVolumeWarnChanged: (Boolean) -> Unit,
    onClipboardToastSuppressChanged: (Boolean) -> Unit,
    onSelectClipboardToastPackages: () -> Unit,
    onFineVolumeStepsChanged: (Boolean) -> Unit,
    onVolumeKeyNonlinearChanged: (Boolean) -> Unit,
    onPenHapticRedirectChanged: (Boolean) -> Unit,
    onPenHapticRuleAdded: (Int) -> Unit,
    onPenHapticRuleTargetChanged: (Int, Int) -> Unit,
    onPenHapticRuleRemoved: (Int) -> Unit,
    onPkgMgrAllowDowngradeChanged: (Boolean) -> Unit,
    onPkgMgrBypassVerificationChanged: (Boolean) -> Unit,
    onPkgMgrDisableVerificationAgentChanged: (Boolean) -> Unit,
    onPkgMgrBypassDigestChanged: (Boolean) -> Unit,
    onPkgMgrUsePreviousSignaturesChanged: (Boolean) -> Unit,
    onPkgMgrBypassExactSigMatchChanged: (Boolean) -> Unit,
    onPkgMgrBypassSharedUserChanged: (Boolean) -> Unit,
    onPkgMgrAllowHiddenApisSystemAppsChanged: (Boolean) -> Unit,
    onPkgMgrBypassArscRestrictionChanged: (Boolean) -> Unit,
    onAiInputSignsChanged: (String) -> Unit,
    onShowAiInputInfo: () -> Unit
): List<SettingSection> {
    return listOf(
        SettingSection(
            title = stringResource(R.string.system_framework_screen_and_display_title),
            items = buildList {
                add(
                    SettingItem.Switch(
                        title = stringResource(R.string.system_framework_keep_rotation_enable_title),
                        summary = stringResource(R.string.system_framework_keep_rotation_enable_summary),
                        checked = state.keepRotation,
                        onCheckedChange = onKeepRotationChanged,
                        key = "framework_keep_rotation"
                    )
                )
                add(
                    SettingItem.Switch(
                        title = stringResource(R.string.system_framework_disable_hbm_thermal_limit_title),
                        checked = state.disableHbmThermalLimit,
                        onCheckedChange = onDisableHbmThermalLimitChanged,
                        key = "framework_disable_hbm_thermal_limit"
                    )
                )
                add(
                    SettingItem.Switch(
                        title = stringResource(R.string.system_framework_force_on_off_animation),
                        summary = stringResource(R.string.system_framework_force_on_off_animation_summary),
                        checked = state.forceOnOffAnimation,
                        onCheckedChange = onForceOnOffAnimationChanged,
                        key = "framework_force_on_off_animation"
                    )
                )
                if (state.forceOnOffAnimation) {
                    add(
                        SettingItem.Custom(
                            content = {
                                ScreenOnOffAnimationDuration(
                                    state = state,
                                    onForceOnOffAnimationDurationChanged = onForceOnOffAnimationDurationChanged
                                )
                            },
                            key = "framework_screen_on_off_animation_duration"
                        )
                    )
                }
            }
        ),
        SettingSection(
            title = stringResource(R.string.system_framework_disable_zui_applist_title),
            items = buildList {
                add(
                    SettingItem.Switch(
                        title = stringResource(R.string.system_framework_disable_zui_applist_enable_title),
                        summary = stringResource(R.string.system_framework_disable_zui_applist_enable_summary),
                        checked = state.allowGetPackages,
                        onCheckedChange = onAllowGetPackagesChanged,
                        key = "framework_disable_zui_applist"
                    )
                )
                add(
                    SettingItem.Switch(
                        title = stringResource(R.string.system_framework_allow_relative_app_launch_title),
                        checked = state.allowRelativeAppLaunch,
                        onCheckedChange = onAllowRelativeAppLaunchChanged,
                        key = "framework_allow_relative_app_launch"
                    )
                )
                add(
                    SettingItem.Switch(
                        title = stringResource(R.string.system_framework_allow_untrusted_touch),
                        checked = state.allowUntrustedTouch,
                        onCheckedChange = onAllowUntrustedTouch,
                        key = "framework_allow_untrusted_touch"
                    )
                )
                add(
                    SettingItem.Switch(
                        title = stringResource(R.string.system_framework_allow_all_restricted_settings_title),
                        summary = stringResource(R.string.system_framework_allow_all_restricted_settings_summary),
                        checked = state.allowAllRestrictedSettings,
                        onCheckedChange = onAllowAllRestrictedSettingsChanged,
                        key = "framework_allow_all_restricted_settings"
                    )
                )
                add(
                    SettingItem.Switch(
                        title = stringResource(R.string.system_framework_disable_flag_secure_title),
                        summary = stringResource(R.string.system_framework_disable_flag_secure_summary),
                        checked = state.disableFlagSecure,
                        onCheckedChange = onDisableFlagSecureChanged,
                        key = "framework_disable_flag_secure"
                    )
                )
                add(
                    SettingItem.Switch(
                        title = stringResource(R.string.system_framework_clipboard_toast_suppress_title),
                        summary = stringResource(R.string.system_framework_clipboard_toast_suppress_summary),
                        checked = state.clipboardAccessToastSuppress,
                        onCheckedChange = onClipboardToastSuppressChanged,
                        key = "framework_clipboard_access_toast"
                    )
                )
                if (state.clipboardAccessToastSuppress) {
                    add(
                        SettingItem.Custom(
                            content = {
                                ClipboardToastPackagesRow(
                                    packageCount = state.clipboardAccessToastPackages.size,
                                    onClick = onSelectClipboardToastPackages
                                )
                            },
                            key = "framework_clipboard_access_toast_apps"
                        )
                    )
                }
            }
        ),
        SettingSection(
            title = stringResource(R.string.system_framework_pkgmgr_section_title),
            items = buildList {
                add(
                    SettingItem.Custom(
                        key = "deco_framework_android16_note",
                        content = { PkgMgrAndroid16Note() }
                    )
                )
                add(
                    SettingItem.Switch(
                        title = stringResource(R.string.system_framework_pkgmgr_allow_downgrade_title),
                        summary = stringResource(R.string.system_framework_pkgmgr_allow_downgrade_summary),
                        checked = state.pkgMgrAllowDowngrade,
                        onCheckedChange = onPkgMgrAllowDowngradeChanged,
                        key = "framework_pkgmgr_allow_downgrade"
                    )
                )
                add(
                    SettingItem.Switch(
                        title = stringResource(R.string.system_framework_pkgmgr_bypass_verification_title),
                        summary = stringResource(R.string.system_framework_pkgmgr_bypass_verification_summary),
                        checked = state.pkgMgrBypassVerification,
                        onCheckedChange = onPkgMgrBypassVerificationChanged,
                        key = "framework_pkgmgr_bypass_verification"
                    )
                )
                add(
                    SettingItem.Switch(
                        title = stringResource(R.string.system_framework_pkgmgr_disable_verification_agent_title),
                        summary = stringResource(R.string.system_framework_pkgmgr_disable_verification_agent_summary),
                        checked = state.pkgMgrDisableVerificationAgent,
                        onCheckedChange = onPkgMgrDisableVerificationAgentChanged,
                        key = "framework_pkgmgr_disable_verification_agent"
                    )
                )
                add(
                    SettingItem.Switch(
                        title = stringResource(R.string.system_framework_pkgmgr_bypass_digest_title),
                        summary = stringResource(R.string.system_framework_pkgmgr_bypass_digest_summary),
                        checked = state.pkgMgrBypassDigest,
                        onCheckedChange = onPkgMgrBypassDigestChanged,
                        key = "framework_pkgmgr_bypass_digest"
                    )
                )
                if (state.pkgMgrBypassDigest) {
                    add(
                        SettingItem.Switch(
                            title = stringResource(R.string.system_framework_pkgmgr_use_previous_signatures_title),
                            summary = stringResource(R.string.system_framework_pkgmgr_use_previous_signatures_summary),
                            checked = state.pkgMgrUsePreviousSignatures,
                            onCheckedChange = onPkgMgrUsePreviousSignaturesChanged,
                            key = "framework_pkgmgr_use_previous_signatures"
                        )
                    )
                }
                add(
                    SettingItem.Switch(
                        title = stringResource(R.string.system_framework_pkgmgr_bypass_exact_sig_match_title),
                        summary = stringResource(R.string.system_framework_pkgmgr_bypass_exact_sig_match_summary),
                        checked = state.pkgMgrBypassExactSigMatch,
                        onCheckedChange = onPkgMgrBypassExactSigMatchChanged,
                        key = "framework_pkgmgr_bypass_exact_sig_match"
                    )
                )
                add(
                    SettingItem.Switch(
                        title = stringResource(R.string.system_framework_pkgmgr_bypass_shared_user_title),
                        summary = stringResource(R.string.system_framework_pkgmgr_bypass_shared_user_summary),
                        checked = state.pkgMgrBypassSharedUser,
                        onCheckedChange = onPkgMgrBypassSharedUserChanged,
                        key = "framework_pkgmgr_bypass_shared_user"
                    )
                )
                add(
                    SettingItem.Switch(
                        title = stringResource(R.string.system_framework_pkgmgr_allow_hidden_apis_title),
                        summary = stringResource(R.string.system_framework_pkgmgr_allow_hidden_apis_summary),
                        checked = state.pkgMgrAllowHiddenApisSystemApps,
                        onCheckedChange = onPkgMgrAllowHiddenApisSystemAppsChanged,
                        key = "framework_pkgmgr_allow_hidden_apis"
                    )
                )
                add(
                    SettingItem.Switch(
                        title = stringResource(R.string.system_framework_pkgmgr_bypass_arsc_title),
                        summary = stringResource(R.string.system_framework_pkgmgr_bypass_arsc_summary),
                        checked = state.pkgMgrBypassArscRestriction,
                        onCheckedChange = onPkgMgrBypassArscRestrictionChanged,
                        key = "framework_pkgmgr_bypass_arsc"
                    )
                )
            }
        ),
        SettingSection(
            title = stringResource(R.string.system_framework_misc_title),
            items = listOf(
                SettingItem.Switch(
                    title = stringResource(R.string.system_framework_disable_volume_warn_title),
                    checked = state.disableSafeVolumeWarn,
                    onCheckedChange = onDisableVolumeWarnChanged,
                    key = "framework_disable_volume_warn"
                ),
                SettingItem.Switch(
                    title = stringResource(R.string.system_framework_volume_key_nonlinear_title),
                    summary = stringResource(R.string.system_framework_volume_key_nonlinear_summary),
                    checked = state.volumeKeyNonlinear,
                    onCheckedChange = onVolumeKeyNonlinearChanged,
                    key = "framework_volume_key_nonlinear"
                ),
                SettingItem.Switch(
                    title = stringResource(R.string.system_framework_fine_volume_steps_title),
                    summary = stringResource(R.string.system_framework_fine_volume_steps_summary),
                    checked = state.fineVolumeSteps,
                    onCheckedChange = onFineVolumeStepsChanged,
                    key = "framework_fine_volume_steps"
                ),
                SettingItem.Custom(
                    content = {
                        AiInputSettingsContent(
                            state = state,
                            onAiInputExpandChanged = onAiInputExpandChanged,
                            onAiInputSignsChanged = onAiInputSignsChanged,
                            onShowAiInputInfo = onShowAiInputInfo
                        )
                    },
                    key = "framework_ai_input_expand"
                )
            )
        ),
        SettingSection(
            title = stringResource(R.string.system_framework_pen_haptic_title),
            items = buildList {
                add(
                    SettingItem.Switch(
                        title = stringResource(R.string.system_framework_pen_haptic_redirect_title),
                        summary = stringResource(R.string.system_framework_pen_haptic_redirect_summary),
                        checked = state.penHapticRedirect,
                        onCheckedChange = onPenHapticRedirectChanged,
                        key = "framework_pen_haptic_redirect"
                    )
                )
                if (state.penHapticRedirect) {
                    add(
                        SettingItem.Custom(
                            content = {
                                PenHapticRedirectEditor(
                                    rules = state.penHapticRedirectRules,
                                    onAddRule = onPenHapticRuleAdded,
                                    onTargetChanged = onPenHapticRuleTargetChanged,
                                    onRemoveRule = onPenHapticRuleRemoved
                                )
                            },
                            key = "framework_pen_haptic_redirect_rules"
                        )
                    )
                }
            }
        )
    )
}

@Composable
private fun PkgMgrAndroid16Note() {
    Text(
        text = stringResource(R.string.system_framework_pkgmgr_android16_note),
        style = MaterialTheme.typography.bodySmall,
        color = LocalZToolColorScheme.current.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
    )
}

@Composable
private fun ScreenOnOffAnimationDuration(
    state: FrameworkSettingsUiState,
    onForceOnOffAnimationDurationChanged: (Int) -> Unit,
) {
    if (state.forceOnOffAnimation) {
        ZToolSliderRow(
            title = stringResource(R.string.system_framework_screen_on_off_animation_duration),
            value = state.forceOnOffAnimationDuration.toFloat(),
            valueText = state.forceOnOffAnimationDuration.toString() + "ms",
            onValueChange = {
                onForceOnOffAnimationDurationChanged(
                    snapToAnimationDuration(it)
                )
            },
            steps = ANIMATION_DURATION_STEPS,
            valueRange = 0f..1000f
        )
    }
}

private const val ANIMATION_DURATION_MIN_MS = 0
private const val ANIMATION_DURATION_MAX_MS = 1000
private const val ANIMATION_DURATION_STEP_MS = 50
private const val ANIMATION_DURATION_STEPS =
    (ANIMATION_DURATION_MAX_MS - ANIMATION_DURATION_MIN_MS) / ANIMATION_DURATION_STEP_MS - 1

private fun snapToAnimationDuration(value: Float): Int {
    return ((value / ANIMATION_DURATION_STEP_MS).roundToInt() * ANIMATION_DURATION_STEP_MS)
        .coerceIn(ANIMATION_DURATION_MIN_MS, ANIMATION_DURATION_MAX_MS)
}

@Composable
private fun AiInputSettingsContent(
    state: FrameworkSettingsUiState,
    onAiInputExpandChanged: (Boolean) -> Unit,
    onAiInputSignsChanged: (String) -> Unit,
    onShowAiInputInfo: () -> Unit
) {
    ZToolSwitchRow(
        title = stringResource(R.string.system_framework_ai_input_expand_title),
        summary = stringResource(R.string.system_framework_ai_input_expand_summary),
        checked = state.aiInputExpand,
        onCheckedChange = onAiInputExpandChanged
    )
    if (state.aiInputExpand) {
        IconButton(
            onClick = onShowAiInputInfo,
            modifier = Modifier.padding(start = 12.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.Info,
                contentDescription = null,
                tint = LocalZToolColorScheme.current.onSurfaceVariant
            )
        }
        ZToolOutlinedTextField(
            value = state.aiInputSigns,
            onValueChange = onAiInputSignsChanged,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 8.dp),
            label = stringResource(R.string.system_framework_custom_detector_hint),
            isError = state.aiInputSignsError != null,
            supportingText = state.aiInputSignsError,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
        )
    }
}

@Composable
private fun AiInputInfoDialog(
    onDismiss: () -> Unit
) {
    ZToolQuickHelpDialog(
        title = stringResource(R.string.system_framework_ai_input_quick_help_title),
        summary = stringResource(R.string.system_framework_ai_input_quick_help_summary),
        quickLabel = stringResource(R.string.common_quick_help_lookup_title),
        examplesLabel = stringResource(R.string.common_quick_help_examples_title),
        items = listOf(
            QuickHelpItem(
                stringResource(R.string.system_framework_ai_input_quick_help_scope),
                stringResource(R.string.system_framework_ai_input_quick_help_scope_desc)
            ),
            QuickHelpItem(
                stringResource(R.string.system_framework_ai_input_quick_help_separator),
                stringResource(R.string.system_framework_ai_input_quick_help_separator_desc)
            ),
            QuickHelpItem(
                stringResource(R.string.system_framework_ai_input_quick_help_conflict),
                stringResource(R.string.system_framework_ai_input_quick_help_conflict_desc)
            )
        ),
        examples = listOf(
            QuickHelpExample(
                stringResource(R.string.system_framework_ai_input_quick_help_example_default_value),
                stringResource(R.string.system_framework_ai_input_quick_help_example_default)
            ),
            QuickHelpExample(
                stringResource(R.string.system_framework_ai_input_quick_help_example_custom_value),
                stringResource(R.string.system_framework_ai_input_quick_help_example_custom)
            )
        ),
        note = stringResource(R.string.system_framework_ai_input_quick_help_note),
        onDismiss = onDismiss,
        confirmButtonText = stringResource(android.R.string.ok)
    )
}

@Composable
private fun PenHapticRedirectEditor(
    rules: List<PenHapticRedirectRule>,
    onAddRule: (Int) -> Unit,
    onTargetChanged: (Int, Int) -> Unit,
    onRemoveRule: (Int) -> Unit
) {
    val silentSuffix = stringResource(R.string.system_framework_pen_wave_silent_label)
    val names = LinkedHashMap<Int, String>()
    for (waveform in PenHapticWaveformCatalog.all) {
        val label = stringResource(waveform.nameRes)
        names[waveform.id] = if (waveform.silent) label + silentSuffix else label
    }
    val usedSources = rules.mapTo(HashSet()) { it.sourceId }
    val availableSources = PenHapticWaveformCatalog.all.filter { it.id !in usedSources }

    Column(modifier = Modifier.fillMaxWidth()) {
        if (rules.isEmpty()) {
            Text(
                text = stringResource(R.string.system_framework_pen_haptic_rules_empty_message),
                style = MaterialTheme.typography.bodyMedium,
                color = LocalZToolColorScheme.current.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp)
            )
        }
        rules.forEachIndexed { index, rule ->
            if (index > 0) {
                ZToolSettingsDivider()
            }
            PenHapticRedirectRuleRow(
                rule = rule,
                names = names,
                onTargetChanged = onTargetChanged,
                onRemoveRule = onRemoveRule
            )
        }
        ZToolSettingsDivider()
        ZToolPopupDialogField(
            value = stringResource(R.string.system_framework_pen_haptic_add_rule_button),
            options = availableSources,
            optionLabel = { names[it.id].orEmpty() },
            onOptionSelected = { onAddRule(it.id) },
            enabled = availableSources.isNotEmpty(),
            dialogTitle = stringResource(R.string.system_framework_pen_haptic_source_title),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp)
        )
    }
}

@Composable
private fun PenHapticRedirectRuleRow(
    rule: PenHapticRedirectRule,
    names: Map<Int, String>,
    onTargetChanged: (Int, Int) -> Unit,
    onRemoveRule: (Int) -> Unit
) {
    val source = PenHapticWaveformCatalog.byId(rule.sourceId)
    val targets = source
        ?.let { PenHapticWaveformCatalog.ofKind(it.kind) }
        ?.filter { it.id != rule.sourceId }
        .orEmpty()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 24.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = names[rule.sourceId] ?: rule.sourceId.toString(),
            style = MaterialTheme.typography.bodyLarge,
            color = LocalZToolColorScheme.current.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        Icon(
            imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
            contentDescription = null,
            tint = LocalZToolColorScheme.current.onSurfaceVariant,
            modifier = Modifier.size(18.dp)
        )
        ZToolPopupDialogField(
            value = names[rule.targetId] ?: rule.targetId.toString(),
            options = targets,
            optionLabel = { names[it.id].orEmpty() },
            onOptionSelected = { onTargetChanged(rule.sourceId, it.id) },
            dialogTitle = stringResource(R.string.system_framework_pen_haptic_target_title),
            modifier = Modifier.weight(1.4f)
        )
        IconButton(onClick = { onRemoveRule(rule.sourceId) }) {
            Icon(
                imageVector = Icons.Rounded.Delete,
                contentDescription = stringResource(R.string.system_framework_pen_haptic_remove_rule_button),
                tint = LocalZToolColorScheme.current.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ClipboardToastPackagesRow(
    packageCount: Int,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = stringResource(
                R.string.system_framework_clipboard_toast_apps_summary,
                packageCount
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = LocalZToolColorScheme.current.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Icon(
            imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
            contentDescription = null,
            tint = LocalZToolColorScheme.current.onSurfaceVariant
        )
    }
}
