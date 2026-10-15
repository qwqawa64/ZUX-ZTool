package com.qimian233.ztool.screens.systemui.controlcenter

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qimian233.ztool.R
import com.qimian233.ztool.ui.components.ZToolCard
import com.qimian233.ztool.ui.theme.LocalZToolColorScheme
import com.qimian233.ztool.viewmodel.ControlCenterPreviewConfig
import kotlinx.coroutines.launch

/** Tallest mock frame; both orientations keep the device aspect ratio below this cap. */
private val MaxPreviewHeight = 240.dp
private val PreviewCornerRadius = 16.dp

// Schematic proportions read off ZUI tablet screenshots (2000x3200 @400dpi). No SystemUI-side
// pixel value is resolved at runtime; only the tile radii keep their measured scale.

/** Panel side padding against the panel width: 24dp of a 640dp-wide portrait panel. */
private const val PanelPaddingFraction = 0.04f

/** Tile gap against the panel content width: 36/1478 measured in portrait. */
private const val TileGapFraction = 0.025f

/** Small tile height against its width, and the label bar under it. */
private const val TileHeightToWidthFraction = 0.92f
private const val TileLabelHeightFraction = 0.12f

/** Head-up tile height against its width: 205/723 portrait, 205/548 landscape. */
private const val HeadUpHeightPortrait = 0.28f
private const val HeadUpHeightLandscape = 0.37f

/** Small-tile rows: `quick_qs_panel_max_rows` is 2 by default and 3 in sw600dp-land. */
private const val PortraitTileRows = 2
private const val LandscapeTileRows = 3

/** Share of the frame the landscape control center occupies, i.e. the shade's right half. */
private const val LandscapePanelWidthFraction = 0.48f

/** Portrait width before the widening hook runs: the panel is centred on half the screen. */
private const val NativePortraitWidthPercent = 50

/**
 * SystemUI's own small/large-screen split, `sw720dp` (roughly a 10-inch tablet): it picks both
 * the tile count and the slider orientation forced below it.
 */
private const val LargeScreenShortEdgeDp = 720
private const val LargeScreenTileColumns = 5
private const val SmallScreenTileColumns = 4

/** Centre of the head-up glyph block, as a share of the tile width from its left edge. */
private const val HeadUpGlyphCenterFraction = 0.25f

/** Vertical slider bar: width against the tile, and its height against the tile. */
private const val VerticalSliderWidthFraction = 0.34f
private const val VerticalSliderHeightFraction = 0.72f

/** Schematic slider levels; the module only decides whether a label is drawn at all. */
private const val BrightnessSamplePercent = 32
private const val VolumeSamplePercent = 0

/** Notification placeholders drawn around the panel to place it inside the shade. */
private const val NotificationCardCount = 4

private const val LandscapePage = 0
private const val PortraitPage = 1
private const val PreviewPageCount = 2

/** Header glyph placeholders of the shade: power, tile count, edit and settings. */
private const val HeaderGlyphCount = 4

/** Legibility floors: a proportional label would be a few sp wide at this mock scale. */
private val DateTextSize = 9.sp

/**
 * Top card of the control-center settings page: a schematic of the shade in both
 * orientations, swiped horizontally between landscape and portrait.
 */
@Composable
fun ControlCenterPreviewCard(config: ControlCenterPreviewConfig, modifier: Modifier = Modifier) {
    val colors = LocalZToolColorScheme.current
    val pagerState = rememberPagerState(pageCount = { PreviewPageCount })
    val scope = rememberCoroutineScope()
    val configuration = LocalConfiguration.current
    val layout = mockLayout(
        config = config,
        shortEdgeDp = minOf(configuration.screenWidthDp, configuration.screenHeightDp)
    )

    ZToolCard(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 20.dp)
        ) {
            Text(
                text = stringResource(R.string.system_ui_control_center_preview_title),
                style = MaterialTheme.typography.titleSmall,
                color = colors.onSurface
            )
            Spacer(modifier = Modifier.height(16.dp))
            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(MaxPreviewHeight)
            ) { page ->
                ControlCenterScreenMock(
                    config = config,
                    layout = layout,
                    portrait = page == PortraitPage,
                    modifier = Modifier.fillMaxSize()
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            PreviewPageSelector(
                selectedPage = pagerState.currentPage,
                onSelect = { page -> scope.launch { pagerState.animateScrollToPage(page) } }
            )
            Spacer(modifier = Modifier.height(10.dp))
            PreviewAnnotations(
                config = config,
                layout = layout,
                portrait = pagerState.currentPage == PortraitPage
            )
        }
    }
}

