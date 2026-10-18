package com.qimian233.ztool.hook.modules.freeformbar

import android.content.Context
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.RelativeLayout
import android.widget.TextView
import com.qimian233.ztool.hook.base.ModuleLog
import java.lang.reflect.Field
import java.lang.reflect.Method

/**
 * Sectioned all-apps grid: intercepts the host's `AppIconRecyclerAdapter` so its rows carry a
 * letter heading in front of every pinyin group, and feeds the A–Z rail and the badges from the
 * same row model.
 *
 * Positions handed back to the host are translated into its own list indices, which keeps its bind
 * (dimming, suspended icons) and its click (launching) correct without replacing either.
 */
internal class SidebarAllAppsGrid(
    private val hostPackage: String,
    classLoader: ClassLoader,
    private val logger: ModuleLog
) : SidebarLetterSource {

    private val model = SidebarAllAppsModel(
        runCatching {
            classLoader.loadClass("com.zui.freeform.sidebar.utils.PinYinUtils")
                .getMethod("toPinYin", String::class.java)
        }.getOrNull()
    )

    private val itemViewField: Field? = runCatching {
        classLoader.loadClass("androidx.recyclerview.widget.RecyclerView\$ViewHolder")
            .getField("itemView")
    }.getOrNull()

    private var adapter: Any? = null
    private var apps: List<Any> = emptyList()
    private var query: String = ""
    private var sections = true
    private var binding = false
    private var iconId = 0
    private var labelId = 0
    private var palette: SidebarPalette? = null

    /** False when the host's item view cannot be reached, which leaves headings unrenderable. */
    val canRenderHeaders: Boolean get() = itemViewField != null

    val isEmpty: Boolean get() = model.isEmpty

    override fun groups(): List<Pair<String, Int>> = model.groups

    override fun letterAt(position: Int): String? = model.letterAt(position)

    override fun rowCount(): Int = model.size

    fun appAt(position: Int): Any? = model.appAt(position)

    fun isHeader(position: Int): Boolean = model.isHeader(position)

    fun owns(candidate: Any?): Boolean = candidate != null && candidate === adapter

    /** Whether the host is currently calling back into itself with an index we handed it. */
    fun isBinding(): Boolean = binding

    fun beginBinding() {
        binding = true
    }

    fun endBinding() {
        binding = false
    }

    /** Takes over [candidate] for the app list the panel was just built from. */
    fun adopt(
        context: Context,
        candidate: Any,
        apps: List<Any>,
        sections: Boolean,
        colors: SidebarPalette
    ) {
        adapter = candidate
        this.apps = apps
        this.sections = sections
        this.palette = colors
        query = ""
        iconId = context.resources.getIdentifier("appicon_iv", "id", hostPackage)
        labelId = context.resources.getIdentifier("appname_tv", "id", hostPackage)
        model.forget()
        model.submit(apps, query, sections)
    }

    /** Swaps in the palette of the theme the host is currently drawing the panel with. */
    fun applyPalette(colors: SidebarPalette) {
        palette = colors
    }

    fun filter(newQuery: String) {
        if (newQuery == query) return
        query = newQuery
        model.submit(apps, query, sections)
        notifyChanged()
    }

    /** Refreshes the rows after the host swapped its app list out from under the panel. */
    fun refresh(newApps: List<Any>) {
        apps = newApps
        model.forget()
        model.submit(apps, query, sections)
        notifyChanged()
    }

    fun notifyChanged() {
        notify(adapter)
    }

    /** Whether the panel's grid is currently intercepted. */
    fun isAdopted(): Boolean = adapter != null

    /** Stops intercepting, so the host's own adapter answers for itself again. */
    fun release() {
        val target = adapter ?: return
        adapter = null
        apps = emptyList()
        sections = false
        model.submit(emptyList(), "", false)
        notify(target)
    }

    private fun notify(target: Any?) {
        target ?: return
        runCatching { target.javaClass.getMethod("notifyDataSetChanged").invoke(target) }
            .onFailure { logger.warn("could not refresh the all-apps grid: " + it.message) }
    }

    fun itemCount(): Int = model.size

    /** The view type for a heading row, or null when the host's own answer should be used. */
    fun headerType(position: Int): Int? = if (model.isHeader(position)) HEADER_TYPE else null

    /** The host list index behind an adapter position, or -1 when the row is a heading. */
    fun hostIndexOf(position: Int): Int = model.hostIndexOf(position)

    /**
     * The column [position] starts at, or -1 to let the host's lookup decide.
     *
     * The host's default lookup answers `position % spanCount`, which is only correct while every
     * item spans one column; with headings in the list it has to come from the row model.
     */
    fun spanIndex(position: Int, spanCount: Int): Int = model.spanIndex(position, spanCount)

    /** Renders [position]'s heading into the item view behind [holder]; false for app rows. */
    fun bindHeader(holder: Any, position: Int): Boolean {
        val letter = model.headerAt(position) ?: return false
        val item = runCatching { itemViewField?.get(holder) as? View }.getOrNull() ?: return true
        runCatching { styleHeader(item, letter) }
            .onFailure { logger.warn("could not render the " + letter + " heading: " + it.message) }
        return true
    }

    /**
     * Turns one of the host's own app item views into a heading: the icon goes away and the name
     * label becomes a left-aligned letter. RecyclerView keeps one view pool per view type, so a
     * view converted here is never bound as an app again.
     */
    private fun styleHeader(item: View, letter: String) {
        if (iconId != 0) item.findViewById<View>(iconId)?.visibility = View.GONE
        val label = if (labelId != 0) item.findViewById<TextView>(labelId) else null
        if (label == null) {
            logger.warn("all-apps item has no name label, headings stay blank")
            return
        }
        val density = item.resources.displayMetrics.density
        label.text = letter
        label.setTextSize(TypedValue.COMPLEX_UNIT_SP, HEADER_SP)
        label.setTextColor(palette?.header ?: HEADER_FALLBACK)
        label.gravity = Gravity.START or Gravity.CENTER_VERTICAL
        label.includeFontPadding = false
        label.setPadding(
            0, (HEADER_TOP_DP * density).toInt(), 0, (HEADER_BOTTOM_DP * density).toInt()
        )
        (label.layoutParams as? RelativeLayout.LayoutParams)?.let { params ->
            params.removeRule(RelativeLayout.BELOW)
            params.addRule(RelativeLayout.ALIGN_PARENT_START)
            params.topMargin = 0
            params.width = ViewGroup.LayoutParams.MATCH_PARENT
            params.height = ViewGroup.LayoutParams.WRAP_CONTENT
            label.layoutParams = params
        }
        item.isClickable = false
        item.setOnClickListener(null)
        item.setOnTouchListener(null)
    }

    private companion object {
        /** Never returned by the host's own `getItemViewType`, so headings pool apart. */
        const val HEADER_TYPE = 0x5A5A
        const val HEADER_SP = 12f
        const val HEADER_TOP_DP = 6f
        const val HEADER_BOTTOM_DP = 2f

        /** Used only when no palette has been resolved yet. */
        const val HEADER_FALLBACK = 0xB3FFFFFF.toInt()
    }
}
