package com.qimian233.ztool.service

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Log

/**
 * 日志服务管理器（无需Root权限，采集应用自身日志）
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
     * 启动日志采集服务
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

            Log.d(TAG, "日志服务启动成功")

            statusListener?.let { listener ->
                Handler(Looper.getMainLooper()).post { listener.onServiceStarted() }
            }

            true
        } catch (e: Exception) {
            Log.e(TAG, "启动日志服务失败", e)

            if (isRestart) {
                handleRestartFailure(context)
            }

            false
        }
    }

    /**
     * 停止日志采集服务
     */
    fun stopLogService(context: Context) {
        val appContext = context.applicationContext
        try {
            val intent = Intent(appContext, LogCollectorService::class.java)
            appContext.stopService(intent)
            resetRestartAttempts(appContext)

            Log.d(TAG, "日志服务停止成功")

            statusListener?.let { listener ->
                Handler(Looper.getMainLooper()).post { listener.onServiceStopped() }
            }
        } catch (e: Exception) {
            Log.e(TAG, "停止日志服务失败", e)
        }
    }

    /**
     * 重启服务（如果之前是启用的）
     */
    fun restartServiceIfNeeded(context: Context) {
        val appContext = context.applicationContext
        val attempts = getRestartAttempts(appContext)

        if (attempts < MAX_RESTART_ATTEMPTS) {
            Log.d(TAG, "自动重启日志服务 (尝试次数: $attempts)")

            Handler(Looper.getMainLooper()).postDelayed({
                val success = startLogService(appContext, true)
                if (!success) {
                    Log.w(TAG, "服务重启失败")
                }
            }, 3000)
        } else {
            Log.w(TAG, "已达到最大重启尝试次数，停止自动重启")
            resetRestartAttempts(appContext)

            statusListener?.let { listener ->
                Handler(Looper.getMainLooper()).post { listener.onServiceRestartFailed() }
            }
        }
    }

    private fun handleRestartFailure(context: Context) {
        val attempts = incrementRestartAttempts(context)
        Log.w(TAG, "服务重启失败，当前尝试次数: $attempts")

        if (attempts >= MAX_RESTART_ATTEMPTS) {
            Log.e(TAG, "达到最大重启尝试次数，服务将不会自动重启")
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
