package com.qimian233.ztool.hook.modules.systemui.keyguard

import android.annotation.SuppressLint
import com.qimian233.ztool.data.keys.PreferenceKeys
import com.qimian233.ztool.data.keys.ScopeKeys
import com.qimian233.ztool.hook.base.AppHookModule
import io.github.libxposed.api.XposedModuleInterface

/**
 * Bypass all "shorter than 72h" face authentication timeout gates inside SystemUI.
 *
 * Background (based on reverse engineering of com.android.systemui):
 * ZUI customizations add two timeout checks in ZuiFaceAuthDelegate.checkAndStartFaceDetecting
 * that do not exist in AOSP. When triggered, they set KeyguardFaceUnlockManager.mSecurityTime = true
 * (a sticky flag, only cleared by setLastPassTimestamp after a successful PIN/pattern/fingerprint
 * verification), and set the face detection state to 20 (FACE_DETECT_DISABLE), forcing a
 * fallback to PIN/pattern verification:
 * - 4h/12h gate: (mScreenTurnedOff ? secureTime : mWakeSecureTime)
 *   >= 14400000ms (4h); 43200000ms (12h) when faceunlock_bcr_timeout_extended_on is enabled
 * - 24h fallback: (mScreenTurnedOff ? mAdditionSecureTime : mWakeSecureTime)
 *   >= 86400000ms (24h since last successful authentication)
 *
 * Implementation: single-point hook on checkAndStartFaceDetecting(boolean):
 * - Before the call: zero out the three time bases (currentTimeOn = now, secureTime = 0,
 *   mAdditionSecureTime = 0), so both timeout comparisons are always false and the state
 *   machine naturally enters the normal face detection branch;
 * - After the call: force-clear the sticky mSecurityTime flag, as a fallback for cases
 *   where it was set before the hook was enabled and for residue read by the Bouncer
 *   message area (ZuiBouncerKeyguardMessageAreaDelegate).
 *
 * Explicitly not handled:
 * - mFaceDetectNum >= 3 (per-wake-up detection retry limit) is unrelated to the timeout gates;
 * - 72h strong verification on the system_server side (LockSettingsStrongAuth) is out of scope;
 * - Device lockout (isUserLockout, triggered by repeated wrong attempts) is a security
 *   mechanism, not a timeout gate.
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
