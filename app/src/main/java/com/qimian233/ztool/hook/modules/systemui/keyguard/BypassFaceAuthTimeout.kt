package com.qimian233.ztool.hook.modules.systemui.keyguard

import android.annotation.SuppressLint
import com.qimian233.ztool.data.keys.PreferenceKeys
import com.qimian233.ztool.data.keys.ScopeKeys
import com.qimian233.ztool.hook.base.AppHookModule
import io.github.libxposed.api.XposedModuleInterface

/**
 * Bypass all "shorter than 72h" face authentication timeout gates inside SystemUI.
 *
 * Background: ZUI adds 4h/12h/24h timeout checks in
 * ZuiFaceAuthDelegate.checkAndStartFaceDetecting that force a fallback to
 * PIN/pattern verification and set a sticky KeyguardFaceUnlockManager.mSecurityTime
 * flag on trigger.
 *
 * Implementation: single-point hook on checkAndStartFaceDetecting(boolean):
 * - Before the call: zero out the three time bases, so both timeout comparisons are
 *   always false and the state machine enters the normal face detection branch;
 * - After the call: force-clear the sticky mSecurityTime flag.
 *
 * Explicitly not handled: per-wake-up retry limits, 72h strong verification on the
 * system_server side (LockSettingsStrongAuth), and device lockout after repeated
 * wrong attempts.
 */
@SuppressLint("PrivateApi")
class BypassFaceAuthTimeout : AppHookModule() {

    companion object {
        private val SYSTEMUI_PACKAGE = ScopeKeys.SYSTEM_UI.packageName
        private const val HOOK_ID = "zui_face_auth_timeout_bypass"
        private const val DELEGATE_CLASS = "com.android.keyguard.ZuiFaceAuthDelegate"
        private const val MANAGER_CLASS = "com.android.keyguard.KeyguardFaceUnlockManager"
    }

    override fun getModuleName(): String = PreferenceKeys.BYPASS_FACE_AUTH_TIMEOUT.name

    override fun getTargetPackages(): Array<String> = arrayOf(SYSTEMUI_PACKAGE)

    override fun handleLoadPackage(param: XposedModuleInterface.PackageLoadedParam) {
        if (param.packageName != SYSTEMUI_PACKAGE) return
        logger.info("Loading module BypassFaceAuthTimeout.")

        try {
            val classLoader = param.defaultClassLoader
            val delegateClass = classLoader.loadClass(DELEGATE_CLASS)
            val managerClass = classLoader.loadClass(MANAGER_CLASS)

            val checkMethod = findMethod(
                delegateClass,
                "checkAndStartFaceDetecting",
                Boolean::class.javaPrimitiveType
            )
            val currentTimeOnField = findField(delegateClass, "currentTimeOn")
            val secureTimeField = findField(delegateClass, "secureTime")
            val additionSecureTimeField = findField(delegateClass, "mAdditionSecureTime")
            val faceUnlockManagerField = findField(delegateClass, "mFaceUnlockManager")
            val securityTimeField = findField(managerClass, "mSecurityTime")

            hookWithId(checkMethod, HOOK_ID) { chain ->
                val delegate = chain.thisObject
                try {
                    // The 4h/12h and 24h comparisons are all based on these three time bases; zeroing them prevents any timeout
                    currentTimeOnField.setLong(delegate, System.currentTimeMillis())
                    secureTimeField.setLong(delegate, 0L)
                    additionSecureTimeField.setLong(delegate, 0L)
                    val manager = faceUnlockManagerField.get(delegate)
                    if (securityTimeField.getBoolean(manager)) {
                        logger.info("Cleared sticky mSecurityTime before checkAndStartFaceDetecting")
                        securityTimeField.setBoolean(manager, false)
                    }
                } catch (t: Throwable) {
                    logger.error("Failed to neutralize face auth timeout bases", t)
                }
                chain.proceed()
                try {
                    // The original method body is the only place that writes true; clear it after return as a fallback
                    val manager = faceUnlockManagerField.get(delegate)
                    if (securityTimeField.getBoolean(manager)) {
                        logger.info("Cleared mSecurityTime after checkAndStartFaceDetecting")
                        securityTimeField.setBoolean(manager, false)
                    }
                } catch (t: Throwable) {
                    logger.error("Failed to clear mSecurityTime after check", t)
                }
                null
            }

            logger.info(
                "BypassFaceAuthTimeout: $DELEGATE_CLASS#$HOOK_ID hooked successfully."
            )
        } catch (e: Throwable) {
            logger.error("Failed to hook ZuiFaceAuthDelegate.checkAndStartFaceDetecting", e)
        }
    }
}
