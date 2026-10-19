package com.qimian233.ztool.screens.features

import android.graphics.drawable.Drawable
import android.widget.ImageView
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.qimian233.ztool.R
import com.qimian233.ztool.data.features.FeatureCard
import com.qimian233.ztool.data.features.FeatureIconLoader
import com.qimian233.ztool.data.features.FeatureIconSource
import com.qimian233.ztool.ui.components.ZToolCard
import com.qimian233.ztool.ui.theme.LocalZToolColorScheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** One Features-grid entry and whether every scope it needs is granted. */
internal data class FeatureCardState(
    val card: FeatureCard,
    val inScope: Boolean
)

private val FeatureCardHeight: Dp = 112.dp

/**
 * One Features-grid card: the icon plate, the name/description column and the chevron.
 * [onClick] carries the scope gate, so the card itself stays presentation-only.
 */
@Composable
internal fun FeatureCardItem(
    state: FeatureCardState,
    iconCache: MutableMap<String, Drawable?>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val card = state.card
    ZToolCard(
        modifier = modifier
            .fillMaxWidth()
            .height(FeatureCardHeight)
            .clickable(onClick = onClick),
        containerColor = LocalZToolColorScheme.current.surfaceContainer
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CardIcon(
                source = card.icon,
                iconCache = iconCache,
                contentDescription = stringResource(card.nameRes)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(card.nameRes),
                    style = MaterialTheme.typography.titleMedium,
                    color = LocalZToolColorScheme.current.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                if (state.inScope) {
                    Text(
                        text = stringResource(card.descriptionRes),
                        style = MaterialTheme.typography.bodyMedium,
                        color = LocalZToolColorScheme.current.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                } else {
                    Text(
                        text = stringResource(R.string.page_features_not_in_scope_tip),
                        style = MaterialTheme.typography.bodyMedium,
                        color = LocalZToolColorScheme.current.error,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                contentDescription = null,
                tint = LocalZToolColorScheme.current.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun CardIcon(
    source: FeatureIconSource,
    iconCache: MutableMap<String, Drawable?>,
    contentDescription: String
) {
    when (source) {
        is FeatureIconSource.Package ->
            PackageIcon(source.packageName, iconCache, contentDescription)
        is FeatureIconSource.LocalGlyph -> {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(LocalZToolColorScheme.current.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(source.resId),
                    contentDescription = contentDescription,
                    tint = LocalZToolColorScheme.current.onPrimaryContainer,
                    modifier = Modifier.size(28.dp)
                )
            }
        }
    }
}

@Composable
private fun PackageIcon(
    packageName: String,
    iconCache: MutableMap<String, Drawable?>,
    contentDescription: String
) {
    val context = LocalContext.current
    val icon by produceState<Drawable?>(
        initialValue = iconCache[packageName],
        packageName
    ) {
        if (iconCache.containsKey(packageName)) {
            value = iconCache[packageName]
            return@produceState
        }
        val loaded = withContext(Dispatchers.IO) {
            FeatureIconLoader.loadPackageIcon(context.applicationContext, packageName)
        }
        iconCache[packageName] = loaded
        value = loaded
    }
    if (icon == null) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(LocalZToolColorScheme.current.surfaceContainerHighest)
        )
    } else {
        AndroidView(
            modifier = Modifier.size(48.dp),
            factory = { viewContext ->
                ImageView(viewContext).apply {
                    scaleType = ImageView.ScaleType.FIT_CENTER
                    this.contentDescription = contentDescription
                }
            },
            update = { imageView ->
                imageView.contentDescription = contentDescription
                imageView.setImageDrawable(icon)
            }
        )
    }
}
