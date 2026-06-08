package com.qimian233.ztool.utils

import android.app.Activity
import android.content.Intent
import android.os.Build
import com.qimian233.ztool.R

fun Activity.applyZToolActivityTransitions() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
        overrideActivityTransition(
            Activity.OVERRIDE_TRANSITION_OPEN,
            R.anim.ztool_slide_in_from_right,
            R.anim.ztool_slide_out_to_left
        )
        overrideActivityTransition(
            Activity.OVERRIDE_TRANSITION_CLOSE,
            R.anim.ztool_slide_in_from_left,
            R.anim.ztool_slide_out_to_right
        )
    }
}

fun Activity.startActivityWithZToolTransition(intent: Intent) {
    startActivity(intent)
    @Suppress("DEPRECATION")
    overridePendingTransition(
        R.anim.ztool_slide_in_from_right,
        R.anim.ztool_slide_out_to_left
    )
}

fun Activity.finishWithZToolTransition() {
    finish()
    @Suppress("DEPRECATION")
    overridePendingTransition(
        R.anim.ztool_slide_in_from_left,
        R.anim.ztool_slide_out_to_right
    )
}
