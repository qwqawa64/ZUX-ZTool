package com.qimian233.ztool.hook.modules.systemframework;

import android.annotation.SuppressLint;

import com.qimian233.ztool.data.ScopeKeys;
import com.qimian233.ztool.hook.base.SystemHookModule;

import io.github.libxposed.api.XposedModuleInterface;

import java.lang.reflect.Method;

/**
 * 禁用FLAG_SECURE标志Hook模块
 * 作用：移除安全窗口标志，允许对"安全内容"进行截图
 */
@SuppressLint({"PrivateApi"})
public class DisableFlagSecure extends SystemHookModule {

    public DisableFlagSecure() {}

    @Override
    public String getModuleName() {
        return "disable_flag_secure";
    }

    @Override
    public String[] getTargetPackages() {
        return new String[]{ScopeKeys.SYSTEM_SERVER.packageName};
    }

    @Override
    public void handleSystemServerStarting(XposedModuleInterface.SystemServerStartingParam param) {
        ClassLoader classLoader = param.getClassLoader();
        hookAndroidSystem(classLoader);
    }

    private void hookAndroidSystem(ClassLoader classLoader) {
        try {
            logger.info("开始Hook FLAG_SECURE...");
            Class<?> windowStateClass = classLoader.loadClass(
                    "com.android.server.wm.WindowState"
            );

            // Hook isSecureLocked方法，始终返回false
            Method method = windowStateClass.getDeclaredMethod("isSecureLocked");
            hookWithId(method, "is_secure_locked", chain -> false);

            logger.info("成功Hook WindowState.isSecureLocked()");

        } catch (Throwable t) {
            logger.error("Hook FLAG_SECURE失败", t);
        }
    }
}
