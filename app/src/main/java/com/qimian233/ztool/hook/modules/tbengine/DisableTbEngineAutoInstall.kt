package com.qimian233.ztool.hook.modules.tbengine

import android.content.Context
import com.qimian233.ztool.data.keys.PreferenceKeys
import com.qimian233.ztool.data.keys.ScopeKeys
import com.qimian233.ztool.hook.base.AppHookModule
import io.github.libxposed.api.XposedModuleInterface

/**
 * Disables automatic installation in the UDS real-time connection engine
 * (com.lenovo.tbengine):
 * - A/B seamless update: intercept ServiceController.startABInstalling
 *   (automatic background install after verification passes);
 * - A-only / Recovery: intercept RecoverySystem.installPackage (the master
 *   switch for rebooting into Recovery to install);
 * - Nightly auto-install policy bit: force OtaPolicy.mSettingNormalAutoInstall
 *   to false.
 *
 * Note: RecoverySystem.installPackage is also the entry point for the user's
 * manual confirmation reboot; once this toggle is enabled, a manually
 * confirmed reboot-install will not execute either.
 */
class DisableTbEngineAutoInstall : AppHookModule() {

    override fun getModuleName(): String = PreferenceKeys.DISABLE_TB_ENGINE_AUTO_INSTALL.name

    override fun getTargetPackages(): Array<out String?>? = arrayOf(ScopeKeys.TB_ENGINE.packageName)

    @Throws(Throwable::class)
    override fun handleLoadPackage(param: XposedModuleInterface.PackageLoadedParam) {
        val classLoader = param.defaultClassLoader

        try {
            val serviceControllerClass = classLoader.loadClass(
                "com.lenovo.tbengine.core.services.ServiceController"
            )
            val startABInstalling = findMethod(
                serviceControllerClass,
                "startABInstalling",
                Context::class.java
            )
            hookWithId(startABInstalling, "tbengine_auto_install_ab") { _ ->
                logger.debug("Blocked automatic A/B seamless install trigger.")
                // no-op: swallow automatic A/B install entry
            }
        } catch (t: Throwable) {
            logger.error("Failed to hook ServiceController.startABInstalling", t)
        }

        try {
            val recoverySystemClass = classLoader.loadClass("android.os.RecoverySystem")
            val installPackage = findMethod(
                recoverySystemClass,
                "installPackage",
                Context::class.java,
                java.io.File::class.java
            )
            hookWithId(installPackage, "tbengine_auto_install_recovery") { _ ->
                logger.debug("Blocked reboot-to-recovery install.")
                // no-op: swallow reboot-to-recovery install
            }
        } catch (t: Throwable) {
            logger.error("Failed to hook RecoverySystem.installPackage", t)
        }

        try {
            val otaPolicyClass = classLoader.loadClass(
                "com.lenovo.tbengine.core.policy.OtaPolicy"
            )
            val autoInstallSetter = findMethod(
                otaPolicyClass,
                "setmSettingNormalAutoInstall",
                Boolean::class.java
            )
            hookWithId(autoInstallSetter, "tbengine_auto_install_policy_set") { chain ->
                chain.proceed(arrayOf(false))
                logger.debug("Forced OtaPolicy.mSettingNormalAutoInstall to false.")
            }
            val autoInstallGetter = findMethod(otaPolicyClass, "getmSettingNormalAutoInstall")
            hookWithId(autoInstallGetter, "tbengine_auto_install_policy_get") { _ ->
                logger.debug("Forced OtaPolicy.mSettingNormalAutoInstall read to false.")
                false
            }
        } catch (t: Throwable) {
            logger.error("Failed to hook OtaPolicy auto install policy", t)
        }
    }
}
