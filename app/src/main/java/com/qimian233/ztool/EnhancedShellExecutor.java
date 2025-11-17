package com.qimian233.ztool;

import android.util.Log;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Shell命令执行工具类
 */
public class EnhancedShellExecutor {
    private static final String TAG = "EnhancedShellExecutor";

    // 单例实例
    private static volatile EnhancedShellExecutor instance;

    // 线程池用于执行Shell命令
    private final ExecutorService executorService;

    // 命令执行计数器（用于限流）
    private final AtomicInteger commandCounter = new AtomicInteger(0);
    private final ReentrantLock commandLock = new ReentrantLock();

    // 命令结果缓存（避免重复执行相同命令）
    private final ConcurrentHashMap<String, CachedResult> commandCache = new ConcurrentHashMap<>();

    // 配置参数
    private static final int DEFAULT_TIMEOUT = 8;
    private static final int MAX_CONCURRENT_COMMANDS = 2;
    private static final long CACHE_DURATION = 30000; // 30秒缓存
    private static final int RETRY_COUNT = 1;

    // 上次执行时间（用于限流）
    private volatile long lastCommandTime = 0;
    private static final long MIN_COMMAND_INTERVAL = 50; // 最小命令间隔100ms

    private EnhancedShellExecutor() {
        // 创建固定大小线程池，限制并发数
        executorService = Executors.newFixedThreadPool(MAX_CONCURRENT_COMMANDS, r -> {
            Thread t = new Thread(r, "ShellExecutor-" + System.currentTimeMillis());
            t.setPriority(Thread.MIN_PRIORITY + 1); // 降低线程优先级
            return t;
        });
        Log.i(TAG, "EnhancedShellExecutor初始化完成");
    }

    public static EnhancedShellExecutor getInstance() {
        if (instance == null) {
            synchronized (EnhancedShellExecutor.class) {
                if (instance == null) {
                    instance = new EnhancedShellExecutor();
                }
            }
        }
        return instance;
    }

    /**
     * Shell命令执行结果
     */
    public static class ShellResult {
        public final boolean success;
        public final String output;
        public final String error;
        public final int exitCode;
        public final Exception exception;
        public final long executionTime;

        public ShellResult(boolean success, String output, String error,
                           int exitCode, Exception exception, long executionTime) {
            this.success = success;
            this.output = output;
            this.error = error;
            this.exitCode = exitCode;
            this.exception = exception;
            this.executionTime = executionTime;
        }

        public boolean isSuccess() {
            return success && exitCode == 0;
        }
    }

    /**
     * 缓存结果
     */
    private static class CachedResult {
        final ShellResult result;
        final long timestamp;

        CachedResult(ShellResult result) {
            this.result = result;
            this.timestamp = System.currentTimeMillis();
        }

        boolean isExpired() {
            return System.currentTimeMillis() - timestamp > CACHE_DURATION;
        }
    }

    /**
     * 执行Shell命令（需要root权限），带缓存
     */
    public ShellResult executeRootCommand(String command) {
        return executeRootCommand(command, DEFAULT_TIMEOUT, true);
    }

    /**
     * 执行Shell命令（需要root权限），可指定超时时间，带缓存
     */
    public ShellResult executeRootCommand(String command, int timeoutSeconds) {
        return executeRootCommand(command, timeoutSeconds, true);
    }

    /**
     * 执行Shell命令（需要root权限），内部方法
     */
    private ShellResult executeRootCommand(String command, int timeoutSeconds, boolean useCache) {
        String cacheKey = "root_" + command;

        // 检查缓存
        if (useCache) {
            CachedResult cached = commandCache.get(cacheKey);
            if (cached != null && !cached.isExpired()) {
                Log.d(TAG, "使用缓存结果: " + command);
                return cached.result;
            }
        }

        ShellResult result = executeCommandInternal("su -c " + command, timeoutSeconds, true);

        // 缓存成功的结果
        if (useCache && result.isSuccess()) {
            commandCache.put(cacheKey, new CachedResult(result));
        }

        return result;
    }

    /**
     * 执行Shell命令（普通权限）
     */
    public ShellResult executeCommand(String command) {
        return executeCommandInternal(command, DEFAULT_TIMEOUT, false);
    }

