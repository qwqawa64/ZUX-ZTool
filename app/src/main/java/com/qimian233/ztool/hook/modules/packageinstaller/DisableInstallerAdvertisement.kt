package com.qimian233.ztool.hook.modules.packageinstaller

import android.annotation.SuppressLint
import com.qimian233.ztool.data.PreferenceKeys
import com.qimian233.ztool.data.ScopeKeys
import com.qimian233.ztool.hook.base.AppHookModule
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam

/**
 * 禁用PackageInstaller应用安装完成后的推荐广告Hook模块
 * 功能：阻止安装成功页面初始化推荐应用数据，消除广告干扰
 */
@SuppressLint("PrivateApi")
class DisableInstallerAdvertisement : AppHookModule() {
    override fun getModuleName(): String = PreferenceKeys.DISABLE_INSTALLER_AD.name

    override fun getTargetPackages(): Array<String> = arrayOf(ScopeKeys.PACKAGE_INSTALLER.packageName)

    override fun handleLoadPackage(param: PackageLoadedParam) {
        val classLoader = param.defaultClassLoader
        try {
            val installSuccessClass = classLoader.loadClass(
                "com.android.packageinstaller.InstallSuccessExtra"
            )

            // Hook initRecommendAppsData方法，阻止广告数据初始化
            val initRecommendAppsData =
                installSuccessClass.getDeclaredMethod("initRecommendAppsData")
            hookWithId(
                initRecommendAppsData,
                "init_recommend_apps_data_1"
            ) {
                // 直接返回，不执行任何广告初始化逻辑
                logger.debug("已阻止PackageInstaller广告数据初始化")
                null
            }

            logger.info("成功Hook PackageInstaller广告屏蔽模块")
        } catch (t: Throwable) {
            logger.error("Hook PackageInstaller失败", t)
        }
    }
}
