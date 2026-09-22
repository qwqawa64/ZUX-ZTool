package com.qimian233.ztool.screens.ztoolsettings.advanced

import android.widget.Toast
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.SettingsEthernet
import androidx.compose.material.icons.rounded.Smartphone
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.qimian233.ztool.R
import com.qimian233.ztool.data.advanced.EngineeringCodeEntry
import com.qimian233.ztool.data.advanced.EngineeringCodeRepository
import com.qimian233.ztool.ui.components.SettingItem
import com.qimian233.ztool.ui.components.SettingSection
import com.qimian233.ztool.ui.components.ZToolPageSurface
import com.qimian233.ztool.ui.components.ZToolScaffold
import com.qimian233.ztool.ui.components.ZToolSettingsList
import com.qimian233.ztool.ui.components.ZToolTopAppBar

/**
 * Sub-screen of the Advanced options page: opens Lenovo engineering screens
 * (secret-code dialogs) directly via root. Destructive codes (factory reset,
 * NVRAM country code) are intentionally not listed.
 */
@Composable
fun EngineeringCodesRoute(
    onBack: () -> Unit,
    targetId: String? = null
) {
    val context = LocalContext.current
    val repository = remember { EngineeringCodeRepository() }
    var busyKey by remember { mutableStateOf<String?>(null) }

    val launchingString = stringResource(R.string.engineering_codes_launching)
    val unavailableString = stringResource(R.string.engineering_codes_unavailable)

    ZToolScaffold(
        topBar = {
            ZToolTopAppBar(
                title = stringResource(R.string.engineering_codes_title),
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
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 960.dp)
                    .padding(horizontal = 24.dp, vertical = 24.dp)
            ) {
                item {
                    ZToolSettingsList(
                        sections = listOf(
                            SettingSection(
                                items = repository.getEntries().map { entry ->
                                    engineeringCodeItem(
                                        entry = entry,
                                        busy = busyKey == entry.key,
                                        onClick = {
                                            if (busyKey == null) {
                                                busyKey = entry.key
                                                Toast.makeText(context, launchingString, Toast.LENGTH_SHORT).show()
                                                repository.launchComponent(entry) { success, message ->
                                                    busyKey = null
                                                    when {
                                                        success -> Unit
                                                        message == EngineeringCodeRepository.ERROR_PACKAGE_MISSING ->
                                                            Toast.makeText(context, unavailableString, Toast.LENGTH_LONG).show()
                                                        else ->
                                                            Toast.makeText(
                                                                context,
                                                                context.getString(failedStringRes(), message),
                                                                Toast.LENGTH_LONG
                                                            ).show()
                                                    }
                                                }
                                            }
                                        }
                                    )
                                }
                            )
                        ),
                        bottomPadding = 32.dp
                    )
                }
            }
        }
    }
}

private fun failedStringRes(): Int = R.string.engineering_codes_launch_failed_with_reason

@Composable
private fun engineeringCodeItem(
    entry: EngineeringCodeEntry,
    busy: Boolean,
    onClick: () -> Unit
) = SettingItem.Action(
    key = "engineering_code_${entry.key}",
    title = engineeringCodeTitle(entry.key),
    summary = stringResource(R.string.engineering_codes_dial_prefix, entry.dialCode),
    onClick = onClick,
    enabled = !busy,
    icon = engineeringCodeIcon(entry.key)
)

@Composable
private fun engineeringCodeTitle(key: String): String = when (key) {
    "version_info" -> stringResource(R.string.engineering_codes_item_version_info)
    "sn" -> stringResource(R.string.engineering_codes_item_sn)
    "framework_version" -> stringResource(R.string.engineering_codes_item_framework_version)
    "imei" -> stringResource(R.string.engineering_codes_item_imei)
    "usb_debug" -> stringResource(R.string.engineering_codes_item_usb_debug)
    "offline_log" -> stringResource(R.string.engineering_codes_item_offline_log)
    else -> key
}

private fun engineeringCodeIcon(key: String) = when (key) {
    "version_info" -> Icons.Rounded.Info
    "sn" -> Icons.Rounded.Smartphone
    "framework_version" -> Icons.Rounded.Verified
    "imei" -> Icons.Rounded.SettingsEthernet
    "usb_debug" -> Icons.Rounded.Tune
    "offline_log" -> Icons.Rounded.BugReport
    else -> Icons.Rounded.Info
}
