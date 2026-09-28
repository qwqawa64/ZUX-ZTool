package com.qimian233.ztool.service

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.os.Handler
import android.os.Looper
import android.view.GestureDetector
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.view.animation.OvershootInterpolator
import kotlin.math.abs

/**
 * The edge-bubble capsule for one minimized freeform task.
 *
 * Geometry: the overlay window is anchored at the screen edge and sized
 * [2*pill - halfPeek] wide, covering both the FULL and HALF positions of the pill
 * (window x can hang offscreen thanks to LAYOUT_NO_LIMITS). FULL↔HALF animates the
 * view's translationX inside the window — pure view-level, no WindowManager calls
 * per frame (updateViewLayout is far too slow for smooth mode switching). Window
 * position changes only while free-dragging.
 *
 * Timing/threshold values follow docs/research/oplus-float-handle-gesture-spec.md.
 */
class EdgeBubbleView(
    context: Context,
    private val taskId: Int,
    initialSide: Int,
    initialCenterYRatio: Float,
    private val listener: Listener
) : View(context) {

    interface Listener {
        /** Restore the task (tap or qualifying fling). */
        fun onBubbleRestore(taskId: Int)
        /** The bubble settled at `side` (0 left / 1 right) with remembered y ratio. */
        fun onBubbleSettled(taskId: Int, side: Int, centerYRatio: Float)
    }

    companion object {
        private const val SIDE_LEFT = 0
        private const val SIDE_RIGHT = 1
        private const val MODE_FULL = 2
        private const val MODE_HALF = 4

        private const val PILL_CORNER_DP = 16f
        private const val PILL_SIZE_DP = 75f
        private const val PILL_INNER_MARGIN_DP = 6f
        private const val HALF_PEEK_DP = 24f
        /** Light-grey pad connecting the backdrop to the screen edge (FULL state). */
        private const val CONNECTOR_W_DP = 24f
        /** Whole-bubble alpha in HALF state (Oplus "collapsed" dimming). */
        private const val HALF_ALPHA = 0.45f
        private const val VERTICAL_LIMIT_DP = 48f
        private const val HALF_HIDE_DELAY_MS = 50L
        private const val APPEAR_FULL_MS = 600L
        private const val SNAP_ANIM_MS = 260L
        private const val MODE_ANIM_MS = 180L
        // Fling thresholds (spec): fast branch and slow-long-distance branch.
        private const val FLING_VELOCITY_PX = 1000f
        private const val FLING_DISTANCE_PX = 100f

        // Dark rounded backdrop behind the icon, 80% opacity (Oplus alignment).
        private val BG_COLOR = Color.argb(204, 28, 28, 30)
        // Lighter grey pad between backdrop and screen edge.
        private val CONNECTOR_COLOR = Color.argb(217, 168, 168, 170)
    }

    private val wm = context.getSystemService(WindowManager::class.java)
    private val mainHandler = Handler(Looper.getMainLooper())
    private val density = context.resources.displayMetrics.density
    // Full display bounds. maximumWindowMetrics (not currentWindowMetrics!) is required:
    // when ZTool itself runs inside a freeform window, currentWindowMetrics returns that
    // small window's bounds and every edge anchor lands mid-screen.
    private val screenWidth: Int
    private val screenHeight: Int

    init {
        val metricsBounds = try {
            context.getSystemService(WindowManager::class.java)
                .maximumWindowMetrics.bounds
        } catch (_: Throwable) { null }
        screenWidth = metricsBounds?.width() ?: context.resources.displayMetrics.widthPixels
        screenHeight = metricsBounds?.height() ?: context.resources.displayMetrics.heightPixels
    }

    private val pillSizePx = dip(PILL_SIZE_DP).toInt()
    private val halfPeekPx = dip(HALF_PEEK_DP).toInt()
    private val connWpx = dip(CONNECTOR_W_DP).toInt()

    private val pillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        // Nearly-invisible fill: only the shadowLayer halo is wanted, cast outward.
        color = 0x01000000
        setShadowLayer(dip(6f), 0f, dip(2f), 0x88000000.toInt())
    }
    private val connectorPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val pillRect = RectF()
    private var appIcon: Drawable? = null

    private var side = if (initialSide == SIDE_RIGHT) SIDE_RIGHT else SIDE_LEFT
    private var mode = MODE_FULL
    private var centerY = (screenHeight * initialCenterYRatio).toInt()

    private var downRawX = 0f
    private var downRawY = 0f
    private var downWinX = 0
    private var downWinY = 0
    private var dragging = false
    private var removed = false
    private var modeAnimator: ValueAnimator? = null
    private var snapAnimator: ValueAnimator? = null

    val layoutParams: WindowManager.LayoutParams = WindowManager.LayoutParams().apply {
        type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        format = android.graphics.PixelFormat.TRANSLUCENT
        width = 2 * pillSizePx - halfPeekPx + connWpx
        height = pillSizePx
        gravity = Gravity.TOP or Gravity.START
        layoutInDisplayCutoutMode =
            WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        x = anchorX(side)
        y = clampY(centerY - pillSizePx / 2)
    }

    private val gestureDetector = GestureDetector(
        context,
        object : GestureDetector.SimpleOnGestureListener() {
            override fun onSingleTapUp(e: MotionEvent): Boolean {
                listener.onBubbleRestore(taskId)
                return true
            }

            override fun onFling(
                e1: MotionEvent?, e2: MotionEvent, velocityX: Float, velocityY: Float
            ): Boolean {
                val distance = abs(e2.rawX - (e1?.rawX ?: e2.rawX))
                val velocity = abs(velocityX)
                val fast = velocity > FLING_VELOCITY_PX && distance > FLING_DISTANCE_PX
                val slow = velocity > (FLING_VELOCITY_PX + 0.5f) / 5f &&
                    distance > FLING_DISTANCE_PX * 5
                if (fast || slow) {
                    listener.onBubbleRestore(taskId)
                    return true
                }
                return false
            }

            override fun onLongPress(e: MotionEvent) {
                // Long press arms free dragging; movement is handled in onTouchEvent.
                dragging = true
                parent?.requestDisallowInterceptTouchEvent(true)
            }
        }
    )

    init {
        setLayerType(View.LAYER_TYPE_SOFTWARE, null)
        pillPaint.color = BG_COLOR
        connectorPaint.color = CONNECTOR_COLOR
        // Start fully visible (FULL position); translationX defaults to 0 which is the
        // HALF position, so it must be set explicitly.
        translationX = fullTranslation(side)
        // Slide to HALF after a brief fully-visible moment (Oplus appear behaviour).
        scheduleHalfHide(APPEAR_FULL_MS)
    }

    fun setAppIcon(drawable: Drawable?) {
        appIcon = drawable
        invalidate()
    }

    // region geometry

    private fun dip(v: Float): Float = v * density

    /** Window x anchor: HALF position of the pill for the given side. */
    private fun anchorX(side: Int): Int =
        if (side == SIDE_LEFT) -(pillSizePx - halfPeekPx) else screenWidth - halfPeekPx

    /** View translation for FULL mode on the given side (connector fits toward edge). */
    private fun fullTranslation(side: Int): Float {
        val t = (pillSizePx - halfPeekPx + connWpx).toFloat()
        return if (side == SIDE_LEFT) t else -t
    }

    private fun clampY(top: Int): Int {
        val limit = dip(VERTICAL_LIMIT_DP).toInt()
        return top.coerceIn(limit, screenHeight - pillSizePx - limit)
    }

    private fun postApply() {
        if (removed) return
        try {
            wm.updateViewLayout(this, layoutParams)
        } catch (_: Exception) {
        }
    }

    /** Animates the in-window translation between FULL and HALF (cheap, view-level). */
    private fun animateToMode(target: Int) {
        modeAnimator?.cancel()
        val to = if (target == MODE_FULL) fullTranslation(side) else 0f
        val from = translationX
        if (from == to) return
        modeAnimator = ValueAnimator.ofFloat(from, to).apply {
            duration = MODE_ANIM_MS
            interpolator = OvershootInterpolator(0.6f)
            addUpdateListener { anim -> translationX = anim.animatedValue as Float }
            start()
        }
    }

    private fun setMode(target: Int, animate: Boolean) {
        mode = target
        if (animate) animateToMode(target) else translationX = fullTranslation(side)
    }

    private fun scheduleHalfHide(delay: Long = HALF_HIDE_DELAY_MS) {
        mainHandler.postDelayed({
            if (!dragging && mode != MODE_HALF && !removed) {
                setMode(MODE_HALF, animate = true)
            }
        }, delay)
    }

    /** Keeps the window parked at the current edge anchor with the given y. */
    private fun parkAtEdge(targetSide: Int = side, animateY: Boolean = false) {
        side = targetSide
        layoutParams.x = anchorX(side)
        val targetY = clampY(centerY - pillSizePx / 2)
        if (!animateY) {
            layoutParams.y = targetY
            postApply()
            return
        }
        val fromY = layoutParams.y
        snapAnimator?.cancel()
        snapAnimator = ValueAnimator.ofFloat(fromY.toFloat(), targetY.toFloat()).apply {
            duration = SNAP_ANIM_MS
            addUpdateListener { anim ->
                layoutParams.y = (anim.animatedValue as Float).toInt()
                postApply()
            }
            start()
        }
    }

    fun expandToFull() {
        if (removed) return
        setMode(MODE_FULL, animate = true)
    }

    fun release() {
        removed = true
        modeAnimator?.cancel()
        snapAnimator?.cancel()
    }

    // endregion

    // region touch handling

    override fun onTouchEvent(event: MotionEvent): Boolean {
        gestureDetector.onTouchEvent(event)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                mainHandler.removeCallbacksAndMessages(null)
                modeAnimator?.cancel()
                downRawX = event.rawX
                downRawY = event.rawY
                downWinX = layoutParams.x
                downWinY = layoutParams.y
                if (mode == MODE_HALF) {
                    setMode(MODE_FULL, animate = true)
                }
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = event.rawX - downRawX
                val dy = event.rawY - downRawY
                val slop = ViewConfiguration.get(context).scaledTouchSlop
                if (dragging || dx * dx + dy * dy > slop * slop) {
                    if (!dragging) {
                        // Bake the current translation into the window position and
                        // rebase the drag origin, so the drag operates on window
                        // coordinates only and the pill doesn't jump. Cancel any
                        // in-flight mode animation first, or it keeps overwriting
                        // translationX after the bake.
                        modeAnimator?.cancel()
                        layoutParams.x += translationX.toInt()
                        translationX = 0f
                        postApply()
                        downWinX = layoutParams.x
                        downWinY = layoutParams.y
                        downRawX = event.rawX
                        downRawY = event.rawY
                    }
                    dragging = true
                    layoutParams.x = downWinX + (event.rawX - downRawX).toInt()
                    layoutParams.y = clampY(downWinY + (event.rawY - downRawY).toInt())
                    postApply()
                }
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (dragging) {
                    dragging = false
                    val windowCenterX = layoutParams.x + width / 2
                    side = if (windowCenterX < screenWidth / 2) SIDE_LEFT else SIDE_RIGHT
                    centerY = layoutParams.y + pillSizePx / 2
                    listener.onBubbleSettled(taskId, side, centerY.toFloat() / screenHeight)
                    // Snap window x/y back to the edge anchor; translation stays 0,
                    // which is exactly the HALF position at the anchor.
                    val fromX = layoutParams.x.toFloat()
                    val targetX = anchorX(side).toFloat()
                    val fromY = layoutParams.y.toFloat()
                    val targetY = clampY(centerY - pillSizePx / 2).toFloat()
                    val fromT = translationX
                    mode = MODE_HALF
                    modeAnimator?.cancel()
                    snapAnimator?.cancel()
                    snapAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
                        duration = SNAP_ANIM_MS
                        interpolator = OvershootInterpolator(0.6f)
                        addUpdateListener { anim ->
                            val t = anim.animatedValue as Float
                            layoutParams.x = (fromX + (targetX - fromX) * t).toInt()
                            layoutParams.y = (fromY + (targetY - fromY) * t).toInt()
                            // Translation must land on 0 (= HALF at the anchor), or the
                            // pill stays stuck fully visible after the drag.
                            translationX = fromT * (1f - t)
                            postApply()
                        }
                        start()
                    }
                } else if (mode == MODE_FULL) {
                    scheduleHalfHide()
                }
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    // endregion

    // region drawing

    override fun onDraw(canvas: Canvas) {
        val inset = dip(PILL_INNER_MARGIN_DP)
        val corner = dip(PILL_CORNER_DP)
        val dim = if (mode == MODE_HALF) HALF_ALPHA else 1f

        // Backdrop occupies the leading part of the view; the connector extends from
        // it toward the screen edge (visible only in FULL, where the whole bubble is
        // pushed off the edge by the connector width).
        pillRect.set(0f, 0f, pillSizePx.toFloat(), pillSizePx.toFloat())
        val connectorRect = RectF(pillRect)
        if (side == SIDE_LEFT) connectorRect.offset(-connWpx.toFloat(), 0f)
        else connectorRect.offset(connWpx.toFloat(), 0f)

        shadowPaint.alpha = (255 * dim).toInt()
        connectorPaint.alpha = (255 * dim).toInt()
        pillPaint.alpha = (255 * dim).toInt()
        canvas.drawRoundRect(connectorRect, corner, corner, shadowPaint)
        canvas.drawRoundRect(connectorRect, corner, corner, connectorPaint)
        canvas.drawRoundRect(pillRect, corner, corner, shadowPaint)
        canvas.drawRoundRect(pillRect, corner, corner, pillPaint)

        val icon = appIcon ?: return
        // Oplus collapsed state shows no icon at all — it slides out with the bubble.
        val iconAlpha = if (mode == MODE_HALF) 0f else dim
        icon.mutate().alpha = (255 * iconAlpha).toInt()
        icon.setBounds(
            inset.toInt(), inset.toInt(),
            (pillSizePx - inset).toInt(), (pillSizePx - inset).toInt()
        )
        icon.draw(canvas)
    }

    // endregion
}
