package com.qimian233.ztool

/**
 * 模块激活状态探测器。
 * <p>
 * 激活状态由 [ZToolApplication] 维护——Application 自身实现
 * [io.github.libxposed.service.XposedServiceHelper.OnServiceListener]，
 * 在 [ZToolApplication.attachBaseContext] 中注册，
 * 在 onServiceBind/onServiceDied 中更新 [ZToolApplication.isModuleActivated]。
 * </p>
 * <p>
 * 此类提供对外的 [isModuleActive] 查询接口，
 * 内部直接委托给 [ZToolApplication.isModuleActivated]。
 * </p>
 */
object ModuleActivationProbe {

    @JvmStatic
    fun isModuleActive(): Boolean = ZToolApplication.isModuleActivated
}
