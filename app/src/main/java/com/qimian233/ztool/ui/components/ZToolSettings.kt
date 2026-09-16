package com.qimian233.ztool.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import com.qimian233.ztool.ui.theme.LocalZToolColorScheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.takeOrElse
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.qimian233.ztool.ui.theme.FrontendStyle
import com.qimian233.ztool.ui.theme.LocalZToolThemeSpec
import top.yukonga.miuix.kmp.icon.basic.ArrowUpDown
import top.yukonga.miuix.kmp.basic.BasicComponent as MiuixBasicComponent
import top.yukonga.miuix.kmp.basic.ListPopupColumn as MiuixListPopupColumn
import top.yukonga.miuix.kmp.basic.PopupPositionProvider as MiuixPopupPositionProvider
import top.yukonga.miuix.kmp.basic.Switch as MiuixSwitch
import top.yukonga.miuix.kmp.icon.MiuixIcons.Basic as MiuixIcons
import top.yukonga.miuix.kmp.window.WindowListPopup as MiuixWindowListPopup

/**
 * Reduced minimum interactive size for controls trailing inside a clickable row: the row
 * itself is the touch target and its horizontal padding already contributes to it, so the
 * 48dp enforcement must not inflate the row height (same approach as the androidx
 * Material3 ListItem decorators).
 */
