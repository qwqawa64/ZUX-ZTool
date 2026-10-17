package com.qimian233.ztool.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Dim strength of [SearchScrim], as an opaque fraction of black. Tune here. */
private const val ScrimAlpha = 0.60f

private val ScrimColor = Color.Black.copy(alpha = ScrimAlpha)

/**
 * Shell-owned handle for the docked search dim layer. The hosting screen publishes the
 * search control's bounds, its expanded state and its dismiss action; the shell draws
 * [SearchScrim], so the dim also covers the navigation rail and the bottom bar.
 */
@Stable
class SearchOverlayState {
    internal var bounds: Rect? by mutableStateOf(null)
    internal var visible: Boolean by mutableStateOf(false)
    internal var onDismiss: () -> Unit = {}
}

/**
 * The [SearchOverlayState] provided by the app shell. Screens composed outside the shell
 * read a throwaway instance, so search still works without the dim layer.
 */
val LocalSearchOverlayState = staticCompositionLocalOf { SearchOverlayState() }

/**
 * Dim layer for the docked search: covers everything outside [SearchOverlayState.bounds]
 * and collapses the search when a covered area is tapped. Host it in the outermost box of
 * the app shell, as the LAST child, so it also dims the navigation chrome.
 *
 * Four bands are drawn around the cutout rather than one full-size box, because a
 * full-size sibling would swallow the pointer events the search bar needs.
 */
@Composable
fun SearchScrim(
    state: SearchOverlayState,
    modifier: Modifier = Modifier
) {
    // The outer box is measured even while hidden, so the cutout is already right on
    // the first visible frame.
    var containerOrigin by remember { mutableStateOf(Offset.Zero) }
    Box(
        modifier = modifier
            .fillMaxSize()
            .onGloballyPositioned { coordinates ->
                containerOrigin = coordinates.boundsInRoot().topLeft
            }
    ) {
        AnimatedVisibility(
            visible = state.visible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val hole = resolveScrimHole(state.bounds, containerOrigin, maxWidth, maxHeight)
                val onDismiss = { state.onDismiss() }
                if (hole == null) {
                    ScrimBand(modifier = Modifier.fillMaxSize(), onDismiss = onDismiss)
                } else {
                    ScrimBand(modifier = Modifier.size(maxWidth, hole.top), onDismiss = onDismiss)
                    ScrimBand(
                        modifier = Modifier
                            .offset(y = hole.bottom)
                            .size(maxWidth, maxHeight - hole.bottom),
                        onDismiss = onDismiss
                    )
                    ScrimBand(
                        modifier = Modifier
                            .offset(y = hole.top)
                            .size(hole.left, hole.bottom - hole.top),
                        onDismiss = onDismiss
                    )
                    ScrimBand(
                        modifier = Modifier
                            .offset(x = hole.right, y = hole.top)
                            .size(maxWidth - hole.right, hole.bottom - hole.top),
                        onDismiss = onDismiss
                    )
                }
            }
        }
    }
}

/** The cutout rectangle in scrim-local coordinates, clamped to the scrim's own size. */
@Composable
private fun resolveScrimHole(
    bounds: Rect?,
    containerOrigin: Offset,
    width: Dp,
    height: Dp
): ScrimHole? {
    if (bounds == null) return null
    val density = LocalDensity.current
    val local = bounds.translate(-containerOrigin.x, -containerOrigin.y)
    val left = with(density) { local.left.toDp() }.coerceIn(0.dp, width)
    val top = with(density) { local.top.toDp() }.coerceIn(0.dp, height)
    val right = with(density) { local.right.toDp() }.coerceIn(left, width)
    val bottom = with(density) { local.bottom.toDp() }.coerceIn(top, height)
    return ScrimHole(left = left, top = top, right = right, bottom = bottom)
}

private data class ScrimHole(
    val left: Dp,
    val top: Dp,
    val right: Dp,
    val bottom: Dp
)

@Composable
private fun ScrimBand(
    modifier: Modifier,
    onDismiss: () -> Unit
) {
    Box(
        modifier = modifier
            .background(ScrimColor)
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() }
            ) { onDismiss() }
    )
}
