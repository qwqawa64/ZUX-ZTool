package com.qimian233.ztool.hook.modules.zuiui.indicator

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF

/**
 * Material Design 2 indeterminate circle.
 *
 * Rebuilt from Material Components Web (`mdc-circular-progress`): the container turns
 * 360° per 1568 ms, the spinner layer 1080° per 5332 ms, and the left/right semi-arcs
 * swing 265° ↔ 130° per 1333 ms. Those two semi-arcs are the expansion itself, so the
 * visible sweep runs from 10° to 280°. Single colour, so the user colour applies.
 */
class CircularLoadingIndicator(color: Int) : LoadingIndicatorDrawable() {

    private val arc = RectF()

    init {
        paint.style = Paint.Style.STROKE
        paint.strokeCap = Paint.Cap.ROUND
        paint.color = color
    }

    override fun drawFrame(
        canvas: Canvas,
        left: Float,
        top: Float,
        size: Float,
        elapsedMs: Long
    ) {
        val strokeWidth = size * STROKE_FRACTION
        paint.strokeWidth = strokeWidth
        val inset = strokeWidth / 2f
        arc.set(left + inset, top + inset, left + size - inset, top + size - inset)

        val spin = clipperSpin(elapsedMs)
        val start = spin + layerTurn(elapsedMs) + containerTurn(elapsedMs)
        canvas.drawArc(arc, start, SWEEP_BASE - spin * 2f, false, paint)
    }

    /** 265° → 130° → 265° per cycle; the semi-arcs ease into the reversal like the CSS. */
    private fun clipperSpin(elapsedMs: Long): Float {
        val phase = phase(elapsedMs, CLIPPER_PERIOD_MS)
        val half = if (phase < 0.5f) phase * 2f else (1f - phase) * 2f
        return CLIPPER_MAX - (CLIPPER_MAX - CLIPPER_MIN) * ease(half)
    }

    /** Both rotations are linear in the source; easing between even keyframes averages out. */
    private fun layerTurn(elapsedMs: Long): Float =
        LAYER_TURN * phase(elapsedMs, LAYER_PERIOD_MS)

    private fun containerTurn(elapsedMs: Long): Float =
        FULL_TURN * phase(elapsedMs, CONTAINER_PERIOD_MS)

    private fun phase(elapsedMs: Long, periodMs: Long): Float =
        (elapsedMs % periodMs).toFloat() / periodMs.toFloat()

    private fun ease(t: Float): Float = EASING(t)

    private companion object {
        /** `cubic-bezier(0.4, 0, 0.2, 1)` from the MDC keyframes. */
        val EASING = Easing.cubicBezier(0.4f, 0f, 0.2f, 1f)

        /** M2 draws a 4 dp stroke in a 48 dp box. */
        const val STROKE_FRACTION = 4f / 48f
        const val FULL_TURN = 360f
        const val LAYER_TURN = 1080f

        /** Union of the two clipped semi-arcs: `540 - 2 * spin`, i.e. 280° down to 10°. */
        const val SWEEP_BASE = 540f
        const val CLIPPER_MAX = 265f
        const val CLIPPER_MIN = 130f
        const val CONTAINER_PERIOD_MS = 1568L
        const val LAYER_PERIOD_MS = 5332L
        const val CLIPPER_PERIOD_MS = 1333L
    }
}
