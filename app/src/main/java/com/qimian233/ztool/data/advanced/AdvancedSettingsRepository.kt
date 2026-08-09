package com.qimian233.ztool.data.advanced

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.qimian233.ztool.EnhancedShellExecutor
import com.qimian233.ztool.XposedServiceBridge
import io.github.libxposed.service.HookedTarget
import io.github.libxposed.service.HotReloadResult
import io.github.libxposed.service.XposedService
import java.util.concurrent.atomic.AtomicInteger

/**
 * 高级选项仓库。
 * <p>
 * 封装模块热重载等开发者功能，处理线程切换与结果汇总。
 * </p>
 */
class AdvancedSettingsRepository(
    private val shellExecutor: EnhancedShellExecutor = EnhancedShellExecutor.getInstance()
) {

    private val mainHandler = Handler(Looper.getMainLooper())

    // ---- 查询 ----

    /** 获取 API 版本，未激活时返回 0 */
    fun getApiVersion(): Int = XposedServiceBridge.getApiVersion()

    /** 获取运行中的 Hook 目标列表 */
    fun getRunningTargets(): List<HookedTarget> = XposedServiceBridge.getRunningTargets()

    // ---- 热重载 ----

    /**
     * 对当前所有非 RELOADING 状态的运行目标执行热重载。
     *
     * @param onProgress 每个目标完成后回调（主线程），参数为 (processName, status, message)
     * @param onComplete 全部完成后回调（主线程），参数为 (succeeded, failed, unsupported, died)
     */
    fun performHotReloadAll(
        onProgress: (target: HookedTarget, result: HotReloadResult) -> Unit,
        onComplete: (succeededCount: Int, failedCount: Int, unsupportedCount: Int, diedCount: Int, details: List<HotReloadDetail>) -> Unit
    ) {
        val targets = getRunningTargets()
        if (targets.isEmpty()) {
            onComplete(0, 0, 0, 0, emptyList())
            return
        }

        val eligible = targets.filter { it.state != HookedTarget.State.RELOADING }
        if (eligible.isEmpty()) {
            onComplete(0, 0, 0, 0, emptyList())
            return
        }

        val total = eligible.size
        val completed = AtomicInteger(0)
        val succeeded = AtomicInteger(0)
        val failed = AtomicInteger(0)
        val unsupported = AtomicInteger(0)
        val died = AtomicInteger(0)
        val details = java.util.Collections.synchronizedList(mutableListOf<HotReloadDetail>())

        for (target in eligible) {
            val callback = object : XposedService.HotReloadCallback {
                override fun onHotReloadResult(target: HookedTarget, result: HotReloadResult) {
                    val status = result.status()
                    val message = result.message() ?: ""
                    val processName = target.processName
                    val detail = HotReloadDetail(processName, status.name, message)
                    details.add(detail)

                    when (status) {
                        HotReloadResult.Status.SUCCEEDED -> {
                            Log.d(TAG, "热重载成功: $processName")
                            succeeded.incrementAndGet()
                        }
                        HotReloadResult.Status.FAILED -> {
                            Log.w(TAG, "热重载失败: $processName — $message")
                            failed.incrementAndGet()
                        }
                        HotReloadResult.Status.UNSUPPORTED -> {
                            Log.w(TAG, "热重载不支持: $processName — $message")
                            unsupported.incrementAndGet()
                        }
                        HotReloadResult.Status.PROCESS_DIED -> {
                            Log.w(TAG, "目标进程已退出: $processName — $message")
                            died.incrementAndGet()
                        }
                        HotReloadResult.Status.IN_PROGRESS -> { return }
                    }

                    mainHandler.post {
                        onProgress(target, result)
                        if (completed.incrementAndGet() >= total) {
                            mainHandler.post {
                                onComplete(
                                    succeeded.get(),
                                    failed.get(),
                                    unsupported.get(),
                                    died.get(),
                                    details.toList()
                                )
                            }
                        }
                    }
                }
            }

            try {
                XposedServiceBridge.hotReloadModule(target, Bundle(), callback)
            } catch (e: Exception) {
                Log.e(TAG, "发起热重载异常: ${target.processName}", e)
                failed.incrementAndGet()
                val detail = HotReloadDetail(target.processName, "FAILED", e.message ?: "unknown")
                details.add(detail)
                mainHandler.post {
                    onProgress(target, HotReloadResult(HotReloadResult.Status.FAILED, e.message))
                    if (completed.incrementAndGet() >= total) {
                        mainHandler.post {
                            onComplete(succeeded.get(), failed.get(), unsupported.get(), died.get(), details.toList())
                        }
                    }
                }
            }
        }
    }

    // ---- 持久化值重置 ----

    /**
     * 重置所有被本应用 Hook 修改过的持久化值，逐项执行并汇总结果。
     *
     * 当前支持：
     * - doze_always_on：清除旧版本通过 `settings put secure doze_always_on 1` 写入的残留。
     * TODO(apk-analysis)：autorun / mistouch 待分析安全中心与游戏中心 APK 后实现。
     *
     * @param onComplete 全部项执行完成后回调（调用方线程），参数为 (succeeded, failed, unsupported, details)
     */
    fun resetPersistentValues(
        onComplete: (succeeded: Int, failed: Int, unsupported: Int, details: List<PersistentResetDetail>) -> Unit
    ) {
        val details = mutableListOf<PersistentResetDetail>()
        var succeeded = 0
        var failed = 0
        var unsupported = 0

        // 1. 原生 AOD 开关（旧版 shell 写入的残留值）
        val aod = resetDozeAlwaysOn()
        if (aod.success) succeeded++ else failed++
        details += PersistentResetDetail(
            KEY_RESET_AOD, if (aod.success) "SUCCEEDED" else "FAILED", aod.message
        )

        // 2. 应用自启动状态（安全中心 AutoRunDbItem）
        // TODO(apk-analysis)：分析 com.lenovo.safecenter / com.zui.safecenter 的 autorun 数据库后实现
        unsupported++
        details += PersistentResetDetail(
            KEY_RESET_AUTORUN, "UNSUPPORTED", "尚未实现：待分析安全中心自启动数据库"
        )

        // 3. 游戏防误触状态（游戏中心 SettingsValueUtilKt）
        // TODO(apk-analysis)：分析 com.zui.game.service 的持久化写入目标后实现
        unsupported++
        details += PersistentResetDetail(
            KEY_RESET_MISTOUCH, "UNSUPPORTED", "尚未实现：待分析游戏中心防误触持久化"
        )

        onComplete(succeeded, failed, unsupported, details)
    }

    /**
     * 清除旧版本通过 `settings put secure doze_always_on 1` 写入的残留值。
     * 现在原生 AOD 由 Hook（ForceNativeAod）接管，删除残留让系统恢复默认。
     */
    private fun resetDozeAlwaysOn(): ResetOutcome {
        val current = shellExecutor.executeRootCommand("settings get secure doze_always_on")
        if (current.isSuccess) {
            val value = current.output?.trim().orEmpty()
            if (value.isEmpty() || value.equals("null", ignoreCase = true)) {
                return ResetOutcome(true, "doze_always_on 无残留值，无需重置")
            }
        }
        val result = shellExecutor.executeRootCommand("settings delete secure doze_always_on")
        return if (result.isSuccess) {
            ResetOutcome(true, "已清除 doze_always_on 残留值")
        } else {
            ResetOutcome(false, "清除 doze_always_on 失败：${result.error ?: result.output}")
        }
    }

    private data class ResetOutcome(val success: Boolean, val message: String)

    companion object {
        private const val TAG = "AdvancedRepo"
        private const val KEY_RESET_AOD = "doze_always_on"
        private const val KEY_RESET_AUTORUN = "autorun"
        private const val KEY_RESET_MISTOUCH = "mistouch"
    }
}

/**
 * 单次热重载操作的结果详情，供 UI 展示。
 */
data class HotReloadDetail(
    val processName: String,
    val status: String,
    val message: String
)

/**
 * 单次持久化值重置的结果详情，供 UI 展示。
 */
data class PersistentResetDetail(
    val key: String,
    val status: String,
    val message: String
)
