package com.qimian233.ztool.service

import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.TileService

/**
 * Extra dim (ReduceBrightColors) tile: tapping it launches the system
 * Settings activity directly, mirroring how MediaOutputTileService offers
 * a one-tap entry from the quick settings shade.
 */
class ReduceBrightColorsTileService : TileService() {

    override fun onClick() {
        super.onClick()
        val intent = Intent().apply {
            setClassName(SETTINGS_PACKAGE, REDUCE_BRIGHT_COLORS_ACTIVITY)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startActivityAndCollapse(
                PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)
            )
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }

    private companion object {
        const val SETTINGS_PACKAGE = "com.android.settings"
        const val REDUCE_BRIGHT_COLORS_ACTIVITY =
            "com.android.settings.Settings\$ReduceBrightColorsSettingsActivity"
    }
}
