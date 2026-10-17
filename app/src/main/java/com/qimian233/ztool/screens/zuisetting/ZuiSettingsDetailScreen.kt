package com.qimian233.ztool.screens.zuisetting

import android.app.Dialog
import android.content.Intent
import android.net.Uri
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import com.qimian233.ztool.ui.components.LoadingDialog
import com.qimian233.ztool.R
import com.qimian233.ztool.data.settings.CustomizeAboutDeviceInfoRepository
import com.qimian233.ztool.data.settings.SettingsDetailRepository
import com.qimian233.ztool.screens.safecenter.RestartConfirmDialog
import com.qimian233.ztool.ui.components.HighlightAnchorRegistry
import com.qimian233.ztool.ui.components.HighlightController
import com.qimian233.ztool.ui.components.SettingItem
import com.qimian233.ztool.ui.components.SettingSection
import com.qimian233.ztool.ui.components.ZToolDialog
import com.qimian233.ztool.ui.components.ZToolExtendedFloatingActionButton
import com.qimian233.ztool.ui.components.ZToolScaffold
import com.qimian233.ztool.ui.components.ZToolSettingsList
import com.qimian233.ztool.ui.components.ZToolTextButton
import com.qimian233.ztool.ui.components.ZToolTextInputRow
import com.qimian233.ztool.ui.components.ZToolTopAppBar
import com.qimian233.ztool.ui.components.showPlatformComposeDialog
import com.qimian233.ztool.ui.theme.LocalZToolColorScheme
import com.qimian233.ztool.viewmodel.SettingsDetailFontInstallResult
import com.qimian233.ztool.viewmodel.SettingsDetailFontPreparationResult
import com.qimian233.ztool.viewmodel.SettingsDetailUiState
import com.qimian233.ztool.viewmodel.SettingsDetailViewModel

