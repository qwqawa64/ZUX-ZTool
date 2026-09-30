package com.qimian233.ztool.hook.modules.lsfdevice

import com.qimian233.ztool.data.keys.PreferenceKeys
import com.qimian233.ztool.data.keys.ScopeKeys
import com.qimian233.ztool.hook.base.AppHookModule
import io.github.libxposed.api.XposedModuleInterface

/**
 * Blocks the silent install path of the Lenovo device service
 * (com.lenovo.lsf.device) used to auto-update pushed apps, including
 * preinstalled/system apps.
 *
 * The app's own installer classes are obfuscated (x.b / "InstallManager"),
 * so the choke point is placed on the stable platform APIs the installer must
 * call, hooked inside the lsf.device process itself:
 * - API 28+: PackageInstaller.Session#commit — the only install path;
 * - API < 28: hidden PackageManager#installPackage /
 *   installByPackageInstaller (reached via reflection) — intercepted on
 *   ApplicationPackageManager.
 *
 * Any install request issued by this app is by definition a push-driven
 * silent install, so all matching calls are swallowed unconditionally.
 */
class DisableLsfDeviceAutoInstall : AppHookModule() {

    override fun getModuleName(): String = PreferenceKeys.DISABLE_LSF_DEVICE_AUTO_INSTALL.name

    override fun getTargetPackages(): Array<out String?>? =
        arrayOf(ScopeKeys.LENOVO_DEVICE_SERVICE.packageName)

    @Throws(Throwable::class)
    override fun handleLoadPackage(param: XposedModuleInterface.PackageLoadedParam) {
        // API 28+ path: PackageInstaller.Session#commit(IntentSender)
        try {
            val commit = findMethod(
                android.content.pm.PackageInstaller.Session::class.java,
                "commit",
                android.content.IntentSender::class.java
            )
            hookWithId(commit, "lsf_device_install_commit") { chain ->
                logger.debug("Blocked PackageInstaller.Session.commit (silent install)")
                // no-op: swallow the install session commit
            }
        } catch (t: Throwable) {
            logger.error("Failed to hook PackageInstaller.Session.commit", t)
        }

        // API < 28 path: hidden PackageManager#installPackage(Uri, IPackageInstallObserver, int, String)
        try {
            val appPmClass = Class.forName("android.app.ApplicationPackageManager")
            val observerClass = param.defaultClassLoader
                .loadClass("android.content.pm.IPackageInstallObserver")
            val installPackage = findMethod(
                appPmClass,
                "installPackage",
                android.net.Uri::class.java,
                observerClass,
                Int::class.javaPrimitiveType,
                String::class.java
            )
            hookWithId(installPackage, "lsf_device_install_legacy") { chain ->
                logger.debug("Blocked PackageManager.installPackage (legacy silent install)")
                // no-op: swallow the legacy install request
            }
        } catch (t: Throwable) {
            // Acceptable: the legacy path does not exist on newer Android versions
            logger.debug("Legacy installPackage hook unavailable: ${t.message}")
        }
    }
}
