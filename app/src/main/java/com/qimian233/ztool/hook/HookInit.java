package com.qimian233.ztool.hook;

import android.os.Build;

import androidx.annotation.NonNull;

import com.qimian233.ztool.hook.base.HookManager;

import java.util.List;

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
        return instance;
    }

    @Override
    public void onModuleLoaded(@NonNull XposedModuleInterface.ModuleLoadedParam param) {
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
    public void onPackageLoaded(@NonNull XposedModuleInterface.PackageLoadedParam param) {
        HookManager.handlePackageLoaded(param);
    }

    @Override
    public void onSystemServerStarting(@NonNull XposedModuleInterface.SystemServerStartingParam param) {
        log(4, TAG, "系统服务器启动中，分发系统作用域Hook");
        HookManager.handleSystemServerStarting(param);
    }

    // ── 热重载支持 ─────────────────────────────────────────────

    @Override
    public boolean onHotReloading(@NonNull XposedModuleInterface.HotReloadingParam param) {
        // 热重载会创建新一代模块代码（新 classloader），HookManager 的静态字段不跨代共享。
        // 生命周期参数是框架创建的对象（classloader-neutral），必须在旧代码冻结前
        // 通过 savedInstanceState 显式传递给新代码，供 onHotReloaded 重放 Hook 安装。
        param.setSavedInstanceState(new Object[]{
                HookManager.getSavedPackageParams(),
                HookManager.getSavedSystemServerParam()
        });
        log(4, TAG, "热重载请求，已保存生命周期参数: "
                + HookManager.getSavedPackageParams().size() + " 个包, 同意重载");
        return true;
    }

    @Override
    public void onHotReloaded(@NonNull XposedModuleInterface.HotReloadedParam param) {
        instance = this;
        // 恢复旧代码传递过来的生命周期参数（静态字段不跨 classloader 共享，
        // 否则 replayAllHooks 拿不到任何参数，Hook 将全部丢失）。
        Object saved = param.getSavedInstanceState();
        if (saved instanceof Object[]) {
            Object[] arr = (Object[]) saved;
            @SuppressWarnings("unchecked")
            List<XposedModuleInterface.PackageLoadedParam> packages =
                    arr.length > 0
                            ? (List<XposedModuleInterface.PackageLoadedParam>) arr[0]
                            : null;
            XposedModuleInterface.SystemServerStartingParam systemServer =
                    arr.length > 1
                            ? (XposedModuleInterface.SystemServerStartingParam) arr[1]
                            : null;
            HookManager.restoreLifecycleParams(packages, systemServer);
        }
        log(4, TAG, "热重载完成，重新注册模块并回放 Hook 安装");
        HookManager.reinitializeForHotReload(this);
        HookManager.replayAllHooks();
        param.getOldHookHandles().forEach(XposedInterface.HookHandle::unhook);
        log(4, TAG, "热重载清理完成，已卸载旧 Hook: "
                + param.getOldHookHandles().size() + " 个");
    }
}
