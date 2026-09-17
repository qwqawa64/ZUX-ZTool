package com.qimian233.ztool.ui.theme

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.isUnspecified
import androidx.compose.ui.graphics.*
import androidx.compose.ui.platform.LocalView
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.hypot

val LocalThemeRevealController = staticCompositionLocalOf<ThemeRevealController> {
    error("ThemeRevealController not provided")
}

interface ThemeRevealController {
    /**
     * Snapshot the current UI, run [onAction], then clear a circle growing
     * from [anchor] over the snapshot to expose the new UI. [anchor] is in
     * composition-root coordinates; [Offset.Unspecified] keeps the legacy
     * right-middle anchor.
     */
    fun triggerReveal(
        onAction: () -> Unit,
        onAnimationMidway: () -> Unit = {},
        onAnimationEnd: () -> Unit = {},
        anchor: Offset = Offset.Unspecified
    )

    /**
     * Reveal without a prior UI snapshot: paint [coverColor] over the whole
     * screen, run [onAction], then clear a circle growing from [anchor].
     * Used when there is no previous UI to snapshot, e.g. right after the
     * system launch mask lifts.
     */
    fun triggerCoverReveal(
        anchor: Offset,
        coverColor: Color,
        onAction: () -> Unit = {}
    )
}

tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/**
 * Opaque cover color for cover reveals: black blended with the current
 * primary color, matching the ambient shadow tint of snapshot reveals and
 * guaranteeing a visible ripple edge against the page background.
 */
@Composable
fun ztoolRevealCoverColor(): Color =
    lerp(Color.Black, LocalZToolColorScheme.current.primary, 0.50f)

@Composable
fun ThemeRevealProvider(
    initialCoverColor: Color? = null,
    content: @Composable () -> Unit
) {
    val view = LocalView.current
    var snapshot by remember { mutableStateOf<ImageBitmap?>(null) }
    // Seeded so the very first frame can already be covered for intro reveals.
    var maskColor by remember { mutableStateOf(initialCoverColor) }
    var revealAnchor by remember { mutableStateOf(Offset.Unspecified) }
    val coroutineScope = rememberCoroutineScope()
    var isRevealing by remember { mutableStateOf(false) }

    val revealRadius = remember { Animatable(0f) }
    val darkOverlayAlpha = remember { Animatable(0f) }

    fun resolveAnchor(anchor: Offset): Offset =
        if (anchor.isUnspecified) Offset(view.width.toFloat(), view.height / 2f) else anchor

    fun maxRevealRadius(anchor: Offset): Float {
        val width = view.width.toFloat()
        val height = view.height.toFloat()
        return maxOf(
            hypot(anchor.x, anchor.y),
            hypot(width - anchor.x, anchor.y),
            hypot(anchor.x, height - anchor.y),
            hypot(width - anchor.x, height - anchor.y)
        )
    }

    val controller = remember {
        object : ThemeRevealController {
            override fun triggerReveal(
                onAction: () -> Unit,
                onAnimationMidway: () -> Unit,
                onAnimationEnd: () -> Unit,
                anchor: Offset
            ) {
                if (isRevealing) return

                val activity = view.context.findActivity()
                val window = activity?.window

                if (window != null) {
                    isRevealing = true

                    val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
                    val location = IntArray(2)
                    view.getLocationInWindow(location)
                    val rect = Rect(
                        location[0], location[1],
                        location[0] + view.width, location[1] + view.height
                    )

                    PixelCopy.request(
                        window, rect, bitmap,
                        { copyResult ->
                            Handler(Looper.getMainLooper()).post {
                                if (copyResult == PixelCopy.SUCCESS) {
                                    coroutineScope.launch {
                                        revealRadius.snapTo(0f)
                                        revealAnchor = resolveAnchor(anchor)
                                        snapshot = bitmap.asImageBitmap()
                                        onAction()

                                        // Raise the initial alpha slightly (0.45) to make the theme color more prominent.
                                        darkOverlayAlpha.snapTo(0.45f)

                                        launch {
                                            delay(250)
                                            onAnimationMidway()
                                        }

                                        launch {
                                            darkOverlayAlpha.animateTo(
                                                targetValue = 0f,
                                                animationSpec = tween(durationMillis = 600)
                                            )
                                        }

                                        revealRadius.animateTo(
                                            targetValue = maxRevealRadius(resolveAnchor(anchor)),
                                            animationSpec = tween(durationMillis = 600)
                                        )

                                        snapshot = null
                                        isRevealing = false
                                        onAnimationEnd()
                                    }
                                } else {
                                    onAction()
                                    isRevealing = false
                                    onAnimationMidway()
                                    onAnimationEnd()
                                }
                            }
                        },
                        Handler(Looper.getMainLooper())
                    )
                } else {
                    onAction()
                    onAnimationMidway()
                    onAnimationEnd()
                }
            }

            override fun triggerCoverReveal(
                anchor: Offset,
                coverColor: Color,
                onAction: () -> Unit
            ) {
                if (isRevealing) return
                isRevealing = true
                val resolved = resolveAnchor(anchor)
                coroutineScope.launch {
                    revealRadius.snapTo(0f)
                    revealAnchor = resolved
                    darkOverlayAlpha.snapTo(0f)
                    snapshot = null
                    maskColor = coverColor
                    onAction()
                    revealRadius.animateTo(
                        targetValue = maxRevealRadius(resolved),
                        animationSpec = tween(durationMillis = 600)
                    )
                    maskColor = null
                    isRevealing = false
                }
            }
        }
    }

    CompositionLocalProvider(LocalThemeRevealController provides controller) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Bottom layer: the real content UI
            content()

            val image = snapshot
            val cover = maskColor
            if (image != null || cover != null) {
                if (image != null && darkOverlayAlpha.value > 0f) {
                    // ====== Tinted ambient shadow ======
                    // 1. Take the accent color of the newly applied theme
                    val primaryColor = LocalZToolColorScheme.current.primary

                    // 2. Blend pure black with the accent color
                    val shadowTint = lerp(Color.Black, primaryColor, 0.50f)

                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(shadowTint.copy(alpha = darkOverlayAlpha.value))
                    )
                }

                // Top layer: screenshot of the old UI, or solid launch mask, cut open by the expanding circle
                Canvas(modifier = Modifier.fillMaxSize()) {
                    drawContext.canvas.saveLayer(
                        androidx.compose.ui.geometry.Rect(Offset.Zero, size),
                        Paint()
                    )

                    image?.let { drawImage(it) }
                    cover?.let { drawRect(color = it) }

                    drawCircle(
                        color = Color.Black,
                        radius = revealRadius.value,
                        center = if (revealAnchor.isUnspecified) {
                            Offset(size.width, size.height / 2f)
                        } else {
                            revealAnchor
                        },
                        blendMode = BlendMode.Clear
                    )

                    drawContext.canvas.restore()
                }
            }
        }
    }
}
