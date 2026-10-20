package com.qimian233.ztool.ui.firstrun

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.qimian233.ztool.R
import com.qimian233.ztool.ui.components.ZToolCard
import com.qimian233.ztool.ui.theme.LocalZToolColorScheme

/** First-run page listing the steps that prepare a complete bug report. */
@Composable
internal fun FirstrunReportPage(
    isLastPage: Boolean,
    countdownRemaining: Int,
    countdownFinished: Boolean,
    pageScrollState: ScrollState,
    onNext: (Offset) -> Unit,
    onBack: (Offset) -> Unit
) {
    val steps = listOf(
        R.string.page_firstrun_report_step1,
        R.string.page_firstrun_report_step2,
        R.string.page_firstrun_report_step3,
        R.string.page_firstrun_report_step4,
        R.string.page_firstrun_report_step5,
        R.string.page_firstrun_report_step6
    )
    val nextLabel = stringResource(
        if (isLastPage) R.string.page_firstrun_agreement_confirm else R.string.page_firstrun_next_step
    )
    val countdownActive = !countdownFinished

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(pageScrollState)
                .padding(bottom = 88.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            PageHeader(
                title = stringResource(R.string.page_firstrun_report_title),
                subtitle = stringResource(R.string.page_firstrun_report_subtitle)
            )

            Text(
                text = stringResource(R.string.page_firstrun_report_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = LocalZToolColorScheme.current.onSurfaceVariant
            )

            ZToolCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    steps.forEachIndexed { index, step ->
                        ReportStep(index = index + 1, text = stringResource(step))
                    }
                }
            }
        }

        BottomActionBar(
            modifier = Modifier.align(Alignment.BottomCenter),
            nextText = if (countdownActive) {
                stringResource(
                    R.string.page_firstrun_customized_confirm_with_countdown,
                    nextLabel,
                    countdownRemaining
                )
            } else {
                nextLabel
            },
            nextEnabled = !countdownActive,
            onNext = onNext,
            onDisagree = onBack,
            negativeText = stringResource(R.string.page_firstrun_previous),
            negativeTonal = true
        )
    }
}

@Composable
private fun ReportStep(index: Int, text: String) {
    Row(verticalAlignment = Alignment.Top) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(CircleShape)
                .background(LocalZToolColorScheme.current.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = index.toString(),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = LocalZToolColorScheme.current.onPrimaryContainer
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = text,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium
        )
    }
}
