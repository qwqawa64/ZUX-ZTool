package com.qimian233.ztool

import android.util.Log
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.util.concurrent.Callable
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.locks.ReentrantLock

/**
 * Shell命令执行工具类
 */
class EnhancedShellExecutor private constructor() {

    init {
        Log.i(TAG, "EnhancedShellExecutor初始化完成")
    }

    // 线程池用于执行Shell命令
    private val executorService: ExecutorService =
        Executors.newFixedThreadPool(MAX_CONCURRENT_COMMANDS) { r ->
            Thread(r, "ShellExecutor-" + System.currentTimeMillis()).apply {
                priority = Thread.MIN_PRIORITY + 1 // 降低线程优先级
            }
        }

    // 命令执行计数器（用于限流）
    private val commandCounter = AtomicInteger(0)
    private val commandLock = ReentrantLock()

    // 命令结果缓存（避免重复执行相同命令）
    private val commandCache = ConcurrentHashMap<String, CachedResult>()

    // 上次执行时间（用于限流）
    @Volatile
    private var lastCommandTime: Long = 0

    /**
     * Shell命令执行结果
     */
    class ShellResult(
        val success: Boolean,
        val output: String,
        val error: String,
        val exitCode: Int,
        val exception: Exception?,
        val executionTime: Long,
    ) {
        val isSuccess: Boolean
            get() = success && exitCode == 0
    }

    /**
     * 缓存结果
     */
    private class CachedResult(val result: ShellResult) {
        val timestamp: Long = System.currentTimeMillis()
        val isExpired: Boolean
            get() = System.currentTimeMillis() - timestamp > CACHE_DURATION
    }

    /**
     * 检查命令是否在白名单中（支持命令匹配）
     */
    private fun isInCacheWhitelist(command: String): Boolean {
        // 直接匹配完整命令
        if (cacheWhitelist.contains(command)) {
            return true
        }

        // 支持部分匹配（对于带参数的命令）
        for (whitelistCommand in cacheWhitelist) {
            if (command.startsWith(whitelistCommand)) {
                return true
            }
        }

        return false
    }

    /**
     * 执行Shell命令（需要root权限），带缓存
     */
    fun executeRootCommand(command: String): ShellResult {
        return executeRootCommand(command, DEFAULT_TIMEOUT)
    }

    /**
     * 执行Shell命令（需要root权限），可指定超时时间，带缓存
     */
    fun executeRootCommand(command: String, timeoutSeconds: Int): ShellResult {
        val cacheKey = "root_$command"

        // 检查缓存（只有白名单中的命令才使用缓存）
        val shouldUseCache = isInCacheWhitelist(command)
        if (shouldUseCache) {
            val cached = commandCache[cacheKey]
            if (cached != null && !cached.isExpired) {
                Log.d(TAG, "使用缓存结果: $command")
                return cached.result
            }
        }

        val result = executeCommandInternal("su -c $command", timeoutSeconds, true)

        // 缓存成功的结果（只有白名单中的命令才缓存）
        if (shouldUseCache && result.isSuccess) {
            commandCache[cacheKey] = CachedResult(result)
            Log.d(TAG, "命令已加入缓存: $command")
        }

        return result
    }

    /**
     * 执行Shell命令（普通权限）
     */
    fun executeCommand(command: String): ShellResult {
        return executeCommandInternal(command, DEFAULT_TIMEOUT, false)
    }

    /**
     * 执行Shell命令的核心方法
     */
    private fun executeCommandInternal(command: String, timeoutSeconds: Int, isRootCommand: Boolean): ShellResult {
        // 限流检查
        if (!acquireCommandSlot()) {
            Log.w(TAG, "命令执行被限流: $command")
            return ShellResult(false, "", "系统繁忙，请稍后重试", -1,
                RuntimeException("Command rate limited"), 0)
        }

        val startTime = System.currentTimeMillis()
        Log.d(TAG, "执行命令: " + (if (isRootCommand) "[ROOT] " else "") + command)

        // 重试机制
        for (attempt in 0..RETRY_COUNT) {
            if (attempt > 0) {
                Log.d(TAG, "命令重试，第 " + (attempt + 1) + " 次: " + command)
                try {
                    Thread.sleep(200) // 重试前短暂等待
                } catch (_: InterruptedException) {
                    Thread.currentThread().interrupt()
                }
            }

            val result = executeSingleCommand(command, timeoutSeconds)

            // 如果成功或非超时错误，直接返回
            if (result.isSuccess || result.exception !is TimeoutException) {
                val executionTime = System.currentTimeMillis() - startTime
                Log.d(TAG, String.format("命令执行完成 - 耗时: %dms, 结果: %s",
                    executionTime, if (result.isSuccess) "成功" else "失败"))
                releaseCommandSlot()
                return ShellResult(result.success, result.output, result.error,
                    result.exitCode, result.exception, executionTime)
            }
        }

        releaseCommandSlot()
        return ShellResult(false, "", "命令执行超时", -1,
            TimeoutException("Command timeout after retries"),
            System.currentTimeMillis() - startTime)
    }

