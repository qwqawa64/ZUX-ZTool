package com.qimian233.ztool

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
 */
object ModuleActivationProbe {

    /** 线程安全的激活标记 */
    private val active = AtomicBoolean(false)

    /** 最近一次收到的 XposedService 实例，可供动态作用域等功能使用 */
    @Volatile
    var currentService: XposedService? = null
        private set

    init {
        XposedServiceHelper.registerListener(object : XposedServiceHelper.OnServiceListener {
            override fun onServiceBind(service: XposedService) {
                active.set(true)
                currentService = service
            }

            override fun onServiceDied(service: XposedService) {
                if (currentService === service) {
                    active.set(false)
                    currentService = null
                }
            }
        })
    }

    @JvmStatic
    fun isModuleActive(): Boolean = active.get()
}