/** Effective layout the mock draws: the hook's value while its switch is on, the host's otherwise. */
private class MockLayout(
    val portraitWidthPercent: Int,
    val portraitColumns: Int,
    val landscapeColumns: Int,
    val verticalSlider: Boolean,
    val detailIndicatorVisible: Boolean
)

private fun mockLayout(config: ControlCenterPreviewConfig, shortEdgeDp: Int): MockLayout {
    val largeScreen = shortEdgeDp >= LargeScreenShortEdgeDp
    val nativeColumns = if (largeScreen) LargeScreenTileColumns else SmallScreenTileColumns
    return MockLayout(
        portraitWidthPercent = if (config.portraitLayoutEnabled) {
            config.portraitWidthPercent
        } else {
            NativePortraitWidthPercent
        },
        portraitColumns = if (config.portraitLayoutEnabled) {
            config.portraitTileColumns
        } else {
            nativeColumns
        },
        landscapeColumns = nativeColumns,
        // SystemUI forces the vertical slider on every screen narrower than sw720dp.
        verticalSlider = if (config.sliderStyleEnabled) config.sliderStyleVertical else !largeScreen,
        detailIndicatorVisible = !config.detailIndicatorHidden
    )
}

@Composable
private fun PreviewPageSelector(selectedPage: Int, onSelect: (Int) -> Unit) {
    val colors = LocalZToolColorScheme.current
    val pages = listOf(
        LandscapePage to stringResource(R.string.system_ui_control_center_preview_landscape_label),
        PortraitPage to stringResource(R.string.system_ui_control_center_preview_portrait_label)
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        pages.forEachIndexed { index, (page, label) ->
            if (index > 0) Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = if (page == selectedPage) colors.primary else colors.onSurfaceVariant,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onSelect(page) }
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            )
        }
    }
}

/**
 * Value chips of the visible page. A chip is dimmed while the switch that owns it is off,
 * because the hook does not apply that value yet.
 */
@Composable
private fun PreviewAnnotations(
    config: ControlCenterPreviewConfig,
    layout: MockLayout,
    portrait: Boolean
) {
    val colors = LocalZToolColorScheme.current
    val sliderDirection = stringResource(
        if (layout.verticalSlider) {
            R.string.system_ui_control_center_slider_style_vertical
        } else {
            R.string.system_ui_control_center_slider_style_horizontal
        }
    )
    val chips = buildList {
        add(
            stringResource(
                R.string.system_ui_control_center_preview_date_label,
                config.dateSample
            ) to config.customDate
        )
        add(
            stringResource(
                R.string.system_ui_control_center_preview_tile_radius_label,
                config.tileCornerRadiusPx
            ) to config.cornerRadiusEnabled
        )
        add(
            stringResource(
                R.string.system_ui_control_center_preview_head_up_radius_label,
                config.headUpCornerRadiusPx
            ) to config.cornerRadiusEnabled
        )
        if (portrait) {
            add(
                stringResource(
                    R.string.system_ui_control_center_preview_panel_width_label,
                    layout.portraitWidthPercent
                ) to config.portraitLayoutEnabled
            )
            add(
                stringResource(
                    R.string.system_ui_control_center_preview_tile_columns_label,
                    layout.portraitColumns
                ) to config.portraitLayoutEnabled
            )
        } else {
            add(
                stringResource(R.string.system_ui_control_center_preview_brightness_label) to
                    config.brightnessPercentVisible
            )
            add(
                stringResource(R.string.system_ui_control_center_preview_volume_label) to
                    config.volumePercentVisible
            )
        }
        add(
            stringResource(
                R.string.system_ui_control_center_preview_slider_style_label,
                sliderDirection
            ) to config.sliderStyleEnabled
        )
        add(
            stringResource(R.string.system_ui_control_center_preview_detail_indicator_label) to
                config.detailIndicatorHidden
        )
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            chips.forEach { (text, active) ->
                Text(
                    text = text,
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.onSurfaceVariant.copy(alpha = if (active) 1f else 0.45f),
                    maxLines = 1,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(colors.onSurfaceVariant.copy(alpha = 0.08f))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }
        if (chips.any { !it.second }) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = stringResource(R.string.system_ui_control_center_preview_inactive_note),
                style = MaterialTheme.typography.labelSmall,
                color = colors.onSurfaceVariant.copy(alpha = 0.6f)
            )
        }
    }
}

