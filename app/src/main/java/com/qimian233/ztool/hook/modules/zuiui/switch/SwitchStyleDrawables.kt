package com.qimian233.ztool.hook.modules.zuiui.switch

import android.content.Context
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Outline
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.PorterDuff
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.os.SystemClock
import android.util.TypedValue
import android.view.View
import com.qimian233.ztool.data.zuiui.SwitchMark

/** Strokes used by the drawn marks, sized from the circle they sit in. */
internal object SwitchGlyphs {

    private val path = Path()

    fun drawCheck(
        canvas: Canvas,
        paint: Paint,
        centerX: Float,
        centerY: Float,
        size: Float,
        color: Int,
        alpha: Int
    ) {
        prepare(paint, color, alpha, size)
        path.reset()
        path.moveTo(centerX - size * 0.42f, centerY)
        path.lineTo(centerX - size * 0.10f, centerY + size * 0.32f)
        path.lineTo(centerX + size * 0.45f, centerY - size * 0.34f)
        canvas.drawPath(path, paint)
    }

    fun drawCross(
        canvas: Canvas,
        paint: Paint,
        centerX: Float,
        centerY: Float,
        size: Float,
        color: Int,
        alpha: Int
    ) {
        prepare(paint, color, alpha, size)
        val arm = size * 0.36f
        canvas.drawLine(centerX - arm, centerY - arm, centerX + arm, centerY + arm, paint)
        canvas.drawLine(centerX - arm, centerY + arm, centerX + arm, centerY - arm, paint)
    }

    private fun prepare(paint: Paint, color: Int, alpha: Int, size: Float) {
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = size * 0.15f
        paint.strokeCap = Paint.Cap.ROUND
        paint.strokeJoin = Paint.Join.ROUND
        paint.color = color
        paint.alpha = alpha
    }
}

/**
 * Thumb wrapper for the indicator style: draws the host thumb, then one mark centred in it.
 *
 * The host is the ZUI animated nine-patch, so its callback is forwarded through this
 * wrapper to keep the checked/unchecked transition animating.
 */
internal class GlyphThumbDrawable(
    private val host: android.graphics.drawable.Drawable,
    private val mark: SwitchMark,
    private val checkColor: Int,
    private val crossColor: Int,
    initiallyChecked: Boolean,
    initiallyEnabled: Boolean
) : Drawable(), Drawable.Callback {

    private val glyphPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var checked = initiallyChecked
    private var enabled = initiallyEnabled

    init {
        host.callback = this
    }

    override fun isStateful(): Boolean = true

    override fun setState(state: IntArray): Boolean {
        val hostChanged = host.setState(state)
        return super.setState(state) or hostChanged
    }

    override fun onStateChange(state: IntArray): Boolean {
        val newChecked = state.contains(android.R.attr.state_checked)
        val newEnabled = state.contains(android.R.attr.state_enabled)
        val changed = newChecked != checked || newEnabled != enabled
        checked = newChecked
        enabled = newEnabled
        return changed
    }

    override fun draw(canvas: Canvas) {
        val area = bounds
        host.setBounds(area)
        host.draw(canvas)
        if (mark == SwitchMark.NONE || area.isEmpty) return
        val showCheck = checked && (mark == SwitchMark.CHECK || mark == SwitchMark.BOTH)
        val showCross = !checked && (mark == SwitchMark.CROSS || mark == SwitchMark.BOTH)
        if (!showCheck && !showCross) return
        val size = minOf(area.width(), area.height()) * 0.34f
        val centerX = area.exactCenterX()
        val centerY = area.exactCenterY()
        val alpha = if (enabled) 255 else 120
        if (showCheck) {
            SwitchGlyphs.drawCheck(canvas, glyphPaint, centerX, centerY, size, checkColor, alpha)
        } else {
            SwitchGlyphs.drawCross(canvas, glyphPaint, centerX, centerY, size, crossColor, alpha)
        }
    }

    override fun onBoundsChange(bounds: Rect) {
        host.setBounds(bounds)
    }

    override fun getIntrinsicWidth(): Int = host.intrinsicWidth

    override fun getIntrinsicHeight(): Int = host.intrinsicHeight

    override fun getMinimumWidth(): Int = host.minimumWidth

    override fun getMinimumHeight(): Int = host.minimumHeight

    override fun getPadding(padding: Rect): Boolean = host.getPadding(padding)

    override fun getOutline(outline: Outline) = host.getOutline(outline)

    @Suppress("OVERRIDE_DEPRECATION")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT

    override fun setAlpha(alpha: Int) {
        host.alpha = alpha
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        host.colorFilter = colorFilter
    }

    override fun setTintList(tint: ColorStateList?) {
        host.setTintList(tint)
    }

    override fun setTintMode(tintMode: PorterDuff.Mode?) {
        host.setTintMode(tintMode)
    }

    override fun setHotspot(x: Float, y: Float) {
        host.setHotspot(x, y)
    }

    override fun setHotspotBounds(left: Int, top: Int, right: Int, bottom: Int) {
        host.setHotspotBounds(left, top, right, bottom)
    }

    override fun setVisible(visible: Boolean, restart: Boolean): Boolean =
        host.setVisible(visible, restart) or super.setVisible(visible, restart)

    override fun jumpToCurrentState() {
        host.jumpToCurrentState()
    }

    override fun invalidateDrawable(who: Drawable) {
        invalidateSelf()
    }

    override fun scheduleDrawable(
        who: Drawable,
        what: Runnable,
        `when`: Long
    ) {
        scheduleSelf(what, `when`)
    }

    override fun unscheduleDrawable(who: Drawable, what: Runnable) {
        unscheduleSelf(what)
    }
}

