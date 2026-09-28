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
        private const val ICON_SIZE_DP = 40f
        private const val PILL_INNER_MARGIN_DP = 6f
        private const val HALF_PEEK_DP = 16f
        private const val VERTICAL_LIMIT_DP = 48f
        private const val HALF_HIDE_DELAY_MS = 50L
        private const val APPEAR_FULL_MS = 600L
        private const val SNAP_ANIM_MS = 260L
        private const val MODE_ANIM_MS = 180L
        // Fling thresholds (spec): fast branch and slow-long-distance branch.
        private const val FLING_VELOCITY_PX = 1000f
        private const val FLING_DISTANCE_PX = 100f

        private val BG_COLOR_DARK = Color.argb(230, 32, 32, 34)
        private val BG_COLOR_LIGHT = Color.argb(230, 244, 244, 246)
    }

    private val wm = context.getSystemService(WindowManager::class.java)
    private val mainHandler = Handler(Looper.getMainLooper())
    private val density = context.resources.displayMetrics.density
    private val screenWidth = context.resources.displayMetrics.widthPixels
    private val screenHeight = context.resources.displayMetrics.heightPixels

    private val pillSizePx = dip(ICON_SIZE_DP + PILL_INNER_MARGIN_DP * 2).toInt()
    private val halfPeekPx = dip(HALF_PEEK_DP).toInt()

    private val pillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
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
        width = 2 * pillSizePx - halfPeekPx
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
        pillPaint.color = if (isDarkTheme()) BG_COLOR_DARK else BG_COLOR_LIGHT
        // Start in HALF after a brief fully-visible moment (Oplus appear behaviour).
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

    /** View translation for FULL mode on the given side. */
    private fun fullTranslation(side: Int): Float =
        if (side == SIDE_LEFT) (pillSizePx - halfPeekPx).toFloat()
        else -(pillSizePx - halfPeekPx).toFloat()

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
                        // coordinates only and the pill doesn't jump.
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
                    mode = MODE_HALF
                    snapAnimator?.cancel()
                    snapAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
                        duration = SNAP_ANIM_MS
                        interpolator = OvershootInterpolator(0.6f)
                        addUpdateListener { anim ->
                            val t = anim.animatedValue as Float
                            layoutParams.x = (fromX + (targetX - fromX) * t).toInt()
                            layoutParams.y = (fromY + (targetY - fromY) * t).toInt()
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
        pillRect.set(inset, inset, pillSizePx - inset, pillSizePx - inset)
        canvas.drawRoundRect(pillRect, dip(PILL_CORNER_DP), dip(PILL_CORNER_DP), pillPaint)
        val icon = appIcon ?: return
        val alpha = if (mode == MODE_HALF) 0.5f else 1f
        icon.mutate().alpha = (alpha * 255).toInt()
        icon.setBounds(
            inset.toInt(), inset.toInt(),
            (pillSizePx - inset).toInt(), (pillSizePx - inset).toInt()
        )
        icon.draw(canvas)
    }

    private fun isDarkTheme(): Boolean {
        val mode = context.resources.configuration.uiMode and
            android.content.res.Configuration.UI_MODE_NIGHT_MASK
        return mode == android.content.res.Configuration.UI_MODE_NIGHT_YES
    }

    // endregion
}