/** Panel geometry in mock dp, solved from the mock frame width. */
private class MockPanel(
    val width: Dp,
    val padding: Dp,
    val gap: Dp,
    val tileWidth: Dp,
    val tileHeight: Dp,
    val headUpWidth: Dp,
    val headUpHeight: Dp,
    /** Device px to mock dp, so the hook's px corner radii keep their on-device proportions. */
    val pxToMock: Dp
)

private fun mockPanel(
    frameWidth: Dp,
    panelWidth: Dp,
    screenWidthDp: Float,
    density: Float,
    columns: Int,
    headUpHeightRatio: Float
): MockPanel {
    val padding = panelWidth * PanelPaddingFraction
    val contentWidth = (panelWidth - padding * 2).coerceAtLeast(1.dp)
    val gap = contentWidth * TileGapFraction
    val tileWidth = ((contentWidth - gap * (columns - 1)) / columns).coerceAtLeast(1.dp)
    val headUpWidth = ((contentWidth - gap) / 2).coerceAtLeast(1.dp)
    return MockPanel(
        width = panelWidth,
        padding = padding,
        gap = gap,
        tileWidth = tileWidth,
        tileHeight = tileWidth * TileHeightToWidthFraction,
        headUpWidth = headUpWidth,
        headUpHeight = (headUpWidth * headUpHeightRatio).coerceAtLeast(1.dp),
        pxToMock = (frameWidth.value / screenWidthDp / density).dp
    )
}

/**
 * Draws the whole screen: status row, the control-center panel with its tiles, and the
 * notification placeholders that place the panel inside the shade.
 */
