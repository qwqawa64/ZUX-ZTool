package com.qimian233.ztool.screens.freeform

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.qimian233.ztool.R
import com.qimian233.ztool.ui.components.DIALOG_BUTTON_VERTICAL_ARRANGEMENT
import com.qimian233.ztool.ui.components.ZToolCheckbox
import com.qimian233.ztool.ui.components.ZToolDialog
import com.qimian233.ztool.ui.components.ZToolTextButton
import com.qimian233.ztool.ui.theme.FrontendStyle
import com.qimian233.ztool.ui.theme.LocalZToolColorScheme
import com.qimian233.ztool.ui.theme.LocalZToolThemeSpec
import com.qimian233.ztool.utils.EmbeddingConfigManager

/** One-vision config selection dialog, moved out of the settings-app screen. */
@Composable
internal fun EmbeddingConfigSelectionDialogContent(
    configs: List<EmbeddingConfigManager.ConfigFileInfo>,
    flashedConfigs: Set<String>,
    configLabel: (EmbeddingConfigManager.ConfigFileInfo) -> String,
    onAlreadyFlashedClick: () -> Unit,
    onDelete: (List<EmbeddingConfigManager.ConfigFileInfo>) -> Unit,
    onFlash: (List<EmbeddingConfigManager.ConfigFileInfo>) -> Unit,
    onRestore: () -> Unit,
    onCancel: () -> Unit
) {
    val selectedIndexes = remember { mutableStateListOf<Int>() }

    ZToolDialog(
        buttonArrangement = DIALOG_BUTTON_VERTICAL_ARRANGEMENT,
        onDismissRequest = onCancel,
        title = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = stringResource(R.string.settings_select_config_files),
                    fontWeight = FontWeight.SemiBold
                )
                if (flashedConfigs.isNotEmpty()) {
                    Text(
                        text = stringResource(
                            R.string.settings_flashed_configs_count,
                            flashedConfigs.size
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = LocalZToolColorScheme.current.primary,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
        },
        text = {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 360.dp)
            ) {
                itemsIndexed(configs) { index, config ->
                    val configKey = config.timestamp + "_" + config.packageName
                    val flashed = configKey in flashedConfigs
                    val selected = index in selectedIndexes
                    EmbeddingConfigSelectionRow(
                        label = configLabel(config),
                        flashed = flashed,
                        selected = selected,
                        onClick = {
                            if (flashed) {
                                onAlreadyFlashedClick()
                            } else if (selected) {
                                selectedIndexes -= index
                            } else {
                                selectedIndexes += index
                            }
                        }
                    )
                }
            }
        },
        confirmButton = {
            ZToolTextButton(
                onClick = {
                    val selectedConfigs = selectedIndexes.map { configs[it] }
                    if (selectedConfigs.isNotEmpty()) onFlash(selectedConfigs)
                },
                text = stringResource(R.string.settings_flash_added_config)
            )
        },
        dismissButton = {
            if (LocalZToolThemeSpec.current.style == FrontendStyle.Material3Expressive) {
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    ZToolTextButton(
                        onClick = onCancel,
                        text = stringResource(R.string.common_cancel),
                        isPrimary = false
                    )
                    ZToolTextButton(
                        onClick = {
                            val selectedConfigs = selectedIndexes.map { configs[it] }
                            if (selectedConfigs.isNotEmpty()) onDelete(selectedConfigs)
                        },
                        text = stringResource(R.string.settings_delete),
                        isPrimary = false
                    )
                    if (flashedConfigs.isNotEmpty()) {
                        ZToolTextButton(
                            onClick = onRestore,
                            text = stringResource(R.string.settings_restore_module),
                            isPrimary = false
                        )
                    }
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    ZToolTextButton(
                        onClick = {
                            val selectedConfigs = selectedIndexes.map { configs[it] }
                            if (selectedConfigs.isNotEmpty()) onDelete(selectedConfigs)
                        },
                        text = stringResource(R.string.settings_delete),
                        isPrimary = false
                    )
                    if (flashedConfigs.isNotEmpty()) {
                        ZToolTextButton(
                            onClick = onRestore,
                            text = stringResource(R.string.settings_restore_module),
                            isPrimary = false
                        )
                    }
                    ZToolTextButton(
                        onClick = onCancel,
                        text = stringResource(R.string.common_cancel),
                        isPrimary = false
                    )
                }
            }
        }
    )
}

@Composable
private fun EmbeddingConfigSelectionRow(
    label: String,
    flashed: Boolean,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ZToolCheckbox(
            checked = flashed || selected,
            onCheckedChange = { onClick() },
            enabled = !flashed
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = if (flashed) {
                LocalZToolColorScheme.current.onSurfaceVariant
            } else {
                LocalZToolColorScheme.current.onSurface
            },
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
    }
}
