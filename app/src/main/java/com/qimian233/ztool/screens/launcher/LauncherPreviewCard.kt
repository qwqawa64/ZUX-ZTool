package com.qimian233.ztool.screens.launcher

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.qimian233.ztool.R
import com.qimian233.ztool.ui.components.ZToolCard
import com.qimian233.ztool.ui.theme.LocalZToolColorScheme
import com.qimian233.ztool.viewmodel.LauncherPreviewConfig

/** Tallest mock frame; the device aspect ratio is kept below this cap. */
private val MaxPreviewHeight = 320.dp
private val PreviewCornerRadius = 20.dp

/** Dead space per side when the wide-grid hook is off, as a fraction of screen width. */
private const val DefaultSideInsetFraction = 0.11f

/** Icon diameter as a fraction of the smaller cell side, before the icon-scale factor. */
private const val IconDiameterFraction = 0.55f
private const val UnlabelledIconDiameterFraction = 0.72f
private const val MaxIconDiameterFraction = 0.8f

/** The launcher's "app updated" dot is blue by definition, regardless of the theme. */
private val UpdateDotBlue = Color(0xFF3B82F6)

private const val DockIconCount = 5
private const val PageDotCount = 3

/**
 * Top card of the launcher settings page: a graphical preview of the current desktop
 * style driven by [config].
 */
@Composable
fun LauncherPreviewCard(config: LauncherPreviewConfig, modifier: Modifier = Modifier) {
    val colors = LocalZToolColorScheme.current
    ZToolCard(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 20.dp)
        ) {
            Text(
                text = stringResource(R.string.launcher_preview_title),
                style = MaterialTheme.typography.titleSmall,
                color = colors.onSurface
            )
            Spacer(modifier = Modifier.height(16.dp))
            LauncherScreenPreview(config = config, modifier = Modifier.fillMaxWidth())
        }
    }
}

/**
 * Draws the desktop mock. Geometry is proportional only - the workspace is split into
 * [LauncherPreviewConfig.columns] x [LauncherPreviewConfig.rows] equal cells - so no
 * launcher-side pixel value is required.
 */
@Composable
fun LauncherScreenPreview(config: LauncherPreviewConfig, modifier: Modifier = Modifier) {
    val colors = LocalZToolColorScheme.current
    val configuration = LocalConfiguration.current
    val screenWidth = configuration.screenWidthDp.toFloat().coerceAtLeast(1f)
    val screenHeight = configuration.screenHeightDp.toFloat().coerceAtLeast(1f)
    val frameAspect = screenWidth / screenHeight

    BoxWithConstraints(modifier = modifier, contentAlignment = Alignment.Center) {
        val frameHeight = minOf(MaxPreviewHeight, maxWidth / frameAspect)
        val frameWidth = frameHeight * frameAspect
        val shape = RoundedCornerShape(PreviewCornerRadius)
        Column(
            modifier = Modifier
                .width(frameWidth)
                .height(frameHeight)
                .clip(shape)
                .background(colors.surfaceContainerHigh)
                .border(1.dp, colors.outlineVariant, shape)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.82f)
                        .height(14.dp)
                        .clip(RoundedCornerShape(7.dp))
                        .background(colors.onSurfaceVariant.copy(alpha = 0.16f))
                )
            }
            WorkspaceGrid(
                config = config,
                frameWidth = frameWidth,
                screenWidth = screenWidth,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(6f)
            )
            if (config.dockVisible) {
                DockRow(
                    frameWidth = frameWidth,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1.1f)
                )
            }
            PageDots(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 10.dp)
            )
        }
    }
}

@Composable
private fun WorkspaceGrid(
    config: LauncherPreviewConfig,
    frameWidth: Dp,
    screenWidth: Float,
    modifier: Modifier = Modifier
) {
    val colors = LocalZToolColorScheme.current
    BoxWithConstraints(modifier = modifier, contentAlignment = Alignment.Center) {
        val columns = config.columns
        val rows = config.rows
        val cellHeight = maxHeight / rows
        // Square mode mirrors the wide-grid hook: widen the side padding until the cells
        // are square, clamped at 0 when the columns no longer fit.
        val horizontalPadding = when {
            config.squareCells -> maxOf(0.dp, (maxWidth - cellHeight * columns) / 2)
            config.wideGrid -> frameWidth * (config.wideGridSideInset / screenWidth)
            else -> frameWidth * DefaultSideInsetFraction
        }
        val cellWidth = (maxWidth - horizontalPadding * 2) / columns
        val cellShortSide = minOf(cellWidth, cellHeight)
        val showLabel = !config.noLabel
        val iconSize = (
            cellShortSide *
                (if (showLabel) IconDiameterFraction else UnlabelledIconDiameterFraction) *
                config.iconScale
            ).coerceAtMost(cellShortSide * MaxIconDiameterFraction)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = horizontalPadding)
        ) {
            repeat(rows) { row ->
                Row(modifier = Modifier.fillMaxWidth().weight(1f)) {
                    repeat(columns) { column ->
                        Box(
                            modifier = Modifier.fillMaxHeight().weight(1f),
                            contentAlignment = Alignment.Center
                        ) {
                            PreviewAppIcon(
                                iconSize = iconSize,
                                cellHeight = cellHeight,
                                showLabel = showLabel,
                                showDot = config.bluePointVisible &&
                                    (row * columns + column) % 3 == 0,
                                colors = colors
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PreviewAppIcon(
    iconSize: Dp,
    cellHeight: Dp,
    showLabel: Boolean,
    showDot: Boolean,
    colors: ColorScheme
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(contentAlignment = Alignment.TopEnd) {
            Box(
                modifier = Modifier
                    .size(iconSize)
                    .clip(RoundedCornerShape(iconSize * 0.3f))
                    .background(colors.primary.copy(alpha = 0.85f))
            )
            if (showDot) {
                Box(
                    modifier = Modifier
                        .size(iconSize * 0.3f)
                        .clip(CircleShape)
                        .background(UpdateDotBlue)
                )
            }
        }
        if (showLabel) {
            Spacer(modifier = Modifier.height(cellHeight * 0.05f))
            Box(
                modifier = Modifier
                    .width(iconSize * 0.9f)
                    .height((cellHeight * 0.08f).coerceIn(1.5.dp, 4.dp))
                    .clip(RoundedCornerShape(2.dp))
                    .background(colors.onSurfaceVariant.copy(alpha = 0.45f))
            )
        }
    }
}

@Composable
private fun DockRow(frameWidth: Dp, modifier: Modifier = Modifier) {
    val colors = LocalZToolColorScheme.current
    val dockIconSize = frameWidth * 0.15f
    Row(
        modifier = modifier.padding(horizontal = frameWidth * 0.06f),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(DockIconCount) {
            Box(
                modifier = Modifier.fillMaxHeight().weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(dockIconSize)
                        .clip(RoundedCornerShape(dockIconSize * 0.3f))
                        .background(colors.onSurfaceVariant.copy(alpha = 0.55f))
                )
            }
        }
    }
}

@Composable
private fun PageDots(modifier: Modifier = Modifier) {
    val colors = LocalZToolColorScheme.current
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(PageDotCount) { index ->
            Box(
                modifier = Modifier
                    .padding(horizontal = 2.dp)
                    .size(4.dp)
                    .clip(CircleShape)
                    .background(
                        if (index == PageDotCount / 2) {
                            colors.primary
                        } else {
                            colors.onSurfaceVariant.copy(alpha = 0.3f)
                        }
                    )
            )
        }
    }
}