    /**
     * 获取命令执行槽位（限流）
     */
    private fun acquireCommandSlot(): Boolean {
        commandLock.lock()
        try {
            // 检查并发数限制
            if (commandCounter.get() >= MAX_CONCURRENT_COMMANDS) {
                return false
            }

            // 检查执行间隔
            val currentTime = System.currentTimeMillis()
            if (currentTime - lastCommandTime < MIN_COMMAND_INTERVAL) {
                try {
                    Thread.sleep(MIN_COMMAND_INTERVAL - (currentTime - lastCommandTime))
                } catch (_: InterruptedException) {
                    Thread.currentThread().interrupt()
                    return false
                }
            }

            commandCounter.incrementAndGet()
            lastCommandTime = System.currentTimeMillis()
            return true
        } finally {
            commandLock.unlock()
        }
    }

    /**
     * 释放命令执行槽位
     */
    private fun releaseCommandSlot() {
        commandLock.lock()
        try {
            commandCounter.decrementAndGet()
        } finally {
            commandLock.unlock()
        }
    }

    /**
     * 执行单个命令
     */
    private fun executeSingleCommand(command: String, timeoutSeconds: Int): ShellResult {
        return try {
            // 创建Callable任务用于超时控制
            val task = Callable {
                var localProcess: Process? = null
                var localOutputReader: BufferedReader? = null
                try {
                    // 使用ProcessBuilder获得更好的控制
                    val processBuilder = ProcessBuilder()
                    if (command.contains(" ")) {
                        processBuilder.command(*command.split(" ".toRegex()).toTypedArray())
                    } else {
                        processBuilder.command(command)
                    }

                    // 重定向错误流到标准输出
                    processBuilder.redirectErrorStream(true)
                    localProcess = processBuilder.start()

                    // 读取输出
                    localOutputReader = BufferedReader(InputStreamReader(localProcess!!.inputStream))
                    val output = StringBuilder()
                    var line: String?
                    while (localOutputReader.readLine().also { line = it } != null) {
                        output.append(line).append("\n")
                    }

                    // 等待进程结束并获取退出码
                    val exitCode = localProcess.waitFor()

                    val outputStr = output.toString().trim()

                    Log.v(TAG, "命令执行完成 - 退出码: " + exitCode +
                        ", 输出: " + (if (outputStr.length > 100) outputStr.substring(0, 100) + "..." else outputStr))

                    ShellResult(true, outputStr, "", exitCode, null, 0)
                } catch (e: Exception) {
                    Log.e(TAG, "命令执行异常: " + e.message, e)
                    ShellResult(false, "", e.message ?: "", -1, e, 0)
                } finally {
                    // 清理资源
                    safeClose(localOutputReader)
                    safeDestroy(localProcess)
                }
            }

            // 提交任务并设置超时
            val future = executorService.submit(task)
            future.get(timeoutSeconds.toLong(), TimeUnit.SECONDS)
        } catch (e: TimeoutException) {
            Log.w(TAG, "命令执行超时: $command")
            ShellResult(false, "", "命令执行超时", -1, e, 0)
        } catch (e: Exception) {
            Log.e(TAG, "命令执行失败: " + e.message, e)
            ShellResult(false, "", e.message ?: "", -1, e, 0)
        }
    }

    /**
     * 安全关闭BufferedReader
     */
    private fun safeClose(reader: BufferedReader?) {
        if (reader != null) {
            try {
                reader.close()
            } catch (e: IOException) {
                Log.w(TAG, "关闭BufferedReader失败: " + e.message)
            }
        }
    }

