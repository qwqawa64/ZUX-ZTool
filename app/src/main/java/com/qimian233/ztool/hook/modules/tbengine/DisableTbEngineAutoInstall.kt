package com.qimian233.ztool.hook.modules.tbengine

import android.content.Context
import com.qimian233.ztool.data.keys.PreferenceKeys
import com.qimian233.ztool.data.keys.ScopeKeys
import com.qimian233.ztool.hook.base.AppHookModule
import io.github.libxposed.api.XposedModuleInterface

/**
 * 禁用 UDS 实时连接引擎 (com.lenovo.tbengine) 的自动安装行为：
 * - A/B 无缝更新：拦截 ServiceController.startABInstalling（校验通过后的自动后台安装）；
 * - A-only / Recovery：拦截 RecoverySystem.installPackage（重启进 Recovery 安装的总闸）；
 * - 夜间自动安装策略位：强制 OtaPolicy.mSettingNormalAutoInstall 为 false。
 *
 * 注意：RecoverySystem.installPackage 同为用户手动确认重启的入口，开关开启后
 * 用户手动确认重启安装也不会执行。
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
                // no-op：吞掉自动进入 AB 安装
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
                // no-op：吞掉重启进 Recovery 的安装
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
