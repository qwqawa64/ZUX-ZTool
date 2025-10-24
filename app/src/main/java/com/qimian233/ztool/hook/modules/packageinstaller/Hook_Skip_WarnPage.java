package com.qimian233.ztool.hook.modules.packageinstaller;

import com.qimian233.ztool.hook.base.BaseHookModule;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * 跳过包安装器警告页面Hook模块
 * 自动点击安装按钮，跳过用户确认步骤
 */
public class Hook_Skip_WarnPage extends BaseHookModule {

    @Override
    public String getModuleName() {
        return "Skip_WarnPage";
    }

    @Override
    public String[] getTargetPackages() {
        return new String[]{
                "com.android.packageinstaller"
        };
    }

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        hookPackageInstallerActivity(lpparam);
    }

    private void hookPackageInstallerActivity(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            // Hook onResume 方法，在界面显示后执行
            XposedHelpers.findAndHookMethod(
                    "com.android.packageinstaller.PackageInstallerActivityExtra",
                    lpparam.classLoader,
                    "onResume",
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                            final Object activity = param.thisObject;

                            // 延迟执行，确保界面完全加载
                            new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(new Runnable() {
                                @Override
                                public void run() {
                                    try {
                                        // 直接调用 handleDirectInstallInFindSameAppCase 方法
                                        XposedHelpers.callMethod(activity, "handleDirectInstallInFindSameAppCase");
                                        log("Successfully called handleDirectInstallInFindSameAppCase");
                                    } catch (Exception e) {
                                        // 如果上面的方法不存在，尝试调用 onDirectInstall 方法
                                        try {
                                            XposedHelpers.callMethod(activity, "onDirectInstall");
                                            log("Successfully called onDirectInstall");
                                        } catch (Exception e2) {
                                            logError("Both installation methods failed", e2);
                                        }
                                    }
                                }
                            }, 10); // 立刻执行
                        }
                    });

            log("Successfully hooked PackageInstallerActivityExtra.onResume");
        } catch (Throwable t) {
            logError("Failed to hook PackageInstallerActivityExtra", t);
        }
    }
}
