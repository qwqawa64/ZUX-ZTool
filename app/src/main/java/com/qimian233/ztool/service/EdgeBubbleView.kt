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
 * Each bubble owns a small overlay window (capsule-sized, LAYOUT_NO_LIMITS lets the
 * window itself hang offscreen — same approach as the Oplus handle whose frame was
 * e.g. [0,650][28,914]). States follow the Oplus spec (FULL / HALF / free drag);
 * values from docs/research/oplus-float-handle-gesture-spec.md.
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
        private const val SCREEN_MARGIN_DP = 8f
        private const val HALF_PEEK_DP = 16f
        private const val VERTICAL_LIMIT_DP = 48f
        private const val HALF_HIDE_DELAY_MS = 50L
        private const val SNAP_ANIM_MS = 260L
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
    private val screenMarginPx = dip(SCREEN_MARGIN_DP).toInt()
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
    private var xAnimator: ValueAnimator? = null

    val layoutParams: WindowManager.LayoutParams = WindowManager.LayoutParams().apply {
        type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        format = android.graphics.PixelFormat.TRANSLUCENT
        width = pillSizePx
        height = pillSizePx
        gravity = Gravity.TOP or Gravity.START
        layoutInDisplayCutoutMode =
            WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        x = fullX()
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
                // Long press arms free dragging; actual movement is handled in onTouchEvent.
                dragging = true
                parent?.requestDisallowInterceptTouchEvent(true)
            }
        }
    )

    init {
        pillPaint.color = if (isDarkTheme()) BG_COLOR_DARK else BG_COLOR_LIGHT
        updateWindowPos(animate = false)
    }

    fun setAppIcon(drawable: Drawable?) {
        appIcon = drawable
        invalidate()
    }

    // region window position management

    private fun dip(v: Float): Float = v * density

    private fun fullX(): Int = if (side == SIDE_LEFT) -screenMarginPx
    else screenWidth - pillSizePx + screenMarginPx

    private fun halfX(): Int = if (side == SIDE_LEFT) -(pillSizePx - halfPeekPx)
    else screenWidth - halfPeekPx

    private fun clampY(top: Int): Int {
        val limit = dip(VERTICAL_LIMIT_DP).toInt()
        return top.coerceIn(limit, screenHeight - pillSizePx - limit)
    }

    private fun updateWindowPos(animate: Boolean, targetX: Int = modeX(), targetY: Int = clampY(centerY - pillSizePx / 2)) {
        xAnimator?.cancel()
        xAnimator = null
        if (!animate) {
            layoutParams.x = targetX
            layoutParams.y = targetY
            postApply()
            return
        }
        val fromX = layoutParams.x
        val fromY = layoutParams.y
        xAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = SNAP_ANIM_MS
            interpolator = OvershootInterpolator(0.8f)
            addUpdateListener { anim ->
                val t = anim.animatedValue as Float
                layoutParams.x = (fromX + (targetX - fromX) * t).toInt()
                layoutParams.y = (fromY + (targetY - fromY) * t).toInt()
                postApply()
            }
            start()
        }
    }

    private fun modeX(): Int = if (mode == MODE_FULL) fullX() else halfX()

    private fun postApply() {
        if (removed) return
        try {
            wm.updateViewLayout(this, layoutParams)
        } catch (_: Exception) {
        }
    }

    private fun scheduleHalfHide() {
        mainHandler.postDelayed({
            if (!dragging && mode != MODE_HALF && !removed) {
                mode = MODE_HALF
                updateWindowPos(animate = true)
            }
        }, HALF_HIDE_DELAY_MS)
    }

    fun expandToFull() {
        if (removed) return
        mode = MODE_FULL
        updateWindowPos(animate = true)
    }

    fun release() {
        removed = true
        xAnimator?.cancel()
    }

    // endregion

    // region touch handling

    override fun onTouchEvent(event: MotionEvent): Boolean {
        gestureDetector.onTouchEvent(event)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                mainHandler.removeCallbacksAndMessages(null)
                xAnimator?.cancel()
                downRawX = event.rawX
                downRawY = event.rawY
                downWinX = layoutParams.x
                downWinY = layoutParams.y
                if (mode == MODE_HALF) {
                    mode = MODE_FULL
                    updateWindowPos(animate = true)
                }
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = event.rawX - downRawX
                val dy = event.rawY - downRawY
                val slop = ViewConfiguration.get(context).scaledTouchSlop
                if (dragging || dx * dx + dy * dy > slop * slop) {
                    dragging = true
                    layoutParams.x = downWinX + dx.toInt()
                    layoutParams.y = clampY(downWinY + dy.toInt())
                    postApply()
                }
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (dragging) {
                    dragging = false
                    // Snap to the nearest edge, remember the y ratio, then half-hide.
                    val windowCenterX = layoutParams.x + pillSizePx / 2
                    side = if (windowCenterX < screenWidth / 2) SIDE_LEFT else SIDE_RIGHT
                    centerY = layoutParams.y + pillSizePx / 2
                    listener.onBubbleSettled(taskId, side, centerY.toFloat() / screenHeight)
                    updateWindowPos(animate = true)
                    scheduleHalfHide()
                } else {
                    // Plain tap: stay FULL briefly then re-hide (Oplus full→half 500 ms
                    // after settle; keep the shorter 50 ms release rule for taps that
                    // did not restore, restore itself removes the view).
                    if (mode == MODE_FULL) {
                        scheduleHalfHide()
                    }
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
