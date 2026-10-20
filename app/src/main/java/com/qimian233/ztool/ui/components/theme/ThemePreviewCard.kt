package com.qimian233.ztool.ui.components.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.qimian233.ztool.R
import com.qimian233.ztool.ui.components.ZToolCard
import com.qimian233.ztool.ui.theme.FrontendStyle
import com.qimian233.ztool.ui.theme.LocalEnableFloatingBottomBar
import com.qimian233.ztool.ui.theme.LocalEnableFloatingBottomBarBlur
import com.qimian233.ztool.ui.theme.LocalZToolColorScheme
import com.qimian233.ztool.ui.theme.LocalZToolThemeSpec
import com.qimian233.ztool.ui.theme.moduleStatusPalette
import top.yukonga.miuix.kmp.theme.MiuixTheme

private val SpecimenPadding = 16.dp

/** Landscape 16:10 ratio for both frames. */
private const val FrameAspectRatio = 16f / 10f

/** Widest a single frame gets before the pair is centered instead of stretched. */
private val MaxFrameWidth = 400.dp
private val FrameGap = 24.dp

private const val InfoListRowCount = 4

/** One info row fewer while the capsule occupies the bottom of the frame. */
private const val FloatingInfoListRowCount = 3
private const val FeatureGridRows = 3
private const val FeatureGridColumns = 2

/** Destinations drawn in the navigation specimens; mirrors MainRoute.entriesInOrder. */
private const val NavigationItemCount = 3

/** Which navigation chrome a specimen frame shows; frames are always landscape. */
private enum class PreviewNavigation {
    Rail,
    Capsule,
    GlassCapsule;

    val isFloating: Boolean
        get() = this != Rail

    val isGlass: Boolean
        get() = this == GlassCapsule
}

/**
 * Appearance specimen: two side-by-side landscape tablet frames, one showing the home
 * page's module-status card and one a features page. Surfaces are drawn from the live
 * palette; bars stand in for text so the specimen shows color, not UI copy.
 */
@Composable
fun ThemePreviewCard(modifier: Modifier = Modifier) {
    val themeSpec = LocalZToolThemeSpec.current
    val floatingBar = themeSpec.style == FrontendStyle.Miuix &&
        LocalEnableFloatingBottomBar.current
    val navigation = when {
        !floatingBar -> PreviewNavigation.Rail
        LocalEnableFloatingBottomBarBlur.current -> PreviewNavigation.GlassCapsule
        else -> PreviewNavigation.Capsule
    }

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
                        PreviewHomePage(navigation = navigation)
                    }
                    PreviewFrameColumn(
                        label = stringResource(R.string.page_settings_theme_preview_features_label),
                        modifier = Modifier.width(frameWidth)
                    ) {
                        PreviewFeaturePage(navigation = navigation)
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

/**
 * App chrome around a specimen page: the landscape navigation rail beside the content,
 * or the floating capsule bar over it.
 */
@Composable
private fun PreviewPageChrome(
    navigation: PreviewNavigation,
    content: @Composable ColumnScope.(frameWidth: Dp) -> Unit
) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val frameWidth = maxWidth
        Row(modifier = Modifier.fillMaxSize()) {
            if (!navigation.isFloating) {
                PreviewNavigationRail(frameWidth = frameWidth)
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            ) {
                PreviewTopBar(width = frameWidth)
                content(frameWidth)
            }
        }
        if (navigation.isFloating) {
            PreviewFloatingNavigationBar(
                frameWidth = frameWidth,
                glass = navigation.isGlass,
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }
    }
}

/**
 * Bottom inset that keeps a page clear of the floating capsule bar. The capsule's top
 * edge is 0.15 × frame width above the frame bottom; the page's own content padding
 * already supplies 0.045, so only the rest plus a small gap is added here.
 */
private fun Modifier.floatingNavigationInset(
    navigation: PreviewNavigation,
    frameWidth: Dp
): Modifier = padding(bottom = if (navigation.isFloating) frameWidth * 0.115f else 0.dp)

@Composable
private fun PreviewHomePage(navigation: PreviewNavigation) {
    PreviewPageChrome(navigation = navigation) { frameWidth ->
        val contentPadding = frameWidth * 0.045f
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(contentPadding)
                .floatingNavigationInset(navigation, frameWidth),
            verticalArrangement = Arrangement.spacedBy(contentPadding * 0.7f)
        ) {
            PreviewStatusCard(modifier = Modifier.fillMaxWidth())
            PreviewInfoList(
                rowCount = if (navigation.isFloating) {
                    FloatingInfoListRowCount
                } else {
                    InfoListRowCount
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            )
        }
    }
}

