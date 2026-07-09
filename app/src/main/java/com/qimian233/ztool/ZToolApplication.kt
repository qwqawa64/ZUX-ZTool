package com.qimian233.ztool

import android.app.Application

/**
 * 自定义 Application 类。
 * <p>
 * 在 onCreate 中尽早触发 [ModuleActivationProbe] 初始化，
 * 确保 XposedServiceHelper 的 listener 在 LSPosed 投递 binder 之前或紧随其后注册，
 * 减少 binder 缓存后因 linkToDeath 失败导致 onServiceBind 不被调用的竞态窗口。
 * </p>
 */
class ZToolApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        // 强制触发 ModuleActivationProbe 的 init 块，
        // 尽早向 XposedServiceHelper 注册 OnServiceListener
        ModuleActivationProbe.ensureInitialized()
    }
}
