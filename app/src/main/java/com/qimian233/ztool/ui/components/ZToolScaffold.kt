package com.qimian233.ztool.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ZToolTabletScaffold(
    title: String,
    navigationRail: @Composable ColumnScope.() -> Unit,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable (androidx.compose.foundation.layout.PaddingValues) -> Unit
) {
    Row(modifier = modifier.fillMaxSize()) {
        ZToolNavigationRail(content = navigationRail)
        Scaffold(
            topBar = {
                ZToolTopAppBar(
                    title = title,
                    actions = actions
                )
            },
            content = content
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ZToolTopAppBar(
    title: String,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {}
) {
    TopAppBar(
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall
            )
        },
        actions = actions,
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface,
            scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer
        ),
        modifier = modifier
    )
}

@Composable
fun ZToolNavigationRail(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    NavigationRail(
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        modifier = modifier,
        content = content
    )
}

@Composable
fun ZToolNavigationRailItem(
    selected: Boolean,
    onClick: () -> Unit,
    icon: @Composable () -> Unit,
    label: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    NavigationRailItem(
        selected = selected,
        onClick = onClick,
        icon = icon,
        label = label,
        enabled = enabled,
        colors = NavigationRailItemDefaults.colors(
            selectedIconColor = MaterialTheme.colorScheme.onSecondaryContainer,
            selectedTextColor = MaterialTheme.colorScheme.onSurface,
            indicatorColor = MaterialTheme.colorScheme.secondaryContainer,
            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
        ),
        modifier = modifier
    )
}
