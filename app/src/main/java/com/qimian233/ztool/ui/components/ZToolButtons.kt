package com.qimian233.ztool.ui.components

import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.qimian233.ztool.ui.theme.FrontendStyle
import com.qimian233.ztool.ui.theme.LocalZToolThemeSpec

import top.yukonga.miuix.kmp.basic.Button as MiuixButton
import top.yukonga.miuix.kmp.basic.ButtonDefaults as MiuixButtonDefaults

@Composable
fun ZToolButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isPrimary: Boolean = true,
    content: @Composable RowScope.() -> Unit
) {
    val dialogModifier = LocalDialogButtonModifier.current
    val effectiveModifier = dialogModifier.then(modifier)

    if (LocalZToolThemeSpec.current.style == FrontendStyle.Miuix) {
        val colors = if (isPrimary) MiuixButtonDefaults.buttonColorsPrimary() else MiuixButtonDefaults.buttonColors()
        MiuixButton(
            onClick = onClick,
            modifier = effectiveModifier,
            enabled = enabled,
            colors = colors
        ) {
            androidx.compose.runtime.CompositionLocalProvider(
                androidx.compose.material3.LocalContentColor provides if (enabled) colors.contentColor else colors.disabledContentColor
            ) {
                content()
            }
        }
        return
    }

    if (isPrimary) {
        Button(
            onClick = onClick,
            modifier = effectiveModifier,
            enabled = enabled,
            content = content
        )
    } else {
        FilledTonalButton(
            onClick = onClick,
            modifier = effectiveModifier,
            enabled = enabled,
            content = content
        )
    }
}

@Composable
fun ZToolTextButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isPrimary: Boolean = true
) {
    val dialogModifier = LocalDialogButtonModifier.current
    val effectiveModifier = dialogModifier.then(modifier)

    if (LocalZToolThemeSpec.current.style == FrontendStyle.Miuix) {
        val colors = if (isPrimary) MiuixButtonDefaults.buttonColorsPrimary() else MiuixButtonDefaults.buttonColors()
        MiuixButton(
            onClick = onClick,
            modifier = effectiveModifier,
            enabled = enabled,
            colors = colors
        ) {
            Text(
                text = text,
                color = if (enabled) colors.contentColor else colors.disabledContentColor
            )
        }
        return
    }

    TextButton(
        onClick = onClick,
        modifier = effectiveModifier,
        enabled = enabled,
    ) {
        Text(text = text)
    }
}

@Composable
fun ZToolExtendedFloatingActionButton(
    text: @Composable () -> Unit,
    icon: @Composable () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isPrimary: Boolean = true
) {
    if (LocalZToolThemeSpec.current.style == FrontendStyle.Miuix) {
        val colors = if (isPrimary) MiuixButtonDefaults.buttonColorsPrimary() else MiuixButtonDefaults.buttonColors()
        MiuixButton(
            onClick = onClick,
            modifier = modifier,
            colors = colors,
            minHeight = 64.dp
        ) {
            androidx.compose.runtime.CompositionLocalProvider(
                androidx.compose.material3.LocalContentColor provides colors.contentColor
            ) {
                icon()
                Spacer(modifier = Modifier.width(8.dp))
                text()
            }
        }
        return
    }

    ExtendedFloatingActionButton(
        text = text,
        icon = icon,
        onClick = onClick,
        modifier = modifier
    )
}

@Composable
fun ZToolFloatingActionButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isPrimary: Boolean = true,
    content: @Composable () -> Unit
) {
    if (LocalZToolThemeSpec.current.style == FrontendStyle.Miuix) {
        val colors = if (isPrimary) MiuixButtonDefaults.buttonColorsPrimary() else MiuixButtonDefaults.buttonColors()
        MiuixButton(
            onClick = onClick,
            modifier = modifier,
            colors = colors,
            minWidth = 64.dp,
            minHeight = 64.dp
        ) {
            androidx.compose.runtime.CompositionLocalProvider(
                androidx.compose.material3.LocalContentColor provides colors.contentColor
            ) {
                content()
            }
        }
        return
    }

    FloatingActionButton(
        onClick = onClick,
        modifier = modifier,
        content = content
    )
}