@Composable
private fun ControlCenterScreenMock(
    config: ControlCenterPreviewConfig,
    layout: MockLayout,
    portrait: Boolean,
    modifier: Modifier = Modifier
) {
    val colors = LocalZToolColorScheme.current
    val density = LocalDensity.current.density
    val configuration = LocalConfiguration.current
    val longSide = maxOf(configuration.screenWidthDp, configuration.screenHeightDp)
        .toFloat().coerceAtLeast(1f)
    val shortSide = minOf(configuration.screenWidthDp, configuration.screenHeightDp)
        .toFloat().coerceAtLeast(1f)
    val aspect = if (portrait) shortSide / longSide else longSide / shortSide

    BoxWithConstraints(modifier = modifier, contentAlignment = Alignment.Center) {
        val frameHeight = minOf(maxHeight, maxWidth / aspect)
        val frameWidth = frameHeight * aspect
        val columns = if (portrait) {
            layout.portraitColumns.coerceIn(1, 10)
        } else {
            layout.landscapeColumns
        }
        val rows = if (portrait) PortraitTileRows else LandscapeTileRows
        val panelWidth = if (portrait) {
            frameWidth * layout.portraitWidthPercent.coerceIn(10, 100) / 100f
        } else {
            frameWidth * LandscapePanelWidthFraction
        }
        val panel = mockPanel(
            frameWidth = frameWidth,
            panelWidth = panelWidth,
            screenWidthDp = if (portrait) shortSide else longSide,
            density = density,
            columns = columns,
            headUpHeightRatio = if (portrait) HeadUpHeightPortrait else HeadUpHeightLandscape
        )
        val statusHeight = (frameHeight * 0.06f).coerceAtLeast(14.dp)
        val shape = RoundedCornerShape(PreviewCornerRadius)

        Column(
            modifier = Modifier
                .width(frameWidth)
                .height(frameHeight)
                .clip(shape)
                .background(colors.surfaceContainerHigh)
                .border(1.dp, colors.outlineVariant, shape)
        ) {
            StatusRow(
                config = config,
                frameWidth = frameWidth,
                minHeight = statusHeight,
                colors = colors
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                NotificationPlaceholders(
                    frameWidth = frameWidth,
                    portrait = portrait,
                    colors = colors,
                    modifier = if (portrait) {
                        Modifier
                            .align(Alignment.BottomCenter)
                            .padding(horizontal = frameWidth * 0.12f, vertical = frameWidth * 0.04f)
                    } else {
                        Modifier
                            .align(Alignment.TopStart)
                            .width(frameWidth * 0.36f)
                            .padding(
                                start = frameWidth * 0.04f,
                                top = frameWidth * 0.03f
                            )
                    }
                )
                PanelBody(
                    config = config,
                    layout = layout,
                    panel = panel,
                    columns = columns,
                    rows = rows,
                    colors = colors,
                    modifier = if (portrait) {
                        Modifier.align(Alignment.TopCenter)
                    } else {
                        Modifier
                            .align(Alignment.TopEnd)
                            .padding(end = frameWidth * 0.02f)
                    }
                )
            }
        }
    }
}

/** Status row of the shade: the clock/date string on the left, system glyphs on the right. */
@Composable
private fun StatusRow(
    config: ControlCenterPreviewConfig,
    frameWidth: Dp,
    minHeight: Dp,
    colors: ColorScheme
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = minHeight)
            .padding(horizontal = frameWidth * 0.04f),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = config.dateSample,
            fontSize = DateTextSize,
            color = colors.onSurfaceVariant.copy(alpha = if (config.customDate) 1f else 0.5f),
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        Spacer(modifier = Modifier.width(frameWidth * 0.02f))
        Row(horizontalArrangement = Arrangement.spacedBy(frameWidth * 0.012f)) {
            repeat(HeaderGlyphCount) {
                Box(
                    modifier = Modifier
                        .size((frameWidth * 0.016f).coerceAtLeast(3.dp))
                        .clip(CircleShape)
                        .background(colors.onSurfaceVariant.copy(alpha = 0.45f))
                )
            }
        }
    }
}

@Composable
private fun NotificationPlaceholders(
    frameWidth: Dp,
    portrait: Boolean,
    colors: ColorScheme,
    modifier: Modifier = Modifier
) {
    val cardHeight = (frameWidth * 0.06f).coerceAtLeast(6.dp)
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(cardHeight * 0.4f)
    ) {
        repeat(if (portrait) NotificationCardCount else NotificationCardCount + 2) { index ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(if (index % 2 == 0) cardHeight else cardHeight * 0.75f)
                    .clip(RoundedCornerShape(cardHeight * 0.35f))
                    .background(colors.onSurfaceVariant.copy(alpha = 0.14f))
            )
        }
    }
}

