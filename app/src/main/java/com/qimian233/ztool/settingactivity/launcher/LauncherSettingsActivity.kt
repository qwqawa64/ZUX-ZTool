package com.qimian233.ztool.settingactivity.launcher

import android.content.Context
import android.content.pm.ApplicationInfo
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.qimian233.ztool.R
import com.qimian233.ztool.hook.modules.SharedPreferencesTool.ModulePreferencesUtils
import com.qimian233.ztool.ui.components.ZToolSwitchRow
import com.qimian233.ztool.ui.theme.ZToolTheme
import com.qimian233.ztool.utils.AppChooserDialog

class LauncherSettingsActivity : ComponentActivity() {

    private var appPackageName: String? = null
    private lateinit var prefsUtils: ModulePreferencesUtils

    private var forceStopMode by mutableStateOf(ForceStopMode.Default)
    private var forceStopWhitelist by mutableStateOf<List<String>>(emptyList())
    private var moreBigDock by mutableStateOf(false)
    private var customGridSize by mutableStateOf(false)
    private var customGridRow by mutableStateOf("")
    private var customGridColumn by mutableStateOf("")
    private var showRestartConfirmDialog by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val appName = intent.getStringExtra("app_name").orEmpty()
        appPackageName = intent.getStringExtra("app_package")
        prefsUtils = ModulePreferencesUtils(this)
        loadSettings()

        setContent {
            ZToolTheme {
                LauncherSettingsScreen(
                    title = appName + stringResource(R.string.launcher_settings_title_suffix),
                    forceStopMode = forceStopMode,
                    forceStopWhitelistCount = forceStopWhitelist.size,
                    moreBigDock = moreBigDock,
                    customGridSize = customGridSize,
                    customGridRow = customGridRow,
                    customGridColumn = customGridColumn,
                    onBack = ::finish,
                    onRestart = { showRestartConfirmDialog = true },
                    onForceStopModeChanged = ::handleForceStopModeChanged,
                    onSelectForceStopWhitelist = ::selectForceStopWhitelist,
                    onMoreBigDockChanged = {
                        moreBigDock = it
                        saveSettings("zui_launcher_hotseat", it)
                    },
                    onCustomGridSizeChanged = {
                        customGridSize = it
                        saveSettings("CustomGridSize", it)
                    },
                    onCustomGridRowChanged = {
                        customGridRow = it.filter(Char::isDigit)
                        saveGridValues()
                    },
                    onCustomGridColumnChanged = {
                        customGridColumn = it.filter(Char::isDigit)
                        saveGridValues()
                    }
                )

                if (showRestartConfirmDialog) {
                    RestartConfirmDialog(
                        packageName = appPackageName.orEmpty(),
                        onConfirm = {
                            showRestartConfirmDialog = false
                            forceStopApp()
                        },
                        onDismiss = { showRestartConfirmDialog = false }
                    )
                }
            }
        }
    }

    private fun loadSettings() {
        forceStopWhitelist = loadForceStopWhitelist()
        val disableForceStop = prefsUtils.loadBooleanSetting("disable_force_stop", false)
        val whitelistEnabled = prefsUtils.loadBooleanSetting("ForceStopWhiteListEnable", false)
        forceStopMode = when {
            disableForceStop && whitelistEnabled -> ForceStopMode.Whitelist
            disableForceStop -> ForceStopMode.AllApps
            else -> ForceStopMode.Default
        }

        moreBigDock = prefsUtils.loadBooleanSetting("zui_launcher_hotseat", false)
        customGridSize = prefsUtils.loadBooleanSetting("CustomGridSize", false)
        customGridRow = prefsUtils.loadIntegerSetting("CustomLauncherRow", 4).toString()
        customGridColumn = prefsUtils.loadIntegerSetting("CustomLauncherColumn", 6).toString()
    }

    private fun loadForceStopWhitelist(): List<String> {
        return prefsUtils.loadStringSetting("ForceStopWhiteList", "")
            .split(",")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
    }

    private fun handleForceStopModeChanged(mode: ForceStopMode) {
        forceStopMode = mode
        when (mode) {
            ForceStopMode.Default -> {
                saveSettings("disable_force_stop", false)
                saveSettings("ForceStopWhiteListEnable", false)
            }
            ForceStopMode.AllApps -> {
                saveSettings("disable_force_stop", true)
                saveSettings("ForceStopWhiteListEnable", false)
            }
            ForceStopMode.Whitelist -> {
                saveSettings("disable_force_stop", true)
                saveSettings("ForceStopWhiteListEnable", true)
            }
        }
    }

    private fun selectForceStopWhitelist() {
        AppChooserDialog.show(
            this,
            getUserInstalledPackageNames(this),
            forceStopWhitelist,
            getString(R.string.force_stop_title),
            object : AppChooserDialog.AppSelectionCallback {
                override fun onSelected(selectedApps: List<AppChooserDialog.AppInfo>) {
                    val selectedPackageNames = selectedApps.map { it.packageName }
                    selectedPackageNames.forEach {
                        Log.d(TAG, "Selected protected app package: $it")
                    }
                    forceStopWhitelist = selectedPackageNames
                    prefsUtils.saveStringSetting(
                        "ForceStopWhiteList",
                        selectedPackageNames.joinToString(separator = ",", postfix = ",")
                    )
                }

                override fun onCancel() = Unit
            }
        )
    }

    private fun saveGridValues() {
        val row = customGridRow.toIntOrNull()
        val column = customGridColumn.toIntOrNull()
        if (row != null && column != null) {
            prefsUtils.saveIntegerSetting("CustomLauncherRow", row)
            prefsUtils.saveIntegerSetting("CustomLauncherColumn", column)
        }
    }

    private fun forceStopApp() {
        val packageName = appPackageName
        if (packageName.isNullOrEmpty()) {
            return
        }

        try {
            val process = Runtime.getRuntime().exec("su -c killall $packageName")
            process.waitFor()
            Toast.makeText(this, R.string.force_stop_success, Toast.LENGTH_SHORT).show()
        } catch (_: Exception) {
            Toast.makeText(this, R.string.force_stop_fail, Toast.LENGTH_SHORT).show()
        }
    }

    private fun saveSettings(moduleName: String, newValue: Boolean) {
        prefsUtils.saveBooleanSetting(moduleName, newValue)
    }

    companion object {
        private const val TAG = "LauncherSettings"

        fun getUserInstalledPackageNames(context: Context): List<String> {
            val packageManager = context.packageManager
            return packageManager.getInstalledPackages(0)
                .filter { packageInfo ->
                    val appInfo = packageInfo.applicationInfo ?: return@filter false
                    val isSystemApp = appInfo.flags and ApplicationInfo.FLAG_SYSTEM != 0
                    val isUpdatedSystemApp =
                        appInfo.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP != 0
                    !isSystemApp || isUpdatedSystemApp
                }
                .map { it.packageName }
        }
    }
}

