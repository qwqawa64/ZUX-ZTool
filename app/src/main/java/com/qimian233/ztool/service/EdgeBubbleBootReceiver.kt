package com.qimian233.ztool.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * Restarts the edge-bubble overlay service without opening ZTool: the switch
 * preference survives reboot / APK update, but the foreground service does not.
 *
 * BOOT_COMPLETED delivery additionally depends on the Lenovo Security Center
 * "auto-start" (自启动) authorization for ZTool. If the service is still not
 * running after boot on some ZUI builds, check whether the security center
 * force-stopped the app (stopped-state apps receive no broadcasts).
 */
class EdgeBubbleBootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action != Intent.ACTION_BOOT_COMPLETED &&
            action != Intent.ACTION_MY_PACKAGE_REPLACED &&
            action != ACTION_QUICKBOOT_POWERON
        ) return
        if (EdgeBubbleService.maybeStart(context)) {
            Log.i(TAG, "$action: edge bubble service started")
        }
    }

    companion object {
        private const val TAG = "EdgeBubbleBootReceiver"

        /** OEM quick-boot broadcast; not among the public SDK Intent constants. */
        private const val ACTION_QUICKBOOT_POWERON = "android.intent.action.QUICKBOOT_POWERON"
    }
}
