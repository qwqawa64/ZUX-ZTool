package com.qimian233.ztool.hook.modules.packageinstaller;

import android.annotation.SuppressLint;

import com.qimian233.ztool.data.ScopeKeys;
import com.qimian233.ztool.hook.base.AppHookModule;

import io.github.libxposed.api.XposedModuleInterface;

import java.lang.reflect.Method;

/**
 * 禁用PackageInstaller应用安装完成后的推荐广告Hook模块
 * 功能：阻止安装成功页面初始化推荐应用数据，消除广告干扰
 */
@SuppressLint("PrivateApi")
public class DisableInstallerAdvertisement extends AppHookModule {

    public DisableInstallerAdvertisement() {}

    @Override
    public String getModuleName() {
        return "disable_installerAD";
    }

    @Override
    public String[] getTargetPackages() {
        return new String[]{
                ScopeKeys.PACKAGE_INSTALLER.packageName,
        };
    }

    @Override
    public void handleLoadPackage(XposedModuleInterface.PackageLoadedParam param) throws Throwable {
        ClassLoader classLoader = param.getDefaultClassLoader();
        hookAndroidPackageInstaller(classLoader);
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
}
