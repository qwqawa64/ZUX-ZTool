package com.qimian233.ztool

import android.app.Application
import android.content.Context
import android.util.Log
import com.qimian233.ztool.dexindex.DexIndexManager
import io.github.libxposed.service.XposedService
import io.github.libxposed.service.XposedServiceHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 自定义 Application 类。
 * <p>
 * 借鉴 HyperCeiler 的模式：Application 自身实现 [XposedServiceHelper.OnServiceListener]，
 * 在 [attachBaseContext] 中注册监听器（早于 onCreate），
 * 最大程度缩短 binder 到达与 listener 注册之间的窗口。
 * </p>
 * <p>
 * 同时暴露 [isModuleActivatedFlow]（[StateFlow]），
 * 让 UI 层可以<b>热更新</b>模块激活状态，无需轮询。
 * </p>
 */
class ZToolApplication : Application(), XposedServiceHelper.OnServiceListener {

    companion object {
        private const val TAG = "ZToolApplication"

        private val _isModuleActivated = MutableStateFlow(false)

        /** 模块激活状态的热更新流，UI 层可 collect 以实时响应状态变化 */
        val isModuleActivatedFlow: StateFlow<Boolean> = _isModuleActivated.asStateFlow()

        /** 模块是否已激活，由 onServiceBind/onServiceDied 维护（即时查询） */
        @Volatile
        var isModuleActivated: Boolean = false
            private set
    }

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        // 离线 DexKit 索引：指纹变化（首次安装/模块更新/目标 app OTA）时后台重扫
        appScope.launch {
            try {
                DexIndexManager.indexAllIfStale(applicationContext)
            } catch (t: Throwable) {
                Log.e(TAG, "stale dex index refresh failed", t)
            }
        }
    }

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base)
        // 在最早的时机注册监听器，比 onCreate() 更早，
        // 减少 binder 被缓存后再排空时 linkToDeath 失败的竞态
        XposedServiceHelper.registerListener(this)
    }

    override fun onServiceBind(service: XposedService) {
        isModuleActivated = true
        _isModuleActivated.value = true
        XposedServiceBridge.currentService = service
    }

    override fun onServiceDied(service: XposedService) {
        isModuleActivated = false
        _isModuleActivated.value = false
        XposedServiceBridge.currentService = null
    }
}
