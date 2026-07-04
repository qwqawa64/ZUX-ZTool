package com.qimian233.ztool.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qimian233.ztool.ui.theme.FrontendStyle
import com.qimian233.ztool.ui.theme.LocalZToolColorScheme
import com.qimian233.ztool.ui.theme.LocalZToolThemeSpec
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.NavigationRailItem as MiuixNavigationRailItem
import top.yukonga.miuix.kmp.basic.ScrollBehavior as MiuixScrollBehaviorType
import top.yukonga.miuix.kmp.basic.TopAppBar as MiuixTopAppBar
import top.yukonga.miuix.kmp.theme.MiuixTheme

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
            Box(modifier = Modifier.fillMaxSize()) {
                top.yukonga.miuix.kmp.basic.Scaffold(
                    modifier = scaffoldModifier,
                    topBar = topBar,
                    content = content
                )
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
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
            Icon(
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

// ===== NavigationBar (Bottom Bar) =====
//
// Future insertion points for Miuix visual effects:
//   [FP-1] Wrap MiuixNavigationBar with BlurredBar(blurBackdrop) for background blur.
//          Requires `miuix-blur` dependency + a CompositionLocal for `blurBackdrop`.
//   [FP-2] Replace the entire Miuix branch with FloatingBottomBar when
//          `LocalEnableFloatingBottomBar.current` is true.
//          Requires porting FloatingBottomBar.kt + DampedDragAnimation from reference.
//   [FP-3] Enable Liquid Glass (vibrancy + lens + BloomStroke) inside
//          FloatingBottomBar by passing `isBlurEnabled = true` + `backdrop`.
//          Requires `miuix-blur` + `drawBackdrop` + `rememberLayerBackdrop`.

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ZToolNavigationBar(
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit
) {
    if (LocalZToolThemeSpec.current.style == FrontendStyle.Miuix) {
        // [FP-1] Insert BlurredBar wrapper here
        // [FP-2] Insert FloatingBottomBar switch here
        top.yukonga.miuix.kmp.basic.NavigationBar(
            modifier = modifier,
            color = LocalZToolColorScheme.current.surface,
        ) {
            content()
        }
        return
    }

    NavigationBar(
        modifier = modifier,
        containerColor = LocalZToolColorScheme.current.surface,
        contentColor = LocalZToolColorScheme.current.onSurface,
        content = content
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ZToolNavigationBarItem(
    selected: Boolean,
    onClick: () -> Unit,
    icon: ImageVector,
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    if (LocalZToolThemeSpec.current.style == FrontendStyle.Miuix) {
        // Miuix NavigationBarItem is not exported in 0.9.2 Android artifact.
        // Use a manual implementation that renders Icon + Text inside a clickable column.
        // Future: replace with top.yukonga.miuix.kmp.basic.NavigationBarItem when available.
        val iconTint = if (selected) MiuixTheme.colorScheme.onSecondaryContainer
            else MiuixTheme.colorScheme.onSurfaceVariantSummary
        val textColor = if (selected) MiuixTheme.colorScheme.onSurface
            else MiuixTheme.colorScheme.onSurfaceVariantSummary
        Box(
            modifier = modifier
                .padding(vertical = 8.dp)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onClick,
                    enabled = enabled,
                    role = Role.Tab
                ),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically)
            ) {
                top.yukonga.miuix.kmp.basic.Icon(
                    imageVector = icon,
                    contentDescription = label,
                    modifier = Modifier.size(24.dp),
                    tint = iconTint
                )
                top.yukonga.miuix.kmp.basic.Text(
                    text = label,
                    color = textColor,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Visible
                )
            }
        }
        return
    }

    // Material3: NavigationBarItem was removed in Material3 1.4.0 — use manual item.
    val iconColor = if (selected) LocalZToolColorScheme.current.onSecondaryContainer
        else LocalZToolColorScheme.current.onSurfaceVariant
    val textColor = if (selected) LocalZToolColorScheme.current.onSurface
        else LocalZToolColorScheme.current.onSurfaceVariant
    Box(
        modifier = modifier
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
                enabled = enabled,
                role = Role.Tab
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                modifier = Modifier.size(28.dp),
                tint = iconColor
            )
            Text(
                text = label,
                color = textColor,
                style = MaterialTheme.typography.labelSmall
            )
        }
    }
}
