package com.qimian233.ztool.hook.modules.safecenter;

import android.content.ContentResolver;
import android.content.Context;

import com.qimian233.ztool.hook.base.BaseHookModule;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XC_MethodReplacement;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class DisableAllVirusScans extends BaseHookModule {
    private static final String KEY_DYNAMIC_ICONS = "com.zui.safecenter.dynamic_icons";
    private static final String KEY_SAFE_CENTER_ICON = "safecentericon";

    @Override
    public String getModuleName() {
        return "disable_all_virus_scans";
    }

    @Override
    public String[] getTargetPackages() {
        return new String[]{"com.lenovo.safecenter", "com.zui.safecenter"};
    }

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        hookGetManager(lpparam);
        hookDbManager(lpparam);
        disableVirusPopup(lpparam);
        blockIconNumChange(lpparam);
        blockDynamicIconSettings(lpparam);
        forceActiveViewNormalIcon(lpparam);
        disableAutoScan(lpparam);
    }

    private void hookGetManager(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            log("Hooking safecenter to block manager initialization.");
            XposedHelpers.findAndHookMethod("tmsdk.fg.creator.ManagerCreatorF",
                    lpparam.classLoader,
                    "getManager",
                    "java.lang.Class",
                    XC_MethodReplacement.returnConstant(null));
            log("Successfully hooked safecenter!");
        } catch (Exception e) {
            logError("Failed to hook scan manager! ", e);
        }
    }

    private void hookDbManager(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            log("Set getVirusAppsCount return value to int 0");
            XposedHelpers.findAndHookMethod("com.lenovo.safecenter.antivirus.db.AntiVirusDBManager",
                    lpparam.classLoader,
                    "getVirusAppsCount",
                    XC_MethodReplacement.returnConstant(0));
            log("getVirusAppsCount is set to 0.");
            log("Blocking AntiVirusDBHelper initialization.");
            XposedHelpers.findAndHookConstructor("com.lenovo.safecenter.antivirus.db.AntiVirusDBHelper",
                    lpparam.classLoader,
                    "android.content.Context",
                    XC_MethodReplacement.returnConstant(null));
        } catch (Exception e) {
            logError("Failed to hook DB manager! ", e);
        }
    }

    private void disableVirusPopup(XC_LoadPackage.LoadPackageParam lpparam) {
        XposedHelpers.findAndHookMethod("com.lenovo.safecenter.antivirus.views.NotiSMSActivity",
                lpparam.classLoader,
                "onCreate",
                "android.os.Bundle",
                XC_MethodReplacement.returnConstant(null)
        );
        log("Virus popup blocked successfully.");
    }

    private void blockInstallVirusHandler(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            XposedHelpers.findAndHookMethod("com.lenovo.safecenter.antivirus.support.InstallJudgeService",
                    lpparam.classLoader,
                    "dealVirus",
                    "com.lesafe.utils.mode.ResultEntity",
                    boolean.class,
                    new XC_MethodReplacement() {
                        @Override
                        protected Object replaceHookedMethod(MethodHookParam param) {
                            log("Blocked installed-virus handler from switching SafeCenter icon");
                            return null;
                        }
                    });
            log("InstallJudgeService virus icon handler blocked.");
        } catch (Throwable t) {
            logError("Failed to hook InstallJudgeService virus icon handler! ", t);
        }
    }

    private void blockIconNumChange(XC_LoadPackage.LoadPackageParam lpparam) {
        blockInstallVirusHandler(lpparam);
        try {
            XposedHelpers.findAndHookMethod("com.lenovo.safecenter.services.HealthScanner",
                    lpparam.classLoader,
                    "setNumIcon",
                    int.class,
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            int originalCount = (int) param.args[0];
                            if (originalCount != 0) {
                                log("Forced HealthScanner icon warning count " + originalCount + " to 0");
                            }
                            param.args[0] = 0;
                        }
                    });
            log("HealthScanner icon count changes blocked.");
        } catch (Throwable t) {
            logError("Failed to hook HealthScanner icon count! ", t);
        }
    }

    private void blockDynamicIconSettings(XC_LoadPackage.LoadPackageParam lpparam) {
        hookSystemPutInt(lpparam);
        hookSystemGetInt(lpparam);
    }

    private void hookSystemPutInt(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            XposedHelpers.findAndHookMethod("android.provider.Settings$System",
                    lpparam.classLoader,
                    "putInt",
                    ContentResolver.class,
                    String.class,
                    int.class,
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            String key = (String) param.args[1];
                            if (isSafeCenterIconSetting(key)) {
                                int value = (int) param.args[2];
                                if (value != 0) {
                                    log("Blocked SafeCenter dynamic icon setting " + key + "=" + value);
                                }
                                param.args[2] = 0;
                            }
                        }
                    });
            log("SafeCenter dynamic icon Settings.System.putInt writes blocked.");
        } catch (Throwable t) {
            logError("Failed to hook dynamic icon Settings.System.putInt! ", t);
        }
    }

    private void hookSystemGetInt(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            XposedHelpers.findAndHookMethod("android.provider.Settings$System",
                    lpparam.classLoader,
                    "getInt",
                    ContentResolver.class,
                    String.class,
                    int.class,
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            String key = (String) param.args[1];
                            if (isSafeCenterIconSetting(key)) {
                                param.setResult(0);
                            }
                        }
                    });
            log("SafeCenter dynamic icon Settings.System.getInt reads forced to normal.");
        } catch (Throwable t) {
            logError("Failed to hook dynamic icon Settings.System.getInt! ", t);
        }
    }

    private void forceActiveViewNormalIcon(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            XposedHelpers.findAndHookMethod("com.lenovo.safecenter.MainTab.ActiveView",
                    lpparam.classLoader,
                    "getBitmapDrawable",
                    Context.class,
                    int.class,
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            param.args[1] = 0;
                        }
                    });
            log("ActiveView dynamic icon rendering forced to normal.");
        } catch (Throwable t) {
            logError("Failed to hook ActiveView dynamic icon rendering! ", t);
        }
    }

    private boolean isSafeCenterIconSetting(String key) {
        return KEY_DYNAMIC_ICONS.equals(key) || KEY_SAFE_CENTER_ICON.equals(key);
    }

    private void disableAutoScan(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            XposedHelpers.findAndHookMethod(
                    "com.lenovo.safecenter.antivirus.autoscan.AutoOverallScan",
                    lpparam.classLoader,
                    "LocalOverallScanVirus",
                    android.content.Context.class,
                    new XC_MethodReplacement() {
                        @Override
                        protected Object replaceHookedMethod(MethodHookParam param) {
                            // 直接返回null，阻止自动扫描执行
                            log("Auto virus scan blocked at entry point");
                            return null;
                        }
                    }
            );
            log("Successfully hooked SafeCenter auto scan entry");
        } catch (Throwable t) {
            logError("Failed to hook SafeCenter auto scan", t);
        }
    }

}
