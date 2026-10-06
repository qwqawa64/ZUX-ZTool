package com.qimian233.ztool.screens.home

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.qimian233.ztool.R
import com.qimian233.ztool.ui.components.ZToolButton
import com.qimian233.ztool.ui.components.ZToolCard
import com.qimian233.ztool.ui.components.ZToolTextButton
import com.qimian233.ztool.ui.theme.LocalZToolColorScheme
import com.qimian233.ztool.viewmodel.UpdateInfo

/*
 * Home-screen cards that depend on the device environment rather than on a user
 * action: the unofficial-system warning and the app-update notice. They live here
 * (instead of as private composables in `HomeRoute.kt`) so that
 * `HomeCardPreviewRoute` can render exactly the same code with mock data — the
 * preview screen is the only way to inspect them on a real ZUX OS device with no
 * pending update.
 *
 * Keep both composables free of repository/network access: they take plain values
 * and lambdas so the preview and the home screen stay visually identical.
 */

/**
 * Warning shown on devices that do not run ZUX OS / ZUI, where most hooks cannot
 * work. Dismissible, unlike the unofficial-build warning card on the home screen.
 */
@Composable
internal fun NonZuxOsCard(onDismiss: () -> Unit) {
    ZToolCard(
        // The dismiss button inside keeps its own clickable, so it consumes the
        // press before this card-level one and the card stays inert there.
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp)
            .cardPressScale()
            .clickable(onClick = {}),
        containerColor = LocalZToolColorScheme.current.errorContainer
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Rounded.Warning,
                    contentDescription = null,
                    tint = LocalZToolColorScheme.current.onErrorContainer
                )
                Spacer(modifier = Modifier.width(16.dp))
                Text(
                    text = stringResource(R.string.page_home_non_zuxos_warn),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = LocalZToolColorScheme.current.onErrorContainer
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                ZToolTextButton(
                    onClick = onDismiss,
                    text = stringResource(R.string.page_home_non_zuxos_dismiss),
                    isPrimary = false
                )
            }
        }
    }
}

/**
 * App-update notice. [update] carries the changelog text fetched from the release
 * feed; `expanded` decides whether it is clamped to four lines or shown in full.
 */
@Composable
internal fun UpdateCard(
    update: UpdateInfo,
    onToggleExpanded: () -> Unit,
    onIgnore: () -> Unit,
    onOpenUpdate: () -> Unit
) {
    ZToolCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggleExpanded),
        containerColor = LocalZToolColorScheme.current.tertiaryContainer
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Rounded.SystemUpdate,
                    contentDescription = null,
                    tint = LocalZToolColorScheme.current.onTertiaryContainer
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = stringResource(R.string.page_home_update_available_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = LocalZToolColorScheme.current.onTertiaryContainer,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = stringResource(R.string.page_home_build_code, update.versionName, update.versionCode),
                    style = MaterialTheme.typography.labelMedium,
                    color = LocalZToolColorScheme.current.onTertiaryContainer
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = update.changelog,
                style = MaterialTheme.typography.bodyMedium,
                color = LocalZToolColorScheme.current.onTertiaryContainer,
                maxLines = if (update.expanded) Int.MAX_VALUE else 4,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                ZToolTextButton(onClick = onIgnore, text = stringResource(R.string.page_home_update_button_ignore), isPrimary = false)
                Spacer(modifier = Modifier.width(8.dp))
                ZToolButton(onClick = onOpenUpdate) {
                    Text(stringResource(R.string.page_home_update_button_update))
                }
            }
        }
    }
}

/**
 * Press feedback for a whole card: scales it down slightly while a finger is held
 * on it. It listens on the Initial pointer pass so it also works for containers
 * whose children consume the press themselves (a card that hosts its own buttons,
 * for example).
 *
 * The shape is clipped by the same layer that scales. A separate `Modifier.clip`
 * in front of the scale layer loses the clip, which leaves the click ripple of a
 * following `clickable` square inside a rounded card.
 */
@Composable
internal fun Modifier.cardPressScale(
    clipShape: Shape = RoundedCornerShape(16.dp),
    pressedScale: Float = 0.98f
): Modifier {
    val isPressed = remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isPressed.value) pressedScale else 1f,
        label = "cardPressScale"
    )
    return this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
            shape = clipShape
            clip = true
        }
        .pointerInput(Unit) {
            val touchSlop = viewConfiguration.touchSlop
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                isPressed.value = true
                try {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        // A drag belongs to the surrounding scroll container, not to the card.
                        if (!change.pressed) break
                        if ((change.position - down.position).getDistance() > touchSlop) break
                    }
                } finally {
                    isPressed.value = false
                }
            }
        }
}
