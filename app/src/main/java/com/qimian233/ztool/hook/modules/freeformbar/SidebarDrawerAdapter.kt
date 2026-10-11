package com.qimian233.ztool.hook.modules.freeformbar

import android.content.Context
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.AbsListView
import android.widget.BaseAdapter
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

/**
 * Drawer adapter owned by the hook, replacing the host's `AppIconListAdapter` so the
 * 5-slot cap and its `i >= 5` early return no longer apply.
 *
 * Renders the app rows plus one trailing action row (编辑 / 完成). In edit mode every row
 * also carries ▲ / ▼ / ✕ affordances.
 */
internal class SidebarDrawerAdapter(
    private val context: Context,
    private val hostPackage: String,
    private val callbacks: DrawerCallbacks
) : BaseAdapter() {

    internal interface DrawerCallbacks {
        fun onToggleEditMode()
        fun onMoveUp(app: Any)
        fun onMoveDown(app: Any)
        fun onRemove(app: Any)
    }

    private var items: List<Any> = emptyList()
    private var visibleKeys: List<String> = emptyList()
    private var editMode: Boolean = false

    private val iconSize = dimen("top_list_item_width", 72)
    private val rowHeight = dimen("top_list_item_height", 72)
    private val padTop = dimen("top_list_item_padding_top", 8)

    /** Tap width for one ▲ / ▼ / ✕ cell; the drawer is widened by 3x this in edit mode. */
    val glyphWidth: Int = (GLYPH_DP * context.resources.displayMetrics.density).toInt()

    private val editLabel = SidebarStrings.get(context, SidebarStrings.STRING_EDIT, SidebarStrings.FALLBACK_EDIT)
    private val doneLabel = SidebarStrings.get(context, SidebarStrings.STRING_DONE, SidebarStrings.FALLBACK_DONE)
    private val glyphUp = SidebarStrings.get(context, SidebarStrings.STRING_GLYPH_UP, SidebarStrings.FALLBACK_GLYPH_UP)
    private val glyphDown = SidebarStrings.get(context, SidebarStrings.STRING_GLYPH_DOWN, SidebarStrings.FALLBACK_GLYPH_DOWN)
    private val glyphRemove = SidebarStrings.get(context, SidebarStrings.STRING_GLYPH_REMOVE, SidebarStrings.FALLBACK_GLYPH_REMOVE)
    private val moveUpLabel = SidebarStrings.get(context, SidebarStrings.STRING_MOVE_UP, SidebarStrings.FALLBACK_MOVE_UP)
    private val moveDownLabel = SidebarStrings.get(context, SidebarStrings.STRING_MOVE_DOWN, SidebarStrings.FALLBACK_MOVE_DOWN)
    private val removeLabel = SidebarStrings.get(context, SidebarStrings.STRING_REMOVE, SidebarStrings.FALLBACK_REMOVE)

    fun submit(newItems: List<Any>, newVisibleKeys: List<String>, newEditMode: Boolean) {
        items = newItems
        visibleKeys = newVisibleKeys
        editMode = newEditMode
        notifyDataSetChanged()
    }

    fun isEditMode(): Boolean = editMode

    override fun getCount(): Int = items.size + 1

    override fun getItem(position: Int): Any? = items.getOrNull(position)

    override fun getItemId(position: Int): Long = position.toLong()

    override fun isEnabled(position: Int): Boolean = true

    override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
        if (position >= items.size) return actionRow()
        val app = items[position]
        val key = SidebarReflect.keyOf(app)
        return appRow(app, key != null && visibleKeys.contains(key))
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

    private fun appRow(app: Any, alreadyVisible: Boolean): View {
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = AbsListView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, rowHeight
            )
            setPadding(0, padTop, 0, padTop)
        }
        row.addView(ImageView(context).apply {
            setImageDrawable(SidebarReflect.iconOf(app))
            contentDescription = SidebarReflect.labelOf(app)
            alpha = if (alreadyVisible && !editMode) 0.3f else 1.0f
            layoutParams = LinearLayout.LayoutParams(iconSize, iconSize)
        })
        if (editMode) {
            row.addView(glyph(glyphUp, moveUpLabel) { callbacks.onMoveUp(app) })
            row.addView(glyph(glyphDown, moveDownLabel) { callbacks.onMoveDown(app) })
            row.addView(glyph(glyphRemove, removeLabel) { callbacks.onRemove(app) })
        }
        // No row-level click listener: the host's ListView item click drives both
        // launching (normal mode) and ignoring taps on empty row areas (edit mode).
        return row
    }

    private fun glyph(text: String, description: String, onClick: () -> Unit): TextView =
        TextView(context).apply {
            this.text = text
            contentDescription = description
            setTextColor(Color.WHITE)
            textSize = 14f
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(glyphWidth, ViewGroup.LayoutParams.MATCH_PARENT)
            setOnClickListener { onClick() }
        }

    private fun dimen(name: String, fallbackDp: Int): Int {
        val id = context.resources.getIdentifier(name, "dimen", hostPackage)
        return if (id != 0) context.resources.getDimensionPixelSize(id) else fallbackDp
    }

    private companion object {
        const val GLYPH_DP = 26
    }
}
