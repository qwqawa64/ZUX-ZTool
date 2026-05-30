package com.qimian233.ztool.settingactivity.systemframework

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.qimian233.ztool.R
import com.qimian233.ztool.data.systemframework.FrameworkSettingsRepository
import com.qimian233.ztool.ui.components.ZToolSettingsDivider
import com.qimian233.ztool.ui.components.ZToolSwitchRow
import com.qimian233.ztool.ui.theme.ZToolTheme
import com.qimian233.ztool.viewmodel.FrameworkSettingsUiState
import com.qimian233.ztool.viewmodel.FrameworkSettingsViewModel
import kotlinx.coroutines.delay

class FrameworkSettingsActivity : ComponentActivity() {

    private lateinit var viewModel: FrameworkSettingsViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val appName = intent.getStringExtra("app_name").orEmpty()
        val repository = FrameworkSettingsRepository(applicationContext)
        viewModel = ViewModelProvider(
            this,
            FrameworkSettingsViewModelFactory(repository)
        )[FrameworkSettingsViewModel::class.java]
        viewModel.loadSettings()

        setContent {
            val uiState by viewModel.uiState.collectAsState()

            ZToolTheme {
                FrameworkSettingsScreen(
                    title = appName + stringResource(R.string.framework_settings_title_suffix),
                    state = uiState,
                    onBack = ::finish,
                    onRestart = viewModel::showRestartConfirmDialog,
                    onKeepRotationChanged = viewModel::setKeepRotation,
                    onAllowGetPackagesChanged = viewModel::setAllowGetPackages,
                    onDisableFlagSecureChanged = viewModel::setDisableFlagSecure,
                    onAiInputExpandChanged = viewModel::setAiInputExpand,
                    onAiInputSignsChanged = viewModel::setAiInputSigns,
                    onShowAiInputInfo = viewModel::showAiInputInfoDialog
                )

                if (uiState.showAiInputInfoDialog) {
                    AiInputInfoDialog(
                        onDismiss = viewModel::dismissAiInputInfoDialog
                    )
                }

                if (uiState.showRestartConfirmDialog) {
                    RestartSystemDialog(
                        onConfirm = {
                            viewModel.restartSystem(::showRestartFailure)
                        },
                        onDismiss = viewModel::dismissRestartConfirmDialog
                    )
                }
            }
        }
    }

    private fun showRestartFailure(error: String) {
        runOnUiThread {
            Toast.makeText(
                this,
                getString(R.string.restart_fail_prefix) + error,
                Toast.LENGTH_SHORT
            ).show()
        }
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
                SettingsCard(title = stringResource(R.string.keep_rotation_title)) {
                    ZToolSwitchRow(
                        title = stringResource(R.string.keep_rotation_enable_title),
                        summary = stringResource(R.string.keep_rotation_enable_summary),
                        checked = state.keepRotation,
                        onCheckedChange = onKeepRotationChanged
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                SettingsCard(title = stringResource(R.string.disable_zui_applist_title)) {
                    ZToolSwitchRow(
                        title = stringResource(R.string.disable_zui_applist_enable_title),
                        summary = stringResource(R.string.disable_zui_applist_enable_summary),
                        checked = state.allowGetPackages,
                        onCheckedChange = onAllowGetPackagesChanged
                    )
                    ZToolSettingsDivider()
                    ZToolSwitchRow(
                        title = stringResource(R.string.disable_flag_secure_title),
                        summary = stringResource(R.string.disable_flag_secure_summary),
                        checked = state.disableFlagSecure,
                        onCheckedChange = onDisableFlagSecureChanged
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                SettingsCard(title = stringResource(R.string.ai_input_Title)) {
                    ZToolSwitchRow(
                        title = stringResource(R.string.ai_input_expand_Title),
                        summary = stringResource(R.string.ai_input_expand_summary),
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
                            label = { Text(stringResource(R.string.custom_detector_hint)) },
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
        title = { Text(stringResource(R.string.Custom_attention)) },
        text = {
            Column {
                Text(
                    text = stringResource(R.string.Custom_Attention_content),
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedTextField(
                    value = testInput,
                    onValueChange = { testInput = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.test_Input)) },
                    minLines = 5,
                    maxLines = 10,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text)
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(android.R.string.ok))
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
        title = { Text(stringResource(R.string.restart_system_title)) },
        text = { Text(stringResource(R.string.restart_system_message)) },
        confirmButton = {
            TextButton(
                enabled = countdown == 0,
                onClick = onConfirm
            ) {
                Text(
                    if (countdown > 0) {
                        stringResource(R.string.confirm) + " ($countdown)"
                    } else {
                        stringResource(R.string.confirm)
                    }
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.restart_no))
            }
        }
    )
}
