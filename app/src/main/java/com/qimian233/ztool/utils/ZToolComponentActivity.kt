package com.qimian233.ztool.utils

import android.os.Bundle
import androidx.activity.ComponentActivity
import com.qimian233.ztool.R

open class ZToolComponentActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        applyZToolActivityTransitions()
    }

    override fun finish() {
        super.finish()
        @Suppress("DEPRECATION")
        overridePendingTransition(
            R.anim.ztool_slide_in_from_left,
            R.anim.ztool_slide_out_to_right
        )
    }
}
