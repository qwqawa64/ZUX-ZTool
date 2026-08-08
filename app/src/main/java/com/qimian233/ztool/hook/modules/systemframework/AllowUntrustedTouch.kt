package com.qimian233.ztool.hook.modules.systemframework;

import com.qimian233.ztool.data.keys.ScopeKeys;
import com.qimian233.ztool.hook.base.SystemHookModule;

import android.annotation.SuppressLint;

import io.github.libxposed.api.XposedModuleInterface;

import java.lang.reflect.Method;

@SuppressLint("PrivateApi")
public class AllowUntrustedTouch extends SystemHookModule {

    public AllowUntrustedTouch() {}

    @Override
    public String getModuleName() {
        return "allow_untrusted_touch";
    }

    @Override
    public String[] getTargetPackages() {
        return new String[] {ScopeKeys.SYSTEM_SERVER.packageName};
    }

    @Override
    public void handleSystemServerStarting(XposedModuleInterface.SystemServerStartingParam param) throws Throwable {
        ClassLoader classLoader = param.getClassLoader();
        Method method = classLoader.loadClass("com.android.server.wm.WindowState")
                .getDeclaredMethod("getTouchOcclusionMode");
        hookWithId(method, "touch_occlusion_mode", chain -> 2);
    }
}
