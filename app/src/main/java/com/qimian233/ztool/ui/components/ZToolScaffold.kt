package com.qimian233.ztool.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.qimian233.ztool.ui.theme.FrontendStyle
import com.qimian233.ztool.ui.theme.LocalZToolThemeSpec
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.NavigationRailItem as MiuixNavigationRailItem
import top.yukonga.miuix.kmp.basic.ScrollBehavior as MiuixScrollBehaviorType
import top.yukonga.miuix.kmp.basic.TopAppBar as MiuixTopAppBar

private val LocalMiuixTopAppBarScrollBehavior = staticCompositionLocalOf<MiuixScrollBehaviorType?> { null }

@Composable
fun ZToolScaffold(
    modifier: Modifier = Modifier,
    topBar: @Composable () -> Unit = {},
    floatingActionButton: @Composable () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit
) {
    val useMiuix = LocalZToolThemeSpec.current.style == FrontendStyle.Miuix
    val scrollBehavior = if (useMiuix) MiuixScrollBehavior() else null
    val scaffoldModifier = scrollBehavior?.let {
        modifier.nestedScroll(it.nestedScrollConnection)
    } ?: modifier

    CompositionLocalProvider(LocalMiuixTopAppBarScrollBehavior provides scrollBehavior) {
        Scaffold(
            modifier = scaffoldModifier,
            topBar = topBar,
            floatingActionButton = floatingActionButton,
            content = content
        )
    }
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
        MiuixTopAppBar(
            title = title,
            modifier = modifier,
            color = MaterialTheme.colorScheme.surface,
            titleColor = MaterialTheme.colorScheme.onSurface,
            navigationIcon = navigationIcon,
            actions = actions,
            scrollBehavior = LocalMiuixTopAppBarScrollBehavior.current,
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
