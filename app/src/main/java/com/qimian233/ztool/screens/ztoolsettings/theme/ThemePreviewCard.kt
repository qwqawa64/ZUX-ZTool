package com.qimian233.ztool.screens.ztoolsettings.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.qimian233.ztool.R
import com.qimian233.ztool.ui.components.ZToolCard
import com.qimian233.ztool.ui.theme.FrontendStyle
import com.qimian233.ztool.ui.theme.LocalZToolColorScheme
import com.qimian233.ztool.ui.theme.LocalZToolThemeSpec
import top.yukonga.miuix.kmp.theme.MiuixTheme

private val SpecimenPadding = 16.dp

/** Landscape 16:9 ratio for both frames. */
private const val FrameAspectRatio = 16f / 10f

/** Widest a single frame gets before the pair is centered instead of stretched. */
private val MaxFrameWidth = 400.dp
private val FrameGap = 24.dp

private const val InfoListRowCount = 4
private const val FeatureGridRows = 3
private const val FeatureGridColumns = 2

/**
 * Appearance specimen: two side-by-side tablet frames, one showing the home page's
 * module-status card and one a features page. Surfaces are drawn from the live
 * palette; bars stand in for text so the specimen shows color, not UI copy.
 */
@Composable
fun ThemePreviewCard(modifier: Modifier = Modifier) {
    ZToolCard(
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(previewPageBackground())
                .padding(SpecimenPadding)
        ) {
            Text(
                text = stringResource(R.string.page_settings_theme_preview_title),
                style = MaterialTheme.typography.titleSmall,
                color = LocalZToolColorScheme.current.onSurface
            )
            Spacer(modifier = Modifier.height(16.dp))

            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                val halfWidth = (maxWidth - FrameGap) / 2
                val frameWidth = minOf(halfWidth, MaxFrameWidth)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(
                        FrameGap,
                        Alignment.CenterHorizontally
                    ),
                    verticalAlignment = Alignment.Top,
                ) {
                    PreviewFrameColumn(
                        label = stringResource(R.string.page_settings_theme_preview_home_label),
                        modifier = Modifier.width(frameWidth)
                    ) {
                        PreviewHomePage()
                    }
                    PreviewFrameColumn(
                        label = stringResource(R.string.page_settings_theme_preview_features_label),
                        modifier = Modifier.width(frameWidth)
                    ) {
                        PreviewFeaturePage()
                    }
                }
            }
        }
    }
}

/** Page background of the current front end; the specimen screens sit on it. */
@Composable
internal fun previewPageBackground(): Color {
    return if (LocalZToolThemeSpec.current.style == FrontendStyle.Miuix) {
        MiuixTheme.colorScheme.background
    } else {
        LocalZToolColorScheme.current.background
    }
}

@Composable
private fun PreviewFrameColumn(
    label: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Column(modifier = modifier) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = LocalZToolColorScheme.current.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(modifier = Modifier.height(8.dp))
        PreviewTabletFrame(content = content)
    }
}

/** A beveled tablet frame; every length scales with the frame width it is given. */
@Composable
private fun PreviewTabletFrame(content: @Composable () -> Unit) {
    val colors = LocalZToolColorScheme.current
    BoxWithConstraints {
        val frameWidth = maxWidth
        val frameShape = RoundedCornerShape(frameWidth * 0.06f)
        val screenShape = RoundedCornerShape(frameWidth * 0.045f)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(FrameAspectRatio)
                .clip(frameShape)
                .background(colors.surfaceContainerHighest)
                .border(1.dp, colors.outlineVariant, frameShape)
                .padding(frameWidth * 0.018f)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(screenShape)
                    .background(previewPageBackground())
            ) {
                content()
            }
        }
    }
}

@Composable
private fun PreviewHomePage() {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val width = maxWidth
        val contentPadding = width * 0.045f
        Column(modifier = Modifier.fillMaxSize()) {
            PreviewTopBar(width = width)
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(contentPadding),
                verticalArrangement = Arrangement.spacedBy(contentPadding * 0.7f)
            ) {
                PreviewStatusCard(modifier = Modifier.fillMaxWidth())
                PreviewInfoList(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                )
            }
        }
    }
}

