package com.qimian233.ztool.hook.modules.safecenter;

import com.qimian233.ztool.hook.base.BaseHookModule;
import de.robv.android.xposed.XC_MethodReplacement;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * 联想安全中心自动扫描拦截模块
 * 阻止安全中心的自动病毒扫描功能执行
 */
public class SafeCenterScanBlocker extends BaseHookModule {

    @Override
    public String getModuleName() {
        return "block_safecenter_scan";
    }

    @Override
    public String[] getTargetPackages() {
        return new String[]{"com.lenovo.safecenter", "com.zui.safecenter"};
    }

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        log("Start hooking SafeCenter to block virus scan");
        hookAutoScanEntry(lpparam);
    }

    private void hookAutoScanEntry(XC_LoadPackage.LoadPackageParam lpparam) {
        if (lpparam.packageName.equals("com.lenovo.safecenter")) {
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
        } else if (lpparam.packageName.equals("com.zui.safecenter")) {
            String romRegion = getRomRegion();
            if (!romRegion.equals("PRC")) {
                if (DEBUG) log("Non-PRC device, no need to hook safecenter, value:" + romRegion);
                return;
            } else {
                if (DEBUG) log("PRC device, hooking safecenter");
            }
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
            } catch (Throwable t) {
                logError("Failed to hook SafeCenter auto scan", t);
            }
        }
    }

    // 利用XSharedPreferences来读取设备ROM所在区域
    private String getRomRegion() {
        return  new XSharedPreferences("com.qimian233.ztool",
                "xposed_module_config")
                .getString("RomRegion", "ROW");
    }
}
