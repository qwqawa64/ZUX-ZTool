package com.qimian233.ztool.hook.modules.launcher.grid

import android.annotation.SuppressLint
import android.view.View
import com.qimian233.ztool.data.ScopeKeys
import com.qimian233.ztool.hook.base.AppHookModule
import io.github.libxposed.api.XposedModuleInterface
import java.lang.reflect.Method

/**
 * Suppress the blue "newly updated" dot on app icons by forcing
 * BluePoint.isPackageNew(View) to always return false.
 */
@SuppressLint("PrivateApi")
class BluePointRemovalHook : AppHookModule() {

    override fun getModuleName(): String = "launcher_hide_blue_point"

    override fun getTargetPackages(): Array<out String?> = arrayOf(ScopeKeys.LAUNCHER.packageName)

    override fun handleLoadPackage(param: XposedModuleInterface.PackageLoadedParam) {
        try {
            val loader: ClassLoader = param.defaultClassLoader
            val bluePointClass: Class<*> = loader.loadClass("com.zui.launcher.BluePoint")

            val isPackageNewMethod: Method = findMethod(
                bluePointClass, "isPackageNew", View::class.java
            )
            hookWithId(isPackageNewMethod, "is_package_new") { 
                false
            }
            logger.info("BluePoint removal hook installed successfully!")
        } catch (e: Throwable) {
            logger.error("Exception caught in blue point removal hook: ", e)
        }
    }
}
