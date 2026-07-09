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
 * <b>防抖说明：</b>{@link XposedServiceHelper#onBinderReceived} 和
 * {@link XposedServiceHelper#registerListener} 的缓存排空中均存在竞态条件：
 * linkToDeath 在 onServiceBind 之前调用，若 binder 已死亡则 linkToDeath 抛出异常，
 * onServiceBind 不会被调用。
 * <p>
 * 缓解措施：
 * <ul>
 *   <li>通过 {@link ZToolApplication#onCreate()} 尽早触发本类初始化，
 *       缩短 binder 缓存在 XposedServiceHelper 中的窗口。</li>
 *   <li>延迟 deactivation（{@link #DEACTIVATION_DELAY_MS}）容忍短暂重连。</li>
 *   <li>延迟探活（{@link #DELAYED_PROBE_DELAY_MS}）在 init 后检查
 *       XposedServiceBridge 是否已被后续的 onBinderReceived 设置。</li>
 *   <li>HomeRepository.isModuleActive() 中增加 XposedServiceBridge 回退检查。</li>
 * </ul>
 * </p>
 */
object ModuleActivationProbe {

    /** deactivation 延迟，给 LSPosed 重连的窗口期 */
    private const val DEACTIVATION_DELAY_MS = 2_000L

    /** 延迟探活间隔，给 registerListener 缓存排空失败后的重连窗口 */
    private const val DELAYED_PROBE_DELAY_MS = 500L

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

        // 延迟探活：若 registerListener 缓存排空时 linkToDeath 失败，
        // onServiceBind 不会被调用，但 LSPosed 可能在之后重新投递 binder。
        // 此时 onBinderReceived 走 else 分支直接调用 onServiceBind 设置 currentService。
        // 此探活作为安全网，在短延迟后检查 currentService 是否已被设置。
        handler.postDelayed({
            if (XposedServiceBridge.currentService != null) {
                active.compareAndSet(false, true)
            }
        }, DELAYED_PROBE_DELAY_MS)
    }

    @JvmStatic
    fun isModuleActive(): Boolean = active.get()

    /**
     * 强制触发 [ModuleActivationProbe] 的初始化。
     * <p>
     * 调用此方法会触发 Kotlin object 的 init 块，
     * 向 [XposedServiceHelper] 注册 OnServiceListener。
     * 在 Application.onCreate 中尽早调用来缩短 binder 缓存窗口。
     * </p>
     */
    @JvmStatic
    fun ensureInitialized() {
        // no-op: 方法调用本身会触发 class loading 和 init 块执行
    }
}
