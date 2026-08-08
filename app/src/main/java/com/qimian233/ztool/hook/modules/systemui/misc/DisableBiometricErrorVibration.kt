package com.qimian233.ztool.hook.modules.systemui.misc

import android.annotation.SuppressLint
import com.qimian233.ztool.data.ScopeKeys
import com.qimian233.ztool.data.PreferenceKeys
import com.qimian233.ztool.hook.base.AppHookModule
import io.github.libxposed.api.XposedModuleInterface

/**
 * 关闭生物识别失败震动 Hook。
 *
 * 拦截 VibratorHelper.performHapticFeedback(View, int)，
 * 当 hapticFeedbackConstant == 10005（生物识别错误震动）时跳过原始调用，
 * 从而关闭面容/指纹识别失败时的震动反馈。
 */
@SuppressLint("PrivateApi")
class DisableBiometricErrorVibration : AppHookModule() {

    companion object {
        private val SYSTEMUI_PACKAGE = ScopeKeys.SYSTEM_UI.packageName
        private const val ERROR_HAPTIC_ID = 10005
    }

    override fun getModuleName(): String = PreferenceKeys.DISABLE_BIOMETRIC_ERROR_VIBRATION.name

    override fun getTargetPackages(): Array<String> = arrayOf(SYSTEMUI_PACKAGE)

    override fun handleLoadPackage(param: XposedModuleInterface.PackageLoadedParam) {
        if (param.packageName != SYSTEMUI_PACKAGE) return
        logger.info("Loading module DisableBiometricErrorVibration.")

        try {
            val vibratorHelperClass = param.defaultClassLoader
                .loadClass("com.android.systemui.statusbar.VibratorHelper")

            val method = findMethod(
                vibratorHelperClass,
                "performHapticFeedback",
                android.view.View::class.java,
                Int::class.javaPrimitiveType
            )

            hookWithId(method, "performHapticFeedback") { chain ->
                val hapticId = chain.args[1] as Int
                if (hapticId == ERROR_HAPTIC_ID) {
                    logger.debug(
                        "DisableBiometricErrorVibration: " +
                            "blocked error haptic feedback (id=$ERROR_HAPTIC_ID)"
                    )
                    return@hookWithId null  // skip original — no vibration
                }
                chain.proceed()
            }

            logger.info(
                "DisableBiometricErrorVibration: " +
                    "performHapticFeedback hooked successfully."
            )
        } catch (e: Throwable) {
            logger.error("Failed to hook performHapticFeedback", e)
        }
    }
}