@Composable
private fun PanelBody(
    config: ControlCenterPreviewConfig,
    layout: MockLayout,
    panel: MockPanel,
    columns: Int,
    rows: Int,
    colors: ColorScheme,
    modifier: Modifier = Modifier
) {
    val tileShape = RoundedCornerShape(panel.pxToMock * config.tileCornerRadiusPx.toFloat())
    val headUpShape = RoundedCornerShape(panel.pxToMock * config.headUpCornerRadiusPx.toFloat())

    Column(
        modifier = modifier
            .width(panel.width)
            .clip(RoundedCornerShape(8.dp))
            .background(colors.onSurfaceVariant.copy(alpha = 0.06f))
            .padding(panel.padding),
        verticalArrangement = Arrangement.spacedBy(panel.gap)
    ) {
        HeaderGlyphRow(panel = panel, colors = colors)
        HeadUpBlock(
            config = config,
            layout = layout,
            panel = panel,
            shape = headUpShape,
            colors = colors
        )
        TileGrid(
            panel = panel,
            shape = tileShape,
            columns = columns,
            rows = rows,
            colors = colors
        )
        PageDots(panel = panel, colors = colors)
    }
}

@Composable
private fun HeaderGlyphRow(panel: MockPanel, colors: ColorScheme) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(HeaderGlyphCount) { index ->
            if (index > 0) Spacer(modifier = Modifier.width(panel.gap * 1.5f))
            Box(
                modifier = Modifier
                    .size((panel.width * 0.05f).coerceAtLeast(3.dp))
                    .clip(CircleShape)
                    .background(colors.onSurfaceVariant.copy(alpha = 0.4f))
            )
        }
    }
}

/** The 2x2 head-up block: two plain tiles and the two sliders that carry the labels. */
@Composable
private fun HeadUpBlock(
    config: ControlCenterPreviewConfig,
    layout: MockLayout,
    panel: MockPanel,
    shape: RoundedCornerShape,
    colors: ColorScheme
) {
    Column(verticalArrangement = Arrangement.spacedBy(panel.gap)) {
        Row(horizontalArrangement = Arrangement.spacedBy(panel.gap)) {
            HeadUpTile(
                panel = panel,
                shape = shape,
                layout = layout,
                active = true,
                colors = colors
            )
            HeadUpTile(
                panel = panel,
                shape = shape,
                layout = layout,
                active = false,
                colors = colors,
                showLabel = config.volumePercentVisible,
                fillPercent = VolumeSamplePercent
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(panel.gap)) {
            HeadUpTile(
                panel = panel,
                shape = shape,
                layout = layout,
                active = true,
                colors = colors
            )
            HeadUpTile(
                panel = panel,
                shape = shape,
                layout = layout,
                active = false,
                colors = colors,
                showLabel = config.brightnessPercentVisible,
                fillPercent = BrightnessSamplePercent
            )
        }
    }
}

/**
 * One head-up tile: the level bar ([fillPercent]) plus the glyph block, which sits at a quarter
 * of the tile width from the left on a horizontal slider and centred on a vertical one.
 * [showLabel] stands for the percentage text the module adds, drawn as a placeholder bar.
 */
@Composable
private fun HeadUpTile(
    panel: MockPanel,
    shape: RoundedCornerShape,
    layout: MockLayout,
    active: Boolean,
    colors: ColorScheme,
    showLabel: Boolean = false,
    fillPercent: Int = 0
) {
    Box(
        modifier = Modifier
            .size(panel.headUpWidth, panel.headUpHeight)
            .clip(shape)
            .background(
                if (active) {
                    colors.primary.copy(alpha = 0.85f)
                } else {
                    colors.onSurfaceVariant.copy(alpha = 0.22f)
                }
            )
    ) {
        if (layout.verticalSlider) {
            VerticalSliderBar(
                panel = panel,
                fillPercent = fillPercent,
                colors = colors
            )
        } else if (fillPercent > 0) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(fillPercent / 100f)
                    .height(panel.headUpHeight)
                    .background(colors.onSurfaceVariant.copy(alpha = 0.35f))
            )
        }
        HeadUpGlyph(
            panel = panel,
            showLabel = showLabel,
            centeredOnSlider = layout.verticalSlider,
            colors = colors
        )
        if (layout.detailIndicatorVisible) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(panel.headUpHeight * 0.14f)
                    .size((panel.headUpHeight * 0.16f).coerceAtLeast(2.dp))
                    .clip(CircleShape)
                    .background(colors.onSurfaceVariant.copy(alpha = 0.6f))
            )
        }
    }
}

