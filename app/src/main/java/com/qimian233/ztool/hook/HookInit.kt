package com.qimian233.ztool.hook

import android.os.Build

import com.qimian233.ztool.hook.base.HookManager

import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface

import org.lsposed.hiddenapibypass.HiddenApiBypass

/**
 * ZTool libxposed 模块主入口（Kotlin）。
 * <p>
 * 继承 [XposedModule]（同时也是 [XposedInterface]），
 * 通过生命周期回调分发给各个 Hook 子模块。
 */
class HookInit : XposedModule() {

    companion object {
        private const val TAG = "ZToolXposedModuleInit"
        @Volatile
        private var instance: HookInit? = null

        @JvmStatic
        fun getInstance(): HookInit? = instance

        @JvmStatic
        fun getXposedInterface(): XposedInterface? = instance
    }

    override fun onModuleLoaded(param: XposedModuleInterface.ModuleLoadedParam) {
        instance = this

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            try {
                HiddenApiBypass.addHiddenApiExemptions("")
            } catch (t: Throwable) {
                log(6, TAG, "HiddenApiBypass 初始化失败", t)
            }
        }

        // 将 this 作为 XposedInterface 传给 HookManager
        HookManager.initialize(this)
        log(4, TAG, "ZTool Hook 模块已加载, 进程: " + param.processName)
    }

    override fun onPackageLoaded(param: XposedModuleInterface.PackageLoadedParam) {
        HookManager.handlePackageLoaded(param)
    }

    override fun onSystemServerStarting(
        param: XposedModuleInterface.SystemServerStartingParam
    ) {
        log(4, TAG, "系统服务器启动中，分发系统作用域Hook")
        HookManager.handleSystemServerStarting(param)
    }

    // ── 热重载支持 ─────────────────────────────────────────────

    override fun onHotReloading(param: XposedModuleInterface.HotReloadingParam): Boolean {
        // 热重载会创建新一代模块代码（新 classloader），HookManager 的静态字段不跨代共享。
        // 生命周期参数是框架创建的对象（classloader-neutral），必须在旧代码冻结前
        // 通过 savedInstanceState 显式传递给新代码，供 onHotReloaded 重放 Hook 安装。
        param.setSavedInstanceState(
            arrayOf<Any?>(
                HookManager.getSavedPackageParams(),
                HookManager.getSavedSystemServerParam()
            )
        )
        log(
            4, TAG,
            "热重载请求，已保存生命周期参数: " + HookManager.getSavedPackageParams().size
                    + " 个包, 同意重载"
        )
        return true
    }

    @Suppress("UNCHECKED_CAST")
    override fun onHotReloaded(param: XposedModuleInterface.HotReloadedParam) {
        instance = this
        // 恢复旧代码传递过来的生命周期参数（静态字段不跨 classloader 共享，
        // 否则 replayAllHooks 拿不到任何参数，Hook 将全部丢失）。
        val saved = param.savedInstanceState
        if (saved is Array<*>) {
            val packages = saved.getOrNull(0)
                    as? List<XposedModuleInterface.PackageLoadedParam>
            val systemServer = saved.getOrNull(1)
                    as? XposedModuleInterface.SystemServerStartingParam
            HookManager.restoreLifecycleParams(packages, systemServer)
        }
        log(4, TAG, "热重载完成，重新注册模块并回放 Hook 安装")
        HookManager.reinitializeForHotReload(this)
        HookManager.replayAllHooks()
        param.oldHookHandles.forEach(XposedInterface.HookHandle::unhook)
        log(4, TAG, "热重载清理完成，已卸载旧 Hook: " + param.oldHookHandles.size + " 个")
    }
}