/** Invisible thumb that keeps `Switch`'s 24dp hit box and measurement when the track is drawn. */
internal class TransparentThumbDrawable(private val sizePx: Int) : Drawable() {

    override fun getIntrinsicWidth(): Int = sizePx

    override fun getIntrinsicHeight(): Int = sizePx

    override fun getPadding(padding: Rect): Boolean {
        padding.setEmpty()
        return false
    }

    @Suppress("OVERRIDE_DEPRECATION")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT

    override fun setAlpha(alpha: Int) = Unit

    override fun setColorFilter(colorFilter: ColorFilter?) = Unit

    override fun draw(canvas: Canvas) = Unit
}

/**
 * Material 3 switch painted into the track drawable's bounds.
 *
 * `Switch` positions that drawable on the switch rectangle, so the whole control is drawn
 * here: 52x32dp pill, a constant 24dp thumb sliding 200ms, outline while off, optional mark.
 */
internal class M3SwitchDrawable(
    private val mark: SwitchMark,
    private val trackOn: Int,
    private val trackOff: Int,
    private val outline: Int,
    private val thumbOn: Int,
    private val thumbOff: Int,
    private val iconOn: Int,
    private val iconOff: Int,
    initiallyChecked: Boolean,
    private val intrinsicHeightPx: Int
) : Drawable() {

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val thumbPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val outlinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val glyphPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    private var checked = initiallyChecked
    private var enabled = true
    private var pressed = false
    private var userAlpha = 255
    private var colorFilter: ColorFilter? = null

    private var from = if (checked) 1f else 0f
    private var to = from
    private var position = from
    private var startedAt = 0L
    private var animating = false

    private val ticker = object : Runnable {
        override fun run() {
            val elapsed = SystemClock.uptimeMillis() - startedAt
            val fraction = (elapsed.toFloat() / ANIMATION_MS).coerceIn(0f, 1f)
            position = from + (to - from) * (1f - (1f - fraction) * (1f - fraction))
            invalidateSelf()
            if (fraction < 1f) {
                scheduleSelf(this, SystemClock.uptimeMillis() + FRAME_MS)
            } else {
                animating = false
            }
        }
    }

    override fun isStateful(): Boolean = true

    override fun onStateChange(state: IntArray): Boolean {
        val newChecked = state.contains(android.R.attr.state_checked)
        val newEnabled = state.contains(android.R.attr.state_enabled)
        val newPressed = state.contains(android.R.attr.state_pressed)
        if (newChecked != checked) {
            checked = newChecked
            animateTo(if (checked) 1f else 0f)
        }
        enabled = newEnabled
        pressed = newPressed
        return true
    }

    private fun animateTo(target: Float) {
        if (!isVisible || callback == null) {
            position = target
            from = target
            to = target
            invalidateSelf()
            return
        }
        from = position
        to = target
        startedAt = SystemClock.uptimeMillis()
        animating = true
        unscheduleSelf(ticker)
        scheduleSelf(ticker, startedAt + FRAME_MS)
    }

    override fun draw(canvas: Canvas) {
        val area = visibleArea() ?: return
        val width = area.width()
        val height = area.height()
        if (width <= 0f || height <= 0f) return

        val alpha = when {
            !enabled -> (userAlpha * DISABLED_ALPHA / 255f).toInt()
            else -> userAlpha
        }
        val left = area.left
        val top = area.top
        val right = area.right
        val bottom = area.bottom
        val radius = height / 2f
        val progress = position.coerceIn(0f, 1f)

        fillPaint.colorFilter = colorFilter
        fillPaint.color = blend(trackOff, trackOn, progress)
        fillPaint.alpha = alpha
        canvas.drawRoundRect(left, top, right, bottom, radius, radius, fillPaint)

        if (progress < 1f) {
            val stroke = height * OUTLINE_WIDTH_RATIO
            outlinePaint.colorFilter = colorFilter
            outlinePaint.color = outline
            outlinePaint.alpha = (alpha * OUTLINE_OFF_ALPHA / 255f).toInt()
            outlinePaint.strokeWidth = stroke
            canvas.drawRoundRect(
                left + stroke / 2f, top + stroke / 2f,
                right - stroke / 2f, bottom - stroke / 2f,
                radius, radius, outlinePaint
            )
        }

        val travel = width - height
        val thumbCenter = height / 2f + travel * progress
        val centerX = if (isRtl()) right - thumbCenter else left + thumbCenter
        val centerY = top + height / 2f
        var thumbRadius = height * THUMB_RADIUS_RATIO
        if (pressed) thumbRadius += height * PRESSED_GROW_RATIO

        thumbPaint.colorFilter = colorFilter
        thumbPaint.color = blend(thumbOff, thumbOn, progress)
        thumbPaint.alpha = alpha
        canvas.drawCircle(centerX, centerY, thumbRadius, thumbPaint)

        drawMark(canvas, centerX, centerY, thumbRadius, alpha)
    }

    /**
     * The 52dp box can be wider than a narrow host slot; clip the drawing to the view
     * rectangle so the view's canvas clip does not cut off the left cap.
     */
    private fun visibleArea(): RectF? {
        val host = callback as? View
        var left = bounds.left.toFloat()
        var top = bounds.top.toFloat()
        var right = bounds.right.toFloat()
        var bottom = bounds.bottom.toFloat()
        if (host != null) {
            if (left < 0f) left = 0f
            if (top < 0f) top = 0f
            if (right > host.width) right = host.width.toFloat()
            if (bottom > host.height) bottom = host.height.toFloat()
        }
        return if (right - left >= 1f && bottom - top >= 1f) {
            RectF(left, top, right, bottom)
        } else {
            null
        }
    }

    private fun drawMark(canvas: Canvas, centerX: Float, centerY: Float, radius: Float, alpha: Int) {
        if (mark == SwitchMark.NONE) return
        val showCheck = checked && (mark == SwitchMark.CHECK || mark == SwitchMark.BOTH)
        val showCross = !checked && (mark == SwitchMark.CROSS || mark == SwitchMark.BOTH)
        if (!showCheck && !showCross) return
        val size = radius * GLYPH_RATIO
        glyphPaint.colorFilter = colorFilter
        if (showCheck) {
            SwitchGlyphs.drawCheck(canvas, glyphPaint, centerX, centerY, size, iconOn, alpha)
        } else {
            SwitchGlyphs.drawCross(canvas, glyphPaint, centerX, centerY, size, iconOff, alpha)
        }
    }

    /** `Switch` keeps the track as the only callback receiver, so this is the view locale. */
    private fun isRtl(): Boolean {
        val host = callback
        return if (host is View) host.layoutDirection == View.LAYOUT_DIRECTION_RTL
        else layoutDirection == View.LAYOUT_DIRECTION_RTL
    }

    private fun blend(start: Int, end: Int, fraction: Float): Int {
        val inverse = 1f - fraction
        return Color.argb(
            (Color.alpha(start) * inverse + Color.alpha(end) * fraction).toInt(),
            (Color.red(start) * inverse + Color.red(end) * fraction).toInt(),
            (Color.green(start) * inverse + Color.green(end) * fraction).toInt(),
            (Color.blue(start) * inverse + Color.blue(end) * fraction).toInt()
        )
    }

    override fun onBoundsChange(bounds: Rect) = Unit

    override fun getIntrinsicWidth(): Int = (intrinsicHeightPx * M3_WIDTH_RATIO).toInt()

    override fun getIntrinsicHeight(): Int = intrinsicHeightPx

    override fun getPadding(padding: Rect): Boolean {
        padding.setEmpty()
        return false
    }

    @Suppress("OVERRIDE_DEPRECATION")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT

    override fun setAlpha(alpha: Int) {
        userAlpha = alpha
        invalidateSelf()
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        this.colorFilter = colorFilter
        invalidateSelf()
    }

    override fun setVisible(visible: Boolean, restart: Boolean): Boolean {
        val changed = super.setVisible(visible, restart)
        if (!visible) {
            unscheduleSelf(ticker)
            animating = false
        }
        return changed
    }

    override fun jumpToCurrentState() {
        unscheduleSelf(ticker)
        animating = false
        position = if (checked) 1f else 0f
        from = position
        to = position
        invalidateSelf()
    }

    private companion object {
        const val ANIMATION_MS = 200f
        const val FRAME_MS = 16L
        const val OUTLINE_WIDTH_RATIO = 2f / 32f
        const val OUTLINE_OFF_ALPHA = 180
        const val THUMB_RADIUS_RATIO = 0.375f
        const val PRESSED_GROW_RATIO = 0.035f
        const val GLYPH_RATIO = 1.25f
        const val M3_WIDTH_RATIO = 52f / 32f
        const val DISABLED_ALPHA = 100f
    }
}

