package com.qimian233.ztool.service;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import com.qimian233.ztool.utils.PermissionChecker;

/**
 * 增强的日志服务管理器（支持Root模式）
 */
public class LogServiceManager {
    private static final String PREF_NAME = "log_service_prefs";
    private static final String KEY_SERVICE_ENABLED = "log_service_enabled";
    private static final String KEY_USE_ROOT_MODE = "use_root_mode";

    /**
     * 启动日志采集服务（自动选择Root模式）
     */
    public static boolean startLogService(Context context) {
        // 检查权限状态
        boolean hasRoot = PermissionChecker.hasRootPermission();
        boolean hasReadLogs = PermissionChecker.hasReadLogsPermission(context);

        android.util.Log.d("LogServiceManager",
                String.format("权限状态 - Root: %s, READ_LOGS: %s", hasRoot, hasReadLogs));

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
            context.startService(intent);
            setServiceEnabled(context, true);
            android.util.Log.d("LogServiceManager", "日志服务启动成功");
            return true;
        } catch (Exception e) {
            android.util.Log.e("LogServiceManager", "启动日志服务失败", e);
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
            android.util.Log.d("LogServiceManager", "日志服务停止成功");
            return true;
        } catch (Exception e) {
            android.util.Log.e("LogServiceManager", "停止日志服务失败", e);
            return false;
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
