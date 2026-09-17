package com.qimian233.ztool.service

import android.content.Intent
import android.service.quicksettings.TileService

/**
 * Media output switcher tile: tapping it launches the system media output switcher
 * dialog (the MediaOutputDialog shown from the "Output switch" entry on the media card).
 *
 * Implementation relies purely on standard Android behavior, no hooks:
 * registered as a standard TileService; on tap, sends an explicit broadcast to
 * SystemUI's MediaOutputDialogReceiver, which renders the dialog (also correct in
 * the empty state with no media session).
 */
class MediaOutputTileService : TileService() {

    override fun onClick() {
        super.onClick()
        sendBroadcast(Intent(ACTION_LAUNCH_MEDIA_OUTPUT_DIALOG).apply {
            setClassName(SYSTEMUI_PACKAGE, RECEIVER_CLASS)
        })
    }

    private companion object {
        const val SYSTEMUI_PACKAGE = "com.android.systemui"
        const val RECEIVER_CLASS = "com.android.systemui.media.dialog.MediaOutputDialogReceiver"
        // Public AOSP SystemUI action, consumed by MediaOutputDialogReceiver as a static receiver
        const val ACTION_LAUNCH_MEDIA_OUTPUT_DIALOG =
            "com.android.systemui.action.LAUNCH_SYSTEM_MEDIA_OUTPUT_DIALOG"
    }
}
