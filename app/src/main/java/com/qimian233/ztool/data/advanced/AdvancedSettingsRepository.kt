package com.qimian233.ztool.data.advanced

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
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
class AdvancedSettingsRepository {

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

    companion object {
        private const val TAG = "AdvancedRepo"
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
