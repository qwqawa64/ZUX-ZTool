package com.qimian233.ztool.hook.modules.lsfdevice

import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import com.qimian233.ztool.data.keys.PreferenceKeys
import com.qimian233.ztool.data.keys.ScopeKeys
import com.qimian233.ztool.hook.base.AppHookModule
import io.github.libxposed.api.XposedModuleInterface

/**
 * Disables the installed-app list reporting of the Lenovo device service
 * (com.lenovo.lsf.device).
 *
 * The reporting task (action_report_install_list) enumerates all installed
 * packages — system apps included — via PackageManager and uploads the
 * package/version list to the push server. Rather than hooking its obfuscated
 * report manager, the data source is cut at the stable platform API:
 * PackageManager#getInstalledPackages / getInstalledApplications return an
 * empty list inside the lsf.device process, so nothing reportable is ever
 * collected.
 *
 * Version checks of single packages (getPackageInfo) are unaffected, keeping
 * legitimate push bookkeeping functional.
 */
class DisableLsfDeviceAppListReporting : AppHookModule() {

    override fun getModuleName(): String =
        PreferenceKeys.DISABLE_LSF_DEVICE_APP_LIST_REPORTING.name

    override fun getTargetPackages(): Array<out String?>? =
        arrayOf(ScopeKeys.LENOVO_DEVICE_SERVICE.packageName)

    @Throws(Throwable::class)
    override fun handleLoadPackage(param: XposedModuleInterface.PackageLoadedParam) {
        // android.app.ApplicationPackageManager is a @hide class; resolve it at runtime
        val appPmClass = try {
            Class.forName("android.app.ApplicationPackageManager")
        } catch (t: Throwable) {
            logger.error("ApplicationPackageManager not found, reporting hook not installed", t)
            return
        }

        try {
            val getInstalledPackages = findMethod(
                appPmClass,
                "getInstalledPackages",
                Int::class.javaPrimitiveType
            )
            hookWithId(getInstalledPackages, "lsf_device_report_installed_packages") { _ ->
                logger.debug("Faked empty getInstalledPackages for app-list reporting")
                ArrayList<PackageInfo>()
            }
        } catch (t: Throwable) {
            logger.error("Failed to hook getInstalledPackages", t)
        }

        try {
            val getInstalledApplications = findMethod(
                appPmClass,
                "getInstalledApplications",
                Int::class.javaPrimitiveType
            )
            hookWithId(getInstalledApplications, "lsf_device_report_installed_apps") { _ ->
                logger.debug("Faked empty getInstalledApplications for app-list reporting")
                ArrayList<ApplicationInfo>()
            }
        } catch (t: Throwable) {
            logger.error("Failed to hook getInstalledApplications", t)
        }
    }
}