/** Colour lookups against the host theme and by resource name, all with explicit fallbacks. */
internal object SwitchThemeColors {

    fun color(context: Context, attr: Int, fallback: Int): Int {
        val value = TypedValue()
        if (!context.theme.resolveAttribute(attr, value, true)) return fallback
        if (value.resourceId != 0) {
            return runCatching { context.getColor(value.resourceId) }.getOrDefault(fallback)
        }
        val isColor = value.type >= TypedValue.TYPE_FIRST_COLOR_INT &&
            value.type <= TypedValue.TYPE_LAST_COLOR_INT
        return if (isColor) value.data else fallback
    }

    fun isNight(context: Context): Boolean =
        (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES

    /** Resolves a host resource by name; pruned APKs simply return null. */
    fun colorStateList(context: Context, name: String): ColorStateList? {
        val id = context.resources.getIdentifier(name, "color", context.packageName)
        if (id == 0) return null
        return runCatching { context.getColorStateList(id) }.getOrNull()
    }

    fun themeColorStateList(context: Context, attr: Int): ColorStateList? {
        val value = TypedValue()
        if (!context.theme.resolveAttribute(attr, value, true)) return null
        if (value.resourceId != 0) {
            return runCatching { context.getColorStateList(value.resourceId) }.getOrNull()
        }
        val isColor = value.type >= TypedValue.TYPE_FIRST_COLOR_INT &&
            value.type <= TypedValue.TYPE_LAST_COLOR_INT
        return if (isColor) ColorStateList.valueOf(value.data) else null
    }
}

/** Defaults when a ZUI theme lacks the accent or outline. */
internal object SwitchFallbackColors {
    const val ACCENT = 0xFF3482FF.toInt()
    const val CROSS = 0xFF7A7A7A.toInt()
    const val OUTLINE = 0xFF79747E.toInt()
}
