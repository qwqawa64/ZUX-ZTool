package com.qimian233.ztool.ui.components

import android.util.Log
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.dp
import com.qimian233.ztool.ui.theme.LocalZToolColorScheme
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.roundToInt

/** How long the controller waits for the target row to report its bounds. */
const val HighlightAwaitRowTimeoutMillis = 1200L
/** Extra grace when falling back to the parent key. */
const val HighlightAwaitParentTimeoutMillis = 400L
/** One pulse cycle (fade in + fade out); the timeline runs two cycles, 1.2s total. */
const val HighlightPulseCycleMillis = 600
/** Grace before running the debug index audit, so conditional rows have composed. */
const val AUDIT_DELAY_MILLIS = 2000L

/**
 * Row bounds (in root coordinates) keyed by [SettingItem.key]; the scroll container
 * root reports its own bounds under [HighlightContainerMarker] so the controller can
 * convert to a scroll offset. Coordinates are re-read on demand, so values stay fresh
 * as layout shifts.
 */
class HighlightAnchorRegistry {

    private val rowBounds = ConcurrentHashMap<String, androidx.compose.ui.geometry.Rect>()
    private var containerTopInRoot: Float = 0f
    private val waiters = ConcurrentHashMap<String, MutableList<CompletableDeferred<Int>>>()
    private val mutex = Mutex()

    /** Called once by the settings-list content column. */
    fun reportContainer(topInRoot: Float) {
        containerTopInRoot = topInRoot
    }

    fun reportRow(key: String, boundsInRoot: androidx.compose.ui.geometry.Rect) {
        rowBounds[key] = boundsInRoot
        val offset = offsetFor(key)
        if (offset != null) {
            synchronized(waiters) {
                waiters.remove(key)?.forEach { it.complete(offset) }
            }
        }
    }

    fun clearRow(key: String) {
        rowBounds.remove(key)
    }

    /** Keys of all rows currently registered (used by the debug index audit). */
    fun snapshotKeys(): Set<String> = rowBounds.keys.toSet()

    fun offsetFor(key: String): Int? {
        val bounds = rowBounds[key] ?: return null
        // Root coordinate of the row's top minus the container's top equals the
        // scroll offset that brings the row to the top of the content column.
        return (bounds.top - containerTopInRoot).roundToInt()
    }

    suspend fun await(key: String, timeoutMillis: Long): Int? {
        offsetFor(key)?.let { return it }
        val deferred = CompletableDeferred<Int>()
        mutex.withLock {
            offsetFor(key)?.let { return it }
            waiters.getOrPut(key) { mutableListOf() }.add(deferred)
        }
        return withTimeoutOrNull(timeoutMillis) { deferred.await() }
    }
}

/** Registry provided once per settings list. */
val LocalHighlightRegistry = staticCompositionLocalOf<HighlightAnchorRegistry?> { null }
/** Id of the row currently drawing the pulse (null when idle). */
val LocalHighlightActiveId = compositionLocalOf<String?> { null }

/**
 * Place directly inside the settings list' scroll container (as the first child of the
 * content column): reports the container's root bounds so row offsets can be computed.
 * Draws and measures nothing.
 */
@Composable
fun HighlightContainerMarker(registry: HighlightAnchorRegistry?) {
    if (registry == null) return
    androidx.compose.foundation.layout.Box(
        modifier = Modifier
            .onGloballyPositioned { coords ->
                registry.reportContainer(coords.positionInRoot().y)
            }
    )
}

/**
 * Runs the land-and-highlight sequence for one navigation `?target=` id: waits for the
 * row to report its bounds, scrolls to it, keeps the pulse for two cycles, and falls
 * back to the parent key when the target never rendered (conditional child behind an
 * OFF parent switch). [onConsumed] lets the screen drop the stale target so back /
 * rotation does not replay the pulse.
 */
