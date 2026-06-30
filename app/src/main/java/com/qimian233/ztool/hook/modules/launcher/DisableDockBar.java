package com.qimian233.ztool.hook.modules.launcher;

import android.view.View;

import com.qimian233.ztool.hook.base.BaseHookModule;

import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModuleInterface;

import java.lang.reflect.Method;

public class DisableDockBar extends BaseHookModule {

    private static final String LAUNCHER_PACKAGE = "com.zui.launcher";

    public DisableDockBar() {}

    @Override
    public String getModuleName() {
        return "disable_dock_bar";
    }

    @Override
    public String[] getTargetPackages() {
        return new String[]{LAUNCHER_PACKAGE};
    }

    @Override
    public void handleLoadPackage(XposedModuleInterface.PackageLoadedParam param) throws Throwable {
        ClassLoader classLoader = param.getDefaultClassLoader();
        String packageName = param.getPackageName();
        if (!LAUNCHER_PACKAGE.equals(packageName)) {
            return;
        }

        log("开始Hook ZUI Launcher Dock栏");

        try {
            hookDockVisibility(classLoader);

            log("ZUI Launcher Dock栏隐藏Hook完成");
        } catch (Throwable t) {
            logError("ZUI Launcher Dock栏Hook过程中发生错误", t);
        }
    }

    /**
     * Hook ZuiHotseat.setVisibility() 强制隐藏Dock栏视图
     */
    private void hookDockVisibility(ClassLoader classLoader) {
        try {
            Class<?> zuiHotseatClass = classLoader.loadClass("com.zui.launcher.uiextend.ZuiHotseat");
            Method setVisibilityMethod = zuiHotseatClass.getDeclaredMethod("setVisibility", int.class);
            this.xposed.hook(setVisibilityMethod).intercept(chain -> {
                int visibility = (int) chain.getArg(0);
                if (visibility == View.VISIBLE) {
                    // Block setting visibility to VISIBLE, effectively hiding the dock
                    return null;
                }
                return chain.proceed();
            });
            log("ZuiHotseat.setVisibility Hook完成");
        } catch (Throwable t) {
            logError("Hook ZuiHotseat.setVisibility失败", t);
        }
    }

}
