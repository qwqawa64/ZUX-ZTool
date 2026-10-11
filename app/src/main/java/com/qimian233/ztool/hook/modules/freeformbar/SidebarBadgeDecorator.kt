package com.qimian233.ztool.hook.modules.freeformbar

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.RelativeLayout
import android.widget.TextView
import com.qimian233.ztool.hook.base.ModuleLog

/**
 * Puts an add/remove badge on the top-right corner of every all-apps icon while the drawer is in
 * edit mode.
 *
 * The grid is the host's own RecyclerView, so instead of replacing its adapter the badges are
 * injected into the item views. Each badge's state is re-derived from its item's current adapter
 * position, which keeps it correct across view recycling. Item taps are swallowed with an
 * OnTouchListener rather than by clearing the adapter's OnClickListener, because the adapter
 * re-installs that listener on every bind.
 */
internal class SidebarBadgeDecorator(
    private val hostPackage: String,
    private val logger: ModuleLog
) {

    private val badgeId = View.generateViewId()
    private var grid: ViewGroup? = null
    private var watching = false

    private var apps: List<Any> = emptyList()
    private var isMember: (Any) -> Boolean = { false }
    private var onToggle: (Any) -> Unit = {}

    /** Shows or refreshes the badges for [apps]; call again whenever the panel is re-shown. */
    fun refresh(
        gridView: View,
        appList: List<Any>,
        member: (Any) -> Boolean,
        toggle: (Any) -> Unit
    ) {
        val container = gridView as? ViewGroup ?: return
        grid = container
        apps = appList
        isMember = member
        onToggle = toggle
        if (!watching) {
            container.viewTreeObserver.addOnScrollChangedListener { redraw() }
            watching = true
        }
        redraw()
    }

    /** Removes the badges and restores normal item taps, e.g. when leaving edit mode. */
    fun clear() {
        val container = grid ?: return
        for (index in 0 until container.childCount) {
            val item = container.getChildAt(index) as? ViewGroup ?: continue
            item.findViewById<View>(badgeId)?.let { (it.parent as? ViewGroup)?.removeView(it) }
            item.setOnTouchListener(null)
        }
        rebind(container)
    }

    private fun redraw() {
        val container = grid ?: return
        for (index in 0 until container.childCount) {
            val item = container.getChildAt(index) as? ViewGroup ?: continue
            val position = adapterPosition(container, item)
            if (position < 0 || position >= apps.size) continue
            val app = apps[position]
            val badge = item.findViewById<View>(badgeId) ?: addBadge(item) ?: continue
            val member = isMember(app)
            (badge as? TextView)?.apply {
                text = SidebarStrings.get(
                    context,
                    if (member) SidebarStrings.STRING_GLYPH_DROP else SidebarStrings.STRING_GLYPH_ADD,
                    if (member) SidebarStrings.FALLBACK_GLYPH_DROP else SidebarStrings.FALLBACK_GLYPH_ADD
                )
                contentDescription = SidebarStrings.get(
                    context,
                    if (member) SidebarStrings.STRING_DROP else SidebarStrings.STRING_ADD,
                    if (member) SidebarStrings.FALLBACK_DROP else SidebarStrings.FALLBACK_ADD
                )
                background = disc(if (member) DROP_COLOR else ADD_COLOR)
            }
            badge.setOnClickListener { onToggle(app) }
            item.setOnTouchListener { _, _ -> true }
        }
    }

    private fun addBadge(item: ViewGroup): View? {
        val iconId = resourceId(item, "appicon_iv")
        if (iconId == 0 || item !is RelativeLayout) {
            logger.warn("all-apps item has no icon anchor, skipping badges")
            return null
        }
        val size = (BADGE_DP * item.resources.displayMetrics.density).toInt()
        val badge = TextView(item.context).apply {
            id = badgeId
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            textSize = 13f
            layoutParams = RelativeLayout.LayoutParams(size, size).apply {
                addRule(RelativeLayout.ALIGN_TOP, iconId)
                addRule(RelativeLayout.ALIGN_END, iconId)
            }
        }
        item.addView(badge)
        return badge
    }

    private fun disc(color: Int): GradientDrawable = GradientDrawable().apply {
        shape = GradientDrawable.OVAL
        setColor(color)
    }

    private fun adapterPosition(container: ViewGroup, item: View): Int =
        runCatching {
            container.javaClass.getMethod("getChildAdapterPosition", View::class.java)
                .invoke(container, item) as Int
        }.getOrDefault(-1)

    private fun rebind(container: ViewGroup) {
        runCatching {
            container.javaClass.getMethod("getAdapter").invoke(container)?.let { adapter ->
                adapter.javaClass.getMethod("notifyDataSetChanged").invoke(adapter)
            }
        }
    }

    private fun resourceId(view: View, name: String): Int =
        view.resources.getIdentifier(name, "id", hostPackage)

    private companion object {
        const val BADGE_DP = 20
        const val ADD_COLOR = 0xE62196F3.toInt()
        const val DROP_COLOR = 0xE6E53935.toInt()
    }
}
