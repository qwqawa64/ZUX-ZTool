package com.qimian233.ztool.ui.firstrun

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qimian233.ztool.ui.components.ZToolCard
import com.qimian233.ztool.ui.components.ZToolMarkdownText
import com.qimian233.ztool.ui.theme.LocalZToolColorScheme

/**
 * Scrollable card rendering the user agreement markdown. Shared by the
 * first-run agreement page and the About screen's user agreement entry.
 */
@Composable
fun AgreementContentCard(
    markdownText: String,
    modifier: Modifier = Modifier,
    scrollState: ScrollState = rememberScrollState(),
    containerHeight: Dp = 400.dp,
    containerColor: Color = LocalZToolColorScheme.current.surfaceContainerHigh
) {
    ZToolCard(
        modifier = modifier.fillMaxWidth(),
        containerColor = containerColor
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .height(containerHeight)
                .verticalScroll(scrollState)
                .padding(16.dp)
        ) {
            ZToolMarkdownText(
                markdown = markdownText,
                style = MaterialTheme.typography.bodyMedium,
                color = LocalZToolColorScheme.current.onSurfaceVariant,
                fontSize = 18.sp
            )
        }
    }
}
