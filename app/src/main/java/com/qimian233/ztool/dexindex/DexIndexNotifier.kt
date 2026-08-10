package com.qimian233.ztool.dexindex

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
 * 离线索引扫描的前台进度通知。
 *
 * 三个触发源（Receiver / 启动检查 / 设置页手动刷新）统一经 [DexIndexManager]
 * 调用，扫描开始发"进行中"通知，完成/失败更新后数秒自动消失。
 *
 * 注意：Android 13+ 需要运行时授权 `POST_NOTIFICATIONS`，未授权时静默跳过通知
 * （扫描本身不受影响，设置页仍会 Toast 结果）。
 */
object DexIndexNotifier {

    private const val TAG = "DexIndexNotifier"
    private const val CHANNEL_ID = "dex_index_channel"
    private const val NOTIFICATION_ID = 0xD11
    private const val AUTO_CANCEL_DELAY_MS = 3_000L

    /** 扫描开始：发"进行中"（不确定进度）通知。 */
    fun start(context: Context) {
        if (!canNotify(context)) return
        try {
            ensureChannel(context)
            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(context.getString(R.string.dexIndexNotifyTitle))
                .setContentText(context.getString(R.string.dexIndexNotifyText))
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setProgress(0, 0, true)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .build()
            notificationManager(context)?.notify(NOTIFICATION_ID, notification)
        } catch (t: Throwable) {
            Log.w(TAG, "failed to show progress notification", t)
        }
    }

    /** 扫描结束：更新为结果通知，数秒后自动取消。 */
    fun finish(context: Context, results: Map<String, Boolean>) {
        if (!canNotify(context)) return
        try {
            val success = results.values.count { it }
            val total = results.size
            val done = total > 0 && success == total
            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(
                    context.getString(
                        if (done) R.string.dexIndexNotifyDone else R.string.dexIndexNotifyFailed
                    )
                )
                .setContentText(context.getString(R.string.dexIndexNotifyDoneText, success))
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setAutoCancel(true)
                .build()
            val nm = notificationManager(context) ?: return
            nm.notify(NOTIFICATION_ID, notification)
            // 结果通知短暂展示后自动清除
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
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = notificationManager(context) ?: return
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.dexIndexChannelName),
            NotificationManager.IMPORTANCE_LOW
        )
        channel.setShowBadge(false)
        channel.setLockscreenVisibility(Notification.VISIBILITY_PRIVATE)
        nm.createNotificationChannel(channel)
    }

    /** Android 13+ 需要运行时授权；未授权时静默跳过通知。 */
    private fun canNotify(context: Context): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
    }
}
