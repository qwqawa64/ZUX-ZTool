package com.qimian233.ztool.hook.modules.freeformbar

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.widget.RelativeLayout
import android.widget.TextView
import com.qimian233.ztool.data.keys.LogLevel
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
    private var adapterMethod: java.lang.reflect.Method? = null
    private var listField: java.lang.reflect.Field? = null
    private var iconViewId = 0
    private var labelViewId = 0
    private var watching = false
    private var warnedNoAnchor = false
    private var warnedUnresolved = false
    private var warnedDivergence = false
    private var reported = false
    private var tracePass = false
    private var active = false
    private var lastPosition = -1
    private var lastSource = "none"

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
        active = true
        apps = appList
        isMember = member
        onToggle = toggle
        canAdd = addAllowed
        if (!watching) {
            // A throw here would run inside the host's layout pass and kill its process.
            container.viewTreeObserver.addOnScrollChangedListener {
                runCatching { redraw() }.onFailure { logger.error("badge redraw failed", it) }
            }
            // Item views only exist after the grid lays out, and scrolling may never happen.
            container.viewTreeObserver.addOnGlobalLayoutListener {
                runCatching { redraw() }.onFailure { logger.error("badge redraw failed", it) }
            }
            // A recycled item is re-bound in place, but a newly added one appears without any
            // layout or scroll we could rely on, so decorate it the moment it is attached.
            container.setOnHierarchyChangeListener(object : ViewGroup.OnHierarchyChangeListener {
                override fun onChildViewAdded(parent: View?, child: View?) {
                    if (!active) return
                    val item = child as? ViewGroup ?: return
                    runCatching {
                        decorateItem(
                            item,
                            container.indexOfChild(item),
                            container,
                            adapterList(container),
                            apps(),
                            ModuleLog.shouldLog(LogLevel.TRACE),
                            false
                        )
                    }.onFailure { logger.error("badge attach failed", it) }
                }

                override fun onChildViewRemoved(parent: View?, child: View?) = Unit
            })
            watching = true
        }
        tracePass = true
        runCatching { redraw() }.onFailure { logger.error("badge redraw failed", it) }
    }

    /** Whether this decorator currently owns the panel's item listeners. */
    fun isActive(): Boolean = active

    /** Removes the badges and restores normal item taps, e.g. when leaving edit mode. */
    fun clear() {
        active = false
        val container = grid ?: return
        for (index in 0 until container.childCount) {
            val item = container.getChildAt(index) as? ViewGroup ?: continue
            item.findViewById<View>(badgeId)?.let { (it.parent as? ViewGroup)?.removeView(it) }
            item.setOnTouchListener(null)
        }
        rebind(container)
    }

    private fun redraw() {
        // The scroll and layout listeners outlive edit mode; without this they would keep
        // re-attaching consuming listeners and badges to the panel the host shows on its own.
        if (!active) return
        val container = grid ?: return
        val service = apps()
        val adapter = adapterList(container)
        // Building the trace strings costs reflective calls per item, so skip it entirely
        // unless the level actually asks for them.
        val tracing = ModuleLog.shouldLog(LogLevel.TRACE)
        val force = tracePass
        tracePass = false
        if (adapter != null && adapter !== service && !warnedDivergence) {
            warnedDivergence = true
            logger.warn("grid adapter list differs from the service list; resolving by adapter")
        }
        if (tracing && force) {
            logger.trace(
                "badge pass: children=" + container.childCount +
                    " adapter=" + (adapter?.size ?: -1) +
                    " service=" + service.size +
                    " sameList=" + (adapter === service) +
                    " canAdd=" + canAdd
            )
        }
        var decorated = 0
        var unresolved = 0
        for (index in 0 until container.childCount) {
            val item = container.getChildAt(index) as? ViewGroup ?: continue
            when (decorateItem(item, index, container, adapter, service, tracing, force)) {
                DECORATED -> decorated++
                UNRESOLVED -> unresolved++
            }
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

    /** Decorates one grid item; returns [DECORATED], [HIDDEN] or [UNRESOLVED]. */
    private fun decorateItem(
        item: ViewGroup,
        index: Int,
        container: ViewGroup,
        adapter: List<Any>?,
        service: List<Any>,
        tracing: Boolean,
        force: Boolean
    ): Int {
        // The host disables the row for apps already open in a small window, which would also
        // block our badge and our touch listener; while picking apps the row must stay
        // interactive, and its own click is swallowed below anyway.
        item.isEnabled = true
        val app = appFor(container, item, adapter, service)
        if (app == null) {
            item.setOnTouchListener { _, _ -> true }
            // Leave no trace of the host's own dimming for apps that are merely running.
            item.findViewById<View>(iconId(container))?.alpha = 1f
            report(item, tracing, force) {
                "#" + index + " pos=" + lastPosition + " src=" + lastSource +
                    " label=" + labelOf(item) + " UNRESOLVED alpha=1.0 badge=none"
            }
            return UNRESOLVED
        }
        val member = isMember(app)
        val key = SidebarReflect.keyOf(app)
        item.setOnTouchListener(itemTouch(item, app, member))
        // Apps already in the drawer are masked and carry no badge: they are removed from the
        // drawer row's own disc. A full drawer masks everything, because nothing more can be
        // added at all.
        val masked = member || !canAdd
        item.findViewById<View>(iconId(container))?.alpha = if (masked) MASKED_ALPHA else 1f
        val existing = item.findViewById<View>(badgeId) as? TextView
        if (member) {
            existing?.visibility = View.GONE
            report(item, tracing, force) {
                "#" + index + " pos=" + lastPosition + " src=" + lastSource +
                    " label=" + labelOf(item) + " key=" + key + " member=true alpha=" +
                    (if (masked) MASKED_ALPHA else 1f) + " badge=hidden"
            }
            return HIDDEN
        }
        val badge = existing ?: addBadge(item) ?: return HIDDEN
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
        report(item, tracing, force) {
            "#" + index + " pos=" + lastPosition + " src=" + lastSource +
                " label=" + labelOf(item) + " key=" + key + " member=false alpha=" +
                (if (masked) MASKED_ALPHA else 1f) + " badge=" +
                (if (enabled) "add" else "add-disabled")
        }
        badge.setOnClickListener(
            if (enabled) {
                // Resolve the app when the tap happens: the item view can be rebound to a
                // different app between a redraw and the tap, so a captured app goes stale.
                View.OnClickListener {
                    appFor(container, item, adapterList(container), apps())?.let(onToggle)
                }
            } else {
                null
            }
        )
        // The badge is clickable, so it consumes the gesture and can squeeze the icon too.
        badge.setOnTouchListener { _, event ->
            runCatching {
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN ->
                        SidebarPressScale.press(item.findViewById<View>(iconId(container)))
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL ->
                        SidebarPressScale.releasePressed()
                }
            }.onFailure { logger.error("badge press failed", it) }
            false
        }
        return DECORATED
    }

    /** Last reported decision per item view; weak so recycled views cannot be kept alive. */
    private val lastReported = java.util.WeakHashMap<View, String>()

    /** Logs a decision for one item on a trace pass, or as soon as it changes. */
    private fun report(item: View, tracing: Boolean, force: Boolean, build: () -> String) {
        if (!tracing) return
        val line = build()
        if (!force && lastReported[item] == line) return
        lastReported[item] = line
        logger.trace("badge " + line)
    }

    /**
     * The app a grid item currently shows.
     *
     * The grid's own adapter list is authoritative: the host's package and locale receivers
     * reassign the service's all-apps list without rebinding the grid, so indexing that list by
     * adapter position yields a different app. The shown label is the second source, and the
     * caller's list the last.
     */
    private fun appFor(
        container: ViewGroup,
        item: View,
        adapter: List<Any>?,
        service: List<Any>
    ): Any? {
        val position = adapterPosition(container, item)
        lastPosition = position
        adapter?.getOrNull(position)?.let { lastSource = "adapter"; return it }
        val label = labelOf(item) ?: run {
            lastSource = "none"
            return service.getOrNull(position)?.also { lastSource = "service" }
        }
        adapter?.firstOrNull { SidebarReflect.labelOf(it) == label }?.let {
            lastSource = "adapter-label"
            return it
        }
        service.firstOrNull { SidebarReflect.labelOf(it) == label }?.let {
            lastSource = "service-label"
            return it
        }
        lastSource = "none"
        return service.getOrNull(position)?.also { lastSource = "service" }
    }

    /**
     * Squeezes the item's icon and swallows the tap, so the host never launches while editing.
     * An app that still shows its add badge is also added when its icon itself is tapped.
     */
    private fun itemTouch(item: ViewGroup, app: Any, member: Boolean): View.OnTouchListener {
        val slop = ViewConfiguration.get(item.context).scaledTouchSlop
        var downY = 0f
        return View.OnTouchListener { _, event ->
            val icon = item.findViewById<View>(iconId(item))
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downY = event.y
                    SidebarPressScale.press(icon)
                }
                MotionEvent.ACTION_UP -> {
                    SidebarPressScale.release(icon)
                    if (!member && canAdd && kotlin.math.abs(event.y - downY) <= slop) onToggle(app)
                }
                MotionEvent.ACTION_CANCEL -> SidebarPressScale.release(icon)
            }
            true
        }
    }

    private fun adapterList(container: ViewGroup): List<Any>? {
        val adapter = adapterOf(container) ?: return null
        val field = listField ?: listFieldOf(adapter)?.also { listField = it } ?: return null
        return runCatching { field.get(adapter) as? List<Any> }.getOrNull()
    }

    private fun adapterOf(container: ViewGroup): Any? {
        val method = adapterMethod ?: runCatching {
            container.javaClass.getMethod("getAdapter")
        }.getOrNull()?.also { adapterMethod = it } ?: return null
        return runCatching { method.invoke(container) }.getOrNull()
    }

    /** The adapter's item list, found by name first and by item count otherwise. */
    private fun listFieldOf(adapter: Any): java.lang.reflect.Field? {
        val count = runCatching {
            adapter.javaClass.getMethod("getItemCount").invoke(adapter) as Int
        }.getOrDefault(-1)
        val candidates = adapter.javaClass.declaredFields.filter {
            List::class.java.isAssignableFrom(it.type)
        }
        return candidates.firstOrNull { it.name == "mAppInfoList" }
            ?: candidates.firstOrNull { field ->
                runCatching {
                    field.isAccessible = true
                    (field.get(adapter) as? List<*>)?.size == count
                }.getOrDefault(false)
            }
    }

    private fun labelOf(item: View): String? {
        if (labelViewId == 0) labelViewId = resourceId(item, "appname_tv")
        if (labelViewId == 0) return null
        val text = (item.findViewById<View>(labelViewId) as? TextView)?.text?.toString()
        return text?.takeIf { it.isNotEmpty() }
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
        const val DECORATED = 1
        const val HIDDEN = 0
        const val UNRESOLVED = -1
    }
}
