package com.qimian233.ztool.hook.modules.systemFramework;

import com.qimian233.ztool.hook.base.BaseHookModule;

import android.annotation.SuppressLint;

import io.github.libxposed.api.XposedModuleInterface;

import java.lang.reflect.Method;

@SuppressLint("PrivateApi")
public class AllowUntrustedTouch extends BaseHookModule {

    public AllowUntrustedTouch() {}

    @Override
    public String getModuleName() {
        return "allow_untrusted_touch";
    }

    @Override
    public String[] getTargetPackages() {
        return new String[] {"system"};
    }

    @Override
    public void handleLoadPackage(XposedModuleInterface.PackageLoadedParam param) throws Throwable {

    }

    @Override
    public void handleSystemServerStarting(XposedModuleInterface.SystemServerStartingParam param) throws Throwable {
        ClassLoader classLoader = param.getClassLoader();
        Method method = classLoader.loadClass("com.android.server.wm.WindowState")
                .getDeclaredMethod("getTouchOcclusionMode");
        this.xposed.hook(method).intercept(chain -> 2);
    }
}
