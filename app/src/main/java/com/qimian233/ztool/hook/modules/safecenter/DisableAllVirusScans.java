package com.qimian233.ztool.hook.modules.safecenter;

import android.content.ContentResolver;
import android.content.Context;

import com.qimian233.ztool.hook.base.BaseHookModule;

import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModuleInterface;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

public class DisableAllVirusScans extends BaseHookModule {
    private static final String KEY_DYNAMIC_ICONS = "com.zui.safecenter.dynamic_icons";
    private static final String KEY_SAFE_CENTER_ICON = "safecentericon";

    public DisableAllVirusScans() {}

    @Override
    public String getModuleName() {
        return "disable_all_virus_scans";
    }

    @Override
    public String[] getTargetPackages() {
        return new String[]{"com.lenovo.safecenter", "com.zui.safecenter"};
    }

    @Override
    public void handleLoadPackage(XposedModuleInterface.PackageLoadedParam param) throws Throwable {
        ClassLoader classLoader = param.getDefaultClassLoader();
        String packageName = param.getPackageName();
        hookGetManager(classLoader);
        hookDbManager(classLoader);
        disableVirusPopup(classLoader);
        blockIconNumChange(classLoader);
        blockDynamicIconSettings(classLoader);
        forceActiveViewNormalIcon(classLoader);
        disableAutoScan(classLoader);
    }

    private void hookGetManager(ClassLoader classLoader) {
        try {
            log("Hooking safecenter to block manager initialization.");
            Class<?> managerCreatorFClass = classLoader.loadClass("tmsdk.fg.creator.ManagerCreatorF");
            Method getManagerMethod = managerCreatorFClass.getDeclaredMethod("getManager", Class.class);
            this.xposed.hook(getManagerMethod).intercept(chain -> null);
            log("Successfully hooked safecenter!");
        } catch (Exception e) {
            logError("Failed to hook scan manager! ", e);
        }
    }

    private void hookDbManager(ClassLoader classLoader) {
        try {
            log("Set getVirusAppsCount return value to int 0");
            Class<?> antiVirusDBManagerClass = classLoader.loadClass(
                    "com.lenovo.safecenter.antivirus.db.AntiVirusDBManager");
            Method getVirusAppsCountMethod = antiVirusDBManagerClass.getDeclaredMethod("getVirusAppsCount");
            this.xposed.hook(getVirusAppsCountMethod).intercept(chain -> 0);
            log("getVirusAppsCount is set to 0.");

            log("Blocking AntiVirusDBHelper initialization.");
            Class<?> antiVirusDBHelperClass = classLoader.loadClass(
                    "com.lenovo.safecenter.antivirus.db.AntiVirusDBHelper");
            Constructor<?> ctor = antiVirusDBHelperClass.getDeclaredConstructor(Context.class);
            this.xposed.hook(ctor).intercept(chain -> null);
        } catch (Exception e) {
            logError("Failed to hook DB manager! ", e);
        }
    }

    private void disableVirusPopup(ClassLoader classLoader) {
        try {
            Class<?> notiSMSActivityClass = classLoader.loadClass(
                    "com.lenovo.safecenter.antivirus.views.NotiSMSActivity");
            Method onCreateMethod = notiSMSActivityClass.getDeclaredMethod("onCreate",
                    android.os.Bundle.class);
            this.xposed.hook(onCreateMethod).intercept(chain -> null);
            log("Virus popup blocked successfully.");
        } catch (Exception e) {
            logError("Failed to disable virus popup! ", e);
        }
    }

    private void blockInstallVirusHandler(ClassLoader classLoader) {
        try {
            Class<?> installJudgeServiceClass = classLoader.loadClass(
                    "com.lenovo.safecenter.antivirus.support.InstallJudgeService");
            Class<?> resultEntityClass = classLoader.loadClass("com.lesafe.utils.mode.ResultEntity");
            Method dealVirusMethod = installJudgeServiceClass.getDeclaredMethod("dealVirus",
                    resultEntityClass, boolean.class);
            this.xposed.hook(dealVirusMethod).intercept(chain -> {
                log("Blocked installed-virus handler from switching SafeCenter icon");
                return null;
            });
            log("InstallJudgeService virus icon handler blocked.");
        } catch (Throwable t) {
            logError("Failed to hook InstallJudgeService virus icon handler! ", t);
        }
    }

