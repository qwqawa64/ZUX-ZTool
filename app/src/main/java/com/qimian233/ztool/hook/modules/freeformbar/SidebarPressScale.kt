package com.qimian233.ztool.hook.modules.freeformbar

import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import com.qimian233.ztool.hook.base.ModuleLog

/**
 * Squeeze feedback for app icons.
 *
 * A view only receives the rest of a gesture if it, or a descendant, consumed the down. Grid items
 * are clickable, so a listener on them sees the whole gesture; the drawer's rows are not, so their
 * feedback is driven from the ListView's own listener, which sees every event.
 */
internal object SidebarPressScale {

    private const val SCALE = 0.88f
    private const val DURATION = 90L
    private val HIGHLIGHT = PorterDuffColorFilter(0x59FFFFFF, PorterDuff.Mode.SRC_ATOP)

    private var iconId = 0
    private var pressed: View? = null
    private var watching = false
    private var warnedIcon = false

    /** Set once by the hook so the press path can be traced. */
    var logger: ModuleLog? = null

    /** Squeezes [view]. Swallows failures: this runs inside the host's input dispatch. */
    fun press(view: View?) {
        if (view == null) return
        runCatching {
            pressed = view
            logger?.trace(
                "press on " + view.javaClass.simpleName + " isIcon=" + (view is ImageView)
            )
            // An icon has no room for a tile behind it, so it brightens instead; the action
            // button is a plain view with its own pressed tile and only scales.
            (view as? ImageView)?.colorFilter = HIGHLIGHT
            view.animate().scaleX(SCALE).scaleY(SCALE).setDuration(DURATION).start()
        }.onFailure { logger?.error("press feedback failed", it) }
    }

    /** Restores [view]. Swallows failures: this runs inside the host's input dispatch. */
    fun release(view: View?) {
        if (view == null) return
        runCatching {
            if (pressed === view) pressed = null
            (view as? ImageView)?.colorFilter = null
            view.animate().scaleX(1f).scaleY(1f).setDuration(DURATION).start()
        }.onFailure { logger?.error("release feedback failed", it) }
    }

    fun releasePressed() = release(pressed)

    /** The icon inside a host row, falling back to the row itself. */
    fun iconOf(row: View, hostPackage: String): View {
        if (iconId == 0) iconId = row.resources.getIdentifier("appicon_iv", "id", hostPackage)
        // The type argument is required: inferring it from a nullable branch casts to Void.
        val found: View? = if (iconId != 0) row.findViewById<View>(iconId) else null
        if (found == null && !warnedIcon) {
            warnedIcon = true
            logger?.warn(
                "press: no appicon_iv in " + row.javaClass.simpleName + ", id=" + iconId
            )
        }
        return found ?: row
    }

    /**
     * Squeezes each item of [container] while it is touched. The listener returns false, so the
     * host's own click still fires. Re-applied on every layout because the grid recycles items,
     * and skipped entirely while [enabled] is false, where editing owns the item listeners.
     */
    fun attachItems(container: ViewGroup, hostPackage: String, enabled: () -> Boolean) {
        applyItems(container, hostPackage, enabled)
        if (watching) return
        watching = true
        container.viewTreeObserver.addOnGlobalLayoutListener {
            runCatching { applyItems(container, hostPackage, enabled) }
        }
    }

    private fun applyItems(container: ViewGroup, hostPackage: String, enabled: () -> Boolean) {
        if (!enabled()) return
        for (index in 0 until container.childCount) {
            val item = container.getChildAt(index) as? ViewGroup ?: continue
            item.setOnTouchListener { _, event ->
                runCatching {
                    when (event.actionMasked) {
                        MotionEvent.ACTION_DOWN -> press(iconOf(item, hostPackage))
                        MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> releasePressed()
                    }
                }.onFailure { logger?.error("press listener failed", it) }
                false
            }
        }
    }
}
