package com.qimian233.ztool.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import com.qimian233.ztool.R
import com.qimian233.ztool.utils.FreeformBubbleBridge

/**
 * Hosts the freeform edge-bubble overlay windows. One [EdgeBubbleView] per minimized
 * freeform task; task events arrive from system_server via
 * [FreeformBubbleBridge.ACTION_EVENT] broadcasts. Started/stopped by the frontend
 * switch (PreferenceKeys.FREEFORM_EDGE_BUBBLE).
 */
class EdgeBubbleService : Service() {

    companion object {
        private const val TAG = "EdgeBubbleService"
        private const val CHANNEL_ID = "edge_bubble_channel"
        private const val NOTIFICATION_ID = 2001
        /** Base vertical ratio for bubbles; per-bubble stagger keeps them separable. */
        private const val BASE_CENTER_Y_RATIO = 0.35f
        private const val CENTER_Y_STAGGER_RATIO = 0.15f
        private const val POLL_INTERVAL_MS = 600L
        /** Window flush to the display edge within this margin triggers a bubble. */
        private const val EDGE_TOUCH_MARGIN_PX = 48
        /** Small cooldown after a bubble restore; the hook now nudges restored
         *  bounds 96px inward, clear of the 48px edge-trigger zone. */
        private const val RESTORE_COOLDOWN_MS = 1000L
    }

    private val bubbles = HashMap<Int, EdgeBubbleView>()
    private var eventReceiver: BroadcastReceiver? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private val restoredAt = HashMap<Int, Long>()
    private val bubblePrefs by lazy {
        getSharedPreferences("edge_bubble_pos", MODE_PRIVATE)
    }
    private var polling = false
    private var realDisplayWidth = 0
    private var realDisplayHeight = 0

    override fun onCreate() {
        super.onCreate()
        startForegroundWithNotification()
        FreeformBubbleBridge.init(this)
        eventReceiver = FreeformBubbleBridge.registerEventReceiver(
            this,
            onBubbleAdded = { taskId, side, pkg ->
                mainHandler.post { addBubble(taskId, side, pkg) }
            },
            onBubbleRemoved = { taskId ->
                mainHandler.post { removeBubble(taskId) }
            })
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startPolling()
        // Reconcile with tasks that were minimized before this service started.
        mainHandler.post { reconcileExisting() }
        return START_STICKY
    }

    override fun onDestroy() {
        polling = false
        mainHandler.removeCallbacksAndMessages(null)
        eventReceiver?.let { FreeformBubbleBridge.unregisterEventReceiver(this, it) }
        eventReceiver = null
        mainHandler.post { removeAllBubbles() }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onTaskRemoved(rootIntent: Intent?) {
        // Keep running with the bubbles; the overlay outlives the task UI.
        super.onTaskRemoved(rootIntent)
    }

    // region bubble management (main thread)

    /**
     * App-side edge detection (option 1): poll the hook's freeform task snapshot and
     * dock+bubble any visible task flushed against the display edge. The hook-side
     * setBounds hook does not see ZUI shell drags, hence this poller.
     */
    private fun startPolling() {
        if (polling) return
        polling = true
        // maximumWindowMetrics: ZTool itself may be inside a freeform window, where
        // currentWindowMetrics would return the small window bounds instead of the
        // display's.
        val metricsBounds = try {
            windowManager.maximumWindowMetrics.bounds
        } catch (_: Throwable) { null }
        realDisplayWidth = metricsBounds?.width() ?: resources.displayMetrics.widthPixels
        realDisplayHeight = metricsBounds?.height() ?: resources.displayMetrics.heightPixels
        mainHandler.postDelayed({ pollOnce() }, POLL_INTERVAL_MS)
    }

    private fun pollOnce() {
        if (!polling) return
        FreeformBubbleBridge.listFreeformTasks { tasks ->
            mainHandler.post { handleFreeformTasks(tasks) }
            if (polling) mainHandler.postDelayed({ pollOnce() }, POLL_INTERVAL_MS)
        }
    }

    private fun handleFreeformTasks(tasks: List<FreeformBubbleBridge.FreeformTask>) {
        if (!polling) return
        val now = SystemClock.elapsedRealtime()
        // Present = still a freeform task, visible OR docked-hidden. A docked task
        // must keep its bubble; only leaving freeform entirely removes it.
        val presentIds = HashSet<Int>()
        for (task in tasks) {
            presentIds.add(task.taskId)
            if (task.hidden) continue // docked behind home: bubble is already up
            val side = when {
                task.bounds.left <= EDGE_TOUCH_MARGIN_PX -> 0
                realDisplayWidth - task.bounds.right <= EDGE_TOUCH_MARGIN_PX -> 1
                else -> -1
            }
            val cooledDown = now - (restoredAt[task.taskId] ?: 0L) >= RESTORE_COOLDOWN_MS
            if (side >= 0 && !bubbles.containsKey(task.taskId) && cooledDown) {
                Log.i(TAG, "edge detected task=${task.taskId} side=$side bounds=${task.bounds}")
                // Refresh the remembered bubble height from the window's vertical
                // center just before it docks, on every bubble-ization.
                val ratio = ((task.bounds.top + task.bounds.bottom) / 2f)
                    .coerceIn(0f, realDisplayHeight.toFloat()) / realDisplayHeight
                bubblePrefs.edit().putFloat("cy_${task.taskId}", ratio).apply()
                FreeformBubbleBridge.minimizeTask(task.taskId, side)
                addBubble(task.taskId, side, task.packageName)
            }
        }
        // A tracked task that left freeform (e.g. exited via launcher) loses its bubble.
        for (taskId in bubbles.keys.toList()) {
            if (taskId !in presentIds) {
                restoredAt.remove(taskId)
                removeBubble(taskId)
                bubblePrefs.edit().remove("cy_$taskId").apply()
            }
        }
    }

    /** Remembered vertical ratio for a task's bubble (0..1 of screen height). */
    private fun storedCenterYRatio(taskId: Int): Float =
        bubblePrefs.getFloat("cy_$taskId", Float.NaN)

    private fun reconcileExisting() {
        if (bubbles.isNotEmpty()) return
        FreeformBubbleBridge.listMinimized { tasks ->
            mainHandler.post {
                for (task in tasks) {
                    addBubble(task.taskId, task.side, task.packageName)
                }
            }
        }
    }

    private fun addBubble(taskId: Int, side: Int, pkg: String?) {
        if (bubbles.containsKey(taskId)) return
        val remembered = storedCenterYRatio(taskId)
        val centerYRatio = if (remembered.isNaN())
            BASE_CENTER_Y_RATIO + (bubbles.size % 3) * CENTER_Y_STAGGER_RATIO
        else remembered
        val view = try {
            EdgeBubbleView(
                this, taskId, side,
                centerYRatio,
                listener = object : EdgeBubbleView.Listener {
                    override fun onBubbleRestore(taskId: Int) {
                        restoredAt[taskId] = SystemClock.elapsedRealtime()
                        FreeformBubbleBridge.restoreTask(taskId)
                    }

                    override fun onBubbleSettled(taskId: Int, side: Int, centerYRatio: Float) {
                        bubblePrefs.edit().putFloat("cy_$taskId", centerYRatio).apply()
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
