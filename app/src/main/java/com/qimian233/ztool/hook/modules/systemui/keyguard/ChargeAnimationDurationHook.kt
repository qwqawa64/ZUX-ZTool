package com.qimian233.ztool.hook.modules.systemui.keyguard

import com.qimian233.ztool.data.keys.PreferenceKeys
import com.qimian233.ztool.data.keys.ScopeKeys
import com.qimian233.ztool.hook.base.AppHookModule
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam

/**
 * 充电动画时长 Hook 模块
 *
 * 锁屏充电动画由 ChargingActivity 通过静态字段 DURATION（默认 3500ms，
 * 由资源 config_chargingAnimationDuration 决定）控制 postDelayed 超时结束。
 * 本模块在 onResume 前改写该静态字段，使超时定时器使用用户自定义时长。
 */
class ChargeAnimationDurationHook : AppHookModule() {
    override fun getModuleName(): String = PreferenceKeys.CHARGE_ANIMATION_DURATION.name

    override fun getTargetPackages(): Array<String> = arrayOf(ScopeKeys.SYSTEM_UI.packageName)

    @Throws(Throwable::class)
    override fun handleLoadPackage(param: PackageLoadedParam) {
        val durationMs = remotePreferences.getInt(
            PreferenceKeys.CHARGE_ANIMATION_DURATION_MS.name,
            PreferenceKeys.CHARGE_ANIMATION_DURATION_MS.default
        )
        try {
            val chargingActivityClass = param.defaultClassLoader.loadClass(TARGET_CLASS)
            val durationField = findField(chargingActivityClass, "DURATION")
            hookWithId(
                findMethod(chargingActivityClass, "onResume"),
                "charge_duration_on_resume"
            ) { chain ->
                try {
                    durationField.set(null, durationMs.toLong())
                    logger.debug("ChargingActivity.DURATION set to ${durationMs}ms")
                } catch (t: Throwable) {
                    logger.error("Failed to set ChargingActivity.DURATION", t)
                }
                chain.proceed()
            }
            logger.info("ChargeAnimationDurationHook applied, duration=${durationMs}ms")
        } catch (t: Throwable) {
            logger.error("ChargeAnimationDurationHook failed", t)
        }
    }

    private companion object {
        const val TARGET_CLASS = "com.android.keyguard.lockscreen.charge.ChargingActivity"
    }
}
