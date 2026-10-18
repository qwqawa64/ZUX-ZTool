package com.qimian233.ztool.hook.modules.freeformbar

import android.graphics.drawable.GradientDrawable

/** The rounded tile used behind the empty slots, the action button and the host's "more" footer. */
internal object SidebarTiles {

    /**
     * Corner radius as a share of the tile, matching how launcher icon masks round. A share rather
     * than a dp value keeps every tile consistent when their sizes differ.
     */
    private const val CORNER_RATIO = 0.25f

    fun tile(color: Int, size: Int): GradientDrawable = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = size * CORNER_RATIO
        setColor(color)
    }
}
