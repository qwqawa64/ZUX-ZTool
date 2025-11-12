package com.qimian233.ztool.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;
import android.os.Handler;
import android.os.Looper;
import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 使用Root权限的日志采集服务（完全修复前台服务超时问题）
 */
public class LogCollectorService extends Service {
    private static final String TAG = "LogCollectorService";
    private static final int NOTIFICATION_ID = 1001;
    private static final String CHANNEL_ID = "log_collector_channel";
    private static final String CHANNEL_NAME = "日志采集服务";

    // 配置参数
    private static final String[] LOG_TAGS = {
            "ZToolXposedModule",           // BaseHookModule的默认标签
            "ZTool"                  // 应用自身日志
    };
    private static final long MAX_FILE_SIZE = 1024 * 1024; // 1MB分片大小
    private static final int MAX_FILES = 20;
    private static final String LOG_DIR = "Log";
    private static final String FILE_PREFIX = "hook_log_";
    private static final String FILE_SUFFIX = ".txt";

    private Process logcatProcess;
    private BufferedWriter currentWriter;
    private File currentFile;
    private final AtomicBoolean isRunning = new AtomicBoolean(false);
    private Thread logcatThread;
    private boolean useRootMode = true;
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

        // 第一步：立即创建通知渠道
        createNotificationChannel();

        // 第二步：立即启动前台服务（必须在5秒内完成）
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

        // 确保前台服务已经启动
        if (!isForeground) {
            android.util.Log.w(TAG, "前台服务未启动，立即启动");
            startForegroundImmediately();
        }

        if (!isRunning.get()) {
            isRunning.set(true);

            // 延迟启动日志收集，确保前台服务已完全建立
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
        android.util.Log.d(TAG, "Root权限日志采集服务已停止");
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

    /**
     * 立即启动前台服务（避免超时的核心方法）
     */
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
                // 即使通知创建失败，也尝试启动一个基本的通知
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

    /**
     * 创建简单的通知（避免复杂操作导致超时）
     */
    private Notification createSimpleNotification() {
        try {
            Notification.Builder builder;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                builder = new Notification.Builder(this, CHANNEL_ID);
            } else {
                builder = new Notification.Builder(this);
            }

            return builder.setContentTitle("日志采集服务")
                    .setContentText("服务启动中...")
                    .setSmallIcon(getNotificationIcon())
                    .setPriority(Notification.PRIORITY_LOW)
                    .setOngoing(true)
                    .setOnlyAlertOnce(true)
                    .build();
        } catch (Exception e) {
            android.util.Log.e(TAG, "创建简单通知失败", e);
            return null;
        }
    }

