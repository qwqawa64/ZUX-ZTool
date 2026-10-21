package com.qimian233.ztool.hook.modules.zuiui.indicator

/**
 * CSS `cubic-bezier(x1, y1, x2, y2)` evaluation for animation timing functions.
 *
 * The endpoints are fixed at (0,0) and (1,1); x is invertible by bisection, which is
 * accurate to ~1/65536 after [SOLVE_STEPS] steps and cheap enough for a 60 fps clock.
 */
internal object Easing {

    fun cubicBezier(x1: Float, y1: Float, x2: Float, y2: Float): (Float) -> Float = { progress ->
        val x = progress.coerceIn(0f, 1f)
        var low = 0f
        var high = 1f
        var t = x
        repeat(SOLVE_STEPS) {
            t = (low + high) / 2f
            if (curve(t, x1, x2) < x) low = t else high = t
        }
        curve(t, y1, y2)
    }

    /** Cubic bezier with P0 = 0 and P3 = 1, so only the two controls matter. */
    private fun curve(t: Float, control1: Float, control2: Float): Float {
        val inverse = 1f - t
        return 3f * inverse * inverse * t * control1 +
            3f * inverse * t * t * control2 +
            t * t * t
    }

    private const val SOLVE_STEPS = 16
}
