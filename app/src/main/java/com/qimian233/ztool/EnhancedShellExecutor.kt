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
 * Shell command execution utility
 */
class EnhancedShellExecutor private constructor() {

    init {
        Log.i(TAG, "EnhancedShellExecutor initialized")
    }

    // Thread pool for executing shell commands
    private val executorService: ExecutorService =
        Executors.newFixedThreadPool(MAX_CONCURRENT_COMMANDS) { r ->
            Thread(r, "ShellExecutor-" + System.currentTimeMillis()).apply {
                priority = Thread.MIN_PRIORITY + 1 // Lower thread priority
            }
        }

    // Command execution counter (for rate limiting)
    private val commandCounter = AtomicInteger(0)
    private val commandLock = ReentrantLock()

    // Command result cache (avoids re-running identical commands)
    private val commandCache = ConcurrentHashMap<String, CachedResult>()

    // Last execution time (for rate limiting)
    @Volatile
    private var lastCommandTime: Long = 0

    /**
     * Shell command execution result
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
     * Cached result
     */
    private class CachedResult(val result: ShellResult) {
        val timestamp: Long = System.currentTimeMillis()
        val isExpired: Boolean
            get() = System.currentTimeMillis() - timestamp > CACHE_DURATION
    }

    /**
     * Check whether the command is in the cache whitelist (supports command matching)
     */
    private fun isInCacheWhitelist(command: String): Boolean {
        // Direct match of the full command
        if (cacheWhitelist.contains(command)) {
            return true
        }

        // Support partial matching (for commands with arguments)
        for (whitelistCommand in cacheWhitelist) {
            if (command.startsWith(whitelistCommand)) {
                return true
            }
        }

        return false
    }

    /**
     * Execute shell command (requires root), with caching
     */
    fun executeRootCommand(command: String): ShellResult {
        return executeRootCommand(command, DEFAULT_TIMEOUT)
    }

    /**
     * Execute shell command (requires root) with a custom timeout, with caching
     */
    fun executeRootCommand(command: String, timeoutSeconds: Int): ShellResult {
        val cacheKey = "root_$command"

        // Check cache (only whitelisted commands use cache)
        val shouldUseCache = isInCacheWhitelist(command)
        if (shouldUseCache) {
            val cached = commandCache[cacheKey]
            if (cached != null && !cached.isExpired) {
                Log.d(TAG, "Using cached result: $command")
                return cached.result
            }
        }

        val result = executeCommandInternal("su -c $command", timeoutSeconds, true)

        // Cache successful results (only whitelisted commands are cached)
        if (shouldUseCache && result.isSuccess) {
            commandCache[cacheKey] = CachedResult(result)
            Log.d(TAG, "Command added to cache: $command")
        }

        return result
    }

    /**
     * Execute shell command (normal privileges)
     */
    fun executeCommand(command: String): ShellResult {
        return executeCommandInternal(command, DEFAULT_TIMEOUT, false)
    }