/** Vertical slider track with its filled part growing from the bottom edge. */
@Composable
private fun BoxScope.VerticalSliderBar(panel: MockPanel, fillPercent: Int, colors: ColorScheme) {
    Box(
        modifier = Modifier
            .align(Alignment.Center)
            .width(panel.headUpWidth * VerticalSliderWidthFraction)
            .fillMaxHeight(VerticalSliderHeightFraction)
            .clip(RoundedCornerShape(percent = 50))
            .background(colors.onSurfaceVariant.copy(alpha = 0.25f))
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .fillMaxHeight(fillPercent / 100f)
                .background(colors.primary.copy(alpha = 0.85f))
        )
    }
}

@Composable
private fun HeadUpGlyph(
    panel: MockPanel,
    showLabel: Boolean,
    centeredOnSlider: Boolean,
    colors: ColorScheme
) {
    Row(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(if (centeredOnSlider) 1f else HeadUpGlyphCenterFraction * 2f),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size((panel.headUpHeight * 0.42f).coerceAtLeast(3.dp))
                    .clip(RoundedCornerShape(panel.headUpHeight * 0.12f))
                    .background(colors.onSurfaceVariant.copy(alpha = 0.7f))
            )
            if (showLabel) {
                Spacer(modifier = Modifier.height(panel.headUpHeight * 0.08f))
                Box(
                    modifier = Modifier
                        .width(panel.headUpWidth * 0.16f)
                        .height((panel.headUpHeight * 0.12f).coerceAtLeast(2.dp))
                        .clip(RoundedCornerShape(percent = 50))
                        .background(colors.onSurfaceVariant.copy(alpha = 0.6f))
                )
            }
        }
    }
}

/** Small tile grid: rounded tiles with the label bar the host draws under each of them. */
@Composable
private fun TileGrid(
    panel: MockPanel,
    shape: RoundedCornerShape,
    columns: Int,
    rows: Int,
    colors: ColorScheme
) {
    val labelHeight = (panel.tileHeight * TileLabelHeightFraction).coerceAtLeast(1.dp)
    Column(verticalArrangement = Arrangement.spacedBy(panel.gap)) {
        repeat(rows) { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(panel.gap)) {
                repeat(columns) { column ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            modifier = Modifier
                                .size(panel.tileWidth, panel.tileHeight)
                                .clip(shape)
                                .background(
                                    if ((row * columns + column) % 3 == 0) {
                                        colors.primary.copy(alpha = 0.85f)
                                    } else {
                                        colors.onSurfaceVariant.copy(alpha = 0.22f)
                                    }
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(panel.tileHeight * 0.45f)
                                    .clip(RoundedCornerShape(panel.tileHeight * 0.12f))
                                    .background(colors.onSurfaceVariant.copy(alpha = 0.7f))
                            )
                        }
                        Spacer(modifier = Modifier.height(labelHeight * 0.5f))
                        Box(
                            modifier = Modifier
                                .width(panel.tileWidth * 0.7f)
                                .height(labelHeight)
                                .clip(RoundedCornerShape(labelHeight * 0.5f))
                                .background(colors.onSurfaceVariant.copy(alpha = 0.45f))
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PageDots(panel: MockPanel, colors: ColorScheme) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(3) { index ->
            if (index > 0) Spacer(modifier = Modifier.width(panel.gap * 0.6f))
            Box(
                modifier = Modifier
                    .size((panel.width * 0.012f).coerceAtLeast(2.dp))
                    .clip(CircleShape)
                    .background(
                        if (index == 0) {
                            colors.primary
                        } else {
                            colors.onSurfaceVariant.copy(alpha = 0.3f)
                        }
                    )
            )
        }
    }
}
