package com.qimian233.ztool.settingactivity.systemframework

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.qimian233.ztool.R
import com.qimian233.ztool.hook.modules.SharedPreferencesTool.ModulePreferencesUtils
import com.qimian233.ztool.ui.components.ZToolSettingsDivider
import com.qimian233.ztool.ui.components.ZToolSwitchRow
import com.qimian233.ztool.ui.theme.ZToolTheme
import kotlinx.coroutines.delay

class FrameworkSettingsActivity : ComponentActivity() {

    private lateinit var prefsUtils: ModulePreferencesUtils

    private var uiState by mutableStateOf(FrameworkSettingsUiState())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val appName = intent.getStringExtra("app_name").orEmpty()
        prefsUtils = ModulePreferencesUtils(this)
        loadSettings()

        setContent {
            ZToolTheme {
                FrameworkSettingsScreen(
                    title = appName + getString(R.string.framework_settings_title_suffix),
                    state = uiState,
                    onBack = ::finish,
                    onRestart = { uiState = uiState.copy(showRestartConfirmDialog = true) },
                    onKeepRotationChanged = {
                        uiState = uiState.copy(keepRotation = it)
                        saveSettings("keep_rotation", it)
                    },
                    onAllowGetPackagesChanged = {
                        uiState = uiState.copy(allowGetPackages = it)
                        saveSettings("allow_get_packages", it)
                    },
                    onDisableFlagSecureChanged = {
                        uiState = uiState.copy(disableFlagSecure = it)
                        saveSettings("disable_flag_secure", it)
                    },
                    onAiInputExpandChanged = {
                        uiState = uiState.copy(
                            aiInputExpand = it,
                            aiInputSignsError = if (it) uiState.aiInputSignsError else null
                        )
                        saveSettings("ai_input_expand", it)
                    },
                    onAiInputSignsChanged = ::handleAiInputSignsChanged,
                    onShowAiInputInfo = { uiState = uiState.copy(showAiInputInfoDialog = true) }
                )

                if (uiState.showAiInputInfoDialog) {
                    AiInputInfoDialog(
                        onDismiss = { uiState = uiState.copy(showAiInputInfoDialog = false) }
                    )
                }

                if (uiState.showRestartConfirmDialog) {
                    RestartSystemDialog(
                        onConfirm = {
                            uiState = uiState.copy(showRestartConfirmDialog = false)
                            restartOS()
                        },
                        onDismiss = { uiState = uiState.copy(showRestartConfirmDialog = false) }
                    )
                }
            }
        }
    }

    private fun loadSettings() {
        val aiInputSigns = prefsUtils.loadStringSetting("AI_INPUT_EXPAND_SIGNS", "")
        uiState = uiState.copy(
            allowGetPackages = prefsUtils.loadBooleanSetting("allow_get_packages", false),
            keepRotation = prefsUtils.loadBooleanSetting("keep_rotation", false),
            disableFlagSecure = prefsUtils.loadBooleanSetting("disable_flag_secure", false),
            aiInputExpand = prefsUtils.loadBooleanSetting("ai_input_expand", false),
            aiInputSigns = aiInputSigns,
            aiInputSignsError = validateAiInputSigns(aiInputSigns)
        )
    }

    private fun saveSettings(moduleName: String, newValue: Boolean) {
        prefsUtils.saveBooleanSetting(moduleName, newValue)
    }

    private fun handleAiInputSignsChanged(value: String) {
        val input = value.trim()
        val error = validateAiInputSigns(input)
        uiState = uiState.copy(
            aiInputSigns = value,
            aiInputSignsError = error
        )

        if (input.isEmpty()) {
            prefsUtils.saveStringSetting("AI_INPUT_EXPAND_SIGNS", "")
            return
        }

        if (error == null) {
            prefsUtils.saveStringSetting("AI_INPUT_EXPAND_SIGNS", input)
        }
    }

    private fun validateAiInputSigns(input: String): String? {
        if (input.isEmpty()) return null
        if (input.contains("，")) return getString(R.string.custom_detector_err)
        return if (input.split(",").any { it.trim().isEmpty() }) {
            getString(R.string.custom_detector_err)
        } else {
            null
        }
    }

    private fun restartOS() {
        try {
            val process = Runtime.getRuntime().exec("su -c reboot")
            process.waitFor()
        } catch (e: Exception) {
            Toast.makeText(
                this,
                getString(R.string.restart_fail_prefix) + e.message,
                Toast.LENGTH_SHORT
            ).show()
        }
    }
}