    /**
     * 安全销毁Process - 增强版本
     */
    private fun safeDestroy(process: Process?) {
        if (process != null) {
            try {
                // 先尝试获取输入流并关闭（避免阻塞）
                try {
                    process.inputStream.close()
                } catch (_: Exception) {
                    // 忽略
                }
                try {
                    process.errorStream.close()
                } catch (_: Exception) {
                    // 忽略
                }
                try {
                    process.outputStream.close()
                } catch (_: Exception) {
                    // 忽略
                }

                // 尝试正常终止
                process.destroy()

                // 等待进程退出
                var terminated = process.waitFor(1, TimeUnit.SECONDS)
                if (!terminated) {
                    // 强制终止
                    process.destroyForcibly()
                    Log.w(TAG, "进程被强制终止")

                    // 再次等待
                    terminated = process.waitFor(1, TimeUnit.SECONDS)
                    if (!terminated) {
                        Log.e(TAG, "进程无法终止，可能存在僵尸进程")
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "销毁进程失败: " + e.message)
            }
        }
    }

    /**
     * 检查Root权限是否可用 - 优化版本
     */
    fun checkRootAccess(): ShellResult {
        Log.d(TAG, "开始检测Root权限可用性...")

        // 使用缓存的root检测结果
        val cacheKey = "root_check"
        val cached = commandCache[cacheKey]
        if (cached != null && !cached.isExpired) {
            Log.d(TAG, "使用缓存的Root检测结果")
            return cached.result
        }

        // 方法1: 执行id命令检查uid（最可靠）- 使用白名单缓存
        val result1 = executeRootCommand("id", 3)
        if (result1.isSuccess && result1.output.contains("uid=0")) {
            Log.i(TAG, "Root权限检测成功: 已获取root权限")
            val successResult = ShellResult(true, "Root可用", "", 0, null, result1.executionTime)
            commandCache[cacheKey] = CachedResult(successResult)
            return successResult
        }

        // 方法2: 检查/system分区是否可写 - 使用白名单缓存
        val result2 = executeRootCommand("touch /system/test_root && rm -f /system/test_root", 3)
        if (result2.isSuccess) {
            Log.i(TAG, "Root权限检测成功: /system分区可写")
            val successResult = ShellResult(true, "Root可用", "", 0, null, result2.executionTime)
            commandCache[cacheKey] = CachedResult(successResult)
            return successResult
        }

        Log.w(TAG, "Root权限检测失败")
        val failResult = ShellResult(false, "", "无法获取root权限", -1, null, 0)
        commandCache[cacheKey] = CachedResult(failResult)
        return failResult
    }

    /**
     * 清理缓存
     */
    fun clearCache() {
        commandCache.clear()
        Log.i(TAG, "命令缓存已清理")
    }

    /**
     * 获取缓存统计信息（用于调试）
     */
    fun printCacheStats() {
        Log.i(TAG, "缓存统计 - 总条目数: " + commandCache.size)
        for (key in commandCache.keys) {
            val cached = commandCache[key]
            if (cached != null) {
                val age = System.currentTimeMillis() - cached.timestamp
                Log.d(TAG, "缓存项: " + key + ", 年龄: " + age + "ms, 过期: " + cached.isExpired)
            }
        }
    }

    /**
     * 清理资源
     */
    fun destroy() {
        clearCache()

        if (!executorService.isShutdown) {
            executorService.shutdown()
            try {
                if (!executorService.awaitTermination(3, TimeUnit.SECONDS)) {
                    executorService.shutdownNow()
                    if (!executorService.awaitTermination(2, TimeUnit.SECONDS)) {
                        Log.e(TAG, "线程池无法正常关闭")
                    }
                }
            } catch (_: InterruptedException) {
                executorService.shutdownNow()
                Thread.currentThread().interrupt()
            }
        }
        Log.i(TAG, "EnhancedShellExecutor已销毁")
    }

    companion object {
        private const val TAG = "EnhancedShellExecutor"

        // 配置参数
        private const val DEFAULT_TIMEOUT = 8
        private const val MAX_CONCURRENT_COMMANDS = 2
        private const val CACHE_DURATION: Long = 30000 // 30秒缓存
        private const val RETRY_COUNT = 1

        // 上次执行时间最小间隔（用于限流）
        private const val MIN_COMMAND_INTERVAL: Long = 50

        // 缓存白名单 - 只有这些命令会被缓存
        private val cacheWhitelist: Set<String> = HashSet(
            listOf(
                "id",                                   // Root权限检测
                "touch /system/test_root && rm -f /system/test_root", // Root权限检测
                "magisk -v",                           // Magisk检测
                "su -v",                               // KernelSU检测
                "apd -v",                              // APatch检测
                "getprop ro.lsposed.version",          // LSPosed版本检测
                "ls -la /data/adb/modules/ | grep -i lsposed", // LSPosed目录检测
                "uname -r",                             // 内核版本检测
                "ls /data/system_ce/0/managed_apps/",    // 游戏包名检测
                "getprop ro.build.display.id",             // 系统版本检测
                "su -c getprop ro.odm.lenovo.gsn"          // 获取SN
            )
        )

        // 单例实例
        @Volatile
        private var instance: EnhancedShellExecutor? = null

        fun getInstance(): EnhancedShellExecutor {
            if (instance == null) {
                synchronized(EnhancedShellExecutor::class.java) {
                    if (instance == null) {
                        instance = EnhancedShellExecutor()
                    }
                }
            }
            return instance!!
        }
    }
}
