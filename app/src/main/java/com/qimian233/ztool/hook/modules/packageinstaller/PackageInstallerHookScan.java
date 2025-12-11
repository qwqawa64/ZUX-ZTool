package com.qimian233.ztool.hook.modules.packageinstaller;

import com.qimian233.ztool.hook.base.BaseHookModule;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * 禁用APK扫描Hook模块
 * 拦截PackageInstaller的扫描流程，直接返回安全结果
 */
public class PackageInstallerHookScan extends BaseHookModule {

    private static final String PACKAGE_INSTALLER = "com.android.packageinstaller";

    @Override
    public String getModuleName() {
        return "disable_scanAPK";
    }

    @Override
    public String[] getTargetPackages() {
        return new String[]{PACKAGE_INSTALLER};
    }

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        String packageName = lpparam.packageName;

        if (PACKAGE_INSTALLER.equals(packageName)) {
            hookPackageInstaller(lpparam);
        }
    }

    private void hookPackageInstaller(XC_LoadPackage.LoadPackageParam lpparam) {
        log("开始Hook PackageInstaller扫描功能...");

        // 方法1：直接跳过扫描，立即返回安全结果
        hookScanMethods(lpparam);

        // 方法2：拦截扫描结果处理
        hookResultMethods(lpparam);

        // 方法3：跳过扫描服务绑定
        hookServiceMethods(lpparam);

        log("PackageInstaller扫描功能Hook完成");
    }

    private void hookScanMethods(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            // 拦截 startScanApps 方法，直接返回不执行扫描
            XposedHelpers.findAndHookMethod(
                    "com.android.packageinstaller.PackageInstallerActivityExtra",
                    lpparam.classLoader,
                    "startScanApps",
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            log("拦截startScanApps，跳过扫描流程");
                            param.setResult(null); // 直接返回，不执行扫描

                            // 立即发送扫描完成的消息
                            Object activity = param.thisObject;
                            Object handler = XposedHelpers.getObjectField(activity, "mHander");
                            if (handler != null) {
                                XposedHelpers.callMethod(handler, "sendEmptyMessage", 2); // SCAN_APP_OK = 2
                                log("发送SCAN_APP_OK消息");
                            }
                        }
                    }
            );
        } catch (Throwable t) {
            logError("Hook startScanApps失败", t);
        }
    }

    private void hookResultMethods(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            // 拦截 showResultIfFinish 方法，强制显示安装界面
            XposedHelpers.findAndHookMethod(
                    "com.android.packageinstaller.PackageInstallerActivityExtra",
                    lpparam.classLoader,
                    "showResultIfFinish",
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            log("拦截showResultIfFinish");

                            Object activity = param.thisObject;

                            // 强制设置扫描结果为安全
                            XposedHelpers.setIntField(activity, "mScanAppResult", 2); // SCAN_APP_OK
                            XposedHelpers.setIntField(activity, "mCheckSafeInstallResult", 1);
                            XposedHelpers.setBooleanField(activity, "isScanBegin", true);

                            log("强制设置扫描结果为安全状态");
                        }
                    }
            );
        } catch (Throwable t) {
            logError("Hook showResultIfFinish失败", t);
        }
    }

    private void hookServiceMethods(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            // 拦截 bindSafeService 方法，跳过服务绑定
            XposedHelpers.findAndHookMethod(
                    "com.android.packageinstaller.PackageInstallerActivityExtra",
                    lpparam.classLoader,
                    "bindSafeService",
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            log("拦截bindSafeService，跳过服务绑定");

                            Object activity = param.thisObject;

                            // 设置已绑定状态，避免重试
                            XposedHelpers.setBooleanField(activity, "isBind", true);
                            XposedHelpers.setBooleanField(activity, "isConnect", true);

                            // 立即发送扫描开始消息
                            Object handler = XposedHelpers.getObjectField(activity, "mHander");
                            if (handler != null) {
                                XposedHelpers.callMethod(handler, "sendEmptyMessage", 1); // SCAN_APP_BEGIN
                                log("发送SCAN_APP_BEGIN消息");
                            }

                            param.setResult(null); // 跳过实际绑定
                        }
                    }
            );
        } catch (Throwable t) {
            logError("Hook bindSafeService失败", t);
        }
    }
}
