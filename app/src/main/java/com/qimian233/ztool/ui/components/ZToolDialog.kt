package com.qimian233.ztool.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.qimian233.ztool.ui.theme.FrontendStyle
import com.qimian233.ztool.ui.theme.LocalZToolThemeSpec
import top.yukonga.miuix.kmp.basic.Surface as MiuixSurface

@Composable
fun ZToolDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    title: @Composable (() -> Unit)? = null,
    text: @Composable (() -> Unit)? = null,
    dismissButton: @Composable (() -> Unit)? = null
) {
    if (LocalZToolThemeSpec.current.style == FrontendStyle.Miuix) {
        Dialog(onDismissRequest = onDismissRequest) {
            MiuixSurface(
                modifier = Modifier.widthIn(min = 280.dp, max = 560.dp),
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface
            ) {
                Column(
                    modifier = Modifier.padding(24.dp)
                ) {
                    if (title != null) {
                        ProvideTextStyle(MaterialTheme.typography.headlineSmall) {
                            CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
                                title()
                            }
                        }
                    }
                    if (text != null) {
                        ProvideTextStyle(MaterialTheme.typography.bodyMedium) {
                            CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurfaceVariant) {
                                Column(modifier = Modifier.padding(top = if (title == null) 0.dp else 16.dp)) {
                                    text()
                                }
                            }
                        }
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 24.dp),
                        horizontalArrangement = Arrangement.End
                    ) {
                        if (dismissButton != null) {
                            dismissButton()
                        }
                        confirmButton()
                    }
                }
            }
        }
        return
    }

    AlertDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = confirmButton,
        title = title,
        text = text,
        dismissButton = dismissButton,
        containerColor = MaterialTheme.colorScheme.surface,
        titleContentColor = MaterialTheme.colorScheme.onSurface,
        textContentColor = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
fun ZToolDialogSurface(
    content: @Composable () -> Unit
) {
    if (LocalZToolThemeSpec.current.style == FrontendStyle.Miuix) {
        MiuixSurface(
            color = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface,
            content = content
        )
        return
    }

    Surface(
        color = MaterialTheme.colorScheme.surface,
        content = content
    )
}
