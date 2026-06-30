package com.qimian233.ztool.hook;

import android.os.Build;

import com.qimian233.ztool.hook.base.HookManager;

import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;
import io.github.libxposed.api.XposedModuleInterface;

import org.lsposed.hiddenapibypass.HiddenApiBypass;

/**
 * ZTool libxposed 模块主入口。
 * <p>
 * 继承 {@link XposedModule}（同时也是 {@link XposedInterface}），
 * 通过生命周期回调分发给各个 Hook 子模块。
 */
public class HookInit extends XposedModule {

    private static final String TAG = "ZToolXposedModuleInit";
    private static volatile HookInit instance;

    public static HookInit getInstance() {
        return instance;
    }

    public static XposedInterface getXposedInterface() {
        HookInit inst = instance;
        if (inst != null) return inst;
        return null;
    }

    @Override
    public void onModuleLoaded(XposedModuleInterface.ModuleLoadedParam param) {
        instance = this;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            try {
                HiddenApiBypass.addHiddenApiExemptions("");
            } catch (Throwable t) {
                log(6, TAG, "HiddenApiBypass 初始化失败", t);
            }
        }

        // 将 this 作为 XposedInterface 传给 HookManager
        HookManager.initialize(this);
        log(4, TAG, "ZTool Hook 模块已加载, 进程: " + param.getProcessName());
    }

    @Override
    public void onPackageLoaded(XposedModuleInterface.PackageLoadedParam param) {
        String packageName = param.getPackageName();
        if (packageName == null) return;
        HookManager.handlePackageLoaded(this, param);
    }

    @Override
    public void onSystemServerStarting(XposedModuleInterface.SystemServerStartingParam param) {
        log(4, TAG, "系统服务器启动中，分发系统作用域Hook");
        HookManager.handleSystemServerStarting(this, param);
    }
}
