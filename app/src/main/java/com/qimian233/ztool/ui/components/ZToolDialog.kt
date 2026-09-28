package com.qimian233.ztool.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import com.qimian233.ztool.ui.theme.LocalZToolColorScheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.qimian233.ztool.ui.theme.FrontendStyle
import com.qimian233.ztool.ui.theme.LocalIsPlatformDialog
import com.qimian233.ztool.ui.theme.LocalZToolThemeSpec
import top.yukonga.miuix.kmp.basic.Surface as MiuixSurface

val DIALOG_BUTTON_HORIZONTAL_ARRANGEMENT = 0
val DIALOG_BUTTON_VERTICAL_ARRANGEMENT = 1

val LocalDialogButtonModifier = staticCompositionLocalOf<Modifier> { Modifier }

@Composable
fun ZToolDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    title: @Composable (() -> Unit)? = null,
    text: @Composable (() -> Unit)? = null,
    dismissButton: @Composable (() -> Unit)? = null,
    buttonArrangement: Int = DIALOG_BUTTON_HORIZONTAL_ARRANGEMENT
) {
    val isPlatformDialog = LocalIsPlatformDialog.current

    if (LocalZToolThemeSpec.current.style == FrontendStyle.Miuix) {
        val miuixContent = @Composable {
            Column(
                modifier = Modifier.padding(if (isPlatformDialog) 24.dp else 0.dp)
            ) {
                if (title != null) {
                    ProvideTextStyle(MaterialTheme.typography.headlineSmall) {
                        CompositionLocalProvider(LocalContentColor provides LocalZToolColorScheme.current.onSurface) {
                            title()
                        }
                    }
                }
                if (text != null) {
                    ProvideTextStyle(MaterialTheme.typography.bodyMedium) {
                        CompositionLocalProvider(LocalContentColor provides LocalZToolColorScheme.current.onSurfaceVariant) {
                            Column(modifier = Modifier.padding(top = if (title == null) 0.dp else 16.dp)) {
                                text()
                            }
                        }
                    }
                }
                if (buttonArrangement == DIALOG_BUTTON_HORIZONTAL_ARRANGEMENT) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 24.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        if (dismissButton != null) {
                            Box(Modifier.weight(1f)) {
                                CompositionLocalProvider(
                                    LocalDialogButtonModifier provides Modifier.fillMaxWidth()
                                ) {
                                    dismissButton()
                                }
                            }
                        }
                        Box(Modifier.weight(1f)) {
                            CompositionLocalProvider(
                                LocalDialogButtonModifier provides Modifier.fillMaxWidth()
                            ) {
                                confirmButton()
                            }
                        }
                    }
                } else {
                    Column (
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Box(Modifier.fillMaxWidth()) {
                            CompositionLocalProvider(
                                LocalDialogButtonModifier provides Modifier.fillMaxWidth()
                            ) {
                                confirmButton()
                            }
                        }
                        if (dismissButton != null) {
                            Box(Modifier.fillMaxWidth()) {
                                CompositionLocalProvider(
                                    LocalDialogButtonModifier provides Modifier.fillMaxWidth()
                                ) {
                                    dismissButton()
                                }
                            }
                        }
                    }
                }
            }
        }

        if (isPlatformDialog) {
            MiuixSurface(
                color = LocalZToolColorScheme.current.surfaceContainerHigh,
                shape = RoundedCornerShape(24.dp)
            ) {
                miuixContent()
            }
        } else {
            // Miuix's OverlayDialog caps content width at DialogDefaults.MaxWidth (420.dp)
            // and measures its Column children against the remaining height, so tall content
            // compresses the trailing buttons. Render the same surface in a platform window
            // with our own width cap and a scrollable body instead.
            androidx.compose.ui.window.Dialog(
                onDismissRequest = onDismissRequest,
                properties = androidx.compose.ui.window.DialogProperties(
                    usePlatformDefaultWidth = false
                )
            ) {
                MiuixSurface(
                    modifier = Modifier
                        .widthIn(max = 560.dp)
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    color = LocalZToolColorScheme.current.surfaceContainerHigh,
                    shape = RoundedCornerShape(24.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .verticalScroll(rememberScrollState())
                            .padding(24.dp)
                    ) {
                        miuixContent()
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
        containerColor = LocalZToolColorScheme.current.surface,
        titleContentColor = LocalZToolColorScheme.current.onSurface,
        textContentColor = LocalZToolColorScheme.current.onSurfaceVariant
    )
}

@Suppress("unused")
@Composable
fun ZToolDialogSurface(
    content: @Composable () -> Unit
) {
    if (LocalZToolThemeSpec.current.style == FrontendStyle.Miuix) {
        MiuixSurface(
            color = LocalZToolColorScheme.current.surface,
            contentColor = LocalZToolColorScheme.current.onSurface,
            content = content
        )
        return
    }

    Surface(
        color = LocalZToolColorScheme.current.surface,
        content = content
    )
}
