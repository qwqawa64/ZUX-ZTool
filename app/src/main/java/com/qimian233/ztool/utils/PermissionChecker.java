package com.qimian233.ztool.utils;

import android.content.Context;
import android.content.pm.PackageManager;
import java.io.BufferedReader;
import java.io.InputStreamReader;

/**
 * Root权限检查工具
 */
public class PermissionChecker {

    /**
     * 检查是否有Root权限
     */
    public static boolean hasRootPermission() {
        try {
            Process process = Runtime.getRuntime().exec("su -c id");
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line = reader.readLine();
            process.waitFor();

            boolean hasRoot = (line != null && line.contains("uid=0"));
            android.util.Log.d("RootPermissionChecker", "Root权限检查: " + (hasRoot ? "有权限" : "无权限"));
            return hasRoot;
        } catch (Exception e) {
            android.util.Log.e("RootPermissionChecker", "Root权限检查异常", e);
            return false;
        }
    }

    /**
     * 检查是否有读取日志权限
     */
    public static boolean hasReadLogsPermission(Context context) {
        try {
            int result = context.checkPermission(
                    "android.permission.READ_LOGS",
                    android.os.Process.myPid(),
                    android.os.Process.myUid()
            );
            boolean hasPermission = (result == PackageManager.PERMISSION_GRANTED);
            android.util.Log.d("RootPermissionChecker", "READ_LOGS权限: " + (hasPermission ? "有权限" : "无权限"));
            return hasPermission;
        } catch (Exception e) {
            android.util.Log.e("RootPermissionChecker", "检查权限失败", e);
            return false;
        }
    }

    /**
     * 获取完整的权限状态报告
     */
    public static String getPermissionStatus(Context context) {
        StringBuilder status = new StringBuilder();
        status.append("Root权限: ").append(hasRootPermission() ? "✓" : "✗").append("\n");
        status.append("READ_LOGS权限: ").append(hasReadLogsPermission(context) ? "✓" : "✗").append("\n");
        status.append("建议: ").append(hasRootPermission() ? "使用Root模式收集完整日志" : "只能收集应用自身日志");
        return status.toString();
    }
}
