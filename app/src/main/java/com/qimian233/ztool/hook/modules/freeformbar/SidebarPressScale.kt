package com.qimian233.ztool.hook.modules.freeformbar

import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup

/** Squeeze feedback for app icons; callers keep returning false so the host still handles taps. */
internal object SidebarPressScale {

    private const val SCALE = 0.88f
    private const val DURATION = 90L

    private var iconId = 0
    private var pressed: View? = null

    fun press(view: View?) {
        if (view == null) return
        pressed = view
        view.animate().scaleX(SCALE).scaleY(SCALE).setDuration(DURATION).start()
    }

    fun release(view: View?) {
        if (view == null) return
        if (pressed === view) pressed = null
        view.animate().scaleX(1f).scaleY(1f).setDuration(DURATION).start()
    }

    /** Tracks presses anywhere in [container]; never consumes, so taps still reach the host. */
    fun attach(container: ViewGroup, hostPackage: String) {
        container.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> press(iconAt(container, event, hostPackage))
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    release(pressed)
                }
            }
            false
        }
    }

    private fun iconAt(container: ViewGroup, event: MotionEvent, hostPackage: String): View? {
        for (index in 0 until container.childCount) {
            val child = container.getChildAt(index)
            if (child.visibility != View.VISIBLE) continue
            if (event.x < child.left || event.x > child.right) continue
            if (event.y < child.top || event.y > child.bottom) continue
            return iconOf(child, hostPackage)
        }
        return null
    }

    private fun iconOf(row: View, hostPackage: String): View {
        if (iconId == 0) iconId = row.resources.getIdentifier("appicon_iv", "id", hostPackage)
        return if (iconId != 0) row.findViewById(iconId) ?: row else row
    }
}
