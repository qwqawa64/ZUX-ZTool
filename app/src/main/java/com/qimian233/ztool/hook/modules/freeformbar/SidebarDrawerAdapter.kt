package com.qimian233.ztool.hook.modules.freeformbar

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.AbsListView
import android.widget.BaseAdapter
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

/**
 * Drawer adapter owned by the hook, replacing the host's `AppIconListAdapter` so the
 * 5-slot cap and its `i >= 5` early return no longer apply.
 *
 * Renders the app rows plus, when [editable], one trailing action row (编辑 / 完成) that
 * switches the list into an ordering mode where every row carries ▲ / ▼ / ✕ affordances.
 */
internal class SidebarDrawerAdapter(
    private val context: Context,
    private val hostPackage: String,
    private val editable: Boolean,
    private val callbacks: DrawerCallbacks
) : BaseAdapter() {

    internal interface DrawerCallbacks {
        fun onToggleEditMode()
        fun onRemove(app: Any)
    }

    private var items: List<Any> = emptyList()
    private var visibleKeys: List<String> = emptyList()
    private var editMode: Boolean = false
    private var emptySlots: Int = 0
    private var draggingIndex: Int = -1

    private val rowHeight = dimen("top_list_item_height", 72)
    private val padTop = dimen("top_list_item_padding_top", 8)

    /**
     * The host's `top_list_item_width` equals the row height, so using it verbatim after adding
     * vertical row padding clips the icon on both edges.
     */
    private val iconSize = minOf(dimen("top_list_item_width", 72), rowHeight - 2 * padTop)

    private val editLabel = SidebarStrings.get(context, SidebarStrings.STRING_EDIT, SidebarStrings.FALLBACK_EDIT)
    private val doneLabel = SidebarStrings.get(context, SidebarStrings.STRING_DONE, SidebarStrings.FALLBACK_DONE)
    private val glyphDrop = SidebarStrings.get(context, SidebarStrings.STRING_GLYPH_DROP, SidebarStrings.FALLBACK_GLYPH_DROP)
    private val dropLabel = SidebarStrings.get(context, SidebarStrings.STRING_DROP, SidebarStrings.FALLBACK_DROP)

    /**
     * [newEmptySlots] renders that many placeholder rows after the apps, so an edit list keeps
     * its full length instead of shrinking as apps are removed.
     */
    fun submit(
        newItems: List<Any>,
        newVisibleKeys: List<String>,
        newEditMode: Boolean,
        newEmptySlots: Int = 0
    ) {
        items = newItems
        visibleKeys = newVisibleKeys
        editMode = newEditMode
        emptySlots = if (newEditMode) newEmptySlots.coerceAtLeast(0) else 0
        notifyDataSetChanged()
    }

    fun isEditMode(): Boolean = editMode

    /** Marks the row being dragged, or -1 when no drag is running. */
    fun setDraggingIndex(index: Int) {
        if (draggingIndex == index) return
        draggingIndex = index
        notifyDataSetChanged()
    }

    private fun rowCount(): Int = items.size + emptySlots

    override fun getCount(): Int = rowCount() + if (editable) 1 else 0

    override fun getItem(position: Int): Any? = items.getOrNull(appIndex(position))

    override fun getItemId(position: Int): Long = position.toLong()

    override fun isEnabled(position: Int): Boolean = true

    /**
     * The action row sits at the bottom normally, but at the top while editing, so 完成 is always
     * in reach. Returns -1 when ordering is switched off, i.e. the list is apps only.
     */
    fun actionPosition(): Int = when {
        !editable -> -1
        editMode -> 0
        else -> rowCount()
    }

    private fun appIndex(position: Int): Int = if (editable && editMode) position - 1 else position

    override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
        if (position == actionPosition()) return actionRow()
        val app = items.getOrNull(appIndex(position))
        if (app == null) return emptyRow()
        val key = SidebarReflect.keyOf(app)
        return appRow(app, key != null && visibleKeys.contains(key), appIndex(position))
    }

    /** A free slot: an empty outline the size of an icon, so the list keeps its full length. */
    private fun emptyRow(): View {
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            layoutParams = AbsListView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, rowHeight
            )
            setPadding(0, padTop, 0, padTop)
        }
        row.addView(View(context).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(EMPTY_FILL)
                setStroke(EMPTY_STROKE_WIDTH, EMPTY_STROKE)
            }
            layoutParams = LinearLayout.LayoutParams(iconSize, iconSize)
        })
        return row
    }

    private fun actionRow(): View {
        val label = if (editMode) doneLabel else editLabel
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            layoutParams = AbsListView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, rowHeight
            )
            setPadding(0, padTop, 0, padTop)
        }
        row.addView(TextView(context).apply {
            text = label
            setTextColor(Color.WHITE)
            textSize = 13f
            gravity = Gravity.CENTER
        })
        return row
    }

    private fun appRow(app: Any, alreadyVisible: Boolean, index: Int): View {
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = AbsListView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, rowHeight
            )
            setPadding(0, padTop, 0, padTop)
            alpha = if (index == draggingIndex) DRAGGING_ALPHA else 1f
        }
        val icon = ImageView(context).apply {
            setImageDrawable(SidebarReflect.iconOf(app))
            contentDescription = SidebarReflect.labelOf(app)
            alpha = if (alreadyVisible && !editMode) 0.3f else 1.0f
        }
        if (editable && editMode) {
            // Removal sits on the icon itself and reordering is a long-press drag, so the row
            // carries no extra controls.
            row.addView(iconWithRemoveBadge(icon, app))
        } else {
            row.addView(icon, LinearLayout.LayoutParams(iconSize, iconSize))
        }
        // No row-level click listener: the host's ListView item click drives both
        // launching (normal mode) and ignoring taps on empty row areas (edit mode).
        return row
    }

    /** The icon with a remove disc pinned to its top-right corner. */
    private fun iconWithRemoveBadge(icon: ImageView, app: Any): View =
        FrameLayout(context).apply {
            layoutParams = LinearLayout.LayoutParams(iconSize, iconSize)
            addView(icon, FrameLayout.LayoutParams(iconSize, iconSize))
            addView(
                SidebarBadgeStyle.view(context, glyphDrop, dropLabel, SidebarBadgeStyle.DROP_COLOR)
                    .apply {
                        val size = SidebarBadgeStyle.size(context)
                        layoutParams = FrameLayout.LayoutParams(
                            size, size, Gravity.TOP or Gravity.END
                        )
                        setOnClickListener { callbacks.onRemove(app) }
                    }
            )
        }

    private fun dimen(name: String, fallbackDp: Int): Int {
        val id = context.resources.getIdentifier(name, "dimen", hostPackage)
        return if (id != 0) context.resources.getDimensionPixelSize(id) else fallbackDp
    }

    private companion object {
        const val DRAGGING_ALPHA = 0.5f
        const val EMPTY_FILL = 0x14FFFFFF
        const val EMPTY_STROKE = 0x4DFFFFFF
        const val EMPTY_STROKE_WIDTH = 3
    }
}
