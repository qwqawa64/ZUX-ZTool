package com.qimian233.ztool.service;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import com.qimian233.ztool.utils.PermissionChecker;

/**
 * 增强的日志服务管理器（支持Root模式和自动重启）
 */
public class LogServiceManager {
    private static final String PREF_NAME = "log_service_prefs";
    private static final String KEY_SERVICE_ENABLED = "log_service_enabled";
    private static final String KEY_USE_ROOT_MODE = "use_root_mode";
    private static final String KEY_SERVICE_RESTART_ATTEMPTS = "service_restart_attempts";
    private static final int MAX_RESTART_ATTEMPTS = 3;
    private static final long RESTART_DELAY_MS = 2000;

    // 服务状态监听器
    public interface ServiceStatusListener {
        void onServiceStarted();
        void onServiceStopped();
        void onServiceRestartFailed();
    }

    private static ServiceStatusListener statusListener;

    /**
     * 设置服务状态监听器
     */
    public static void setServiceStatusListener(ServiceStatusListener listener) {
        statusListener = listener;
    }

    /**
     * 启动日志采集服务（自动选择Root模式）
     */
    public static boolean startLogService(Context context) {
        return startLogService(context, false);
    }

    /**
     * 启动日志采集服务（支持重启模式）
     */
    private static boolean startLogService(Context context, boolean isRestart) {
        // 检查权限状态
        boolean hasRoot = PermissionChecker.hasRootPermission();
        boolean hasReadLogs = PermissionChecker.hasReadLogsPermission(context);
        // 检查前台服务权限
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            if (context.checkSelfPermission(android.Manifest.permission.FOREGROUND_SERVICE)
                    != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                android.util.Log.w("LogServiceManager", "缺少 FOREGROUND_SERVICE 权限，服务可能无法正常启动");
                // 继续启动，让服务自己处理权限问题
            }
        }


        android.util.Log.d("LogServiceManager",
                String.format("权限状态 - Root: %s, READ_LOGS: %s, 重启模式: %s",
                        hasRoot, hasReadLogs, isRestart));

        // 自动选择最佳模式
        boolean useRootMode = hasRoot;
        setUseRootMode(context, useRootMode);

        if (useRootMode) {
            android.util.Log.d("LogServiceManager", "使用Root模式启动日志服务");
        } else {
            android.util.Log.w("LogServiceManager",
                    "无Root权限，日志收集可能不完整，只能收集应用自身日志");
        }

