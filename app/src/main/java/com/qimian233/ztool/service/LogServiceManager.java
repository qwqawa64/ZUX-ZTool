package com.qimian233.ztool.service;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;

/**
 * 日志服务管理器（无需Root权限，采集应用自身日志）
 */
public class LogServiceManager {
    private static final String PREF_NAME = "log_service_prefs";
    private static final String KEY_SERVICE_ENABLED = "log_service_enabled";
    private static final String KEY_SERVICE_RESTART_ATTEMPTS = "service_restart_attempts";
    private static final int MAX_RESTART_ATTEMPTS = 3;

    public interface ServiceStatusListener {
        void onServiceStarted();
        void onServiceStopped();
        void onServiceRestartFailed();
    }

    private static ServiceStatusListener statusListener;

    public static void setServiceStatusListener(ServiceStatusListener listener) {
        statusListener = listener;
    }

    public static void clearCallbacks() {
        statusListener = null;
    }

    /**
     * 启动日志采集服务
     */
    public static void startLogService(Context context) {
        startLogService(context.getApplicationContext(), false);
    }

    private static boolean startLogService(Context context, boolean isRestart) {
        Context appContext = context.getApplicationContext();

        try {
            Intent intent = new Intent(appContext, LogCollectorService.class);
            intent.putExtra("is_restart", isRestart);

            appContext.startForegroundService(intent);

            resetRestartAttempts(appContext);

            android.util.Log.d("LogServiceManager", "日志服务启动成功");

            if (statusListener != null) {
                new Handler(Looper.getMainLooper()).post(() -> statusListener.onServiceStarted());
            }

            return true;
        } catch (Exception e) {
            android.util.Log.e("LogServiceManager", "启动日志服务失败", e);

            if (isRestart) {
                handleRestartFailure(context);
            }

            return false;
        }
    }

    /**
     * 停止日志采集服务
     */
    public static void stopLogService(Context context) {
        Context appContext = context.getApplicationContext();
        try {
            Intent intent = new Intent(appContext, LogCollectorService.class);
            appContext.stopService(intent);
            resetRestartAttempts(appContext);

            android.util.Log.d("LogServiceManager", "日志服务停止成功");

            if (statusListener != null) {
                new Handler(Looper.getMainLooper()).post(() -> statusListener.onServiceStopped());
            }

        } catch (Exception e) {
            android.util.Log.e("LogServiceManager", "停止日志服务失败", e);
        }
    }

    /**
     * 重启服务（如果之前是启用的）
     */
    public static void restartServiceIfNeeded(Context context) {
        Context appContext = context.getApplicationContext();
        int attempts = getRestartAttempts(appContext);

        if (attempts < MAX_RESTART_ATTEMPTS) {
            android.util.Log.d("LogServiceManager",
                    "自动重启日志服务 (尝试次数: " + attempts + ")");

            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                boolean success = startLogService(appContext, true);
                if (!success) {
                    android.util.Log.w("LogServiceManager", "服务重启失败");
                }
            }, 3000);

        } else {
            android.util.Log.w("LogServiceManager",
                    "已达到最大重启尝试次数，停止自动重启");
            resetRestartAttempts(appContext);

            if (statusListener != null) {
                new Handler(Looper.getMainLooper()).post(() -> statusListener.onServiceRestartFailed());
            }
        }
    }

    private static void handleRestartFailure(Context context) {
        int attempts = incrementRestartAttempts(context);
        android.util.Log.w("LogServiceManager",
                "服务重启失败，当前尝试次数: " + attempts);

        if (attempts >= MAX_RESTART_ATTEMPTS) {
            android.util.Log.e("LogServiceManager",
                    "达到最大重启尝试次数，服务将不会自动重启");
            resetRestartAttempts(context);

            if (statusListener != null) {
                new Handler(Looper.getMainLooper()).post(() -> statusListener.onServiceRestartFailed());
            }
        }
    }

    public static boolean isServiceEnabled(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        return prefs.getBoolean(KEY_SERVICE_ENABLED, false);
    }

    private static int getRestartAttempts(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        return prefs.getInt(KEY_SERVICE_RESTART_ATTEMPTS, 0);
    }

    private static int incrementRestartAttempts(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        int attempts = prefs.getInt(KEY_SERVICE_RESTART_ATTEMPTS, 0) + 1;
        prefs.edit().putInt(KEY_SERVICE_RESTART_ATTEMPTS, attempts).apply();
        return attempts;
    }

    private static void resetRestartAttempts(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        prefs.edit().putInt(KEY_SERVICE_RESTART_ATTEMPTS, 0).apply();
    }

    private static void setServiceEnabled(Context context, boolean enabled) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        prefs.edit().putBoolean(KEY_SERVICE_ENABLED, enabled).apply();
    }
}
