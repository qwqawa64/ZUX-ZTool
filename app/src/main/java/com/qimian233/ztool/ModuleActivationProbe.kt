package com.qimian233.ztool

import android.os.Handler
import android.os.Looper
import io.github.libxposed.service.XposedService
import io.github.libxposed.service.XposedServiceHelper
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 模块激活状态探测器（libxposed 版）。
 * <p>
 * 使用 {@link XposedServiceHelper#registerListener} 以线程安全的方式接收 libxposed service binder。
 * 成功收到 service 即表示模块已被框架激活。
 * </p>
 * <p>
 * libxposed 不支持 hook 自身进程，且模块自身的 app 进程不在 scope 中，
 * 因此旧版 hook {@code isModuleActive()} 的方案不可行。
 * </p>
 * <p>
 * <b>防抖说明：</b>{@link XposedServiceHelper#onBinderReceived} 中存在竞态条件：
 * binder 到达后、linkToDeath 注册前可能已经死亡，此时 linkToDeath 抛出异常，
 * onServiceBind 不会被调用，但 onServiceDied 已经将 active 置为 false。
 * 通过延迟 deactivation 来容忍短暂的重连间隔。
 * </p>
 */
object ModuleActivationProbe {

    /** deactivation 延迟，给 LSPosed 重连的窗口期 */
    private const val DEACTIVATION_DELAY_MS = 2_000L

    /** 线程安全的激活标记 */
    private val active = AtomicBoolean(false)

    private val handler = Handler(Looper.getMainLooper())
    private val deactivationRunnable = Runnable {
        active.set(false)
    }

    init {
        XposedServiceHelper.registerListener(object : XposedServiceHelper.OnServiceListener {
            override fun onServiceBind(service: XposedService) {
                handler.removeCallbacks(deactivationRunnable)
                active.set(true)
                XposedServiceBridge.currentService = service
            }

            override fun onServiceDied(service: XposedService) {
                if (XposedServiceBridge.currentService === service) {
                    XposedServiceBridge.currentService = null
                    // 不立即标记为未激活；延迟后若仍未收到新 binder 才降级
                    handler.removeCallbacks(deactivationRunnable)
                    handler.postDelayed(deactivationRunnable, DEACTIVATION_DELAY_MS)
                }
            }
        })
    }

    @JvmStatic
    fun isModuleActive(): Boolean = active.get()
}
