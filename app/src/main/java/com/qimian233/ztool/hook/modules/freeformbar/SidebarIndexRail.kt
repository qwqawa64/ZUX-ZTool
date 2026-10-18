package com.qimian233.ztool.hook.modules.freeformbar

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.qimian233.ztool.hook.base.ModuleLog

/** Where the rail gets its letters and their grid rows from. */
internal interface SidebarLetterSource {

    /** Letter to the grid row its heading occupies, in grid order. */
    fun groups(): List<Pair<String, Int>>

    /** The letter of the group the grid row [position] belongs to. */
    fun letterAt(position: Int): String?
}

/**
 * A–Z rail for the all-apps panel, occupying the slot the host reserved for its own
 * `zui.widget.AlphabeticIndexBar`, which is hidden: touching that bar shows a PopupWindow whose
 * token is already invalid, which raises `BadTokenException` in the host process.
 *
 * Letters and their grid rows come from the panel's own row model, so the rail keeps matching the
 * sections once the grid grew letter headings.
 */
internal class SidebarIndexRail(
    classLoader: ClassLoader,
    private val logger: ModuleLog
) {

    private val hostBarClass: Class<*>? =
        runCatching { classLoader.loadClass("zui.widget.AlphabeticIndexBar") }.getOrNull()

    private var rail: LinearLayout? = null
    private var grid: View? = null
    private var source: SidebarLetterSource? = null
    private var letters: List<String> = emptyList()
    private var positions: List<Int> = emptyList()
    private var itemHeight: Int = 0
    private var focused: Int = -1
    private var inactiveColor: Int = DEFAULT_INACTIVE
    private var focusedColor: Int = DEFAULT_FOCUSED
    private var focusFillColor: Int = DEFAULT_FOCUS_FILL
    private val letterViews = ArrayList<TextView>()
    private var scrollListener: ViewTreeObserver.OnScrollChangedListener? = null

    /** Builds or refreshes the rail for the panel's current sections. */
    fun attach(
        panel: ViewGroup,
        grid: View,
        source: SidebarLetterSource,
        colors: SidebarPalette
    ) {
        this.grid = grid
        this.source = source
        inactiveColor = colors.railInactive
        focusedColor = colors.railFocusedText
        focusFillColor = colors.railFocusFill
        val groups = source.groups()
        letters = groups.map { it.first }
        positions = groups.map { it.second }
        if (letters.isEmpty()) return
        val hostBar = findHostBar(panel)
        hostBar?.visibility = View.GONE
        val host = hostBar?.parent as? ViewGroup ?: panel
        itemHeight = itemHeightFor(host)
        val existing = rail
        if (existing == null) {
            rail = buildRail(host.context, hostBar?.layoutParams).also { host.addView(it) }
        } else if (existing.parent !== host) {
            // The host re-inflates the panel on a configuration change, which leaves the rail in a
            // panel that is no longer on screen; move it to the host the panel has now.
            (existing.parent as? ViewGroup)?.removeView(existing)
            host.addView(existing)
        }
        focused = -1
        rail?.visibility = View.VISIBLE
        render()
        watchScroll(grid)
        logger.info("index rail attached with " + letters.size + " letters")
    }

    /** Hides the rail while the panel has no sections to jump to. */
    fun hide() {
        rail?.visibility = View.GONE
    }

    /** Keeps the highlight on the group at the top of the grid while the user scrolls it. */
    private fun watchScroll(grid: View) {
        scrollListener?.let { stale ->
            runCatching { grid.viewTreeObserver.removeOnScrollChangedListener(stale) }
        }
        val listener = ViewTreeObserver.OnScrollChangedListener { syncToScroll() }
        scrollListener = listener
        grid.viewTreeObserver.addOnScrollChangedListener(listener)
    }

    private fun syncToScroll() {
        val position = firstVisiblePosition() ?: return
        val letter = source?.letterAt(position) ?: return
        val index = letters.indexOf(letter)
        if (index < 0 || index == focused) return
        val previous = focused
        focused = index
        applyFocus(previous, index)
    }

    /** The grid's first visible adapter position, via its layout manager. */
    private fun firstVisiblePosition(): Int? = runCatching {
        val target = grid ?: return null
        val manager = target.javaClass.getMethod("getLayoutManager").invoke(target) ?: return null
        val position = manager.javaClass
            .getMethod("findFirstVisibleItemPosition")
            .invoke(manager) as Int
        position.takeIf { it >= 0 }
    }.getOrNull()

    private fun buildRail(context: Context, hostParams: ViewGroup.LayoutParams?): LinearLayout =
        LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            val inset = (RAIL_INSET_DP * context.resources.displayMetrics.density).toInt()
            setPadding(inset, inset, inset, inset)
            layoutParams = hostParams ?: FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.END or Gravity.CENTER_VERTICAL
            )
            setOnTouchListener { view, event -> onTouch(view, event) }
        }

    private fun render() {
        val container = rail ?: return
        container.removeAllViews()
        letterViews.clear()
        letters.forEachIndexed { index, letter ->
            val view = TextView(container.context).apply {
                text = letter
                textSize = LETTER_SP
                gravity = Gravity.CENTER
                setTextColor(inactiveColor)
                // Without this the glyph sits low in its box, so a centred disc looks high.
                includeFontPadding = false
                layoutParams = LinearLayout.LayoutParams(itemHeight, itemHeight)
            }
            letterViews.add(view)
            container.addView(view)
            if (index == focused) style(view, true, false)
        }
    }

    /** Focus moves without rebuilding, so the highlight can animate instead of being recreated. */
    private fun applyFocus(previous: Int, current: Int) {
        letterViews.getOrNull(previous)?.let { style(it, false, true) }
        letterViews.getOrNull(current)?.let { style(it, true, true) }
    }

    private fun style(view: TextView, active: Boolean, animate: Boolean) {
        view.setTextColor(if (active) focusedColor else inactiveColor)
        view.background = if (active) focusBubble() else null
        val scale = if (active) FOCUSED_SCALE else 1f
        if (animate) {
            view.animate().scaleX(scale).scaleY(scale).setDuration(FOCUS_ANIM_MS).start()
        } else {
            view.scaleX = scale
            view.scaleY = scale
        }
    }

    private fun focusBubble(): GradientDrawable = GradientDrawable().apply {
        shape = GradientDrawable.OVAL
        setColor(focusFillColor)
    }

    private fun onTouch(view: View, event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                val index = (event.y / itemHeight).toInt().coerceIn(0, letters.lastIndex)
                focus(index)
            }
        }
        return true
    }

    private fun focus(index: Int) {
        if (index != focused) {
            val previous = focused
            focused = index
            applyFocus(previous, index)
        }
        val target = grid ?: return
        val position = positions.getOrNull(index) ?: return
        runCatching { target.javaClass.getMethod("scrollToPosition", Int::class.javaPrimitiveType) }
            .onSuccess { it.invoke(target, position) }
            .onFailure { logger.warn("could not scroll the all-apps grid: " + it.message) }
    }

    private fun findHostBar(root: View): View? {
        val target = hostBarClass ?: return null
        if (target.isInstance(root)) return root
        if (root !is ViewGroup) return null
        for (index in 0 until root.childCount) {
            findHostBar(root.getChildAt(index))?.let { return it }
        }
        return null
    }

    /** Prefers 14dp per letter, shrinking towards 8dp when the panel is short. */
    private fun itemHeightFor(host: ViewGroup): Int {
        val density = host.resources.displayMetrics.density
        val preferred = (ITEM_DP * density).toInt()
        val available = host.height
        if (available <= 0 || letters.isEmpty()) return preferred
        return (available / letters.size).coerceIn((MIN_ITEM_DP * density).toInt(), preferred)
    }

    private companion object {
        const val ITEM_DP = 17
        const val MIN_ITEM_DP = 9
        const val RAIL_INSET_DP = 6
        const val LETTER_SP = 13f
        /** Kept small: the glyph has to stay inside the disc drawn behind it. */
        const val FOCUSED_SCALE = 1.12f
        const val FOCUS_ANIM_MS = 90L

        /** Used only before the first [attach], i.e. when no palette has been resolved yet. */
        const val DEFAULT_INACTIVE = 0x99FFFFFF.toInt()
        const val DEFAULT_FOCUSED = 0xFFFFFFFF.toInt()
        const val DEFAULT_FOCUS_FILL = 0x59FFFFFF
    }
}
