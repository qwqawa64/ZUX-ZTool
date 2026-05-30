package com.qimian233.ztool

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.qimian233.ztool.data.settings.SettingsRepository
import com.qimian233.ztool.ui.components.ZToolCard
import com.qimian233.ztool.ui.components.ZToolDialog
import com.qimian233.ztool.ui.components.ZToolPageSurface
import com.qimian233.ztool.ui.components.ZToolSwitchRow
import com.qimian233.ztool.ui.theme.ZToolTheme
import com.qimian233.ztool.viewmodel.SettingsUiState
import com.qimian233.ztool.viewmodel.SettingsViewModel

class SettingsFragment : Fragment() {

    private lateinit var viewModel: SettingsViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val repository = SettingsRepository(requireContext().applicationContext)
        viewModel = ViewModelProvider(
            this,
            SettingsViewModelFactory(repository)
        )[SettingsViewModel::class.java]
    }

    private val backupLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            viewModel.backupConfig(uri) { result ->
                activity?.runOnUiThread {
                    if (result) {
                        showToast(getString(R.string.config_backup_success))
                    }
                }
            }
        }
    }

    private val restoreLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            viewModel.restoreConfig(uri) { result ->
                activity?.runOnUiThread {
                    if (result) {
                        showToast(getString(R.string.config_restore_success))
                    }
                }
            }
        }
    }

    override fun onCreateView(
        inflater: android.view.LayoutInflater,
        container: android.view.ViewGroup?,
        savedInstanceState: Bundle?
    ): android.view.View {
        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                val uiState by viewModel.uiState.collectAsState()

                ZToolTheme {
                    SettingsRoute(
                        state = uiState,
                        onBackup = { backupLauncher.launch(viewModel.backupFileName()) },
                        onRestore = { restoreLauncher.launch(arrayOf("application/json")) },
                        onRestoreDefault = viewModel::showRestoreConfirmDialog,
                        onLogServiceChanged = {
                            viewModel.setLogServiceEnabled(it)
                            showToast(
                                getString(
                                    if (it) R.string.log_service_started else R.string.log_service_stopped
                                )
                            )
                        },
                        onDetailedLoggingChanged = viewModel::setDetailedLoggingEnabled,
                        onHomepageYiyanChanged = viewModel::setHomepageYiyanEnabled,
                        onAbout = viewModel::showAboutDialog
                    )

                    if (uiState.showRestoreConfirmDialog) {
                        RestoreDefaultDialog(
                            onConfirm = {
                                viewModel.restoreDefaultConfig()
                                showToast(getString(R.string.default_config_restored))
                            },
                            onDismiss = viewModel::dismissRestoreConfirmDialog
                        )
                    }

                    if (uiState.showAboutDialog) {
                        AboutDialog(
                            version = uiState.moduleVersion,
                            onDismiss = viewModel::dismissAboutDialog,
                            onOpenGithub = {
                                openExternalLink("https://github.com/qwqawa64/ZUX-ZTool", false, "")
                            },
                            onOpenCredits = {
                                openExternalLink("https://github.com/dantmnf/UnfuckZUI", false, "")
                            },
                            onOpenAuthor = {
                                openExternalLink("http://www.coolapk.com/u/10099756", true, "com.coolapk.market")
                            },
                            onOpenCollaborator = {
                                openExternalLink("http://www.coolapk.com/u/18634835", true, "com.coolapk.market")
                            }
                        )
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.refresh()
    }

    private fun openExternalLink(link: String, shouldDeterminePackage: Boolean, packageName: String) {
        try {
            startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse(link)).apply {
                    if (shouldDeterminePackage) setPackage(packageName)
                }
            )
        } catch (_: Exception) {
            showToast(getString(R.string.open_web_link_failed))
        }
    }

    private fun showToast(message: String) {
        if (context != null) {
            Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
        }
    }

}

private class SettingsViewModelFactory(
    private val repository: SettingsRepository
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(SettingsViewModel::class.java)) {
            return SettingsViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}

