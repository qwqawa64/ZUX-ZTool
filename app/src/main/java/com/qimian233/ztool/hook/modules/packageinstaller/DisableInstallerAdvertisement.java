package com.qimian233.ztool.hook.modules.packageinstaller;

import com.qimian233.ztool.hook.base.BaseHookModule;

import io.github.libxposed.api.XposedModuleInterface;

import java.lang.reflect.Method;

/**
 * 禁用PackageInstaller应用安装完成后的推荐广告Hook模块
 * 功能：阻止安装成功页面初始化推荐应用数据，消除广告干扰
 */
public class DisableInstallerAdvertisement extends BaseHookModule {

    public DisableInstallerAdvertisement() {}

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
    public void handleLoadPackage(XposedModuleInterface.PackageLoadedParam param) throws Throwable {
        ClassLoader classLoader = param.getDefaultClassLoader();
        String packageName = param.getPackageName();
        if ("com.android.packageinstaller".equals(packageName)) {
            hookAndroidPackageInstaller(classLoader);
        } else if ("com.google.android.packageinstaller".equals(packageName)) {
            hookGooglePackageInstaller(classLoader);
        }
    }

    private void hookAndroidPackageInstaller(ClassLoader classLoader) {
        try {
            Class<?> installSuccessClass = classLoader.loadClass(
                    "com.android.packageinstaller.InstallSuccessExtra");

            // Hook initRecommendAppsData方法，阻止广告数据初始化
            Method initRecommendAppsData = installSuccessClass.getDeclaredMethod("initRecommendAppsData");
            hookWithId(initRecommendAppsData, "init_recommend_apps_data_1", chain -> {
                // 直接返回，不执行任何广告初始化逻辑
                logger.debug("已阻止PackageInstaller广告数据初始化");
                return null;
            });

            logger.info("成功Hook PackageInstaller广告屏蔽模块");

        } catch (Throwable t) {
            logger.error("Hook PackageInstaller失败", t);
        }
    }

    private void hookGooglePackageInstaller(ClassLoader classLoader) {
        try {
            // Google版本的PackageInstaller可能有不同的类结构
            // 这里可以添加对Google版本的特殊处理
            logger.warn("检测到Google PackageInstaller，使用标准Hook方法");

            // 尝试Hook相同的类和方法
            Class<?> installSuccessClass = null;
            try {
                installSuccessClass = classLoader.loadClass(
                        "com.android.packageinstaller.InstallSuccessExtra");
            } catch (ClassNotFoundException e) {
                // class not found, installSuccessClass remains null
            }

            if (installSuccessClass != null) {
                Method initRecommendAppsData = installSuccessClass.getDeclaredMethod("initRecommendAppsData");
                hookWithId(initRecommendAppsData, "init_recommend_apps_data_2", chain -> {
                    logger.debug("已阻止Google PackageInstaller广告数据初始化");
                    return null;
                });
                logger.info("成功Hook Google PackageInstaller广告屏蔽");
            } else {
                logger.warn("Google PackageInstaller未找到目标类，可能需要适配");
            }

        } catch (Throwable t) {
            logger.error("Hook Google PackageInstaller失败", t);
        }
    }
}
