package com.qimian233.ztool.hook.modules.freeformbar

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.AbsListView
import android.widget.BaseAdapter
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.qimian233.ztool.hook.base.ModuleLog

/**
 * Drawer adapter owned by the hook, replacing the host's `AppIconListAdapter` so the
 * 5-slot cap and its `i >= 5` early return no longer apply.
 *
 * Renders the app rows plus, when [editable], an action row (编辑 / 完成) that switches the list
 * into an ordering mode where each row carries a remove disc and is reordered by dragging.
 */
internal class SidebarDrawerAdapter(
    private val context: Context,
    private val hostPackage: String,
    private val editable: Boolean,
    private val callbacks: DrawerCallbacks,
    private val logger: ModuleLog
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
    private val pendingOffsets = HashMap<Int, Float>()

    /** Uniform row height; the swap animation measures row offsets with it. */
    val rowHeight = dimen("top_list_item_height", 72)
    private val padTop = dimen("top_list_item_padding_top", 8)

    /**
     * The host's `top_list_item_width` equals the row height, so using it verbatim after adding
     * vertical row padding clips the icon on both edges.
     */
    private val iconSize = minOf(dimen("top_list_item_width", 72), rowHeight - 2 * padTop)
    private val iconViewId = context.resources.getIdentifier("appicon_iv", "id", hostPackage)

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

    /**
     * Starts the two rows of a swap at the offset they came from, so the exchange slides instead
     * of jumping. Applied when the row is built: a posted runnable can beat the list's layout.
     */
    fun animateSwap(fromIndex: Int, toIndex: Int, rowHeight: Int) {
        val distance = (toIndex - fromIndex) * rowHeight
        if (distance == 0) return
        logger.trace("swap animation queued from=" + fromIndex + " to=" + toIndex +
            " distance=" + distance)
        pendingOffsets[toIndex] = -distance.toFloat()
        pendingOffsets[fromIndex] = distance.toFloat()
    }

    /** Marks the row being dragged, or -1 when no drag is running. */
    fun setDraggingIndex(index: Int) {
        if (draggingIndex == index) return
        draggingIndex = index
        notifyDataSetChanged()
    }

    /** Moves the drag highlight without rebuilding; the caller's next submit renders it. */
    fun setDraggingIndexSilently(index: Int) {
        draggingIndex = index
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

    /** A free slot: an empty rounded tile the size of an icon, so the list keeps its length. */
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
            background = tile(SLOT_FILL, iconSize)
            layoutParams = LinearLayout.LayoutParams(iconSize, iconSize)
        })
        return row
    }

    private fun actionRow(): View {
        val label = if (editMode) doneLabel else editLabel
        val icon = SidebarIcons.get(context, if (editMode) SidebarIcons.DONE else SidebarIcons.EDIT)
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            layoutParams = AbsListView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, rowHeight
            )
            setPadding(0, padTop, 0, padTop)
        }
        if (icon == null) {
            row.addView(TextView(context).apply {
                text = label
                setTextColor(Color.WHITE)
                textSize = 13f
                gravity = Gravity.CENTER
            })
            return row
        }
        val size = iconSize
        // Clickable so it owns its pressed state; the host's item click then never fires for it.
        row.addView(FrameLayout(context).apply {
            background = buttonBackground(size)
            isClickable = true
            contentDescription = label
            setOnClickListener { callbacks.onToggleEditMode() }
            setOnTouchListener { view, event ->
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> SidebarPressScale.press(view)
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL ->
                        SidebarPressScale.release(view)
                }
                false
            }
            layoutParams = LinearLayout.LayoutParams(size, size)
            addView(ImageView(context).apply {
                setImageDrawable(icon)
                val glyph = (size * ACTION_ICON_RATIO).toInt()
                layoutParams = FrameLayout.LayoutParams(glyph, glyph, Gravity.CENTER)
            })
        })
        return row
    }

    /** The slot tile's shape, darkened, plus a pressed state. */
    private fun buttonBackground(size: Int): StateListDrawable = StateListDrawable().apply {
        addState(intArrayOf(android.R.attr.state_pressed), tile(BUTTON_FILL_PRESSED, size))
        addState(intArrayOf(), tile(BUTTON_FILL, size))
    }

    private fun tile(color: Int, size: Int): GradientDrawable = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = size * TILE_CORNER_RATIO
        setColor(color)
    }

    private fun appRow(app: Any, alreadyVisible: Boolean, index: Int): View {
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            layoutParams = AbsListView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, rowHeight
            )
            setPadding(0, padTop, 0, padTop)
            alpha = if (index == draggingIndex) DRAGGING_ALPHA else 1f
        }
        pendingOffsets.remove(index)?.let { offset ->
            logger.trace("swap animation applied to row " + index + " offset=" + offset)
            row.translationY = offset
            row.animate().translationY(0f).setDuration(SWAP_ANIM_MS).start()
        }
        val icon = ImageView(context).apply {
            // Carries the host's icon id so press feedback can find the icon, not the whole row.
            if (iconViewId != 0) id = iconViewId
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
            // Squeeze feedback on press; returning false keeps the host's item click working.
            row.setOnTouchListener { _, event ->
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> SidebarPressScale.press(icon)
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL ->
                        SidebarPressScale.release(icon)
                }
                false
            }
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
                        // The disc is clickable, so it consumes the gesture and can squeeze the
                        // icon it belongs to.
                        setOnTouchListener { _, event ->
                            when (event.actionMasked) {
                                MotionEvent.ACTION_DOWN -> SidebarPressScale.press(icon)
                                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL ->
                                    SidebarPressScale.release(icon)
                            }
                            false
                        }
                    }
            )
        }

    private fun dimen(name: String, fallbackDp: Int): Int {
        val id = context.resources.getIdentifier(name, "dimen", hostPackage)
        return if (id != 0) context.resources.getDimensionPixelSize(id) else fallbackDp
    }

    private companion object {
        const val DRAGGING_ALPHA = 0.5f
        const val SWAP_ANIM_MS = 120L
        /** Share of the tile the glyph fills; the rest is the tile's own padding. */
        const val ACTION_ICON_RATIO = 0.52f
        /** Corner radius as a share of the tile, matching how launcher icon masks round. */
        const val TILE_CORNER_RATIO = 0.25f
        const val SLOT_FILL = 0x14FFFFFF
        const val BUTTON_FILL = 0x33FFFFFF
        const val BUTTON_FILL_PRESSED = 0x80FFFFFF.toInt()
    }
}
