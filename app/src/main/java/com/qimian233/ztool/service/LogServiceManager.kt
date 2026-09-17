package com.qimian233.ztool.service

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Log

/**
 * Log service manager (no root required; collects the app's own logs)
 */
object LogServiceManager {
    private const val TAG = "LogServiceManager"
    private const val PREF_NAME = "log_service_prefs"
    private const val KEY_SERVICE_ENABLED = "log_service_enabled"
    private const val KEY_SERVICE_RESTART_ATTEMPTS = "service_restart_attempts"
    private const val MAX_RESTART_ATTEMPTS = 3

    interface ServiceStatusListener {
        fun onServiceStarted()
        fun onServiceStopped()
        fun onServiceRestartFailed()
    }

    private var statusListener: ServiceStatusListener? = null

    fun setServiceStatusListener(listener: ServiceStatusListener?) {
        statusListener = listener
    }

    fun clearCallbacks() {
        statusListener = null
    }

    /**
     * Start the log collection service
     */
    fun startLogService(context: Context) {
        startLogService(context.applicationContext, false)
    }

    private fun startLogService(context: Context, isRestart: Boolean): Boolean {
        val appContext = context.applicationContext

        return try {
            val intent = Intent(appContext, LogCollectorService::class.java)
            intent.putExtra("is_restart", isRestart)

            appContext.startForegroundService(intent)

            resetRestartAttempts(appContext)

            Log.d(TAG, "log service started")

            statusListener?.let { listener ->
                Handler(Looper.getMainLooper()).post { listener.onServiceStarted() }
            }

            true
        } catch (e: Exception) {
            Log.e(TAG, "failed to start log service", e)

            if (isRestart) {
                handleRestartFailure(context)
            }

            false
        }
    }

    /**
     * Stop the log collection service
     */
    fun stopLogService(context: Context) {
        val appContext = context.applicationContext
        try {
            val intent = Intent(appContext, LogCollectorService::class.java)
            appContext.stopService(intent)
            resetRestartAttempts(appContext)

            Log.d(TAG, "log service stopped")

            statusListener?.let { listener ->
                Handler(Looper.getMainLooper()).post { listener.onServiceStopped() }
            }
        } catch (e: Exception) {
            Log.e(TAG, "failed to stop log service", e)
        }
    }

    /**
     * Restart the service (if it was previously enabled)
     */
    fun restartServiceIfNeeded(context: Context) {
        val appContext = context.applicationContext
        val attempts = getRestartAttempts(appContext)

        if (attempts < MAX_RESTART_ATTEMPTS) {
            Log.d(TAG, "auto-restarting log service (attempt: $attempts)")

            Handler(Looper.getMainLooper()).postDelayed({
                val success = startLogService(appContext, true)
                if (!success) {
                    Log.w(TAG, "service restart failed")
                }
            }, 3000)
        } else {
            Log.w(TAG, "max restart attempts reached, stopping auto-restart")
            resetRestartAttempts(appContext)

            statusListener?.let { listener ->
                Handler(Looper.getMainLooper()).post { listener.onServiceRestartFailed() }
            }
        }
    }

    private fun handleRestartFailure(context: Context) {
        val attempts = incrementRestartAttempts(context)
        Log.w(TAG, "service restart failed, current attempt count: $attempts")

        if (attempts >= MAX_RESTART_ATTEMPTS) {
            Log.e(TAG, "max restart attempts reached, service will not auto-restart")
            resetRestartAttempts(context)

            statusListener?.let { listener ->
                Handler(Looper.getMainLooper()).post { listener.onServiceRestartFailed() }
            }
        }
    }

    fun isServiceEnabled(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_SERVICE_ENABLED, false)
    }

    private fun getRestartAttempts(context: Context): Int {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        return prefs.getInt(KEY_SERVICE_RESTART_ATTEMPTS, 0)
    }

    private fun incrementRestartAttempts(context: Context): Int {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val attempts = prefs.getInt(KEY_SERVICE_RESTART_ATTEMPTS, 0) + 1
        prefs.edit().putInt(KEY_SERVICE_RESTART_ATTEMPTS, attempts).apply()
        return attempts
    }

    private fun resetRestartAttempts(context: Context) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        prefs.edit().putInt(KEY_SERVICE_RESTART_ATTEMPTS, 0).apply()
    }
}
