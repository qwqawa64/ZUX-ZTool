package com.qimian233.ztool.hook.modules.packageinstaller;

import com.qimian233.ztool.hook.base.AppHookModule;
import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModuleInterface;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * 禁用APK扫描Hook模块
 * 拦截PackageInstaller的扫描流程，直接返回安全结果
 */
public class PackageInstallerHookScan extends AppHookModule {

    private static final String PACKAGE_INSTALLER = "com.android.packageinstaller";

    public PackageInstallerHookScan() {}

    @Override
    public String getModuleName() {
        return "disable_scanAPK";
    }

    @Override
    public String[] getTargetPackages() {
        return new String[]{PACKAGE_INSTALLER};
    }

    @Override
    public void handleLoadPackage(XposedModuleInterface.PackageLoadedParam param) throws Throwable {
        ClassLoader classLoader = param.getDefaultClassLoader();
        String packageName = param.getPackageName();
        if (PACKAGE_INSTALLER.equals(packageName)) {
            hookPackageInstaller(classLoader);
        }
    }

    private void hookPackageInstaller(ClassLoader classLoader) {
        logger.info("开始Hook PackageInstaller扫描功能...");

        // 方法1：直接跳过扫描，立即返回安全结果
        hookScanMethods(classLoader);

        // 方法2：拦截扫描结果处理
        hookResultMethods(classLoader);

        // 方法3：跳过扫描服务绑定
        hookServiceMethods(classLoader);

        logger.info("PackageInstaller扫描功能Hook完成");
    }

    private void hookScanMethods(ClassLoader classLoader) {
        try {
            // 拦截 startScanApps 方法，直接返回不执行扫描
            Class<?> activityExtraClass = classLoader.loadClass(
                    "com.android.packageinstaller.PackageInstallerActivityExtra");
            Method startScanApps = activityExtraClass.getDeclaredMethod("startScanApps");
            hookWithId(startScanApps, "start_scan_apps", chain -> {
                logger.debug("拦截startScanApps，跳过扫描流程");

                // 立即发送扫描完成的消息
                Object activity = chain.getThisObject();
                Field mHanderField = activity.getClass().getDeclaredField("mHander");
                mHanderField.setAccessible(true);
                Object handler = mHanderField.get(activity);
                if (handler != null) {
                    handler.getClass().getDeclaredMethod("sendEmptyMessage", int.class)
                            .invoke(handler, 2); // SCAN_APP_OK = 2
                    logger.debug("发送SCAN_APP_OK消息");
                }

                return null; // 直接返回，不执行扫描
            });
        } catch (Throwable t) {
            logger.error("Hook startScanApps失败", t);
        }
    }

    private void hookResultMethods(ClassLoader classLoader) {
        try {
            // 拦截 showResultIfFinish 方法，强制显示安装界面
            Class<?> activityExtraClass = classLoader.loadClass(
                    "com.android.packageinstaller.PackageInstallerActivityExtra");
            Method showResultIfFinish = activityExtraClass.getDeclaredMethod("showResultIfFinish");
            hookWithId(showResultIfFinish, "show_result_if_finish", chain -> {
                logger.debug("拦截showResultIfFinish");

                Object activity = chain.getThisObject();

                // 强制设置扫描结果为安全
                Field mScanAppResultField = activity.getClass().getDeclaredField("mScanAppResult");
                mScanAppResultField.setAccessible(true);
                mScanAppResultField.setInt(activity, 2); // SCAN_APP_OK

                Field mCheckSafeInstallResultField = activity.getClass().getDeclaredField("mCheckSafeInstallResult");
                mCheckSafeInstallResultField.setAccessible(true);
                mCheckSafeInstallResultField.setInt(activity, 1);

                Field isScanBeginField = activity.getClass().getDeclaredField("isScanBegin");
                isScanBeginField.setAccessible(true);
                isScanBeginField.setBoolean(activity, true);

                logger.debug("强制设置扫描结果为安全状态");
                return chain.proceed();
            });
        } catch (Throwable t) {
            logger.error("Hook showResultIfFinish失败", t);
        }
    }

    private void hookServiceMethods(ClassLoader classLoader) {
        try {
            // 拦截 bindSafeService 方法，跳过服务绑定
            Class<?> activityExtraClass = classLoader.loadClass(
                    "com.android.packageinstaller.PackageInstallerActivityExtra");
            Method bindSafeService = activityExtraClass.getDeclaredMethod("bindSafeService");
            hookWithId(bindSafeService, "bind_safe_service", chain -> {
                logger.debug("拦截bindSafeService，跳过服务绑定");

                Object activity = chain.getThisObject();

                // 设置已绑定状态，避免重试
                Field isBindField = activity.getClass().getDeclaredField("isBind");
                isBindField.setAccessible(true);
                isBindField.setBoolean(activity, true);

                Field isConnectField = activity.getClass().getDeclaredField("isConnect");
                isConnectField.setAccessible(true);
                isConnectField.setBoolean(activity, true);

                // 立即发送扫描开始消息
                Field mHanderField = activity.getClass().getDeclaredField("mHander");
                mHanderField.setAccessible(true);
                Object handler = mHanderField.get(activity);
                if (handler != null) {
                    handler.getClass().getDeclaredMethod("sendEmptyMessage", int.class)
                            .invoke(handler, 1); // SCAN_APP_BEGIN
                    logger.debug("发送SCAN_APP_BEGIN消息");
                }

                return null; // 跳过实际绑定
            });
        } catch (Throwable t) {
            logger.error("Hook bindSafeService失败", t);
        }
    }
}
