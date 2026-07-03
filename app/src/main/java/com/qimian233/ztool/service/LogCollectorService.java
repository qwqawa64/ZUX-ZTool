package com.qimian233.ztool.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.os.Handler;
import android.os.Looper;

import androidx.core.app.NotificationCompat;

import com.qimian233.ztool.R;

import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 应用自身日志采集服务（无需Root权限，通过 PID 过滤 logcat）
 */
public class LogCollectorService extends Service {
    private static final String TAG = "LogCollectorService";
    private static final int NOTIFICATION_ID = 1001;
    private static final String CHANNEL_ID = "log_collector_channel";
    private static final String CHANNEL_NAME = "日志采集服务";

    private static final long MAX_FILE_SIZE = 1024 * 1024; // 1MB
    private static final int MAX_FILES = 20;
    private static final String LOG_DIR = "Log";
    private static final String APP_LOG_SUBDIR = "app";
    private static final String FILE_PREFIX = "app_log_";
    private static final String FILE_SUFFIX = ".log";

    private Process logcatProcess;
    private BufferedWriter currentWriter;
    private File currentFile;
    private final AtomicBoolean isRunning = new AtomicBoolean(false);
    private Thread logcatThread;
    private boolean isRestartMode = false;
    private boolean isForeground = false;
    private Handler mainHandler;
    private NotificationManager notificationManager;

    @Override
    public void onCreate() {
        super.onCreate();
        android.util.Log.d(TAG, "服务 onCreate() 开始");

        mainHandler = new Handler(Looper.getMainLooper());
        notificationManager = getSystemService(NotificationManager.class);

        createNotificationChannel();
        startForegroundImmediately();

        android.util.Log.d(TAG, "服务 onCreate() 完成");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        android.util.Log.d(TAG, "服务 onStartCommand() 开始");

        if (intent != null) {
            isRestartMode = intent.getBooleanExtra("is_restart", false);
        }

        if (isRestartMode) {
            android.util.Log.d(TAG, "服务重启模式启动");
        }

        if (!isForeground) {
            android.util.Log.w(TAG, "前台服务未启动，立即启动");
            startForegroundImmediately();
        }

        if (!isRunning.get()) {
            isRunning.set(true);
            mainHandler.postDelayed(() -> {
                android.util.Log.d(TAG, "开始启动日志收集");
                startLogCollection();
            }, 100);
        } else {
            android.util.Log.d(TAG, "服务已在运行中，更新通知");
            updateNotification("服务运行中");
        }

        android.util.Log.d(TAG, "服务 onStartCommand() 完成");
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        android.util.Log.d(TAG, "服务 onDestroy() 开始");
        stopLogCollection();
        android.util.Log.d(TAG, "日志采集服务已停止");
        super.onDestroy();
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        android.util.Log.d(TAG, "应用任务被移除，但服务继续运行");
        super.onTaskRemoved(rootIntent);
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void startForegroundImmediately() {
        android.util.Log.d(TAG, "开始启动前台服务");

        try {
            Notification notification = createSimpleNotification();
            if (notification != null) {
                startForeground(NOTIFICATION_ID, notification);
                isForeground = true;
                android.util.Log.d(TAG, "前台服务启动成功");
            } else {
                android.util.Log.e(TAG, "创建通知失败，无法启动前台服务");
                startFallbackForeground();
            }
        } catch (SecurityException e) {
            android.util.Log.e(TAG, "启动前台服务权限异常", e);
            startFallbackForeground();
        } catch (Exception e) {
            android.util.Log.e(TAG, "启动前台服务失败", e);
            startFallbackForeground();
        }
    }

    private Notification createSimpleNotification() {
        try {
            NotificationCompat.Builder builder;
            builder = new NotificationCompat.Builder(this, CHANNEL_ID);
            return builder.setContentTitle("日志采集服务")
                    .setContentText("服务启动中...")
                    .setSmallIcon(getNotificationIcon())
                    .setPriority(NotificationCompat.PRIORITY_LOW)
                    .setOngoing(true)
                    .setOnlyAlertOnce(true)
                    .build();
        } catch (Exception e) {
            android.util.Log.e(TAG, "创建简单通知失败", e);
            return null;
        }
    }

    private void startFallbackForeground() {
        try {
            NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID);
            Notification notification = builder
                    .setContentTitle("日志服务")
                    .setContentText("运行中")
                    .setSmallIcon(android.R.drawable.ic_dialog_info)
                    .setPriority(NotificationCompat.PRIORITY_LOW)
                    .build();
            startForeground(NOTIFICATION_ID, notification);
            isForeground = true;
            android.util.Log.d(TAG, "备用前台服务启动成功");
        } catch (Exception e) {
            android.util.Log.e(TAG, "备用前台服务也启动失败", e);
        }
    }

