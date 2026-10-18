package com.qimian233.ztool.data.freeform

import com.qimian233.ztool.data.keys.PreferenceKeys

/** Shared bounds of the edge-bubble trigger, as a percentage of the window width. */
object FreeformEdgeBubbleConfig {
    const val TRIGGER_PERCENT_MIN = 10
    const val TRIGGER_PERCENT_MAX = 90
    /** Slider granularity in percent; 10..90 in 10 % steps. */
    const val TRIGGER_PERCENT_STEP = 10
    val TRIGGER_PERCENT_DEFAULT =
        PreferenceKeys.FREEFORM_EDGE_BUBBLE_TRIGGER_PERCENT.default

    fun normalizeTriggerPercent(percent: Int): Int =
        percent.coerceIn(TRIGGER_PERCENT_MIN, TRIGGER_PERCENT_MAX)
}
