package com.qimian233.ztool

import android.app.Application
import android.content.Context
import io.github.libxposed.service.XposedService
import io.github.libxposed.service.XposedServiceHelper

/**
 * 自定义 Application 类。
 * <p>
 * 借鉴 HyperCeiler 的模式：Application 自身实现 [XposedServiceHelper.OnServiceListener]，
 * 在 [attachBaseContext] 中注册监听器（早于 onCreate），
 * 最大程度缩短 binder 到达与 listener 注册之间的窗口。
 * </p>
 */
class ZToolApplication : Application(), XposedServiceHelper.OnServiceListener {

    companion object {
        /** 模块是否已激活，由 onServiceBind/onServiceDied 维护 */
        @Volatile
        var isModuleActivated: Boolean = false
            private set
    }

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base)
        // 在最早的时机注册监听器，比 onCreate() 更早，
        // 减少 binder 被缓存后再排空时 linkToDeath 失败的竞态
        XposedServiceHelper.registerListener(this)
    }

    override fun onServiceBind(service: XposedService) {
        isModuleActivated = true
        XposedServiceBridge.currentService = service
    }

    override fun onServiceDied(service: XposedService) {
        isModuleActivated = false
        XposedServiceBridge.currentService = null
    }
}
