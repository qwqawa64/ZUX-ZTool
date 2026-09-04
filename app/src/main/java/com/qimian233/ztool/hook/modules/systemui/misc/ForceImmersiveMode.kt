package com.qimian233.ztool.hook.modules.systemui.misc

import android.annotation.SuppressLint
import com.qimian233.ztool.data.keys.ScopeKeys
import com.qimian233.ztool.hook.base.AppHookModule
import io.github.libxposed.api.XposedModuleInterface
import java.lang.reflect.Method

/**
 * 强制沉浸式模式 Hook。
 *
 * 通过拦截 SystemUI CommandQueue 中控制系统栏可见性的方法，
 * 强制所有应用使用沉浸式模式（状态栏/导航栏隐藏，可滑动唤出）。
 *
 * 现代 Android (13+) 主要通过 onSystemBarAttributesChanged 的
 * requestedVisibleTypes 参数控制栏的可见性。setWindowState 作为旧路径兜底。
 */
@SuppressLint("PrivateApi")
class ForceImmersiveMode : AppHookModule() {

    companion object {
        private val SYSTEMUI_PACKAGE = ScopeKeys.SYSTEM_UI.packageName
        private const val WINDOW_STATE_SHOWING = 0
        private const val WINDOW_STATE_HIDDEN = 2
    }

    override fun getModuleName(): String = "force_immersive_mode"

    override fun getTargetPackages(): Array<String> = arrayOf(SYSTEMUI_PACKAGE)

    override fun handleLoadPackage(param: XposedModuleInterface.PackageLoadedParam) {
        if (param.packageName != SYSTEMUI_PACKAGE) return
        logger.info("Loading module ForceImmersiveMode.")

        val commandQueueClass = param.defaultClassLoader
            .loadClass("com.android.systemui.statusbar.CommandQueue")

        hookSystemBarAttributes(commandQueueClass)
        hookSetWindowState(commandQueueClass)
    }

    /**
     * 主 Hook：拦截 onSystemBarAttributesChanged，
     * 将 requestedVisibleTypes 强制设为 0，使状态栏和导航栏均隐藏。
     *
     * 方法签名（8 个参数）：
     *   onSystemBarAttributesChanged(
     *     int displayId,             // args[0]
     *     int appearance,            // args[1]
     *     AppearanceRegion[] regions,// args[2]
     *     boolean imeManaged,        // args[3]
     *     int behavior,              // args[4]
     *     int requestedVisibleTypes, // args[5] ← 核心：0=隐藏所有栏
     *     String packageName,        // args[6]
     *     LetterboxDetails[] details // args[7]
     *   )
     */
    private fun hookSystemBarAttributes(commandQueueClass: Class<*>) {
        try {
            // onSystemBarAttributesChanged 使用了内部 Android 类型参数
            // (AppearanceRegion[], LetterboxDetails[])，无法直接引用。
            // 通过名称 + 参数个数定位目标方法。
            val targetMethod: Method = commandQueueClass.declaredMethods
                .first { it.name == "onSystemBarAttributesChanged" && it.parameterTypes.size == 8 }

            hookWithId(targetMethod, "target") {  chain ->
                val args = chain.args.toMutableList()
                // args[5] = requestedVisibleTypes; 设为 0 隐藏状态栏+导航栏
                val current = args[5] as Int
                if (current != 0) {
                    args[5] = 0
                    logger.debug("ForceImmersiveMode: onSystemBarAttributesChanged " +
                        "requestedVisibleTypes=$current -> 0 (hide all bars)")
                }
                chain.proceed(args.toTypedArray())
            }

            logger.info("ForceImmersiveMode: onSystemBarAttributesChanged hooked successfully.")
        } catch (e: Throwable) {
            logger.error("Failed to hook onSystemBarAttributesChanged", e)
        }
    }

    /**
     * 兜底 Hook：拦截旧版 setWindowState(int, int, int)，
     * 将 state=0（显示）改写为 state=2（沉浸式隐藏）。
     */
    private fun hookSetWindowState(commandQueueClass: Class<*>) {
        try {
            val method: Method = findMethod(
                commandQueueClass,
                "setWindowState",
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType
            )

            hookWithId(method, "method") {  chain ->
                val args = chain.args
                val displayId = args[0] as Int
                val type = args[1] as Int
                val state = args[2] as Int

                if (state == WINDOW_STATE_SHOWING) {
                    logger.debug("ForceImmersiveMode: setWindowState(" +
                        "displayId=$displayId, type=$type, state=${0}" +
                        ") -> forcing state=$WINDOW_STATE_HIDDEN")
                    chain.proceed(arrayOf(displayId, type, WINDOW_STATE_HIDDEN))
                } else {
                    chain.proceed()
                }
            }

            logger.info("ForceImmersiveMode: setWindowState hooked successfully.")
        } catch (e: Throwable) {
            logger.error("Failed to hook setWindowState", e)
        }
    }
}
