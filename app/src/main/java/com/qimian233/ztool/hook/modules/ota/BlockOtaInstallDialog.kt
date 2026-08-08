package com.qimian233.ztool.hook.modules.ota

import android.app.Dialog
import com.qimian233.ztool.data.keys.ScopeKeys
import com.qimian233.ztool.hook.base.AppHookModule
import io.github.libxposed.api.XposedModuleInterface

/**
 * Block the NightConfrimDialog that appears after dismissing the OTA install
 * warning dialog, preventing night auto-installation from being silently enabled.
 *
 * The OTA dialog chain:
 * 1. InstallConfirmDialog — positive = reboot (preserved), negative = shows NightConfrimDialog
 * 2. NightConfrimDialog  — positive = enables night auto-install (silent reboot later),
 *                          negative = dismiss + finish
 *
 * By hooking NightConfrimDialog.show() to immediately dismiss, we prevent the
 * night auto-installation from being set up while still allowing intentional reboots
 * through the InstallConfirmDialog positive button.
 */
class BlockOtaInstallDialog : AppHookModule() {

    override fun getModuleName(): String = "block_ota_install_dialog"

    override fun getTargetPackages(): Array<out String> = arrayOf(ScopeKeys.OTA.packageName)

    override fun handleLoadPackage(param: XposedModuleInterface.PackageLoadedParam) {
        val cl: ClassLoader = param.defaultClassLoader
        try {
            val nightDialogClass: Class<*> = cl.loadClass(
                "com.lenovo.row.ota.core.d.ui.NightConfrimDialog"
            )
            val showMethod = findMethod(nightDialogClass, "show")
            hookWithId(showMethod, "show") {  chain ->
                logger.debug("Blocked NightConfrimDialog to prevent night auto-install setup")
                val dialog = chain.thisObject as Dialog
                dialog.dismiss()
                // Do not proceed — dialog must not stay visible
            }
        } catch (e: Exception) {
            logger.error("Failed to hook NightConfrimDialog.show", e)
        }
    }
}