    /**
     * Core method for executing shell commands
     */
    private fun executeCommandInternal(command: String, timeoutSeconds: Int, isRootCommand: Boolean): ShellResult {
        // Rate limit check
        if (!acquireCommandSlot()) {
            Log.w(TAG, "Command execution rate limited: $command")
            return ShellResult(false, "", "系统繁忙，请稍后重试", -1,
                RuntimeException("Command rate limited"), 0)
        }

        val startTime = System.currentTimeMillis()
        Log.d(TAG, "Executing command: " + (if (isRootCommand) "[ROOT] " else "") + command)

        // Retry mechanism
        for (attempt in 0..RETRY_COUNT) {
            if (attempt > 0) {
                Log.d(TAG, "Retrying command, attempt " + (attempt + 1) + ": " + command)
                try {
                    Thread.sleep(200) // Brief wait before retry
                } catch (_: InterruptedException) {
                    Thread.currentThread().interrupt()
                }
            }

            val result = executeSingleCommand(command, timeoutSeconds)

            // Return immediately on success or non-timeout errors
            if (result.isSuccess || result.exception !is TimeoutException) {
                val executionTime = System.currentTimeMillis() - startTime
                Log.d(TAG, String.format("Command execution finished - took: %dms, result: %s",
                    executionTime, if (result.isSuccess) "success" else "failure"))
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
     * Acquire a command execution slot (rate limiting)
     */
    private fun acquireCommandSlot(): Boolean {
        commandLock.lock()
        try {
            // Check concurrency limit
            if (commandCounter.get() >= MAX_CONCURRENT_COMMANDS) {
                return false
            }

            // Check execution interval
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
     * Release a command execution slot
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
     * Execute a single command
     */
    private fun executeSingleCommand(command: String, timeoutSeconds: Int): ShellResult {
        return try {
            // Create a Callable task for timeout control
            val task = Callable {
                var localProcess: Process? = null
                var localOutputReader: BufferedReader? = null
                try {
                    // Use ProcessBuilder for finer control
                    val processBuilder = ProcessBuilder()
                    if (command.contains(" ")) {
                        processBuilder.command(*command.split(" ".toRegex()).toTypedArray())
                    } else {
                        processBuilder.command(command)
                    }

                    // Redirect error stream to standard output
                    processBuilder.redirectErrorStream(true)
                    localProcess = processBuilder.start()

                    // Read output
                    localOutputReader = BufferedReader(InputStreamReader(localProcess!!.inputStream))
                    val output = StringBuilder()
                    var line: String?
                    while (localOutputReader.readLine().also { line = it } != null) {
                        output.append(line).append("\n")
                    }

                    // Wait for the process to finish and get the exit code
                    val exitCode = localProcess.waitFor()

                    val outputStr = output.toString().trim()

                    Log.v(TAG, "Command execution finished - exit code: " + exitCode +
                        ", output: " + (if (outputStr.length > 100) outputStr.substring(0, 100) + "..." else outputStr))

                    ShellResult(true, outputStr, "", exitCode, null, 0)
                } catch (e: Exception) {
                    Log.e(TAG, "Command execution exception: " + e.message, e)
                    ShellResult(false, "", e.message ?: "", -1, e, 0)
                } finally {
                    // Clean up resources
                    safeClose(localOutputReader)
                    safeDestroy(localProcess)
                }
            }

            // Submit the task with a timeout
            val future = executorService.submit(task)
            future.get(timeoutSeconds.toLong(), TimeUnit.SECONDS)
        } catch (e: TimeoutException) {
            Log.w(TAG, "Command execution timed out: $command")
            ShellResult(false, "", "命令执行超时", -1, e, 0)
        } catch (e: Exception) {
            Log.e(TAG, "Command execution failed: " + e.message, e)
            ShellResult(false, "", e.message ?: "", -1, e, 0)
        }
    }

    /**
     * Safely close a BufferedReader
     */
    private fun safeClose(reader: BufferedReader?) {
        if (reader != null) {
            try {
                reader.close()
            } catch (e: IOException) {
                Log.w(TAG, "Failed to close BufferedReader: " + e.message)
            }
        }
    }

    /**
     * Safely destroy a Process - enhanced version
     */
    private fun safeDestroy(process: Process?) {
        if (process != null) {
            try {
                // Try to close the streams first (to avoid blocking)
                try {
                    process.inputStream.close()
                } catch (_: Exception) {
                    // Ignore
                }
                try {
                    process.errorStream.close()
                } catch (_: Exception) {
                    // Ignore
                }
                try {
                    process.outputStream.close()
                } catch (_: Exception) {
                    // Ignore
                }

                // Try graceful termination
                process.destroy()

                // Wait for the process to exit
                var terminated = process.waitFor(1, TimeUnit.SECONDS)
                if (!terminated) {
                    // Force termination
                    process.destroyForcibly()
                    Log.w(TAG, "Process was forcibly terminated")

                    // Wait again
                    terminated = process.waitFor(1, TimeUnit.SECONDS)
                    if (!terminated) {
                        Log.e(TAG, "Process could not be terminated, possible zombie process")
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to destroy process: " + e.message)
            }
        }
    }

    /**
     * Check whether root access is available - optimized version
     */
    fun checkRootAccess(): ShellResult {
        Log.d(TAG, "Checking root access availability...")

        // Use the cached root check result
        val cacheKey = "root_check"
        val cached = commandCache[cacheKey]
        if (cached != null && !cached.isExpired) {
            Log.d(TAG, "Using cached root check result")
            return cached.result
        }

        // Method 1: run the id command to check uid (most reliable) - with whitelist caching
        val result1 = executeRootCommand("id", 3)
        if (result1.isSuccess && result1.output.contains("uid=0")) {
            Log.i(TAG, "Root check succeeded: root access obtained")
            val successResult = ShellResult(true, "Root可用", "", 0, null, result1.executionTime)
            commandCache[cacheKey] = CachedResult(successResult)
            return successResult
        }

        // Method 2: check whether the /system partition is writable - with whitelist caching
        val result2 = executeRootCommand("touch /system/test_root && rm -f /system/test_root", 3)
        if (result2.isSuccess) {
            Log.i(TAG, "Root check succeeded: /system partition is writable")
            val successResult = ShellResult(true, "Root可用", "", 0, null, result2.executionTime)
            commandCache[cacheKey] = CachedResult(successResult)
            return successResult
        }

        Log.w(TAG, "Root access check failed")
        val failResult = ShellResult(false, "", "无法获取root权限", -1, null, 0)
        commandCache[cacheKey] = CachedResult(failResult)
        return failResult
    }

    /**
     * Clear the cache
     */
    fun clearCache() {
        commandCache.clear()
        Log.i(TAG, "Command cache cleared")
    }

    /**
     * Print cache statistics (for debugging)
     */
    fun printCacheStats() {
        Log.i(TAG, "Cache stats - total entries: " + commandCache.size)
        for (key in commandCache.keys) {
            val cached = commandCache[key]
            if (cached != null) {
                val age = System.currentTimeMillis() - cached.timestamp
                Log.d(TAG, "Cache entry: " + key + ", age: " + age + "ms, expired: " + cached.isExpired)
            }
        }
    }

    /**
     * Clean up resources
     */
    fun destroy() {
        clearCache()

        if (!executorService.isShutdown) {
            executorService.shutdown()
            try {
                if (!executorService.awaitTermination(3, TimeUnit.SECONDS)) {
                    executorService.shutdownNow()
                    if (!executorService.awaitTermination(2, TimeUnit.SECONDS)) {
                        Log.e(TAG, "Thread pool could not be shut down gracefully")
                    }
                }
            } catch (_: InterruptedException) {
                executorService.shutdownNow()
                Thread.currentThread().interrupt()
            }
        }
        Log.i(TAG, "EnhancedShellExecutor destroyed")
    }

    companion object {
        private const val TAG = "EnhancedShellExecutor"

        // Configuration parameters
        private const val DEFAULT_TIMEOUT = 8
        private const val MAX_CONCURRENT_COMMANDS = 2
        private const val CACHE_DURATION: Long = 30000 // 30s cache
        private const val RETRY_COUNT = 1

        // Minimum interval since last execution (for rate limiting)
        private const val MIN_COMMAND_INTERVAL: Long = 50

        // Cache whitelist - only these commands are cached
        private val cacheWhitelist: Set<String> = HashSet(
            listOf(
                "id",                                   // Root access check
                "touch /system/test_root && rm -f /system/test_root", // Root access check
                "magisk -v",                           // Magisk detection
                "su -v",                               // KernelSU detection
                "apd -v",                              // APatch detection
                "getprop ro.lsposed.version",          // LSPosed version detection
                "ls -la /data/adb/modules/ | grep -i lsposed", // LSPosed directory detection
                "uname -r",                             // Kernel version detection
                "ls /data/system_ce/0/managed_apps/",    // Game package name detection
                "getprop ro.build.display.id",             // System version detection
                "su -c getprop ro.odm.lenovo.gsn"          // Get serial number
            )
        )

        // Singleton instance
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
