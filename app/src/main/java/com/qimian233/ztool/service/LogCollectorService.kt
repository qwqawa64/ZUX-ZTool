package com.qimian233.ztool.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import com.qimian233.ztool.R
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.File
import java.io.FileWriter
import java.io.IOException
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.util.Arrays
import java.util.Comparator
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 应用自身日志采集服务（无需Root权限，通过 PID 过滤 logcat）
 */
class LogCollectorService : Service() {
    private var logcatProcess: Process? = null
    private var currentWriter: BufferedWriter? = null
    private var currentFile: File? = null
    private val isRunning = AtomicBoolean(false)
    private var logcatThread: Thread? = null
    private var isRestartMode = false
    private var isForeground = false
    private var mainHandler: Handler? = null
    private var notificationManager: NotificationManager? = null

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "服务 onCreate() 开始")

        mainHandler = Handler(Looper.getMainLooper())
        notificationManager = getSystemService(NotificationManager::class.java)

        createNotificationChannel()
        startForegroundImmediately()

        Log.d(TAG, "服务 onCreate() 完成")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "服务 onStartCommand() 开始")

        intent?.let {
            isRestartMode = it.getBooleanExtra("is_restart", false)
        }

        if (isRestartMode) {
            Log.d(TAG, "服务重启模式启动")
        }

        if (!isForeground) {
            Log.w(TAG, "前台服务未启动，立即启动")
            startForegroundImmediately()
        }

        if (!isRunning.get()) {
            isRunning.set(true)
            mainHandler?.postDelayed({
                Log.d(TAG, "开始启动日志收集")
                startLogCollection()
            }, 100)
        } else {
            Log.d(TAG, "服务已在运行中")
        }

        Log.d(TAG, "服务 onStartCommand() 完成")
        return START_STICKY
    }

    override fun onDestroy() {
        Log.d(TAG, "服务 onDestroy() 开始")
        stopLogCollection()
        Log.d(TAG, "日志采集服务已停止")
        super.onDestroy()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        Log.d(TAG, "应用任务被移除，但服务继续运行")
        super.onTaskRemoved(rootIntent)
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    private fun startForegroundImmediately() {
        Log.d(TAG, "开始启动前台服务")

        try {
            val notification = createSimpleNotification()
            if (notification != null) {
                startForeground(NOTIFICATION_ID, notification)
                isForeground = true
                Log.d(TAG, "前台服务启动成功")
            } else {
                Log.e(TAG, "创建通知失败，无法启动前台服务")
                startFallbackForeground()
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "启动前台服务权限异常", e)
            startFallbackForeground()
        } catch (e: Exception) {
            Log.e(TAG, "启动前台服务失败", e)
            startFallbackForeground()
        }
    }

    private fun createSimpleNotification(): Notification? {
        return try {
            val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            builder.setContentTitle("日志采集服务")
                .setContentText("日志服务运行中")
                .setSmallIcon(getNotificationIcon())
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .build()
        } catch (e: Exception) {
            Log.e(TAG, "创建简单通知失败", e)
            null
        }
    }

    private fun startFallbackForeground() {
        try {
            val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            val notification = builder
                .setContentTitle("日志服务")
                .setContentText("运行中")
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build()
            startForeground(NOTIFICATION_ID, notification)
            isForeground = true
            Log.d(TAG, "备用前台服务启动成功")
        } catch (e: Exception) {
            Log.e(TAG, "备用前台服务也启动失败", e)
        }
    }

    fun createNotificationChannel() {
        try {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW
            )
            channel.description = "用于采集应用自身运行日志"
            channel.setShowBadge(false)
            channel.lockscreenVisibility = Notification.VISIBILITY_PRIVATE

            val manager = notificationManager
            if (manager != null) {
                manager.createNotificationChannel(channel)
                Log.d(TAG, "通知渠道创建成功")
            } else {
                Log.e(TAG, "NotificationManager 为 null")
            }
        } catch (e: Exception) {
            Log.e(TAG, "创建通知渠道失败", e)
        }
    }

    private fun getNotificationIcon(): Int {
        return try {
            val icon = R.mipmap.ic_launcher
            if (icon == 0) {
                android.R.drawable.ic_dialog_info
            } else {
                icon
            }
        } catch (e: Exception) {
            android.R.drawable.ic_dialog_info
        }
    }

    private fun startLogCollection() {
        Log.d(TAG, "准备启动日志收集")

        val existingThread = logcatThread
        if (existingThread != null && existingThread.isAlive) {
            Log.d(TAG, "停止现有日志收集线程")
            existingThread.interrupt()
            try {
                existingThread.join(1000)
            } catch (e: InterruptedException) {
                Log.w(TAG, "等待旧线程结束被中断", e)
            }
        }

        val collectorThread = Thread(LogCollectorRunnable())
        collectorThread.name = "AppLogCollector-Thread"
        collectorThread.priority = Thread.MIN_PRIORITY
        logcatThread = collectorThread
        collectorThread.start()

        Log.d(TAG, "日志收集线程已启动")
    }

    private fun stopLogCollection() {
        Log.d(TAG, "开始停止日志收集")
        isRunning.set(false)

        val thread = logcatThread
        if (thread != null && thread.isAlive) {
            thread.interrupt()
            try {
                thread.join(2000)
            } catch (e: InterruptedException) {
                Log.w(TAG, "等待日志线程结束被中断", e)
            }
        }

        val process = logcatProcess
        if (process != null) {
            try {
                process.destroy()
            } catch (e: Exception) {
                Log.e(TAG, "停止logcat进程失败", e)
            }
            logcatProcess = null
        }

        closeCurrentWriter()

        if (isForeground) {
            try {
                stopForeground(STOP_FOREGROUND_REMOVE)
                isForeground = false
                Log.d(TAG, "前台服务已停止")
            } catch (e: Exception) {
                Log.e(TAG, "停止前台服务失败", e)
            }
        }

        Log.d(TAG, "日志收集已完全停止")
    }

    private fun buildLogcatCommand(): List<String> {
        val command = ArrayList<String>()
        command.add("logcat")
        command.add("-v")
        command.add("time")
        command.add("--pid=" + android.os.Process.myPid())
        command.add("*:V")
        Log.d(TAG, "Logcat命令: $command")
        return command
    }

    private inner class LogCollectorRunnable : Runnable {
        override fun run() {
            Log.d(TAG, "日志收集线程启动")

            try {
                val logDir = File(filesDir, LOG_DIR)
                val appLogDir = File(logDir, APP_LOG_SUBDIR)
                if (!appLogDir.exists() && !appLogDir.mkdirs()) {
                    Log.e(TAG, "无法创建日志目录: " + appLogDir.absolutePath)
                    return
                }

                val command = buildLogcatCommand()
                Log.d(TAG, "执行logcat命令: $command")

                val processBuilder = ProcessBuilder(command)
                processBuilder.redirectErrorStream(true)
                val process = processBuilder.start()
                logcatProcess = process

                val reader = BufferedReader(InputStreamReader(process.inputStream))

                val logFile = createNewLogFile(appLogDir)
                currentFile = logFile
                currentWriter = BufferedWriter(FileWriter(logFile, true))

                Log.d(TAG, "开始写入日志文件: " + logFile.absolutePath)

                var line: String?
                var lineCount = 0
                var lastStatusLogTime = System.currentTimeMillis()
                var lastFileCheckTime = System.currentTimeMillis()
                val fileCheckInterval = 5000L

                while (isRunning.get() && !Thread.currentThread().isInterrupted()) {
                    try {
                        val currentTime = System.currentTimeMillis()
                        if (currentTime - lastFileCheckTime > fileCheckInterval) {
                            val checkedFile = currentFile
                            if (checkedFile != null && !checkedFile.exists()) {
                                Log.w(TAG, "当前日志文件已被删除，重新创建新文件")

                                closeCurrentWriter()
                                val recreatedFile = createNewLogFile(appLogDir)
                                currentFile = recreatedFile
                                currentWriter = BufferedWriter(FileWriter(recreatedFile, true))

                                Log.d(TAG, "已创建新日志文件: " + recreatedFile.absolutePath)
                            }
                            lastFileCheckTime = currentTime
                        }

                        line = reader.readLine()
                        if (line != null) {
                            val enhancedLine = enhanceLogLine(line)

                            val activeFile = currentFile
                            if (currentWriter == null || (activeFile != null && !activeFile.exists())) {
                                Log.w(TAG, "日志文件状态异常，重新初始化")
                                closeCurrentWriter()
                                val recreatedFile = createNewLogFile(appLogDir)
                                currentFile = recreatedFile
                                currentWriter = BufferedWriter(FileWriter(recreatedFile, true))
                            }

                            try {
                                currentWriter!!.write(enhancedLine)
                                currentWriter!!.newLine()
                                currentWriter!!.flush()
                            } catch (e: IOException) {
                                if (e.message != null &&
                                    (e.message!!.contains("ENOENT") ||
                                            e.message!!.contains("No such file") ||
                                            e.message!!.contains("Stream closed"))
                                ) {
                                    Log.w(TAG, "写入日志失败，文件可能被删除，重新创建: " + e.message)
                                    closeCurrentWriter()
                                    val recreatedFile = createNewLogFile(appLogDir)
                                    currentFile = recreatedFile
                                    currentWriter = BufferedWriter(FileWriter(recreatedFile, true))

                                    currentWriter!!.write(enhancedLine)
                                    currentWriter!!.newLine()
                                    currentWriter!!.flush()
                                } else {
                                    throw e
                                }
                            }

                            lineCount++

                            if (lineCount % 100 == 0 || (currentTime - lastStatusLogTime) > 30000) {
                                Log.d(TAG, "已采集 $lineCount 行日志")
                                lastStatusLogTime = currentTime
                            }

                            val sizedFile = currentFile
                            if (sizedFile != null && sizedFile.length() >= MAX_FILE_SIZE) {
                                Log.d(TAG, "日志文件达到大小限制，开始轮转")
                                rotateLogFile(appLogDir)
                            }
                        } else {
                            Log.d(TAG, "Logcat 流已结束")
                            break
                        }
                    } catch (e: IOException) {
                        if (isRunning.get()) {
                            Log.e(TAG, "读取日志流失败", e)
                        }
                        break
                    }
                }

                Log.d(TAG, "日志采集完成，共采集 $lineCount 行日志")
            } catch (e: IOException) {
                Log.e(TAG, "启动日志采集失败", e)
            } finally {
                Log.d(TAG, "日志采集线程结束")
                closeCurrentWriter()
                logcatProcess?.destroy()
            }
        }

        private fun enhanceLogLine(originalLine: String): String {
            val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault())
                .format(Date())
            return String.format("[%s] %s", timestamp, originalLine)
        }
    }

    private fun createNewLogFile(logDir: File): File {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
            .format(Date())
        val fileName = FILE_PREFIX + timestamp + FILE_SUFFIX
        val newFile = File(logDir, fileName)
        Log.d(TAG, "创建新日志文件: " + newFile.absolutePath)
        return newFile
    }

    private fun rotateLogFile(logDir: File) {
        closeCurrentWriter()

        val newFile = createNewLogFile(logDir)
        currentFile = newFile
        try {
            currentWriter = BufferedWriter(FileWriter(newFile, true))
            Log.d(TAG, "日志文件轮转完成")
        } catch (e: IOException) {
            Log.e(TAG, "创建新日志文件失败", e)
            return
        }

        cleanupOldFiles(logDir)
    }

    private fun cleanupOldFiles(logDir: File) {
        val logFiles = logDir.listFiles { _, name ->
            name.startsWith(FILE_PREFIX) && name.endsWith(FILE_SUFFIX)
        }

        if (logFiles != null && logFiles.size > MAX_FILES) {
            Arrays.sort(logFiles, Comparator.comparingLong { obj: File -> obj.lastModified() })

            val filesToDelete = logFiles.size - MAX_FILES
            for (i in 0 until filesToDelete) {
                if (logFiles[i].delete()) {
                    Log.d(TAG, "删除旧日志文件: " + logFiles[i].name)
                } else {
                    Log.w(TAG, "删除旧日志文件失败: " + logFiles[i].name)
                }
            }
        }
    }

    private fun closeCurrentWriter() {
        val writer = currentWriter
        if (writer != null) {
            try {
                writer.close()
                Log.d(TAG, "日志写入器已关闭")
            } catch (e: IOException) {
                Log.e(TAG, "关闭日志写入器失败", e)
            }
            currentWriter = null
        }
    }

    companion object {
        private const val TAG = "LogCollectorService"
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "log_collector_channel"
        private const val CHANNEL_NAME = "日志采集服务"

        private const val MAX_FILE_SIZE = 1024L * 1024L // 1MB
        private const val MAX_FILES = 20
        private const val LOG_DIR = "Log"
        private const val APP_LOG_SUBDIR = "app"
        private const val FILE_PREFIX = "app_log_"
        private const val FILE_SUFFIX = ".log"
    }
}
