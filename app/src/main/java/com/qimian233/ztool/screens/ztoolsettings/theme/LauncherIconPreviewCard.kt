package com.qimian233.ztool.screens.ztoolsettings.theme

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.qimian233.ztool.R
import com.qimian233.ztool.ui.components.ZToolCard
import com.qimian233.ztool.ui.theme.LocalZToolColorScheme

/** Preview of the launcher icon choice; the unselected candidate is dimmed by a scrim. */
@Composable
fun LauncherIconPreviewCard(
    useAlternativeIcon: Boolean,
    modifier: Modifier = Modifier
) {
    ZToolCard(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(previewPageBackground())
                .padding(16.dp)
        ) {
            Text(
                text = stringResource(R.string.page_settings_theme_preview_launcher_icon_title),
                style = MaterialTheme.typography.titleSmall,
                color = LocalZToolColorScheme.current.onSurface
            )
            Spacer(modifier = Modifier.height(16.dp))
            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                val iconSize = minOf(maxWidth * 0.22f, 112.dp)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(
                        48.dp,
                        Alignment.CenterHorizontally
                    )
                ) {
                    LauncherIconSample(
                        iconRes = R.mipmap.ic_launcher,
                        label = stringResource(R.string.page_settings_theme_preview_default_icon_label),
                        selected = !useAlternativeIcon,
                        iconSize = iconSize
                    )
                    LauncherIconSample(
                        iconRes = R.mipmap.ic_launcher_alt,
                        label = stringResource(R.string.page_settings_theme_preview_alternative_icon_label),
                        selected = useAlternativeIcon,
                        iconSize = iconSize
                    )
                }
            }
        }
    }
}

@Composable
private fun LauncherIconSample(
    @DrawableRes iconRes: Int,
    label: String,
    selected: Boolean,
    iconSize: Dp
) {
    val colors = LocalZToolColorScheme.current
    val bitmap = rememberLauncherIcon(iconRes, iconSize)
    val shape = RoundedCornerShape(iconSize * 0.23f)
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box {
            Image(
                bitmap = bitmap,
                contentDescription = null,
                modifier = Modifier
                    .size(iconSize)
                    .clip(shape)
            )
            if (!selected) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .clip(shape)
                        .background(Color.Black.copy(alpha = 0.5f))
                )
            }
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .clip(shape)
                    .border(1.dp, colors.outlineVariant.copy(alpha = 0.6f), shape)
            )
            if (selected) {
                Icon(
                    imageVector = Icons.Rounded.CheckCircle,
                    contentDescription = null,
                    tint = colors.primary,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .size(iconSize * 0.26f)
                        .background(colors.surface, CircleShape)
                        .padding(iconSize * 0.015f)
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) {
                colors.onSurface
            } else {
                colors.onSurfaceVariant.copy(alpha = 0.7f)
            },
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/** Renders an adaptive icon (XML) into a bitmap, which Compose's Image cannot do directly. */
@Composable
private fun rememberLauncherIcon(@DrawableRes iconRes: Int, size: Dp): ImageBitmap {
    val context = LocalContext.current
    val sizePx = with(LocalDensity.current) { size.roundToPx() }.coerceAtLeast(1)
    return remember(context, iconRes, sizePx) {
        val drawable = checkNotNull(context.getDrawable(iconRes))
        drawable.toBitmap(sizePx, sizePx).asImageBitmap()
    }
}