@Composable
fun HighlightController(
    highlightTargetId: String?,
    scrollState: androidx.compose.foundation.ScrollState,
    registry: HighlightAnchorRegistry?,
    onConsumed: () -> Unit,
    content: @Composable () -> Unit
) {
    var activeId by remember { mutableStateOf<String?>(null) }

    // Debug-only index audit: once rows have composed, compare rendered keys against
    // the static index. No target needed; release builds short-circuit to a no-op.
    val auditedRoute = highlightTargetId?.let { id ->
        SearchHighlightIndexBridge.routeOfId(id)?.substringBefore('?')
    }
    if (com.qimian233.ztool.BuildConfig.DEBUG) {
        LaunchedEffect(auditedRoute, registry) {
            if (registry != null && auditedRoute != null) {
                delay(AUDIT_DELAY_MILLIS)
                com.qimian233.ztool.search.SearchIndexAudit.auditRoute(auditedRoute, registry)
            }
        }
    }

    LaunchedEffect(highlightTargetId, registry) {
        val target = highlightTargetId ?: return@LaunchedEffect
        if (registry == null) {
            onConsumed()
            return@LaunchedEffect
        }

        var offset = registry.await(target, HighlightAwaitRowTimeoutMillis)
        var key = target
        if (offset == null) {
            val parentKey = SearchHighlightIndexBridge.parentKeyOf(target)
            if (parentKey != null) {
                val parentOffset = registry.await(parentKey, HighlightAwaitParentTimeoutMillis)
                if (parentOffset != null) {
                    key = parentKey
                    offset = parentOffset
                }
            }
        }

        if (offset != null) {
            scrollState.animateScrollTo(offset.coerceAtLeast(0))
            activeId = key
            delay(HighlightPulseCycleMillis * 2L)
            activeId = null
        } else {
            Log.w(
                "HighlightController",
                "Highlight target '$target' not found on screen and no parent fallback"
            )
        }
        onConsumed()
    }

    CompositionLocalProvider(LocalHighlightActiveId provides activeId, content = content)
}

/**
 * Row wrapper used by the settings funnel: reports the row's bounds into the registry
 * and overlays the container-color double pulse while [LocalHighlightActiveId] equals
 * [highlightKey]. Content is rendered inside a Box so the overlay covers the whole row.
 * The pulse timeline runs only while the row is active; inactive rows pay one
 * CompositionLocal read.
 */
@Composable
fun HighlightableSettingRow(
    highlightKey: String?,
    modifier: Modifier = Modifier,
    shape: Shape? = null,
    content: @Composable () -> Unit
) {
    val registry = LocalHighlightRegistry.current
    val activeId = LocalHighlightActiveId.current
    val isActive = highlightKey != null && activeId == highlightKey

    val pulseAlpha: Float
    if (isActive) {
        val transition = rememberInfiniteTransition(label = "settingPulse")
        val alpha by transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = HighlightPulseCycleMillis, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "settingPulseAlpha"
        )
        pulseAlpha = alpha
    } else {
        pulseAlpha = 0f
    }

    val pulseColor = if (isActive) {
        LocalZToolColorScheme.current.primaryContainer
    } else {
        Color.Transparent
    }

    androidx.compose.foundation.layout.Box(
        modifier = modifier
            .onGloballyPositioned { coords ->
                if (highlightKey != null && registry != null) {
                    registry.reportRow(highlightKey, coords.boundsInRoot())
                }
            }
            .drawWithContent {
                // Pulse renders BEHIND the row content so text stays readable.
                if (pulseAlpha > 0f) {
                    val corner = CornerRadius(12.dp.toPx(), 12.dp.toPx())
                    val color = pulseColor.copy(alpha = pulseAlpha * 0.5f)
                    when (shape) {
                        null -> drawRoundRect(
                            color = color,
                            cornerRadius = corner
                        )
                        else -> drawPath(
                            path = shape.createOutline(size, layoutDirection, this).getOutlinePath(),
                            color = color
                        )
                    }
                }
                drawContent()
            }
    ) {
        content()
    }
}

private fun androidx.compose.ui.graphics.Outline.getOutlinePath(): androidx.compose.ui.graphics.Path = when (this) {
    is androidx.compose.ui.graphics.Outline.Rectangle -> androidx.compose.ui.graphics.Path().apply { addRect(rect) }
    is androidx.compose.ui.graphics.Outline.Rounded -> androidx.compose.ui.graphics.Path().apply { addRoundRect(roundRect) }
    is androidx.compose.ui.graphics.Outline.Generic -> path
}

/**
 * Lookup seam between the highlight controller and the search index, keeping
 * ui/components free of a direct dependency on the search package. The real
 * implementation is installed from ZToolNavHost at composition root.
 */
object SearchHighlightIndexBridge {
    @Volatile
    var parentKeyOf: (String) -> String? = { null }

    /** Route a target id belongs to, so the debug audit knows which screen it is on. */
    @Volatile
    var routeOfId: (String) -> String? = { null }
}
