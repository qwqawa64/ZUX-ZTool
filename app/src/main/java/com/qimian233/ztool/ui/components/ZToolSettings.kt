package com.qimian233.ztool.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.qimian233.ztool.ui.theme.FrontendStyle
import com.qimian233.ztool.ui.theme.LocalZToolThemeSpec
import top.yukonga.miuix.kmp.basic.BasicComponent as MiuixBasicComponent
import top.yukonga.miuix.kmp.basic.HorizontalDivider as MiuixHorizontalDivider
import top.yukonga.miuix.kmp.basic.Switch as MiuixSwitch

@Composable
fun ZListItem(
    title: String,
    modifier: Modifier = Modifier,
    summary: String? = null,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null,
    leadingContent: @Composable (RowScope.() -> Unit)? = null,
    trailingContent: @Composable (RowScope.() -> Unit)? = null
) {
    if (LocalZToolThemeSpec.current.style == FrontendStyle.Miuix) {
        MiuixBasicComponent(
            modifier = modifier,
            title = title,
            summary = summary,
            startAction = leadingContent?.let {
                {
                    Row {
                        it()
                    }
                }
            },
            endActions = trailingContent,
            insideMargin = PaddingValues(horizontal = 24.dp, vertical = 16.dp),
            onClick = onClick,
            enabled = enabled
        )
        return
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (onClick != null) {
                    Modifier.clickable(enabled = enabled) { onClick() }
                } else {
                    Modifier
                }
            )
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (leadingContent != null) {
            leadingContent()
            Spacer(modifier = Modifier.padding(horizontal = 8.dp))
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .widthIn(max = 720.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (summary != null) {
                Text(
                    text = summary,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
        if (trailingContent != null) {
            Spacer(modifier = Modifier.padding(horizontal = 8.dp))
            trailingContent()
        }
    }
}

@Composable
fun ZToolSettingLeadingIcon(
    icon: ImageVector,
    enabled: Boolean = true
) {
    Icon(
        imageVector = icon,
        contentDescription = null,
        tint = if (enabled) {
            MaterialTheme.colorScheme.onSurfaceVariant
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
        },
        modifier = Modifier.size(24.dp)
    )
}

@Composable
fun ZToolSwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    summary: String? = null,
    enabled: Boolean = true,
    icon: ImageVector? = null
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(enabled = enabled) { onCheckedChange(!checked) }
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            ZToolSettingLeadingIcon(icon = icon, enabled = enabled)
            Spacer(modifier = Modifier.padding(horizontal = 8.dp))
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .widthIn(max = 720.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (summary != null) {
                Text(
                    text = summary,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
        Spacer(modifier = Modifier.padding(horizontal = 12.dp))
        if (LocalZToolThemeSpec.current.style == FrontendStyle.Miuix) {
            MiuixSwitch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                enabled = enabled,
                colors = top.yukonga.miuix.kmp.basic.SwitchDefaults.switchColors(
                    disabledCheckedThumbColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.38f),
                    disabledCheckedTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                    disabledUncheckedThumbColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                    disabledUncheckedTrackColor = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.12f),
                )
            )
        } else {
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                enabled = enabled,
                colors = SwitchDefaults.colors(
                    disabledCheckedThumbColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.38f),
                    disabledCheckedTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                    disabledCheckedIconColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.38f),
                    disabledUncheckedThumbColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                    disabledUncheckedTrackColor = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.12f),
                    disabledUncheckedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f),
                    disabledUncheckedIconColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                ),
                thumbContent = if (checked) {
                    {
                        Icon(
                            imageVector = Icons.Filled.Check,
                            contentDescription = null,
                            modifier = Modifier.size(SwitchDefaults.IconSize),
                        )
                    }
                } else {
                    {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = null,
                            modifier = Modifier.size(SwitchDefaults.IconSize),
                        )
                    }
                }
            )
        }
    }
}

@Composable
fun ZToolSettingsDivider(modifier: Modifier = Modifier) {
    if (LocalZToolThemeSpec.current.style == FrontendStyle.Miuix) {
        MiuixHorizontalDivider(
            modifier = modifier.padding(start = 24.dp, end = 24.dp)
        )
        return
    }

    HorizontalDivider(
        modifier = modifier.padding(start = 24.dp, end = 24.dp),
        color = MaterialTheme.colorScheme.outlineVariant
    )
}

@Composable
fun <T> ZToolPopupMenuField(
    value: String,
    options: List<T>,
    optionLabel: (T) -> String,
    onOptionSelected: (T) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
    dialogTitle: String? = null
) {
    if (LocalZToolThemeSpec.current.style == FrontendStyle.Miuix) {
        ZToolMiuixPopupMenuField(
            value = value,
            options = options,
            optionLabel = optionLabel,
            onOptionSelected = onOptionSelected,
            modifier = modifier,
            enabled = enabled,
            icon = icon,
            dialogTitle = dialogTitle
        )
        return
    }

    ZToolMaterialPopupMenuField(
        value = value,
        options = options,
        optionLabel = optionLabel,
        onOptionSelected = onOptionSelected,
        modifier = modifier,
        enabled = enabled,
        icon = icon,
        dialogTitle = dialogTitle
    )
}

@Composable
private fun <T> ZToolMiuixPopupMenuField(
    value: String,
    options: List<T>,
    optionLabel: (T) -> String,
    onOptionSelected: (T) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
    dialogTitle: String? = null
) {
    ZToolMaterialPopupMenuField(
        value = value,
        options = options,
        optionLabel = optionLabel,
        onOptionSelected = onOptionSelected,
        modifier = modifier,
        enabled = enabled,
        icon = icon,
        dialogTitle = dialogTitle
    )
}

@Composable
private fun <T> ZToolMaterialPopupMenuField(
    value: String,
    options: List<T>,
    optionLabel: (T) -> String,
    onOptionSelected: (T) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
    dialogTitle: String? = null
) {
    var showDialog by remember { mutableStateOf(false) }
    fun selectedIndex(): Int = options.indexOfFirst { optionLabel(it) == value }.coerceAtLeast(0)
    var pendingIndex by remember(value, options) {
        mutableStateOf(selectedIndex())
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(enabled = enabled && options.isNotEmpty()) {
                pendingIndex = selectedIndex()
                showDialog = true
            }
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            ZToolSettingLeadingIcon(icon = icon, enabled = enabled)
            Spacer(modifier = Modifier.padding(horizontal = 4.dp))
        }
        Text(
            text = value,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.labelLarge,
            color = if (enabled) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            modifier = Modifier.weight(1f)
        )
        IconButton(
            enabled = enabled && options.isNotEmpty(),
            onClick = {
                pendingIndex = selectedIndex()
                showDialog = true
            },
            modifier = Modifier.size(40.dp)
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                contentDescription = null,
                tint = if (enabled && options.isNotEmpty()) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                }
            )
        }
    }

    if (showDialog) {
        ZToolDialog(
            onDismissRequest = { showDialog = false },
            title = {
                Text(dialogTitle ?: value)
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 360.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    options.forEachIndexed { index, option ->
                        val selected = index == pendingIndex
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { pendingIndex = index }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = selected,
                                onClick = { pendingIndex = index }
                            )
                            Text(
                                text = optionLabel(option),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(start = 8.dp)
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        options.getOrNull(pendingIndex)?.let(onOptionSelected)
                        showDialog = false
                    }
                ) {
                    Text(androidx.compose.ui.res.stringResource(android.R.string.ok))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDialog = false }) {
                    Text(androidx.compose.ui.res.stringResource(android.R.string.cancel))
                }
            }
        )
    }
}
