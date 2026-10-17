package com.qimian233.ztool.ui.firstrun

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.qimian233.ztool.R
import com.qimian233.ztool.ui.components.ZToolButton
import com.qimian233.ztool.ui.theme.LocalZToolColorScheme

/** Workaround walkthrough for Usage Access blocked by restricted settings (issue #24). */
@Composable
internal fun FirstrunUsageRestrictedDialog(onDismiss: () -> Unit) {
    val steps = listOf(
        UsageRestrictedStep(
            title = R.string.page_firstrun_usage_restricted_step1_title,
            summary = R.string.page_firstrun_usage_restricted_step1_summary,
            image = R.drawable.firstrun_usage_restricted_step_1
        ),
        UsageRestrictedStep(
            title = R.string.page_firstrun_usage_restricted_step2_title,
            summary = R.string.page_firstrun_usage_restricted_step2_summary,
            image = R.drawable.firstrun_usage_restricted_step_2
        ),
        UsageRestrictedStep(
            title = R.string.page_firstrun_usage_restricted_step3_title,
            summary = R.string.page_firstrun_usage_restricted_step3_summary,
            image = R.drawable.firstrun_usage_restricted_step_3
        )
    )

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp, vertical = 24.dp),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                modifier = Modifier.widthIn(max = 560.dp),
                shape = RoundedCornerShape(24.dp),
                color = LocalZToolColorScheme.current.surfaceContainerHigh,
                contentColor = LocalZToolColorScheme.current.onSurface
            ) {
                Column(
                    modifier = Modifier
                        .verticalScroll(rememberScrollState())
                        .padding(24.dp)
                ) {
                    Text(
                        text = stringResource(R.string.page_firstrun_usage_restricted_title),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = stringResource(R.string.page_firstrun_usage_restricted_summary),
                        style = MaterialTheme.typography.bodyMedium,
                        color = LocalZToolColorScheme.current.onSurfaceVariant
                    )

                    steps.forEach { step ->
                        Spacer(modifier = Modifier.height(20.dp))
                        Text(
                            text = stringResource(step.title),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = stringResource(step.summary),
                            style = MaterialTheme.typography.bodyMedium,
                            color = LocalZToolColorScheme.current.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        StepImage(step.image)
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = stringResource(R.string.page_firstrun_usage_restricted_refresh_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = LocalZToolColorScheme.current.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(20.dp))
                    ZToolButton(
                        onClick = onDismiss,
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Text(stringResource(R.string.page_firstrun_usage_restricted_confirm_button))
                    }
                }
            }
        }
    }
}

@Composable
private fun StepImage(@DrawableRes image: Int) {
    Image(
        bitmap = ImageBitmap.imageResource(image),
        contentDescription = null,
        contentScale = ContentScale.FillWidth,
        modifier = Modifier
            .fillMaxWidth()
            // Packaged screenshots are 2590x1619; keep that ratio while scaling.
            .aspectRatio(2590f / 1619f)
            .clip(RoundedCornerShape(12.dp))
    )
}

private data class UsageRestrictedStep(
    @param:StringRes val title: Int,
    @param:StringRes val summary: Int,
    @param:DrawableRes val image: Int
)
