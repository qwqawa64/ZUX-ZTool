package com.qimian233.ztool.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Alignment
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import com.qimian233.ztool.ui.theme.LocalZToolColorScheme
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
        if (useMiuix) {
            androidx.compose.foundation.layout.Box(modifier = Modifier.fillMaxSize()) {
                top.yukonga.miuix.kmp.basic.Scaffold(
                    modifier = scaffoldModifier,
                    topBar = topBar,
                    content = content
                )
                androidx.compose.foundation.layout.Box(
                    modifier = Modifier
                        .align(androidx.compose.ui.Alignment.BottomEnd)
                        .padding(24.dp)
                ) {
                    floatingActionButton()
                }
            }
        } else {
            Scaffold(
                modifier = scaffoldModifier,
                topBar = topBar,
                floatingActionButton = floatingActionButton,
                containerColor = LocalZToolColorScheme.current.background,
                content = content
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ZToolTopAppBar(
    title: String,
    modifier: Modifier = Modifier,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
    addNavIcon : Boolean = true
) {
    if (LocalZToolThemeSpec.current.style == FrontendStyle.Miuix) {
        if (addNavIcon) {
            MiuixTopAppBar(
                title = title,
                modifier = modifier,
                navigationIcon = navigationIcon,
                actions = actions,
                scrollBehavior = LocalMiuixTopAppBarScrollBehavior.current,
            )
        } else {
            MiuixTopAppBar(
                title = title,
                modifier = modifier,
                actions = actions,
                scrollBehavior = LocalMiuixTopAppBarScrollBehavior.current,
            )
        }

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
            containerColor = LocalZToolColorScheme.current.surface,
            scrolledContainerColor = LocalZToolColorScheme.current.surfaceContainer
        ),
        modifier = modifier
    )
}

@Composable
fun ZToolNavigationRail(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    if (LocalZToolThemeSpec.current.style == FrontendStyle.Miuix) {
        NavigationRail(
            containerColor = LocalZToolColorScheme.current.background,
            contentColor = LocalZToolColorScheme.current.onSurface,
            modifier = modifier
        ) {
            Column(
                modifier = Modifier.fillMaxHeight(),
                verticalArrangement = Arrangement.Center,
                content = content
            )
        }
        return
    }

    NavigationRail(
        containerColor = LocalZToolColorScheme.current.surface,
        contentColor = LocalZToolColorScheme.current.onSurface,
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
            selectedIconColor = LocalZToolColorScheme.current.onSecondaryContainer,
            selectedTextColor = LocalZToolColorScheme.current.onSurface,
            indicatorColor = LocalZToolColorScheme.current.secondaryContainer,
            unselectedIconColor = LocalZToolColorScheme.current.onSurfaceVariant,
            unselectedTextColor = LocalZToolColorScheme.current.onSurfaceVariant
        ),
        modifier = modifier
            .width(80.dp)
            .height(72.dp)
    )
}
