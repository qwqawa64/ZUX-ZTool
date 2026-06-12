package com.qimian233.ztool.hook.modules.safecenter;

import com.qimian233.ztool.hook.base.BaseHookModule;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XC_MethodReplacement;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class DisableInstallScan extends BaseHookModule {
    @Override
    public String getModuleName() {
        return "hook_test";
    }

    @Override
    public String[] getTargetPackages() {
        return new String[]{"com.lenovo.safecenter", "com.zui.safecenter"};
    }

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) {
        log("Installing install-scan hooks for " + lpparam.packageName);
        hookLegacyInstallJudgeService(lpparam);
        hookAidlScanService(lpparam);
    }

    private void hookLegacyInstallJudgeService(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            XposedHelpers.findAndHookMethod(
                    "com.lenovo.safecenter.antivirus.support.InstallJudgeService",
                    lpparam.classLoader,
                    "onStart",
                    android.content.Intent.class,
                    int.class,
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            log("Blocked InstallJudgeService.onStart");
                            param.setResult(null);
                        }
                    }
            );
            log("Hooked InstallJudgeService.onStart");
        } catch (Throwable t) {
            logError("Failed to hook InstallJudgeService.onStart", t);
        }

        try {
            XposedHelpers.findAndHookMethod(
                    "com.lenovo.safecenter.antivirus.support.InstallJudgeService",
                    lpparam.classLoader,
                    "scanVirus",
                    String.class,
                    boolean.class,
                    XC_MethodReplacement.returnConstant(null)
            );
            log("Hooked InstallJudgeService.scanVirus");
        } catch (Throwable t) {
            logError("Failed to hook InstallJudgeService.scanVirus", t);
        }

        try {
            XposedHelpers.findAndHookMethod(
                    "com.lenovo.safecenter.antivirus.support.InstallJudgeService",
                    lpparam.classLoader,
                    "dealVirus",
                    "com.lesafe.utils.mode.ResultEntity",
                    boolean.class,
                    XC_MethodReplacement.returnConstant(null)
            );
            log("Hooked InstallJudgeService.dealVirus");
        } catch (Throwable t) {
            logError("Failed to hook InstallJudgeService.dealVirus", t);
        }

        try {
            XposedHelpers.findAndHookMethod(
                    "com.lenovo.safecenter.antivirus.support.InstallJudgeService",
                    lpparam.classLoader,
                    "doinbg",
                    android.content.Intent.class,
                    XC_MethodReplacement.returnConstant(null)
            );
            log("Hooked InstallJudgeService.doinbg");
        } catch (Throwable t) {
            logError("Failed to hook InstallJudgeService.doinbg", t);
        }
    }

    private void hookAidlScanService(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            XposedHelpers.findAndHookMethod(
                    "com.lenovo.safe.antivirusengine.aidl.ScanAppService",
                    lpparam.classLoader,
                    "onBind",
                    android.content.Intent.class,
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            log("ScanAppService.onBind was called");
                        }
                    }
            );
            log("Hooked ScanAppService.onBind");
        } catch (Throwable t) {
            logError("Failed to hook ScanAppService.onBind", t);
        }

        try {
            XposedHelpers.findAndHookMethod(
                    "com.lenovo.safe.antivirusengine.aidl.ScanAppService$ScanAppServiceImpl",
                    lpparam.classLoader,
                    "scan",
                    String.class,
                    "com.lenovo.safe.antivirusengine.aidl.ScanAppResult",
                    XC_MethodReplacement.returnConstant(0)
            );
            log("Hooked ScanAppServiceImpl.scan");
        } catch (Throwable t) {
            logError("Failed to hook ScanAppServiceImpl.scan", t);
        }

        try {
            XposedHelpers.findAndHookMethod(
                    "com.lenovo.safe.antivirusengine.aidl.ScanAppService$ScanAppServiceImpl",
                    lpparam.classLoader,
                    "scanVirus",
                    String.class,
                    "com.lenovo.safe.antivirusengine.aidl.ScanAppResult",
                    XC_MethodReplacement.returnConstant(0)
            );
            log("Hooked ScanAppServiceImpl.scanVirus");
        } catch (Throwable t) {
            logError("Failed to hook ScanAppServiceImpl.scanVirus", t);
        }
    }
}
