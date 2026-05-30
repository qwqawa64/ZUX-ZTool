package com.qimian233.ztool.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.qimian233.ztool.ui.theme.FrontendStyle
import com.qimian233.ztool.ui.theme.LocalZToolThemeSpec
import top.yukonga.miuix.kmp.basic.NavigationRailItem as MiuixNavigationRailItem
import top.yukonga.miuix.kmp.basic.SmallTopAppBar as MiuixSmallTopAppBar

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

@Composable
fun ZToolScaffold(
    modifier: Modifier = Modifier,
    topBar: @Composable () -> Unit = {},
    floatingActionButton: @Composable () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit
) {
    Scaffold(
        modifier = modifier,
        topBar = topBar,
        floatingActionButton = floatingActionButton,
        content = content
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ZToolTopAppBar(
    title: String,
    modifier: Modifier = Modifier,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {}
) {
    if (LocalZToolThemeSpec.current.style == FrontendStyle.Miuix) {
        MiuixSmallTopAppBar(
            title = title,
            modifier = modifier,
            color = MaterialTheme.colorScheme.surface,
            titleColor = MaterialTheme.colorScheme.onSurface,
            navigationIcon = navigationIcon,
            actions = actions
        )
        return
    }

    TopAppBar(
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        navigationIcon = navigationIcon,
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
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.fillMaxHeight(),
            verticalArrangement = Arrangement.Center,
            content = content
        )
    }
}

@Composable
fun ZToolNavigationRailItem(
    selected: Boolean,
    onClick: () -> Unit,
    icon: ImageVector,
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    if (LocalZToolThemeSpec.current.style == FrontendStyle.Miuix) {
        MiuixNavigationRailItem(
            selected = selected,
            onClick = onClick,
            icon = icon,
            label = label,
            modifier = modifier
                .width(80.dp)
                .height(72.dp),
            enabled = enabled
        )
        return
    }

    NavigationRailItem(
        selected = selected,
        onClick = onClick,
        icon = {
            androidx.compose.material3.Icon(
                imageVector = icon,
                contentDescription = label,
                modifier = Modifier.size(28.dp)
            )
        },
        label = { Text(label) },
        enabled = enabled,
        colors = NavigationRailItemDefaults.colors(
            selectedIconColor = MaterialTheme.colorScheme.onSecondaryContainer,
            selectedTextColor = MaterialTheme.colorScheme.onSurface,
            indicatorColor = MaterialTheme.colorScheme.secondaryContainer,
            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
        ),
        modifier = modifier
            .width(80.dp)
            .height(72.dp)
    )
}
