package com.qimian233.ztool.data.freeform

import com.qimian233.ztool.data.keys.PreferenceKeys

/** Shared bounds of the edge-bubble trigger distance (px from the display edge). */
object FreeformEdgeBubbleConfig {
    const val TRIGGER_MARGIN_PX_MIN = 0
    const val TRIGGER_MARGIN_PX_MAX = 96
    /** Slider granularity in px; 96 / 4 gives 24 intervals. */
    const val TRIGGER_MARGIN_PX_STEP = 4
    val TRIGGER_MARGIN_PX_DEFAULT =
        PreferenceKeys.FREEFORM_EDGE_BUBBLE_TRIGGER_MARGIN_PX.default

    fun normalizeTriggerMarginPx(px: Int): Int =
        px.coerceIn(TRIGGER_MARGIN_PX_MIN, TRIGGER_MARGIN_PX_MAX)
}
