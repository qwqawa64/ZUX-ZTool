package com.qimian233.ztool.hook.modules.packageinstaller;

import com.qimian233.ztool.hook.base.BaseHookModule;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * 禁用PackageInstaller应用安装完成后的推荐广告Hook模块
 * 功能：阻止安装成功页面初始化推荐应用数据，消除广告干扰
 */
public class Hook_disable_installerAD extends BaseHookModule {

    @Override
    public String getModuleName() {
        return "disable_installerAD";
    }

    @Override
    public String[] getTargetPackages() {
        return new String[]{
                "com.android.packageinstaller",
                "com.google.android.packageinstaller"  // 部分设备可能使用Google的包名
        };
    }

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        String packageName = lpparam.packageName;

        if ("com.android.packageinstaller".equals(packageName)) {
            hookAndroidPackageInstaller(lpparam);
        } else if ("com.google.android.packageinstaller".equals(packageName)) {
            hookGooglePackageInstaller(lpparam);
        }
    }

    private void hookAndroidPackageInstaller(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            Class<?> installSuccessClass = XposedHelpers.findClass(
                    "com.android.packageinstaller.InstallSuccessExtra",
                    lpparam.classLoader
            );

            // Hook initRecommendAppsData方法，阻止广告数据初始化
            XposedHelpers.findAndHookMethod(installSuccessClass, "initRecommendAppsData",
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                            // 直接返回，不执行任何广告初始化逻辑
                            param.setResult(null);
                            log("已阻止PackageInstaller广告数据初始化");
                        }
                    });

            log("成功Hook PackageInstaller广告屏蔽模块");

        } catch (Throwable t) {
            logError("Hook PackageInstaller失败", t);
        }
    }

    private void hookGooglePackageInstaller(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            // Google版本的PackageInstaller可能有不同的类结构
            // 这里可以添加对Google版本的特殊处理
            log("检测到Google PackageInstaller，使用标准Hook方法");

            // 尝试Hook相同的类和方法
            Class<?> installSuccessClass = XposedHelpers.findClassIfExists(
                    "com.android.packageinstaller.InstallSuccessExtra",
                    lpparam.classLoader
            );

            if (installSuccessClass != null) {
                XposedHelpers.findAndHookMethod(installSuccessClass, "initRecommendAppsData",
                        new XC_MethodHook() {
                            @Override
                            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                                param.setResult(null);
                                log("已阻止Google PackageInstaller广告数据初始化");
                            }
                        });
                log("成功Hook Google PackageInstaller广告屏蔽");
            } else {
                log("Google PackageInstaller未找到目标类，可能需要适配");
            }

        } catch (Throwable t) {
            logError("Hook Google PackageInstaller失败", t);
        }
    }
}
