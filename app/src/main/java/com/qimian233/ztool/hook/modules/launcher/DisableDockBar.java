package com.qimian233.ztool.hook.modules.launcher;

import android.view.View;

import com.qimian233.ztool.hook.base.BaseHookModule;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class DisableDockBar extends BaseHookModule {

    private static final String LAUNCHER_PACKAGE = "com.zui.launcher";

    @Override
    public String getModuleName() {
        return "disable_dock_bar";
    }

    @Override
    public String[] getTargetPackages() {
        return new String[]{LAUNCHER_PACKAGE};
    }

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        if (!LAUNCHER_PACKAGE.equals(lpparam.packageName)) {
            return;
        }

        log("开始Hook ZUI Launcher Dock栏");

        try {
            hookDockVisibility(lpparam);

            log("ZUI Launcher Dock栏隐藏Hook完成");
        } catch (Throwable t) {
            logError("ZUI Launcher Dock栏Hook过程中发生错误", t);
        }
    }

    /**
     * Hook ZuiHotseat.setVisibility() 强制隐藏Dock栏视图
     */
    private void hookDockVisibility(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            XposedHelpers.findAndHookMethod(
                    "com.zui.launcher.uiextend.ZuiHotseat",
                    lpparam.classLoader,
                    "setVisibility",
                    int.class,
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            int visibility = (int) param.args[0];
                            if (visibility == View.VISIBLE) {
                                param.args[0] = View.GONE;
                            }
                        }
                    }
            );
            log("ZuiHotseat.setVisibility Hook完成");
        } catch (Throwable t) {
            logError("Hook ZuiHotseat.setVisibility失败", t);
        }
    }

}
