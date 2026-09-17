package com.qimian233.ztool

/**
 * Module activation state probe.
 * <p>
 * The activation state is maintained by [ZToolApplication] — the Application itself implements
 * [io.github.libxposed.service.XposedServiceHelper.OnServiceListener],
 * registered in [ZToolApplication.attachBaseContext], and updates
 * [ZToolApplication.isModuleActivated] in onServiceBind/onServiceDied.
 * </p>
 * <p>
 * This class provides the external [isModuleActive] query interface,
 * which internally delegates directly to [ZToolApplication.isModuleActivated].
 * </p>
 */
object ModuleActivationProbe {

    fun isModuleActive(): Boolean = ZToolApplication.isModuleActivated
}
