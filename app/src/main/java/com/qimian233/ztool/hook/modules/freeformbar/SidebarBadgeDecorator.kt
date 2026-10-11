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
    private var positionMethod: java.lang.reflect.Method? = null
    private var iconViewId = 0
    private var labelViewId = 0
    private var watching = false
    private var warnedNoAnchor = false
    private var warnedUnresolved = false
    private var reported = false

    private var apps: () -> List<Any> = { emptyList() }
    private var isMember: (Any) -> Boolean = { false }
    private var onToggle: (Any) -> Unit = {}
    private var canAdd: Boolean = true

    /**
     * Shows or refreshes the badges; call again whenever the panel is re-shown.
     *
     * [appList] is a provider rather than a list because the host swaps its own all-apps list
     * out, and a captured copy would then index the wrong app.
     */
    fun refresh(
        gridView: View,
        appList: () -> List<Any>,
        member: (Any) -> Boolean,
        toggle: (Any) -> Unit,
        addAllowed: Boolean
    ) {
        val container = gridView as? ViewGroup ?: return
        grid = container
        apps = appList
        isMember = member
        onToggle = toggle
        canAdd = addAllowed
        if (!watching) {
            container.viewTreeObserver.addOnScrollChangedListener { redraw() }
            // Item views only exist after the grid lays out, and scrolling may never happen.
            container.viewTreeObserver.addOnGlobalLayoutListener { redraw() }
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
        val list = apps()
        var decorated = 0
        var unresolved = 0
        for (index in 0 until container.childCount) {
            val item = container.getChildAt(index) as? ViewGroup ?: continue
            // The host disables the row for apps already open in a small window, which would
            // also block our badge and our touch listener; while picking apps the row must stay
            // interactive, and its own click is swallowed below anyway.
            item.isEnabled = true
            item.setOnTouchListener { _, _ -> true }
            val app = appFor(container, item, list)
            if (app == null) {
                unresolved++
                continue
            }
            val member = isMember(app)
            // Apps already in the drawer are masked and carry no badge: they are removed from
            // the drawer row's own disc. A full drawer masks everything, because nothing more
            // can be added at all.
            item.findViewById<View>(iconId(container))?.alpha =
                if (member || !canAdd) MASKED_ALPHA else 1f
            val existing = item.findViewById<View>(badgeId) as? TextView
            if (member) {
                existing?.visibility = View.GONE
                continue
            }
            val badge = existing ?: addBadge(item) ?: continue
            decorated++
            val enabled = canAdd
            (badge as? TextView)?.let { view ->
                if (view.visibility != View.VISIBLE || view.isEnabled != enabled) {
                    view.visibility = View.VISIBLE
                    view.isEnabled = enabled
                    view.alpha = if (enabled) 1f else DISABLED_ALPHA
                    view.background = SidebarBadgeStyle.disc(
                        if (enabled) SidebarBadgeStyle.ADD_COLOR else SidebarBadgeStyle.MUTED_COLOR
                    )
                }
            }
            badge.setOnClickListener(
                if (enabled) {
                    // Resolve the app when the tap happens: the item view can be rebound to a
                    // different app between a redraw and the tap, so a captured app goes stale.
                    View.OnClickListener { appFor(container, item, apps())?.let(onToggle) }
                } else {
                    null
                }
            )
        }
        if (decorated > 0 && !reported) {
            reported = true
            logger.info("add/remove badges attached to " + decorated + " icons")
        }
        if (unresolved > 0 && !warnedUnresolved) {
            warnedUnresolved = true
            logger.warn("badges: " + unresolved + " item views could not be matched to an app")
        }
    }

    /**
     * The app a grid item currently shows: by adapter position when the grid reports one, and
     * otherwise by matching the item's label, so an item the RecyclerView cannot place still
     * gets its badge and its tap handled.
     */
    private fun appFor(container: ViewGroup, item: View, list: List<Any>): Any? {
        list.getOrNull(adapterPosition(container, item))?.let { return it }
        val label = labelOf(item) ?: return null
        return list.firstOrNull { SidebarReflect.labelOf(it) == label }
    }

    private fun labelOf(item: View): String? {
        val id = labelId(item) ?: return null
        val text = (item.findViewById<View>(id) as? TextView)?.text?.toString()
        return text?.takeIf { it.isNotEmpty() }
    }

    private fun labelId(item: View): Int? {
        if (labelViewId == 0) labelViewId = resourceId(item, "appname_tv")
        return labelViewId.takeIf { it != 0 }
    }

    private fun addBadge(item: ViewGroup): View? {
        val anchorId = iconId(item)
        if (anchorId == 0 || item !is RelativeLayout) {
            if (!warnedNoAnchor) {
                warnedNoAnchor = true
                logger.warn("all-apps item has no icon anchor, skipping badges")
            }
            return null
        }
        val badge = SidebarBadgeStyle.view(
            item.context,
            SidebarStrings.get(
                item.context, SidebarStrings.STRING_GLYPH_ADD, SidebarStrings.FALLBACK_GLYPH_ADD
            ),
            SidebarStrings.get(
                item.context, SidebarStrings.STRING_ADD, SidebarStrings.FALLBACK_ADD
            ),
            SidebarBadgeStyle.ADD_COLOR
        ).apply {
            id = badgeId
            val size = SidebarBadgeStyle.size(item.context)
            layoutParams = RelativeLayout.LayoutParams(size, size).apply {
                addRule(RelativeLayout.ALIGN_TOP, anchorId)
                addRule(RelativeLayout.ALIGN_END, anchorId)
            }
        }
        item.addView(badge)
        return badge
    }

    private fun adapterPosition(container: ViewGroup, item: View): Int {
        val method = positionMethod ?: runCatching {
            container.javaClass.getMethod("getChildAdapterPosition", View::class.java)
        }.getOrNull()?.also { positionMethod = it } ?: return -1
        return runCatching { method.invoke(container, item) as Int }.getOrDefault(-1)
    }

    private fun rebind(container: ViewGroup) {
        runCatching {
            container.javaClass.getMethod("getAdapter").invoke(container)?.let { adapter ->
                adapter.javaClass.getMethod("notifyDataSetChanged").invoke(adapter)
            }
        }
    }

    private fun resourceId(view: View, name: String): Int =
        view.resources.getIdentifier(name, "id", hostPackage)

    private fun iconId(container: ViewGroup): Int {
        if (iconViewId == 0) iconViewId = resourceId(container, "appicon_iv")
        return iconViewId
    }

    private companion object {
        const val DISABLED_ALPHA = 0.35f
        const val MASKED_ALPHA = 0.35f
    }
}