    /**
     * 备用前台服务启动方法
     */
    private void startFallbackForeground() {
        try {
            // 使用最基本的通知设置
            Notification.Builder builder = new Notification.Builder(this);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                builder.setChannelId(CHANNEL_ID);
            }

            Notification notification = builder
                    .setContentTitle("日志服务")
                    .setContentText("运行中")
                    .setSmallIcon(android.R.drawable.ic_dialog_info)
                    .setPriority(Notification.PRIORITY_LOW)
                    .build();

            startForeground(NOTIFICATION_ID, notification);
            isForeground = true;
            android.util.Log.d(TAG, "备用前台服务启动成功");
        } catch (Exception e) {
            android.util.Log.e(TAG, "备用前台服务也启动失败", e);
        }
    }

    /**
     * 创建通知渠道
     */
    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                NotificationChannel channel = new NotificationChannel(
                        CHANNEL_ID,
                        CHANNEL_NAME,
                        NotificationManager.IMPORTANCE_LOW
                );
                channel.setDescription("用于收集Hook模块的运行日志");
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
    }

    /**
     * 更新通知内容
     */
    private void updateNotification(String contentText) {
        if (isForeground) {
            mainHandler.post(() -> {
                try {
                    Notification.Builder builder;
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        builder = new Notification.Builder(this, CHANNEL_ID);
                    } else {
                        builder = new Notification.Builder(this);
                    }

                    Notification notification = builder
                            .setContentTitle("日志采集服务")
                            .setContentText(contentText)
                            .setSmallIcon(getNotificationIcon())
                            .setPriority(Notification.PRIORITY_LOW)
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

    /**
     * 获取通知图标
     */
    private int getNotificationIcon() {
        // 使用应用图标或默认系统图标
        try {
            int icon = getResources().getIdentifier("ic_launcher", "mipmap", getPackageName());
            if (icon == 0) {
                icon = android.R.drawable.ic_dialog_info;
            }
            return icon;
        } catch (Exception e) {
            return android.R.drawable.ic_dialog_info;
        }
    }

    /**
     * 启动日志收集
     */
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

        // 更新通知状态
        updateNotification("正在初始化日志收集...");

        logcatThread = new Thread(new LogCollectorRunnable());
        logcatThread.setName("RootLogCollector-Thread");
        logcatThread.setPriority(Thread.MIN_PRIORITY);
        logcatThread.start();

        android.util.Log.d(TAG, "日志收集线程已启动");
    }

    /**
     * 停止日志收集
     */
    private void stopLogCollection() {
        android.util.Log.d(TAG, "开始停止日志收集");
        isRunning.set(false);

        // 更新通知状态
        updateNotification("正在停止服务...");

        // 先中断线程
        if (logcatThread != null && logcatThread.isAlive()) {
            logcatThread.interrupt();
            try {
                logcatThread.join(2000);
            } catch (InterruptedException e) {
                android.util.Log.w(TAG, "等待日志线程结束被中断", e);
            }
        }

        // 然后停止进程
        if (logcatProcess != null) {
            try {
                logcatProcess.destroy();
            } catch (Exception e) {
                android.util.Log.e(TAG, "停止logcat进程失败", e);
            }
            logcatProcess = null;
        }

        closeCurrentWriter();

        // 停止前台服务
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

    // 其余方法保持不变（buildLogcatCommand, checkRootPermission, 文件操作等）
    // 为了简洁，这里省略重复代码，实际使用时需要保留

    private List<String> buildLogcatCommand() {
        List<String> command = new ArrayList<>();

        if (useRootMode) {
            command.add("su");
            command.add("-c");

            StringBuilder logcatCmd = new StringBuilder();
            logcatCmd.append("logcat");
            logcatCmd.append(" -v");
            logcatCmd.append(" time");
            logcatCmd.append(" -b");
            logcatCmd.append(" main");
            logcatCmd.append(" -b");
            logcatCmd.append(" system");
            logcatCmd.append(" -b");
            logcatCmd.append(" events");

            for (String tag : LOG_TAGS) {
                logcatCmd.append(" ").append(tag).append(":I");
            }

            logcatCmd.append(" *:S");

            command.add(logcatCmd.toString());
            android.util.Log.d(TAG, "Root模式命令: " + command.toString());
        } else {
            command.add("logcat");
            command.add("-v");
            command.add("time");
            command.add("-b");
            command.add("main");
            command.add("-b");
            command.add("system");

            for (String tag : LOG_TAGS) {
                command.add(tag + ":I");
            }

            command.add("*:S");
            android.util.Log.d(TAG, "非Root模式命令: " + command.toString());
        }

        return command;
    }

    private boolean checkRootPermission() {
        try {
            Process process = Runtime.getRuntime().exec("su -c id");
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line = reader.readLine();
            process.waitFor();

            if (line != null && line.contains("uid=0")) {
                android.util.Log.d(TAG, "Root权限检查通过");
                return true;
            } else {
                android.util.Log.w(TAG, "Root权限检查失败");
                return false;
            }
        } catch (Exception e) {
            android.util.Log.e(TAG, "Root权限检查异常", e);
            return false;
        }
    }

    /**
     * 日志收集主循环
     */
    private class LogCollectorRunnable implements Runnable {
        @Override
        public void run() {
            android.util.Log.d(TAG, "Root日志收集线程启动");

            // 更新通知状态
            mainHandler.post(() -> updateNotification("正在检查Root权限..."));

            // 检查Root权限
            if (useRootMode && !checkRootPermission()) {
                android.util.Log.w(TAG, "无法获取Root权限，切换到非Root模式");
                useRootMode = false;
                mainHandler.post(() -> updateNotification("使用普通模式收集日志..."));
            } else {
                mainHandler.post(() -> updateNotification(
                        useRootMode ? "使用Root模式收集日志..." : "使用普通模式收集日志..."));
            }

            try {
                // 创建日志目录
                File logDir = new File(getFilesDir(), LOG_DIR);
                if (!logDir.exists() && !logDir.mkdirs()) {
                    android.util.Log.e(TAG, "无法创建日志目录: " + logDir.getAbsolutePath());
                    mainHandler.post(() -> updateNotification("创建日志目录失败"));
                    return;
                }

                // 构建并启动logcat进程
                List<String> command = buildLogcatCommand();
                android.util.Log.d(TAG, "执行logcat命令: " + command.toString());

                mainHandler.post(() -> updateNotification("正在启动logcat进程..."));

                ProcessBuilder processBuilder = new ProcessBuilder(command);
                processBuilder.redirectErrorStream(true);
                logcatProcess = processBuilder.start();

                BufferedReader reader = new BufferedReader(
                        new InputStreamReader(logcatProcess.getInputStream()));

                // 初始化第一个日志文件
                currentFile = createNewLogFile(logDir);
                currentWriter = new BufferedWriter(new FileWriter(currentFile, true));

                android.util.Log.d(TAG, "开始写入日志文件: " + currentFile.getAbsolutePath());

                mainHandler.post(() -> updateNotification("正在收集日志..."));

                String line;
                int lineCount = 0;
                long lastStatusLogTime = System.currentTimeMillis();
                long lastNotificationUpdate = System.currentTimeMillis();

                while (isRunning.get() && !Thread.currentThread().isInterrupted()) {
                    try {
                        if (reader.ready() && (line = reader.readLine()) != null) {
                            String enhancedLine = enhanceLogLine(line);
                            currentWriter.write(enhancedLine);
                            currentWriter.newLine();
                            currentWriter.flush();

                            lineCount++;

                            long currentTime = System.currentTimeMillis();
                            if (lineCount % 100 == 0 || (currentTime - lastStatusLogTime) > 30000) {
                                android.util.Log.d(TAG, "已收集 " + lineCount + " 行日志");
                                lastStatusLogTime = currentTime;
                            }

                            if (currentTime - lastNotificationUpdate > 30000) {
                                int finalLineCount = lineCount;
                                mainHandler.post(() ->
                                        updateNotification("已收集 " + finalLineCount + " 行日志"));
                                lastNotificationUpdate = currentTime;
                            }

                            if (currentFile.length() >= MAX_FILE_SIZE) {
                                android.util.Log.d(TAG, "日志文件达到大小限制，开始轮转");
                                mainHandler.post(() -> updateNotification("正在轮转日志文件..."));
                                rotateLogFile(logDir);
                                mainHandler.post(() -> updateNotification("正在收集日志..."));
                            }
                        } else {
                            Thread.sleep(100);
                        }
                    } catch (InterruptedException e) {
                        android.util.Log.d(TAG, "日志收集线程被中断");
                        Thread.currentThread().interrupt();
                        break;
                    } catch (IOException e) {
                        if (isRunning.get()) {
                            android.util.Log.e(TAG, "读取日志流失败", e);
                        }
                        break;
                    }
                }

                android.util.Log.d(TAG, "日志收集完成，共收集 " + lineCount + " 行日志");
                mainHandler.post(() -> updateNotification("日志收集已完成"));
            } catch (IOException e) {
                android.util.Log.e(TAG, "启动日志收集失败", e);
                mainHandler.post(() -> updateNotification("日志收集启动失败"));

                if (useRootMode) {
                    android.util.Log.w(TAG, "Root模式失败，尝试非Root模式");
                    useRootMode = false;
                    mainHandler.post(() -> updateNotification("切换到普通模式..."));

                    if (isRunning.get()) {
                        try {
                            Thread.sleep(2000);
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                        }
                        if (isRunning.get()) {
                            run();
                        }
                    }
                }
            } finally {
                android.util.Log.d(TAG, "Root日志收集线程结束");
                closeCurrentWriter();
                if (logcatProcess != null) {
                    logcatProcess.destroy();
                }
            }
        }

        private String enhanceLogLine(String originalLine) {
            String timestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault())
                    .format(new Date());
            String mode = useRootMode ? "ROOT" : "NORMAL";
            return String.format("[%s] [%s] %s", timestamp, mode, originalLine);
        }
    }

    private File createNewLogFile(File logDir) {
        String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
                .format(new Date());
        String mode = useRootMode ? "root" : "normal";
        String fileName = FILE_PREFIX + mode + "_" + timestamp + FILE_SUFFIX;
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
        File[] logFiles = logDir.listFiles(new FilenameFilter() {
            @Override
            public boolean accept(File dir, String name) {
                return name.startsWith(FILE_PREFIX) && name.endsWith(FILE_SUFFIX);
            }
        });

        if (logFiles != null && logFiles.length > MAX_FILES) {
            Arrays.sort(logFiles, new Comparator<File>() {
                @Override
                public int compare(File f1, File f2) {
                    return Long.compare(f1.lastModified(), f2.lastModified());
                }
            });

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
