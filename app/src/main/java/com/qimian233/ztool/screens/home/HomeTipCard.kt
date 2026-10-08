package com.qimian233.ztool.screens.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.qimian233.ztool.data.home.HomeTip
import com.qimian233.ztool.ui.components.ZToolCard
import com.qimian233.ztool.ui.theme.LocalZToolColorScheme

/**
 * The rotating tip card, placed between the module status card and the system info list.
 *
 * It is not dismissible: an ordinary tip is gone on the next launch anyway, and the hot reload
 * variant exists to tell the user that the hooks still running in other processes are stale, which
 * is exactly the message a dismiss button would let them throw away unread.
 *
 * [HomeTip.hotReloadWarning] drives both the palette and the icon — the warning reuses the error
 * container the non-ZUXOS and dev-build cards already use, so it reads as a warning at a glance
 * rather than as another piece of flavour text.
 */
@Composable
internal fun TipCard(tip: HomeTip) {
    val colors = LocalZToolColorScheme.current
    val containerColor: Color
    val contentColor: Color
    if (tip.hotReloadWarning) {
        containerColor = colors.errorContainer
        contentColor = colors.onErrorContainer
    } else {
        containerColor = colors.secondaryContainer
        contentColor = colors.onSecondaryContainer
    }

    ZToolCard(
        modifier = Modifier
            .fillMaxWidth()
            .homeCardGutter()
            .cardPressScale()
            .clickable(onClick = {}),
        containerColor = containerColor
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (tip.hotReloadWarning) Icons.Rounded.Warning else Icons.Rounded.Lightbulb,
                    contentDescription = null,
                    tint = contentColor
                )
                Spacer(modifier = Modifier.width(16.dp))
                Text(
                    text = stringResource(tip.messageRes),
                    style = MaterialTheme.typography.bodyMedium,
                    color = contentColor
                )
            }
        }
    }
}
