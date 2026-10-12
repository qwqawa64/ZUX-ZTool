package com.qimian233.ztool.hook.modules.freeformbar

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.InsetDrawable
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.qimian233.ztool.hook.base.ModuleLog
import java.lang.reflect.Method

/**
 * A–Z rail for the all-apps panel, occupying the slot the host reserved for its own
 * `zui.widget.AlphabeticIndexBar`.
 *
 * The host's bar is hidden rather than used: on touch it shows its own PopupWindow, which fails
 * with `BadTokenException` because the drawer's PopupWindow token is no longer valid, killing the
 * process. This rail only scrolls the grid, so it cannot fail that way.
 *
 * Letters come from `PinYinUtils.toPinYin(appName)`, the helper the host sorts the grid with, so
 * each letter maps to that group's first grid position.
 */
internal class SidebarIndexRail(
    classLoader: ClassLoader,
    private val logger: ModuleLog
) {

    private val hostBarClass: Class<*>? =
        runCatching { classLoader.loadClass("zui.widget.AlphabeticIndexBar") }.getOrNull()

    private val toPinYin: Method? = runCatching {
        classLoader.loadClass("com.zui.freeform.sidebar.utils.PinYinUtils")
            .getMethod("toPinYin", String::class.java)
    }.getOrNull()

    private var rail: LinearLayout? = null
    private var grid: View? = null
    private var letters: List<String> = emptyList()
    private var positions: List<Int> = emptyList()
    private var itemHeight: Int = 0
    private var focused: Int = -1
    private val letterViews = ArrayList<TextView>()

    /** Builds or refreshes the rail for [apps] in grid order, anchored where the host bar sits. */
    fun attach(panel: ViewGroup, grid: View, apps: List<Any>) {
        this.grid = grid
        computeGroups(apps)
        if (letters.isEmpty()) return
        val hostBar = findHostBar(panel)
        hostBar?.visibility = View.GONE
        val host = hostBar?.parent as? ViewGroup ?: panel
        itemHeight = itemHeightFor(host)
        val existing = rail
        if (existing == null) {
            rail = buildRail(host.context, hostBar?.layoutParams).also { host.addView(it) }
        } else if (existing.parent == null) {
            host.addView(existing)
        }
        render()
        logger.info("index rail attached with " + letters.size + " letters")
    }

    private fun computeGroups(apps: List<Any>) {
        val newLetters = ArrayList<String>()
        val newPositions = ArrayList<Int>()
        apps.forEachIndexed { index, app ->
            val letter = letterOf(app)
            if (newLetters.lastOrNull() != letter) {
                newLetters.add(letter)
                newPositions.add(index)
            }
        }
        letters = newLetters
        positions = newPositions
    }

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
                setTextColor(INACTIVE)
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
        view.setTextColor(if (active) Color.WHITE else INACTIVE)
        view.background = if (active) focusBubble(view.context) else null
        val scale = if (active) FOCUSED_SCALE else 1f
        if (animate) {
            view.animate().scaleX(scale).scaleY(scale).setDuration(FOCUS_ANIM_MS).start()
        } else {
            view.scaleX = scale
            view.scaleY = scale
        }
    }

    private fun focusBubble(context: Context): Drawable {
        val inset = (BUBBLE_INSET_DP * context.resources.displayMetrics.density).toInt()
        val disc = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(FOCUS_FILL)
        }
        return InsetDrawable(disc, inset, inset, inset, inset)
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

    private fun letterOf(app: Any): String {
        val name = SidebarReflect.labelOf(app) ?: return OTHER
        val pinyin = runCatching { toPinYin?.invoke(null, name) as? String }.getOrNull() ?: name
        val first = pinyin.firstOrNull()?.uppercaseChar() ?: return OTHER
        return if (first in 'A'..'Z') first.toString() else OTHER
    }

    private companion object {
        const val OTHER = "#"
        const val ITEM_DP = 17
        const val MIN_ITEM_DP = 9
        const val RAIL_INSET_DP = 6
        const val BUBBLE_INSET_DP = 2
        const val LETTER_SP = 13f
        const val FOCUSED_SCALE = 1.25f
        const val FOCUS_ANIM_MS = 90L
        const val INACTIVE = 0x99FFFFFF.toInt()
        const val FOCUS_FILL = 0x59FFFFFF
    }
}