    /**
     * 执行Shell命令的核心方法
     */
    private ShellResult executeCommandInternal(String command, int timeoutSeconds, boolean isRootCommand) {
        // 限流检查
        if (!acquireCommandSlot()) {
            Log.w(TAG, "命令执行被限流: " + command);
            return new ShellResult(false, "", "系统繁忙，请稍后重试", -1,
                    new RuntimeException("Command rate limited"), 0);
        }

        long startTime = System.currentTimeMillis();
        Log.d(TAG, "执行命令: " + (isRootCommand ? "[ROOT] " : "") + command);

        // 重试机制
        for (int attempt = 0; attempt <= RETRY_COUNT; attempt++) {
            if (attempt > 0) {
                Log.d(TAG, "命令重试，第 " + (attempt + 1) + " 次: " + command);
                try {
                    Thread.sleep(200); // 重试前短暂等待
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }

            ShellResult result = executeSingleCommand(command, timeoutSeconds, isRootCommand);

            // 如果成功或非超时错误，直接返回
            if (result.isSuccess() || !(result.exception instanceof TimeoutException)) {
                long executionTime = System.currentTimeMillis() - startTime;
                Log.d(TAG, String.format("命令执行完成 - 耗时: %dms, 结果: %s",
                        executionTime, result.isSuccess() ? "成功" : "失败"));
                releaseCommandSlot();
                return new ShellResult(result.success, result.output, result.error,
                        result.exitCode, result.exception, executionTime);
            }
        }

        releaseCommandSlot();
        return new ShellResult(false, "", "命令执行超时", -1,
                new TimeoutException("Command timeout after retries"),
                System.currentTimeMillis() - startTime);
    }

    /**
     * 获取命令执行槽位（限流）
     */
    private boolean acquireCommandSlot() {
        commandLock.lock();
        try {
            // 检查并发数限制
            if (commandCounter.get() >= MAX_CONCURRENT_COMMANDS) {
                return false;
            }

            // 检查执行间隔
            long currentTime = System.currentTimeMillis();
            if (currentTime - lastCommandTime < MIN_COMMAND_INTERVAL) {
                try {
                    Thread.sleep(MIN_COMMAND_INTERVAL - (currentTime - lastCommandTime));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return false;
                }
            }

            commandCounter.incrementAndGet();
            lastCommandTime = System.currentTimeMillis();
            return true;

        } finally {
            commandLock.unlock();
        }
    }

    /**
     * 释放命令执行槽位
     */
    private void releaseCommandSlot() {
        commandLock.lock();
        try {
            commandCounter.decrementAndGet();
        } finally {
            commandLock.unlock();
        }
    }

    /**
     * 执行单个命令
     */
    private ShellResult executeSingleCommand(String command, int timeoutSeconds, boolean isRootCommand) {
        Process process = null;
        BufferedReader outputReader = null;
        BufferedReader errorReader = null;

        try {
            // 创建Callable任务用于超时控制
            Callable<ShellResult> task = () -> {
                Process localProcess = null;
                BufferedReader localOutputReader = null;
                BufferedReader localErrorReader = null;

                try {
                    // 使用ProcessBuilder获得更好的控制
                    ProcessBuilder processBuilder = new ProcessBuilder();
                    if (command.contains(" ")) {
                        processBuilder.command(command.split(" "));
                    } else {
                        processBuilder.command(command);
                    }

                    // 重定向错误流到标准输出
                    processBuilder.redirectErrorStream(true);
                    localProcess = processBuilder.start();

                    // 读取输出
                    localOutputReader = new BufferedReader(
                            new InputStreamReader(localProcess.getInputStream()));
                    StringBuilder output = new StringBuilder();
                    String line;
                    while ((line = localOutputReader.readLine()) != null) {
                        output.append(line).append("\n");
                    }

                    // 等待进程结束并获取退出码
                    int exitCode = localProcess.waitFor();

                    String outputStr = output.toString().trim();

                    Log.v(TAG, "命令执行完成 - 退出码: " + exitCode +
                            ", 输出: " + (outputStr.length() > 100 ?
                            outputStr.substring(0, 100) + "..." : outputStr));

                    return new ShellResult(true, outputStr, "", exitCode, null, 0);

                } catch (Exception e) {
                    Log.e(TAG, "命令执行异常: " + e.getMessage(), e);
                    return new ShellResult(false, "", e.getMessage(), -1, e, 0);
                } finally {
                    // 清理资源
                    safeClose(localOutputReader);
                    safeClose(localErrorReader);
                    safeDestroy(localProcess);
                }
            };

            // 提交任务并设置超时
            Future<ShellResult> future = executorService.submit(task);
            return future.get(timeoutSeconds, TimeUnit.SECONDS);

        } catch (TimeoutException e) {
            Log.w(TAG, "命令执行超时: " + command);
            safeDestroy(process);
            return new ShellResult(false, "", "命令执行超时", -1, e, 0);
        } catch (Exception e) {
            Log.e(TAG, "命令执行失败: " + e.getMessage(), e);
            return new ShellResult(false, "", e.getMessage(), -1, e, 0);
        } finally {
            // 最终清理
            safeClose(outputReader);
            safeClose(errorReader);
            safeDestroy(process);
        }
    }

    /**
     * 安全关闭BufferedReader
     */
    private void safeClose(BufferedReader reader) {
        if (reader != null) {
            try {
                reader.close();
            } catch (IOException e) {
                Log.w(TAG, "关闭BufferedReader失败: " + e.getMessage());
            }
        }
    }

    /**
     * 安全销毁Process - 增强版本
     */
    private void safeDestroy(Process process) {
        if (process != null) {
            try {
                // 先尝试获取输入流并关闭（避免阻塞）
                try {
                    process.getInputStream().close();
                } catch (Exception e) {
                    // 忽略
                }
                try {
                    process.getErrorStream().close();
                } catch (Exception e) {
                    // 忽略
                }
                try {
                    process.getOutputStream().close();
                } catch (Exception e) {
                    // 忽略
                }

                // 尝试正常终止
                process.destroy();

                // 等待进程退出
                boolean terminated = process.waitFor(1, TimeUnit.SECONDS);
                if (!terminated) {
                    // 强制终止
                    process.destroyForcibly();
                    Log.w(TAG, "进程被强制终止");

                    // 再次等待
                    terminated = process.waitFor(1, TimeUnit.SECONDS);
                    if (!terminated) {
                        Log.e(TAG, "进程无法终止，可能存在僵尸进程");
                    }
                }
            } catch (Exception e) {
                Log.w(TAG, "销毁进程失败: " + e.getMessage());
            }
        }
    }

    /**
     * 检查Root权限是否可用 - 优化版本
     */
    public ShellResult checkRootAccess() {
        Log.d(TAG, "开始检测Root权限可用性...");

        // 使用缓存的root检测结果
        String cacheKey = "root_check";
        CachedResult cached = commandCache.get(cacheKey);
        if (cached != null && !cached.isExpired()) {
            Log.d(TAG, "使用缓存的Root检测结果");
            return cached.result;
        }

        // 方法1: 执行id命令检查uid（最可靠）
        ShellResult result1 = executeRootCommand("id", 3);
        if (result1.isSuccess() && result1.output.contains("uid=0")) {
            Log.i(TAG, "Root权限检测成功: 已获取root权限");
            ShellResult successResult = new ShellResult(true, "Root可用", "", 0, null, result1.executionTime);
            commandCache.put(cacheKey, new CachedResult(successResult));
            return successResult;
        }

        // 方法2: 检查/system分区是否可写
        ShellResult result2 = executeRootCommand("touch /system/test_root && rm -f /system/test_root", 3);
        if (result2.isSuccess()) {
            Log.i(TAG, "Root权限检测成功: /system分区可写");
            ShellResult successResult = new ShellResult(true, "Root可用", "", 0, null, result2.executionTime);
            commandCache.put(cacheKey, new CachedResult(successResult));
            return successResult;
        }

        Log.w(TAG, "Root权限检测失败");
        ShellResult failResult = new ShellResult(false, "", "无法获取root权限", -1, null, 0);
        commandCache.put(cacheKey, new CachedResult(failResult));
        return failResult;
    }

    /**
     * 清理缓存
     */
    public void clearCache() {
        commandCache.clear();
        Log.i(TAG, "命令缓存已清理");
    }

    /**
     * 清理资源
     */
    public void destroy() {
        clearCache();

        if (executorService != null && !executorService.isShutdown()) {
            executorService.shutdown();
            try {
                if (!executorService.awaitTermination(3, TimeUnit.SECONDS)) {
                    executorService.shutdownNow();
                    if (!executorService.awaitTermination(2, TimeUnit.SECONDS)) {
                        Log.e(TAG, "线程池无法正常关闭");
                    }
                }
            } catch (InterruptedException e) {
                executorService.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
        Log.i(TAG, "EnhancedShellExecutor已销毁");
    }
}