@Composable
fun SettingsDetailRoute(
    title: String,
    packageName: String,
    onBack: () -> Unit,
    targetId: String? = null
) {
    val context = LocalContext.current
    val activity = context as? android.app.Activity
    val owner = LocalViewModelStoreOwner.current
        ?: error("SettingsDetailRoute requires a ViewModelStoreOwner")
    val viewModel = remember(owner) {
        ViewModelProvider(
            owner,
            SettingsDetailViewModelFactory(
                SettingsDetailRepository(context.applicationContext),
                CustomizeAboutDeviceInfoRepository(context.applicationContext)
            )
        )[SettingsDetailViewModel::class.java]
    }
    val gotItButtonText = stringResource(R.string.settings_got_it_button)
    val saveSuccessTitleText = stringResource(R.string.settings_success_title)
    val saveSuccessMessageText = stringResource(R.string.settings_save_success_message)
    val errorTitleText = stringResource(R.string.common_error_title)
    val errorPrefixText = stringResource(R.string.common_error_prefix)
    val importingFontText = stringResource(R.string.settings_importing_font)
    val importSuccessTitleText = stringResource(R.string.settings_import_success_title)
    val importSuccessMessageText = stringResource(R.string.settings_import_success_message)
    val importFailedTitleText = stringResource(R.string.settings_import_failed_title)
    val preparingFontFileText = stringResource(R.string.settings_preparing_font_file)
    val unknownFontFilenameText = stringResource(R.string.settings_unknown_font_filename)
    val selectTtfFileText = stringResource(R.string.settings_select_ttf_file)
    val aboutDeviceInfoImageSavedText = stringResource(R.string.settings_about_device_info_image_saved)
    val aboutDeviceInfoImageSaveFailedText = stringResource(R.string.settings_about_device_info_image_save_failed)
    var loadingDialog by remember { mutableStateOf<LoadingDialog?>(null) }
    val aboutDeviceInfoImageLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            val saved = viewModel.saveAboutDeviceInfoHeaderImage(uri)
            Toast.makeText(
                context,
                if (saved) aboutDeviceInfoImageSavedText else aboutDeviceInfoImageSaveFailedText,
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    fun showMessageDialog(
        dialogTitle: String,
        message: String
    ) {
        if (activity == null) return
        showPlatformComposeDialog(activity, width = ViewGroup.LayoutParams.WRAP_CONTENT) { dialog ->
            SimpleSettingsDetailDialogContent(
                title = dialogTitle,
                message = message,
                confirmText = gotItButtonText,
                onConfirm = { dialog.dismiss() }
            )
        }
    }

    fun showComposeDialog(content: @Composable () -> Unit): Dialog? {
        return activity?.let {
            showPlatformComposeDialog(it, width = ViewGroup.LayoutParams.WRAP_CONTENT) {
                content()
            }
        }
    }

    lateinit var handleFontSelection: (Uri?) -> Unit
    val fontPickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val data = result.data
        if (result.resultCode == android.app.Activity.RESULT_OK && data != null) {
            handleFontSelection(data.data)
        }
    }

    fun startFontImport(fontName: String, fontDescription: String) {
        if (activity == null) return
        loadingDialog = LoadingDialog(context).also {
            it.show(importingFontText)
        }

        viewModel.installFont(fontName, fontDescription) { result ->
            activity.runOnUiThread {
                loadingDialog?.dismiss()
                when (result) {
                    SettingsDetailFontInstallResult.Success -> {
                        showMessageDialog(
                        dialogTitle = importSuccessTitleText,
                        message = importSuccessMessageText
                    )
                }
                    is SettingsDetailFontInstallResult.Failure -> {
                        showMessageDialog(
                            dialogTitle = importFailedTitleText,
                            message = result.message
                        )
                    }
                }
            }
        }
    }

    fun showFontInputDialog(originalFileName: String) {
        lateinit var dialog: Dialog
        val shownDialog = showComposeDialog {
            FontInputDialogContent(
                originalDescription = stringResource(R.string.settings_default_font_description, originalFileName),
                onConfirm = { name, description ->
                    if (name.isNotEmpty() && description.isNotEmpty()) {
                        dialog.dismiss()
                        startFontImport(name, description)
                    }
                },
                onCancel = { dialog.dismiss() }
            )
        }
        if (shownDialog != null) {
            dialog = shownDialog
        }
    }

    handleFontSelection = { uri ->
        if (uri != null && activity != null) {
            loadingDialog = LoadingDialog(context).also {
                it.show(preparingFontFileText)
            }
            viewModel.prepareFontImport(uri) { result ->
                activity.runOnUiThread {
                    loadingDialog?.dismiss()
                    when (result) {
                        is SettingsDetailFontPreparationResult.Success -> {
                            showFontInputDialog(
                                result.originalFileName ?: unknownFontFilenameText
                            )
                        }
                        is SettingsDetailFontPreparationResult.Failure -> {
                            Toast.makeText(context, "File Error: " + result.message, Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        }
    }

    fun startFontImportProcess() {
        val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
            type = "*/*"
            addCategory(Intent.CATEGORY_OPENABLE)
            putExtra(
                Intent.EXTRA_MIME_TYPES,
                arrayOf("font/ttf", "application/x-font-ttf", "application/octet-stream")
            )
        }
        try {
            fontPickerLauncher.launch(
                Intent.createChooser(intent, selectTtfFileText)
            )
        } catch (_: Exception) {
            Toast.makeText(context, R.string.settings_no_file_manager_found, Toast.LENGTH_SHORT).show()
        }
    }

    LaunchedEffect(viewModel) {
        viewModel.loadSettings()
    }

    DisposableEffect(Unit) {
        onDispose {
            loadingDialog?.dismiss()
        }
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
        SettingsDetailScreen(
            title = title,
            state = uiState,
            onBack = onBack,
            scrollState = scrollState,
            highlightRegistry = highlightRegistry,
            onImportFont = ::startFontImportProcess,
        onAllowNativePermissionControllerChanged = viewModel::setAllowNativePermissionController,
        onAllowDisableDolbyChanged = viewModel::setAllowDisableDolby,
        onAppDetailsChanged = viewModel::setAppDetails,
        onAppIconUnmaskChanged = viewModel::setAppIconUnmask,
        aboutDeviceInfoState = uiState.aboutDeviceInfoState,
        onAboutDeviceInfoEnabledChanged = viewModel::setAboutDeviceInfoEnabled,
        onAboutDeviceInfoModelEnabledChanged = viewModel::setAboutDeviceInfoModelEnabled,
        onAboutDeviceInfoCpuEnabledChanged = viewModel::setAboutDeviceInfoCpuEnabled,
        onAboutDeviceInfoRamEnabledChanged = viewModel::setAboutDeviceInfoRamEnabled,
        onAboutDeviceInfoRomEnabledChanged = viewModel::setAboutDeviceInfoRomEnabled,
        onAboutDeviceInfoSoftwareEnabledChanged = viewModel::setAboutDeviceInfoSoftwareEnabled,
        onAboutDeviceInfoHeaderEnabledChanged = viewModel::setAboutDeviceInfoHeaderEnabled,
        onAboutDeviceInfoModelChanged = viewModel::setAboutDeviceInfoModel,
        onAboutDeviceInfoCpuChanged = viewModel::setAboutDeviceInfoCpu,
        onAboutDeviceInfoRamChanged = viewModel::setAboutDeviceInfoRam,
        onAboutDeviceInfoRomChanged = viewModel::setAboutDeviceInfoRom,
        onAboutDeviceInfoSoftwareChanged = viewModel::setAboutDeviceInfoSoftware,
        onAboutDeviceInfoHeaderSelected = { aboutDeviceInfoImageLauncher.launch(arrayOf("image/*")) },
        onAllowAddingLanguageChanged = viewModel::setAllowAddingLanguage,
        onRestartScope = viewModel::showRestartDialog,
        )
    }

    if (uiState.showRestartDialog) {
        RestartConfirmDialog(
            packageName = packageName,
            onConfirm = {
                viewModel.restartScope()
            },
            onDismiss = viewModel::dismissRestartDialog
        )
    }
}

@Composable
private fun SimpleSettingsDetailDialogContent(
    title: String,
    message: String,
    confirmText: String,
    dismissText: String? = null,
    onConfirm: () -> Unit,
    onDismiss: (() -> Unit)? = null
) {
    ZToolDialog(
        onDismissRequest = { onDismiss?.invoke() ?: onConfirm() },
        title = { Text(text = title) },
        text = { Text(text = message) },
        confirmButton = {
            ZToolTextButton(onClick = onConfirm, text = confirmText)
        },
        dismissButton = if (dismissText != null && onDismiss != null) {
            {
                ZToolTextButton(
                    onClick = onDismiss,
                    text = dismissText,
                    isPrimary = false
                )
            }
        } else null
    )
}

private class SettingsDetailViewModelFactory(
    private val repository: SettingsDetailRepository,
    private val aboutDeviceInfoRepository: CustomizeAboutDeviceInfoRepository
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(SettingsDetailViewModel::class.java)) {
            return SettingsDetailViewModel(repository, aboutDeviceInfoRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}

@Composable
private fun SettingsDetailScreen(
    title: String,
    state: SettingsDetailUiState,
    onBack: () -> Unit,
    scrollState: ScrollState,
    highlightRegistry: HighlightAnchorRegistry,
    onImportFont: () -> Unit,
    onAllowNativePermissionControllerChanged: (Boolean) -> Unit,
    onAllowDisableDolbyChanged: (Boolean) -> Unit,
    onAppDetailsChanged: (Boolean) -> Unit,
    onAppIconUnmaskChanged: (Boolean) -> Unit,
    aboutDeviceInfoState: com.qimian233.ztool.data.settings.CustomizeAboutDeviceInfoState,
    onAboutDeviceInfoEnabledChanged: (Boolean) -> Unit,
    onAboutDeviceInfoModelEnabledChanged: (Boolean) -> Unit,
    onAboutDeviceInfoCpuEnabledChanged: (Boolean) -> Unit,
    onAboutDeviceInfoRamEnabledChanged: (Boolean) -> Unit,
    onAboutDeviceInfoRomEnabledChanged: (Boolean) -> Unit,
    onAboutDeviceInfoSoftwareEnabledChanged: (Boolean) -> Unit,
    onAboutDeviceInfoHeaderEnabledChanged: (Boolean) -> Unit,
    onAllowAddingLanguageChanged: (Boolean) -> Unit,
    onAboutDeviceInfoModelChanged: (String) -> Unit,
    onAboutDeviceInfoCpuChanged: (String) -> Unit,
    onAboutDeviceInfoRamChanged: (String) -> Unit,
    onAboutDeviceInfoRomChanged: (String) -> Unit,
    onAboutDeviceInfoSoftwareChanged: (String) -> Unit,
    onAboutDeviceInfoHeaderSelected: (Uri) -> Unit,
    onRestartScope: () -> Unit
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
                onClick = onRestartScope,
                icon = {
                    Icon(
                        imageVector = Icons.Rounded.Refresh,
                        contentDescription = null
                    )
                },
                text = { Text(stringResource(R.string.common_restart_yes)) }
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
                    .verticalScroll(scrollState)
                    .padding(horizontal = 24.dp, vertical = 24.dp)
                    .padding(bottom = 88.dp)
            ) {
                ZToolSettingsList(
                    highlightRegistry = highlightRegistry,
                    sections = settingsDetailSections(
                        state = state,
                        onImportFont = onImportFont,
                        onAllowNativePermissionControllerChanged = onAllowNativePermissionControllerChanged,
                        onAllowDisableDolbyChanged = onAllowDisableDolbyChanged,
                        onAppDetailsChanged = onAppDetailsChanged,
                        onAppIconUnmaskChanged = onAppIconUnmaskChanged,
                        aboutDeviceInfoState = aboutDeviceInfoState,
                        onAboutDeviceInfoEnabledChanged = onAboutDeviceInfoEnabledChanged,
                        onAboutDeviceInfoModelEnabledChanged = onAboutDeviceInfoModelEnabledChanged,
                        onAboutDeviceInfoCpuEnabledChanged = onAboutDeviceInfoCpuEnabledChanged,
                        onAboutDeviceInfoRamEnabledChanged = onAboutDeviceInfoRamEnabledChanged,
                        onAboutDeviceInfoRomEnabledChanged = onAboutDeviceInfoRomEnabledChanged,
                        onAboutDeviceInfoSoftwareEnabledChanged = onAboutDeviceInfoSoftwareEnabledChanged,
                        onAboutDeviceInfoHeaderEnabledChanged = onAboutDeviceInfoHeaderEnabledChanged,
                        onAboutDeviceInfoModelChanged = onAboutDeviceInfoModelChanged,
                        onAboutDeviceInfoCpuChanged = onAboutDeviceInfoCpuChanged,
                        onAboutDeviceInfoRamChanged = onAboutDeviceInfoRamChanged,
                        onAboutDeviceInfoRomChanged = onAboutDeviceInfoRomChanged,
                        onAboutDeviceInfoSoftwareChanged = onAboutDeviceInfoSoftwareChanged,
                        onAboutDeviceInfoHeaderSelected = onAboutDeviceInfoHeaderSelected,
                        onAllowAddingLanguageChanged = onAllowAddingLanguageChanged,
                    )
                )
            }
        }
    }
}

@Composable
private fun settingsDetailSections(
    state: SettingsDetailUiState,
    onImportFont: () -> Unit,
    onAllowNativePermissionControllerChanged: (Boolean) -> Unit,
    onAllowDisableDolbyChanged: (Boolean) -> Unit,
    onAppDetailsChanged: (Boolean) -> Unit,
    onAppIconUnmaskChanged: (Boolean) -> Unit,
    aboutDeviceInfoState: com.qimian233.ztool.data.settings.CustomizeAboutDeviceInfoState,
    onAboutDeviceInfoEnabledChanged: (Boolean) -> Unit,
    onAboutDeviceInfoModelEnabledChanged: (Boolean) -> Unit,
    onAboutDeviceInfoCpuEnabledChanged: (Boolean) -> Unit,
    onAboutDeviceInfoRamEnabledChanged: (Boolean) -> Unit,
    onAboutDeviceInfoRomEnabledChanged: (Boolean) -> Unit,
    onAboutDeviceInfoSoftwareEnabledChanged: (Boolean) -> Unit,
    onAboutDeviceInfoHeaderEnabledChanged: (Boolean) -> Unit,
    onAllowAddingLanguageChanged: (Boolean) -> Unit,
    onAboutDeviceInfoModelChanged: (String) -> Unit,
    onAboutDeviceInfoCpuChanged: (String) -> Unit,
    onAboutDeviceInfoRamChanged: (String) -> Unit,
    onAboutDeviceInfoRomChanged: (String) -> Unit,
    onAboutDeviceInfoSoftwareChanged: (String) -> Unit,
    onAboutDeviceInfoHeaderSelected: (Uri) -> Unit,
): List<SettingSection> {
    return buildList {
        add(
            SettingSection(
                title = stringResource(R.string.settings_font_settings_title),
                items = listOf(
                    settingsDetailActionItem(
                        key = "settings_detail_import_font",
                        title = stringResource(R.string.settings_import_font_title),
                        summary = stringResource(R.string.settings_import_font_summary),
                        onClick = onImportFont,
                        icon = {
                            Icon(
                                imageVector = Icons.Rounded.Add,
                                contentDescription = null,
                                tint = LocalZToolColorScheme.current.onSurfaceVariant
                            )
                        }
                    )
                )
            )
        )

        add(
            SettingSection(
                title = stringResource(R.string.settings_about_device_info_title),
                items = buildList {
                    add(
                        SettingItem.Switch(
                            key = "settings_detail_about_device_info_master",
                            title = stringResource(R.string.settings_about_device_info_master),
                            summary = stringResource(R.string.settings_about_device_info_master_summary),
                            checked = aboutDeviceInfoState.enabled,
                            onCheckedChange = onAboutDeviceInfoEnabledChanged
                        )
                    )
                    if (aboutDeviceInfoState.enabled) {
                        add(
                            SettingItem.Switch(
                                key = "settings_detail_about_device_info_model",
                                title = stringResource(R.string.settings_about_device_info_model_title),
                                checked = aboutDeviceInfoState.modelEnabled,
                                onCheckedChange = onAboutDeviceInfoModelEnabledChanged
                            )
                        )
                        if (aboutDeviceInfoState.modelEnabled) {
                            add(SettingItem.TextInput(
                                key = "settings_detail_about_device_info_model_input",
                                label = stringResource(R.string.settings_about_device_info_model_label),
                                value = aboutDeviceInfoState.model,
                                onValueChange = onAboutDeviceInfoModelChanged
                            ))
                        }
                        add(
                            SettingItem.Switch(
                                key = "settings_detail_about_device_info_cpu",
                                title = stringResource(R.string.settings_about_device_info_cpu_title),
                                checked = aboutDeviceInfoState.cpuEnabled,
                                onCheckedChange = onAboutDeviceInfoCpuEnabledChanged
                            )
                        )
                        if (aboutDeviceInfoState.cpuEnabled) {
                            add(SettingItem.TextInput(
                                key = "settings_detail_about_device_info_cpu_input",
                                label = stringResource(R.string.settings_about_device_info_cpu_label),
                                value = aboutDeviceInfoState.cpu,
                                onValueChange = onAboutDeviceInfoCpuChanged
                            ))
                        }
                        add(
                            SettingItem.Switch(
                                key = "settings_detail_about_device_info_ram",
                                title = stringResource(R.string.settings_about_device_info_ram_title),
                                checked = aboutDeviceInfoState.ramEnabled,
                                onCheckedChange = onAboutDeviceInfoRamEnabledChanged
                            )
                        )
                        if (aboutDeviceInfoState.ramEnabled) {
                            add(SettingItem.TextInput(
                                key = "settings_detail_about_device_info_ram_input",
                                label = stringResource(R.string.settings_about_device_info_ram_label),
                                value = aboutDeviceInfoState.ram,
                                onValueChange = onAboutDeviceInfoRamChanged
                            ))
                        }
                        add(
                            SettingItem.Switch(
                                key = "settings_detail_about_device_info_rom",
                                title = stringResource(R.string.settings_about_device_info_rom_title),
                                checked = aboutDeviceInfoState.romEnabled,
                                onCheckedChange = onAboutDeviceInfoRomEnabledChanged
                            )
                        )
                        if (aboutDeviceInfoState.romEnabled) {
                            add(SettingItem.TextInput(
                                key = "settings_detail_about_device_info_rom_input",
                                label = stringResource(R.string.settings_about_device_info_rom_label),
                                value = aboutDeviceInfoState.rom,
                                onValueChange = onAboutDeviceInfoRomChanged
                            ))
                        }
                        add(
                            SettingItem.Switch(
                                key = "settings_detail_about_device_info_software",
                                title = stringResource(R.string.settings_about_device_info_software_title),
                                checked = aboutDeviceInfoState.softwareEnabled,
                                onCheckedChange = onAboutDeviceInfoSoftwareEnabledChanged
                            )
                        )
                        if (aboutDeviceInfoState.softwareEnabled) {
                            add(SettingItem.TextInput(
                                key = "settings_detail_about_device_info_software_input",
                                label = stringResource(R.string.settings_about_device_info_software_label),
                                value = aboutDeviceInfoState.software,
                                onValueChange = onAboutDeviceInfoSoftwareChanged
                            ))
                        }
                        add(
                            SettingItem.Switch(
                                key = "settings_detail_about_device_info_header",
                                title = stringResource(R.string.settings_about_device_info_header_title),
                                checked = aboutDeviceInfoState.headerEnabled,
                                onCheckedChange = onAboutDeviceInfoHeaderEnabledChanged
                            )
                        )
                        if (aboutDeviceInfoState.headerEnabled) {
                            add(
                                settingsDetailActionItem(
                                    key = "settings_detail_about_device_info_header_action",
                                    title = stringResource(R.string.settings_about_device_info_header_action),
                                    summary = stringResource(R.string.settings_about_device_info_header_action_summary),
                                    onClick = { onAboutDeviceInfoHeaderSelected(Uri.EMPTY) }
                                )
                            )
                        }
                    }
                }
            )
        )

        add(
            SettingSection(
                title = stringResource(R.string.settings_misc),
                items = listOf(
                    SettingItem.Switch(
                        key = "settings_detail_native_permission_controller",
                        title = stringResource(R.string.settings_native_permission_controller_enable_title),
                        summary = stringResource(R.string.settings_native_permission_controller_enable_summary),
                        checked = state.allowNativePermissionController,
                        onCheckedChange = onAllowNativePermissionControllerChanged
                    ),
                    SettingItem.Switch(
                        key = "settings_detail_allow_adding_language",
                        title = stringResource(R.string.settings_allow_adding_language),
                        checked = state.allowAddingLanguages,
                        onCheckedChange = onAllowAddingLanguageChanged
                    ),
                    SettingItem.Switch(
                        key = "settings_detail_allow_disable_dolby",
                        title = stringResource(R.string.settings_allow_disable_dolby),
                        summary = stringResource(R.string.settings_allow_disable_dolby_summary),
                        checked = state.allowDisableDolby,
                        onCheckedChange = onAllowDisableDolbyChanged
                    ),
                    SettingItem.Switch(
                        key = "settings_detail_app_details_completion",
                        title = stringResource(R.string.settings_app_details_completion),
                        summary = stringResource(R.string.settings_app_details_completion_summary),
                        checked = state.appDetail,
                        onCheckedChange = onAppDetailsChanged
                    ),
                    SettingItem.Switch(
                        key = "settings_detail_app_icon_unmask",
                        title = stringResource(R.string.settings_app_icon_unmask_title),
                        summary = stringResource(R.string.settings_app_icon_unmask_summary),
                        checked = state.appIconUnmask,
                        onCheckedChange = onAppIconUnmaskChanged
                    ),
                )
            )
        )

    }
}

@Composable
private fun settingsDetailActionItem(
    title: String,
    summary: String,
    onClick: () -> Unit,
    icon: (@Composable () -> Unit)? = null,
    key: String
): SettingItem {
    return SettingItem.Action(
        key = key,
        title = title,
        summary = summary,
        onClick = onClick,
        trailingContent = {
            if (icon != null) {
                icon()
            } else {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                    contentDescription = null,
                    tint = LocalZToolColorScheme.current.onSurfaceVariant
                )
            }
        }
    )
}

@Composable
private fun FontInputDialogContent(
    originalDescription: String,
    onConfirm: (String, String) -> Unit,
    onCancel: () -> Unit
) {
    var fontName by remember { mutableStateOf("") }
    var fontDescription by remember { mutableStateOf(originalDescription) }

    ZToolDialog(
        onDismissRequest = onCancel,
        title = {
            Column {
                Text(
                    text = stringResource(R.string.settings_input_font_info_title),
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = stringResource(R.string.settings_do_not_use_duplicated_font_name),
                    style = MaterialTheme.typography.bodySmall,
                    color = LocalZToolColorScheme.current.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                ZToolTextInputRow(
                    label = stringResource(R.string.settings_font_name),
                    value = fontName,
                    onValueChange = { fontName = it },
                    singleLine = true,
                    horizontalPadding = 0.dp
                )
                ZToolTextInputRow(
                    label = stringResource(R.string.settings_font_description),
                    value = fontDescription,
                    onValueChange = { fontDescription = it },
                    singleLine = false,
                    horizontalPadding = 0.dp
                )
            }
        },
        confirmButton = {
            ZToolTextButton(
                onClick = {
                    onConfirm(fontName.trim(), fontDescription.trim())
                },
                text = stringResource(R.string.settings_confirm_button)
            )
        },
        dismissButton = {
            ZToolTextButton(
                onClick = onCancel,
                text = stringResource(R.string.common_cancel),
                isPrimary = false
            )
        }
    )
}
