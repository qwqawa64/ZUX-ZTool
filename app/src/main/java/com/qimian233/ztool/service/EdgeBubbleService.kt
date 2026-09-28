package com.qimian233.ztool.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.Binder
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Parcel
import android.util.Log
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import com.qimian233.ztool.R
import com.qimian233.ztool.utils.FreeformBubbleBridge

/**
 * Hosts the freeform edge-bubble overlay windows. One [EdgeBubbleView] per minimized
 * freeform task; task events arrive from system_server through
 * [FreeformBubbleBridge.registerCallback]. Started/stopped by the frontend switch
 * (PreferenceKeys.FREEFORM_EDGE_BUBBLE).
 */
class EdgeBubbleService : Service() {

    companion object {
        private const val TAG = "EdgeBubbleService"
        private const val CHANNEL_ID = "edge_bubble_channel"
        private const val NOTIFICATION_ID = 2001
        /** Base vertical ratio for bubbles; per-bubble stagger keeps them separable. */
        private const val BASE_CENTER_Y_RATIO = 0.35f
        private const val CENTER_Y_STAGGER_RATIO = 0.15f
    }

    private val bubbles = HashMap<Int, EdgeBubbleView>()
    private var callbackRegistered = false
    private val mainHandler = Handler(Looper.getMainLooper())

    private val eventCallback = object : Binder() {
        override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
            if (code != Binder.INTERFACE_TRANSACTION) {
                data.enforceInterface(FreeformBubbleBridge.DESCRIPTOR)
            }
            when (code) {
                FreeformBubbleBridge.CB_BUBBLE_ADDED -> {
                    val taskId = data.readInt()
                    val side = data.readInt()
                    val pkg = data.readString()
                    mainHandler.post { addBubble(taskId, side, pkg) }
                    return true
                }
                FreeformBubbleBridge.CB_BUBBLE_REMOVED -> {
                    val taskId = data.readInt()
                    mainHandler.post { removeBubble(taskId) }
                    return true
                }
                else -> return super.onTransact(code, data, reply, flags)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        startForegroundWithNotification()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!callbackRegistered) {
            callbackRegistered = true
            FreeformBubbleBridge.registerCallback(eventCallback)
            // Reconcile with tasks that were minimized before this service started.
            mainHandler.post { reconcileExisting() }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        mainHandler.post { removeAllBubbles() }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onTaskRemoved(rootIntent: Intent?) {
        // Keep running with the bubbles; the overlay outlives the task UI.
        super.onTaskRemoved(rootIntent)
    }

    // region bubble management (main thread)

    private fun reconcileExisting() {
        if (bubbles.isNotEmpty()) return
        for (task in FreeformBubbleBridge.listMinimized()) {
            addBubble(task.taskId, task.side, task.packageName)
        }
    }

    private fun addBubble(taskId: Int, side: Int, pkg: String?) {
        if (bubbles.containsKey(taskId)) return
        val view = try {
            EdgeBubbleView(
                this, taskId, side,
                BASE_CENTER_Y_RATIO + (bubbles.size % 3) * CENTER_Y_STAGGER_RATIO,
                listener = object : EdgeBubbleView.Listener {
                    override fun onBubbleRestore(taskId: Int) {
                        FreeformBubbleBridge.restoreTask(taskId)
                    }

                    override fun onBubbleSettled(taskId: Int, side: Int, centerYRatio: Float) {
                        // v1: side/y are per-bubble view state; nothing to persist yet.
                    }
                }
            )
        } catch (t: Throwable) {
            Log.e(TAG, "create bubble view failed", t)
            return
        }
        try {
            windowManager.addView(view, view.layoutParams)
        } catch (t: Throwable) {
            Log.e(TAG, "addView failed for task $taskId", t)
            return
        }
        bubbles[taskId] = view
        pkg?.let { loadIconAsync(taskId, it) }
        Log.i(TAG, "bubble added task=$taskId side=$side pkg=$pkg count=${bubbles.size}")
    }

    private fun loadIconAsync(taskId: Int, pkg: String) {
        Thread {
            val icon: Drawable? = try {
                packageManager.getApplicationIcon(pkg)
            } catch (_: PackageManager.NameNotFoundException) {
                null
            }
            mainHandler.post {
                bubbles[taskId]?.setAppIcon(icon)
            }
        }.start()
    }

    private fun removeBubble(taskId: Int) {
        val view = bubbles.remove(taskId) ?: return
        view.release()
        try {
            windowManager.removeView(view)
        } catch (t: Throwable) {
            Log.w(TAG, "removeView failed for task $taskId: ${t.message}")
        }
        Log.i(TAG, "bubble removed task=$taskId count=${bubbles.size}")
    }

    private fun removeAllBubbles() {
        for (taskId in bubbles.keys.toList()) {
            removeBubble(taskId)
        }
    }

    private val windowManager get() = getSystemService(WindowManager::class.java)

    // endregion

    // region foreground notification

    private fun startForegroundWithNotification() {
        val manager = getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.edge_bubble_notification_channel),
            NotificationManager.IMPORTANCE_MIN
        )
        manager.createNotificationChannel(channel)
        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .setContentTitle(getString(R.string.edge_bubble_notification_title))
            .setContentText(getString(R.string.edge_bubble_notification_text))
            .setOngoing(true)
            .build()
        startForeground(NOTIFICATION_ID, notification)
    }

    // endregion
}
