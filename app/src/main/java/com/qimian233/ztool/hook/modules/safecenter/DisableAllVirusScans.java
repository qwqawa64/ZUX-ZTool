package com.qimian233.ztool.hook.modules.safecenter;

import android.content.ContentResolver;
import android.content.Context;

import com.qimian233.ztool.hook.base.AppHookModule;

import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModuleInterface;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

public class DisableAllVirusScans extends AppHookModule {
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
            logger.info("Hooking safecenter to block manager initialization.");
            Class<?> managerCreatorFClass = classLoader.loadClass("tmsdk.fg.creator.ManagerCreatorF");
            Method getManagerMethod = managerCreatorFClass.getDeclaredMethod("getManager", Class.class);
            hookWithId(getManagerMethod, "get_manager", chain -> null);
            logger.info("Successfully hooked safecenter!");
        } catch (Exception e) {
            logger.error("Failed to hook scan manager! ", e);
        }
    }

    private void hookDbManager(ClassLoader classLoader) {
        try {
            logger.info("Set getVirusAppsCount return value to int 0");
            Class<?> antiVirusDBManagerClass = classLoader.loadClass(
                    "com.lenovo.safecenter.antivirus.db.AntiVirusDBManager");
            Method getVirusAppsCountMethod = antiVirusDBManagerClass.getDeclaredMethod("getVirusAppsCount");
            hookWithId(getVirusAppsCountMethod, "get_virus_apps_count", chain -> 0);
            logger.info("getVirusAppsCount is set to 0.");

            logger.info("Blocking AntiVirusDBHelper initialization.");
            Class<?> antiVirusDBHelperClass = classLoader.loadClass(
                    "com.lenovo.safecenter.antivirus.db.AntiVirusDBHelper");
            Constructor<?> ctor = antiVirusDBHelperClass.getDeclaredConstructor(Context.class);
            hookWithId(ctor, "ctor", chain -> null);
        } catch (Exception e) {
            logger.error("Failed to hook DB manager! ", e);
        }
    }

    private void disableVirusPopup(ClassLoader classLoader) {
        try {
            Class<?> notiSMSActivityClass = classLoader.loadClass(
                    "com.lenovo.safecenter.antivirus.views.NotiSMSActivity");
            Method onCreateMethod = notiSMSActivityClass.getDeclaredMethod("onCreate",
                    android.os.Bundle.class);
            hookWithId(onCreateMethod, "on_create", chain -> null);
            logger.info("Virus popup blocked successfully.");
        } catch (Exception e) {
            logger.error("Failed to disable virus popup! ", e);
        }
    }

    private void blockInstallVirusHandler(ClassLoader classLoader) {
        try {
            Class<?> installJudgeServiceClass = classLoader.loadClass(
                    "com.lenovo.safecenter.antivirus.support.InstallJudgeService");
            Class<?> resultEntityClass = classLoader.loadClass("com.lesafe.utils.mode.ResultEntity");
            Method dealVirusMethod = installJudgeServiceClass.getDeclaredMethod("dealVirus",
                    resultEntityClass, boolean.class);
            hookWithId(dealVirusMethod, "deal_virus", chain -> {
                logger.debug("Blocked installed-virus handler from switching SafeCenter icon");
                return null;
            });
            logger.info("InstallJudgeService virus icon handler blocked.");
        } catch (Throwable t) {
            logger.error("Failed to hook InstallJudgeService virus icon handler! ", t);
        }
    }

    private void blockIconNumChange(ClassLoader classLoader) {
        blockInstallVirusHandler(classLoader);
        try {
            Class<?> healthScannerClass = classLoader.loadClass(
                    "com.lenovo.safecenter.services.HealthScanner");
            Method setNumIconMethod = healthScannerClass.getDeclaredMethod("setNumIcon", int.class);
            hookWithId(setNumIconMethod, "set_num_icon", chain -> {
                int originalCount = (int) chain.getArg(0);
                if (originalCount != 0) {
                    logger.debug("Forced HealthScanner icon warning count " + originalCount + " to 0");
                }
                return chain.proceed(new Object[]{0});
            });
            logger.info("HealthScanner icon count changes blocked.");
        } catch (Throwable t) {
            logger.error("Failed to hook HealthScanner icon count! ", t);
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
            hookWithId(putIntMethod, "put_int", chain -> {
                String key = (String) chain.getArg(1);
                if (isSafeCenterIconSetting(key)) {
                    int value = (int) chain.getArg(2);
                    if (value != 0) {
                        logger.debug("Blocked SafeCenter dynamic icon setting " + key + "=" + value);
                    }
                    return chain.proceed(new Object[]{chain.getArg(0), key, 0});
                }
                return chain.proceed();
            });
            logger.info("SafeCenter dynamic icon Settings.System.putInt writes blocked.");
        } catch (Throwable t) {
            logger.error("Failed to hook dynamic icon Settings.System.putInt! ", t);
        }
    }

    private void hookSystemGetInt() {
        try {
            Method getIntMethod = android.provider.Settings.System.class.getDeclaredMethod(
                    "getInt", ContentResolver.class, String.class, int.class);
            hookWithId(getIntMethod, "get_int", chain -> {
                String key = (String) chain.getArg(1);
                if (isSafeCenterIconSetting(key)) {
                    return 0;
                }
                return chain.proceed();
            });
            logger.info("SafeCenter dynamic icon Settings.System.getInt reads forced to normal.");
        } catch (Throwable t) {
            logger.error("Failed to hook dynamic icon Settings.System.getInt! ", t);
        }
    }

    private void forceActiveViewNormalIcon(ClassLoader classLoader) {
        try {
            Class<?> activeViewClass = classLoader.loadClass(
                    "com.lenovo.safecenter.MainTab.ActiveView");
            Method getBitmapDrawableMethod = activeViewClass.getDeclaredMethod(
                    "getBitmapDrawable", Context.class, int.class);
            hookWithId(getBitmapDrawableMethod, "get_bitmap_drawable", chain -> {
                return chain.proceed(new Object[]{chain.getArg(0), 0});
            });
            logger.info("ActiveView dynamic icon rendering forced to normal.");
        } catch (Throwable t) {
            logger.error("Failed to hook ActiveView dynamic icon rendering! ", t);
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
            hookWithId(localOverallScanVirusMethod, "local_overall_scan_virus", chain -> {
                // 直接返回null，阻止自动扫描执行
                logger.debug("Auto virus scan blocked at entry point");
                return null;
            });
            logger.info("Successfully hooked SafeCenter auto scan entry");
        } catch (Throwable t) {
            logger.error("Failed to hook SafeCenter auto scan", t);
        }
    }

}
