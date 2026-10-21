package com.qimian233.ztool.hook.modules.zuiui.indicator

import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.drawable.Animatable
import android.graphics.drawable.Drawable
import android.os.SystemClock
import android.view.View
import kotlin.math.min

/**
 * Frame clock shared by the animation styles that replace the ZUI spinner.
 *
 * Subclasses draw a single frame from [elapsedMs]; this class owns the ticker, the
 * visible/attached gating and the square letterboxing. `ProgressBar` calls `start()`
 * and `stop()` but never asks for a frame, so the ticker lives here.
 * See docs/research/zui_progress_dialog_x_indicator.md.
 */
abstract class LoadingIndicatorDrawable : Drawable(), Animatable {

    protected val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }

    private var running = false
    private var startedAt = 0L

    private val ticker = object : Runnable {
        override fun run() {
            if (!running) return
            if (!isAttached()) {
                running = false
                return
            }
            invalidateSelf()
            scheduleSelf(this, SystemClock.uptimeMillis() + FRAME_INTERVAL_MS)
        }
    }

    /** The view bounds are a wide rectangle on ZUI, so a square frame is centred in them. */
    override fun draw(canvas: Canvas) {
        val size = min(bounds.width(), bounds.height())
        if (size <= 0) return
        val left = bounds.left + (bounds.width() - size) / 2f
        val top = bounds.top + (bounds.height() - size) / 2f
        drawFrame(canvas, left, top, size.toFloat(), elapsedMs())
    }

    /** Draws one frame inside the square at ([left], [top]) with an edge of [size] px. */
    protected abstract fun drawFrame(
        canvas: Canvas,
        left: Float,
        top: Float,
        size: Float,
        elapsedMs: Long
    )

    override fun start() {
        if (running || !isVisible || !isAttached()) return
        running = true
        startedAt = SystemClock.uptimeMillis()
        invalidateSelf()
        scheduleSelf(ticker, SystemClock.uptimeMillis() + FRAME_INTERVAL_MS)
    }

    override fun stop() {
        if (!running) return
        running = false
        unscheduleSelf(ticker)
    }

    override fun isRunning(): Boolean = running

    @Suppress("OVERRIDE_DEPRECATION")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT

    override fun setVisible(visible: Boolean, restart: Boolean): Boolean {
        val changed = super.setVisible(visible, restart)
        if (visible) start() else stop()
        return changed
    }

    override fun setAlpha(alpha: Int) {
        paint.alpha = alpha
        invalidateSelf()
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        paint.colorFilter = colorFilter
        invalidateSelf()
    }

    /** The callback is the ProgressBar; a detached host would never draw a scheduled frame. */
    private fun isAttached(): Boolean {
        val host = callback ?: return false
        return host !is View || host.isAttachedToWindow
    }

    private fun elapsedMs(): Long =
        if (startedAt == 0L) 0L else SystemClock.uptimeMillis() - startedAt

    private companion object {
        const val FRAME_INTERVAL_MS = 16L
    }
}