    public void createNotificationChannel() {
        try {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("用于采集应用自身运行日志");
            channel.setShowBadge(false);
            channel.setLockscreenVisibility(Notification.VISIBILITY_PRIVATE);

            if (notificationManager != null) {
                notificationManager.createNotificationChannel(channel);
                android.util.Log.d(TAG, "通知渠道创建成功");
            } else {
                android.util.Log.e(TAG, "NotificationManager 为 null");
            }
        } catch (Exception e) {
            android.util.Log.e(TAG, "创建通知渠道失败", e);
        }
    }

    private void updateNotification(String contentText) {
        if (isForeground) {
            mainHandler.post(() -> {
                try {
                    NotificationCompat.Builder builder;
                    builder = new NotificationCompat.Builder(this, CHANNEL_ID);

                    Notification notification = builder
                            .setContentTitle("日志采集服务")
                            .setContentText(contentText)
                            .setSmallIcon(getNotificationIcon())
                            .setPriority(NotificationCompat.PRIORITY_LOW)
                            .setOngoing(true)
                            .setOnlyAlertOnce(true)
                            .build();

                    if (notificationManager != null) {
                        notificationManager.notify(NOTIFICATION_ID, notification);
                    }
                } catch (Exception e) {
                    android.util.Log.e(TAG, "更新通知失败", e);
                }
            });
        }
    }

    private int getNotificationIcon() {
        try {
            int icon = R.mipmap.ic_launcher;
            if (icon == 0) {
                icon = android.R.drawable.ic_dialog_info;
            }
            return icon;
        } catch (Exception e) {
            return android.R.drawable.ic_dialog_info;
        }
    }

    private void startLogCollection() {
        android.util.Log.d(TAG, "准备启动日志收集");

        if (logcatThread != null && logcatThread.isAlive()) {
            android.util.Log.d(TAG, "停止现有日志收集线程");
            logcatThread.interrupt();
            try {
                logcatThread.join(1000);
            } catch (InterruptedException e) {
                android.util.Log.w(TAG, "等待旧线程结束被中断", e);
            }
        }

        updateNotification("正在初始化日志收集...");

        logcatThread = new Thread(new LogCollectorRunnable());
        logcatThread.setName("AppLogCollector-Thread");
        logcatThread.setPriority(Thread.MIN_PRIORITY);
        logcatThread.start();

        android.util.Log.d(TAG, "日志收集线程已启动");
    }

    private void stopLogCollection() {
        android.util.Log.d(TAG, "开始停止日志收集");
        isRunning.set(false);

        updateNotification("正在停止服务...");

        if (logcatThread != null && logcatThread.isAlive()) {
            logcatThread.interrupt();
            try {
                logcatThread.join(2000);
            } catch (InterruptedException e) {
                android.util.Log.w(TAG, "等待日志线程结束被中断", e);
            }
        }

        if (logcatProcess != null) {
            try {
                logcatProcess.destroy();
            } catch (Exception e) {
                android.util.Log.e(TAG, "停止logcat进程失败", e);
            }
            logcatProcess = null;
        }

        closeCurrentWriter();

        if (isForeground) {
            try {
                stopForeground(true);
                isForeground = false;
                android.util.Log.d(TAG, "前台服务已停止");
            } catch (Exception e) {
                android.util.Log.e(TAG, "停止前台服务失败", e);
            }
        }

        android.util.Log.d(TAG, "日志收集已完全停止");
    }