@Composable
private fun SettingsRoute(
    state: SettingsUiState,
    onBackup: () -> Unit,
    onRestore: () -> Unit,
    onRestoreDefault: () -> Unit,
    onLogServiceChanged: (Boolean) -> Unit,
    onDetailedLoggingChanged: (Boolean) -> Unit,
    onHomepageYiyanChanged: (Boolean) -> Unit,
    onAbout: () -> Unit
) {
    ZToolPageSurface(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 960.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 32.dp, vertical = 32.dp)
        ) {
            Text(
                text = stringResource(R.string.settingsFragment_title),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(24.dp))

            SettingsSection(title = stringResource(R.string.backupAndRestore)) {
                SettingsActionRow(
                    title = stringResource(R.string.backupConfigToFile),
                    onClick = onBackup
                )
                SettingsActionRow(
                    title = stringResource(R.string.restoreConfigFromFile),
                    onClick = onRestore
                )
                SettingsActionRow(
                    title = stringResource(R.string.restoreDefaultConfig),
                    onClick = onRestoreDefault
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            SettingsSection(title = stringResource(R.string.moreSettings)) {
                ZToolSwitchRow(
                    title = stringResource(R.string.enableLogService),
                    summary = stringResource(R.string.enableLogServiceDescription),
                    checked = state.isLogServiceEnabled,
                    onCheckedChange = onLogServiceChanged
                )
                ZToolSwitchRow(
                    title = stringResource(R.string.enableDetailedLogging),
                    summary = stringResource(R.string.enableDetailedLoggingDescription),
                    checked = state.isDetailedLoggingEnabled,
                    onCheckedChange = onDetailedLoggingChanged
                )
                ZToolSwitchRow(
                    title = stringResource(R.string.enableHomePageYiyan),
                    summary = stringResource(R.string.enableHomePageYiyanSummary),
                    checked = state.isHomepageYiyanEnabled,
                    onCheckedChange = onHomepageYiyanChanged
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            SettingsSection {
                SettingsActionRow(
                    title = stringResource(R.string.showAboutPage),
                    onClick = onAbout
                )
            }
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun SettingsSection(
    title: String? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    ZToolCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp)
        ) {
            if (title != null) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                )
            }
            content()
        }
    }
}

@Composable
private fun SettingsActionRow(
    title: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Icon(
            imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun RestoreDefaultDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    ZToolDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.final_confirmation_title)) },
        text = { Text(stringResource(R.string.restore_default_confirmation)) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.restart_yes))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.restart_no))
            }
        }
    )
}

@Composable
private fun AboutDialog(
    version: String,
    onDismiss: () -> Unit,
    onOpenGithub: () -> Unit,
    onOpenCredits: () -> Unit,
    onOpenAuthor: () -> Unit,
    onOpenCollaborator: () -> Unit
) {
    ZToolDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.about_ztool_title)) },
        text = {
            Column {
                Text(
                    text = version,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = stringResource(R.string.about_description)
                        .replace("<br>", "\n")
                        .replace("&lt;br&gt;", "\n"),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(android.R.string.ok))
            }
        },
        dismissButton = {
            Row {
                TextButton(
                    contentPadding = PaddingValues(horizontal = 8.dp),
                    onClick = onOpenGithub
                ) {
                    Text(stringResource(R.string.button_project_homepage))
                }
                TextButton(
                    contentPadding = PaddingValues(horizontal = 8.dp),
                    onClick = onOpenCredits
                ) {
                    Text("Credits")
                }
                TextButton(
                    contentPadding = PaddingValues(horizontal = 8.dp),
                    onClick = onOpenAuthor
                ) {
                    Text("Qimian233")
                }
                TextButton(
                    contentPadding = PaddingValues(horizontal = 8.dp),
                    onClick = onOpenCollaborator
                ) {
                    Text("WASD")
                }
            }
        }
    )
}
