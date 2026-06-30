package com.qimian233.ztool.hook.modules.packageinstaller;

import com.qimian233.ztool.hook.base.BaseHookModule;
import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModuleInterface;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.widget.TextView;

/**
 * ZUI包安装器Hook模块
 * 功能：绕过ZUI系统的安装限制，修改包安装器界面样式
 * 目标：com.android.packageinstaller (ZUI系统包安装器)
 */
public class packageInstallerStyleHook extends BaseHookModule {

    public packageInstallerStyleHook() {}

    @Override
    public String getModuleName() {
        return "packageInstallerStyle_hook";
    }

    @Override
    public String[] getTargetPackages() {
        return new String[]{
                "com.android.packageinstaller"  // ZUI系统包安装器
        };
    }

    @Override
    public void handleLoadPackage(XposedModuleInterface.PackageLoadedParam param) throws Throwable {
        ClassLoader classLoader = param.getDefaultClassLoader();
        String packageName = param.getPackageName();
        if ("com.android.packageinstaller".equals(packageName)) {
            hookZuiPackageInstaller(classLoader);
            doNotShowWarnTextHook(classLoader);
        }
    }

    private void hookZuiPackageInstaller(ClassLoader classLoader) {
        try {
            // 1. Hook Utils类的isCTSandGTS方法，绕过安装限制
            hookInstallationRestrictions(classLoader);

            // 2. Hook Activity样式，修改界面显示
            hookActivityStyles(classLoader);

            log("ZUI Package Installer Hook 成功加载");
        } catch (Throwable t) {
            logError("ZUI Package Installer Hook 加载失败", t);
        }
    }

    private void hookInstallationRestrictions(ClassLoader classLoader) {
        try {
            Class<?> utilsClass = classLoader.loadClass(
                    "com.android.packageinstaller.extra.Utils");

            // Hook isCTSandGTS方法的重载版本
            Method isCTSandGTS1 = utilsClass.getDeclaredMethod("isCTSandGTS", String.class);
            this.xposed.hook(isCTSandGTS1).intercept(chain -> Boolean.TRUE);

            Method isCTSandGTS2 = utilsClass.getDeclaredMethod("isCTSandGTS", String.class, Intent.class);
            this.xposed.hook(isCTSandGTS2).intercept(chain -> Boolean.TRUE);

            log("成功Hook安装限制检查方法");
        } catch (Throwable t) {
            logError("Hook安装限制检查方法失败", t);
        }
    }

    private void hookActivityStyles(ClassLoader classLoader) {
        try {
            // 获取Theme_AlertDialogActivity的资源ID
            Class<?> styleClass = classLoader.loadClass(
                    "com.android.packageinstaller.R$style");
            Field themeField = styleClass.getDeclaredField("Theme_AlertDialogActivity");
            themeField.setAccessible(true);
            final int themeAlertDialogActivity = themeField.getInt(null);

            // Hook Activity的onCreate方法，修改主题和窗口属性
            Method onCreate = Activity.class.getDeclaredMethod("onCreate", Bundle.class);
            this.xposed.hook(onCreate).intercept(chain -> {
                Activity activity = (Activity) chain.getThisObject();

                // 检查是否为目标包安装器的Activity
                if (activity.getPackageName().equals("com.android.packageinstaller")) {
                    try {
                        // 设置对话框主题
                        activity.setTheme(themeAlertDialogActivity);

                        // 设置透明背景
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                            activity.setTranslucent(true);
                        }

                        // 请求无标题栏
                        activity.requestWindowFeature(1); // 1对应Window.FEATURE_NO_TITLE

                        // 禁用窗口动画
                        activity.getWindow().setWindowAnimations(0);

                        log("成功修改包安装器Activity样式");
                    } catch (Throwable t) {
                        logError("修改Activity样式时出错", t);
                    }
                }

                return chain.proceed();
            });

            log("成功Hook Activity样式修改");
        } catch (Throwable t) {
            logError("Hook Activity样式修改失败", t);
        }
    }

    private void doNotShowWarnTextHook(ClassLoader classLoader) {
        try {
            Class<?> activityClass = classLoader.loadClass(
                    "com.android.packageinstaller.PackageInstallerActivity");
            Method startInstallConfirm = activityClass.getDeclaredMethod("startInstallConfirm");
            this.xposed.hook(startInstallConfirm).intercept(chain -> {
                Object result = chain.proceed();
                try {
                    Class<?> resourcesClass = classLoader.loadClass("com.android.packageinstaller.R$id");
                    Field warnTextViewIdField = resourcesClass.getDeclaredField("install_confirm_question_warning");
                    warnTextViewIdField.setAccessible(true);
                    int warnTextViewId = warnTextViewIdField.getInt(null);

                    Field mDialogField = chain.getThisObject().getClass().getDeclaredField("mDialog");
                    mDialogField.setAccessible(true);
                    AlertDialog dialog = (AlertDialog) mDialogField.get(chain.getThisObject());

                    TextView tv = dialog.findViewById(warnTextViewId);
                    tv.setVisibility(TextView.GONE);
                    log("Successfully set install warn visibility to GONE");
                } catch (Exception e) {
                    logError("Exception happened when trying to set warn text to GONE!", e);
                }
                return result;
            });
        } catch (Throwable t) {
            logError("Failed to hook doNotShowWarnTextHook", t);
        }
    }
}
