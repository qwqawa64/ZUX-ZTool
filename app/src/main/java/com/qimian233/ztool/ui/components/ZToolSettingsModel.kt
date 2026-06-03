package com.qimian233.ztool.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.qimian233.ztool.ui.theme.FrontendStyle
import com.qimian233.ztool.ui.theme.LocalZToolThemeSpec

data class SettingSection(
    val title: String? = null,
    val items: List<SettingItem>
)

sealed interface SettingItem {
    val key: String?
    val enabled: Boolean

    data class Switch(
        val title: String,
        val checked: Boolean,
        val onCheckedChange: (Boolean) -> Unit,
        val summary: String? = null,
        override val enabled: Boolean = true,
        override val key: String? = null
    ) : SettingItem

    data class Entry(
        val title: String,
        val onClick: () -> Unit,
        val summary: String? = null,
        val leadingContent: (@Composable RowScope.() -> Unit)? = null,
        val trailingContent: (@Composable RowScope.() -> Unit)? = null,
        override val enabled: Boolean = true,
        override val key: String? = null
    ) : SettingItem

    data class Dropdown<T>(
        val label: String,
        val value: String,
        val options: List<T>,
        val optionLabel: (T) -> String,
        val onOptionSelected: (T) -> Unit,
        override val enabled: Boolean = true,
        override val key: String? = null
    ) : SettingItem

    data class Slider(
        val title: String,
        val value: Float,
        val onValueChange: (Float) -> Unit,
        val summary: String? = null,
        val valueText: String? = null,
        val valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
        val steps: Int = 0,
        val onValueChangeFinished: (() -> Unit)? = null,
        override val enabled: Boolean = true,
        override val key: String? = null
    ) : SettingItem

    data class TextInput(
        val label: String,
        val value: String,
        val onValueChange: (String) -> Unit,
        val title: String? = null,
        val summary: String? = null,
        val placeholder: String? = null,
        val singleLine: Boolean = true,
        override val enabled: Boolean = true,
        override val key: String? = null
    ) : SettingItem

    data class ColorPreview(
        val title: String,
        val color: Color,
        val onClick: () -> Unit,
        val summary: String? = null,
        override val enabled: Boolean = true,
        override val key: String? = null
    ) : SettingItem

    data class Action(
        val title: String,
        val onClick: () -> Unit,
        val summary: String? = null,
        val leadingContent: (@Composable RowScope.() -> Unit)? = null,
        val trailingContent: (@Composable RowScope.() -> Unit)? = null,
        override val enabled: Boolean = true,
        override val key: String? = null
    ) : SettingItem

    data class Custom(
        val content: @Composable () -> Unit,
        override val enabled: Boolean = true,
        override val key: String? = null
    ) : SettingItem
}

@Composable
fun ZToolSettingsList(
    sections: List<SettingSection>,
    modifier: Modifier = Modifier,
    sectionSpacing: Dp = 16.dp,
    bottomPadding: Dp = 0.dp
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(sectionSpacing)
    ) {
        sections.forEach { section ->
            ZToolSettingsSection(section = section)
        }
        if (bottomPadding > 0.dp) {
            Spacer(modifier = Modifier.height(bottomPadding))
        }
    }
}

@Composable
fun ZToolSettingsSection(
    section: SettingSection,
    modifier: Modifier = Modifier,
    titlePadding: PaddingValues = PaddingValues(horizontal = 24.dp, vertical = 8.dp)
) {
    if (LocalZToolThemeSpec.current.style == FrontendStyle.Material3Expressive) {
        MaterialExpressiveSettingsSection(
            section = section,
            modifier = modifier,
            titlePadding = titlePadding
        )
        return
    }

    ZToolCard(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp)
        ) {
            if (section.title != null) {
                Text(
                    text = section.title,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(titlePadding)
                )
            }

            section.items.forEachIndexed { index, item ->
                if (index > 0) {
                    ZToolSettingsDivider()
                }
                ZToolSettingItem(item = item)
            }
        }
    }
}

@Composable
private fun MaterialExpressiveSettingsSection(
    section: SettingSection,
    modifier: Modifier = Modifier,
    titlePadding: PaddingValues
) {
    ZToolCard(
        modifier = modifier.fillMaxWidth(),
        containerColor = materialExpressiveSettingsSectionColor()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            if (section.title != null) {
                Text(
                    text = section.title,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(titlePadding)
                )
            }

            ExpressiveSectionItems(count = section.items.size) { itemModifier ->
                section.items.forEach { item ->
                    ZToolSettingItem(item = item, modifier = itemModifier())
                }
            }
        }
    }
}

