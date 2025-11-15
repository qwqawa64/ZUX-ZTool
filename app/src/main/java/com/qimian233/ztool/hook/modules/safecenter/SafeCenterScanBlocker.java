package com.qimian233.ztool.hook.modules.safecenter;

import com.qimian233.ztool.hook.base.BaseHookModule;
import de.robv.android.xposed.XC_MethodReplacement;
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
        return new String[]{"com.lenovo.safecenter"};
    }

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        hookAutoScanEntry(lpparam);
    }

    private void hookAutoScanEntry(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            XposedHelpers.findAndHookMethod(
                    "com.lenovo.safecenter.antivirus.autoscan.AutoOverallScan",
                    lpparam.classLoader,
                    "LocalOverallScanVirus",
                    android.content.Context.class,
                    new XC_MethodReplacement() {
                        @Override
                        protected Object replaceHookedMethod(MethodHookParam param) throws Throwable {
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
