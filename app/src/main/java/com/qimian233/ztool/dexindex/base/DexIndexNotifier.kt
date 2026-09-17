package com.qimian233.ztool.dexindex.base

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.qimian233.ztool.R

/**
 * Fallback result notification for offline index scans.
 *
 * Progress feedback for the foreground scenario is handled by the UI progress
 * dialog (see DexIndexProgressDialog); this object only posts a result
 * notification after the scan finishes and auto-dismisses after a few seconds.
 * For example, when DexIndexReceiver triggers indexing in the background on
 * module update while the app is not in the foreground, users with notification
 * permission still get feedback.
 *
 * Note: Android 13+ requires the runtime `POST_NOTIFICATIONS` permission; when
 * not granted, notifications are silently skipped (the scan itself is not
 * affected, and the foreground UI still toasts the result).
 */
object DexIndexNotifier {

    private const val TAG = "DexIndexNotifier"
    private const val CHANNEL_ID = "dex_index_channel"
    private const val NOTIFICATION_ID = 0xD11
    private const val AUTO_CANCEL_DELAY_MS = 3_000L

    /** Scan finished: update to a result notification and auto-cancel after a few seconds. */
    fun finish(context: Context, results: Map<String, Boolean>) {
        if (!canNotify(context)) return
        try {
            ensureChannel(context)
            val success = results.values.count { it }
            val total = results.size
            val done = total > 0 && success == total
            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(
                    context.getString(
                        if (done) R.string.common_dex_index_notify_done else R.string.common_dex_index_notify_failed
                    )
                )
                .setContentText(context.getString(R.string.common_dex_index_notify_done_text, success))
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setAutoCancel(true)
                .build()
            val nm = notificationManager(context) ?: return
            nm.notify(NOTIFICATION_ID, notification)
            // Auto-clear the result notification shortly after it is shown
            Thread {
                try {
                    Thread.sleep(AUTO_CANCEL_DELAY_MS)
                    nm.cancel(NOTIFICATION_ID)
                } catch (_: Throwable) {
                }
            }.start()
        } catch (t: Throwable) {
            Log.w(TAG, "failed to update result notification", t)
        }
    }

    private fun notificationManager(context: Context): NotificationManager? =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager

    private fun ensureChannel(context: Context) {
        val nm = notificationManager(context) ?: return
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.common_dex_index_channel_name),
            NotificationManager.IMPORTANCE_LOW
        )
        channel.setShowBadge(false)
        channel.lockscreenVisibility = Notification.VISIBILITY_PRIVATE
        nm.createNotificationChannel(channel)
    }

    /** Android 13+ requires runtime permission; silently skip notifications when not granted. */
    private fun canNotify(context: Context): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
    }
}
