package com.qimian233.ztool.hook.modules.ota

import android.app.Activity
import com.qimian233.ztool.hook.base.BaseHookModule
import io.github.libxposed.api.XposedModuleInterface

/**
 * Block OTA install warning dialog to prevent accidental reboot and root loss.
 *
 * Hooks OtaDialogActivity.showInstallWarningDialog(boolean) and replaces it with
 * a simple finish() call, preventing the InstallConfirmDialog from ever appearing.
 * The positive button of that dialog triggers either rebootNow (AB update) or
 * startRebootRecoveryAndInstallPackage (non-AB), both of which cause the device
 * to reboot and lose root access.
 */
class BlockOtaInstallDialog : BaseHookModule() {

    override fun getModuleName(): String = "block_ota_install_dialog"

    override fun getTargetPackages(): Array<out String> = arrayOf("com.lenovo.ota")

    override fun handleLoadPackage(param: XposedModuleInterface.PackageLoadedParam) {
        val cl: ClassLoader = param.defaultClassLoader
        try {
            val dialogActivityClass: Class<*> = cl.loadClass(
                "com.lenovo.row.ota.core.d.ui.OtaDialogActivity"
            )
            val showDialogMethod = findMethod(
                dialogActivityClass,
                "showInstallWarningDialog",
                Boolean::class.javaPrimitiveType!!
            )
            xposed.hook(showDialogMethod).intercept { chain ->
                log("Blocked OTA install warning dialog to prevent accidental reboot")
                val activity = chain.getThisObject() as Activity
                activity.finish()
                // Do not proceed with the original method — dialog must not appear
            }
        } catch (e: Exception) {
            logError("Failed to hook showInstallWarningDialog", e)
        }
    }
}
