package com.qimian233.ztool.service;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 使用Root权限的日志采集服务
 */
public class LogCollectorService extends Service {
    private static final String TAG = "LogCollectorService";

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
    private boolean useRootMode = true; // 使用root模式

    @Override
    public void onCreate() {
        super.onCreate();
        android.util.Log.d(TAG, "Root权限日志采集服务已创建");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (!isRunning.get()) {
            isRunning.set(true);
            startLogCollection();
            android.util.Log.d(TAG, "开始使用Root权限收集Hook模块日志");
        }
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        stopLogCollection();
        android.util.Log.d(TAG, "Root权限日志采集服务已停止");
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    /**
     * 启动日志收集
     */
    private void startLogCollection() {
        logcatThread = new Thread(new LogCollectorRunnable());
        logcatThread.setName("RootLogCollector-Thread");
        logcatThread.start();
    }

    /**
     * 停止日志收集
     */
    private void stopLogCollection() {
        isRunning.set(false);

        // 先中断线程
        if (logcatThread != null && logcatThread.isAlive()) {
            logcatThread.interrupt();
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
    }

    /**
     * 构建logcat命令参数（Root模式）
     */
    private List<String> buildLogcatCommand() {
        List<String> command = new ArrayList<>();

        if (useRootMode) {
            // Root模式：使用su命令执行logcat
            command.add("su");
            command.add("-c");

            // 构建logcat命令字符串
            StringBuilder logcatCmd = new StringBuilder();
            logcatCmd.append("logcat");
            logcatCmd.append(" -v");
            logcatCmd.append(" time"); // 包含时间戳
            logcatCmd.append(" -b");
            logcatCmd.append(" main"); // 主缓冲区
            logcatCmd.append(" -b");
            logcatCmd.append(" system"); // 系统缓冲区
            logcatCmd.append(" -b");
            logcatCmd.append(" events"); // 事件缓冲区

            // 添加多个标签过滤器
            for (String tag : LOG_TAGS) {
                logcatCmd.append(" ").append(tag).append(":I"); // I级别及以上
            }

            logcatCmd.append(" *:S"); // 静默其他所有日志

            command.add(logcatCmd.toString());
            android.util.Log.d(TAG, "Root模式命令: " + command.toString());
        } else {
            // 非Root模式（备用）
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

    /**
     * 检查Root权限
     */
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

            // 检查Root权限
            if (useRootMode && !checkRootPermission()) {
                android.util.Log.w(TAG, "无法获取Root权限，切换到非Root模式");
                useRootMode = false;
            }

            try {
                // 创建日志目录
                File logDir = new File(getFilesDir(), LOG_DIR);
                if (!logDir.exists() && !logDir.mkdirs()) {
                    android.util.Log.e(TAG, "无法创建日志目录: " + logDir.getAbsolutePath());
                    return;
                }

                // 构建并启动logcat进程
                List<String> command = buildLogcatCommand();
                android.util.Log.d(TAG, "执行logcat命令: " + command.toString());

                ProcessBuilder processBuilder = new ProcessBuilder(command);
                processBuilder.redirectErrorStream(true);
                logcatProcess = processBuilder.start();

                BufferedReader reader = new BufferedReader(
                        new InputStreamReader(logcatProcess.getInputStream()));

                // 初始化第一个日志文件
                currentFile = createNewLogFile(logDir);
                currentWriter = new BufferedWriter(new FileWriter(currentFile, true));

                android.util.Log.d(TAG, "开始写入日志文件: " + currentFile.getAbsolutePath());

                String line;
                int lineCount = 0;
                while (isRunning.get() && !Thread.currentThread().isInterrupted()) {
                    try {
                        if (reader.ready() && (line = reader.readLine()) != null) {
                            // 写入日志行，添加额外的模块识别信息
                            String enhancedLine = enhanceLogLine(line);
                            currentWriter.write(enhancedLine);
                            currentWriter.newLine();
                            currentWriter.flush();

                            lineCount++;

                            // 每收集100行日志输出一次状态
                            if (lineCount % 100 == 0) {
                                android.util.Log.d(TAG, "已收集 " + lineCount + " 行日志");
                            }

                            // 检查文件大小，达到阈值时轮转
                            if (currentFile.length() >= MAX_FILE_SIZE) {
                                android.util.Log.d(TAG, "日志文件达到大小限制，开始轮转");
                                rotateLogFile(logDir);
                            }
                        } else {
                            // 短暂休眠避免CPU占用过高
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
            } catch (IOException e) {
                android.util.Log.e(TAG, "启动日志收集失败", e);

                // 如果Root模式失败，尝试非Root模式
                if (useRootMode) {
                    android.util.Log.w(TAG, "Root模式失败，尝试非Root模式");
                    useRootMode = false;
                    if (isRunning.get()) {
                        run(); // 重新运行
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

        /**
         * 增强日志行，添加收集时间戳和Root标记
         */
        private String enhanceLogLine(String originalLine) {
            String timestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault())
                    .format(new Date());
            String mode = useRootMode ? "ROOT" : "NORMAL";
            return String.format("[%s] [%s] %s", timestamp, mode, originalLine);
        }
    }

    /**
     * 创建新的日志文件
     */
    private File createNewLogFile(File logDir) {
        String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
                .format(new Date());
        String mode = useRootMode ? "root" : "normal";
        String fileName = FILE_PREFIX + mode + "_" + timestamp + FILE_SUFFIX;
        File newFile = new File(logDir, fileName);
        android.util.Log.d(TAG, "创建新日志文件: " + newFile.getAbsolutePath());
        return newFile;
    }

    /**
     * 轮转日志文件
     */
    private void rotateLogFile(File logDir) {
        closeCurrentWriter();

        // 创建新文件
        currentFile = createNewLogFile(logDir);
        try {
            currentWriter = new BufferedWriter(new FileWriter(currentFile, true));
            android.util.Log.d(TAG, "日志文件轮转完成");
        } catch (IOException e) {
            android.util.Log.e(TAG, "创建新日志文件失败", e);
            return;
        }

        // 清理旧文件
        cleanupOldFiles(logDir);
    }

    /**
     * 清理过多的旧文件
     */
    private void cleanupOldFiles(File logDir) {
        File[] logFiles = logDir.listFiles(new FilenameFilter() {
            @Override
            public boolean accept(File dir, String name) {
                return name.startsWith(FILE_PREFIX) && name.endsWith(FILE_SUFFIX);
            }
        });

        if (logFiles != null && logFiles.length > MAX_FILES) {
            // 按最后修改时间排序，删除最旧的
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

    /**
     * 关闭当前写入器
     */
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
