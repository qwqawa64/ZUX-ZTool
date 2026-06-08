package com.qimian233.ztool.utils

import android.app.Activity
import android.content.Intent
import android.os.Build
import com.qimian233.ztool.R

fun Activity.applyZToolActivityTransitions(predictiveBackGestureEnabled: Boolean = true) {
    val openEnterAnimation = if (predictiveBackGestureEnabled) {
        R.anim.ztool_slide_in_from_right
    } else {
        R.anim.ztool_no_animation
    }
    val openExitAnimation = if (predictiveBackGestureEnabled) {
        R.anim.ztool_slide_out_to_left
    } else {
        R.anim.ztool_no_animation
    }
    val closeEnterAnimation = if (predictiveBackGestureEnabled) {
        R.anim.ztool_slide_in_from_left
    } else {
        R.anim.ztool_no_animation
    }
    val closeExitAnimation = if (predictiveBackGestureEnabled) {
        R.anim.ztool_slide_out_to_right
    } else {
        R.anim.ztool_no_animation
    }

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
        overrideActivityTransition(
            Activity.OVERRIDE_TRANSITION_OPEN,
            openEnterAnimation,
            openExitAnimation
        )
        overrideActivityTransition(
            Activity.OVERRIDE_TRANSITION_CLOSE,
            closeEnterAnimation,
            closeExitAnimation
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
