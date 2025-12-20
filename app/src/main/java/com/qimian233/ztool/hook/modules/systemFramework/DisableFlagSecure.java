package com.qimian233.ztool.hook.modules.systemFramework;

import com.qimian233.ztool.hook.base.BaseHookModule;

import android.os.Build;
import de.robv.android.xposed.XC_MethodReplacement;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * 禁用FLAG_SECURE标志Hook模块
 * 作用：移除安全窗口标志，允许对"安全内容"进行截图
 */
public class DisableFlagSecure extends BaseHookModule {

    @Override
    public String getModuleName() {
        return "disable_flag_secure";
    }

    @Override
    public String[] getTargetPackages() {
        return new String[]{"android"};
    }

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        String packageName = lpparam.packageName;

        if ("android".equals(packageName)) {
            hookAndroidSystem(lpparam);
        }
    }

    private void hookAndroidSystem(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            log("开始Hook FLAG_SECURE...");
            Class<?> windowStateClass = XposedHelpers.findClass(
                    "com.android.server.wm.WindowState",
                    lpparam.classLoader
            );

            // Hook isSecureLocked方法，始终返回false
            XposedHelpers.findAndHookMethod(
                    windowStateClass,
                    "isSecureLocked",
                    new Object[]{XC_MethodReplacement.returnConstant(false)}
            );

            log("成功Hook WindowState.isSecureLocked()");

        } catch (Throwable t) {
            logError("Hook FLAG_SECURE失败", t);
        }
    }
}