private data class FrameworkSettingsUiState(
    val keepRotation: Boolean = false,
    val allowGetPackages: Boolean = false,
    val disableFlagSecure: Boolean = false,
    val aiInputExpand: Boolean = false,
    val aiInputSigns: String = "",
    val aiInputSignsError: String? = null,
    val showAiInputInfoDialog: Boolean = false,
    val showRestartConfirmDialog: Boolean = false
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FrameworkSettingsScreen(
    title: String,
    state: FrameworkSettingsUiState,
    onBack: () -> Unit,
    onRestart: () -> Unit,
    onKeepRotationChanged: (Boolean) -> Unit,
    onAllowGetPackagesChanged: (Boolean) -> Unit,
    onDisableFlagSecureChanged: (Boolean) -> Unit,
    onAiInputExpandChanged: (Boolean) -> Unit,
    onAiInputSignsChanged: (String) -> Unit,
    onShowAiInputInfo: () -> Unit
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
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = null
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onRestart) {
                Icon(
                    imageVector = Icons.Rounded.Refresh,
                    contentDescription = null
                )
            }
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
            ) {
                SettingsCard(title = getStringResource(R.string.keep_rotation_title)) {
                    ZToolSwitchRow(
                        title = getStringResource(R.string.keep_rotation_enable_title),
                        summary = getStringResource(R.string.keep_rotation_enable_summary),
                        checked = state.keepRotation,
                        onCheckedChange = onKeepRotationChanged
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                SettingsCard(title = getStringResource(R.string.disable_zui_applist_title)) {
                    ZToolSwitchRow(
                        title = getStringResource(R.string.disable_zui_applist_enable_title),
                        summary = getStringResource(R.string.disable_zui_applist_enable_summary),
                        checked = state.allowGetPackages,
                        onCheckedChange = onAllowGetPackagesChanged
                    )
                    ZToolSettingsDivider()
                    ZToolSwitchRow(
                        title = getStringResource(R.string.disable_flag_secure_title),
                        summary = getStringResource(R.string.disable_flag_secure_summary),
                        checked = state.disableFlagSecure,
                        onCheckedChange = onDisableFlagSecureChanged
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                SettingsCard(title = getStringResource(R.string.ai_input_Title)) {
                    ZToolSwitchRow(
                        title = getStringResource(R.string.ai_input_expand_Title),
                        summary = getStringResource(R.string.ai_input_expand_summary),
                        checked = state.aiInputExpand,
                        onCheckedChange = onAiInputExpandChanged
                    )
                    IconButton(
                        onClick = onShowAiInputInfo,
                        modifier = Modifier.padding(start = 12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (state.aiInputExpand) {
                        OutlinedTextField(
                            value = state.aiInputSigns,
                            onValueChange = onAiInputSignsChanged,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 24.dp, vertical = 8.dp),
                            label = { Text(getStringResource(R.string.custom_detector_hint)) },
                            isError = state.aiInputSignsError != null,
                            supportingText = {
                                if (state.aiInputSignsError != null) {
                                    Text(state.aiInputSignsError)
                                }
                            },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                            minLines = 1,
                            maxLines = 3
                        )
                    }
                }

                Spacer(modifier = Modifier.height(96.dp))
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
private fun AiInputInfoDialog(
    onDismiss: () -> Unit
) {
    var testInput by mutableStateOf("")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(getStringResource(R.string.Custom_attention)) },
        text = {
            Column {
                Text(
                    text = getStringResource(R.string.Custom_Attention_content),
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedTextField(
                    value = testInput,
                    onValueChange = { testInput = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(getStringResource(R.string.test_Input)) },
                    minLines = 5,
                    maxLines = 10,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text)
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(getStringResource(android.R.string.ok))
            }
        }
    )
}

@Composable
private fun RestartSystemDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    var countdown by mutableIntStateOf(3)

    LaunchedEffect(Unit) {
        while (countdown > 0) {
            delay(1000)
            countdown -= 1
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(getStringResource(R.string.restart_system_title)) },
        text = { Text(getStringResource(R.string.restart_system_message)) },
        confirmButton = {
            TextButton(
                enabled = countdown == 0,
                onClick = onConfirm
            ) {
                Text(
                    if (countdown > 0) {
                        getStringResource(R.string.confirm) + " ($countdown)"
                    } else {
                        getStringResource(R.string.confirm)
                    }
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(getStringResource(R.string.restart_no))
            }
        }
    )
}

@Composable
private fun getStringResource(id: Int): String {
    return androidx.compose.ui.res.stringResource(id)
}
