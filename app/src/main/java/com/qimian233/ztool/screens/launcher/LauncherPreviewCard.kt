package com.qimian233.ztool.screens.launcher

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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

// Ratios below were measured on a ZUI tablet screenshot (2590x1619) of the real desktop.

/** Status-bar band the desktop keeps free above the wallpaper: 160/1619. */
private const val TopBandFraction = 0.10f

/** Dock pill height 195/1619 and its gap to the screen bottom 39/1619. */
private const val DockBandFraction = 0.12f
private const val DockBottomGapFraction = 0.02f

/** Side dead space with the wide-grid hook off: 32/2590 on a tablet, far more on phones. */
private const val DefaultSideInsetFractionTablet = 0.012f
private const val DefaultSideInsetFractionPhone = 0.11f
private const val TabletWidthDp = 600f

/** Desktop icon diameter against the cell's short side: 120/188. */
private const val IconToCellFraction = 0.62f
private const val MaxIconToCellFraction = 0.8f

/** Icon + label block height against the row pitch (166/188), the square mode target. */
private const val SquareCellContentFraction = 0.88f

/** Dock pill width against its content: gap and padding are half an icon each. */
private const val DockGapToIconFraction = 0.5f

/** Slot that carries the separator between the fixed icons and the app icons. */
private const val DockSeparatorIndex = 5

/** The launcher's "app updated" dot is blue by definition, regardless of the theme. */
private val UpdateDotBlue = Color(0xFF3B82F6)

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

        val topBand = frameHeight * TopBandFraction
        val dockBand = if (config.dockVisible) frameHeight * DockBandFraction else 0.dp
        val dockBottomGap = if (config.dockVisible) frameHeight * DockBottomGapFraction else 0.dp
        val workspaceHeight = frameHeight - topBand - dockBand - dockBottomGap
        val cellHeight = workspaceHeight / config.rows
        val cellWidth = (frameWidth - sidePadding(config, frameWidth, screenWidth, cellHeight) * 2) /
            config.columns
        val cellShortSide = minOf(cellWidth, cellHeight)
        val iconSize = (cellShortSide * IconToCellFraction * config.iconScale)
            .coerceAtMost(cellShortSide * MaxIconToCellFraction)

        Column(
            modifier = Modifier
                .width(frameWidth)
                .height(frameHeight)
                .clip(shape)
                .background(colors.surfaceContainerHigh)
                .border(1.dp, colors.outlineVariant, shape)
        ) {
            PageDots(modifier = Modifier.fillMaxWidth().height(topBand))
            WorkspaceGrid(config = config, cellWidth = cellWidth, cellHeight = cellHeight, iconSize = iconSize)
            if (config.dockVisible) {
                Box(
                    modifier = Modifier.fillMaxWidth().height(dockBand),
                    contentAlignment = Alignment.Center
                ) {
                    DockRow(
                        colors = colors,
                        iconSize = iconSize,
                        frameWidth = frameWidth,
                        dockIconCount = config.dockIconCount
                    )
                }
                Spacer(modifier = Modifier.height(dockBottomGap))
            }
        }
    }
}

/**
 * Side inset of the workspace. Square mode mirrors the wide-grid hook: it pads until a
 * cell's content width matches its content height, clamped at 0 when columns no longer fit.
 */
private fun sidePadding(
    config: LauncherPreviewConfig,
    frameWidth: Dp,
    screenWidth: Float,
    cellHeight: Dp
): Dp = when {
    config.squareCells -> maxOf(
        0.dp,
        (frameWidth - cellHeight * SquareCellContentFraction * config.columns) / 2
    )

    config.wideGrid -> frameWidth * (config.wideGridSideInset / screenWidth)
    else -> frameWidth * if (screenWidth >= TabletWidthDp) {
        DefaultSideInsetFractionTablet
    } else {
        DefaultSideInsetFractionPhone
    }
}

@Composable
private fun WorkspaceGrid(
    config: LauncherPreviewConfig,
    cellWidth: Dp,
    cellHeight: Dp,
    iconSize: Dp
) {
    val colors = LocalZToolColorScheme.current
    val showLabel = !config.noLabel
    Column(modifier = Modifier.fillMaxWidth().height(cellHeight * config.rows)) {
        repeat(config.rows) { row ->
            Row(modifier = Modifier.fillMaxWidth().height(cellHeight)) {
                repeat(config.columns) { column ->
                    Box(
                        modifier = Modifier.width(cellWidth).height(cellHeight),
                        contentAlignment = Alignment.Center
                    ) {
                        PreviewAppIcon(
                            iconSize = iconSize,
                            cellHeight = cellHeight,
                            showLabel = showLabel,
                            showDot = config.bluePointVisible && (row * config.columns + column) % 3 == 0,
                            colors = colors
                        )
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
                    .clip(RoundedCornerShape(iconSize * 0.26f))
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
                    .height((cellHeight * 0.12f).coerceIn(1.5.dp, 5.dp))
                    .clip(RoundedCornerShape(2.dp))
                    .background(colors.onSurfaceVariant.copy(alpha = 0.45f))
            )
        }
    }
}

/** Centered, content-hugging dock pill, matching the launcher's own dock layout. */
@Composable
private fun DockRow(
    colors: ColorScheme,
    iconSize: Dp,
    frameWidth: Dp,
    dockIconCount: Int
) {
    val gap = iconSize * DockGapToIconFraction
    val fittingIconSize = minOf(
        iconSize,
        frameWidth / (dockIconCount * (1f + DockGapToIconFraction) + DockGapToIconFraction)
    )
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(fittingIconSize * 0.9f))
            .background(colors.onSurfaceVariant.copy(alpha = 0.22f))
            .padding(horizontal = gap, vertical = gap * 0.6f),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(gap)
    ) {
        repeat(dockIconCount) { index ->
            if (index == DockSeparatorIndex) {
                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .height(fittingIconSize * 0.8f)
                        .background(colors.onSurfaceVariant.copy(alpha = 0.5f))
                )
            }
            Box(
                modifier = Modifier
                    .size(fittingIconSize)
                    .clip(RoundedCornerShape(fittingIconSize * 0.26f))
                    .background(colors.onSurfaceVariant.copy(alpha = 0.55f))
            )
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
