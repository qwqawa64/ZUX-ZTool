package com.qimian233.ztool.hook.modules.packageinstaller;

import com.qimian233.ztool.hook.base.BaseHookModule;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XC_MethodReplacement;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

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
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        String packageName = lpparam.packageName;

        if ("com.android.packageinstaller".equals(packageName)) {
            hookZuiPackageInstaller(lpparam);
            doNotShowWarnTextHook(lpparam);
        }
    }

    private void hookZuiPackageInstaller(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            // 1. Hook Utils类的isCTSandGTS方法，绕过安装限制
            hookInstallationRestrictions(lpparam);

            // 2. Hook Activity样式，修改界面显示
            hookActivityStyles(lpparam);

            log("ZUI Package Installer Hook 成功加载");
        } catch (Throwable t) {
            logError("ZUI Package Installer Hook 加载失败", t);
        }
    }

    private void hookInstallationRestrictions(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            Class<?> utilsClass = XposedHelpers.findClass(
                    "com.android.packageinstaller.extra.Utils",
                    lpparam.classLoader
            );

            // Hook isCTSandGTS方法的重载版本
            XposedHelpers.findAndHookMethod(
                    utilsClass,
                    "isCTSandGTS",
                    String.class,
                    XC_MethodReplacement.returnConstant(Boolean.TRUE)
            );

            XposedHelpers.findAndHookMethod(
                    utilsClass,
                    "isCTSandGTS",
                    String.class,
                    Intent.class,
                    XC_MethodReplacement.returnConstant(Boolean.TRUE)
            );

            log("成功Hook安装限制检查方法");
        } catch (Throwable t) {
            logError("Hook安装限制检查方法失败", t);
        }
    }

    private void hookActivityStyles(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            // 获取Theme_AlertDialogActivity的资源ID
            Class<?> styleClass = XposedHelpers.findClass(
                    "com.android.packageinstaller.R$style",
                    lpparam.classLoader
            );
            final int themeAlertDialogActivity = XposedHelpers.getStaticIntField(
                    styleClass,
                    "Theme_AlertDialogActivity"
            );

            // Hook Activity的onCreate方法，修改主题和窗口属性
            XposedHelpers.findAndHookMethod(
                    Activity.class,
                    "onCreate",
                    Bundle.class,
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            Activity activity = (Activity) param.thisObject;

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
                        }
                    }
            );

            log("成功Hook Activity样式修改");
        } catch (Throwable t) {
            logError("Hook Activity样式修改失败", t);
        }
    }

    private void doNotShowWarnTextHook(XC_LoadPackage.LoadPackageParam lpparam) {
        XposedHelpers.findAndHookMethod("com.android.packageinstaller.PackageInstallerActivity",
                lpparam.classLoader,
                "startInstallConfirm",
                new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        try {
                            Class<?> resourcesClass = XposedHelpers.findClass("com.android.packageinstaller.R$id", lpparam.classLoader);
                            int warnTextViewId = XposedHelpers.getStaticIntField(resourcesClass, "install_confirm_question_warning");
                            AlertDialog dialog = (AlertDialog) XposedHelpers.getObjectField(param.thisObject, "mDialog");
                            TextView tv = dialog.findViewById(warnTextViewId);
                            tv.setVisibility(TextView.GONE);
                            log("Successfully set install warn visibility to GONE");
                        } catch (Exception e) {
                            logError("Exception happened when trying to set warn text to GONE!", e);
                        }
                    }
        });

    }
}
