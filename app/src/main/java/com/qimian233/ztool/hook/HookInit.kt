package com.qimian233.ztool.hook

import android.os.Build

import com.qimian233.ztool.hook.base.HookManager

import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface

import org.lsposed.hiddenapibypass.HiddenApiBypass

/**
 * ZTool libxposed module main entry point (Kotlin).
 * <p>
 * Extends [XposedModule] (also an [XposedInterface]) and dispatches to each
 * Hook submodule via lifecycle callbacks.
 */
class HookInit : XposedModule() {

    companion object {
        private const val TAG = "ZToolXposedModuleInit"
        @Volatile
        private var instance: HookInit? = null

        fun getInstance(): HookInit? = instance

        fun getXposedInterface(): XposedInterface? = instance
    }

    override fun onModuleLoaded(param: XposedModuleInterface.ModuleLoadedParam) {
        instance = this

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            try {
                HiddenApiBypass.addHiddenApiExemptions("")
            } catch (t: Throwable) {
                log(6, TAG, "HiddenApiBypass initialization failed", t)
            }
        }

        // Pass this as the XposedInterface to HookManager
        HookManager.initialize(this)
        log(4, TAG, "ZTool Hook module loaded, process: " + param.processName)
    }

    override fun onPackageLoaded(param: XposedModuleInterface.PackageLoadedParam) {
        HookManager.handlePackageLoaded(param)
    }

    override fun onSystemServerStarting(
        param: XposedModuleInterface.SystemServerStartingParam
    ) {
        log(4, TAG, "System server starting, dispatching system-scope hooks")
        HookManager.handleSystemServerStarting(param)
    }

    // ── Hot reload support ─────────────────────────────────────

    override fun onHotReloading(param: XposedModuleInterface.HotReloadingParam): Boolean {
        // Hot reload creates a new generation of module code (new classloader);
        // HookManager's static fields are not shared across generations.
        // Lifecycle params are framework-created objects (classloader-neutral) and must be
        // passed explicitly to the new code via savedInstanceState before the old code is
        // frozen, so that onHotReloaded can replay hook installation.
        param.setSavedInstanceState(
            arrayOf(
                HookManager.getSavedPackageParams(),
                HookManager.getSavedSystemServerParam()
            )
        )
        log(
            4, TAG,
            "Hot reload requested, saved lifecycle params: "
                    + HookManager.getSavedPackageParams().size
                    + " packages, reload accepted"
        )
        return true
    }

    @Suppress("UNCHECKED_CAST")
    override fun onHotReloaded(param: XposedModuleInterface.HotReloadedParam) {
        instance = this
        // Restore the lifecycle params passed by the old code (static fields are not shared
        // across classloaders, otherwise replayAllHooks would see no params and all hooks would be lost).
        val saved = param.savedInstanceState
        if (saved is Array<*>) {
            val packages = saved.getOrNull(0)
                    as? List<XposedModuleInterface.PackageLoadedParam>
            val systemServer = saved.getOrNull(1)
                    as? XposedModuleInterface.SystemServerStartingParam
            HookManager.restoreLifecycleParams(packages, systemServer)
        }
        log(4, TAG, "Hot reload complete, re-registering modules and replaying hook installation")
        HookManager.reinitializeForHotReload(this)
        HookManager.replayAllHooks()
        param.oldHookHandles.forEach(XposedInterface.HookHandle::unhook)
        log(4, TAG, "Hot reload cleanup complete, unhooked old hooks: " + param.oldHookHandles.size)
    }
}