@Composable
private fun PreviewFeaturePage(navigation: PreviewNavigation) {
    PreviewPageChrome(navigation = navigation) { frameWidth ->
        val contentPadding = frameWidth * 0.045f
        val gap = frameWidth * 0.03f
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(contentPadding)
                .floatingNavigationInset(navigation, frameWidth),
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

/** Landscape navigation rail with three destinations, the first one selected. */
@Composable
private fun PreviewNavigationRail(frameWidth: Dp) {
    val colors = LocalZToolColorScheme.current
    Column(
        modifier = Modifier
            .fillMaxHeight()
            .width(frameWidth * 0.11f)
            .background(colors.surface),
        verticalArrangement = Arrangement.spacedBy(
            frameWidth * 0.04f,
            Alignment.CenterVertically
        ),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        repeat(NavigationItemCount) { index ->
            PreviewNavigationRailItem(
                frameWidth = frameWidth,
                selected = index == 0
            )
        }
    }
}

@Composable
private fun PreviewNavigationRailItem(frameWidth: Dp, selected: Boolean) {
    val colors = LocalZToolColorScheme.current
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(frameWidth * 0.012f)
    ) {
        Box(
            modifier = Modifier
                .width(frameWidth * 0.08f)
                .height(frameWidth * 0.045f)
                .clip(CircleShape)
                .background(if (selected) colors.secondaryContainer else Color.Transparent),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(frameWidth * 0.03f)
                    .clip(RoundedCornerShape(frameWidth * 0.009f))
                    .background(
                        if (selected) colors.onSecondaryContainer else colors.onSurfaceVariant
                    )
            )
        }
        PreviewBar(
            barWidth = frameWidth * 0.045f,
            barHeight = frameWidth * 0.011f,
            color = if (selected) colors.onSurface else colors.onSurfaceVariant
        )
    }
}

/**
 * Floating capsule navigation for the Miuix front end. The glass variant is a plain
 * lens-like material: translucent fill, a light top sheen and a bright rim.
 */
@Composable
private fun PreviewFloatingNavigationBar(
    frameWidth: Dp,
    glass: Boolean,
    modifier: Modifier = Modifier
) {
    val colors = LocalZToolColorScheme.current
    Box(
        modifier = modifier
            .padding(bottom = frameWidth * 0.035f)
            .width(frameWidth * 0.62f)
            .height(frameWidth * 0.115f)
            .shadow(elevation = if (glass) 3.dp else 2.dp, shape = CircleShape)
            .clip(CircleShape)
            .then(
                if (glass) {
                    Modifier
                        .background(colors.surface.copy(alpha = 0.55f))
                        .background(colors.onSurface.copy(alpha = 0.05f))
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    Color.White.copy(alpha = 0.5f),
                                    Color.White.copy(alpha = 0.04f)
                                )
                            )
                        )
                        .border(
                            width = frameWidth * 0.003f,
                            brush = Brush.verticalGradient(
                                listOf(
                                    Color.White.copy(alpha = 0.95f),
                                    colors.onSurface.copy(alpha = 0.18f)
                                )
                            ),
                            shape = CircleShape
                        )
                } else {
                    Modifier
                        .background(colors.surface.copy(alpha = 0.75f))
                        .border(
                            width = 1.dp,
                            color = colors.outlineVariant.copy(alpha = 0.6f),
                            shape = CircleShape
                        )
                }
            )
            .padding(
                horizontal = frameWidth * 0.018f,
                vertical = frameWidth * 0.012f
            ),
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(frameWidth * 0.014f),
            verticalAlignment = Alignment.CenterVertically
        ) {
            repeat(NavigationItemCount) { index ->
                PreviewFloatingNavigationTab(
                    frameWidth = frameWidth,
                    selected = index == 0,
                    glass = glass,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun PreviewFloatingNavigationTab(
    frameWidth: Dp,
    selected: Boolean,
    glass: Boolean,
    modifier: Modifier = Modifier
) {
    val colors = LocalZToolColorScheme.current
    val contentColor = if (glass && selected) colors.primary else colors.onSurface
    val indicatorColor = when {
        selected && glass -> colors.onSurface.copy(alpha = 0.08f)
        selected -> colors.primary.copy(alpha = 0.15f)
        else -> Color.Transparent
    }
    Column(
        modifier = modifier
            .fillMaxHeight()
            .clip(CircleShape)
            .background(indicatorColor),
        verticalArrangement = Arrangement.spacedBy(
            frameWidth * 0.008f,
            Alignment.CenterVertically
        ),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(frameWidth * 0.028f)
                .clip(CircleShape)
                .background(contentColor)
        )
        PreviewBar(
            barWidth = frameWidth * 0.04f,
            barHeight = frameWidth * 0.009f,
            color = contentColor
        )
    }
}

/** Home module-status card in its fully active state, colored by the shared palette. */
@Composable
private fun PreviewStatusCard(modifier: Modifier = Modifier) {
    val palette = moduleStatusPalette(bothActive = true, anyActive = true)
    BoxWithConstraints(modifier = modifier) {
        val width = maxWidth
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(width * 0.035f))
                .background(palette.container)
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
                        color = palette.content
                    )
                    Spacer(modifier = Modifier.width(width * 0.02f))
                    Box(
                        modifier = Modifier
                            .width(width * 0.14f)
                            .height(width * 0.032f)
                            .clip(RoundedCornerShape(width * 0.008f))
                            .background(palette.content.copy(alpha = 0.15f))
                    )
                }
                PreviewBar(
                    barWidth = width * 0.26f,
                    barHeight = width * 0.02f,
                    color = palette.content.copy(alpha = 0.8f)
                )
            }
            Spacer(modifier = Modifier.width(width * 0.04f))
            Box(
                modifier = Modifier
                    .size(width * 0.075f)
                    .clip(CircleShape)
                    .background(palette.icon)
            )
        }
    }
}

/** Home system-info rows: one surface container, icon plates and value bars. */
@Composable
private fun PreviewInfoList(
    rowCount: Int,
    modifier: Modifier = Modifier
) {
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
            repeat(rowCount) { index ->
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
