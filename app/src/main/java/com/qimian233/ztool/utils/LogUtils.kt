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
 * Log utilities: manage app logs (export, cleanup, LSPosed sync)
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
     * Clean up app logs: delete everything in Log/app/ if total size exceeds 10MB
     */
    fun cleanupAppLogsIfNeeded(context: Context) {
        val dir = appLogDir(context)
        if (!dir.exists() || !dir.isDirectory()) return

        val files = dir.listFiles() ?: return
        val totalSize = files.sumOf { it.length() }
        val maxSize = 10L * 1024 * 1024 // 10MB

        if (totalSize > maxSize) {
            Log.i(TAG, "app log total size $totalSize exceeds 10MB, auto-cleaning")
            for (file in files) {
                file.delete()
            }
        }
    }

    /**
     * Delete all logs (app + lsposed)
     */
    fun deleteAllLogs(context: Context) {
        val dir = logDir(context)
        if (dir.exists() && dir.isDirectory()) {
            FileUtils.deleteRecursive(dir)
            Log.i(TAG, "all logs deleted")
        }
    }

    /**
     * Sync LSPosed logs from /data/adb/lspd/log to the app's private directory.
     * Requires root; all operations run via shell (to avoid File.exists false
     * negatives without root).
     */
    fun syncLsposedLogs(context: Context) {
        val destDir = lsposedLogDir(context)
        if (!destDir.exists() && !destDir.mkdirs()) {
            Log.w(TAG, "cannot create LSPosed log target directory")
            return
        }

        val destPath = destDir.absolutePath
        val shell = EnhancedShellExecutor.getInstance()

        // Check the source directory via root shell; copy if it exists
        val result = shell.executeRootCommand(
            "if [ -d /data/adb/lspd/log ]; then" +
            " cp -rf /data/adb/lspd/log/* $destPath" +
            " && chmod -R 644 $destPath/*" +
            " && echo 'SYNC_OK';" +
            " else echo 'SRC_MISSING'; fi"
        )

        if (!result.isSuccess) {
            Log.w(TAG, "LSPosed log sync failed: ${result.error}")
            showSyncFailedToast(context)
            return
        }

        when {
            result.output.contains("SYNC_OK") -> {
                Log.i(TAG, "LSPosed log sync succeeded")
            }
            result.output.contains("SRC_MISSING") -> {
                Log.d(TAG, "LSPosed log directory missing, skipping sync")
            }
            else -> {
                Log.w(TAG, "unknown LSPosed log sync result: ${result.output}")
            }
        }
    }

    private fun showSyncFailedToast(context: Context) {
        val handler = android.os.Handler(android.os.Looper.getMainLooper())
        handler.post {
            Toast.makeText(
                context,
                context.getString(R.string.common_lsposed_log_sync_failed),
                Toast.LENGTH_SHORT
            ).show()
        }
    }
}