    private void blockIconNumChange(ClassLoader classLoader) {
        blockInstallVirusHandler(classLoader);
        try {
            Class<?> healthScannerClass = classLoader.loadClass(
                    "com.lenovo.safecenter.services.HealthScanner");
            Method setNumIconMethod = healthScannerClass.getDeclaredMethod("setNumIcon", int.class);
            this.xposed.hook(setNumIconMethod).intercept(chain -> {
                int originalCount = (int) chain.getArg(0);
                if (originalCount != 0) {
                    log("Forced HealthScanner icon warning count " + originalCount + " to 0");
                }
                return chain.proceed(new Object[]{0});
            });
            log("HealthScanner icon count changes blocked.");
        } catch (Throwable t) {
            logError("Failed to hook HealthScanner icon count! ", t);
        }
    }

    private void blockDynamicIconSettings(ClassLoader classLoader) {
        hookSystemPutInt();
        hookSystemGetInt();
    }

    private void hookSystemPutInt() {
        try {
            Method putIntMethod = android.provider.Settings.System.class.getDeclaredMethod(
                    "putInt", ContentResolver.class, String.class, int.class);
            this.xposed.hook(putIntMethod).intercept(chain -> {
                String key = (String) chain.getArg(1);
                if (isSafeCenterIconSetting(key)) {
                    int value = (int) chain.getArg(2);
                    if (value != 0) {
                        log("Blocked SafeCenter dynamic icon setting " + key + "=" + value);
                    }
                    return chain.proceed(new Object[]{chain.getArg(0), key, 0});
                }
                return chain.proceed();
            });
            log("SafeCenter dynamic icon Settings.System.putInt writes blocked.");
        } catch (Throwable t) {
            logError("Failed to hook dynamic icon Settings.System.putInt! ", t);
        }
    }

    private void hookSystemGetInt() {
        try {
            Method getIntMethod = android.provider.Settings.System.class.getDeclaredMethod(
                    "getInt", ContentResolver.class, String.class, int.class);
            this.xposed.hook(getIntMethod).intercept(chain -> {
                String key = (String) chain.getArg(1);
                if (isSafeCenterIconSetting(key)) {
                    return 0;
                }
                return chain.proceed();
            });
            log("SafeCenter dynamic icon Settings.System.getInt reads forced to normal.");
        } catch (Throwable t) {
            logError("Failed to hook dynamic icon Settings.System.getInt! ", t);
        }
    }

    private void forceActiveViewNormalIcon(ClassLoader classLoader) {
        try {
            Class<?> activeViewClass = classLoader.loadClass(
                    "com.lenovo.safecenter.MainTab.ActiveView");
            Method getBitmapDrawableMethod = activeViewClass.getDeclaredMethod(
                    "getBitmapDrawable", Context.class, int.class);
            this.xposed.hook(getBitmapDrawableMethod).intercept(chain -> {
                return chain.proceed(new Object[]{chain.getArg(0), 0});
            });
            log("ActiveView dynamic icon rendering forced to normal.");
        } catch (Throwable t) {
            logError("Failed to hook ActiveView dynamic icon rendering! ", t);
        }
    }

    private boolean isSafeCenterIconSetting(String key) {
        return KEY_DYNAMIC_ICONS.equals(key) || KEY_SAFE_CENTER_ICON.equals(key);
    }

    private void disableAutoScan(ClassLoader classLoader) {
        try {
            Class<?> autoOverallScanClass = classLoader.loadClass(
                    "com.lenovo.safecenter.antivirus.autoscan.AutoOverallScan");
            Method localOverallScanVirusMethod = autoOverallScanClass.getDeclaredMethod(
                    "LocalOverallScanVirus", Context.class);
            this.xposed.hook(localOverallScanVirusMethod).intercept(chain -> {
                // 直接返回null，阻止自动扫描执行
                log("Auto virus scan blocked at entry point");
                return null;
            });
            log("Successfully hooked SafeCenter auto scan entry");
        } catch (Throwable t) {
            logError("Failed to hook SafeCenter auto scan", t);
        }
    }

}