@Composable
private fun PreviewFeaturePage() {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val width = maxWidth
        val contentPadding = width * 0.045f
        val gap = width * 0.03f
        Column(modifier = Modifier.fillMaxSize()) {
            PreviewTopBar(width = width)
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(contentPadding),
                verticalArrangement = Arrangement.spacedBy(gap)
            ) {
                repeat(FeatureGridRows) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(gap)
                    ) {
                        repeat(FeatureGridColumns) {
                            PreviewFeatureTile(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PreviewTopBar(width: Dp) {
    val colors = LocalZToolColorScheme.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(width * 0.13f)
            .background(colors.surface)
            .padding(horizontal = width * 0.04f),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(width * 0.045f)
                .clip(CircleShape)
                .background(colors.primary)
        )
        Spacer(modifier = Modifier.width(width * 0.03f))
        PreviewBar(
            barWidth = width * 0.26f,
            barHeight = width * 0.022f,
            color = colors.onSurface
        )
        Spacer(modifier = Modifier.weight(1f))
        repeat(2) {
            Box(
                modifier = Modifier
                    .size(width * 0.018f)
                    .clip(CircleShape)
                    .background(colors.onSurfaceVariant)
            )
            Spacer(modifier = Modifier.width(width * 0.025f))
        }
    }
}

/** Active module status: primary container with an on-container title stack. */
@Composable
private fun PreviewStatusCard(modifier: Modifier = Modifier) {
    val colors = LocalZToolColorScheme.current
    BoxWithConstraints(modifier = modifier) {
        val width = maxWidth
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(width * 0.035f))
                .background(colors.primaryContainer)
                .padding(horizontal = width * 0.045f, vertical = width * 0.04f),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(width * 0.02f)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    PreviewBar(
                        barWidth = width * 0.34f,
                        barHeight = width * 0.03f,
                        color = colors.onPrimaryContainer
                    )
                    Spacer(modifier = Modifier.width(width * 0.02f))
                    PreviewBar(
                        barWidth = width * 0.12f,
                        barHeight = width * 0.026f,
                        color = colors.onPrimaryContainer.copy(alpha = 0.2f)
                    )
                }
                PreviewBar(
                    barWidth = width * 0.26f,
                    barHeight = width * 0.02f,
                    color = colors.onPrimaryContainer.copy(alpha = 0.65f)
                )
            }
            Spacer(modifier = Modifier.width(width * 0.04f))
            Box(
                modifier = Modifier
                    .size(width * 0.075f)
                    .clip(CircleShape)
                    .background(colors.primary)
            )
        }
    }
}

/** Home system-info rows: one surface container, icon plates and value bars. */
@Composable
private fun PreviewInfoList(modifier: Modifier = Modifier) {
    val colors = LocalZToolColorScheme.current
    BoxWithConstraints(modifier = modifier) {
        val width = maxWidth
        Column(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(width * 0.035f))
                .background(colors.surfaceContainer)
                .padding(horizontal = width * 0.04f)
        ) {
            repeat(InfoListRowCount) { index ->
                if (index > 0) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(colors.outlineVariant.copy(alpha = 0.6f))
                    )
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(width * 0.032f)
                            .clip(RoundedCornerShape(width * 0.01f))
                            .background(colors.onSurfaceVariant.copy(alpha = 0.6f))
                    )
                    Spacer(modifier = Modifier.width(width * 0.03f))
                    PreviewBar(
                        barWidth = width * 0.24f,
                        barHeight = width * 0.02f,
                        color = colors.onSurface
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    PreviewBar(
                        barWidth = width * 0.16f,
                        barHeight = width * 0.016f,
                        color = colors.onSurfaceVariant
                    )
                }
            }
        }
    }
}

/** Features-grid tile: icon plate, two text bars and a chevron dot. */
@Composable
private fun PreviewFeatureTile(modifier: Modifier = Modifier) {
    val colors = LocalZToolColorScheme.current
    BoxWithConstraints(modifier = modifier) {
        val width = maxWidth
        val height = maxHeight
        Row(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(width * 0.09f))
                .background(colors.surfaceContainer)
                .padding(horizontal = width * 0.07f, vertical = height * 0.14f),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val plateSize = height * 0.6f
            Box(
                modifier = Modifier
                    .size(plateSize)
                    .clip(RoundedCornerShape(plateSize * 0.3f))
                    .background(colors.primaryContainer)
            )
            Spacer(modifier = Modifier.width(width * 0.06f))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(height * 0.1f)
            ) {
                PreviewBar(
                    barWidth = width * 0.4f,
                    barHeight = height * 0.13f,
                    color = colors.onSurface
                )
                PreviewBar(
                    barWidth = width * 0.55f,
                    barHeight = height * 0.09f,
                    color = colors.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.width(width * 0.04f))
            Box(
                modifier = Modifier
                    .size(height * 0.13f)
                    .clip(CircleShape)
                    .background(colors.onSurfaceVariant.copy(alpha = 0.55f))
            )
        }
    }
}

/** A text-shaped placeholder used inside the specimen surfaces. */
@Composable
private fun PreviewBar(
    barWidth: Dp,
    barHeight: Dp,
    color: Color
) {
    Box(
        modifier = Modifier
            .width(barWidth)
            .height(barHeight)
            .clip(RoundedCornerShape(barHeight / 2f))
            .background(color)
    )
}
