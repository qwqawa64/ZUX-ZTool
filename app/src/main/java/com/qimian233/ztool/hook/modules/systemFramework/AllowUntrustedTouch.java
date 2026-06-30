package com.qimian233.ztool.hook.modules.systemFramework;

import com.qimian233.ztool.hook.base.BaseHookModule;

import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModuleInterface;

import java.lang.reflect.Method;

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
        ClassLoader classLoader = param.getDefaultClassLoader();
        String packageName = param.getPackageName();
        Method method = classLoader.loadClass("com.android.server.wm.WindowState")
                .getDeclaredMethod("getTouchOcclusionMode");
        this.xposed.hook(method).intercept(chain -> 2);
    }
}
