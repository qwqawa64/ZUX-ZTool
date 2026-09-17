package com.qimian233.ztool.service

import android.content.Intent
import android.service.quicksettings.TileService

/**
 * Media output switcher tile: tapping it launches the system media output switcher
 * dialog (the MediaOutputDialog shown from the "Output switch" entry on the media card).
 *
 * Implementation relies purely on standard Android behavior, no hooks:
 * 1. Registered as a standard TileService; the user drags it in manually via the
 *    control center tile editor.
 * 2. On tap, sends an explicit broadcast to SystemUI's MediaOutputDialogReceiver
 *    (verified on real devices that this receiver is reachable by third-party apps);
 *    the system-side MediaOutputDialogManager.createAndShow(null, ...) renders the
 *    dialog, which also displays correctly in the empty state (no media session).
 *
 * onClick is invoked on the main thread and the broadcast is fire-and-forget,
 * so no extra threading is needed.
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
