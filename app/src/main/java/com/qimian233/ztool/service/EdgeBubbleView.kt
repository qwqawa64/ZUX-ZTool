package com.qimian233.ztool.service

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Outline
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.GestureDetector
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewOutlineProvider
import android.view.WindowManager
import android.view.animation.OvershootInterpolator
import androidx.core.graphics.toColorInt
import kotlin.math.abs
import kotlin.math.min

/**
 * The edge-bubble capsule for one minimized freeform task — 1:1 replica of the Oplus
 * `FloatHandleView` visual stack (see docs/research/oplus-float-handle-gesture-spec.md
 * and the oplus-framework-res sources):
 *
 * - Container: `zoom_float_handle_view_relative_layout` — width = icon(48) + 2×inner(8)
 *   + screenMargin(30) = 94dp, height = icon + 2×inner = 64dp, OPAQUE background
 *   #f0f0f0 (light) / #444444 (dark), smooth-rounded outline (squircle, weight≈3,
 *   radius 18dp), clipToOutline, elevation 3.33dp with #38000000 shadows.
 * - Icon: 48dp, top/bottom margin 8dp; marginStart = screenMargin+inner (38dp) on the
 *   LEFT side, inner (8dp) on the RIGHT side — the screenMargin part of the container
 *   always hangs offscreen toward the docked edge (that IS the "connection pad"; there
 *   is no separate connector view in Oplus).
 * - FULL: only the screenMargin part offscreen; HALF: all but `collapse` (16dp)
 *   offscreen, icon invisible. Mode switch animates translationX inside a window wide
 *   enough for both positions (no per-frame updateViewLayout).
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

        // Oplus values (oplus-framework-res + FloatHandleView/FloatHandleUIParams).
        private const val ICON_SIZE_DP = 48f
        private const val INNER_MARGIN_DP = 8f
        private const val SCREEN_MARGIN_DP = 30f
        // How much of the container hangs offscreen in FULL. Oplus reuses
        // screen_margin here, but that retracts the bubble 30dp when pressed and
        // breaks the "attached to the edge" look — keep the FULL hang small.
        private const val FULL_HANG_DP = 24f
        private const val CONTAINER_RADIUS_DP = 18f
        private const val COLLAPSE_DP = 16f
        private const val SQUIRCLE_WEIGHT = 3f
        private const val ELEVATION_DP = 3.33f
        /** Whole-bubble alpha in the collapsed state. */
        private const val HALF_ALPHA = 0.45f
        private const val SHADOW_COLOR_INT = 0x38000000

        val BG_LIGHT = "#f0f0f0".toColorInt()
        val BG_DARK = "#444444".toColorInt()

        private const val HALF_HIDE_DELAY_MS = 50L
        private const val APPEAR_FULL_MS = 600L
        private const val MODE_ANIM_MS = 200L
        // Fling thresholds (spec): fast branch and slow-long-distance branch.
        private const val FLING_VELOCITY_PX = 1000f
        private const val FLING_DISTANCE_PX = 100f
    }

    private val wm = context.getSystemService(WindowManager::class.java)
    private val mainHandler = Handler(Looper.getMainLooper())
    private val density = context.resources.displayMetrics.density
    private val screenWidth: Int
    private val screenHeight: Int

    init {
        val bounds = try {
            context.getSystemService(WindowManager::class.java).maximumWindowMetrics.bounds
        } catch (_: Throwable) { null }
        screenWidth = bounds?.width() ?: context.resources.displayMetrics.widthPixels
        screenHeight = bounds?.height() ?: context.resources.displayMetrics.heightPixels
    }

    private val iconPx = dip(ICON_SIZE_DP).toInt()
    private val innerPx = dip(INNER_MARGIN_DP).toInt()
    private val screenMarginPx = dip(SCREEN_MARGIN_DP).toInt()
    private val fullHangPx = dip(FULL_HANG_DP).toInt()
    private val containerWpx = iconPx + innerPx * 2 + screenMarginPx
    private val containerHpx = iconPx + innerPx * 2
    private val collapsePx = dip(COLLAPSE_DP).toInt()
    /** Distance between FULL and HALF window positions along x. */
    private val modeShiftPx = containerWpx - collapsePx - fullHangPx

    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val containerRect = RectF()

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
    private var appIcon: Drawable? = null

    val layoutParams: WindowManager.LayoutParams = WindowManager.LayoutParams().apply {
        type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        format = android.graphics.PixelFormat.TRANSLUCENT
        width = containerWpx + modeShiftPx
        height = containerHpx
        gravity = Gravity.TOP or Gravity.START
        layoutInDisplayCutoutMode =
            WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        x = anchorX(side)
        y = clampY(centerY - containerHpx / 2)
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
                dragging = true
                parent?.requestDisallowInterceptTouchEvent(true)
            }
        }
    )

    init {
        bgPaint.color = if (isDarkTheme()) BG_DARK else BG_LIGHT
        // Oplus: smooth-rounded outline + elevation + colored shadows + clipToOutline.
        outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                val path = squirclePath(
                    containerOriginX(), 0f,
                    containerWpx.toFloat(), containerHpx.toFloat()
                )
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    outline.setPath(path)
                } else {
                    @Suppress("DEPRECATION")
                    outline.setConvexPath(path)
                }
            }
        }
        clipToOutline = true
        stateListAnimator = null
        elevation = dip(ELEVATION_DP)
        outlineAmbientShadowColor = SHADOW_COLOR_INT
        outlineSpotShadowColor = SHADOW_COLOR_INT
        // Window anchor is the FULL position — no initial translation needed.
        scheduleHalfHide(APPEAR_FULL_MS)
    }

    fun setAppIcon(drawable: Drawable?) {
        appIcon = drawable
        invalidate()
    }

    // region geometry

    private fun dip(v: Float): Float = v * density

    /**
     * Window x anchor = the FULL position (only [FULL_HANG_DP] hangs offscreen).
     * HALF is reached with translation ±modeShift: LEFT slides further out
     * (negative), RIGHT further in-positive — leaving exactly `collapse` px visible.
     */
    private fun anchorX(side: Int): Int =
        if (side == SIDE_LEFT) -(fullHangPx + modeShiftPx)
        else screenWidth - (containerWpx - fullHangPx)

    /** View translation for the HALF (collapsed) mode on the given side. */
    private fun halfTranslation(side: Int): Float {
        val t = modeShiftPx.toFloat()
        return if (side == SIDE_LEFT) -t else t
    }

    /** Container origin inside the view for the current side. */
    private fun containerOriginX(): Float =
        if (side == SIDE_LEFT) modeShiftPx.toFloat() else 0f

    private fun clampY(top: Int): Int {
        val limit = dip(8f).toInt()
        return top.coerceIn(limit, screenHeight - containerHpx - limit)
    }

    private fun postApply() {
        if (removed) return
        try {
            wm.updateViewLayout(this, layoutParams)
        } catch (_: Exception) {
        }
    }

    private fun animateToMode(target: Int) {
        modeAnimator?.cancel()
        val to = if (target == MODE_FULL) 0f else halfTranslation(side)
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
        val targetAlpha = if (target == MODE_HALF) HALF_ALPHA else 1f
        if (animate) {
            animateToMode(target)
            // Whole-view alpha: covers background, icon and shadow uniformly on
            // every collapse path (first collapse included).
            animate().alpha(targetAlpha)
                .setDuration(MODE_ANIM_MS)
                .start()
        } else {
            translationX = if (target == MODE_FULL) 0f else halfTranslation(side)
            alpha = targetAlpha
        }
    }

    private fun scheduleHalfHide(delay: Long = HALF_HIDE_DELAY_MS) {
        mainHandler.postDelayed({
            if (!dragging && mode != MODE_HALF && !removed) {
                setMode(MODE_HALF, animate = true)
            }
        }, delay)
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
                        modeAnimator?.cancel()
                        // Bake the current translation into the window position and
                        // rebase the drag origin.
                        layoutParams.x += translationX.toInt()
                        translationX = 0f
                        postApply()
                        downWinX = layoutParams.x
                        downWinY = layoutParams.y
                        downRawX = event.rawX
                        downRawY = event.rawY
                    }
                    dragging = true
                    // Horizontal position locked to the FULL anchor: the bubble slides
                    // along its edge vertically only. Side switching is done by the
                    // user via the window itself, never by dragging across the screen.
                    layoutParams.x = anchorX(side)
                    layoutParams.y = clampY(downWinY + (event.rawY - downRawY).toInt())
                    postApply()
                }
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (dragging) {
                    dragging = false
                    // Side is locked at drag start: crossing the screen midline while
                    // dragging must NOT flip the dock side (screenMargin anchor would
                    // jump to the other edge). Release always returns to the origin
                    // side's anchor.
                    centerY = layoutParams.y + containerHpx / 2
                    listener.onBubbleSettled(taskId, side, centerY.toFloat() / screenHeight)
                    // Snap window x/y back to the FULL anchor while translation
                    // interpolates to halfTranslation — window anchor encodes FULL,
                    // translation encodes the mode; both must land consistently.
                    val fromX = layoutParams.x.toFloat()
                    val targetX = anchorX(side).toFloat()
                    val fromY = layoutParams.y.toFloat()
                    val targetY = clampY(centerY - containerHpx / 2).toFloat()
                    val fromT = translationX
                    // Window anchor is the FULL position: snap there, show the full
                    // bubble for 600ms, then the scheduled collapse slides it to HALF.
                    val targetT = 0f
                    mode = MODE_FULL
                    modeAnimator?.cancel()
                    snapAnimator?.cancel()
                    snapAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
                        duration = MODE_ANIM_MS
                        interpolator = OvershootInterpolator(0.6f)
                        addUpdateListener { anim ->
                            val t = anim.animatedValue as Float
                            layoutParams.x = (fromX + (targetX - fromX) * t).toInt()
                            layoutParams.y = (fromY + (targetY - fromY) * t).toInt()
                            translationX = fromT + (targetT - fromT) * t
                            postApply()
                        }
                        start()
                    }
                    scheduleHalfHide(APPEAR_FULL_MS)
                } else if (mode == MODE_FULL) {
                    // Every expansion gets the full 600ms showcase before collapsing
                    // (the default 50ms here made the icon appear to vanish).
                    scheduleHalfHide(APPEAR_FULL_MS)
                }
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    // endregion

    // region drawing

    /**
     * Smooth-rounded ("squircle", OplusOutlineAdapter weight≈3) container path —
     * per-corner cubics whose handle extends past the circle radius along both edges.
     */
    private fun squirclePath(l: Float, t: Float, w: Float, h: Float): Path {
        val r = dip(CONTAINER_RADIUS_DP)
        val span = min(r * (SQUIRCLE_WEIGHT / 2f), min(w, h) / 2f - 1f)
        val handle = span * 0.6f
        val right = l + w; val bottom = t + h
        return Path().apply {
            moveTo(l + span, t)
            lineTo(right - span, t)
            cubicTo(right - span + handle, t, right, t + span - handle, right, t + span)
            lineTo(right, bottom - span)
            cubicTo(right, bottom - span + handle, right - span + handle, bottom, right - span, bottom)
            lineTo(l + span, bottom)
            cubicTo(l + span - handle, bottom, l, bottom - span + handle, l, bottom - span)
            lineTo(l, t + span)
            cubicTo(l, t + span - handle, l + span - handle, t, l + span, t)
            close()
        }
    }

    override fun onDraw(canvas: Canvas) {
        val ox = containerOriginX()
        // Opaque theme background, drawn only inside the clipped container region.
        containerRect.set(ox, 0f, ox + containerWpx, containerHpx.toFloat())
        canvas.drawRoundRect(
            containerRect, dip(CONTAINER_RADIUS_DP), dip(CONTAINER_RADIUS_DP), bgPaint)
        // Icon: marginStart 38dp (LEFT) / 8dp (RIGHT), 8dp top/bottom. Whole-bubble
        // dimming is applied via view-level alpha in setMode.
        val iconStart = ox + (if (side == SIDE_LEFT) screenMarginPx + innerPx else innerPx)
        val icon = appIcon ?: return
        icon.mutate().alpha = 255
        icon.setBounds(
            iconStart.toInt(), innerPx,
            (iconStart + iconPx).toInt(), (innerPx + iconPx)
        )
        icon.draw(canvas)
    }

    private fun isDarkTheme(): Boolean {
        val uiMode = context.resources.configuration.uiMode and
            android.content.res.Configuration.UI_MODE_NIGHT_MASK
        return uiMode == android.content.res.Configuration.UI_MODE_NIGHT_YES
    }

    // endregion
}
