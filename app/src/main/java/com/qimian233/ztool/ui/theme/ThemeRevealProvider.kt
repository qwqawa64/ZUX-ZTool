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
import androidx.compose.ui.graphics.*
import androidx.compose.ui.platform.LocalView
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.hypot

val LocalThemeRevealController = staticCompositionLocalOf<ThemeRevealController> {
    error("ThemeRevealController not provided")
}

interface ThemeRevealController {
    fun triggerReveal(
        onAction: () -> Unit,
        onAnimationMidway: () -> Unit = {},
        onAnimationEnd: () -> Unit = {}
    )
}

tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

@Composable
fun ThemeRevealProvider(content: @Composable () -> Unit) {
    val view = LocalView.current
    var snapshot by remember { mutableStateOf<ImageBitmap?>(null) }
    val coroutineScope = rememberCoroutineScope()
    var isRevealing by remember { mutableStateOf(false) }

    val revealRadius = remember { Animatable(0f) }
    val darkOverlayAlpha = remember { Animatable(0f) }

    val controller = remember {
        object : ThemeRevealController {
            override fun triggerReveal(
                onAction: () -> Unit,
                onAnimationMidway: () -> Unit,
                onAnimationEnd: () -> Unit
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
                                    snapshot = bitmap.asImageBitmap()
                                    onAction()

                                    val maxRadius = hypot(view.width.toFloat(), view.height / 2f)

                                    coroutineScope.launch {
                                        revealRadius.snapTo(0f)
                                        // 为了让主题颜色更加明显，初始透明度可以稍微提高到 0.45
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
                                            targetValue = maxRadius,
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
        }
    }

    CompositionLocalProvider(LocalThemeRevealController provides controller) {
        Box(modifier = Modifier.fillMaxSize()) {
            // 最底层：真实的内容UI层
            content()

            snapshot?.let { image ->
                // ====== 核心改动点：有色环境阴影 ======
                // 1. 获取刚刚切换后的新主题的强调色
                val primaryColor = LocalZToolColorScheme.current.primary

                // 2. 将纯黑与强调色混合。
                val shadowTint = lerp(Color.Black, primaryColor, 0.50f)

                if (darkOverlayAlpha.value > 0f) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(shadowTint.copy(alpha = darkOverlayAlpha.value))
                    )
                }

                // 顶层：旧界面的截图
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val bounds = androidx.compose.ui.geometry.Rect(Offset.Zero, size)
                    drawContext.canvas.saveLayer(bounds, Paint())

                    drawImage(image)

                    drawCircle(
                        color = Color.Black,
                        radius = revealRadius.value,
                        center = Offset(size.width, size.height / 2f),
                        blendMode = BlendMode.Clear
                    )

                    drawContext.canvas.restore()
                }
            }
        }
    }
}
