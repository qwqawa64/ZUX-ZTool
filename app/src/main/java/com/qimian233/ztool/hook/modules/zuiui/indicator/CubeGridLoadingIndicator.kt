package com.qimian233.ztool.hook.modules.zuiui.indicator

import android.graphics.Canvas
import android.graphics.RectF

/**
 * SpinKit cube grid: nine squares that shrink and grow along a diagonal wave.
 *
 * Timing follows `spinkit@2.0.1` `.sk-grid`: a 1300 ms cycle, `(2 - row + col) * 100 ms`
 * delays and a `1 → 0 → 1` scale over the first 70% of the cycle. The stock style has no
 * gap, which flashes into a solid block at dialog size, so the cells are inset.
 */
class CubeGridLoadingIndicator(
    color: Int,
    private val gapFraction: Float = GAP_FRACTION
) : LoadingIndicatorDrawable() {

    private val cube = RectF()

    init {
        paint.color = color
    }

    override fun drawFrame(
        canvas: Canvas,
        left: Float,
        top: Float,
        size: Float,
        elapsedMs: Long
    ) {
        val cell = size / CELLS
        val side = cell * (1f - gapFraction)
        for (row in 0 until CELLS) {
            for (col in 0 until CELLS) {
                val scale = scaleAt(elapsedMs, (2 - row + col) * DELAY_STEP_MS)
                if (scale <= MIN_SCALE) continue
                val half = side * scale / 2f
                val centerX = left + cell * (col + 0.5f)
                val centerY = top + cell * (row + 0.5f)
                cube.set(centerX - half, centerY - half, centerX + half, centerY + half)
                canvas.drawRect(cube, paint)
            }
        }
    }

    private fun scaleAt(elapsedMs: Long, delayMs: Long): Float {
        val phase = (((elapsedMs - delayMs) % PERIOD_MS) + PERIOD_MS) % PERIOD_MS /
            PERIOD_MS.toFloat()
        return when {
            phase < SHRINK_END -> 1f - ease(phase / SHRINK_END)
            phase < GROW_END -> ease((phase - SHRINK_END) / (GROW_END - SHRINK_END))
            else -> 1f
        }
    }

    private companion object {
        /** `cubic-bezier(0.42, 0, 0.58, 1)`, the CSS `ease-in-out` of the keyframes. */
        val EASING = Easing.cubicBezier(0.42f, 0f, 0.58f, 1f)

        const val CELLS = 3
        const val PERIOD_MS = 1300L
        const val DELAY_STEP_MS = 100L

        /** Keyframes `0%`/`70%`/`100%` → scale 1, `35%` → scale 0. */
        const val SHRINK_END = 0.35f
        const val GROW_END = 0.70f

        /** Inset per cell; keeps the grid readable while a cell is at full size. */
        const val GAP_FRACTION = 0.10f
        const val MIN_SCALE = 0.01f

        fun ease(t: Float): Float = EASING(t)
    }
}