        try {
            Intent intent = new Intent(context, LogCollectorService.class);
            intent.putExtra("is_restart", isRestart);

            // 对于 Android 8.0+ 使用 startForegroundService，其他使用 startService
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent);
            } else {
                context.startService(intent);
            }

            setServiceEnabled(context, true);
            resetRestartAttempts(context);

            android.util.Log.d("LogServiceManager", "日志服务启动成功");

            if (statusListener != null) {
                new Handler(Looper.getMainLooper()).post(() -> {
                    statusListener.onServiceStarted();
                });
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
    public static boolean stopLogService(Context context) {
        try {
            Intent intent = new Intent(context, LogCollectorService.class);
            context.stopService(intent);
            setServiceEnabled(context, false);
            resetRestartAttempts(context);

            android.util.Log.d("LogServiceManager", "日志服务停止成功");

            // 通知监听器
            if (statusListener != null) {
                new Handler(Looper.getMainLooper()).post(() -> {
                    statusListener.onServiceStopped();
                });
            }

            return true;
        } catch (Exception e) {
            android.util.Log.e("LogServiceManager", "停止日志服务失败", e);
            return false;
        }
    }

    /**
     * 重启服务（如果之前是启用的）
     * 用于应用被杀死后重新启动时恢复服务
     */
    // 在 restartServiceIfNeeded 方法中添加延迟
    public static void restartServiceIfNeeded(Context context) {
        if (isServiceEnabled(context)) {
            int attempts = getRestartAttempts(context);

            if (attempts < MAX_RESTART_ATTEMPTS) {
                android.util.Log.d("LogServiceManager",
                        "检测到服务之前是启用的，延迟重启 (尝试次数: " + attempts + ")");

                // 增加延迟时间，确保系统稳定
                new Handler(Looper.getMainLooper()).postDelayed(() -> {
                    boolean success = startLogService(context, true);
                    if (!success) {
                        android.util.Log.w("LogServiceManager", "服务重启失败");
                    }
                }, 3000); // 增加到3秒延迟

            } else {
                android.util.Log.w("LogServiceManager",
                        "已达到最大重启尝试次数，停止自动重启");
                setServiceEnabled(context, false);

                if (statusListener != null) {
                    new Handler(Looper.getMainLooper()).post(() -> {
                        statusListener.onServiceRestartFailed();
                    });
                }
            }
        }
    }


    /**
     * 处理重启失败
     */
    private static void handleRestartFailure(Context context) {
        int attempts = incrementRestartAttempts(context);
        android.util.Log.w("LogServiceManager",
                "服务重启失败，当前尝试次数: " + attempts);

        if (attempts >= MAX_RESTART_ATTEMPTS) {
            android.util.Log.e("LogServiceManager",
                    "达到最大重启尝试次数，服务将不会自动重启");
            setServiceEnabled(context, false);

            if (statusListener != null) {
                new Handler(Looper.getMainLooper()).post(() -> {
                    statusListener.onServiceRestartFailed();
                });
            }
        }
    }

    /**
     * 检查服务是否启用
     */
    public static boolean isServiceEnabled(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        return prefs.getBoolean(KEY_SERVICE_ENABLED, false);
    }

    /**
     * 检查是否使用Root模式
     */
    public static boolean isUseRootMode(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        return prefs.getBoolean(KEY_USE_ROOT_MODE, false);
    }

    /**
     * 获取重启尝试次数
     */
    private static int getRestartAttempts(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        return prefs.getInt(KEY_SERVICE_RESTART_ATTEMPTS, 0);
    }

    /**
     * 增加重启尝试次数
     */
    private static int incrementRestartAttempts(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        int attempts = prefs.getInt(KEY_SERVICE_RESTART_ATTEMPTS, 0) + 1;
        prefs.edit().putInt(KEY_SERVICE_RESTART_ATTEMPTS, attempts).apply();
        return attempts;
    }

    /**
     * 重置重启尝试次数
     */
    private static void resetRestartAttempts(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        prefs.edit().putInt(KEY_SERVICE_RESTART_ATTEMPTS, 0).apply();
    }

    private static void setServiceEnabled(Context context, boolean enabled) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        prefs.edit().putBoolean(KEY_SERVICE_ENABLED, enabled).apply();
    }

    private static void setUseRootMode(Context context, boolean useRoot) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        prefs.edit().putBoolean(KEY_USE_ROOT_MODE, useRoot).apply();
    }

    /**
     * 获取服务状态信息
     */
    public static String getServiceStatus(Context context) {
        StringBuilder status = new StringBuilder();
        status.append("服务状态: ").append(isServiceEnabled(context) ? "运行中" : "已停止").append("\n");
        status.append("运行模式: ").append(isUseRootMode(context) ? "Root模式" : "普通模式").append("\n");
        status.append("重启尝试: ").append(getRestartAttempts(context)).append("/").append(MAX_RESTART_ATTEMPTS).append("\n");
        status.append(PermissionChecker.getPermissionStatus(context));
        return status.toString();
    }

    /**
     * 强制使用Root模式（如果可用）
     */
    public static boolean forceRootMode(Context context) {
        if (PermissionChecker.hasRootPermission()) {
            setUseRootMode(context, true);

            // 如果服务正在运行，重启以应用新模式
            if (isServiceEnabled(context)) {
                stopLogService(context);
                try {
                    Thread.sleep(1000); // 等待服务完全停止
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                return startLogService(context);
            }
            return true;
        }
        return false;
    }
}
