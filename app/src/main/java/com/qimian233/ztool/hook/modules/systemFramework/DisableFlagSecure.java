package com.qimian233.ztool.hook.modules.systemFramework;

import com.qimian233.ztool.hook.base.BaseHookModule;

import android.os.Build;
import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModuleInterface;

import java.lang.reflect.Method;

/**
 * 禁用FLAG_SECURE标志Hook模块
 * 作用：移除安全窗口标志，允许对"安全内容"进行截图
 */
public class DisableFlagSecure extends BaseHookModule {

    public DisableFlagSecure() {}

    @Override
    public String getModuleName() {
        return "disable_flag_secure";
    }

    @Override
    public String[] getTargetPackages() {
        return new String[]{"system"};
    }

    @Override
    public void handleLoadPackage(XposedModuleInterface.PackageLoadedParam param) throws Throwable {
        ClassLoader classLoader = param.getDefaultClassLoader();
        String packageName = param.getPackageName();
        hookAndroidSystem(classLoader);
    }

    private void hookAndroidSystem(ClassLoader classLoader) {
        try {
            log("开始Hook FLAG_SECURE...");
            Class<?> windowStateClass = classLoader.loadClass(
                    "com.android.server.wm.WindowState"
            );

            // Hook isSecureLocked方法，始终返回false
            Method method = windowStateClass.getDeclaredMethod("isSecureLocked");
            this.xposed.hook(method).intercept(chain -> false);

            log("成功Hook WindowState.isSecureLocked()");

        } catch (Throwable t) {
            logError("Hook FLAG_SECURE失败", t);
        }
    }
}
