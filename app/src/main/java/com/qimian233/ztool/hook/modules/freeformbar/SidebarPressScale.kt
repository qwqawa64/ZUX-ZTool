package com.qimian233.ztool.hook.modules.freeformbar

import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import com.qimian233.ztool.hook.base.ModuleLog

/**
 * Press feedback: the icon squeezes and the row behind it lights up.
 *
 * A view only receives the rest of a gesture if it, or a descendant, consumed the down. Grid items
 * are clickable, so a listener on them sees the whole gesture; the drawer's rows are not, so their
 * feedback is driven from the ListView's own listener, which sees every event.
 */
internal object SidebarPressScale {

    private const val SCALE = 0.88f
    private const val DURATION = 90L
    private const val HIGHLIGHT_COLOR = 0x33FFFFFF
    private const val HIGHLIGHT_CORNER_DP = 12f

    /** Set once by the hook so the press path can be traced. */
    var logger: ModuleLog? = null

    private var iconId = 0
    private var pressedIcon: View? = null
    private var savedForeground: Drawable? = null
    private var warnedIcon = false
    private var layoutListener: ViewTreeObserver.OnGlobalLayoutListener? = null

    /** Squeezes [icon] and lights it up. Swallows failures: this runs in input dispatch. */
    fun press(icon: View?) {
        runCatching {
            release()
            pressedIcon = icon
            icon ?: return
            // A foreground covers exactly the icon, so the highlight is the icon's own area
            // rather than the whole row. A background would sit behind an icon that fills it.
            savedForeground = icon.foreground
            icon.foreground = highlight(icon)
            icon.animate().scaleX(SCALE).scaleY(SCALE).setDuration(DURATION).start()
        }.onFailure { logger?.error("press feedback failed", it) }
    }

    /** Undoes [press]. Swallows failures: this runs in input dispatch. */
    fun release() {
        runCatching {
            pressedIcon?.let { icon ->
                icon.foreground = savedForeground
                icon.animate().scaleX(1f).scaleY(1f).setDuration(DURATION).start()
            }
            pressedIcon = null
            savedForeground = null
        }.onFailure { logger?.error("release feedback failed", it) }
    }

    /** The icon inside a host row, falling back to the row itself. */
    fun iconOf(row: View, hostPackage: String): View {
        if (iconId == 0) iconId = row.resources.getIdentifier("appicon_iv", "id", hostPackage)
        // The type argument is required: inferring it from a nullable branch casts to Void.
        val found: View? = if (iconId != 0) row.findViewById<View>(iconId) else null
        if (found == null && !warnedIcon) {
            warnedIcon = true
            logger?.warn("press: no appicon_iv in " + row.javaClass.simpleName + ", id=" + iconId)
        }
        return found ?: row
    }

    /**
     * Squeezes each item of [container] while it is touched, and lights it up. The listener
     * returns false, so the host's own click still fires. Skipped while [enabled] is false, where
     * editing owns the item listeners.
     */
    fun attachItems(container: ViewGroup, hostPackage: String, enabled: () -> Boolean) {
        applyItems(container, hostPackage, enabled)
        // The popup's view tree is rebuilt between shows, which leaves a listener registered on
        // the previous observer dead, so re-register on the tree the grid is attached to now.
        layoutListener?.let { stale ->
            runCatching { container.viewTreeObserver.removeOnGlobalLayoutListener(stale) }
        }
        val listener = ViewTreeObserver.OnGlobalLayoutListener {
            runCatching { applyItems(container, hostPackage, enabled) }
        }
        layoutListener = listener
        container.viewTreeObserver.addOnGlobalLayoutListener(listener)
    }

    private fun applyItems(container: ViewGroup, hostPackage: String, enabled: () -> Boolean) {
        if (!enabled()) return
        for (index in 0 until container.childCount) {
            val item = container.getChildAt(index) as? ViewGroup ?: continue
            item.setOnTouchListener { _, event ->
                runCatching {
                    when (event.actionMasked) {
                        MotionEvent.ACTION_DOWN -> press(iconOf(item, hostPackage))
                        MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> release()
                    }
                }.onFailure { logger?.error("press listener failed", it) }
                false
            }
        }
    }

    private fun highlight(container: View): GradientDrawable = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = HIGHLIGHT_CORNER_DP * container.resources.displayMetrics.density
        setColor(HIGHLIGHT_COLOR)
    }
}
