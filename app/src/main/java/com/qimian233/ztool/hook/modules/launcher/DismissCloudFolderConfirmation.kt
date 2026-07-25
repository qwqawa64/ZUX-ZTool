package com.qimian233.ztool.hook.modules.launcher

import android.annotation.SuppressLint
import com.qimian233.ztool.hook.base.BaseHookModule
import io.github.libxposed.api.XposedModuleInterface
import java.lang.reflect.Method

class DismissCloudFolderConfirmation: BaseHookModule() {
    override fun getModuleName(): String = "test_hook"

    override fun getTargetPackages(): Array<out String> = arrayOf("com.zui.launcher")

    @SuppressLint("PrivateApi")
    override fun handleLoadPackage(param: XposedModuleInterface.PackageLoadedParam) {
        try {
            val classLoader: ClassLoader = param.defaultClassLoader
            val targetClass: Class<*> = classLoader.loadClass("com.zui.launcher.cloudfolder.CloudUtils")
            val launcherClass: Class<*> = classLoader.loadClass("com.android.launcher3.Launcher")
            val targetMethod: Method = findMethod(targetClass, "showCloudFolderAuthorizationDialog",
                launcherClass, Runnable::class.java, Runnable::class.java)
            xposed.hook(targetMethod).intercept { chain ->
                try {
                    val dialog = chain.proceed(chain.args.toTypedArray()) as? android.app.Dialog
                    dialog?.dismiss()
                    log("Cloud folder authorization dialog auto-dismissed")
                    dialog
                } catch (th: Throwable) {
                    logError("Failed to intercept cloud folder authorization dialog!", th)
                }
            }
        } catch (e: Throwable) {
            logError("Exception caught in DismissCloudFolderConfirmation hook: ", e)
        }
    }

}