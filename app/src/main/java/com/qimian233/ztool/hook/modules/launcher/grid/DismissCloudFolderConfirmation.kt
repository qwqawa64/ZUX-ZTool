package com.qimian233.ztool.hook.modules.launcher.grid

import android.annotation.SuppressLint
import android.app.Dialog
import android.content.Context
import com.qimian233.ztool.data.keys.PreferenceKeys
import com.qimian233.ztool.data.keys.ScopeKeys
import com.qimian233.ztool.hook.base.AppHookModule
import io.github.libxposed.api.XposedModuleInterface
import java.lang.reflect.Method

/**
 * Auto-dismisses the cloud folder authorization dialog.
 */
class DismissCloudFolderConfirmation : AppHookModule() {

    companion object {
        private const val TARGET_CLASS = "com.zui.launcher.cloudfolder.CloudUtils"
        private const val LAUNCHER_CLASS = "com.android.launcher3.Launcher"

        // Android 17 renamed the method with a Prc suffix; the unsuffixed name is the pre-17 fallback.
        private val METHOD_NAMES = listOf(
            "showCloudFolderAuthorizationDialogPrc",
            "showCloudFolderAuthorizationDialog"
        )
    }

    override fun getModuleName(): String = PreferenceKeys.DISMISS_CLOUD_FOLDER_CONFIRMATION.name

    override fun getTargetPackages(): Array<out String> = arrayOf(ScopeKeys.LAUNCHER.packageName)

    @SuppressLint("PrivateApi")
    override fun handleLoadPackage(param: XposedModuleInterface.PackageLoadedParam) {
        val targetMethod = resolveDialogMethod(param.defaultClassLoader)
        if (targetMethod == null) {
            logger.warn("Cloud folder authorization dialog method not found; dialog stays visible")
            return
        }
        // The Android 17 method is static, so chain.thisObject is null; only the return value is used.
        hookWithId(targetMethod, "target") { chain ->
            val dialog = chain.proceed() as? Dialog
            dialog?.dismiss()
            logger.debug("Cloud folder authorization dialog auto-dismissed")
            dialog
        }
    }

    private fun resolveDialogMethod(classLoader: ClassLoader): Method? {
        val targetClass = try {
            classLoader.loadClass(TARGET_CLASS)
        } catch (_: Throwable) {
            return null
        }
        val paramCandidates = ArrayList<Array<Class<*>>>()
        try {
            paramCandidates.add(
                arrayOf(
                    classLoader.loadClass(LAUNCHER_CLASS),
                    Runnable::class.java,
                    Runnable::class.java
                )
            )
        } catch (_: Throwable) {
            logger.debug("Launcher class not found; trying Context signature")
        }
        paramCandidates.add(arrayOf(Context::class.java, Runnable::class.java, Runnable::class.java))
        for (name in METHOD_NAMES) {
            for (paramTypes in paramCandidates) {
                try {
                    return targetClass.getDeclaredMethod(name, *paramTypes)
                } catch (_: Throwable) {
                    logger.debug("Cloud folder dialog candidate not found: $name")
                }
            }
        }
        return null
    }
}