    private List<String> buildLogcatCommand() {
        List<String> command = new ArrayList<>();
        command.add("logcat");
        command.add("-v");
        command.add("time");
        command.add("--pid=" + android.os.Process.myPid());
        command.add("*:V");
        android.util.Log.d(TAG, "Logcat命令: " + command);
        return command;
    }

    private class LogCollectorRunnable implements Runnable {
        @Override
        public void run() {
            android.util.Log.d(TAG, "日志收集线程启动");
            mainHandler.post(() -> updateNotification("正在采集应用日志..."));

            try {
                File logDir = new File(getFilesDir(), LOG_DIR);
                File appLogDir = new File(logDir, APP_LOG_SUBDIR);
                if (!appLogDir.exists() && !appLogDir.mkdirs()) {
                    android.util.Log.e(TAG, "无法创建日志目录: " + appLogDir.getAbsolutePath());
                    mainHandler.post(() -> updateNotification("创建日志目录失败"));
                    return;
                }

                List<String> command = buildLogcatCommand();
                android.util.Log.d(TAG, "执行logcat命令: " + command);

                mainHandler.post(() -> updateNotification("正在启动logcat进程..."));

                ProcessBuilder processBuilder = new ProcessBuilder(command);
                processBuilder.redirectErrorStream(true);
                logcatProcess = processBuilder.start();

                BufferedReader reader = new BufferedReader(
                        new InputStreamReader(logcatProcess.getInputStream()));

                currentFile = createNewLogFile(appLogDir);
                currentWriter = new BufferedWriter(new FileWriter(currentFile, true));

                android.util.Log.d(TAG, "开始写入日志文件: " + currentFile.getAbsolutePath());
                mainHandler.post(() -> updateNotification("正在采集日志..."));

                String line;
                int lineCount = 0;
                long lastStatusLogTime = System.currentTimeMillis();
                long lastNotificationUpdate = System.currentTimeMillis();
                long lastFileCheckTime = System.currentTimeMillis();
                final long FILE_CHECK_INTERVAL = 5000;

                while (isRunning.get() && !Thread.currentThread().isInterrupted()) {
                    try {
                        long currentTime = System.currentTimeMillis();
                        if (currentTime - lastFileCheckTime > FILE_CHECK_INTERVAL) {
                            if (currentFile != null && !currentFile.exists()) {
                                android.util.Log.w(TAG, "当前日志文件已被删除，重新创建新文件");
                                mainHandler.post(() -> updateNotification("检测到文件被删除，重新创建..."));

                                closeCurrentWriter();
                                currentFile = createNewLogFile(appLogDir);
                                currentWriter = new BufferedWriter(new FileWriter(currentFile, true));

                                android.util.Log.d(TAG, "已创建新日志文件: " + currentFile.getAbsolutePath());
                                mainHandler.post(() -> updateNotification("已重新创建日志文件"));
                            }
                            lastFileCheckTime = currentTime;
                        }

                        line = reader.readLine();
                        if (line != null) {
                            String enhancedLine = enhanceLogLine(line);

                            if (currentWriter == null || (currentFile != null && !currentFile.exists())) {
                                android.util.Log.w(TAG, "日志文件状态异常，重新初始化");
                                closeCurrentWriter();
                                currentFile = createNewLogFile(appLogDir);
                                currentWriter = new BufferedWriter(new FileWriter(currentFile, true));
                            }

                            try {
                                currentWriter.write(enhancedLine);
                                currentWriter.newLine();
                                currentWriter.flush();
                            } catch (IOException e) {
                                if (e.getMessage() != null &&
                                        (e.getMessage().contains("ENOENT") ||
                                                e.getMessage().contains("No such file") ||
                                                e.getMessage().contains("Stream closed"))) {

                                    android.util.Log.w(TAG, "写入日志失败，文件可能被删除，重新创建: " + e.getMessage());
                                    closeCurrentWriter();
                                    currentFile = createNewLogFile(appLogDir);
                                    currentWriter = new BufferedWriter(new FileWriter(currentFile, true));

                                    currentWriter.write(enhancedLine);
                                    currentWriter.newLine();
                                    currentWriter.flush();
                                } else {
                                    throw e;
                                }
                            }

                            lineCount++;

                            if (lineCount % 100 == 0 || (currentTime - lastStatusLogTime) > 30000) {
                                android.util.Log.d(TAG, "已采集 " + lineCount + " 行日志");
                                lastStatusLogTime = currentTime;
                            }

                            if (currentTime - lastNotificationUpdate > 30000) {
                                int finalLineCount = lineCount;
                                mainHandler.post(() ->
                                        updateNotification("已采集 " + finalLineCount + " 行日志"));
                                lastNotificationUpdate = currentTime;
                            }

                            if (currentFile.length() >= MAX_FILE_SIZE) {
                                android.util.Log.d(TAG, "日志文件达到大小限制，开始轮转");
                                mainHandler.post(() -> updateNotification("正在轮转日志文件..."));
                                rotateLogFile(appLogDir);
                                mainHandler.post(() -> updateNotification("正在采集日志..."));
                            }
                        } else {
                            android.util.Log.d(TAG, "Logcat 流已结束");
                            break;
                        }
                    } catch (IOException e) {
                        if (isRunning.get()) {
                            android.util.Log.e(TAG, "读取日志流失败", e);
                        }
                        break;
                    }
                }

                android.util.Log.d(TAG, "日志采集完成，共采集 " + lineCount + " 行日志");
                mainHandler.post(() -> updateNotification("日志采集已完成"));
            } catch (IOException e) {
                android.util.Log.e(TAG, "启动日志采集失败", e);
                mainHandler.post(() -> updateNotification("日志采集启动失败"));
            } finally {
                android.util.Log.d(TAG, "日志采集线程结束");
                closeCurrentWriter();
                if (logcatProcess != null) {
                    logcatProcess.destroy();
                }
            }
        }

