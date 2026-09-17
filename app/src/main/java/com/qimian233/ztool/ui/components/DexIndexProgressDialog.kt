package com.qimian233.ztool.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.qimian233.ztool.R
import com.qimian233.ztool.dexindex.base.DexIndexProgress

/**
 * DexKit index progress dialog: scope-level progress bar with a
 * "x / total scopes" text. Shared by the home-entry staleness check
 * (non-first-run refresh) and the Settings manual refresh entry.
 */
@Composable
fun DexIndexProgressDialog(
    progress: DexIndexProgress,
    modifier: Modifier = Modifier,
) {
    val fraction = if (progress.total > 0) {
        progress.current.toFloat() / progress.total
    } else {
        0f
    }

    ZToolDialog(
        onDismissRequest = {},
        confirmButton = {},
        text = {
            Column(
                modifier = modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp)
            ) {
                Text(
                    text = stringResource(R.string.common_dex_index_dialog_title),
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
                LinearProgressIndicator(
                    progress = { fraction.coerceIn(0f, 1f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp)
                )
                Text(
                    text = stringResource(
                        R.string.common_dex_index_progress_text,
                        progress.current,
                        progress.total
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                )
            }
        }
    )
}
