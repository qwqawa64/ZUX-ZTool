package com.qimian233.ztool.hook.modules.launcher.grid

import android.annotation.SuppressLint
import android.app.Dialog
import com.qimian233.ztool.hook.base.BaseHookModule
import io.github.libxposed.api.XposedModuleInterface
import java.lang.reflect.Method

class DismissCloudFolderConfirmation: BaseHookModule() {
    override fun getModuleName(): String = "dismiss_cloud_folder_confirmation"

    override fun getTargetPackages(): Array<out String> = arrayOf("com.zui.launcher")

    @SuppressLint("PrivateApi")
    override fun handleLoadPackage(param: XposedModuleInterface.PackageLoadedParam) {
        try {
            val classLoader: ClassLoader = param.defaultClassLoader
            val targetClass: Class<*> = classLoader.loadClass("com.zui.launcher.cloudfolder.CloudUtils")
            val launcherClass: Class<*> = classLoader.loadClass("com.android.launcher3.Launcher")
            val targetMethod: Method = findMethod(targetClass, "showCloudFolderAuthorizationDialog",
                launcherClass, Runnable::class.java, Runnable::class.java)
            hookWithId(targetMethod, "target") {  chain ->
                try {
                    val dialog = chain.proceed(chain.args.toTypedArray()) as? Dialog
                    dialog?.dismiss()
                    logger.debug("Cloud folder authorization dialog auto-dismissed")
                    dialog
                } catch (th: Throwable) {
                    logger.error("Failed to intercept cloud folder authorization dialog!", th)
                }
            }
        } catch (e: Throwable) {
            logger.error("Exception caught in DismissCloudFolderConfirmation hook: ", e)
        }
    }

}