@Composable
private fun trailingControlMinimumInteractiveSize(horizontalPadding: Dp): Dp {
    return (LocalMinimumInteractiveComponentSize.current.takeOrElse { 0.dp } - horizontalPadding)
        .coerceAtLeast(0.dp)
}

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
            .heightIn(min = if (summary == null) 56.dp else 72.dp)
            .then(
                if (onClick != null) {
                    Modifier.clickable(enabled = enabled) { onClick() }
                } else {
                    Modifier
                }
            )
            .padding(horizontal = 24.dp, vertical = 8.dp),
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
                color = if (enabled) LocalZToolColorScheme.current.onSurface else LocalZToolColorScheme.current.onSurfaceVariant
            )
            if (summary != null) {
                Text(
                    text = summary,
                    style = MaterialTheme.typography.bodyMedium,
                    color = LocalZToolColorScheme.current.onSurfaceVariant,
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
            LocalZToolColorScheme.current.onSurfaceVariant
        } else {
            LocalZToolColorScheme.current.onSurfaceVariant.copy(alpha = 0.38f)
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
    icon: ImageVector? = null,
    padding: Dp = 24.dp
) {
    if (LocalZToolThemeSpec.current.style == FrontendStyle.Miuix) {
        MiuixBasicComponent(
            modifier = modifier,
            title = title,
            summary = summary,
            startAction = icon?.let { ic ->
                { ZToolSettingLeadingIcon(icon = ic, enabled = enabled) }
            },
            endActions = {
                MiuixSwitch(
                    checked = checked,
                    onCheckedChange = onCheckedChange,
                    enabled = enabled
                )
            },
            // The 28dp-tall Miuix switch would push single-line rows to 60dp; trim the
            // vertical inset for single-line rows so they stay at the 56dp baseline.
            insideMargin = PaddingValues(
                horizontal = padding,
                vertical = if (summary == null) 14.dp else 16.dp
            ),
            onClick = { onCheckedChange(!checked) },
            enabled = enabled
        )
        return
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = if (summary == null) 56.dp else 72.dp)
            .clickable(enabled = enabled) { onCheckedChange(!checked) }
            .padding(horizontal = padding, vertical = 8.dp),
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
                color = if (enabled) LocalZToolColorScheme.current.onSurface
                        else LocalZToolColorScheme.current.onSurfaceVariant
            )
            if (summary != null) {
                Text(
                    text = summary,
                    style = MaterialTheme.typography.bodyMedium,
                    color = LocalZToolColorScheme.current.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        CompositionLocalProvider(
            LocalMinimumInteractiveComponentSize provides trailingControlMinimumInteractiveSize(padding)
        ) {
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                enabled = enabled,
                colors = SwitchDefaults.colors(
                    disabledCheckedThumbColor = LocalZToolColorScheme.current.primary.copy(alpha = 0.38f),
                    disabledCheckedTrackColor = LocalZToolColorScheme.current.primary.copy(alpha = 0.12f),
                    disabledCheckedIconColor = LocalZToolColorScheme.current.onPrimary.copy(alpha = 0.38f),
                    disabledUncheckedThumbColor = LocalZToolColorScheme.current.onSurface.copy(alpha = 0.38f),
                    disabledUncheckedTrackColor = LocalZToolColorScheme.current.surfaceContainerHighest.copy(alpha = 0.12f),
                    disabledUncheckedBorderColor = LocalZToolColorScheme.current.outline.copy(alpha = 0.12f),
                    disabledUncheckedIconColor = LocalZToolColorScheme.current.onSurface.copy(alpha = 0.38f)
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
fun ZToolCheckboxRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    summary: String? = null,
    enabled: Boolean = true,
    icon: ImageVector? = null
) {
    if (LocalZToolThemeSpec.current.style == FrontendStyle.Miuix) {
        MiuixBasicComponent(
            modifier = modifier,
            title = title,
            summary = summary,
            startAction = icon?.let { ic ->
                { ZToolSettingLeadingIcon(icon = ic, enabled = enabled) }
            },
            endActions = {
                top.yukonga.miuix.kmp.basic.Checkbox(
                    state = if (checked) ToggleableState.On else ToggleableState.Off,
                    onClick = { onCheckedChange(!checked) },
                    enabled = enabled
                )
            },
            insideMargin = PaddingValues(
                horizontal = 24.dp,
                vertical = if (summary == null) 14.dp else 16.dp
            ),
            onClick = { onCheckedChange(!checked) },
            enabled = enabled
        )
        return
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = if (summary == null) 56.dp else 72.dp)
            .clickable(enabled = enabled) { onCheckedChange(!checked) }
            .padding(horizontal = 24.dp, vertical = 8.dp),
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
                color = if (enabled) LocalZToolColorScheme.current.onSurface
                        else LocalZToolColorScheme.current.onSurfaceVariant
            )
            if (summary != null) {
                Text(
                    text = summary,
                    style = MaterialTheme.typography.bodyMedium,
                    color = LocalZToolColorScheme.current.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        CompositionLocalProvider(
            LocalMinimumInteractiveComponentSize provides trailingControlMinimumInteractiveSize(24.dp)
        ) {
            Checkbox(
                checked = checked,
                onCheckedChange = onCheckedChange,
                enabled = enabled
            )
        }
    }
}

@Composable
fun ZToolCheckbox(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    if (LocalZToolThemeSpec.current.style == FrontendStyle.Miuix) {
        top.yukonga.miuix.kmp.basic.Checkbox(
            state = if (checked) ToggleableState.On else ToggleableState.Off,
            onClick = { onCheckedChange(!checked) },
            modifier = modifier,
            enabled = enabled
        )
    } else {
        Checkbox(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = modifier,
            enabled = enabled
        )
    }
}

@Composable
fun ZToolSettingsDivider(
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    addDefaultPadding: Boolean = true
) {
    val effectiveModifier = if (addDefaultPadding) {
        modifier.padding(start = 24.dp, end = 24.dp)
    } else {
        modifier
    }

    if (LocalZToolThemeSpec.current.style == FrontendStyle.Miuix) {
        val miuixModifier = if (color == Color.Unspecified) effectiveModifier else effectiveModifier
        top.yukonga.miuix.kmp.basic.HorizontalDivider(
            modifier = miuixModifier,
            color = if (color != Color.Unspecified) color else Color.Unspecified
        )
        return
    }

    HorizontalDivider(
        modifier = effectiveModifier,
        color = if (color == Color.Unspecified) LocalZToolColorScheme.current.outlineVariant else color
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
    externalExpanded: Boolean? = null,
    onExternalExpandedChange: ((Boolean) -> Unit)? = null
) {
    ZToolSettingsNavigationEventProvider {
        if (LocalZToolThemeSpec.current.style == FrontendStyle.Miuix) {
            ZToolMiuixPopupMenuField(
                value = value,
                options = options,
                optionLabel = optionLabel,
                onOptionSelected = onOptionSelected,
                modifier = modifier,
                enabled = enabled,
                icon = icon,
                externalExpanded = externalExpanded,
                onExternalExpandedChange = onExternalExpandedChange
            )
            return@ZToolSettingsNavigationEventProvider
        }

        ZToolMaterialPopupMenuField(
            value = value,
            options = options,
            optionLabel = optionLabel,
            onOptionSelected = onOptionSelected,
            modifier = modifier,
            enabled = enabled,
            icon = icon,
            externalExpanded = externalExpanded,
            onExternalExpandedChange = onExternalExpandedChange
        )
    }
}

@Composable
fun <T> ZToolPopupMenuSettingRow(
    title: String,
    value: String,
    options: List<T>,
    optionLabel: (T) -> String,
    onOptionSelected: (T) -> Unit,
    modifier: Modifier = Modifier,
    summary: String? = null,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    fieldMinWidth: Dp = 132.dp,
    fieldMaxWidth: Dp = 180.dp
) {
    var expanded by remember { mutableStateOf(false) }

    if (LocalZToolThemeSpec.current.style == FrontendStyle.Miuix) {
        MiuixBasicComponent(
            modifier = modifier,
            title = title,
            summary = summary,
            startAction = icon?.let { ic ->
                { ZToolSettingLeadingIcon(icon = ic, enabled = enabled) }
            },
            endActions = {
                ZToolPopupMenuField(
                    value = value,
                    options = options,
                    optionLabel = optionLabel,
                    onOptionSelected = onOptionSelected,
                    enabled = enabled,
                    modifier = Modifier.widthIn(min = fieldMinWidth, max = fieldMaxWidth),
                    externalExpanded = expanded,
                    onExternalExpandedChange = { expanded = it }
                )
            },
            insideMargin = PaddingValues(
                horizontal = 24.dp,
                // The 40dp-wide value field would push single-line rows to 80dp; trim the
                // vertical inset for single-line rows so they stay at the 56dp baseline.
                vertical = if (summary == null) 12.dp else 16.dp
            ),
            onClick = {
                if (enabled && options.isNotEmpty()) {
                    expanded = true
                }
            },
            enabled = enabled
        )
        return
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = if (summary == null) 56.dp else 72.dp)
            .clickable(enabled = enabled && options.isNotEmpty()) {
                expanded = true
            }
            .padding(horizontal = 24.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            ZToolSettingLeadingIcon(icon = icon, enabled = enabled)
            Spacer(modifier = Modifier.width(16.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = if (enabled) LocalZToolColorScheme.current.onSurface
                        else LocalZToolColorScheme.current.onSurfaceVariant
            )
            if (summary != null) {
                Text(
                    text = summary,
                    style = MaterialTheme.typography.bodyMedium,
                    color = LocalZToolColorScheme.current.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
        Spacer(modifier = Modifier.width(16.dp))
        CompositionLocalProvider(
            LocalMinimumInteractiveComponentSize provides trailingControlMinimumInteractiveSize(24.dp)
        ) {
            ZToolPopupMenuField(
                value = value,
                options = options,
                optionLabel = optionLabel,
                onOptionSelected = onOptionSelected,
                enabled = enabled,
                modifier = Modifier.widthIn(min = fieldMinWidth, max = fieldMaxWidth),
                externalExpanded = expanded,
                onExternalExpandedChange = { expanded = it }
            )
        }
    }
}

@Composable
fun <T> ZToolPopupDialogField(
    value: String,
    options: List<T>,
    optionLabel: (T) -> String,
    onOptionSelected: (T) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
    dialogTitle: String? = null,
    externalShowDialog: Boolean? = null,
    onExternalShowDialogChange: ((Boolean) -> Unit)? = null
) {
    val internalShowDialog = remember { mutableStateOf(false) }
    val showDialog = externalShowDialog ?: internalShowDialog.value
    val setShowDialog: (Boolean) -> Unit = onExternalShowDialogChange ?: { internalShowDialog.value = it }
    fun selectedIndex(): Int = options.indexOfFirst { optionLabel(it) == value }.coerceAtLeast(0)
    var pendingIndex by remember(value, options) {
        mutableIntStateOf(selectedIndex())
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(enabled = enabled && options.isNotEmpty()) {
                pendingIndex = selectedIndex()
                setShowDialog(true)
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
                LocalZToolColorScheme.current.primary
            } else {
                LocalZToolColorScheme.current.onSurfaceVariant
            },
            modifier = Modifier.weight(1f)
        )
        IconButton(
            enabled = enabled && options.isNotEmpty(),
            onClick = {
                pendingIndex = selectedIndex()
                setShowDialog(true)
            },
            modifier = Modifier.size(40.dp)
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                contentDescription = null,
                tint = if (enabled && options.isNotEmpty()) {
                    LocalZToolColorScheme.current.onSurfaceVariant
                } else {
                    LocalZToolColorScheme.current.onSurfaceVariant.copy(alpha = 0.38f)
                }
            )
        }
    }

    if (showDialog) {
        ZToolDialog(
            onDismissRequest = { setShowDialog(false) },
            title = dialogTitle?.let { titleText ->
                { Text(titleText) }
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
                        val style = LocalZToolThemeSpec.current.style
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(
                                    color = if (selected && style == FrontendStyle.Material3Expressive) {
                                        LocalZToolColorScheme.current.secondaryContainer
                                    } else {
                                        Color.Transparent
                                    }
                                )
                                .clickable { pendingIndex = index }
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (style == FrontendStyle.Miuix) {
                                ZToolCheckbox(
                                    checked = selected,
                                    onCheckedChange = { pendingIndex = index }
                                )
                            } else {
                                RadioButton(
                                    selected = selected,
                                    onClick = { pendingIndex = index }
                                )
                            }
                            Text(
                                text = optionLabel(option),
                                style = MaterialTheme.typography.bodyLarge,
                                color = LocalZToolColorScheme.current.onSurface,
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
                        setShowDialog(false)
                    }
                ) {
                    Text(androidx.compose.ui.res.stringResource(android.R.string.ok))
                }
            },
            dismissButton = {
                TextButton(onClick = { setShowDialog(false) }) {
                    Text(androidx.compose.ui.res.stringResource(android.R.string.cancel))
                }
            }
        )
    }
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
    externalExpanded: Boolean? = null,
    onExternalExpandedChange: ((Boolean) -> Unit)? = null
) {
    val internalExpanded = remember { mutableStateOf(false) }
    val expanded = externalExpanded ?: internalExpanded.value
    val setExpanded: (Boolean) -> Unit = onExternalExpandedChange ?: { internalExpanded.value = it }

    Box(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = enabled && options.isNotEmpty()) {
                    setExpanded(true)
                }
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (icon != null) {
                ZToolSettingLeadingIcon(icon = icon, enabled = enabled)
                Spacer(modifier = Modifier.padding(horizontal = 4.dp))
            }
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = value,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.labelLarge,
                color = LocalZToolColorScheme.current.onSurfaceVariant
            )
            IconButton(
                enabled = enabled && options.isNotEmpty(),
                onClick = { setExpanded(true) },
                modifier = Modifier.size(24.dp)
            ) {
                Icon(
                    imageVector = MiuixIcons.ArrowUpDown,
                    contentDescription = null,
                    tint = if (enabled && options.isNotEmpty()) {
                        LocalZToolColorScheme.current.onSurfaceVariant
                    } else {
                        LocalZToolColorScheme.current.onSurfaceVariant.copy(alpha = 0.38f)
                    }
                )
            }
        }

        MiuixWindowListPopup(
            show = expanded,
            onDismissRequest = { setExpanded(false) },
            alignment = MiuixPopupPositionProvider.Align.End,
            maxHeight = 360.dp,
            minWidth = 0.dp
        ) {
            MiuixListPopupColumn {
                options.forEach { option ->
                    val label = optionLabel(option)
                    val selected = label == value
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onOptionSelected(option)
                                setExpanded(false)
                            }
                            .padding(horizontal = 20.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.bodyLarge,
                            color = LocalZToolColorScheme.current.onSurface,
                            modifier = Modifier.weight(1f)
                        )
                        if (selected) {
                            Icon(
                                imageVector = Icons.Filled.Check,
                                contentDescription = null,
                                tint = LocalZToolColorScheme.current.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        }
    }
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
    externalExpanded: Boolean? = null,
    onExternalExpandedChange: ((Boolean) -> Unit)? = null
) {
    val internalExpanded = remember { mutableStateOf(false) }
    val expanded = externalExpanded ?: internalExpanded.value
    val setExpanded: (Boolean) -> Unit = onExternalExpandedChange ?: { internalExpanded.value = it }

    val transitionState = remember { MutableTransitionState(false) }
    LaunchedEffect(expanded) {
        transitionState.targetState = expanded
    }

    var anchorHeight by remember { mutableIntStateOf(0) }

    Box(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .onSizeChanged { anchorHeight = it.height }
                .clickable(enabled = enabled && options.isNotEmpty()) {
                    setExpanded(true)
                }
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (icon != null) {
                ZToolSettingLeadingIcon(icon = icon, enabled = enabled)
                Spacer(modifier = Modifier.padding(horizontal = 4.dp))
            }
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = value,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.labelLarge,
                color = if (enabled) {
                    LocalZToolColorScheme.current.primary
                } else {
                    LocalZToolColorScheme.current.onSurfaceVariant
                }
            )
            IconButton(
                enabled = enabled && options.isNotEmpty(),
                onClick = { setExpanded(true) },
                modifier = Modifier.size(24.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.ArrowDropDown,
                    contentDescription = null,
                    tint = if (enabled && options.isNotEmpty()) {
                        LocalZToolColorScheme.current.onSurfaceVariant
                    } else {
                        LocalZToolColorScheme.current.onSurfaceVariant.copy(alpha = 0.38f)
                    }
                )
            }
        }

        if (transitionState.currentState || transitionState.targetState) {
            Popup(
                onDismissRequest = { setExpanded(false) },
                offset = IntOffset(0, anchorHeight),
                properties = PopupProperties(focusable = true, dismissOnBackPress = true, dismissOnClickOutside = true),
            ) {
                AnimatedVisibility(
                    visibleState = transitionState,
                    enter = expandVertically(expandFrom = Alignment.Top) + fadeIn(),
                    exit = shrinkVertically(shrinkTowards = Alignment.Top) + fadeOut()
                ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = LocalZToolColorScheme.current.surfaceContainerHigh,
                    tonalElevation = 3.dp,
                    shadowElevation = 3.dp,
                    modifier = Modifier
                        .heightIn(max = 360.dp)
                        .widthIn(max = 160.dp)
                ) {
                    Column {
                        options.forEach { option ->
                            val label = optionLabel(option)
                            val selected = label == value
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .then(
                                        if (selected) {
                                            Modifier.background(
                                                color = LocalZToolColorScheme.current.primaryContainer,
                                                shape = RoundedCornerShape(12.dp)
                                            )
                                        } else {
                                            Modifier
                                        }
                                    )
                                    .clickable {
                                        onOptionSelected(option)
                                        setExpanded(false)
                                    }
                                    .padding(horizontal = 12.dp, vertical = 14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (selected) {
                                    Icon(
                                        imageVector = Icons.Filled.Check,
                                        contentDescription = null,
                                        tint = LocalZToolColorScheme.current.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                }
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = if (selected) {
                                        LocalZToolColorScheme.current.primary
                                    } else {
                                        LocalZToolColorScheme.current.onSurface
                                    },
                                    modifier = Modifier.weight(1f).padding(start = if (selected) 0.dp else 24.dp)
                                )
                            }
                        }
                    }
                }
                }
            }
        }
    }
}