private enum class ForceStopMode {
    Default,
    AllApps,
    Whitelist
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LauncherSettingsScreen(
    title: String,
    forceStopMode: ForceStopMode,
    forceStopWhitelistCount: Int,
    moreBigDock: Boolean,
    customGridSize: Boolean,
    customGridRow: String,
    customGridColumn: String,
    onBack: () -> Unit,
    onRestart: () -> Unit,
    onForceStopModeChanged: (ForceStopMode) -> Unit,
    onSelectForceStopWhitelist: () -> Unit,
    onMoreBigDockChanged: (Boolean) -> Unit,
    onCustomGridSizeChanged: (Boolean) -> Unit,
    onCustomGridRowChanged: (String) -> Unit,
    onCustomGridColumnChanged: (String) -> Unit
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
                SettingsCard(title = stringResource(R.string.disable_force_stop_title)) {
                    ForceStopModeRow(
                        selectedMode = forceStopMode,
                        onModeChanged = onForceStopModeChanged
                    )
                    if (forceStopMode == ForceStopMode.Whitelist) {
                        WhitelistRow(
                            whitelistCount = forceStopWhitelistCount,
                            onClick = onSelectForceStopWhitelist
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                SettingsCard(title = stringResource(R.string.dock_Title)) {
                    ZToolSwitchRow(
                        title = stringResource(R.string.moreBig_dockTitle),
                        summary = stringResource(R.string.moreBig_dockSummary),
                        checked = moreBigDock,
                        onCheckedChange = onMoreBigDockChanged
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                SettingsCard(title = stringResource(R.string.customLauncherLayoutTitle)) {
                    ZToolSwitchRow(
                        title = stringResource(R.string.customGridTitle),
                        summary = stringResource(R.string.customGridSummary),
                        checked = customGridSize,
                        onCheckedChange = onCustomGridSizeChanged
                    )
                    if (customGridSize) {
                        Text(
                            text = stringResource(R.string.customGridInputZoneTitle),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
                        )
                        GridInputRow(
                            row = customGridRow,
                            column = customGridColumn,
                            onRowChanged = onCustomGridRowChanged,
                            onColumnChanged = onCustomGridColumnChanged
                        )
                    }
                }

                Spacer(modifier = Modifier.height(96.dp))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ForceStopModeRow(
    selectedMode: ForceStopMode,
    onModeChanged: (ForceStopMode) -> Unit
) {
    var expanded by mutableStateOf(false)
    val options = listOf(
        ForceStopMode.Default to stringResource(R.string.SelectDefault),
        ForceStopMode.AllApps to stringResource(R.string.SelectAllAPP),
        ForceStopMode.Whitelist to stringResource(R.string.SelectWhiteList)
    )
    val selectedLabel = options.first { it.first == selectedMode }.second

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.disable_force_stop_enable_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = stringResource(R.string.disable_force_stop_enable_summary),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
        Spacer(modifier = Modifier.width(16.dp))
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = !expanded },
            modifier = Modifier.widthIn(min = 132.dp, max = 180.dp)
        ) {
            OutlinedTextField(
                value = selectedLabel,
                onValueChange = {},
                readOnly = true,
                singleLine = true,
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                modifier = Modifier.menuAnchor()
            )
            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                options.forEach { (mode, label) ->
                    DropdownMenuItem(
                        text = { Text(label) },
                        onClick = {
                            expanded = false
                            onModeChanged(mode)
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun WhitelistRow(
    whitelistCount: Int,
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
            text = stringResource(R.string.protected_apps_summary, whitelistCount),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
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
private fun GridInputRow(
    row: String,
    column: String,
    onRowChanged: (String) -> Unit,
    onColumnChanged: (String) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        OutlinedTextField(
            value = row,
            onValueChange = onRowChanged,
            label = { Text(stringResource(R.string.inputRowNumberHere)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.width(128.dp)
        )
        Text(
            text = stringResource(R.string.multiply),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
        OutlinedTextField(
            value = column,
            onValueChange = onColumnChanged,
            label = { Text(stringResource(R.string.inputColumnNumberHere)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.width(128.dp)
        )
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
private fun RestartConfirmDialog(
    packageName: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.restart_xp_title)) },
        text = {
            Text(
                stringResource(R.string.restart_xp_message_header) +
                    packageName +
                    stringResource(R.string.restart_xp_message)
            )
        },
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