        private String enhanceLogLine(String originalLine) {
            String timestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault())
                    .format(new Date());
            return String.format("[%s] %s", timestamp, originalLine);
        }
    }

    private File createNewLogFile(File logDir) {
        String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
                .format(new Date());
        String fileName = FILE_PREFIX + timestamp + FILE_SUFFIX;
        File newFile = new File(logDir, fileName);
        android.util.Log.d(TAG, "创建新日志文件: " + newFile.getAbsolutePath());
        return newFile;
    }

    private void rotateLogFile(File logDir) {
        closeCurrentWriter();

        currentFile = createNewLogFile(logDir);
        try {
            currentWriter = new BufferedWriter(new FileWriter(currentFile, true));
            android.util.Log.d(TAG, "日志文件轮转完成");
        } catch (IOException e) {
            android.util.Log.e(TAG, "创建新日志文件失败", e);
            return;
        }

        cleanupOldFiles(logDir);
    }

    private void cleanupOldFiles(File logDir) {
        File[] logFiles = logDir.listFiles((dir, name) -> name.startsWith(FILE_PREFIX) && name.endsWith(FILE_SUFFIX));

        if (logFiles != null && logFiles.length > MAX_FILES) {
            Arrays.sort(logFiles, Comparator.comparingLong(File::lastModified));

            int filesToDelete = logFiles.length - MAX_FILES;
            for (int i = 0; i < filesToDelete; i++) {
                if (logFiles[i].delete()) {
                    android.util.Log.d(TAG, "删除旧日志文件: " + logFiles[i].getName());
                } else {
                    android.util.Log.w(TAG, "删除旧日志文件失败: " + logFiles[i].getName());
                }
            }
        }
    }

    private void closeCurrentWriter() {
        if (currentWriter != null) {
            try {
                currentWriter.close();
                android.util.Log.d(TAG, "日志写入器已关闭");
            } catch (IOException e) {
                android.util.Log.e(TAG, "关闭日志写入器失败", e);
            }
            currentWriter = null;
        }
    }
}
