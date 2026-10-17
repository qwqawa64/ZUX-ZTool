package com.qimian233.ztool.ui.components

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.qimian233.ztool.R
import androidx.compose.ui.res.stringResource
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds

/**
 * Confirmation dialog for a full system reboot, with a short countdown so a wrapped
 * click cannot reboot the device by accident.
 */
@Composable
fun ZToolRestartSystemDialog(
    title: String,
    message: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    var countdown by remember { mutableIntStateOf(3) }

    LaunchedEffect(Unit) {
        while (countdown > 0) {
            delay(1000.milliseconds)
            countdown -= 1
        }
    }

    ZToolDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            ZToolTextButton(
                enabled = countdown == 0,
                onClick = onConfirm,
                text = if (countdown > 0) {
                    stringResource(R.string.common_confirm) + " ($countdown)"
                } else {
                    stringResource(R.string.common_confirm)
                }
            )
        },
        dismissButton = {
            ZToolTextButton(
                onClick = onDismiss,
                text = stringResource(R.string.common_restart_no),
                isPrimary = false
            )
        }
    )
}
