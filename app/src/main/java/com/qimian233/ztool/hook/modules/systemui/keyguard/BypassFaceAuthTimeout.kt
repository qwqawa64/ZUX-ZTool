package com.qimian233.ztool.hook.modules.systemui.keyguard

import android.annotation.SuppressLint
import com.qimian233.ztool.data.keys.PreferenceKeys
import com.qimian233.ztool.data.keys.ScopeKeys
import com.qimian233.ztool.hook.base.AppHookModule
import io.github.libxposed.api.XposedModuleInterface

/**
 * 绕过 SystemUI 内部所有"短于 72h"的人脸识别超时门禁。
 *
 * 背景（基于 com.android.systemui 逆向结论）：
 * ZUI 定制在 ZuiFaceAuthDelegate.checkAndStartFaceDetecting 中加入了两道
 * AOSP 不存在的超时判定，命中后置 KeyguardFaceUnlockManager.mSecurityTime = true
 * （粘性标志，直到密码/图案/指纹验证成功才由 setLastPassTimestamp 清除），
 * 并将人脸检测状态置为 20（FACE_DETECT_DISABLE），强制回退到密码/图案验证：
 * - 4h/12h 门禁：(mScreenTurnedOff ? secureTime : mWakeSecureTime)
 *   >= 14400000ms（4h）；开启 faceunlock_bcr_timeout_extended_on 时为 43200000ms（12h）
 * - 24h 兜底：(mScreenTurnedOff ? mAdditionSecureTime : mWakeSecureTime)
 *   >= 86400000ms（距上次成功验证 24h）
 *
 * 实现方式：单点 Hook checkAndStartFaceDetecting(boolean)：
 * - 进入前：把三个时间基准归零（currentTimeOn = now，secureTime = 0，
 *   mAdditionSecureTime = 0），使两道超时比较恒为 false，状态机自然进入
 *   正常人脸检测分支；
 * - 返回后：强制清除 mSecurityTime 粘性标志，兜底处理 Hook 启用前已置位、
 *   以及 Bouncer 提示区（ZuiBouncerKeyguardMessageAreaDelegate）读取该标志的残留。
 *
 * 明确不处理：
 * - mFaceDetectNum >= 3（单次亮屏内检测重试上限）与超时门禁无关；
 * - system_server 侧 72h 强验证（LockSettingsStrongAuth）不属于本 Hook 范围；
 * - 设备锁定（isUserLockout，多次输错触发）属安全机制，不属于超时门禁。
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
                    // 4h/12h 与 24h 比较全部基于这三个时间基准，归零后恒不超时
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
                    // 原方法内部是唯一写 true 的位置，返回后清掉兜底
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
