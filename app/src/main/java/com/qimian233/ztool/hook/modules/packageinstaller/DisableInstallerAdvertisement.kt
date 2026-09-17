package com.qimian233.ztool.hook.modules.packageinstaller

import android.annotation.SuppressLint
import com.qimian233.ztool.data.keys.PreferenceKeys
import com.qimian233.ztool.data.keys.ScopeKeys
import com.qimian233.ztool.hook.base.AppHookModule
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam

/**
 * Hook module to disable the recommendation ads shown by PackageInstaller
 * after an installation completes.
 * Function: prevents the install success page from initializing recommended
 * app data, eliminating the ad interference.
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

            // Hook initRecommendAppsData to prevent ad data initialization
            val initRecommendAppsData =
                installSuccessClass.getDeclaredMethod("initRecommendAppsData")
            hookWithId(
                initRecommendAppsData,
                "init_recommend_apps_data_1"
            ) {
                // Return directly without executing any ad initialization logic
                logger.debug("Blocked PackageInstaller ad data initialization")
                null
            }

            logger.info("Successfully hooked PackageInstaller ad blocking module")
        } catch (t: Throwable) {
            logger.error("Failed to hook PackageInstaller", t)
        }
    }
}