@Composable
fun ColumnScope.ExpressiveSectionItems(
    count: Int,
    content: @Composable ColumnScope.(() -> Modifier) -> Unit
) {
    if (LocalZToolThemeSpec.current.style != FrontendStyle.Material3Expressive) {
        content { Modifier }
        return
    }

    var index = 0
    val itemColor = MaterialTheme.colorScheme.surfaceContainerHigh
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        content {
            val itemIndex = index
            val shape = expressiveSettingsItemShape(index = itemIndex, count = count)
            index += 1
            Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(
                    color = itemColor,
                    shape = shape
                )
        }
    }
}

@Composable
fun materialExpressiveSettingsSectionColor(): Color {
    val colorScheme = MaterialTheme.colorScheme
    return if (colorScheme.surface.luminance() > 0.5f) {
        colorScheme.surfaceContainerLowest
    } else {
        colorScheme.surfaceContainerLow
    }
}

fun expressiveSettingsItemShape(index: Int, count: Int): Shape {
    if (count <= 1) {
        return RoundedCornerShape(16.dp)
    }

    return when (index) {
        0 -> RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = 8.dp, bottomEnd = 8.dp)
        count - 1 -> RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp, bottomStart = 16.dp, bottomEnd = 16.dp)
        else -> RoundedCornerShape(8.dp)
    }
}

@Composable
fun ZToolSettingItem(
    item: SettingItem,
    modifier: Modifier = Modifier
) {
    when (item) {
        is SettingItem.Switch -> {
            ZToolSwitchRow(
                title = item.title,
                summary = item.summary,
                checked = item.checked,
                onCheckedChange = item.onCheckedChange,
                enabled = item.enabled,
                modifier = modifier
            )
        }

        is SettingItem.Entry -> {
            ZListItem(
                title = item.title,
                summary = item.summary,
                enabled = item.enabled,
                onClick = item.onClick,
                leadingContent = item.leadingContent,
                trailingContent = item.trailingContent,
                modifier = modifier
            )
        }

        is SettingItem.Dropdown<*> -> {
            ZToolDropdownSettingItem(item = item, modifier = modifier)
        }

        is SettingItem.Slider -> {
            ZToolSliderRow(
                title = item.title,
                summary = item.summary,
                value = item.value,
                valueText = item.valueText,
                valueRange = item.valueRange,
                steps = item.steps,
                onValueChange = item.onValueChange,
                onValueChangeFinished = item.onValueChangeFinished,
                enabled = item.enabled,
                modifier = modifier
            )
        }

        is SettingItem.TextInput -> {
            ZToolTextInputRow(
                label = item.label,
                value = item.value,
                onValueChange = item.onValueChange,
                title = item.title,
                summary = item.summary,
                placeholder = item.placeholder,
                singleLine = item.singleLine,
                enabled = item.enabled,
                modifier = modifier
            )
        }

        is SettingItem.ColorPreview -> {
            ZListItem(
                title = item.title,
                summary = item.summary,
                enabled = item.enabled,
                onClick = item.onClick,
                trailingContent = {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .background(item.color, CircleShape)
                    )
                },
                modifier = modifier
            )
        }

        is SettingItem.Action -> {
            ZListItem(
                title = item.title,
                summary = item.summary,
                enabled = item.enabled,
                onClick = item.onClick,
                leadingContent = item.leadingContent,
                trailingContent = item.trailingContent,
                modifier = modifier
            )
        }

        is SettingItem.Custom -> {
            item.content()
        }
    }
}

@Composable
private fun <T> ZToolDropdownSettingItem(
    item: SettingItem.Dropdown<T>,
    modifier: Modifier = Modifier
) {
    ZToolDropdownField(
        label = item.label,
        value = item.value,
        options = item.options,
        optionLabel = item.optionLabel,
        onOptionSelected = item.onOptionSelected,
        enabled = item.enabled,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 12.dp)
    )
}

@Composable
fun ZToolSliderRow(
    title: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    summary: String? = null,
    valueText: String? = null,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    steps: Int = 0,
    onValueChangeFinished: (() -> Unit)? = null,
    enabled: Boolean = true
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 16.dp)
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
        if (valueText != null) {
            Text(
                text = valueText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.padding(top = 8.dp),
            enabled = enabled,
            valueRange = valueRange,
            steps = steps,
            onValueChangeFinished = onValueChangeFinished
        )
    }
}

@Composable
fun ZToolTextInputRow(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    title: String? = null,
    summary: String? = null,
    placeholder: String? = null,
    singleLine: Boolean = true,
    enabled: Boolean = true
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 16.dp)
    ) {
        if (title != null) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (summary != null) {
            Text(
                text = summary,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text(label) },
            placeholder = placeholder?.let { placeholderText ->
                { Text(placeholderText) }
            },
            singleLine = singleLine,
            enabled = enabled,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = if (title != null || summary != null) 12.dp else 0.dp)
        )
    }
}
