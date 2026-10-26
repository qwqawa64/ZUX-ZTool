package com.qimian233.ztool.hook.modules.systemui.keyguard

import android.annotation.SuppressLint
import com.qimian233.ztool.data.keys.PreferenceKeys
import com.qimian233.ztool.data.keys.ScopeKeys
import com.qimian233.ztool.hook.base.AppHookModule
import io.github.libxposed.api.XposedModuleInterface

/**
 * Bypasses ZUI's 4h/12h/24h face-auth timeout gates that force PIN/pattern.
 * ZUXOS <= 2.0: zeroes the ZuiFaceAuthDelegate time bases and the sticky
 * `KeyguardFaceUnlockManager.mSecurityTime` flag around checkAndStartFaceDetecting.
 * ZUXOS 2.5 (Android 17): that delegate is gone; the timeouts moved to the AIDL
 * face service, so SystemUI has no gate left — this module warns once and stays off.
 * Not covered: per-wake retry limits, system_server strong auth and lockout.
 */
@SuppressLint("PrivateApi")
class BypassFaceAuthTimeout : AppHookModule() {

    companion object {
        private val SYSTEMUI_PACKAGE = ScopeKeys.SYSTEM_UI.packageName
        private const val HOOK_ID = "zui_face_auth_timeout_bypass"
        private const val LEGACY_DELEGATE_CLASS = "com.android.keyguard.ZuiFaceAuthDelegate"
        private const val LEGACY_MANAGER_CLASS = "com.android.keyguard.KeyguardFaceUnlockManager"

        /** Android 17 replacement for the legacy delegate; display-only, no timeout gate. */
        private const val AIDL_DELEGATE_CLASS = "com.android.keyguard.ZuiFaceAuthAIDLDelegate"
    }

    private var warnedNoHookPoint = false

    override fun getModuleName(): String = PreferenceKeys.BYPASS_FACE_AUTH_TIMEOUT.name

    override fun getTargetPackages(): Array<String> = arrayOf(SYSTEMUI_PACKAGE)

    override fun handleLoadPackage(param: XposedModuleInterface.PackageLoadedParam) {
        if (param.packageName != SYSTEMUI_PACKAGE) return
        logger.info("Loading module BypassFaceAuthTimeout.")

        val classLoader = param.defaultClassLoader
        val delegateClass = loadClassOrNull(classLoader, LEGACY_DELEGATE_CLASS)
        if (delegateClass == null) {
            warnNoHookPoint(classLoader)
            return
        }

        try {
            hookLegacyDelegate(classLoader, delegateClass)
        } catch (e: Throwable) {
            logger.error("Failed to hook $LEGACY_DELEGATE_CLASS.checkAndStartFaceDetecting", e)
        }
    }

    private fun hookLegacyDelegate(classLoader: ClassLoader, delegateClass: Class<*>) {
        val managerClass = classLoader.loadClass(LEGACY_MANAGER_CLASS)

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

        logger.info("BypassFaceAuthTimeout: $LEGACY_DELEGATE_CLASS#$HOOK_ID hooked successfully.")
    }

    /**
     * Reports the Android 17 layout once: face detection and its timeout gates
     * are owned by the AIDL face service, so there is nothing left to hook here.
     */
    private fun warnNoHookPoint(classLoader: ClassLoader) {
        if (warnedNoHookPoint) return
        warnedNoHookPoint = true
        val aidlPresent = loadClassOrNull(classLoader, AIDL_DELEGATE_CLASS) != null
        logger.warn(
            "BypassFaceAuthTimeout: $LEGACY_DELEGATE_CLASS missing; face auth timeouts now run in the " +
                "AIDL face service and SystemUI only shows its state (AIDL delegate present: $aidlPresent). " +
                "No equivalent hook point on this build; module stays inactive."
        )
    }

    private fun loadClassOrNull(classLoader: ClassLoader, name: String): Class<*>? =
        try {
            classLoader.loadClass(name)
        } catch (_: ClassNotFoundException) {
            null
        }
}
