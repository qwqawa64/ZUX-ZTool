package com.qimian233.ztool.hook.modules.freeformbar

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.widget.TextView

/** Shared look for the add and remove discs on the drawer rows and the all-apps grid. */
internal object SidebarBadgeStyle {

    const val ADD_COLOR = 0xE62196F3.toInt()
    const val DROP_COLOR = 0xE6E53935.toInt()
    const val MUTED_COLOR = 0x66888888.toInt()
    const val DISABLED_ALPHA = 0.35f

    private const val SIZE_DP = 20

    fun size(context: Context): Int =
        (SIZE_DP * context.resources.displayMetrics.density).toInt()

    fun view(context: Context, glyph: String, description: String, color: Int): TextView =
        TextView(context).apply {
            text = glyph
            contentDescription = description
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            textSize = 13f
            background = disc(color)
        }

    fun disc(color: Int): GradientDrawable = GradientDrawable().apply {
        shape = GradientDrawable.OVAL
        setColor(color)
    }
}
