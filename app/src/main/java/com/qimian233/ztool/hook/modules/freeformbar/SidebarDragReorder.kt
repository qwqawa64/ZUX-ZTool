package com.qimian233.ztool.hook.modules.freeformbar

import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.widget.ListView
import com.qimian233.ztool.hook.base.ModuleLog
import kotlin.math.abs

/**
 * Long-press drag reordering for the drawer's ListView.
 *
 * The host holds the drawer as a `ListView` and calls `ListView` methods on it, so it cannot be
 * swapped for a RecyclerView and `ItemTouchHelper` is unavailable; the gesture is tracked by
 * hand. A drag is only offered while editing, and only from a row area that is not itself
 * clickable, so the up/down glyphs and the remove disc keep working.
 */
internal class SidebarDragReorder(
    private val logger: ModuleLog,
    private val isDraggable: (position: Int) -> Boolean,
    private val onSwap: (fromPosition: Int, toPosition: Int) -> Unit,
    private val onDragging: (position: Int) -> Unit
) {

    private var listView: ListView? = null
    private var longPress: Runnable? = null
    private var dragging = -1
    private var downY = 0f
    private var slop = 0
    private var longPressTimeout = 0L

    /** Installs the gesture; call once per drawer ListView. */
    fun attach(view: ListView) {
        listView = view
        slop = ViewConfiguration.get(view.context).scaledTouchSlop
        longPressTimeout = ViewConfiguration.getLongPressTimeout().toLong()
        view.setOnTouchListener { _, event ->
            // A throw here would run inside the host's touch dispatch and kill its process.
            runCatching { handle(event) }.getOrElse {
                logger.error("drag gesture failed", it)
                false
            }
        }
    }

    private fun handle(event: MotionEvent): Boolean {
        val view = listView ?: return false
        return when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> onDown(view, event)
            MotionEvent.ACTION_MOVE -> onMove(view, event)
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> onUp()
            else -> false
        }
    }

    private fun onDown(view: ListView, event: MotionEvent): Boolean {
        cancelLongPress()
        dragging = -1
        val position = view.pointToPosition(event.x.toInt(), event.y.toInt())
        if (position < 0 || !isDraggable(position)) return false
        if (clickableChildAt(view, position, event.x, event.y) != null) return false
        downY = event.y
        // Returning false keeps the ListView's own scrolling alive until the press matures.
        longPress = Runnable {
            runCatching { beginDrag(view, position) }
                .onFailure { logger.error("drag start failed", it) }
        }.also {
            view.postDelayed(it, longPressTimeout)
        }
        return false
    }

    private fun onMove(view: ListView, event: MotionEvent): Boolean {
        if (dragging < 0) {
            if (abs(event.y - downY) > slop) cancelLongPress()
            return false
        }
        val target = view.pointToPosition(view.width / 2, event.y.toInt())
        if (target >= 0 && target != dragging && isDraggable(target)) {
            // Highlight first: the swap rebuilds the list, and a rebuild after it would replace
            // the rows that are mid-animation.
            onDragging(target)
            onSwap(dragging, target)
            dragging = target
        }
        return true
    }

    private fun onUp(): Boolean {
        cancelLongPress()
        if (dragging < 0) return false
        dragging = -1
        onDragging(-1)
        return true
    }

    private fun beginDrag(view: ListView, position: Int) {
        dragging = position
        // The ListView already saw the press, so clear its touch state before taking the gesture.
        cancelListViewTouch(view)
        onDragging(dragging)
        logger.debug("drag started at position " + position)
    }

    private fun cancelLongPress() {
        longPress?.let { listView?.removeCallbacks(it) }
        longPress = null
    }

    /** Calling `onTouchEvent` directly resets the ListView without re-entering our listener. */
    private fun cancelListViewTouch(view: ListView) {
        val now = SystemClock.uptimeMillis()
        val cancel = MotionEvent.obtain(now, now, MotionEvent.ACTION_CANCEL, 0f, 0f, 0)
        runCatching { view.onTouchEvent(cancel) }
        cancel.recycle()
    }

    /** Depth-first search for a clickable child under the touch point, in row-local coordinates. */
    private fun clickableChildAt(view: ListView, position: Int, x: Float, y: Float): View? {
        val row = view.getChildAt(position - view.firstVisiblePosition) ?: return null
        val rowLocation = IntArray(2)
        val listLocation = IntArray(2)
        row.getLocationOnScreen(rowLocation)
        view.getLocationOnScreen(listLocation)
        val localX = x + listLocation[0] - rowLocation[0]
        val localY = y + listLocation[1] - rowLocation[1]
        return findClickable(row, localX, localY)
    }

    private fun findClickable(parent: View, x: Float, y: Float): View? {
        if (parent is ViewGroup) {
            for (index in 0 until parent.childCount) {
                val child = parent.getChildAt(index)
                if (child.visibility != View.VISIBLE) continue
                findClickable(child, x - child.left, y - child.top)?.let { return it }
            }
        }
        val inside = x >= 0 && y >= 0 && x < parent.width && y < parent.height
        return if (inside && parent.isClickable) parent else null
    }
}
