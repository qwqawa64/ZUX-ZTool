package com.qimian233.ztool

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.util.Log
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.fragment.app.Fragment
import com.qimian233.ztool.hook.modules.SharedPreferencesTool.ModulePreferencesUtils
import com.qimian233.ztool.service.LogServiceManager
import com.qimian233.ztool.ui.theme.ZToolTheme
import com.qimian233.ztool.utils.FileManager

class SettingsFragment : Fragment() {

    private var isLogServiceEnabled by mutableStateOf(false)
    private var isDetailedLoggingEnabled by mutableStateOf(false)
    private var isHomepageYiyanEnabled by mutableStateOf(true)
    private var showRestoreConfirmDialog by mutableStateOf(false)
    private var showAboutDialog by mutableStateOf(false)

    private val backupLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            val result = FileManager.saveConfigWithSAF(
                requireContext(),
                uri,
                FileManager.generateBackupFileName(),
                ModulePreferencesUtils.getAllSettingsAsJSON(requireContext())
            )
            if (result) {
                Log.d(TAG, "Saved config backup to user-selected uri: $uri")
                showToast(getString(R.string.config_backup_success))
            } else {
                Log.e(TAG, "Config backup failed")
            }
        }
    }

    private val restoreLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            val content = FileManager.readConfigWithSAF(requireContext(), uri)
            if (content != null) {
                Log.d(TAG, "Read config content: $content")
                ModulePreferencesUtils.restoreConfig(requireContext(), content)
                showToast(getString(R.string.config_restore_success))
                loadSwitchStates()
            } else {
                Log.e(TAG, "Config file read failed or content was empty")
            }
        }
    }

    override fun onCreateView(
        inflater: android.view.LayoutInflater,
        container: android.view.ViewGroup?,
        savedInstanceState: Bundle?
    ): android.view.View {
        loadSwitchStates()
        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                ZToolTheme {
                    SettingsRoute(
                        isLogServiceEnabled = isLogServiceEnabled,
                        isDetailedLoggingEnabled = isDetailedLoggingEnabled,
                        isHomepageYiyanEnabled = isHomepageYiyanEnabled,
                        onBackup = ::performBackup,
                        onRestore = { restoreLauncher.launch(arrayOf("application/json")) },
                        onRestoreDefault = { showRestoreConfirmDialog = true },
                        onLogServiceChanged = ::handleLogServiceSwitch,
                        onDetailedLoggingChanged = ::handleDetailedLoggingSwitch,
                        onHomepageYiyanChanged = ::handleHomepageYiyanSwitch,
                        onAbout = { showAboutDialog = true }
                    )

                    if (showRestoreConfirmDialog) {
                        RestoreDefaultDialog(
                            onConfirm = {
                                showRestoreConfirmDialog = false
                                performRestore()
                            },
                            onDismiss = { showRestoreConfirmDialog = false }
                        )
                    }

                    if (showAboutDialog) {
                        AboutDialog(
                            version = updateModuleStatus(),
                            onDismiss = { showAboutDialog = false },
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
        loadSwitchStates()
    }

    private fun loadSwitchStates() {
        if (context == null) return
        val prefs = ModulePreferencesUtils(requireContext())
        isLogServiceEnabled = LogServiceManager.isServiceEnabled(requireContext())
        isDetailedLoggingEnabled = prefs.loadBooleanSetting("isDetailedLogging", false)
        isHomepageYiyanEnabled = prefs.loadBooleanSetting("enable_homepage_yiyan", true)
    }

    private fun performBackup() {
        backupLauncher.launch(FileManager.generateBackupFileName())
    }

    private fun performRestore() {
        ModulePreferencesUtils(requireContext()).clearAllSettings()
        loadSwitchStates()
        showToast(getString(R.string.default_config_restored))
    }

    private fun handleLogServiceSwitch(isEnabled: Boolean) {
        isLogServiceEnabled = isEnabled
        if (isEnabled) {
            LogServiceManager.startLogService(requireContext())
            showToast(getString(R.string.log_service_started))
        } else {
            LogServiceManager.stopLogService(requireContext())
            showToast(getString(R.string.log_service_stopped))
        }
    }

    private fun handleDetailedLoggingSwitch(isEnabled: Boolean) {
        isDetailedLoggingEnabled = isEnabled
        ModulePreferencesUtils(requireContext()).saveBooleanSetting("isDetailedLogging", isEnabled)
    }

    private fun handleHomepageYiyanSwitch(isEnabled: Boolean) {
        isHomepageYiyanEnabled = isEnabled
        ModulePreferencesUtils(requireContext()).saveBooleanSetting("enable_homepage_yiyan", isEnabled)
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

    private fun updateModuleStatus(): String {
        return try {
            val activity: Activity? = activity
            if (activity != null) {
                val packageInfo = activity.packageManager.getPackageInfo(activity.packageName, 0)
                "${packageInfo.versionName} (${packageInfo.versionCode})"
            } else {
                getString(R.string.unknown_activity_null)
            }
        } catch (e: PackageManager.NameNotFoundException) {
            Log.w(TAG, "Unable to get module version: ${e.message}")
            getString(R.string.unknown)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to update module status: ${e.message}")
            getString(R.string.unknown)
        }
    }

    companion object {
        private const val TAG = "SettingsFragment"
    }
}

@Composable
private fun SettingsRoute(
    isLogServiceEnabled: Boolean,
    isDetailedLoggingEnabled: Boolean,
    isHomepageYiyanEnabled: Boolean,
    onBackup: () -> Unit,
    onRestore: () -> Unit,
    onRestoreDefault: () -> Unit,
    onLogServiceChanged: (Boolean) -> Unit,
    onDetailedLoggingChanged: (Boolean) -> Unit,
    onHomepageYiyanChanged: (Boolean) -> Unit,
    onAbout: () -> Unit
) {
    Box(
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
                SettingsSwitchRow(
                    title = stringResource(R.string.enableLogService),
                    summary = stringResource(R.string.enableLogServiceDescription),
                    checked = isLogServiceEnabled,
                    onCheckedChange = onLogServiceChanged
                )
                SettingsSwitchRow(
                    title = stringResource(R.string.enableDetailedLogging),
                    summary = stringResource(R.string.enableDetailedLoggingDescription),
                    checked = isDetailedLoggingEnabled,
                    onCheckedChange = onDetailedLoggingChanged
                )
                SettingsSwitchRow(
                    title = stringResource(R.string.enableHomePageYiyan),
                    summary = stringResource(R.string.enableHomePageYiyanSummary),
                    checked = isHomepageYiyanEnabled,
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
private fun SettingsSwitchRow(
    title: String,
    summary: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = summary,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.width(16.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}

@Composable
private fun RestoreDefaultDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
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
    AlertDialog(
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
