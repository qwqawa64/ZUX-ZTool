package com.qimian233.ztool.utils

import android.content.Context
import android.net.Uri
import android.util.Log
import android.widget.Toast
import com.qimian233.ztool.EnhancedShellExecutor
import com.qimian233.ztool.R
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 日志工具类：管理应用日志（导出、清理、LSPosed 同步）
 */
object LogUtils {
    private const val TAG = "LogUtils"
    private const val LOG_DIR_NAME = "Log"
    private const val APP_LOG_SUBDIR = "app"
    private const val LSPOSED_SUBDIR = "lsposed"

    fun logDir(context: Context): File = File(context.filesDir, LOG_DIR_NAME)

    fun appLogDir(context: Context): File = File(logDir(context), APP_LOG_SUBDIR)

    fun lsposedLogDir(context: Context): File = File(logDir(context), LSPOSED_SUBDIR)

    fun exportFileName(): String {
        return "ZTool_Logs_" +
            SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date()) +
            ".zip"
    }

    fun exportLogsToUri(context: Context, uri: Uri): Boolean {
        syncLsposedLogs(context)

        val zipFile = zipLogDir(context) ?: return false
        return FileManager.exportFileWithSAF(
            context,
            uri,
            "logs_" + SimpleDateFormat("yyyy-MM-dd_HHmmss", Locale.US).format(Date()) + ".zip",
            zipFile
        )
    }

    private fun zipLogDir(context: Context): File? {
        val dir = logDir(context)
        if (!dir.exists() || !dir.isDirectory()) return null

        val entries = dir.listFiles()
        if (entries.isNullOrEmpty()) return null

        val outputDir = File(context.cacheDir, "temp")
        if (!outputDir.exists() && !outputDir.mkdirs()) return null

        val zipFile = File(
            outputDir,
            "logs_" + SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date()) + ".zip"
        )
        return if (FileUtils.createZipFromDirectory(dir, zipFile)) zipFile else null
    }

    /**
     * 清理应用日志：如果 Log/app/ 目录下文件总大小超过 10MB，则全部删除
     */
    fun cleanupAppLogsIfNeeded(context: Context) {
        val dir = appLogDir(context)
        if (!dir.exists() || !dir.isDirectory()) return

        val files = dir.listFiles() ?: return
        val totalSize = files.sumOf { it.length() }
        val maxSize = 10L * 1024 * 1024 // 10MB

        if (totalSize > maxSize) {
            Log.i(TAG, "应用日志总大小 $totalSize 超过 10MB，自动清理")
            for (file in files) {
                file.delete()
            }
        }
    }

    /**
     * 删除所有日志（app + lsposed）
     */
    fun deleteAllLogs(context: Context) {
        val dir = logDir(context)
        if (dir.exists() && dir.isDirectory()) {
            FileUtils.deleteRecursive(dir)
            Log.i(TAG, "所有日志已删除")
        }
    }

    /**
     * 从 /data/adb/lspd/log 同步 LSPosed 日志到应用私有目录
     * 需要 Root 权限，全部通过 shell 完成（避免无 root 的 File.exists 误判）
     */
    fun syncLsposedLogs(context: Context) {
        val destDir = lsposedLogDir(context)
        if (!destDir.exists() && !destDir.mkdirs()) {
            Log.w(TAG, "无法创建 LSPosed 日志目标目录")
            return
        }

        val destPath = destDir.absolutePath
        val shell = EnhancedShellExecutor.getInstance()

        // 用 root shell 检查源目录是否存在，存在则拷贝
        val result = shell.executeRootCommand(
            "if [ -d /data/adb/lspd/log ]; then" +
            " cp -rf /data/adb/lspd/log/* $destPath" +
            " && chmod -R 644 $destPath/*" +
            " && echo 'SYNC_OK';" +
            " else echo 'SRC_MISSING'; fi"
        )

        if (!result.isSuccess) {
            Log.w(TAG, "LSPosed 日志同步失败: ${result.error}")
            showSyncFailedToast(context)
            return
        }

        when {
            result.output.contains("SYNC_OK") -> {
                Log.i(TAG, "LSPosed 日志同步成功")
            }
            result.output.contains("SRC_MISSING") -> {
                Log.d(TAG, "LSPosed 日志目录不存在，跳过同步")
            }
            else -> {
                Log.w(TAG, "LSPosed 日志同步结果未知: ${result.output}")
            }
        }
    }

    private fun showSyncFailedToast(context: Context) {
        val handler = android.os.Handler(android.os.Looper.getMainLooper())
        handler.post {
            Toast.makeText(
                context,
                context.getString(R.string.lsposed_log_sync_failed),
                Toast.LENGTH_SHORT
            ).show()
        }
    }
}
