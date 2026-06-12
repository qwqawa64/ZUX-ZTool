package com.qimian233.ztool.hook.modules.safecenter;

import android.content.Intent;

import com.qimian233.ztool.hook.base.BaseHookModule;

import java.util.List;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XC_MethodReplacement;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class DisableInstallScan extends BaseHookModule {
    private static final String ACTION_VIRUS_FROM_APK_INSTALL =
            "com.zui.safecenter.intent.action.VIRUS_FROM_APKINSTALL";

    @Override
    public String getModuleName() {
        return "disable_install_scan";
    }

    @Override
    public String[] getTargetPackages() {
        return new String[]{"com.lenovo.safecenter", "com.zui.safecenter"};
    }

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) {
        log("Installing install virus-scan hooks for " + lpparam.packageName
                + " process " + lpparam.processName);
        hookInstallPackageReceiver(lpparam);
        hookLegacyInstallJudgeService(lpparam);
        hookAidlScanService(lpparam);
        hookQScannerInstallEntrypoints(lpparam);
    }

    private void hookInstallPackageReceiver(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            XposedHelpers.findAndHookMethod(
                    "com.lenovo.safecenter.antivirus.support.AppBroadcast",
                    lpparam.classLoader,
                    "onReceive",
                    android.content.Context.class,
                    Intent.class,
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            Intent intent = (Intent) param.args[1];
                            if (intent == null) {
                                return;
                            }
                            String action = intent.getAction();
                            if (Intent.ACTION_PACKAGE_ADDED.equals(action)
                                    || Intent.ACTION_PACKAGE_REPLACED.equals(action)
                                    || ACTION_VIRUS_FROM_APK_INSTALL.equals(action)) {
                                log("Blocked AppBroadcast install-virus path: " + action);
                                param.setResult(null);
                            }
                        }
                    }
            );
            log("Hooked AppBroadcast.onReceive install-virus actions");
        } catch (Throwable t) {
            logError("Failed to hook AppBroadcast.onReceive", t);
        }
    }

    private void hookLegacyInstallJudgeService(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            XposedHelpers.findAndHookMethod(
                    "com.lenovo.safecenter.antivirus.support.InstallJudgeService",
                    lpparam.classLoader,
                    "onStart",
                    Intent.class,
                    int.class,
                    XC_MethodReplacement.returnConstant(null)
            );
            log("Hooked InstallJudgeService.onStart");
        } catch (Throwable t) {
            logError("Failed to hook InstallJudgeService.onStart", t);
        }

        try {
            XposedHelpers.findAndHookMethod(
                    "com.lenovo.safecenter.antivirus.support.InstallJudgeService",
                    lpparam.classLoader,
                    "doinbg",
                    Intent.class,
                    XC_MethodReplacement.returnConstant(null)
            );
            log("Hooked InstallJudgeService.doinbg");
        } catch (Throwable t) {
            logError("Failed to hook InstallJudgeService.doinbg", t);
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
    }

    private void hookAidlScanService(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            XposedHelpers.findAndHookMethod(
                    "com.lenovo.safe.antivirusengine.aidl.ScanAppService",
                    lpparam.classLoader,
                    "onBind",
                    Intent.class,
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            Object binder = param.getResult();
                            if (binder != null) {
                                hookReturnedScanBinder(binder.getClass());
                            }
                            log("ScanAppService.onBind returned, install scanner binder hooked if present");
                        }
                    }
            );
            log("Hooked ScanAppService.onBind");
        } catch (Throwable t) {
            logError("Failed to hook ScanAppService.onBind", t);
        }

        hookScanAppMethodByName(lpparam, "scan");
        hookScanAppMethodByName(lpparam, "scanVirus");
    }

    private void hookReturnedScanBinder(Class<?> binderClass) {
        try {
            XposedBridge.hookAllMethods(binderClass, "scan", blockScanAppMethod("binder.scan"));
            XposedBridge.hookAllMethods(binderClass, "scanVirus", blockScanAppMethod("binder.scanVirus"));
            log("Hooked returned ScanApp binder class: " + binderClass.getName());
        } catch (Throwable t) {
            logError("Failed to hook returned ScanApp binder", t);
        }
    }

    private void hookScanAppMethodByName(XC_LoadPackage.LoadPackageParam lpparam, String methodName) {
        try {
            XposedHelpers.findAndHookMethod(
                    "com.lenovo.safe.antivirusengine.aidl.ScanAppService$ScanAppServiceImpl",
                    lpparam.classLoader,
                    methodName,
                    String.class,
                    "com.lenovo.safe.antivirusengine.aidl.ScanAppResult",
                    blockScanAppMethod("ScanAppServiceImpl." + methodName)
            );
            log("Hooked ScanAppServiceImpl." + methodName);
        } catch (Throwable t) {
            logError("Failed to hook ScanAppServiceImpl." + methodName, t);
        }
    }

    private XC_MethodReplacement blockScanAppMethod(final String source) {
        return new XC_MethodReplacement() {
            @Override
            protected Object replaceHookedMethod(MethodHookParam param) {
                if (param.args.length > 1) {
                    markScanResultSafe(param.args[1]);
                }
                log("Blocked " + source + " for " + param.args[0]);
                return 0;
            }
        };
    }

    private void hookQScannerInstallEntrypoints(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            XposedHelpers.findAndHookMethod(
                    "tmsdk.common.module.qscanner.QScannerManagerV2",
                    lpparam.classLoader,
                    "scanInstalledPackages",
                    int.class,
                    List.class,
                    "tmsdk.common.module.qscanner.QScanListener",
                    int.class,
                    long.class,
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            if (isSingleTargetList(param.args[1])) {
                                log("Blocked single-package scanInstalledPackages");
                                param.setResult(0);
                            }
                        }
                    }
            );
            log("Hooked QScannerManagerV2.scanInstalledPackages");
        } catch (Throwable t) {
            logError("Failed to hook QScannerManagerV2.scanInstalledPackages", t);
        }

        try {
            XposedHelpers.findAndHookMethod(
                    "tmsdk.common.module.qscanner.QScannerManagerV2",
                    lpparam.classLoader,
                    "scanUninstallApks",
                    int.class,
                    List.class,
                    "tmsdk.common.module.qscanner.QScanListener",
                    long.class,
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            if (isSingleTargetList(param.args[1])) {
                                log("Blocked single-APK scanUninstallApks");
                                param.setResult(0);
                            }
                        }
                    }
            );
            log("Hooked QScannerManagerV2.scanUninstallApks");
        } catch (Throwable t) {
            logError("Failed to hook QScannerManagerV2.scanUninstallApks", t);
        }
    }

    private boolean isSingleTargetList(Object value) {
        return value instanceof List && ((List<?>) value).size() == 1;
    }

    private void markScanResultSafe(Object scanResult) {
        if (scanResult == null) {
            return;
        }
        try {
            XposedHelpers.setIntField(scanResult, "mType", 0);
            XposedHelpers.setIntField(scanResult, "mAdvice", 0);
            XposedHelpers.setObjectField(scanResult, "mVirusName", null);
            XposedHelpers.setObjectField(scanResult, "mDescription", null);
        } catch (Throwable t) {
            logError("Failed to mark ScanAppResult safe", t);
        }
    }
}
